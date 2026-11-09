package com.github.cerealklla.buildyngblocks.registration;

import com.github.cerealklla.buildyngblocks.BuildyngBlocksMod;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, BuildyngBlocksMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RollerDoorMenu>> ROLLER_DOOR = MENU_TYPES.register(
            "roller_door",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) ->
                    new RollerDoorMenu(null, windowId, inventory, extraData.readBlockPos())));
}
