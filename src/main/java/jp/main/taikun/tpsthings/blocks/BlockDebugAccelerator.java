package jp.main.taikun.tpsthings.blocks;

import jp.main.taikun.tpsthings.registries.ModBlockEntityTypes;
import jp.main.taikun.tpsthings.blockentities.BEDebugAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BlockDebugAccelerator extends Block implements EntityBlock {
    public BlockDebugAccelerator() {
        super(Block.Properties.copy(net.minecraft.world.level.block.Blocks.IRON_BLOCK));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player p_60506_, InteractionHand p_60507_, BlockHitResult blockHitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Item item = p_60506_.getItemInHand(p_60507_).getItem();
        if (item == Items.STICK || item == Items.DIRT) {
            BEDebugAccelerator accelerator = (BEDebugAccelerator) level.getBlockEntity(pos);
            if (accelerator != null) {
                int result = accelerator.changeSpeed(item == Items.DIRT);
                p_60506_.displayClientMessage(net.minecraft.network.chat.Component.literal("Speed changed into: " + result), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new BEDebugAccelerator(p_153215_, p_153216_);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_153212_, BlockState p_153213_, BlockEntityType<T> type) {
        return type == ModBlockEntityTypes.DEBUG_ACCELERATOR.get() ? BEDebugAccelerator::tick : null;
    }
}
