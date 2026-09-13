package jp.main.taikun.tpsthings.items;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * おお への系譜の素材のうち、ツールチップに専用シェーダーを被せるもの。
 * シェーダーは tpsthings:tooltip_&lt;shader&gt; で、絵柄はそれぞれの実績名から決めている。
 */
public class ItemMaterial extends Item {

    private final String shader;
    private final boolean foil;

    public ItemMaterial(String shader, boolean foil, Properties properties) {
        super(properties);
        this.shader = shader;
        this.foil = foil;
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public @NotNull Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return Optional.of(new MaterialTooltip(shader));
    }

    /** 共通側のマーカー。クライアント側の描画は ClientShaderTooltip が担当する。 */
    public record MaterialTooltip(String shader) implements TooltipComponent {
    }
}
