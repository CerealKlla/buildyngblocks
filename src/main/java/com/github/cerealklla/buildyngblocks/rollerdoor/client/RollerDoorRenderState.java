package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import java.util.List;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** One entry per currently-animating chain block -- see {@code RollerDoorBlockEntityRenderer}'s own doc. */
public class RollerDoorRenderState extends BlockEntityRenderState {

    public List<Entry> entries = List.of();

    public static final class Entry {
        public MovingBlockRenderState block;
        public float yOffset;
    }
}
