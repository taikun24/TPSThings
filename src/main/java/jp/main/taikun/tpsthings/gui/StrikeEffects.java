package jp.main.taikun.tpsthings.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.damage.PiercingStrike;
import jp.main.taikun.tpsthings.network.PacketStrikeEffect;
import jp.main.taikun.tpsthings.network.PacketStrikeReport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 貫通攻撃の演出 (クライアント側)。見た目は<b>どの層で通ったか</b>だけで決める。相手が誰かは見ない。
 *
 * <ul>
 *   <li>正規の道 (hurt → 倒れ終わって remove) で済んだ相手: サーバの粒だけ。いつもどおりの死</li>
 *   <li>HP の直書き (L6) や死亡処理の直呼び (L8) が要った相手: 体力の数字が剥がれ落ちる</li>
 *   <li>索引から直接外した相手 (L10): 輪郭が色収差の走査線に割れて上から抜け、周りの音が一瞬止む</li>
 *   <li>どこかの層で最後まで拒まれた相手: 層の色ではなく灰白。HP が削れなければ「×」が宙に残り、
 *       索引から外せなければ輪郭は割れかけて元に戻る</li>
 * </ul>
 *
 * <p>アクションバーの報告も層を 1 つずつ区切って出し、深い層ほど低い音で鳴らす。
 */
