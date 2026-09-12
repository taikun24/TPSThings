package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.menus.ExtendedCraftingMenu;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Tpsthings.MODID);
    public static final RegistryObject<MenuType<ExtendedCraftingMenu>> EXTENDED_CRAFTING
            = MENU_TYPES.register("extended_crafting", () -> new MenuType<>(ExtendedCraftingMenu::new, FeatureFlagSet.of()));
    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}
