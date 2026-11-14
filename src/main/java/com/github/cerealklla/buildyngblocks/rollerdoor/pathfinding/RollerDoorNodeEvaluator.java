package com.github.cerealklla.buildyngblocks.rollerdoor.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Makes a closed/animating Roller Door's chain column read as a closed wooden door to vanilla's
 * own pathfinding machinery, instead of whatever arbitrary block (player-stored cargo, or the
 * hide-step's {@code Blocks.BARRIER}) happens to physically occupy it right now.
 *
 * <p>This can't be done the normal way -- overriding {@code BlockState#getBlockPathType} on the
 * Roller Door's own {@code Block} class -- because the blocked cells are arbitrary vanilla blocks
 * this mod doesn't own (whatever a player dropped into storage); only the control block itself is
 * really ours, and every closed chain depth below it is player-chosen cargo instead. A subclassed
 * evaluator is the one extension point that can still classify an *arbitrary* occupying block
 * based on where it physically is, not what class it is.
 *
 * <p>Overriding only {@link #getPathType} is sufficient to cover the whole pipeline: {@code
 * WalkNodeEvaluator#getPathTypeWithinMobBB}/{@code #getPathTypeOfMob} (inherited unchanged) both
 * call {@code this.getPathType(...)} polymorphically, so they automatically pick up this override,
 * including the existing {@code DOOR_WOOD_CLOSED -> WALKABLE_DOOR} translation ({@code
 * canOpenDoors}/{@code canPassDoors}, see {@code NodeEvaluator}) and the diagonal-movement checks
 * that already special-case {@code WALKABLE_DOOR} -- all of vanilla's existing door-aware
 * machinery works completely unchanged, it just never had a way to know a Roller Door could
 * produce that classification before. A genuinely open door (chain fully retracted) needs no
 * special case here at all -- those cells are real air already, handled by the normal fallthrough.
 *
 * <p>Confirmed against this exact MC version's real decompiled {@code WalkNodeEvaluator} source
 * before writing this, not guessed -- see {@code getPathTypeFromState}'s own hardcoded {@code
 * instanceof DoorBlock}/{@code FenceGateBlock} chain for why a subclass override, not a per-block
 * {@code getBlockPathType} hook, is the only extension point that can reach an arbitrary occupying
 * block at all. {@link RollerDoorPathTypes} is the shared lookup this and {@link
 * RollerDoorOpenGoal} both use.
 */
public class RollerDoorNodeEvaluator extends WalkNodeEvaluator {

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (RollerDoorPathTypes.findBlockingDoor(context.level(), pos) != null) {
            return PathType.DOOR_WOOD_CLOSED;
        }
        return super.getPathType(context, x, y, z);
    }
}
