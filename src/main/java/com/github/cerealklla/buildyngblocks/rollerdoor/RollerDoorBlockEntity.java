package com.github.cerealklla.buildyngblocks.rollerdoor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.StringJoiner;

import com.github.cerealklla.buildyngblocks.BuildyngBlocksMod;
import com.github.cerealklla.buildyngblocks.registration.ModBlockEntities;
import com.github.cerealklla.buildyngblocks.registration.ModBlocks;
import com.github.cerealklla.buildyngblocks.registration.ModMenus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.extensions.IMenuProviderExtension;
import org.jspecify.annotations.Nullable;

/**
 * The Roller Door's control block state -- 27-slot free-grid storage, a two-state (Open/Closed,
 * with Opening/Closing in between) door, and the hanging chain of real world blocks below.
 *
 * <p><b>Open/Closed meaning</b> (confirmed against the real garage-door physical metaphor, not the
 * first naive reading): <b>Open = fully retracted</b> -- nothing hangs below, passage is clear, and
 * this is the only state in which {@link #storage()}'s 27-slot inventory is safe to edit (nothing
 * is "in flight"). <b>Closed = fully extended</b> -- the chain hangs below in a stack, blocking
 * passage. Redstone power = commanded Open.
 *
 * <p><b>Chain mechanics</b> -- per the explicit spec ("take a block out of the storage to add at
 * the TOP of the hanging door, or take the TOP block from the door to store"), new material always
 * enters and leaves at the position directly below this control block (depth 1), with the rest of
 * the already-hanging chain shifting to make room -- a uniform "conveyor" motion, not a fixed-tip
 * operation. Concretely, per {@link #TICKS_PER_STEP}-tick step:
 * <ul>
 *   <li><b>Closing (extend)</b>: pop one unit from the highest-index non-empty storage slot
 *   (remembering that slot index on {@link #originSlots}, a LIFO stack), shift every existing
 *   chain block one position deeper, then place the popped block's state at depth 1. The chain's
 *   permanent {@link RollerDoorEndCapBlock} is always pushed along with everything else, so it
 *   naturally ends up the deepest block always. The very first closing step (chain length 0) has
 *   no material to pop -- it just places the End Cap at depth 1 directly.</li>
 *   <li><b>Opening (retract)</b>: the depth-1 block is removed and returned to storage at the slot
 *   popped off {@link #originSlots} (always valid -- same item type, same slot, by construction),
 *   then every remaining chain block shifts one position shallower. When the chain reaches length 1
 *   (only the End Cap left), one more retract step discards it structurally (never added to
 *   inventory -- it has no item form).</li>
 * </ul>
 * Because insertion and removal always happen at the same end (depth 1), the deployed chain itself
 * is a true LIFO stack -- the last block fed in is always the first one retracted, back into the
 * exact slot it came from -- while the initial 27-slot storage stays a free, player-managed grid.
 *
 * <p>Obstruction (closing only -- the single new position each step, depth {@code chainLength+1},
 * might not be air/replaceable): pause this step and retry on the next tick, piston-style. Closing
 * itself has no fixed target length -- it simply keeps extending every step for as long as storage
 * still has material and nothing commands it open, settling to {@code CLOSED} once storage is
 * empty.
 *
 * <p>Movement rate: 2 blocks/sec = one step per {@link #TICKS_PER_STEP} (10) game ticks. Each step
 * runs in two phases, mirroring vanilla's own piston moving-block technique (confirmed against the
 * real decompiled {@code PistonHeadRenderer}/{@code PistonMovingBlockEntity} source for this MC
 * version before writing this, not guessed): {@link #beginStep} hides every real world block
 * currently in the chain (sets it to air) and records where each one is sliding to/from for
 * {@code client.RollerDoorBlockEntityRenderer} to draw as a moving ghost over the step's duration;
 * {@link #commitStep}, once the duration elapses, sets every real final block state and actually
 * mutates storage/{@link #chainLength}. Because every block shifts by exactly one position each
 * step (the "add/remove at the top" conveyor model above), the whole currently-deployed chain
 * visually slides together, not just the newest/departing segment.
 *
 * <p>{@code animating}/{@code activeSlides}/the other mid-step {@code pending*} fields ARE
 * persisted (not just a nice-to-have -- see {@link #setChanged()}'s own doc for the real reason:
 * this same save/load pair doubles as this BlockEntity's client sync payload, since {@code
 * BlockEntity#setChanged()} alone never pushes anything over the network by default). A previous
 * version of this class deliberately left these unpersisted on disk, accepting "a save/restart
 * mid-step leaves stray air with no record" as a rare edge case -- that's resolved as a side effect
 * of fixing the real sync bug, not a separate deliberate change. Hidden chain positions are
 * {@code Blocks.BARRIER} during a step now, not air (see {@link #beginStep}'s own doc) -- a save
 * landing in that same rare window instead leaves a stray, solid-but-invisible barrier until the
 * door next operates (persistence means it resumes and corrects itself on the very next real load,
 * same as any other mid-step crash; only a server that genuinely never comes back online again for
 * this save would ever leave one stuck).
 */
