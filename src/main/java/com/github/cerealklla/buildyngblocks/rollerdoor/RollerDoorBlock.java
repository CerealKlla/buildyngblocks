package com.github.cerealklla.buildyngblocks.rollerdoor;

import java.util.HashSet;
import java.util.Set;

import com.github.cerealklla.buildyngblocks.registration.ModBlockEntities;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Roller Door's control block -- v1 visual is a plain stone cube per spec (v2's wheel-and-chain
 * look is separate, explicit future work). See {@link RollerDoorBlockEntity} for the real chain/
 * state-machine mechanics; this class is just the Block-level glue: redstone detection + neighbor
 * propagation (powering one Roller Door opens/closes every horizontally-adjacent one too, per
 * explicit spec), the 10-tick step ticker, shift-right-click to open the storage GUI, and
 * break-cleanup.
 */
public class RollerDoorBlock extends Block implements EntityBlock {

    public static final MapCodec<RollerDoorBlock> CODEC = simpleCodec(RollerDoorBlock::new);

    public RollerDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RollerDoorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ROLLER_DOOR.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<RollerDoorBlockEntity>) (lvl, pos, st, door) -> {
            if (lvl instanceof ServerLevel serverLevel) {
                door.serverTick(serverLevel, pos);
            }
        };
    }

    /**
     * Redstone-on = commanded Open (see {@link RollerDoorBlockEntity}'s own doc for why). Also
     * flood-fills the commanded state to every horizontally-adjacent Roller Door, visited-set
     * guarded so a loop of doors converges once -- "powering one opens/closes the whole connected
     * row," per explicit spec.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                    Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof RollerDoorBlockEntity doorEntity)) {
            return;
        }
        boolean poweredOpen = level.hasNeighborSignal(pos);
        applyCommand(level, pos, doorEntity, poweredOpen, new HashSet<>());
    }

    private static void applyCommand(Level level, BlockPos pos, RollerDoorBlockEntity doorEntity, boolean open, Set<BlockPos> visited) {
        if (!visited.add(pos) || doorEntity.commandedOpen() == open) {
            return;
        }
        doorEntity.setCommandedOpen(open);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof RollerDoorBlockEntity neighborEntity) {
                applyCommand(level, neighborPos, neighborEntity, open, visited);
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof RollerDoorBlockEntity doorEntity)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.SUCCESS;
        }
        if (doorEntity.doorState() != RollerDoorBlockEntity.DoorState.OPEN) {
            player.sendSystemMessage(Component.literal("The Roller Door must be fully open to access its storage."));
            return InteractionResult.SUCCESS_SERVER;
        }
        serverPlayer.openMenu(doorEntity);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.getBlockEntity(pos) instanceof RollerDoorBlockEntity doorEntity) {
            doorEntity.dropEverythingAndDiscardChain(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
