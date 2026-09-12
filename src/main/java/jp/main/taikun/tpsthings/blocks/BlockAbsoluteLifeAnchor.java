package jp.main.taikun.tpsthings.blocks;

import jp.main.taikun.tpsthings.blockentities.BEAbsoluteLifeAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BlockAbsoluteLifeAnchor extends Block implements EntityBlock {
    public BlockAbsoluteLifeAnchor() {
        super(Block.Properties.of().strength(Integer.MAX_VALUE, Integer.MAX_VALUE).requiresCorrectToolForDrops().noOcclusion().isRedstoneConductor((state, world, pos) -> false));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new BEAbsoluteLifeAnchor(p_153215_, p_153216_);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_153212_, BlockState p_153213_, BlockEntityType<T> p_153214_) {
        return (level, pos, p_155255_, p_155256_) -> {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof   BEAbsoluteLifeAnchor myBE) {
                myBE.tick();
            }
        };
    }
}
