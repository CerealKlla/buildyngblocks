package com.github.cerealklla.buildyngblocks.rollerdoor.pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * Drop-in replacement for the default {@code GroundPathNavigation} that makes {@link
 * RollerDoorNodeEvaluator} the mob's real pathfinding node evaluator instead of vanilla's plain
 * {@code WalkNodeEvaluator} -- the one override point {@code GroundPathNavigation} exposes for
 * this (confirmed against the real decompiled source: {@code createPathFinder} is exactly where
 * vanilla itself constructs {@code new WalkNodeEvaluator()}).
 *
 * <p>A mob also still needs {@code getNavigation().setCanOpenDoors(true)} (the same call any
 * vanilla door-opening mob already makes in its own constructor) for a closed Roller Door to
 * become passable-at-a-cost rather than a permanent wall -- this class only supplies the evaluator
 * that's capable of recognizing one at all; it doesn't change that flag's default.
 *
 * <p>A mob wanting this should override {@code Mob#createNavigation(Level)} to return an instance
 * of this instead of relying on the inherited default.
 */
public class RollerDoorAwareGroundNavigation extends GroundPathNavigation {

    public RollerDoorAwareGroundNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new RollerDoorNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}
