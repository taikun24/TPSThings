package jp.main.taikun.tpsthings.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.items.ItemPrism;
import jp.main.taikun.tpsthings.registries.ClientRegister;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ツールチップ全体にカスタムコアシェーダー (tpsthings:tooltip_test) のクアッドを被せる。
 * このコンポーネント自体はサイズ 0 のマーカーで、renderImage は背景描画後に呼ばれるため
 * 全体を覆うオーバーレイとして機能する。全体の矩形は RenderTooltipEvent.Pre で
 * 捕まえたコンポーネント一覧から vanilla のレイアウト計算を再現して求める。
 */
@Mod.EventBusSubscriber(modid = Tpsthings.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientShaderTooltip implements ClientTooltipComponent {
    /** 背景枠がテキスト矩形から食み出すピクセル数 (TooltipRenderUtil.renderTooltipBackground と同じ) */
    private static final int PADDING = 3;
    /** オーバーレイの不透明度 (0-255)。テキストの可読性を残すため半透明にする */
    private static final int ALPHA = 110;

    /** いま描画中のツールチップのコンポーネント一覧 (フレームごとに Pre で更新) */
    private static List<ClientTooltipComponent> frameComponents = List.of();

    // ---- Shadertoy 互換 uniform 用の状態 ----
    private static final long START_MS = Util.getMillis();
    private static long lastFrameMs = START_MS;
    private static int frameCounter = 0;
    private static float clickX = 0;
    private static float clickY = 0;
    private static boolean wasLeftDown = false;

    @SubscribeEvent
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        frameComponents = event.getComponents();
    }

    /**
     * WavyNameItem のツールチップのタイトル行と、ロア (display.Lore) の行を
     * 波打ちアニメーション付きの自前描画 (WaveTitleTooltip) に差し替える。
     * 画面によっては ItemStack がイベントに渡ってこない (EMPTY になる) ので、
     * スタックではなく ShaderTooltip マーカーの有無でも判定する。
     */
    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        var elements = event.getTooltipElements();
        if (elements.isEmpty()) {
            return;
        }
        boolean isWavy = event.getItemStack().getItem() instanceof jp.main.taikun.tpsthings.items.WavyNameItem
                || elements.stream().anyMatch(e ->
                        e.right().filter(t -> t instanceof ItemPrism.ShaderTooltip
                                || t instanceof jp.main.taikun.tpsthings.items.ItemOo.OoTooltip).isPresent());
        if (!isWavy) {
            return;
        }
        elements.get(0).left().ifPresent(title ->
                elements.set(0, com.mojang.datafixers.util.Either.right(
                        new ItemPrism.WaveName(title.getString(), WaveTitleTooltip.TITLE_SCALE))));
        for (int i = 1; i < elements.size(); i++) {
            int index = i;
            elements.get(i).left().ifPresent(line -> {
                if (!(line instanceof Component component)) {
                    return;
                }
                if (isLoreStyle(component)) {
                    // ロア行 (バニラが紫+斜体のスタイルを付ける) は全体を波打ちに (等倍)
                    elements.set(index, com.mojang.datafixers.util.Either.right(
                            new ItemPrism.WaveName(component.getString(), 1.0F)));
                } else {
                    // 属性行などの数値は Infinity に置き換え、そこだけ波打ちに
                    ItemPrism.WaveSegments replaced = replaceNumbers(component);
                    if (replaced != null) {
                        elements.set(index, com.mojang.datafixers.util.Either.right(replaced));
                    }
                }
            });
        }
    }
    /** 行中の数値 (符号・NaN 含む) を検出する */
    private static final java.util.regex.Pattern NUMBER_PATTERN =
            java.util.regex.Pattern.compile("[-+]?(?:NaN|Infinity|\\d[\\d,]*(?:\\.\\d+)?)");

    private static ItemPrism.WaveSegments replaceNumbers(Component component) {
        String text = component.getString();
        java.util.regex.Matcher matcher = NUMBER_PATTERN.matcher(text);

        // 置き換え対象が存在するかチェック
        boolean hasMatch = false;
        while (matcher.find()) {
            if (shouldReplace(matcher.group())) {
                hasMatch = true;
                break;
            }
        }
        if (!hasMatch) {
            return null; // 1000以上の数値がない場合は置き換え不要
        }

        matcher.reset(); // マッチング位置を先頭にリセット
        int staticColor = component.getStyle().getColor() != null
                ? component.getStyle().getColor().getValue()
                : 0xFFFFFF;

        java.util.List<ItemPrism.WaveSegments.Segment> segments = new java.util.ArrayList<>();
        int cursor = 0;

        while (matcher.find()) {
            String rawVal = matcher.group();
            if (shouldReplace(rawVal)) {
                // 1000以上の場合のみ Infinity 化
                if (matcher.start() > cursor) {
                    segments.add(new ItemPrism.WaveSegments.Segment(text.substring(cursor, matcher.start()), false, staticColor));
                }
                segments.add(new ItemPrism.WaveSegments.Segment("Infinity", true, 0xFFFFFF));
                cursor = matcher.end();
            }
        }

        if (cursor < text.length()) {
            segments.add(new ItemPrism.WaveSegments.Segment(text.substring(cursor), false, staticColor));
        }

        return new ItemPrism.WaveSegments(segments);
    }

    /**
     * 対象の数値文字列が1000以上（または既に入力されている Infinity）であるかを判定
     */
    private static boolean shouldReplace(String valStr) {
        if ("Infinity".equalsIgnoreCase(valStr) || "+Infinity".equalsIgnoreCase(valStr)) {
            return true;
        }
        if ("NaN".equalsIgnoreCase(valStr) || "-Infinity".equalsIgnoreCase(valStr)) {
            return false;
        }

        try {
            // カンマを除去して double にパース
            String cleanStr = valStr.replace(",", "");
            double val = Double.parseDouble(cleanStr);
            return val >= 1000.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    /** バニラの LORE_STYLE (DARK_PURPLE + イタリック) が付いた行かどうか */
    private static boolean isLoreStyle(Component component) {
        Style style = component.getStyle();
        return style.isItalic()
                && style.getColor() != null
                && style.getColor().equals(TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE));
    }

    /** 被せるシェーダー。リソース再読み込みで差し替わるので、毎回取り直す */
    private final java.util.function.Supplier<ShaderInstance> shaderSource;
    /** 貫通層 (0〜10)。層を持たないシェーダーでは -1 */
    private final float layer;

    public ClientShaderTooltip(ItemPrism.ShaderTooltip tooltip) {
        this.shaderSource = ClientRegister::getTooltipTestShader;
        this.layer = -1.0F;
    }

    public ClientShaderTooltip(jp.main.taikun.tpsthings.items.ItemOo.OoTooltip tooltip) {
        this.shaderSource = ClientRegister::getTooltipOoShader;
        this.layer = -1.0F;
    }

    public ClientShaderTooltip(jp.main.taikun.tpsthings.items.ItemMaterial.MaterialTooltip tooltip) {
        String name = tooltip.shader();
        this.shaderSource = () -> ClientRegister.getMaterialShader(name);
        this.layer = -1.0F;
    }

    public ClientShaderTooltip(jp.main.taikun.tpsthings.items.ItemLayer.LayerTooltip tooltip) {
        this.shaderSource = ClientRegister::getTooltipLayerShader;
        this.layer = tooltip.layer();
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getWidth(Font font) {
        return 0;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        ShaderInstance shader = shaderSource.get();
        if (shader == null) {
            return;
        }
        List<ClientTooltipComponent> components = frameComponents;
        int index = -1;
        for (int i = 0; i < components.size(); i++) {
            if (components.get(i) == this) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }

        // vanilla の renderTooltipInternal と同じ計算で全体の矩形を復元する
        int width = 0;
        int height = components.size() == 1 ? -2 : 0;
        int offsetY = 0;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            width = Math.max(width, component.getWidth(font));
            height += component.getHeight();
            if (i < index) {
                offsetY += component.getHeight();
            }
        }
        if (index > 0) {
            offsetY += 2; // 先頭行 (アイテム名) の直後に入る 2px の隙間
        }

        float left = x - PADDING;
        float top = y - offsetY - PADDING;
        float right = x + width + PADDING;
        float bottom = top + height + PADDING * 2;

        // 文字は bufferSource に溜められ、描画待ちのまま renderImage に来る。
        // 溜まった分がこのクアッドより前に出るか後に出るかはフォントのページ切り替えしだいで揺れ、
        // 一部の文字だけがシェーダーの下に沈んでいた。先に出し切って順番を確定させる
        guiGraphics.flush();

        Matrix4f matrix = guiGraphics.pose().last().pose();
        applyShadertoyUniforms(shader, left, top, right, bottom);
        if (layer >= 0.0F) {
            shader.safeGetUniform("Layer").set(layer);
        }
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, left, top, 0).color(255, 255, 255, ALPHA).endVertex();
        buffer.vertex(matrix, left, bottom, 0).color(255, 255, 255, ALPHA).endVertex();
        buffer.vertex(matrix, right, bottom, 0).color(255, 255, 255, ALPHA).endVertex();
        buffer.vertex(matrix, right, top, 0).color(255, 255, 255, ALPHA).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();

        // 被せた上から文字を描き直す。これで文字は常にシェーダーより手前に来る
        redrawText(font, components, x, y - offsetY, guiGraphics);
    }

    /** vanilla の renderTooltipInternal と同じ並べ方で、全行の文字をもう一度描く。 */
    private static void redrawText(Font font, List<ClientTooltipComponent> components, int x, int textTop,
                                   GuiGraphics guiGraphics) {
        Matrix4f matrix = guiGraphics.pose().last().pose();
        int lineY = textTop;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            component.renderText(font, x, lineY, matrix, guiGraphics.bufferSource());
            lineY += component.getHeight() + (i == 0 ? 2 : 0);
        }
        guiGraphics.flush();
    }

    /**
     * Shadertoy 互換 uniform を毎フレーム流し込む。
     * 座標系は fragCoord と同じ「オーバーレイ矩形の左下原点・上向き正・GUI 座標単位」。
     * 全画面に被せる AbyssOverlay も使う。
     */
    static void applyShadertoyUniforms(ShaderInstance shader, float left, float top, float right, float bottom) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        float time = (now - START_MS) / 1000.0F;
        float delta = Math.max((now - lastFrameMs) / 1000.0F, 1.0E-4F);
        lastFrameMs = now;
        frameCounter++;

        // マウス位置 (ウィンドウピクセル → GUI 座標)
        double guiScale = mc.getWindow().getGuiScale();
        float mouseX = (float) (mc.mouseHandler.xpos() / guiScale) - left;
        float mouseY = bottom - (float) (mc.mouseHandler.ypos() / guiScale);
        boolean leftDown = mc.mouseHandler.isLeftPressed();
        if (leftDown && !wasLeftDown) {
            clickX = mouseX;
            clickY = mouseY;
        }
        wasLeftDown = leftDown;

        LocalDateTime date = LocalDateTime.now();
        float secondsOfDay = date.getHour() * 3600.0F + date.getMinute() * 60.0F
                + date.getSecond() + date.getNano() / 1.0E9F;

        shader.safeGetUniform("iResolution").set(right - left, bottom - top, 1.0F);
        shader.safeGetUniform("iTime").set(time);
        shader.safeGetUniform("iTimeDelta").set(delta);
        shader.safeGetUniform("iFrameRate").set(1.0F / delta);
        shader.safeGetUniform("iFrame").set(frameCounter);
        // Shadertoy 準拠: xy は左ボタン押下中のみ現在位置、zw はクリック位置 (押下中は正)
        shader.safeGetUniform("iMouse").set(
                leftDown ? mouseX : clickX,
                leftDown ? mouseY : clickY,
                leftDown ? clickX : -clickX,
                leftDown ? clickY : -clickY);
        shader.safeGetUniform("iDate").set(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), secondsOfDay);
        shader.safeGetUniform("iSampleRate").set(44100.0F);
        shader.safeGetUniform("Rect").set(left, top, right, bottom);
    }
}
