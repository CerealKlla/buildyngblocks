package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the 1+ chain blocks currently sliding by exactly one position (see {@code
 * RollerDoorBlockEntity}'s own doc for the two-phase begin/commit step model this draws the
 * in-between state of). Built on vanilla's own real moving-block primitive -- {@code
 * MovingBlockRenderState} + {@code SubmitNodeCollector#submitMovingBlock} -- confirmed against the
 * real decompiled {@code PistonHeadRenderer}/{@code PistonHeadRenderState} source for this exact MC
 * version (26.1.2) before writing this, not guessed: that class is vanilla's own equivalent "render
 * an arbitrary BlockState at a translated offset" renderer (a piston head/moving block), and this
 * mirrors its {@code createRenderState}/{@code extractRenderState}/{@code submit} shape and its
 * {@code poseStack.translate(...)} + {@code submitMovingBlock(...)} pattern exactly.
 *
 * <p>Each {@link RollerDoorRenderState.Entry#block}'s own {@code blockPos} is the slide's real
 * final (destination) position -- used only for biome/lighting lookups, matching
 * {@code PistonHeadRenderer}'s own convention of keying a moving block's light/tint off where it's
 * headed, not where it currently renders. {@code yOffset} is the translation applied on top of
 * that: {@code direction * (1 - progress)}, so it starts at a full 1-block displacement and
 * shrinks to 0 exactly as the step's 10-tick duration completes.
 */
public class RollerDoorBlockEntityRenderer implements BlockEntityRenderer<RollerDoorBlockEntity, RollerDoorRenderState> {

    @Override
    public RollerDoorRenderState createRenderState() {
        return new RollerDoorRenderState();
    }

    @Override
    public void extractRenderState(RollerDoorBlockEntity blockEntity, RollerDoorRenderState state, float partialTicks,
                                    Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.entries = List.of();
        if (!blockEntity.isAnimating() || !(blockEntity.getLevel() instanceof ClientLevel level)) {
            return;
        }
        boolean closing = blockEntity.isAnimatingClosing();
        float progress = blockEntity.animationProgress(level.getGameTime(), partialTicks);
        float yOffset = (closing ? 1f : -1f) * (1f - progress);
        BlockPos controlPos = blockEntity.getBlockPos();

        List<RollerDoorRenderState.Entry> entries = new ArrayList<>();
        for (RollerDoorBlockEntity.SlideRecord record : blockEntity.activeSlides()) {
            BlockPos destPos = controlPos.below(Math.max(record.destDepth(), 0));
            RollerDoorRenderState.Entry entry = new RollerDoorRenderState.Entry();
            entry.block = createMovingBlock(destPos, record.state(), level);
            entry.yOffset = yOffset;
            entries.add(entry);
        }
        state.entries = entries;
    }

    @Override
    public void submit(RollerDoorRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        for (RollerDoorRenderState.Entry entry : state.entries) {
            poseStack.pushPose();
            poseStack.translate(0.0, entry.yOffset, 0.0);
            submitNodeCollector.submitMovingBlock(poseStack, entry.block);
            poseStack.popPose();
        }
    }

    private static MovingBlockRenderState createMovingBlock(BlockPos pos, BlockState blockState, ClientLevel level) {
        MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
        movingBlockRenderState.randomSeedPos = pos;
        movingBlockRenderState.blockPos = pos;
        movingBlockRenderState.blockState = blockState;
        movingBlockRenderState.biome = level.getBiome(pos);
        movingBlockRenderState.cardinalLighting = level.cardinalLighting();
        movingBlockRenderState.lightEngine = level.getLightEngine();
        return movingBlockRenderState;
    }
}
