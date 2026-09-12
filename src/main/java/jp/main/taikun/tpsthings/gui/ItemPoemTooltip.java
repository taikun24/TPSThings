package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.items.ItemLayer;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * おお への系譜に並ぶ中間素材 ({@link ItemLayer}) のツールチップ末尾にポエムを添える。
 *
 * <p>本文は lang の「説明キー + .poem」に置く。
 */
@Mod.EventBusSubscriber(modid = Tpsthings.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemPoemTooltip {

    private ItemPoemTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        // ポエムを持つのは おお への系譜の中間素材だけ
        if (!(stack.getItem() instanceof ItemLayer)) {
            return;
        }
        String nameKey = stack.getDescriptionId();
        String poemKey = nameKey + ".poem";
        List<Component> tooltip = event.getToolTip();

        if (I18n.exists(poemKey)) {
            tooltip.add(Component.empty());
            for (String line : I18n.get(poemKey).split("\n")) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        } else if (!I18n.exists(nameKey)) {
            // 名前すら持たないもの (未定義の欠片) は、ポエムも生のキーのまま出す
            tooltip.add(Component.empty());
            tooltip.add(Component.literal(poemKey).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /** おおの独り言。シフトを押している間だけ、最後の行の上に 1 つ出る。 */
    private static final int OO_MURMUR_COUNT = 4;

    /**
     * 系譜を降りきった果ての言葉を、おおのツールチップの最後の行に置く。
     *
     * <p>属性の行は appendHoverText より後に足されるので、末尾に置くにはこのイベントを一番遅く受ける。
     * 最後の行は tooltip_oo シェーダーの白い帯と重なる。紫+斜体はロアとして波打ちに差し替わる。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOoTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ModItems.OO.get())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        if (Screen.hasShiftDown()) {
            int murmur = (int) (Util.getMillis() / 5000L % OO_MURMUR_COUNT);
            tooltip.add(Component.translatable("tooltip.tpsthings.oo.murmur." + murmur)
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        tooltip.add(Component.translatable("tooltip.tpsthings.oo")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
