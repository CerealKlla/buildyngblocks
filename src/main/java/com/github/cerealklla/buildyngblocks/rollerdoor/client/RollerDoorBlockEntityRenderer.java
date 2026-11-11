package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlock;
import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
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
 * headed, not where it currently renders (confirmed against the real decompiled
 * {@code ModelBlockRenderer#tesselateBlock} -- {@code blockPos} never feeds into actual vertex
 * position at all, only light/AO/tint sampling; render placement comes entirely from this
 * renderer's own {@code PoseStack} translate). {@link RollerDoorRenderState.Entry#translateY} is
 * therefore the FULL translate needed -- the block's real depth below the control block, PLUS the
 * fractional in-progress slide offset ({@code direction * (1 - progress)}, which starts at a full
 * 1-block displacement and shrinks to 0 exactly as the step's 10-tick duration completes). Missing
 * the depth component here (a real bug, found live via heavy flicker on any chain longer than one
 * block) left every sliding entry rendering stacked at nearly the same position instead of spread
 * across the shaft, z-fighting every frame.
 */
public class RollerDoorBlockEntityRenderer implements BlockEntityRenderer<RollerDoorBlockEntity, RollerDoorRenderState> {

    /** Radians/tick while actively animating -- a cosmetic constant, not tied to any real mechanical ratio. */
    private static final float WHEEL_RADIANS_PER_TICK = 0.26f;
    // Cached once, not rebuilt every submit() call -- real bug, found live via a screenshot showing
    // garbage/wrong texture data (not a clean "missing texture" checkerboard): the render pipeline
    // groups/batches draws by RenderType identity, and constructing a brand-new RenderType instance
    // every single frame broke that, leaving the actual texture binding in an undefined state.
    //
    // Path convention confirmed against real decompiled source (RenderTypes#entityCutout traced all
    // the way through RenderSetup/TextureManager/SimpleTexture -- no "textures/"/".png" is ever
    // appended internally, unlike a block-model texOffs reference into the stitched atlas): this
    // wants the FULL resource path, exactly like a vanilla entity texture
    // (DragonFireballRenderer/GuardianRenderer both pass "textures/entity/....png" verbatim). Passing
    // the bare atlas-style "block/iron_block" here was a real bug, found live via a screenshot showing
    // the purple/black missing-texture checkerboard -- it resolved to a nonexistent resource instead
    // of the real file at textures/block/iron_block.png.
    //
    // Shaft (hub/axle/rim/spokes) reads as iron, the hanging chain "paddles" read as wood -- explicit
    // request, matching a real roller shutter's own metal-shaft/wood-slat look.
    private static final RenderType WHEEL_RENDER_TYPE = RenderTypes.entityCutout(Identifier.withDefaultNamespace("textures/block/iron_block.png"));
    private static final RenderType CHAIN_RENDER_TYPE = RenderTypes.entityCutout(Identifier.withDefaultNamespace("textures/block/oak_planks.png"));

    private final RollerDoorWheelModel wheelModel;

    public RollerDoorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.wheelModel = new RollerDoorWheelModel(context.bakeLayer(ModRollerDoorModelLayers.WHEEL));
    }

    @Override
    public RollerDoorRenderState createRenderState() {
        return new RollerDoorRenderState();
    }

    @Override
    public void extractRenderState(RollerDoorBlockEntity blockEntity, RollerDoorRenderState state, float partialTicks,
                                    Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.entries = List.of();
        state.wheelRotation = 0f;
        state.shaftAlongZ = blockEntity.getBlockState().getValue(RollerDoorBlock.AXIS) == Direction.Axis.Z;
        if (!(blockEntity.getLevel() instanceof ClientLevel level)) {
            return;
        }
        // Spins continuously (keyed off absolute game time, not animStartGameTime, so it doesn't
        // visibly snap back to a fixed position at every individual step boundary while a multi-step
        // operation is still running) only while a step is actually in flight; rests at a fixed angle
        // the rest of the time. Accepted cosmetic limitation, not a bug: the wheel can visibly "jump"
        // to a different angle between two separate, non-contiguous operations, since nothing persists
        // the exact angle it stopped at -- not worth a new persisted field for a purely decorative spin.
        if (!blockEntity.isAnimating()) {
            return;
        }
        state.wheelRotation = (level.getGameTime() + partialTicks) * WHEEL_RADIANS_PER_TICK;
        boolean closing = blockEntity.isAnimatingClosing();
        float progress = blockEntity.animationProgress(level.getGameTime(), partialTicks);
        float yOffset = (closing ? 1f : -1f) * (1f - progress);
        BlockPos controlPos = blockEntity.getBlockPos();

        List<RollerDoorRenderState.Entry> entries = new ArrayList<>();
        for (RollerDoorBlockEntity.SlideRecord record : blockEntity.activeSlides()) {
            int depth = Math.max(record.destDepth(), 0);
            BlockPos destPos = controlPos.below(depth);
            // record.state() is already the correct, neighbor-aware connection shape -- computed
            // ONCE, server-side, when this step began (see RollerDoorBlockEntity#tryBeginExtendStep/
            // tryBeginRetractStep), not recomputed here. A real, worse regression was found live
            // trying the opposite (recomputing every frame against this *client's* live neighbor
            // data): with multiple adjacent Roller Doors, each door's own hide/show cycle isn't
            // synchronized with its neighbor's, so a live per-frame read kept alternating between
            // "neighbor is its real block" (connected) and "neighbor is mid-step, hidden as air"
            // (disconnected) -- constant flicker, worse than the single end-of-slide pop it replaced.
            RollerDoorRenderState.Entry entry = new RollerDoorRenderState.Entry();
            entry.block = createMovingBlock(destPos, record.state(), level);
            entry.translateY = -depth + yOffset;
            entries.add(entry);
        }
        state.entries = entries;
    }

    @Override
    public void submit(RollerDoorRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        ModelPart wheelRoot = wheelModel.wheelRoot();
        wheelRoot.xRot = state.wheelRotation;

        poseStack.pushPose();
        if (state.shaftAlongZ) {
            // The model is authored assuming the shaft runs along X (see RollerDoorWheelModel's own
            // doc) -- a 90deg yaw around the block's own center reorients the whole assembly to run
            // along Z instead, per RollerDoorBlock#AXIS, rather than duplicating the geometry.
            poseStack.translate(8.0 / 16.0, 0.0, 8.0 / 16.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            poseStack.translate(-8.0 / 16.0, 0.0, -8.0 / 16.0);
        }
        submitNodeCollector.submitModelPart(wheelRoot, poseStack, WHEEL_RENDER_TYPE,
                state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(wheelModel.chainGroup(), poseStack, CHAIN_RENDER_TYPE,
                state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();

        for (RollerDoorRenderState.Entry entry : state.entries) {
            poseStack.pushPose();
            poseStack.translate(0.0, entry.translateY, 0.0);
            submitNodeCollector.submitMovingBlock(poseStack, entry.block);
            poseStack.popPose();
        }
    }

    /**
     * NeoForge's default render bounding box is just a 1x1x1 cube at the control block's own
     * position ({@code IBlockEntityRendererExtension#getRenderBoundingBox}'s own default) -- but a
     * ghost here can render up to {@code chainLength} blocks below that. Without this override,
     * frustum culling (confirmed against the real decompiled {@code BlockEntityRenderDispatcher},
     * which skips extraction/submission entirely when the bounding box isn't in view) was dropping
     * this renderer almost every frame, only drawing on the rare frame the single 1x1x1 cell
     * happened to be in view -- a real bug, found live: the ghost appeared to flash for a fraction
     * of a second then vanish, with the real block popping in solid once the step committed.
     * Matches {@code PistonHeadRenderer}'s own exact precedent (confirmed against its decompiled
     * source), which has this same unbounded-movement problem and fixes it the same way.
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RollerDoorBlockEntity blockEntity) {
        return net.minecraft.world.phys.AABB.INFINITE;
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
