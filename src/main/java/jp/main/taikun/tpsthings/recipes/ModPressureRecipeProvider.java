package jp.main.taikun.tpsthings.recipes;

import mekanism.api.chemical.gas.GasStack;
import mekanism.api.math.FloatingLong;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.recipe.impl.PressurizedReactionIRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ModPressureRecipeProvider extends PressurizedReactionIRecipe {
    public ModPressureRecipeProvider(ResourceLocation id, ItemStackIngredient inputSolid, FluidStackIngredient inputFluid, ChemicalStackIngredient.GasStackIngredient inputGas, FloatingLong energyRequired, int duration, ItemStack outputItem, GasStack outputGas) {
        super(id, inputSolid, inputFluid, inputGas, energyRequired, duration, outputItem, outputGas);
    }

}
