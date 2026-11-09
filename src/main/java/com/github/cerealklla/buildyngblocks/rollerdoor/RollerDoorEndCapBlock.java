package com.github.cerealklla.buildyngblocks.rollerdoor;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;

/**
 * The permanent leading/trailing cap of a Roller Door's chain -- always the deepest real block of
 * the currently-deployed chain (see {@link RollerDoorBlockEntity}'s own doc for the full chain
 * mechanics). Indestructible and never player-obtainable: placed/removed only by
 * {@link RollerDoorBlockEntity} itself, never via a {@code BlockItem} (none is registered).
 */
public class RollerDoorEndCapBlock extends Block {

    public static final MapCodec<RollerDoorEndCapBlock> CODEC = simpleCodec(RollerDoorEndCapBlock::new);

    public RollerDoorEndCapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
