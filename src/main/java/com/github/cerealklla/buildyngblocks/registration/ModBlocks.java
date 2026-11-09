package com.github.cerealklla.buildyngblocks.registration;

import com.github.cerealklla.buildyngblocks.BuildyngBlocksMod;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlock;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorEndCapBlock;

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

    // v1 visual is a plain stone cube per spec -- the real wheel-and-chain look is explicit future
    // work (v2), not built now.
    public static final DeferredBlock<RollerDoorBlock> ROLLER_DOOR = BLOCKS.register(
            "roller_door",
            id -> new RollerDoorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F)
                    .sound(SoundType.STONE)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredItem<BlockItem> ROLLER_DOOR_ITEM = ITEMS.register(
            "roller_door",
            id -> new BlockItem(ROLLER_DOOR.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    // The chain's permanent leading/trailing cap -- indestructible, never player-obtainable, so
    // deliberately has no BlockItem. Only RollerDoorBlockEntity ever places or removes one.
    public static final DeferredBlock<RollerDoorEndCapBlock> ROLLER_DOOR_END_CAP = BLOCKS.register(
            "roller_door_end_cap",
            id -> new RollerDoorEndCapBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.STONE)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));
}
