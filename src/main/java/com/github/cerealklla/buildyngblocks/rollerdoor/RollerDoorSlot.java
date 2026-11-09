package com.github.cerealklla.buildyngblocks.rollerdoor;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Rejects any non-block item. Must override {@code mayPlace} here, not just {@code
 * Container#canPlaceItem} -- vanilla's plain {@link Slot#mayPlace} defaults to unconditionally
 * {@code true} and never consults the backing Container on its own (confirmed precedent:
 * Settlemynts' {@code guardhouse.GuardhouseFoodSlot}, which hit this exact bug first).
 */
public class RollerDoorSlot extends Slot {

    public RollerDoorSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }
}
