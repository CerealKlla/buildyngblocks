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
    private static final int PLAYER_INV_Y = 84;

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
            } else if (!moveItemStackTo(stack, 0, SLOT_COUNT, false)) {
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

    @Override
    public boolean stillValid(Player player) {
        return storage.stillValid(player);
    }
}
