package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import com.github.cerealklla.buildyngblocks.BuildyngBlocksMod;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/** Client-only -- never reference from a common-sourceset class (same rule as {@code Minecraft}/{@code LocalPlayer}). */
public final class ModRollerDoorModelLayers {

    public static final ModelLayerLocation WHEEL =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(BuildyngBlocksMod.MODID, "roller_door_wheel"), "main");

    private ModRollerDoorModelLayers() {
    }
}
