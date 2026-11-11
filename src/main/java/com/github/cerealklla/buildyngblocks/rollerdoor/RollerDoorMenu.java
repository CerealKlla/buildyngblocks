package com.github.cerealklla.buildyngblocks.rollerdoor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Standard vanilla 3-row-chest slot layout (27 slots), scaled from Settlemynts' GuardhouseFoodMenu. */
public class RollerDoorMenu extends AbstractContainerMenu {

    private static final int SLOT_COUNT = RollerDoorBlockEntity.SLOT_COUNT;
    private static final int ROWS = 3;
    private static final int COLS = 9;
    // Matches vanilla ChestMenu's own formula exactly (18 + rows*18 + 13) -- confirmed against its
    // real decompiled source, not guessed; the earlier 84 was off by one pixel against the real
    // generic_54.png texture's baked-in grid lines, see RollerDoorScreen's own doc for the full story.
    private static final int PLAYER_INV_Y = 18 + ROWS * 18 + 13;

    private final Container storage;
    private final BlockPos doorPos;

    public RollerDoorMenu(MenuType<?> type, int containerId, Inventory inventory, Container storage, BlockPos doorPos) {
        super(type, containerId);
        this.storage = storage;
        this.doorPos = doorPos;
        layoutSlots(inventory);
    }

    /** Client-side reconstruction -- no real block to read from yet; see {@code ModMenus.ROLLER_DOOR}. */
    public RollerDoorMenu(MenuType<?> type, int containerId, Inventory inventory, BlockPos doorPos) {
        this(type, containerId, inventory, new SimpleContainer(SLOT_COUNT), doorPos);
    }

    public BlockPos doorPos() {
        return doorPos;
    }

    private void layoutSlots(Inventory inventory) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new RollerDoorSlot(storage, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex < SLOT_COUNT) {
                if (!moveItemStackTo(stack, SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!distributeOneEach(stack)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return clicked;
    }

    /**
     * Every storage slot is capped to 1 item ({@code RollerDoorSlot#getMaxStackSize}), but vanilla's
     * own {@code moveItemStackTo} stops after filling just the FIRST empty slot it finds (confirmed
     * against its real decompiled source, not assumed) -- fine for a normal stack-based container,
     * wrong here: a shift-clicked stack of 6 would leave 5 behind in the player's inventory instead
     * of spreading one-per-slot across the storage grid the way the chain order needs to be visually
     * unambiguous. This keeps placing exactly 1 into each empty, placeable slot (in slot order) until
     * either the stack is exhausted or the storage is full.
     */
    private boolean distributeOneEach(ItemStack stack) {
        boolean anythingChanged = false;
        for (int i = 0; i < SLOT_COUNT && !stack.isEmpty(); i++) {
            Slot target = slots.get(i);
            if (target.getItem().isEmpty() && target.mayPlace(stack)) {
                target.setByPlayer(stack.split(1));
                target.setChanged();
                anythingChanged = true;
            }
        }
        return anythingChanged;
    }

    @Override
    public boolean stillValid(Player player) {
        return storage.stillValid(player);
    }
}
