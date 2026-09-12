package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.items.ItemPrism;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * ツールチップのタイトル行 (とロア行) を 1 文字ずつ sin で上下させながら描く。
 * 色と難読化は ItemPrism.decorateChar と同じもの (波が加わるだけ)。
 */
public class WaveTitleTooltip implements ClientTooltipComponent {
    /** タイトル行の拡大率 (日本語が細く見えるため)。名前以外は等倍 */
    public static final float TITLE_SCALE = 1.25F;
    /** 波の高さ (px)。行高に収まる程度にしておく */
    public static final float AMPLITUDE = 1.5F;
    /** 波の速さ (rad/秒) */
    public static final float SPEED = 6.0F;
    /** 文字ごとの位相差 (rad)。大きいほど波が細かくなる */
    public static final float PHASE = 0.55F;

    private final String text;
    private final float scale;

    public WaveTitleTooltip(ItemPrism.WaveName wave) {
        this.text = wave.text();
        this.scale = wave.scale();
    }

    @Override
    public int getHeight() {
        return (int) Math.ceil(10 * scale);
    }

    @Override
    public int getWidth(Font font) {
        return (int) Math.ceil(font.width(text) * scale);
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
        Matrix4f scaled = new Matrix4f(matrix).scale(scale);
        float time = Util.getMillis() / 1000.0F;
        float advance = x;
        for (int i = 0; i < text.length(); i++) {
            Component ch = ItemPrism.decorateChar(text.charAt(i), i, time);
            float waveY = y + Mth.sin(time * SPEED + i * PHASE) * AMPLITUDE;
            // 行列側を scale 倍している分、渡す座標は割っておく (見た目の位置は advance, waveY のまま)
            font.drawInBatch(ch.getVisualOrderText(), advance / scale, waveY / scale, 0xFFFFFFFF, true,
                    scaled, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            advance += font.width(ch) * scale;
        }
    }
}