@Mod.EventBusSubscriber(modid = Tpsthings.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StrikeEffects {

    private static final int CHIP_LIFE = 30;
    private static final int AFTERIMAGE_LIFE = 16;
    private static final int AFTERIMAGE_SLICES = 8;
    /** 報告を 1 区切り進める間隔 (tick) */
    private static final int REPORT_INTERVAL = 3;
    /** 索引から消えたあと、周りの音を止める長さ (tick) */
    private static final int ERASE_SILENCE = 20;

    /** 表層のシアンと深層の赤紫。tooltip_layer と同じ色 */
    private static final int SURFACE = 0x1ACCE6;
    private static final int ABYSS = 0xE6146A;
    /** 最後まで拒まれたときの灰白。層の色に乗せない */
    private static final int REFUSED = 0xD8D8E0;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Chip> CHIPS = new ArrayList<>();
    private static final List<Afterimage> AFTERIMAGES = new ArrayList<>();

    private static String reportPrefix = "";
    private static final List<String> REPORT_SEGMENTS = new ArrayList<>();
    private static final List<Integer> REPORT_LAYERS = new ArrayList<>();
    /** 報告を出し始めてからの tick。出し終えたら -1 */
    private static int reportAge = -1;

    /** 剥がれ落ちる数字 1 文字。 */
    private static final class Chip {
        final String text;
        final int color;
        /** false なら落ちずに、その場に残る */
        final boolean falls;
        double x, y, z, prevX, prevY, prevZ, vx, vy, vz;
        int age;

        Chip(String text, int color, boolean falls, double x, double y, double z, double vx, double vy, double vz) {
            this.text = text;
            this.color = color;
            this.falls = falls;
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }
    }

    /** 索引から抜けていく輪郭。 */
    private static final class Afterimage {
        final AABB box;
        final int seed;
        /** 索引から外せなかった。抜けていかずに、割れかけて元に戻る */
        final boolean refused;
        int age;

        Afterimage(AABB box, int seed, boolean refused) {
            this.box = box;
            this.seed = seed;
            this.refused = refused;
        }
    }

    private StrikeEffects() {
    }

    public static void acceptEffect(PacketStrikeEffect packet) {
        boolean refused = packet.health() == PiercingStrike.FAILED || packet.death() == PiercingStrike.FAILED
                || packet.removal() == PiercingStrike.FAILED;
        int color = refused ? REFUSED : layerColor(deepest(packet.health(), packet.death(), packet.removal()));

        // 正規の hurt で HP が 0 にならなかった相手 (L6 以降 / 通らなかった) だけ、数字を剥がす
        if (packet.health() > 5 || packet.death() > 5) {
            double top = packet.y() + packet.height();
            if (packet.health() == PiercingStrike.FAILED) {
                // HP を削れなかった。剥がれ落ちるものが無いので、「×」だけが宙に残る
                CHIPS.add(new Chip("×", color, false, packet.x(), top, packet.z(), 0.0, 0.03, 0.0));
            } else {
                String text = String.format(Locale.ROOT, "%.1f", packet.hp());
                for (int i = 0; i < text.length(); i++) {
                    CHIPS.add(new Chip(String.valueOf(text.charAt(i)), color, true, packet.x(), top, packet.z(),
                            (RANDOM.nextDouble() - 0.5) * 0.12,
                            0.06 + RANDOM.nextDouble() * 0.08,
                            (RANDOM.nextDouble() - 0.5) * 0.12));
                }
            }
        }

        double half = packet.width() / 2.0;
        AABB box = new AABB(
                packet.x() - half, packet.y(), packet.z() - half,
                packet.x() + half, packet.y() + packet.height(), packet.z() + half);
        if (packet.removal() == 10) {
            // 除去の印では消えず、索引の直接操作まで要った相手。名簿から名前が消えた瞬間、世界が一瞬黙る
            AFTERIMAGES.add(new Afterimage(box, RANDOM.nextInt(), false));
            AbyssOverlay.hush(ERASE_SILENCE);
        } else if (packet.removal() == PiercingStrike.FAILED) {
            // 索引まで降りても外れなかった。抜けていく演出を出すと、消えたと嘘をつくことになる
            AFTERIMAGES.add(new Afterimage(box, RANDOM.nextInt(), true));
        }
    }

    public static void acceptReport(PacketStrikeReport packet) {
        reportPrefix = packet.count() > 1 ? packet.count() + " 体 | " : "";
        REPORT_SEGMENTS.clear();
        REPORT_LAYERS.clear();
        addSegment("HP", packet.health());
        addSegment("死", packet.death());
        if (packet.removal() != 0) {
            addSegment("消", packet.removal());
        }
        reportAge = 0;
        showReport(0);
    }

    private static void addSegment(String label, int layer) {
        REPORT_SEGMENTS.add(label + " " + PiercingStrike.Result.label(layer));
        REPORT_LAYERS.add(layer);
    }

    private static void showReport(int step) {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder text = new StringBuilder(reportPrefix);
        for (int i = 0; i <= step; i++) {
            if (i > 0) {
                text.append(" · ");
            }
            text.append(REPORT_SEGMENTS.get(i));
        }
        mc.gui.setOverlayMessage(Component.literal(text.toString()).withStyle(ChatFormatting.LIGHT_PURPLE), false);

        int layer = REPORT_LAYERS.get(step);
        if (layer == 0) {
            return;
        }
        // 深い層ほど低い音。通らなかった層は一番低く。UI の音 (MASTER) なので、世界が黙っていても鳴る
        float pitch = layer == PiercingStrike.FAILED ? 0.5F : Mth.clamp(2.0F - layer * 0.14F, 0.5F, 2.0F);
        SoundManager sounds = mc.getSoundManager();
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), pitch, 0.5F));
        if (layer >= 10) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.4F));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CHIPS.clear();
            AFTERIMAGES.clear();
            reportAge = -1;
            return;
        }
        if (mc.isPaused()) {
            return;
        }

        CHIPS.removeIf(chip -> ++chip.age > CHIP_LIFE);
        for (Chip chip : CHIPS) {
            chip.prevX = chip.x;
            chip.prevY = chip.y;
            chip.prevZ = chip.z;
            chip.x += chip.vx;
            chip.y += chip.vy;
            chip.z += chip.vz;
            chip.vx *= 0.96;
            chip.vz *= 0.96;
            chip.vy = chip.falls ? chip.vy * 0.98 - 0.012 : chip.vy * 0.8;
        }
        AFTERIMAGES.removeIf(image -> ++image.age > AFTERIMAGE_LIFE);

        if (reportAge >= 0 && ++reportAge % REPORT_INTERVAL == 0) {
            int step = reportAge / REPORT_INTERVAL;
            if (step < REPORT_SEGMENTS.size()) {
                showReport(step);
            } else {
                reportAge = -1;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || (CHIPS.isEmpty() && AFTERIMAGES.isEmpty())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (!AFTERIMAGES.isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (Afterimage image : AFTERIMAGES) {
                renderAfterimage(pose, lines, image, partial);
            }
            buffers.endBatch(RenderType.lines());
        }
        for (Chip chip : CHIPS) {
            renderChip(pose, buffers, camera, mc.font, chip, partial);
        }
        buffers.endBatch();
        pose.popPose();
    }

    private static void renderAfterimage(PoseStack pose, VertexConsumer lines, Afterimage image, float partial) {
        float t = Mth.clamp((image.age + partial) / AFTERIMAGE_LIFE, 0.0F, 1.0F);
        AABB box = image.box;
        double sliceHeight = box.getYsize() / AFTERIMAGE_SLICES;
        int frame = image.age / 2;
        if (image.refused) {
            // 半ばまで割れて、元の 1 つの形に戻る。色は割らない
            float crack = Mth.sin(t * Mth.PI);
            float fade = 1.0F - t * 0.6F;
            for (int s = 0; s < AFTERIMAGE_SLICES; s++) {
                double jitter = (hash(image.seed, s, frame) - 0.5) * 0.6 * crack;
                double minY = box.minY + s * sliceHeight;
                LevelRenderer.renderLineBox(pose, lines,
                        new AABB(box.minX + jitter, minY, box.minZ, box.maxX + jitter, minY + sliceHeight, box.maxZ),
                        0.85F, 0.85F, 0.88F, fade);
            }
            return;
        }
        // 上の段から順に索引から抜けていく
        int remaining = Mth.ceil(AFTERIMAGE_SLICES * (1.0F - t));
        float alpha = 1.0F - t * 0.7F;
        for (int s = 0; s < remaining; s++) {
            double jitter = (hash(image.seed, s, frame) - 0.5) * (0.1 + 0.6 * t);
            double minY = box.minY + s * sliceHeight;
            AABB slice = new AABB(box.minX + jitter, minY, box.minZ, box.maxX + jitter, minY + sliceHeight, box.maxZ);
            // 色収差: 表層の色と深層の色を左右に割る
            LevelRenderer.renderLineBox(pose, lines, slice.move(-0.04, 0.0, 0.0), 0.10F, 0.80F, 0.90F, alpha);
            LevelRenderer.renderLineBox(pose, lines, slice.move(0.04, 0.0, 0.0), 0.90F, 0.08F, 0.40F, alpha);
        }
    }

    private static void renderChip(PoseStack pose, MultiBufferSource buffers, Camera camera, Font font,
                                   Chip chip, float partial) {
        float t = Mth.clamp((chip.age + partial) / CHIP_LIFE, 0.0F, 1.0F);
        pose.pushPose();
        pose.translate(Mth.lerp(partial, chip.prevX, chip.x),
                Mth.lerp(partial, chip.prevY, chip.y),
                Mth.lerp(partial, chip.prevZ, chip.z));
        // 名札と同じ向き (常にカメラを向く)
        pose.mulPose(camera.rotation());
        pose.scale(-0.04F, -0.04F, 0.04F);
        // フォントは不透明度が 4 未満の色を不透明として扱うので、下限を置く
        int alpha = Math.max(16, (int) (255 * (1.0F - t)));
        font.drawInBatch(chip.text, -font.width(chip.text) / 2.0F, 0.0F, (alpha << 24) | chip.color, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** 3 つの結果のうち一番深い層。通らなかった層は最深として数える。 */
    private static int deepest(int health, int death, int removal) {
        int deepest = 0;
        for (int layer : new int[]{health, death, removal}) {
            deepest = Math.max(deepest, Math.min(layer, 10));
        }
        return deepest;
    }

    private static int layerColor(int layer) {
        float depth = layer / 10.0F;
        int r = (int) Mth.lerp(depth, (SURFACE >> 16) & 0xFF, (ABYSS >> 16) & 0xFF);
        int g = (int) Mth.lerp(depth, (SURFACE >> 8) & 0xFF, (ABYSS >> 8) & 0xFF);
        int b = (int) Mth.lerp(depth, SURFACE & 0xFF, ABYSS & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    private static float hash(int seed, int a, int b) {
        int h = seed ^ (a * 0x27D4EB2D) ^ (b * 0x165667B1);
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65536.0F;
    }
}
