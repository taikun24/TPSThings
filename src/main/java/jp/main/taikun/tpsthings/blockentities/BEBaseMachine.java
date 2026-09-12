package jp.main.taikun.tpsthings.blockentities;

import mekanism.api.IConfigurable;
import mekanism.api.providers.IBlockProvider;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BEBaseMachine extends TileEntityMekanism implements IConfigurable {

    public BEBaseMachine(IBlockProvider blockProvider, BlockPos pos, BlockState state){
        super(blockProvider, pos, state);
    }

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onRightClick(Player player) {
        return openGui(player);
    }
}
