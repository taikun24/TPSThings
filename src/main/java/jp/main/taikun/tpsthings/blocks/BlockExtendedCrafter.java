package jp.main.taikun.tpsthings.blocks;

import jp.main.taikun.tpsthings.menus.ExtendedCraftingMenu;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public class BlockExtendedCrafter extends Block{
    private static final Component CONTAINER_TITLE = Component.translatable("container.crafting");
    public BlockExtendedCrafter() {
        super(BlockBehaviour.Properties.of().strength(2.5F).noOcclusion());
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(@NotNull BlockState bs, @NotNull Level level, @NotNull BlockPos bp, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult result) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            player.openMenu(bs.getMenuProvider(level, bp));
            return InteractionResult.CONSUME;
        }
    }
    public MenuProvider getMenuProvider(BlockState p_52240_, @NotNull Level p_52241_, @NotNull BlockPos p_52242_) {
        return new SimpleMenuProvider((id, inv, player) -> new ExtendedCraftingMenu(id, inv), CONTAINER_TITLE);
    }

}
