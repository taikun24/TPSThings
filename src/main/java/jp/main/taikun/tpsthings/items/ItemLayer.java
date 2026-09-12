package jp.main.taikun.tpsthings.items;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * おお への系譜に並ぶ中間素材。
 *
 * <p>それぞれが貫通層 (表層〜索引層) を 1 つ受け持ち、ツールチップにはその層の深さに応じた
 * シェーダー (tpsthings:tooltip_layer) を被せる。深い層ほど表層の秩序が崩れて見える。
 */
public class ItemLayer extends Item {

    private final int layer;
    private final boolean foil;

    public ItemLayer(int layer, boolean foil, Properties properties) {
        super(properties);
        this.layer = layer;
        this.foil = foil;
    }

    public int layer() {
        return layer;
    }

    /**
     * 層の呼び名。
     *
     * <p>番号はレシピの順序と崩れ具合の計算に要るので内部には残すが、<b>表には名前しか出さない</b>。
     * 番号で呼ぶと層が単なる目盛りに見えて、降りていく感じが出ない。
     */
    public static Component layerName(int layer) {
        return Component.translatable("tpsthings.layer." + layer);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    /**
     * 名前が崩れ始める層の手前。生値層 (HP の直書き) から先は、人間の言葉を保てなくなっていく。
     */
    private static final int CORRUPTION_START = 5;

    /**
     * 深い層ほど、名前の文字が難読化で読めなくなる。崩れる文字の位置は時間で入れ替わる。
     *
     * <p>訳が無いとき (サーバー側、または名前を持たない未定義の欠片) は触らない。
     * 欠片は生の翻訳キーが出るのが正しい姿なので。
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        String key = getDescriptionId(stack);
        Language language = Language.getInstance();
        if (layer <= CORRUPTION_START || !language.has(key)) {
            return super.getName(stack);
        }
        String text = language.getOrDefault(key);
        // 生値層で 1 割弱、索引層で 4 割。深いほど速く入れ替わる
        float ratio = (layer - CORRUPTION_START) * 0.08F;
        long frame = Util.getMillis() / (400L - layer * 30L);
        MutableComponent name = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean broken = !Character.isWhitespace(c) && noise(i, frame) < ratio;
            name.append(Component.literal(String.valueOf(c)).withStyle(style -> style.withObfuscated(broken)));
        }
        return name;
    }

    /** 文字の位置とフレームから 0〜1 の値を決める。フレーム内では同じ文字が崩れ続ける。 */
    private static float noise(int index, long frame) {
        long h = index * 0x9E3779B97F4A7C15L + frame * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(layerName(layer).copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public @NotNull Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return Optional.of(new LayerTooltip(layer));
    }

    /** 共通側のマーカー。クライアント側の描画は ClientShaderTooltip が担当する。 */
    public record LayerTooltip(int layer) implements TooltipComponent {
    }
}
