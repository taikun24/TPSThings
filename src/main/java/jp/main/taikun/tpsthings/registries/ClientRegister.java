package jp.main.taikun.tpsthings.registries;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.gui.ClientShaderTooltip;
import jp.main.taikun.tpsthings.gui.GuiExampleMachine;
import jp.main.taikun.tpsthings.items.ItemPrism;
import jp.main.taikun.tpsthings.blockentities.BELagGenerator;
import jp.main.taikun.tpsthings.blockentities.BETpsGenerator;
import jp.main.taikun.tpsthings.gui.GuiTimeAccelerator;
import jp.main.taikun.tpsthings.gui.GuiTimeFluxCollector;
import jp.main.taikun.tpsthings.gui.GuiTpsGenerator;
import jp.main.taikun.tpsthings.machines.BaseMachine;
import mekanism.client.ClientRegistrationUtil;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = Tpsthings.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientRegister {

    /** oo の元テクスチャモデル。BEWLR (PrismItemRenderer) がベースとして描く */
    public static final ResourceLocation OO_BASE_MODEL =
            ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "item/oo_base");

    @SubscribeEvent
    public static void onRegisterAdditionalModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(OO_BASE_MODEL);
    }

    private static ShaderInstance tooltipTestShader;

    public static ShaderInstance getTooltipTestShader() {
        return tooltipTestShader;
    }

    private static ShaderInstance tooltipLayerShader;

    public static ShaderInstance getTooltipLayerShader() {
        return tooltipLayerShader;
    }

    private static ShaderInstance tooltipOoShader;

    public static ShaderInstance getTooltipOoShader() {
        return tooltipOoShader;
    }

    /** Shadertoy 形式 (mainImage だけを書く) で読み込む fsh。前後のラッパーを注入する対象。 */
    private static final java.util.Set<String> SHADERTOY_FRAGMENTS =
            java.util.Set.of("tooltip_test.fsh", "tooltip_layer.fsh", "tooltip_oo.fsh");

    private static ShaderInstance sugoiMenuScreenShader;

    public static ShaderInstance getSugoiMenuScreenShader() {
        return sugoiMenuScreenShader;
    }

    /**
     * tooltip_test.fsh には Shadertoy のコードを丸ごと貼るだけでよい。
     * uniform 宣言とラッパー main() は読み込み時にここで注入する。
     */
    private static final String SHADERTOY_PRELUDE = """
            #version 150

            uniform vec3  iResolution;
            uniform float iTime;
            uniform float iTimeDelta;
            uniform float iFrameRate;
            uniform int   iFrame;
            uniform vec4  iMouse;
            uniform vec4  iDate;
            uniform float iSampleRate;
            uniform vec4  Rect;

            in vec4 vertexColor;
            in vec2 screenPos;

            out vec4 fragColor;
            #line 1
            """;

    private static final String SHADERTOY_POSTLUDE = """

            void main() {
                // GUI の 1 ドット単位に丸める。マイクラのドットと揃えて、滑らかすぎる見た目を避ける
                vec2 fragCoord = floor(vec2(screenPos.x - Rect.x, Rect.w - screenPos.y)) + 0.5;
                vec4 color = vec4(0.0);
                mainImage(color, fragCoord);
                fragColor = vec4(color.rgb, 1.0) * vertexColor;
            }
            """;

    @SubscribeEvent
    public static void onShaderRegistry(RegisterShadersEvent event) throws java.io.IOException {
        ResourceProvider base = event.getResourceProvider();
        ResourceProvider wrapping = location -> {
            Optional<Resource> found = base.getResource(location);
            if (found.isEmpty()
                    || !Tpsthings.MODID.equals(location.getNamespace())
                    || SHADERTOY_FRAGMENTS.stream().noneMatch(location.getPath()::endsWith)) {
                return found;
            }
            Resource original = found.get();
            return Optional.of(new Resource(Minecraft.getInstance().getVanillaPackResources(), () -> {
                String body;
                try (InputStream in = original.open()) {
                    body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                // ヘッダー込みの完全な fsh を貼られても二重定義にならないよう #version 行だけは除去
                body = body.replaceAll("(?m)^\\s*#version.*$", "");
                String full = SHADERTOY_PRELUDE + body + SHADERTOY_POSTLUDE;
                return new ByteArrayInputStream(full.getBytes(StandardCharsets.UTF_8));
            }));
        };
        event.registerShader(
                new ShaderInstance(wrapping,
                        ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "tooltip_test"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> tooltipTestShader = shader);
        event.registerShader(
                new ShaderInstance(wrapping,
                        ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "tooltip_layer"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> tooltipLayerShader = shader);
        event.registerShader(
                new ShaderInstance(wrapping,
                        ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "tooltip_oo"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> tooltipOoShader = shader);
        event.registerShader(
                new ShaderInstance(base,
                        ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "sugoi_menu_screen"),
                        DefaultVertexFormat.POSITION_TEX),
                shader -> sugoiMenuScreenShader = shader);
    }

    /** 儀式の後処理シェーダーは PostChain なので、資源の再読み込みで自分から読み直す必要がある。 */
    @SubscribeEvent
    public static void registerReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                jp.main.taikun.tpsthings.gui.AbyssOverlay.invalidateChain());
    }

    @SubscribeEvent
    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ItemPrism.ShaderTooltip.class, ClientShaderTooltip::new);
        event.register(jp.main.taikun.tpsthings.items.ItemLayer.LayerTooltip.class, ClientShaderTooltip::new);
        event.register(jp.main.taikun.tpsthings.items.ItemOo.OoTooltip.class, ClientShaderTooltip::new);
        event.register(ItemPrism.WaveName.class, jp.main.taikun.tpsthings.gui.WaveTitleTooltip::new);
        event.register(ItemPrism.WaveSegments.class, jp.main.taikun.tpsthings.gui.WaveSegmentsTooltip::new);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void registerScreens(RegisterEvent event) {
        event.register(Registries.MENU, menuTypeRegisterHelper -> ClientRegistrationUtil.registerScreen(ModContainerTypes.EXAMPLE_MACHINE, GuiExampleMachine::new));
        event.register(Registries.MENU, menuTypeRegisterHelper -> ClientRegistrationUtil.registerScreen(ModContainerTypes.TIME_FLUX_COLLECTOR, GuiTimeFluxCollector::new));

        event.register(Registries.MENU, menuTypeRegisterHelper -> ClientRegistrationUtil.registerScreen(ModContainerTypes.TIME_ACCELERATOR, GuiTimeAccelerator::new));
        event.register(Registries.MENU, menuTypeRegisterHelper -> ClientRegistrationUtil.registerScreen(ModContainerTypes.TPS_GENERATOR, GuiTpsGenerator<BETpsGenerator>::new));
        event.register(Registries.MENU, menuTypeRegisterHelper -> ClientRegistrationUtil.registerScreen(ModContainerTypes.LAG_GENERATOR, GuiTpsGenerator<BELagGenerator>::new));

        // ヘルパーメソッドを介して登録を呼び出す
        MachineRegistry.INSTANCE.forEachMachine(machine -> registerMachineScreenHelper(event, machine));
    }

    // 型キャプチャエラーを回避するためのジェネリックヘルパーメソッド
    private static <BE extends TileEntityMekanism> void registerMachineScreenHelper(RegisterEvent event, BaseMachine<BE> machine) {
        event.register(Registries.MENU, menuTypeRegisterHelper ->
                ClientRegistrationUtil.registerScreen(machine.tileContainer, machine.getGuiSupplier())
        );
    }
}