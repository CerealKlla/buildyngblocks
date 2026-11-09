package com.github.cerealklla.buildyngblocks.registration;

import com.github.cerealklla.buildyngblocks.BuildyngBlocksMod;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BuildyngBlocksMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RollerDoorBlockEntity>> ROLLER_DOOR =
            BLOCK_ENTITIES.register("roller_door",
                    () -> new BlockEntityType<>(RollerDoorBlockEntity::new, ModBlocks.ROLLER_DOOR.get()));
}
