package com.github.cerealklla.buildyngblocks;

import com.github.cerealklla.buildyngblocks.registration.ModMenus;
import com.github.cerealklla.buildyngblocks.rollerdoor.client.RollerDoorScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BuildyngBlocksMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BuildyngBlocksMod.MODID, value = Dist.CLIENT)
public class BuildyngBlocksModClient {

    @net.neoforged.bus.api.SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROLLER_DOOR.get(), RollerDoorScreen::new);
    }
}
