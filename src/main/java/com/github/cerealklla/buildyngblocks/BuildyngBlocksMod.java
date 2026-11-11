package com.github.cerealklla.buildyngblocks;

import com.github.cerealklla.buildyngblocks.registration.ModBlockEntities;
import com.github.cerealklla.buildyngblocks.registration.ModBlocks;
import com.github.cerealklla.buildyngblocks.registration.ModMenus;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorSlotGuard;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Central package for non-mod-specific custom blocks in the suite -- generic building/utility
 * blocks not tied to any one system (settlements, quests, economy, etc.), unlike every other mod
 * here. First block: the Roller Door (see {@code rollerdoor} package).
 */
@Mod(BuildyngBlocksMod.MODID)
public class BuildyngBlocksMod {

    public static final String MODID = "buildyngblocks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BuildyngBlocksMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(RollerDoorSlotGuard::onItemStackedOnOther);
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("BuildyngBlocks common setup");
    }
}
