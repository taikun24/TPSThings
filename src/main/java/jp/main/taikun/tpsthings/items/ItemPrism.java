package jp.main.taikun.tpsthings.items;

import net.minecraft.Util;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * ツールチップシェーダーのテスト用アイテム。
 */
public class ItemPrism extends Item implements WavyNameItem {
    public ItemPrism() {
        super(new Properties());
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new ShaderTooltip());
    }

    /**
     * カスタム描画 (回転する八面体) を紐付ける。クライアント専用クラスへの参照は
     * この匿名クラスの中に閉じ込めること (サーバーでロードされないように)。
     */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return jp.main.taikun.tpsthings.gui.PrismItemRenderer.INSTANCE.get();
            }
        });
    }

    /**
     * 名前を 1 文字ずつ色付けした Component にして返す。
     * ツールチップ・インベントリ・持ち替え時の名前表示は毎フレームここを呼び直すので、
     * 時間を混ぜるだけでアニメーションになる。
     */
    @Override
    public Component getName(ItemStack stack) {
        return decorateName(this, stack);
    }

    /**
     * WavyNameItem 実装アイテムの getName から呼ぶ共通ヘルパー。
     */
    public static Component decorateName(Item item, ItemStack stack) {
        String text = Language.getInstance().getOrDefault(item.getDescriptionId(stack));
        float time = Util.getMillis() / 1000.0F;
        MutableComponent result = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            result.append(decorateChar(text.charAt(i), i, time));
        }
        return result;
    }

    /**
     * 共通側のマーカー。クライアント側の描画は ClientShaderTooltip が担当する。
     */
    public record ShaderTooltip() implements TooltipComponent {
    }

    /**
     * ツールチップのタイトル行 (とロア行) を波打つ自前描画に差し替えるためのマーカー。
     * scale はこの行だけの拡大率 (タイトル=1.25, ロア=1.0 など)。
     * クライアント側の描画は WaveTitleTooltip が担当する。
     */
    public record WaveName(String text, float scale) implements TooltipComponent {
    }

    /**
     * 「一部だけ波打つ」行のマーカー。wavy=true のセグメントは虹色+波打ち、
     * false のセグメントは color の色で普通に描かれる。
     * クライアント側の描画は WaveSegmentsTooltip が担当する。
     */
    public record WaveSegments(java.util.List<Segment> segments) implements TooltipComponent {
        public record Segment(String text, boolean wavy, int color) {
        }
    }

    /**
     * 1 文字ぶんの装飾 (虹色ウェーブ + たまに難読化)。getName と WaveTitleTooltip で共用。
     */
    public static Component decorateChar(char c, int index, float time) {
        return decorateChar(c, index, time, 0.0F);
    }

    /**
     * pinkMix > 0 で虹色をピンク (0xFF7FD4) 側にブレンドする (0.0=通常の虹色, 1.0=完全にピンク)。
     */
    public static Component decorateChar(char c, int index, float time, float pinkMix) {
        float hue = Mth.positiveModulo(time * 0.5F - index * 0.08F, 1.0F);
        int rainbow = Mth.hsvToRgb(hue, 0.7F, 1.0F);
        int color = pinkMix > 0.0F ? mixColor(rainbow, 0xFF7FD4, pinkMix) : rainbow;
        return Component.literal(String.valueOf(c))
                .withStyle(style -> style.withColor(color));
                        // .withObfuscated((index * 31 + (int) (time * 3.0F)) % 17 == 0));
    }

    private static int mixColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
