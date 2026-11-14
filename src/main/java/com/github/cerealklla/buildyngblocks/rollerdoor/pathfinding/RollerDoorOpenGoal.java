package com.github.cerealklla.buildyngblocks.rollerdoor.pathfinding;

import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Opens (and closes behind) a closed/animating Roller Door a mob is physically pressed against --
 * same real-world trigger and "have I walked past it yet" dot-product shape as vanilla's own
 * {@code DoorInteractGoal}/{@code OpenDoorGoal} (real doors) and this suite's own {@code
 * GuardGateOpenGoal} (fence gates, which have no vanilla equivalent either). Deliberately reuses
 * the simpler "react to a collision, not a full path-node lookahead" shape {@code
 * GuardGateOpenGoal} already established as sufficient for "an NPC pressed against a closed
 * obstacle," rather than vanilla's fancier path-node scan.
 *
 * <p>One real difference from both of those precedents: a Roller Door's retract is a genuine
 * multi-tick animation ({@code chainLength * 10} ticks), not an instant single-tick toggle, so
 * this goal only ever commands the door open/closed ({@link
 * RollerDoorBlockEntity#setCommandedOpen}) -- it never assumes the doorway is immediately
 * passable the moment it starts opening. The mob simply keeps waiting (this goal stays active via
 * {@link #canContinueToUse}) until the chain has actually retracted far enough to clear, at which
 * point its own path-following resumes walking forward exactly as it would through a real open
 * door, and the dot-product check below marks the goal done once it's clearly past.
 *
 * <p>Needs {@link RollerDoorAwareGroundNavigation}'s custom {@link RollerDoorNodeEvaluator} to
 * matter for route *planning* -- without it, pathfinding never deliberately walks a mob toward a
 * closed Roller Door at all, the same accepted limitation already documented for fence gates. This
 * goal alone only rescues a mob that's already physically pressed against one (wandered there, or
 * committed to a path that assumed it was already open).
 */
public class RollerDoorOpenGoal extends Goal {

    private final Mob mob;
    private RollerDoorBlockEntity door;
    private BlockPos controlPos;
    private boolean passed;
    private float doorDirX;
    private float doorDirZ;

    public RollerDoorOpenGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (!mob.horizontalCollision) {
            return false;
        }
        RollerDoorBlockEntity found = findAdjacentBlockingDoor();
        if (found == null) {
            return false;
        }
        door = found;
        controlPos = found.getBlockPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !passed;
    }

    @Override
    public void start() {
        passed = false;
        doorDirX = (float) (controlPos.getX() + 0.5 - mob.getX());
        doorDirZ = (float) (controlPos.getZ() + 0.5 - mob.getZ());
        door.setCommandedOpen(true);
    }

    @Override
    public void stop() {
        if (door != null) {
            door.setCommandedOpen(false);
        }
        door = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        float newDirX = (float) (controlPos.getX() + 0.5 - mob.getX());
        float newDirZ = (float) (controlPos.getZ() + 0.5 - mob.getZ());
        float dot = doorDirX * newDirX + doorDirZ * newDirZ;
        if (dot < 0.0F) {
            passed = true;
        }
    }

    private RollerDoorBlockEntity findAdjacentBlockingDoor() {
        BlockPos base = mob.blockPosition();
        RollerDoorBlockEntity found = RollerDoorPathTypes.findBlockingDoor(mob.level(), base);
        if (found != null) {
            return found;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            found = RollerDoorPathTypes.findBlockingDoor(mob.level(), base.relative(direction));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
