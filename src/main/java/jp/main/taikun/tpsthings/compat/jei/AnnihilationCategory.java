package jp.main.taikun.tpsthings.compat.jei;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.blockentities.BEAnnihilationChamber;
import jp.main.taikun.tpsthings.registries.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 対消滅炉の反応。左に材料、右に抽選結果とその確率を並べる。
 *
 * <p>対消滅炉は GUI もレシピ JSON も持たず、反応はブロックエンティティの中に定義されている。
 * ここはその定義 ({@link BEAnnihilationChamber#reactions()}) をそのまま読んで描くだけ。
 */
public class AnnihilationCategory implements IRecipeCategory<BEAnnihilationChamber.Reaction> {

    public static final RecipeType<BEAnnihilationChamber.Reaction> TYPE =
            RecipeType.create(Tpsthings.MODID, "annihilation", BEAnnihilationChamber.Reaction.class);

    /** 材料は最大 3 つ、結果も最大 3 つ並ぶ前提の大きさ。 */
    private static final int WIDTH = 150;
    private static final int ROW = 20;
    private static final int HEIGHT = ROW * 3;
    private static final int SLOT = 18;
    private static final int ARROW_X = 60;
    private static final int OUTPUT_X = 88;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    public AnnihilationCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(ModBlocks.ANNIHILATION_CHAMBER.get()));
        this.slot = gui.getSlotDrawable();
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public @NotNull RecipeType<BEAnnihilationChamber.Reaction> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.translatable("jei.tpsthings.annihilation");
    }

    @Override
    public @NotNull IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull BEAnnihilationChamber.Reaction recipe,
                          @NotNull IFocusGroup focuses) {
        List<BEAnnihilationChamber.Need> inputs = recipe.inputs();
        int inputY = (HEIGHT - SLOT) / 2 + 1;
        for (int i = 0; i < inputs.size(); i++) {
            BEAnnihilationChamber.Need need = inputs.get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + i * (SLOT + 1), inputY)
                    .setBackground(slot, -1, -1)
                    .addItemStack(new ItemStack(need.item().get(), need.count()));
        }

        List<BEAnnihilationChamber.Outcome> outcomes = recipe.outcomes();
        int top = outputTop(outcomes);
        for (int i = 0; i < outcomes.size(); i++) {
            BEAnnihilationChamber.Outcome outcome = outcomes.get(i);
            // 「何も出ない」はスロットを置かず、draw で文字だけ描く
            if (outcome.item() == null || outcome.count() <= 0) {
                continue;
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, top + i * ROW + 1)
                    .setBackground(slot, -1, -1)
                    .addItemStack(new ItemStack(outcome.item().get(), outcome.count()));
        }
    }

    @Override
    public void draw(@NotNull BEAnnihilationChamber.Reaction recipe, @NotNull IRecipeSlotsView recipeSlotsView,
                     @NotNull GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, ARROW_X, (HEIGHT - arrow.getHeight()) / 2);

        Font font = Minecraft.getInstance().font;
        List<BEAnnihilationChamber.Outcome> outcomes = recipe.outcomes();
        int total = outcomes.stream().mapToInt(BEAnnihilationChamber.Outcome::weight).sum();
        int top = outputTop(outcomes);
        for (int i = 0; i < outcomes.size(); i++) {
            BEAnnihilationChamber.Outcome outcome = outcomes.get(i);
            int y = top + i * ROW + (SLOT - font.lineHeight) / 2 + 1;
            String chance = Math.round(outcome.weight() * 100.0F / total) + "%";
            if (outcome.item() == null || outcome.count() <= 0) {
                Component nothing = Component.translatable("jei.tpsthings.annihilation.nothing");
                guiGraphics.drawString(font, nothing, OUTPUT_X, y, 0x808080, false);
                guiGraphics.drawString(font, chance, OUTPUT_X + font.width(nothing) + 4, y, 0x404040, false);
            } else {
                guiGraphics.drawString(font, chance, OUTPUT_X + SLOT + 4, y, 0x404040, false);
            }
        }
    }

    /** 結果の列を縦に中央寄せしたときの一番上の y。 */
    private static int outputTop(List<BEAnnihilationChamber.Outcome> outcomes) {
        return (HEIGHT - outcomes.size() * ROW) / 2;
    }
}
