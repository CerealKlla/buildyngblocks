package com.github.cerealklla.buildyngblocks;

import com.github.cerealklla.buildyngblocks.registration.ModBlockEntities;
import com.github.cerealklla.buildyngblocks.registration.ModMenus;
import com.github.cerealklla.buildyngblocks.rollerdoor.client.ModRollerDoorModelLayers;
import com.github.cerealklla.buildyngblocks.rollerdoor.client.RollerDoorBlockEntityRenderer;
import com.github.cerealklla.buildyngblocks.rollerdoor.client.RollerDoorScreen;
import com.github.cerealklla.buildyngblocks.rollerdoor.client.RollerDoorWheelModel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BuildyngBlocksMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BuildyngBlocksMod.MODID, value = Dist.CLIENT)
public class BuildyngBlocksModClient {

    @net.neoforged.bus.api.SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROLLER_DOOR.get(), RollerDoorScreen::new);
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ROLLER_DOOR.get(), RollerDoorBlockEntityRenderer::new);
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModRollerDoorModelLayers.WHEEL, RollerDoorWheelModel::createLayer);
    }
}
