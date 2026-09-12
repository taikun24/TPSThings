package jp.main.taikun.tpsthings.time;

import jp.main.taikun.tpsthings.registries.ModBlocks;
import jp.main.taikun.tpsthings.mixin.AccessorTileEntityMultiblock;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class TickUtil {
    public static final List<Block> BLACKLIST = List.of(
            ModBlocks.TIME_ACCELERATOR.getBlock(),
            ModBlocks.DEBUG_ACCELERATOR.get()
    );
    public static void tick(Level level, BlockPos blockPos, int repeatCount) {
        BlockState blockState = level.getBlockState(blockPos);
        Block block  = blockState.getBlock();
        if (!(block instanceof EntityBlock entityBlock)){
            return;
        }
        if (BLACKLIST.contains(block)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity != null) {
            // if it's multiblock
            BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) entityBlock.getTicker(level, blockState, blockEntity.getType());
            if (ticker == null) return;
            if (blockEntity instanceof TileEntityMultiblock<?> multiblock) {
                var data = multiblock.getMultiblock();
                if (!data.isFormed())return;
                boolean isMaster = multiblock.isMaster();
                var multiTile = (AccessorTileEntityMultiblock) multiblock;
                multiTile.setIsMaster(true);
                for (int  i = 0; i < repeatCount; i++) {
                    if (blockEntity.isRemoved()) break;
                    ticker.tick(level, blockPos, blockState, blockEntity);
                }
                multiTile.setIsMaster(isMaster);
                return;
            }

            // else
            for (int i = 0; i < repeatCount; i++)
                ticker.tick(level, blockPos, blockState, blockEntity);
        }
    }
    public static void randomTick(Level level, BlockPos blockPos, int repeatCount) {
        if (level.isClientSide) return;
        BlockState blockState = level.getBlockState(blockPos);
        for (int i = 0; i < repeatCount; i++) {
            blockState.randomTick((ServerLevel) level, blockPos, level.getRandom());
        }
    }
}
