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

import java.util.List;

/**
 * 「一部だけ波打つ」ツールチップ行。wavy セグメント (Infinity) は
 * 縦揺れ控えめ + ピンク寄りレインボー、それ以外は指定色の静的テキストで描く。
 */
public class WaveSegmentsTooltip implements ClientTooltipComponent {
    /** Infinity 用: 縦揺れ控えめ */
    private static final float AMPLITUDE = 0.8F;
    /** Infinity 用: 波の速さ (rad/秒)。タイトルよりゆっくり = 周期が長い */
    private static final float SPEED = 2.0F;
    /** Infinity 用: ピンクへのブレンド率 (0..1) */
    private static final float PINK_MIX = 0.55F;

    private final List<ItemPrism.WaveSegments.Segment> segments;

    public WaveSegmentsTooltip(ItemPrism.WaveSegments line) {
        this.segments = line.segments();
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public int getWidth(Font font) {
        int width = 0;
        for (ItemPrism.WaveSegments.Segment segment : segments) {
            width += font.width(segment.text());
        }
        return width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
        float time = Util.getMillis() / 1000.0F;
        float advance = x;
        for (ItemPrism.WaveSegments.Segment segment : segments) {
            if (segment.wavy()) {
                String text = segment.text();
                for (int i = 0; i < text.length(); i++) {
                    Component ch = ItemPrism.decorateChar(text.charAt(i), i, time, PINK_MIX);
                    float waveY = y + Mth.sin(time * SPEED + i * WaveTitleTooltip.PHASE) * AMPLITUDE;
                    font.drawInBatch(ch.getVisualOrderText(), advance, waveY, 0xFFFFFFFF, true,
                            matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    advance += font.width(ch);
                }
            } else {
                font.drawInBatch(segment.text(), advance, y, 0xFF000000 | segment.color(), true,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                advance += font.width(segment.text());
            }
        }
    }
}
