package jp.main.taikun.tpsthings.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.network.PacketAbyss;
import jp.main.taikun.tpsthings.registries.ClientRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * 周りの世界が深層に寄っていく演出 (クライアント側)。
 *
 * <ul>
 *   <li>崩れる・暗くなる: 世界の絵そのものに後処理 (shaders/post/abyss.json) をかける。帯状のずれ・色収差・砂嵐・暗さ</li>
 *   <li>インパクトフレーム: 数 tick だけ白黒 2 階調と集中線に叩き落とし、画面を揺らす</li>
 *   <li>白く飛ぶ: HUD ごと</li>
 *   <li>音が減る: これから鳴る音を割合で消す。UI の音 (MASTER) と、深層の源 (儀式の炉) のそばで鳴る音は残す</li>
 *   <li>フラッシュ: 貫通層シェーダー (tooltip_layer) を索引層の深さで全画面に被せる</li>
 * </ul>
 *
 * <p>描くのは {@code MixinGameRendererAbyss} から。Forge の HUD のイベントは F1 で HUD を隠すと来ないので使わない。
 *
 * <p>既に鳴っている音 (BGM など) は止めない。止めると戻す手段が無い。
 */
@Mod.EventBusSubscriber(modid = Tpsthings.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AbyssOverlay {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation CHAIN_ID =
            ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "shaders/post/abyss.json");
    private static final int FLASH_ALPHA = 230;
    /** 白飛びの最初の濃さ。真っ白には塗らない */
    private static final float WHITEOUT_PEAK = 0.75F;
    /** 深層の源からこの距離 (ブロック) までで鳴った音は、静けさに消されない */
    private static final double SOURCE_RADIUS = 2.0;

    /** 儀式から送られてくる状態。holdTicks が尽きたら元へ戻る */
    private static int holdTicks;
    private static float quiet;
    private static float dim;
    private static float noise;
    private static double sourceX;
    private static double sourceY;
    private static double sourceZ;

    /** 貫通攻撃で索引から消えた瞬間の、短く完全な無音 */
    private static int hushTicks;

    private static float shownDim;
    private static float prevShownDim;
    private static float shownNoise;
    private static float prevShownNoise;
    private static int flashTicks;
    private static int flashLength;
    private static int impactTicks;
    private static int impactLength;
    private static int whiteoutTicks;
    private static int whiteoutLength;
    /** 崩れ方の種。tick ごとに進める。シェーダーでは float になるので、一周させて大きくしない (白黒の反転に使う偶奇は保つ) */
    private static int seed;

    /** 世界を崩す後処理。資源の再読み込みで作り直す */
    private static PostChain chain;
    private static boolean chainFailed;
    private static int chainWidth;
    private static int chainHeight;

    private AbyssOverlay() {
    }

    public static void accept(PacketAbyss packet) {
        if (packet.ticks() <= 0) {
            // 光と音を一度に戻す
            holdTicks = 0;
            quiet = 0.0F;
            dim = 0.0F;
            noise = 0.0F;
            shownDim = prevShownDim = 0.0F;
            shownNoise = prevShownNoise = 0.0F;
            flashTicks = 0;
        } else {
            holdTicks = packet.ticks();
            quiet = packet.quiet();
            dim = packet.dim();
            noise = packet.noise();
            sourceX = packet.x();
            sourceY = packet.y();
            sourceZ = packet.z();
        }
        if (packet.flash() > 0) {
            flashTicks = flashLength = packet.flash();
        }
        if (packet.impact() > 0) {
            impactTicks = impactLength = packet.impact();
        }
        if (packet.whiteout() > 0) {
            whiteoutTicks = whiteoutLength = packet.whiteout();
        }
    }

    public static void hush(int ticks) {
        hushTicks = Math.max(hushTicks, ticks);
    }

    /** 資源の再読み込みで、後処理のシェーダーを次の tick に読み直させる。 */
    public static void invalidateChain() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        chainFailed = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // 読み込みの失敗が起動時にログへ出るよう、使う前から読んでおく。資源の読み込み中は触らない
        if (chain == null && !chainFailed && mc.getOverlay() == null) {
            loadChain(mc);
        }
        if (mc.level == null) {
            accept(PacketAbyss.RESET);
            hushTicks = impactTicks = whiteoutTicks = 0;
            return;
        }
        seed = (seed + 1) & 0xFFFF;
        if (holdTicks > 0) holdTicks--;
        if (hushTicks > 0) hushTicks--;
        if (flashTicks > 0) flashTicks--;
        if (impactTicks > 0) impactTicks--;
        if (whiteoutTicks > 0) whiteoutTicks--;

        prevShownDim = shownDim;
        prevShownNoise = shownNoise;
        float dimTarget = holdTicks > 0 ? dim : 0.0F;
        float noiseTarget = holdTicks > 0 ? noise : 0.0F;
        // 沈むのはゆっくり、戻るのは速く
        shownDim = shownDim < dimTarget ? Math.min(dimTarget, shownDim + 0.05F) : Math.max(dimTarget, shownDim - 0.1F);
        shownNoise = shownNoise < noiseTarget ? Math.min(noiseTarget, shownNoise + 0.05F) : Math.max(noiseTarget, shownNoise - 0.15F);
    }

    private static void loadChain(Minecraft mc) {
        try {
            RenderTarget main = mc.getMainRenderTarget();
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN_ID);
            chain.resize(main.width, main.height);
            chainWidth = main.width;
            chainHeight = main.height;
        } catch (IOException | RuntimeException e) {
            chainFailed = true;
            LOGGER.error("深層の後処理シェーダー {} を読み込めませんでした", CHAIN_ID, e);
        }
    }

    /** インパクトフレームの今の強さ。1 コマおきに叩きつけ、最後のコマだけ余韻を残す。 */
    private static float impact() {
        if (impactTicks <= 0) {
            return 0.0F;
        }
        if (impactTicks == 1) {
            return 0.35F;
        }
        return (impactLength - impactTicks) % 3 == 2 ? 0.0F : 1.0F;
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() == SoundSource.MASTER) {
            return;
        }
        float strength;
        if (hushTicks > 0) {
            strength = 1.0F;
        } else if (holdTicks > 0 && !fromSource(sound)) {
            strength = quiet;
        } else {
            strength = 0.0F;
        }
        if (strength > 0.0F && RANDOM.nextFloat() < strength) {
            event.setSound(null);
        }
    }

    /** 深層の源のそばで鳴った音か。儀式の音まで、儀式が作った静けさに消されないように。 */
    private static boolean fromSource(SoundInstance sound) {
        if (sound.isRelative()) {
            return false;
        }
        double dx = sound.getX() - sourceX;
        double dy = sound.getY() - sourceY;
        double dz = sound.getZ() - sourceZ;
        return dx * dx + dy * dy + dz * dz <= SOURCE_RADIUS * SOURCE_RADIUS;
    }

    /** インパクトフレームとノイズに合わせて画面を揺らす。 */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float shake = impact() * 3.0F + shownNoise * 0.6F;
        if (shake <= 0.0F) {
            return;
        }
        event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * shake * 2.0F);
        event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5F) * shake * 0.5F);
        event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5F) * shake * 0.5F);
    }

    /**
     * 世界を描き終えた直後、HUD より前。崩すのも暗くするのも世界だけにする。
     * 描き終えたあとの描き込み先の戻しは、呼び出し元 (GameRenderer) がする。
     */
    public static void renderWorld(float partial) {
        float noiseNow = Mth.lerp(partial, prevShownNoise, shownNoise);
        float impactNow = impact();
        float darkness = Mth.clamp(Mth.lerp(partial, prevShownDim, shownDim), 0.0F, 1.0F);
        if (chain == null || (noiseNow <= 0.001F && impactNow <= 0.0F && darkness <= 0.001F)) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width != chainWidth || main.height != chainHeight) {
            chain.resize(main.width, main.height);
            chainWidth = main.width;
            chainHeight = main.height;
        }
        uniform("Noise", noiseNow);
        uniform("Impact", impactNow);
        uniform("Seed", seed);
        uniform("Dim", darkness);
        // 静けさは段の深さに比例させて送っているので、色の深さにもそのまま使う
        uniform("Depth", Mth.clamp(quiet / 0.8F, 0.0F, 1.0F));
        // バニラの後処理 (スペクテイターの視点など) と同じ状態で掛ける
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(partial);
    }

    /** 全パスのプログラムに流す。持っていないパス (最後の blit) では何も起きない。 */
    private static void uniform(String name, float value) {
        for (net.minecraft.client.renderer.PostPass pass : ((jp.main.taikun.tpsthings.mixin.AccessorPostChain) chain).tpsthings$getPasses()) {
            pass.getEffect().safeGetUniform(name).set(value);
        }
    }

    /** HUD を描き終えた後、画面 (ポーズメニューなど) より前。F1 で HUD を隠していても呼ばれる。 */
    public static void renderScreen(float partial) {
        boolean flash = flashTicks > 0 && flashLength > 0;
        boolean white = whiteoutTicks > 0 && whiteoutLength > 0;
        // 後処理が読めなかったときだけ、暗さを HUD ごと塗って代わりにする
        float fallbackDim = chain == null ? Mth.clamp(Mth.lerp(partial, prevShownDim, shownDim), 0.0F, 1.0F) : 0.0F;
        if (!flash && !white && fallbackDim <= 0.001F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        if (fallbackDim > 0.001F) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int) (fallbackDim * 255) << 24);
        }
        if (flash) {
            renderFlash(graphics, Mth.clamp((flashTicks - partial) / flashLength, 0.0F, 1.0F));
        }
        if (white) {
            float fade = Mth.clamp((whiteoutTicks - partial) / whiteoutLength, 0.0F, 1.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    ((int) (fade * fade * WHITEOUT_PEAK * 255) << 24) | 0xFFFFFF);
        }
        graphics.flush();
    }

    private static void renderFlash(GuiGraphics graphics, float strength) {
        ShaderInstance shader = ClientRegister.getTooltipLayerShader();
        if (shader == null) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int alpha = (int) (FLASH_ALPHA * strength);

        graphics.flush();
        ClientShaderTooltip.applyShadertoyUniforms(shader, 0.0F, 0.0F, width, height);
        shader.safeGetUniform("Layer").set(10.0F);

        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).color(255, 255, 255, alpha).endVertex();
        buffer.vertex(matrix, 0.0F, height, 0.0F).color(255, 255, 255, alpha).endVertex();
        buffer.vertex(matrix, width, height, 0.0F).color(255, 255, 255, alpha).endVertex();
        buffer.vertex(matrix, width, 0.0F, 0.0F).color(255, 255, 255, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
