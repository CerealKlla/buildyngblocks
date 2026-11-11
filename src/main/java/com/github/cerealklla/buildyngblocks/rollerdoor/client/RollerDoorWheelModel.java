package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * v2 control-block visual: a wheel the hanging chain wraps around, in place of v1's plain stone
 * cube. Built from a real {@code ModelPart}/{@code LayerDefinition} hierarchy (not a static JSON
 * blockstate model, which can't animate at all) -- same technique Caryvan's own
 * {@code WagonModel}/{@code HandCartModel} use for their rotating cart wheels (confirmed against
 * that real, compiling code for this exact MC/NeoForge version before writing this, not guessed),
 * and the same {@code ModelPart}/{@code PartPose} coordinate convention vanilla's own
 * {@code BellModel} uses inside a {@code BlockEntityRenderer} specifically (0-16 block-space units,
 * Y-up from the block's bottom -- confirmed against its real decompiled source; this is NOT the
 * inverted Y-down convention an {@code EntityModel} uses).
 *
 * <p>Roundness is approximated the same way Caryvan's wagon wheels are -- Minecraft block models
 * have no true curves, so a wheel is really 8 thin rim segments and 8 thin spokes, each pre-rotated
 * by a fixed {@code i * 45deg} around the shared axle (the X axis, so the wheel's flat face reads
 * east/west -- a horizontal roller spanning the doorway, matching a real garage door's own roller
 * mechanism). {@link #wheelRoot()} is the one part whose {@code xRot} the renderer sets every frame
 * to make the whole assembly actually spin -- the per-segment {@code 45deg} rotations baked in here
 * are fixed layout, not the animated one.
 *
 * <p>{@link #chainGroup()} is four small "X-cross" chain-link accents (the same two-crossed-thin-
 * planes technique vanilla's own {@code template_chain} block model uses, confirmed against its
 * real decompiled source) parented separately from {@link #wheelRoot()} so they do NOT spin with
 * the wheel -- a chain draped over a turning wheel stays roughly fixed in space while the wheel
 * surface moves under it, not the other way around.
 *
 * <p>Deliberately not a texture-sheet-mapped {@code Model<T>} -- every box's own UV is a flat
 * {@code texOffs(0, 0)} against a declared 16x16 canvas, matching the real pixel size of the two
 * existing vanilla textures this reuses (iron_block for the shaft, oak_planks for the chain
 * "paddles" -- explicit request, metal shaft/wood slats) with no custom art needed; which texture
 * actually gets bound is chosen per submitted part at render time (see
 * {@code RollerDoorBlockEntityRenderer#submit}), the same per-part-texture-at-submit-time pattern
 * Caryvan's own {@code WagonRenderer} uses for its own wheels (different wood types, same model).
 */
public final class RollerDoorWheelModel {

    private static final float TWO_PI_EIGHTH = (float) (Math.PI / 4.0);

    private final ModelPart wheelRoot;
    private final ModelPart chainGroup;

    public RollerDoorWheelModel(ModelPart root) {
        this.wheelRoot = root.getChild("wheel_root");
        this.chainGroup = root.getChild("chain_group");
    }

    public ModelPart wheelRoot() {
        return wheelRoot;
    }

    public ModelPart chainGroup() {
        return chainGroup;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition wheelRoot = root.addOrReplaceChild(
                "wheel_root", CubeListBuilder.create(), PartPose.offset(8f, 8f, 8f));
        wheelRoot.addOrReplaceChild(
                "hub", CubeListBuilder.create().texOffs(0, 0).addBox(-2f, -2f, -2f, 4f, 4f, 4f),
                PartPose.offset(0f, 0f, 0f));
        // A horizontal axle rod spanning the FULL block width (X: 0 to 16 relative to the pivot at
        // X=8, i.e. -8 to 8) -- explicit request: a roller door placed directly next to another
        // should visually read as one continuous mechanism, with each block's own shaft reaching the
        // full east/west edges so adjacent blocks' shafts line up and appear to connect.
        wheelRoot.addOrReplaceChild(
                "axle", CubeListBuilder.create().texOffs(0, 0).addBox(-8f, -1.5f, -1.5f, 16f, 3f, 3f),
                PartPose.offset(0f, 0f, 0f));
        for (int i = 0; i < 8; i++) {
            float angle = TWO_PI_EIGHTH * i;
            wheelRoot.addOrReplaceChild(
                    "rim_" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-2f, 4f, -0.5f, 4f, 3f, 1f),
                    PartPose.offsetAndRotation(0f, 0f, 0f, angle, 0f, 0f));
            wheelRoot.addOrReplaceChild(
                    "spoke_" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, 2f, -0.5f, 1f, 2f, 1f),
                    PartPose.offsetAndRotation(0f, 0f, 0f, angle, 0f, 0f));
        }

        PartDefinition chainGroup = root.addOrReplaceChild(
                "chain_group", CubeListBuilder.create(), PartPose.offset(0f, 0f, 0f));
        addChainLink(chainGroup, "chain_top", 8f, 15.5f, 8f);
        addChainLink(chainGroup, "chain_bottom", 8f, 0.5f, 8f);
        addChainLink(chainGroup, "chain_north", 8f, 8f, 0.5f);
        addChainLink(chainGroup, "chain_south", 8f, 8f, 15.5f);

        return LayerDefinition.create(mesh, 16, 16);
    }

    /** One "X-cross" link (two thin perpendicular planes, both rotated 45deg around Y together) centered at (x, y, z). */
    private static void addChainLink(PartDefinition parent, String name, float x, float y, float z) {
        CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1f, -1.5f, -0.5f, 2f, 3f, 1f)
                .addBox(-0.5f, -1.5f, -1f, 1f, 3f, 2f);
        parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(x, y, z, 0f, TWO_PI_EIGHTH, 0f));
    }
}
