package com.github.cerealklla.buildyngblocks.rollerdoor.pathfinding;

import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;

/**
 * Finds the {@link RollerDoorBlockEntity} (if any) whose chain is currently physically occupying a
 * given position -- the shared lookup both {@link RollerDoorNodeEvaluator} (pathfinding) and
 * {@link RollerDoorOpenGoal} (the open/close trigger) need.
 *
 * <p>A control block's chain always hangs directly below it, so "is {@code pos} blocked by a Roller
 * Door" is answered by scanning straight up from {@code pos}: the first {@link
 * RollerDoorBlockEntity} found owns this column, and it's currently blocking {@code pos} exactly
 * when {@code pos}'s depth below that control block is within its own {@code chainLength()} --
 * which, since {@code chainLength} is only ever mutated at {@code commitStep}, is correct both at
 * rest (CLOSED) and mid-animation (OPENING/CLOSING): {@code beginStep} always hides exactly
 * {@code [1, chainLength]} to {@code Blocks.BARRIER} regardless of direction, so the field's
 * pre-step value already equals the real currently-blocking extent throughout the whole step, not
 * just once it settles.
 *
 * <p>Bounded to {@link #MAX_SEARCH_DEPTH} rather than a full world-height scan -- a Roller Door
 * taller than that is an extreme, unlikely case accepted as out of scope here rather than building
 * a dedicated world-wide position index for it; the design doesn't configure any chain-length cap
 * at all, so an unbounded scan would have to walk the whole world height on every miss (the
 * overwhelmingly common case for a mob with no Roller Door anywhere near it).
 */
public final class RollerDoorPathTypes {

    private static final int MAX_SEARCH_DEPTH = 48;

    private RollerDoorPathTypes() {
    }

    public static @Nullable RollerDoorBlockEntity findBlockingDoor(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int depth = 1; depth <= MAX_SEARCH_DEPTH; depth++) {
            cursor.setY(pos.getY() + depth);
            if (level.getBlockEntity(cursor) instanceof RollerDoorBlockEntity door) {
                return depth <= door.chainLength() ? door : null;
            }
        }
        return null;
    }
}
