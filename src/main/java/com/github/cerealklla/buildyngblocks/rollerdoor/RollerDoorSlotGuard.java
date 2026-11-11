package com.github.cerealklla.buildyngblocks.rollerdoor;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;

/**
 * Closes a real vanilla loophole around {@link RollerDoorSlot#mayPlace} -- confirmed via the real
 * decompiled {@code AbstractContainerMenu#tryItemClickBehaviourOverride}: a "stack this carried item
 * onto the one already in the slot" click (the mechanism a vanilla Bundle, or this suite's own
 * {@code lyfe.fishing.LockedChestItem}, uses to pour/insert without going through the ordinary
 * pickup/place path) is dispatched via {@code Item#overrideStackedOnOther}/
 * {@code #overrideOtherStackedOnMe} BEFORE {@code Slot#mayPlace} is ever consulted -- a slot's own
 * placement restriction is simply never in the loop for that path. NeoForge's {@link
 * ItemStackedOnOtherEvent} fires immediately before both of those, specifically so a mod can plug
 * this hole; cancelling it here reproduces the exact same "do nothing" outcome a `mayPlace() ==
 * false` would have produced on the normal path.
 */
public final class RollerDoorSlotGuard {

    private RollerDoorSlotGuard() {
    }

    @SubscribeEvent
    public static void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        if (!(event.getSlot() instanceof RollerDoorSlot)) {
            return;
        }
        ItemStack carried = event.getCarriedItem();
        if (!carried.isEmpty() && !RollerDoorSlot.isValidCargo(carried)) {
            event.setCanceled(true);
        }
    }
}
