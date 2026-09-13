package jp.main.taikun.tpsthings.items;

import mekanism.common.content.qio.IQIODriveItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * QIO ドライブ。Mekanism の {@code QIODriveTier} は enum なので継承して段を足せない。
 * ドライブとして扱われる条件は {@link IQIODriveItem} を実装していることだけなので、
 * 容量を自分で持つだけの実装をこちらに置く。
 */
public class ItemQioDrive extends Item implements IQIODriveItem {

    private final long countCapacity;
    private final int typeCapacity;

    public ItemQioDrive(long countCapacity, int typeCapacity) {
        super(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
        this.countCapacity = countCapacity;
        this.typeCapacity = typeCapacity;
    }

    @Override
    public long getCountCapacity(@NotNull ItemStack stack) {
        return countCapacity;
    }

    @Override
    public int getTypeCapacity(@NotNull ItemStack stack) {
        return typeCapacity;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.tpsthings.qio_drive.capacity",
                        Component.literal(String.valueOf(typeCapacity)),
                        Component.literal(String.valueOf(countCapacity)))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
