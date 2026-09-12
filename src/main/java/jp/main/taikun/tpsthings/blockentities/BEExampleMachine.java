package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.registries.ModBlocks;
import mekanism.api.IConfigurable;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public class BEExampleMachine extends TileEntityMekanism implements IConfigurable {
    public BEExampleMachine(BlockPos pos, BlockState state) {
        super(ModBlocks.EXAMPLE_MACHINE, pos, state);
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