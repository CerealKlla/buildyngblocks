package com.github.cerealklla.buildyngblocks.rollerdoor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

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

    /**
     * {@code instanceof BlockItem} alone isn't enough -- live-reported bug: saplings and leaf
     * litter (and single-layer snow, the only placeable snow state) are all real {@code
     * BlockItem}s but have NO collision at all, confirmed against their real decompiled
     * registrations in {@code Blocks.java} (both {@code .noCollision()}; snow's own {@code
     * SnowLayerBlock#getCollisionShape} is a zero-height box at its default 1-layer state) -- an
     * entity can walk straight through them, which is wrong for something meant to become a solid
     * hanging door segment. Explicitly NOT tightened to "a full 1x1x1 cube" (tried first, then
     * corrected per explicit request) -- that would also reject fences and iron bars, which this
     * door is explicitly meant to support despite only having a thin center-post collision shape
     * (confirmed via their shared {@code CrossCollisionBlock} base, neither of which calls {@code
     * .noCollision()} either). "Has any real collision at all" is the actual dividing line between
     * the two groups.
     */
    public static boolean isValidCargo(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return isValidCargo(stack);
    }

    /**
     * Capped to 1 per slot -- the door's chain order must be visually obvious (one cargo block per
     * slot, in slot order) rather than hidden inside a stack count. {@code RollerDoorMenu
     * #quickMoveStack} relies on this same cap to spread a shift-clicked stack across multiple empty
     * slots instead of piling it into one.
     */
    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
