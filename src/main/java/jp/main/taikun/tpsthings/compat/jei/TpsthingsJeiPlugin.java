package jp.main.taikun.tpsthings.compat.jei;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.blockentities.BEAnnihilationChamber;
import jp.main.taikun.tpsthings.registries.ModBlocks;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** JEI 連携。JEI が入っているときだけ JEI 側から読み込まれる。 */
@JeiPlugin
public class TpsthingsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AnnihilationCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(AnnihilationCategory.TYPE, BEAnnihilationChamber.reactions());
        // GUI が無いので、使い方は説明ページで伝える
        registration.addItemStackInfo(new ItemStack(ModBlocks.ANNIHILATION_CHAMBER.get()),
                Component.translatable("jei.tpsthings.annihilation.info"));
        // おおは作業台では作れない。儀式の手順も説明ページで伝える
        List<Component> ritual = new ArrayList<>();
        ritual.add(Component.translatable("jei.tpsthings.ritual.info"));
        List<Supplier<Item>> steps = BEAnnihilationChamber.ritual();
        for (int i = 0; i < steps.size(); i++) {
            ritual.add(Component.translatable("jei.tpsthings.ritual.step", i + 1, steps.get(i).get().getDescription()));
        }
        registration.addItemStackInfo(new ItemStack(ModItems.OO.get()), ritual.toArray(Component[]::new));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.ANNIHILATION_CHAMBER.get(), AnnihilationCategory.TYPE);
    }
}
