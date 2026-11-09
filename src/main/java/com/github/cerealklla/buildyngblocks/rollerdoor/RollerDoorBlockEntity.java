package com.github.cerealklla.buildyngblocks.rollerdoor;

import java.util.ArrayDeque;
import java.util.Deque;
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
 * might not be air/replaceable): pause this step and retry on the next tick window, piston-style.
 * Closing itself has no fixed target length -- it simply keeps extending every step for as long as
 * storage still has material and nothing commands it open, settling to {@code CLOSED} once storage
 * is empty.
 *
 * <p>Movement rate: 2 blocks/sec = one step per {@link #TICKS_PER_STEP} (10) game ticks. v1 commits
 * each step's final block state immediately (no slide animation yet) -- smoothing that into a real
 * interpolated slide is tracked as separate, explicit follow-up work.
 */
public class RollerDoorBlockEntity extends BlockEntity implements MenuProvider, IMenuProviderExtension {

    public static final int SLOT_COUNT = 27;
    private static final int TICKS_PER_STEP = 10;

    public enum DoorState {
        OPEN, OPENING, CLOSED, CLOSING
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
    private long nextStepGameTime = -1;

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
        if (level != null) {
            nextStepGameTime = level.getGameTime() + TICKS_PER_STEP;
        }
        setChanged();
    }

    public void serverTick(ServerLevel level, BlockPos controlPos) {
        if (state != DoorState.OPENING && state != DoorState.CLOSING) {
            return;
        }
        long gameTime = level.getGameTime();
        if (nextStepGameTime < 0) {
            nextStepGameTime = gameTime + TICKS_PER_STEP;
            return;
        }
        if (gameTime < nextStepGameTime) {
            return;
        }
        if (state == DoorState.CLOSING) {
            StepResult result = tryExtendStep(level, controlPos);
            if (result == StepResult.NO_MATERIAL) {
                state = DoorState.CLOSED;
                setChanged();
                return;
            }
        } else {
            tryRetractStep(level, controlPos);
            if (chainLength == 0) {
                state = DoorState.OPEN;
                setChanged();
                return;
            }
        }
        nextStepGameTime = gameTime + TICKS_PER_STEP;
        setChanged();
    }

    private enum StepResult {
        PROGRESSED, BLOCKED, NO_MATERIAL
    }

    private StepResult tryExtendStep(ServerLevel level, BlockPos controlPos) {
        if (chainLength == 0) {
            BlockPos target = controlPos.below(1);
            if (!canOccupy(level, target)) {
                return StepResult.BLOCKED;
            }
            level.setBlock(target, ModBlocks.ROLLER_DOOR_END_CAP.get().defaultBlockState(), 3);
            chainLength = 1;
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

        for (int depth = chainLength; depth >= 1; depth--) {
            BlockState moving = level.getBlockState(controlPos.below(depth));
            level.setBlock(controlPos.below(depth + 1), moving, 3);
        }
        level.setBlock(controlPos.below(1), newCargoState, 3);

        slotStack.shrink(1);
        originSlots.push(slotIndex);
        chainLength++;
        return StepResult.PROGRESSED;
    }

    private void tryRetractStep(ServerLevel level, BlockPos controlPos) {
        if (chainLength == 0) {
            return;
        }
        BlockState removed = level.getBlockState(controlPos.below(1));
        for (int depth = 1; depth < chainLength; depth++) {
            BlockState moving = level.getBlockState(controlPos.below(depth + 1));
            level.setBlock(controlPos.below(depth), moving, 3);
        }
        level.setBlock(controlPos.below(chainLength), Blocks.AIR.defaultBlockState(), 3);

        if (!originSlots.isEmpty()) {
            int slotIndex = originSlots.pop();
            returnToSlot(slotIndex, removed);
        }
        chainLength--;
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
