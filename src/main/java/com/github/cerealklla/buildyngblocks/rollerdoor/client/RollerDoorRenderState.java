package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import java.util.List;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** One entry per currently-animating chain block -- see {@code RollerDoorBlockEntityRenderer}'s own doc. */
public class RollerDoorRenderState extends BlockEntityRenderState {

    public List<Entry> entries = List.of();
    /** Radians -- see {@code RollerDoorBlockEntityRenderer#extractRenderState}'s own doc for how this is computed. */
    public float wheelRotation;
    /** True when the shaft should run along Z instead of the model's natively-authored X axis -- see {@code RollerDoorBlock#AXIS}. */
    public boolean shaftAlongZ;

    public static final class Entry {
        public MovingBlockRenderState block;
        /**
         * The FULL vertical translate to apply, relative to the control block's own position --
         * {@code -depth} (the block's real resting position below the control block) PLUS the
         * fractional in-progress slide offset. See {@code RollerDoorBlockEntityRenderer}'s own doc
         * for why both pieces are needed: {@code MovingBlockRenderState.blockPos} only affects
         * lighting/tint/AO sampling, never actual render placement (confirmed against the real
         * decompiled {@code ModelBlockRenderer#tesselateBlock} -- it tesselates at local 0,0,0 and
         * relies entirely on the caller's own {@code PoseStack} for world position) -- a real bug,
         * found live 2026-11-09: every currently-sliding block in a chain was rendering stacked at
         * nearly the same position (only the small fractional slide amount apart, never by its real
         * depth), so a multi-block chain's ghosts z-fought every frame, reading as heavy flicker.
         */
        public float translateY;
    }
}