public class RollerDoorBlockEntity extends BlockEntity implements MenuProvider, IMenuProviderExtension {

    public static final int SLOT_COUNT = 27;
    static final int TICKS_PER_STEP = 10;

    public enum DoorState {
        OPEN, OPENING, CLOSED, CLOSING
    }

    /**
     * One block sliding into (closing) or out of (opening) the chain this step. {@code destDepth}
     * is the position this block will actually occupy once the step commits (1-indexed, below the
     * control block) -- except {@code 0}, a sentinel meaning "this block is vanishing back into
     * storage/the control block, never actually placed anywhere."
     */
    public record SlideRecord(int destDepth, BlockState state) {
        static final Codec<SlideRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("Depth").forGetter(SlideRecord::destDepth),
                BlockState.CODEC.fieldOf("State").forGetter(SlideRecord::state)
        ).apply(instance, SlideRecord::new));
    }

    private final SimpleContainer storage = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return RollerDoorSlot.isValidCargo(stack);
        }
    };

    private DoorState state = DoorState.OPEN;
    private boolean commandedOpen = true;
    // Separate from commandedOpen (whose true-by-default is deliberate, for a freshly-placed door's
    // storage to be immediately editable) -- this is purely a cache of the last real redstone signal
    // reading, defaulting to the actually-true state of an unwired block (no signal). See
    // RollerDoorBlock#neighborChanged's own doc for why the two must not be conflated: that hook
    // fires on ANY neighbor block change, not just a real signal edit (confirmed real bug, found
    // live 2026-11-09 -- placing a second Roller Door next to an already-placed one fired this on
    // the existing door, read hasNeighborSignal=false same as always, but with no cache to compare
    // against it was wrongly compared to commandedOpen's unrelated true default and treated as a
    // real "redstone just turned off" edge, slamming the door shut). Deliberately not persisted --
    // worst case a stale cache after a reload costs one redundant no-op comparison on the next real
    // signal change, not worth the extra NBT field.
    private boolean lastKnownPowered = false;
    private int chainLength = 0;
    private final Deque<Integer> originSlots = new ArrayDeque<>();
    /** See {@link #serverTick}'s own doc -- logs an obstruction once, not every tick. Deliberately not persisted; worst case a reload re-logs once more. */
    private boolean obstructionLogged = false;

    private boolean animating = false;
    private boolean animatingClosing = false;
    private long animStartGameTime = -1;
    private List<SlideRecord> activeSlides = List.of();
    private int pendingChainLength = 0;
    private int pendingSlotToConsume = -1;
    private BlockState pendingRemovedState = Blocks.AIR.defaultBlockState();

    public RollerDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROLLER_DOOR.get(), pos, state);
    }

    public Container storage() {
        return storage;
    }

    public DoorState doorState() {
        return state;
    }

    public boolean commandedOpen() {
        return commandedOpen;
    }

    public boolean lastKnownPowered() {
        return lastKnownPowered;
    }

    public void setLastKnownPowered(boolean powered) {
        this.lastKnownPowered = powered;
    }

    public int chainLength() {
        return chainLength;
    }

    public boolean isAnimating() {
        return animating;
    }

    public boolean isAnimatingClosing() {
        return animatingClosing;
    }

    public List<SlideRecord> activeSlides() {
        return activeSlides;
    }

    /** 0 (just started) .. 1 (about to commit), interpolated by {@code partialTicks} for smooth client rendering. */
    public float animationProgress(long levelGameTime, float partialTicks) {
        if (!animating) {
            return 1f;
        }
        float elapsed = (levelGameTime - animStartGameTime) + partialTicks;
        return Math.clamp(elapsed / TICKS_PER_STEP, 0f, 1f);
    }

    /** Called from {@link RollerDoorBlock#neighborChanged} (directly, or via neighbor propagation). */
    public void setCommandedOpen(boolean open) {
        if (this.commandedOpen == open) {
            return;
        }
        DoorState oldState = this.state;
        this.commandedOpen = open;
        if (open && (state == DoorState.CLOSED || state == DoorState.CLOSING)) {
            state = DoorState.OPENING;
        } else if (!open && (state == DoorState.OPEN || state == DoorState.OPENING)) {
            state = DoorState.CLOSING;
        }
        obstructionLogged = false;
        BuildyngBlocksMod.LOGGER.info("[RollerDoor DEBUG] {} setCommandedOpen({}): state {} -> {}",
                getBlockPos(), open, oldState, state);
        setChanged();
    }

    public void serverTick(ServerLevel level, BlockPos controlPos) {
        if (animating) {
            if (level.getGameTime() - animStartGameTime >= TICKS_PER_STEP) {
                commitStep(level, controlPos);
            }
            return;
        }
        if (state != DoorState.OPENING && state != DoorState.CLOSING) {
            return;
        }
        if (state == DoorState.CLOSING) {
            StepResult result = tryBeginExtendStep(level, controlPos);
            if (result == StepResult.NO_MATERIAL) {
                BuildyngBlocksMod.LOGGER.info("[RollerDoor DEBUG] {} CLOSING -> CLOSED (storage empty, chainLength={})",
                        controlPos, chainLength);
                state = DoorState.CLOSED;
                setChanged();
            } else if (result == StepResult.BLOCKED) {
                // Logged once per obstruction, not every tick -- real bug, found live: an obstruction
                // that never clears retries (correctly, by design) every single tick forever, which
                // at 20 logged lines/sec indefinitely was almost certainly the actual cause behind
                // what looked like full client freezes -- not an engine hang, just massive sustained
                // log I/O from a door silently stuck on a leftover obstruction (e.g. a stray barrier
                // from an earlier interrupted step) with no cap and no throttling.
                if (!obstructionLogged) {
                    BuildyngBlocksMod.LOGGER.info("[RollerDoor DEBUG] {} CLOSING step blocked (obstruction at new position), retrying next tick",
                            controlPos);
                    obstructionLogged = true;
                }
            } else {
                obstructionLogged = false;
            }
        } else {
            if (!tryBeginRetractStep(level, controlPos)) {
                BuildyngBlocksMod.LOGGER.info("[RollerDoor DEBUG] {} OPENING -> OPEN (chain fully retracted)", controlPos);
                state = DoorState.OPEN;
                setChanged();
            }
        }
    }

    private enum StepResult {
        PROGRESSED, BLOCKED, NO_MATERIAL
    }

    private StepResult tryBeginExtendStep(ServerLevel level, BlockPos controlPos) {
        List<SlideRecord> records = new ArrayList<>();
        if (chainLength == 0) {
            BlockPos target = controlPos.below(1);
            if (!canOccupy(level, target)) {
                return StepResult.BLOCKED;
            }
            BlockPos endCapPos = controlPos.below(1);
            BlockState endCapState = bakeConnectedShape(level, endCapPos, controlPos,
                    ModBlocks.ROLLER_DOOR_END_CAP.get().defaultBlockState());
            records.add(new SlideRecord(1, endCapState));
            beginStep(level, controlPos, true, records);
            pendingChainLength = 1;
            pendingSlotToConsume = -1;
            return StepResult.PROGRESSED;
        }

        int slotIndex = findTopOccupiedSlot();
        if (slotIndex < 0) {
            return StepResult.NO_MATERIAL;
        }

        BlockPos newDeepest = controlPos.below(chainLength + 1);
        if (!canOccupy(level, newDeepest)) {
            return StepResult.BLOCKED;
        }

        ItemStack slotStack = storage.getItem(slotIndex);
        BlockItem blockItem = (BlockItem) slotStack.getItem();
        BlockState newCargoState = blockItem.getBlock().defaultBlockState();

        // Each record's connection shape (iron bars/fences/panes/walls etc. -- anything that looks
        // at its neighbors) is computed ONCE here, against this step's real current neighbor data,
        // and baked into the record -- not recomputed later (neither per-frame on the client nor
        // again at commit). See bakeConnectedShape's own doc for why a plain live neighbor read
        // (vanilla's Block#updateFromNeighbourShapes) isn't good enough with multiple adjacent doors.
        BlockState newDepth1 = bakeConnectedShape(level, controlPos.below(1), controlPos, newCargoState);
        records.add(new SlideRecord(1, newDepth1));
        for (int oldDepth = 1; oldDepth <= chainLength; oldDepth++) {
            BlockState existing = level.getBlockState(controlPos.below(oldDepth));
            BlockPos newPos = controlPos.below(oldDepth + 1);
            BlockState shaped = bakeConnectedShape(level, newPos, controlPos, existing);
            records.add(new SlideRecord(oldDepth + 1, shaped));
        }
        beginStep(level, controlPos, true, records);
        pendingChainLength = chainLength + 1;
        pendingSlotToConsume = slotIndex;
        return StepResult.PROGRESSED;
    }

    private boolean tryBeginRetractStep(ServerLevel level, BlockPos controlPos) {
        if (chainLength == 0) {
            return false;
        }
        List<SlideRecord> records = new ArrayList<>();
        for (int oldDepth = 2; oldDepth <= chainLength; oldDepth++) {
            BlockState existing = level.getBlockState(controlPos.below(oldDepth));
            BlockPos newPos = controlPos.below(oldDepth - 1);
            records.add(new SlideRecord(oldDepth - 1, bakeConnectedShape(level, newPos, controlPos, existing)));
        }
        // Re-baked here too, not just read raw -- real bug, found live via a screenshot of the
        // vanishing top segment showing a wrong (partially disconnected) shape as it slides up. This
        // entry's destDepth is 0 (never actually placed anywhere, see commitStep), but its state is
        // still what the ghost renders for the whole step -- reading it straight off the live world
        // is exactly as vulnerable to a sibling door's same-tick hide side effect as any other entry
        // (vanilla's own reactive shape propagation can have already, correctly-from-its-own-
        // perspective, stripped this block's connection toward a neighbor that just went transiently
        // hidden), so it needs the same substitution logic as everything else in bakeConnectedShape.
        BlockPos removedPos = controlPos.below(1);
        BlockState removed = bakeConnectedShape(level, removedPos, controlPos, level.getBlockState(removedPos));
        records.add(new SlideRecord(0, removed));

        beginStep(level, controlPos, false, records);
        pendingChainLength = chainLength - 1;
        pendingRemovedState = removed;
        return true;
    }

    /**
     * Computes {@code state}'s connection shape against its real neighbors at {@code pos} -- like
     * vanilla's own {@code Block#updateFromNeighbourShapes}, but substituting each horizontal
     * neighbor's LOGICAL content for its literal, possibly-hidden-mid-animation one.
     *
     * <p>Real bug, found live via a concrete log trace across several adjacent Roller Doors (iron
     * bars/fences/panes on 4 doors in a row): every connection came out asymmetric -- one side of
     * each pair baked "connected," the other baked "not connected," identically and repeatably every
     * single step, never flickering. Root cause confirmed from the log data itself, not guessed:
     * within one server tick, each door's own {@link #beginStep} hides its real chain blocks to air
     * as a side effect of starting its next step -- since sibling BlockEntities tick in some fixed
     * (not spatial) order, whichever door's step happens to process LATER in that same tick reads
     * its neighbor's position as the air {@link #beginStep} JUST wrote, baking "disconnected" --
     * deterministically, every time, since that tick order never changes. Plain {@code
     * Block#updateFromNeighbourShapes} (tried first, reverted) has no way to tell "genuinely empty"
     * apart from "a sibling's real content, just momentarily hidden for its own animation."
     *
     * <p>Fix: for any horizontal neighbor position that reads as air, check whether an adjacent
     * Roller Door's own control block sits directly above it (assumes same-height placement, the
     * normal case for a row of doors) and, if so, whether that door's {@link #activeSlides()} has an
     * in-flight record destined for that exact depth -- if so, treat that record's own state as the
     * real neighbor instead of the transient air, exactly as if it had already committed.
     */
    private static BlockState bakeConnectedShape(ServerLevel level, BlockPos pos, BlockPos controlPos, BlockState state) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = resolveLogicalNeighborState(level, neighborPos, controlPos);
            result = result.updateShape(level, level, pos, direction, neighborPos, neighborState, level.getRandom());
        }
        return result;
    }

    private static BlockState resolveLogicalNeighborState(ServerLevel level, BlockPos neighborPos, BlockPos controlPos) {
        BlockState real = level.getBlockState(neighborPos);
        // A hidden-mid-animation position reads as BARRIER now (see beginStep's own doc for why --
        // it's not literal air), so it needs the same "could be a sibling's real content" treatment
        // air always got here, or this substitution would silently stop firing the instant BARRIER
        // was introduced, reintroducing the exact flicker this method was built to fix.
        if (!real.isAir() && !real.is(Blocks.BARRIER)) {
            return real;
        }
        BlockPos neighborControlPos = new BlockPos(neighborPos.getX(), controlPos.getY(), neighborPos.getZ());
        int neighborDepth = controlPos.getY() - neighborPos.getY();
        if (neighborDepth <= 0 || neighborControlPos.equals(neighborPos)
                || !(level.getBlockEntity(neighborControlPos) instanceof RollerDoorBlockEntity neighborDoor)) {
            return real;
        }
        for (SlideRecord record : neighborDoor.activeSlides()) {
            if (record.destDepth() == neighborDepth) {
                return record.state();
            }
        }
        return real;
    }

    /**
     * Hides every real block currently in the chain (so only the animated ghost shows) and records
     * the step.
     *
     * <p>Hides to {@code Blocks.BARRIER}, not literal air -- real bug, found live: a plain-air hide
     * left nothing solid there for the whole ~0.5s step, so a player could simply walk straight
     * through a door mid-open/close despite the visible ghost sliding through that exact space (the
     * ghost is a pure rendering primitive, confirmed against the real decompiled {@code
     * MovingBlockRenderState}/{@code SubmitNodeCollector#submitMovingBlock} -- no collision box of
     * any kind). Vanilla's own pistons solve this with a dedicated moving-block placeholder whose
     * collision shape is itself interpolated by animation progress; a barrier is a much simpler
     * stand-in that gets the same practical result (invisible, fully solid) without a second new
     * Block/BlockEntity pair and its own shape-interpolation logic. See {@link
     * #resolveLogicalNeighborState}'s own doc for the one other place this had to be accounted for.
     */
    private void beginStep(ServerLevel level, BlockPos controlPos, boolean closing, List<SlideRecord> records) {
        BuildyngBlocksMod.LOGGER.info("[RollerDoor DEBUG] {} beginStep closing={} chainLength={} slides={}",
                controlPos, closing, chainLength, records.size());
        for (int depth = 1; depth <= chainLength; depth++) {
            level.setBlock(controlPos.below(depth), Blocks.BARRIER.defaultBlockState(), 3);
        }
        animating = true;
        animatingClosing = closing;
        activeSlides = records;
        animStartGameTime = level.getGameTime();
        setChanged();
    }

    /**
     * Applies every real final block state (already the correct, neighbor-aware connection shape --
     * see {@link #tryBeginExtendStep}/{@link #tryBeginRetractStep}, which compute it once when the
     * step begins, not here) and the matching storage/chainLength mutation.
     */
    private void commitStep(ServerLevel level, BlockPos controlPos) {
        for (SlideRecord record : activeSlides) {
            if (record.destDepth() > 0) {
                BlockPos commitPos = controlPos.below(record.destDepth());
                level.setBlock(commitPos, record.state(), 3);
                BuildyngBlocksMod.LOGGER.info("[RollerDoor DEBUG] {} commit depth={} intended={} actuallyPersisted={}",
                        controlPos, record.destDepth(), record.state(), level.getBlockState(commitPos));
            }
        }
        // A retract step shrinks the chain -- every depth beyond the new, shorter chainLength was
        // hidden to BARRIER by beginStep but never gets a shifted record of its own (nothing moves
        // INTO a position outside the chain), so the placement loop above never touches it. Real bug,
        // found live: without this, the chain's old deepest position stayed BARRIER forever once
        // retracted past it -- an invisible, solid, permanently impassible block exactly where the
        // chain used to hang. A no-op for an extend step (pendingChainLength > chainLength there, so
        // this range is empty).
        for (int depth = pendingChainLength + 1; depth <= chainLength; depth++) {
            level.setBlock(controlPos.below(depth), Blocks.AIR.defaultBlockState(), 3);
        }
        if (animatingClosing) {
            if (pendingSlotToConsume >= 0) {
                storage.getItem(pendingSlotToConsume).shrink(1);
                originSlots.push(pendingSlotToConsume);
            }
        } else if (!originSlots.isEmpty()) {
            int slotIndex = originSlots.pop();
            returnToSlot(slotIndex, pendingRemovedState);
        }
        BuildyngBlocksMod.LOGGER.info("[RollerDoor DEBUG] {} commitStep done: chainLength {} -> {}",
                controlPos, chainLength, pendingChainLength);
        chainLength = pendingChainLength;
        animating = false;
        activeSlides = List.of();
        setChanged();
    }

    private int findTopOccupiedSlot() {
        for (int i = SLOT_COUNT - 1; i >= 0; i--) {
            if (!storage.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void returnToSlot(int slotIndex, BlockState removed) {
        Item item = removed.getBlock().asItem();
        ItemStack existing = storage.getItem(slotIndex);
        if (existing.isEmpty()) {
            storage.setItem(slotIndex, new ItemStack(item, 1));
        } else {
            existing.grow(1);
        }
    }

    /**
     * Self-heals a stray {@code Blocks.BARRIER} left behind by an interrupted step (see {@link
     * #beginStep}'s own doc for why barrier is used to hide a mid-animation chain) -- confirmed real,
     * found live: a step that never reaches {@link #commitStep} (a client/server disconnect mid-slide,
     * etc.) leaves one behind, and without this, the next extend attempt saw it as a real permanent
     * obstruction and retried forever, every tick, indefinitely -- the door looked permanently stuck.
     * A barrier in this exact column can only ever be our own leftover (nothing else in normal play
     * ever places one directly below a Roller Door's own control block), so it's always safe to clear
     * and treat the position as open, no player action needed.
     */
    private boolean canOccupy(Level level, BlockPos pos) {
        if (pos.getY() < level.getMinY()) {
            return false;
        }
        BlockState existing = level.getBlockState(pos);
        if (existing.is(Blocks.BARRIER)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return true;
        }
        return existing.isAir() || existing.canBeReplaced();
    }

    /** Snap-retracts and drops everything -- called right before this block itself is broken. */
    public void dropEverythingAndDiscardChain(Level level, BlockPos controlPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (animating) {
            // Force any in-flight step to a consistent, fully-real state before computing drops --
            // the hidden (air) positions mid-animation have no real block to read otherwise.
            commitStep(serverLevel, controlPos);
        }
        for (int depth = 1; depth <= chainLength; depth++) {
            BlockPos cellPos = controlPos.below(depth);
            BlockState cellState = serverLevel.getBlockState(cellPos);
            if (!cellState.is(ModBlocks.ROLLER_DOOR_END_CAP.get())) {
                Containers.dropItemStack(serverLevel, cellPos.getX(), cellPos.getY(), cellPos.getZ(),
                        new ItemStack(cellState.getBlock().asItem()));
            }
            serverLevel.setBlock(cellPos, Blocks.AIR.defaultBlockState(), 3);
        }
        chainLength = 0;
        originSlots.clear();
        Containers.dropContents(serverLevel, controlPos, storage);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Roller Door");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RollerDoorMenu(ModMenus.ROLLER_DOOR.get(), containerId, inventory, storage, getBlockPos());
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(getBlockPos());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input.childOrEmpty("Storage"), storage.getItems());
        state = DoorState.valueOf(input.getStringOr("State", DoorState.OPEN.name()));
        commandedOpen = input.getBooleanOr("CommandedOpen", true);
        chainLength = input.getIntOr("ChainLength", 0);
        originSlots.clear();
        String joined = input.getStringOr("OriginSlots", "");
        if (!joined.isEmpty()) {
            for (String part : joined.split(",")) {
                originSlots.addLast(Integer.parseInt(part));
            }
        }
        animating = input.getBooleanOr("Animating", false);
        animatingClosing = input.getBooleanOr("AnimatingClosing", false);
        animStartGameTime = input.getLongOr("AnimStartGameTime", -1);
        activeSlides = input.listOrEmpty("ActiveSlides", SlideRecord.CODEC).stream().toList();
        pendingChainLength = input.getIntOr("PendingChainLength", 0);
        pendingSlotToConsume = input.getIntOr("PendingSlotToConsume", -1);
        pendingRemovedState = input.read("PendingRemovedState", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("Storage"), storage.getItems());
        output.putString("State", state.name());
        output.putBoolean("CommandedOpen", commandedOpen);
        output.putInt("ChainLength", chainLength);
        StringJoiner joiner = new StringJoiner(",");
        // Deque iteration order (head-to-tail) is top-of-stack-first -- reloaded via addLast in the
        // same order, so the stack's order is preserved exactly across a save/load round trip.
        for (Integer slot : originSlots) {
            joiner.add(String.valueOf(slot));
        }
        output.putString("OriginSlots", joiner.toString());
        // Also doubles as this BlockEntity's client-sync payload (see getUpdateTag/getUpdatePacket) --
        // the whole reason these fields are here at all, not just for disk persistence. See this
        // class's own doc for the real bug this fixed: without syncing this, the client's copy of
        // animating/activeSlides never reliably updated, so the slide-animation renderer had almost
        // nothing real to draw on most frames.
        output.putBoolean("Animating", animating);
        output.putBoolean("AnimatingClosing", animatingClosing);
        output.putLong("AnimStartGameTime", animStartGameTime);
        ValueOutput.TypedOutputList<SlideRecord> slides = output.list("ActiveSlides", SlideRecord.CODEC);
        for (SlideRecord record : activeSlides) {
            slides.add(record);
        }
        output.putInt("PendingChainLength", pendingChainLength);
        output.putInt("PendingSlotToConsume", pendingSlotToConsume);
        output.store("PendingRemovedState", BlockState.CODEC, pendingRemovedState);
    }

    /**
     * Pushes a real network sync to tracked clients on every state change, not just a local save-dirty
     * mark -- confirmed real bug, found live 2026-11-09: {@code BlockEntity#setChanged()} alone never
     * sends anything over the network by default ({@code getUpdatePacket()}/{@code getUpdateTag(...)}
     * both no-op unless overridden), so the client's own {@code RollerDoorBlockEntity} copy almost
     * never actually received {@code animating}/{@code activeSlides} updates -- the slide-animation
     * renderer (already confirmed correctly positioned and never frustum-culled, see its own doc)
     * still had nothing real to draw most of the time. {@code onDataPacket}/{@code handleUpdateTag}
     * don't need separate overrides -- their NeoForge defaults already route into {@link
     * #loadAdditional}, matching {@link #getUpdateTag} routing into {@link #saveAdditional} on the
     * send side (both via the base {@code BlockEntity}'s own {@code saveCustomOnly}/{@code
     * loadWithComponents}).
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState blockState = getBlockState();
            serverLevel.sendBlockUpdated(getBlockPos(), blockState, blockState, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
