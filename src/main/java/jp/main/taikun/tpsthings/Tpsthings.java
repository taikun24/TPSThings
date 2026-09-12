package jp.main.taikun.tpsthings;

import com.mojang.logging.LogUtils;
import jp.main.taikun.tpsthings.network.ModNetwork;
import jp.main.taikun.tpsthings.registries.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.*;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Tpsthings.MODID)
public class Tpsthings {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "tpsthings";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    /** クリエイティブタブの翻訳キー。タブ自身の getId() はここでは触れない (自己参照になる) */
    public static final String TAB_KEY = "itemGroup." + MODID;
    public static final DeferredRegister<CreativeModeTab> MOD_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> TAB = MOD_TABS.register("tpsthings",
            () -> CreativeModeTab.builder()
                    .icon(Items.DIAMOND::getDefaultInstance)
                    .title(Component.translatable(TAB_KEY))
                    .displayItems((param, output) -> {
                        // output.accept(ModBlocks.EXAMPLE_MACHINE.getBlock());
                        // output.accept(ModBlocks.TIME_FLUX_COLLECTOR.getBlock());
                        // output.accept(ModBlocks.TIME_ACCELERATOR.getBlock());
                        output.accept(ModBlocks.DEBUG_ACCELERATOR.get());
                        output.accept(ModBlocks.TIME_ACCELERATOR.getBlock());
                        output.accept(ModBlocks.TPS_GENERATOR.getBlock());
                        output.accept(ModBlocks.LAG_GENERATOR.getBlock());
                        output.accept(ModItems.TIME_FLUX_CRYSTAL.get());
                        output.accept(ModItems.QOL.get());
                        output.accept(ModItems.QOLER.get());
                        output.accept(ModItems.QOLEST.get());
                        output.accept(ModItems.HOPE_BIO_FUEL.get());
                        output.accept(ModItems.HOPE_PELLET.get());
                        output.accept(ModItems.HOPE_SHEET.get());
                        output.accept(ModItems.HOPE_SUBSTRATE.get());
                        output.accept(ModItems.SPICY_SPICE.get());
                        output.accept(ModBlocks.ANNIHILATION_CHAMBER.get());
                        output.accept(ModItems.ANTI_HOPE_SHEET.get());
                        output.accept(ModItems.UNDEFINED_SHARD.get());
                        output.accept(ModItems.FROZEN_TICK.get());
                        output.accept(ModItems.INVULNERABLE_FLAG.get());
                        output.accept(ModItems.UNDEFINED_BEHAVIOR.get());
                        output.accept(ModItems.CANCELLED_EVENT.get());
                        output.accept(ModItems.RAW_HEALTH.get());
                        output.accept(ModItems.LYING_READER.get());
                        output.accept(ModItems.MIXIN.get());
                        output.accept(ModItems.DEATH_HOOK.get());
                        output.accept(ModItems.REMOVAL_VETO.get());
                        output.accept(ModItems.COREMOD.get());
                        output.accept(ModItems.ERASED_INDEX.get());
                        output.accept(ModItems.JAVA_AGENT.get());
                        output.accept(ModItems.OO.get());
                        output.accept(ModItems.NOT_OO.get());
                        output.accept(ModItems.TUNA.get());
                        output.accept(ModItems.TEACUP.get());
                        output.accept(ModItems.FLUORESCENT_LIGHT.get());
                        output.accept(ModItems.CAT_TEASER.get());
                        output.accept(ModItems.ACCELERATION_WAND.get());
                        output.accept(ModItems.PRISM.get());
                    })
                    .build());


    public Tpsthings(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModBlocks.register(modEventBus);
        ModBlockEntityTypes.register(modEventBus);
        ModGases.register(modEventBus);
        ModContainerTypes.register(modEventBus);
        ModItems.register(modEventBus);
        ModFluids.register(modEventBus);
        ModFluidTypes.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        MOD_TABS.register(modEventBus);
        ModNetwork.register();

        modEventBus.addListener(this::registerProviders);
    }
    private static void trackGenerated(ExistingFileHelper existingFileHelper, ResourceLocation rl) {
        existingFileHelper.trackGenerated(
                rl,
                PackType.CLIENT_RESOURCES, ".png", "textures"
        );
    }
    private static ResourceLocation rl(String k, String v) {
        return ResourceLocation.fromNamespaceAndPath(k, v);
    }
    private void registerProviders(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        ExistingFileHelper helper = event.getExistingFileHelper();
        trackGenerated(helper, rl("mekanism", "item/bio_fuel"));
        trackGenerated(helper, rl("mekanism", "item/substrate"));
        trackGenerated(helper, rl("mekanism", "item/hdpe_pellet"));
        trackGenerated(helper, rl("mekanism", "item/hdpe_sheet"));


        event.getGenerator().addProvider(event.includeClient(), new ModLangs.JA(output));
        event.getGenerator().addProvider(event.includeClient(), new ModLangs.EN(output));
        event.getGenerator().addProvider(event.includeClient(), new ModItemModelProvider(output, helper));
        event.getGenerator().addProvider(event.includeClient(), new ModBlockStateProvider(output, helper));
    }
}