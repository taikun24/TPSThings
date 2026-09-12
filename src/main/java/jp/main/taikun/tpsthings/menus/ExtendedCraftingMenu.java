package jp.main.taikun.tpsthings.menus;

import jp.main.taikun.tpsthings.registries.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ExtendedCraftingMenu extends AbstractContainerMenu  {
    public ExtendedCraftingMenu(int p_38852_, Inventory inventory) {
        this(p_38852_, inventory, ContainerLevelAccess.NULL);
    }

    public ExtendedCraftingMenu(int windowId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenuTypes.EXTENDED_CRAFTING.get(), windowId);
        for(int k = 0; k < 3; ++k) {
            for(int i1 = 0; i1 < 9; ++i1) {
                this.addSlot(new Slot(inventory, i1 + k * 9 + 9, 8 + i1 * 18, 84 + k * 18));
            }
        }

        for(int l = 0; l < 9; ++l) {
            this.addSlot(new Slot(inventory, l, 8 + l * 18, 142));
        }
    }
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player p_38941_, int p_38942_) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }
}
