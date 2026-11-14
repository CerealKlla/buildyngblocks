package com.github.cerealklla.buildyngblocks;

import com.github.cerealklla.buildyngblocks.registration.ModBlockEntities;
import com.github.cerealklla.buildyngblocks.registration.ModBlocks;
import com.github.cerealklla.buildyngblocks.registration.ModMenus;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorSlotGuard;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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
        modEventBus.addListener(this::addCreativeTabItems);
        NeoForge.EVENT_BUS.addListener(RollerDoorSlotGuard::onItemStackedOnOther);
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("BuildyngBlocks common setup");
    }

    /*
     * Real bug, found 2026-11-14: the Roller Door's BlockItem was registered but never added to any
     * BuildCreativeModeTabContentsEvent tab -- same gap Blueprynts/TinyWyrlds both had and fixed for
     * their own hand-placed blocks (see their CLAUDE.md entries). Functional Blocks, same tab real
     * vanilla doors/trapdoors live in. The end cap has no BlockItem at all (never player-obtainable,
     * see ModBlocks) so there's nothing to add for it.
     */
    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            return;
        }
        event.accept(ModBlocks.ROLLER_DOOR_ITEM.get());
    }
}
