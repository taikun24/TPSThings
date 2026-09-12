package jp.main.taikun.tpsthings.blocks;

import jp.main.taikun.tpsthings.blockentities.BEAnnihilationChamber;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 対消滅炉。上に材料を落とすと {@link BEAnnihilationChamber} が反応させる。
 * 投入口を持たないのは、GUI を挟むと「投げ込む」以外の意味が出てしまうため。
 */
public class BlockAnnihilationChamber extends Block implements EntityBlock {
    public BlockAnnihilationChamber() {
        super(Block.Properties.of().strength(50.0F, 1200.0F).requiresCorrectToolForDrops());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BEAnnihilationChamber(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BEAnnihilationChamber chamber) chamber.tick();
        };
    }
}
