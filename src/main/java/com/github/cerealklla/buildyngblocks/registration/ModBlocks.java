package com.github.cerealklla.buildyngblocks.registration;

import com.github.cerealklla.buildyngblocks.BuildyngBlocksMod;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BuildyngBlocksMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BuildyngBlocksMod.MODID);

    // v2's wheel-and-chain model doesn't fill the full cube -- noOcclusion() is required or
    // neighboring blocks wrongly cull their own touching face assuming full solid occlusion (vanilla
    // defaults every Block to reporting as a full opaque cube regardless of what its model actually
    // draws), leaving a visible gap. Real bug, found live via a screenshot.
    public static final DeferredBlock<RollerDoorBlock> ROLLER_DOOR = BLOCKS.register(
            "roller_door",
            id -> new RollerDoorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredItem<BlockItem> ROLLER_DOOR_ITEM = ITEMS.register(
            "roller_door",
            id -> new BlockItem(ROLLER_DOOR.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))));
}
