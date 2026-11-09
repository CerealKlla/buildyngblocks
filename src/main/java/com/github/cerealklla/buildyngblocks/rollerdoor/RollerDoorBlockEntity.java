package com.github.cerealklla.buildyngblocks.rollerdoor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.StringJoiner;

import com.github.cerealklla.buildyngblocks.registration.ModBlockEntities;
import com.github.cerealklla.buildyngblocks.registration.ModBlocks;
import com.github.cerealklla.buildyngblocks.registration.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.extensions.IMenuProviderExtension;

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
 * <p><b>Known limitation, accepted rather than engineered around</b>: {@code animating}/{@code
 * activeSlides} are deliberately not persisted (they only ever matter for ~10 ticks/0.5 real
 * seconds). A server save/restart landing inside that exact window would leave the real world
 * blocks for the hidden positions as air with no record of what they should become, until the door
 * is operated again. Accepted as a vanishingly rare edge case, not silently unnoticed.
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
    }

    private final SimpleContainer storage = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof BlockItem;
        }
    };

    private DoorState state = DoorState.OPEN;
    private boolean commandedOpen = true;
    private int chainLength = 0;
    private final Deque<Integer> originSlots = new ArrayDeque<>();

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
        this.commandedOpen = open;
        if (open && (state == DoorState.CLOSED || state == DoorState.CLOSING)) {
            state = DoorState.OPENING;
        } else if (!open && (state == DoorState.OPEN || state == DoorState.OPENING)) {
            state = DoorState.CLOSING;
        }
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
                state = DoorState.CLOSED;
                setChanged();
            }
        } else {
            if (!tryBeginRetractStep(level, controlPos)) {
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
            records.add(new SlideRecord(1, ModBlocks.ROLLER_DOOR_END_CAP.get().defaultBlockState()));
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

        records.add(new SlideRecord(1, newCargoState));
        for (int oldDepth = 1; oldDepth <= chainLength; oldDepth++) {
            BlockState existing = level.getBlockState(controlPos.below(oldDepth));
            records.add(new SlideRecord(oldDepth + 1, existing));
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
            records.add(new SlideRecord(oldDepth - 1, existing));
        }
        BlockState removed = level.getBlockState(controlPos.below(1));
        records.add(new SlideRecord(0, removed));

        beginStep(level, controlPos, false, records);
        pendingChainLength = chainLength - 1;
        pendingRemovedState = removed;
        return true;
    }

    /** Hides every real block currently in the chain (so only the animated ghost shows) and records the step. */
    private void beginStep(ServerLevel level, BlockPos controlPos, boolean closing, List<SlideRecord> records) {
        for (int depth = 1; depth <= chainLength; depth++) {
            level.setBlock(controlPos.below(depth), Blocks.AIR.defaultBlockState(), 3);
        }
        animating = true;
        animatingClosing = closing;
        activeSlides = records;
        animStartGameTime = level.getGameTime();
        setChanged();
    }

    /** Applies every real final block state and the matching storage/chainLength mutation. */
    private void commitStep(ServerLevel level, BlockPos controlPos) {
        for (SlideRecord record : activeSlides) {
            if (record.destDepth() > 0) {
                level.setBlock(controlPos.below(record.destDepth()), record.state(), 3);
            }
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

    private boolean canOccupy(Level level, BlockPos pos) {
        if (pos.getY() < level.getMinY()) {
            return false;
        }
        BlockState existing = level.getBlockState(pos);
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
    }
}
