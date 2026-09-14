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
                    .icon(ModItems.NOT_OO.get()::getDefaultInstance)
                    .title(Component.translatable(TAB_KEY))
                    .displayItems((param, output) -> {
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
                        // おお への系譜。並びはレシピの順 (底の おおじゃないが から 9x9 の卓まで)
                        output.accept(ModItems.NOT_OO.get());
                        output.accept(ModItems.NULL_POINTER.get());
                        output.accept(ModItems.GLITCH.get());
                        output.accept(ModItems.GLITCH_SHARD.get());
                        output.accept(ModItems.COMPRESSED_CARDBOARD_BOX.get());
                        output.accept(ModItems.SUPER_COMPRESSED_CARDBOARD_BOX.get());
                        output.accept(ModItems.COMPRESSED_OREDICTIONIFICATOR.get());
                        output.accept(ModItems.SUPER_COMPRESSED_OREDICTIONIFICATOR.get());
                        output.accept(ModItems.UNDEFINED_BEHAVIOR.get());
                        output.accept(ModItems.BYTECODE.get());
                        output.accept(ModItems.JAVA.get());
                        output.accept(ModItems.C.get());
                        output.accept(ModItems.JVM.get());
                        output.accept(ModItems.MIXIN.get());
                        output.accept(ModItems.ASM.get());
                        output.accept(ModItems.COREMOD.get());
                        output.accept(ModItems.ATTACK_MODULE.get());
                        output.accept(ModItems.DEFENSE_MODULE.get());
                        output.accept(ModItems.SHADER.get());
                        output.accept(ModItems.BERWL.get());
                        output.accept(ModItems.PRISM.get());
                        output.accept(ModItems.SUGOI_MENU.get());
                        output.accept(ModItems.ENRICHED_NETHERITE.get());
                        output.accept(ModItems.ALLOY_TSUYOSUGI.get());
                        output.accept(ModItems.ALLOY_YABASUGI.get());
                        output.accept(ModItems.ALLOY_EGUSUGI.get());
                        output.accept(ModItems.ALLOY_OO.get());
                        output.accept(ModItems.INFINITY_INGOT.get());
                        output.accept(ModItems.ETERNITY_INGOT.get());
                        output.accept(ModItems.UNITY_INGOT.get());
                        output.accept(ModItems.ANTIMATTER_CHUNK.get());
                        output.accept(ModItems.ANTIMATTER_INGOT.get());
                        output.accept(ModBlocks.ANTIMATTER_BLOCK.get());
                        output.accept(ModBlocks.COMPRESSED_ANTIMATTER_BLOCK.get());
                        output.accept(ModItems.ANTIMATTER_SINGULARITY.get());
                        output.accept(ModItems.QIO_DRIVE_QUANTUM.get());
                        output.accept(ModItems.QIO_DRIVE_COSMIC.get());
                        output.accept(ModItems.QIO_DRIVE_ABSURD.get());
                        output.accept(ModItems.OO.get());
                        output.accept(ModItems.MODULE_OO_UNIT.get());
                        output.accept(ModItems.TUNA.get());
                        output.accept(ModItems.TEACUP.get());
                        output.accept(ModItems.FLUORESCENT_LIGHT.get());
                        output.accept(ModItems.ACCELERATION_WAND.get());
                    })
                    .build());


    public Tpsthings(FMLJavaModLoadingContext context) {
        // 専用サーバーでは登録より先に止める (Mixin の段階でも止めているが、そちらを通らない構成に備える)
        SingleplayerGate.refuseDedicatedServer();
        IEventBus modEventBus = context.getModEventBus();
        ModBlocks.register(modEventBus);
        ModBlockEntityTypes.register(modEventBus);
        ModGases.register(modEventBus);
        ModInfuseTypes.register(modEventBus);
        ModContainerTypes.register(modEventBus);
        ModItems.register(modEventBus);
        ModModules.register(modEventBus);
        ModFluids.register(modEventBus);
        ModFluidTypes.register(modEventBus);
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


        event.getGenerator().addProvider(event.includeClient(), new ModLangs.JA(output));
        event.getGenerator().addProvider(event.includeClient(), new ModLangs.EN(output));
        event.getGenerator().addProvider(event.includeClient(), new ModItemModelProvider(output, helper));
        event.getGenerator().addProvider(event.includeClient(), new ModBlockStateProvider(output, helper));
    }
}