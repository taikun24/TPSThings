package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.items.ItemPrism;
import jp.main.taikun.tpsthings.items.WavyNameItem;
import jp.main.taikun.tpsthings.mixin.AccessorGui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ホットバー持ち替え時に画面下へ出るアイテム名 (Gui.renderSelectedItemName) を、
 * プリズムのときだけキャンセルして波打ちアニメーション付きで描き直す。
 * 波のパラメータはツールチップ側 (WaveTitleTooltip) と揃えてある。
 */
@Mod.EventBusSubscriber(modid = Tpsthings.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaveItemNameOverlay {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.ITEM_NAME.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        AccessorGui gui = (AccessorGui) mc.gui;
        ItemStack stack = gui.tpsthings$getLastToolHighlight();
        if (!(stack.getItem() instanceof WavyNameItem)) {
            return;
        }
        event.setCanceled(true);

        int timer = gui.tpsthings$getToolHighlightTimer();
        if (stack.isEmpty() || timer <= 0) {
            return;
        }
        // vanilla と同じフェードアウト (残り 10 tick から薄くなる)
        int alpha = Math.min(255, (int) (timer * 256.0F / 10.0F));
        if (alpha <= 0) {
            return;
        }

        Font font = mc.font;
        String text = stack.getHoverName().getString();
        float time = Util.getMillis() / 1000.0F;

        // vanilla と同じ位置決め
        int width = font.width(text);
        int x = (event.getWindow().getGuiScaledWidth() - width) / 2;
        int y = event.getWindow().getGuiScaledHeight() - 59;
        if (mc.gameMode != null && !mc.gameMode.canHurtPlayer()) {
            y += 14;
        }

        GuiGraphics g = event.getGuiGraphics();
        g.fill(x - 2, y - 2, x + width + 2, y + 9 + 2, mc.options.getBackgroundColor(0));

        int fadedWhite = 0xFFFFFF | (alpha << 24);
        float advance = x;
        for (int i = 0; i < text.length(); i++) {
            Component ch = ItemPrism.decorateChar(text.charAt(i), i, time);
            float waveY = Mth.sin(time * 6.0F + i * 0.55F) * 1.5F;
            g.pose().pushPose();
            g.pose().translate(advance - x, waveY, 0);
            // スタイル色が RGB を上書きし、アルファは第 5 引数のものが使われる
            g.drawString(font, ch, x, y, fadedWhite, true);
            g.pose().popPose();
            advance += font.width(ch);
        }
    }
}
