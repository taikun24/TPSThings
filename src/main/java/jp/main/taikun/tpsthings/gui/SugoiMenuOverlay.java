package jp.main.taikun.tpsthings.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import jp.main.taikun.tpsthings.damage.GuardSettings;
import jp.main.taikun.tpsthings.network.ModNetwork;
import jp.main.taikun.tpsthings.network.PacketGuardChange;
import jp.main.taikun.tpsthings.network.PacketGuardRequest;
import jp.main.taikun.tpsthings.registries.ClientRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SugoiMenuOverlay implements IGuiOverlay {
    // キーを押している間だけ true。入力 (スクロール/クリック) はこれで受け付ける
    private static boolean open;
    // 見た目上の開き具合 0..1。open に向かって指数的に寄っていき、描画はすべてこの値から決める。
    // 閉じている途中で再度開いても値が連続なので見た目が飛ばない
    private static float openness = 0f;
    private static final float OPEN_RATE = 10f;
    private static final float CLOSE_RATE = 14f;

    private static long startTime;
    // 直近の開閉の時刻。衝撃波やフラッシュなど、開閉のたびに一度だけ走る演出用
    private static long transitionNanos;

    // 目標インデックス(整数, スナップ先)
    private static int targetIndex = 0;
    // 実際に描画に使う値(連続値, バネで補間される)
    private static float displayIndex = 0f;
    // 現在の速度(バネ計算用)
    private static float velocity = 0f;

    private static long lastRenderNanos = 0L;

    // バネ係数。硬さと減衰。好みで調整
    private static final float STIFFNESS = 220f;
    private static final float DAMPING = 26f;

    private static final int ITEM_SPACING = 50;
    private static final float VISIBLE_RANGE = 6f;
    // 選択中から 1 つ離れるごとに出現が遅れる量 (openness 単位)
    private static final float STAGGER = 0.08f;
    private static final int BOX_HALF_H = 10;
    private static final int BOX_PAD_X = 8;
    private static final int ACCENT_W = 3;
    // 項目名と値の間の最低限の隙間
    private static final int VALUE_GAP = 18;
    private static final float MESSAGE_SECONDS = 4f;
    // 画面端に必ず残す余白と、選択中の項目が一番大きくなるときの倍率
    private static final int EDGE_MARGIN = 6;
    private static final float MAX_ITEM_SCALE = 1.45f;
    private static final float CHANGE_PULSE_SECONDS = 0.35f;

    // ---- サーバから届いた即死対策の設定 ----
    private static boolean loaded = false;
    private static boolean permitted = false;
    private static List<GuardSettings.Entry> entries = List.of();
    private static String lastMessage = "";
    private static long messageNanos = 0L;
    // 値が変わった瞬間の時刻 (id ごと)。変わった項目を光らせる
    private static final Map<String, Long> changedAt = new HashMap<>();

    private static final List<GuardSettings.Entry> LOADING_ROWS = List.of(
            new GuardSettings.Entry("", "読み込み中…", "", GuardSettings.OFF, ""));
    private static final List<GuardSettings.Entry> DENIED_ROWS = List.of(
            new GuardSettings.Entry("", "即死対策の設定", "権限なし", 0xFF5555,
                    "OP 権限 (レベル 2) が必要です。/tpsthings damage と同じ条件です"));

    public static void show() {
        if (open) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1F));
        open = true;
        startTime = System.nanoTime();
        transitionNanos = startTime;
        if (openness == 0f) lastRenderNanos = 0L;
        // 開くたびに取り直す。コマンドなど別経路で変わっていても最新が並ぶ
        ModNetwork.CHANNEL.sendToServer(new PacketGuardRequest());
    }
    public static void hide() {
        if (!open) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1F));
        open = false;
        transitionNanos = System.nanoTime();
    }
    public static void click() {
        List<GuardSettings.Entry> rows = rows();
        GuardSettings.Entry selected = rows.get(clampIndex(targetIndex, rows.size()));
        if (selected.id().isEmpty()) return;
        ModNetwork.CHANNEL.sendToServer(new PacketGuardChange(selected.id(), Screen.hasShiftDown() ? -1 : 1));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
    }
    public static boolean isEnabled() {
        return open;
    }

    /** サーバから設定一覧が届いた。PacketGuardState から呼ばれる */
    public static void acceptGuardState(boolean permittedNow, List<GuardSettings.Entry> received, String message) {
        long now = System.nanoTime();
        Map<String, String> previous = new HashMap<>();
        entries.forEach(entry -> previous.put(entry.id(), entry.value()));
        for (GuardSettings.Entry entry : received) {
            String before = previous.get(entry.id());
            if (before != null && !before.equals(entry.value())) {
                changedAt.put(entry.id(), now);
            }
        }
        if (!message.isEmpty()) {
            lastMessage = message;
            messageNanos = now;
        }
        // 権限が無くても、おおの道具設定は届く。即死対策の分が無いことは末尾の 1 行で伝える
        if (permittedNow || received.isEmpty()) {
            entries = received;
        } else {
            List<GuardSettings.Entry> combined = new java.util.ArrayList<>(received);
            combined.addAll(DENIED_ROWS);
            entries = combined;
        }
        permitted = permittedNow;
        loaded = true;
        targetIndex = clampIndex(targetIndex, rows().size());
    }

    private static List<GuardSettings.Entry> rows() {
        if (!loaded) return LOADING_ROWS;
        if (entries.isEmpty()) return DENIED_ROWS;
        return entries;
    }

    private static int clampIndex(int index, int size) {
        return Math.min(Math.max(index, 0), size - 1);
    }

    public static void scroll(double delta) {
        int size = rows().size();
        int dir = delta > 0 ? 1 : (delta < 0 ? -1 : 0);
        targetIndex = clampIndex(targetIndex + dir, size);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT,
                0.5F + 0.5f * ((float) targetIndex / (float) size)));
    }

    private static void updateSpring(float dt) {
        // 1フレームが極端に長い場合(初回やラグ時)に暴れないようclamp
        dt = Math.min(dt, 1f / 30f);

        float dx = targetIndex - displayIndex;
        float acc = STIFFNESS * dx - DAMPING * velocity;
        velocity += acc * dt;
        displayIndex += velocity * dt;

        // 十分収束したらピタッと止める
        if (Math.abs(dx) < 0.001f && Math.abs(velocity) < 0.001f) {
            displayIndex = targetIndex;
            velocity = 0f;
        }
    }

    private static void updateOpenness(float dt) {
        float target = open ? 1f : 0f;
        float rate = open ? OPEN_RATE : CLOSE_RATE;
        openness += (target - openness) * (1f - (float) Math.exp(-rate * dt));
        if (open && openness > 0.999f) openness = 1f;
        if (!open && openness < 0.01f) openness = 0f;
    }

    private static float easeOut(float t){
        return 1 - (float) Math.pow(1 - t, 3);
    }

    private static float clamp01(float v) {
        return Math.min(Math.max(v, 0f), 1f);
    }

    private static int argb(float a, int rgb) {
        return (Math.round(clamp01(a) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpRgb(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static final ResourceLocation VIGNETTE_TEXTURE = ResourceLocation.tryBuild("tpsthings", "textures/gui/vignette.png");

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int w, int h) {
        if (!open && openness == 0f) return;

        long now = System.nanoTime();
        // dt計算(初回フレームは0扱い)
        float dt = lastRenderNanos == 0L ? 0f : Math.min((now - lastRenderNanos) / 1_000_000_000f, 1f / 30f);
        lastRenderNanos = now;
        updateSpring(dt);
        updateOpenness(dt);
        if (openness == 0f) return;

        float ms = (now - startTime) / 1_000_000f;
        float seconds = ms / 1000f;
        float sinceTransition = (now - transitionNanos) / 1_000_000_000f;

        float hue = (System.currentTimeMillis() % 3000) / 3000f;
        int tint = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF;

        Font font = gui.getFont();
        List<GuardSettings.Entry> rows = rows();
        int fullW = measureFullWidth(font, rows);

        // 縦: 画面に収まる間隔と表示範囲まで詰める。低い解像度では項目が上下にはみ出していた
        float spacing = Math.min(ITEM_SPACING, Math.max(12f, (h / 2f - 24f) / 3f));
        float visible = Math.min(VISIBLE_RANGE, Math.max(1f, (h / 2f - 24f) / spacing));

        // 横: 一番広がる状態でも右端を越えない位置へ寄せ、それでも入らなければ縮める
        float widest = fullW * MAX_ITEM_SCALE;
        float menuX = Math.min((float) w / 3 * 2, w - widest - EDGE_MARGIN);
        float contentScale = 1f;
        float minMenuX = w * 0.34f;
        if (menuX < minMenuX) {
            menuX = minMenuX;
            contentScale = clamp01((w - EDGE_MARGIN - minMenuX) / Math.max(widest, 1f));
        }

        // それまでに溜まっている HUD の描画を先に吐き出してから画面をコピーする
        guiGraphics.flush();
        if (!renderScreenEffect(guiGraphics, w, h, seconds, sinceTransition * 3, (menuX + 40f) / w, 0.5f, tint)) {
            renderVignette(guiGraphics, w, h, tint);
        }

        renderRail(guiGraphics, menuX - 12f, h, tint);
        renderItems(guiGraphics, font, rows, fullW, menuX, w, h, spacing, visible, contentScale,
                tint, seconds, sinceTransition, now);
        renderTitle(guiGraphics, font, ms, tint);
        renderInfoPanel(guiGraphics, font, menuX, h, now, tint);
    }

    private static TextureTarget screenCopy;

    /** 画面全体にシェーダーを掛ける。シェーダーが読めていなければ false */
    private static boolean renderScreenEffect(GuiGraphics guiGraphics, int w, int h, float time, float sinceTransition,
                                              float centerU, float centerV, int tint) {
        ShaderInstance shader = ClientRegister.getSugoiMenuScreenShader();
        if (shader == null) return false;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (screenCopy == null) {
            screenCopy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        } else if (screenCopy.width != main.width || screenCopy.height != main.height) {
            screenCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        // 書き込み中のターゲットは同時に読めないので、いまの画面を別ターゲットへ写してそれを読む
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, screenCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);

        shader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        shader.safeGetUniform("Openness").set(openness);
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("Center").set(centerU, centerV);
        shader.safeGetUniform("WaveAge").set(sinceTransition);
        shader.safeGetUniform("WaveSign").set(open ? 1f : -1f);
        shader.safeGetUniform("Tint").set(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f);

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, screenCopy.getColorTextureId());
        RenderSystem.disableBlend();
        // ホットバーのアイテムが書いた深度で一部だけ素通しにならないよう深度は無視
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // GL のテクスチャは下が v=0 なので上下を反転して貼る
        Matrix4f matrix = guiGraphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, 0, 0, 0).uv(0, 1).endVertex();
        buffer.vertex(matrix, 0, h, 0).uv(0, 0).endVertex();
        buffer.vertex(matrix, w, h, 0).uv(1, 0).endVertex();
        buffer.vertex(matrix, w, 0, 0).uv(1, 1).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        return true;
    }

    private static void renderVignette(GuiGraphics guiGraphics, int w, int h, int tint) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, openness * 0.3f);
        guiGraphics.blit(VIGNETTE_TEXTURE, 0, 0, 0, 0, w, h, w, h);
        guiGraphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** 項目の左に伸びる縦のレールと、選択位置のマーカー */
    private static void renderRail(GuiGraphics guiGraphics, float x, int h, int tint) {
        int len = Math.round(h * 0.45f * easeOut(openness));
        if (len <= 0) return;
        int rx = Math.round(x);
        int cy = h / 2;
        int solid = argb(0.8f * openness, tint);
        int clear = argb(0f, tint);
        guiGraphics.fillGradient(rx, cy - len, rx + 1, cy, clear, solid);
        guiGraphics.fillGradient(rx, cy, rx + 1, cy + len, solid, clear);
        guiGraphics.fill(rx - 2, cy - 2, rx + 3, cy + 3, argb(openness, 0xFFFFFF));
    }

    /** 値の列が揃うよう、ボックスの幅は全項目で共通にする */
    private static int measureFullWidth(Font font, List<GuardSettings.Entry> rows) {
        int labelW = 0;
        int valueW = 0;
        for (GuardSettings.Entry entry : rows) {
            labelW = Math.max(labelW, font.width(entry.label()));
            valueW = Math.max(valueW, font.width(entry.value()));
        }
        return ACCENT_W + BOX_PAD_X + labelW + (valueW > 0 ? VALUE_GAP + valueW : 0) + BOX_PAD_X;
    }

    private static void renderItems(GuiGraphics guiGraphics, Font font, List<GuardSettings.Entry> rows, int fullW,
                                    float menuX, int w, int h, float spacing, float visible, float contentScale,
                                    int tint, float time, float sinceTransition, long now) {
        // 閉じた瞬間に選択中の項目を白く光らせる
        float closeFlash = open ? 0f : clamp01(1f - sinceTransition / 0.22f);

        for (int i = 0; i < rows.size(); i++) {
            float diff = i - displayIndex;
            float dist = Math.abs(diff);
            if (dist > visible) continue;

            // 選択中に近いほど早く出て、閉じるときは最後まで残る
            float delay = Math.min(dist, 5f) * STAGGER;
            float progress = clamp01((openness - delay) / (1f - 5f * STAGGER));
            if (progress <= 0f) continue;
            float eased = easeOut(progress);
            float focus = clamp01(1f - dist);
            float alpha = eased * clamp01(1f - dist * 0.2f);

            float scale = (1f + 0.25f * focus + 0.2f * focus * closeFlash) * contentScale;
            // 離れた項目ほど右へ逃がして円弧状に並べる。ただし右端は越えさせない
            float x = menuX + ((1f - eased) * 80f + dist * dist * 3f) * contentScale;
            float overflow = (x + fullW * scale) - (w - EDGE_MARGIN);
            if (overflow > 0f) x -= overflow;
            float y = h / 2f + diff * spacing;

            GuardSettings.Entry entry = rows.get(i);
            Long changed = changedAt.get(entry.id());
            float changePulse = changed == null ? 0f
                    : 0.6f * clamp01(1f - (now - changed) / 1_000_000_000f / CHANGE_PULSE_SECONDS);

            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1f);
            renderItemBox(guiGraphics, font, entry, fullW, progress, focus, alpha, tint, time,
                    Math.max(closeFlash * focus, changePulse));
            pose.popPose();
        }
    }

    /** 原点 = ボックスの左端・縦中央 */
    private static void renderItemBox(GuiGraphics guiGraphics, Font font, GuardSettings.Entry entry, int fullW,
                                      float progress, float focus, float alpha, int tint, float time, float flash) {
        int boxW = Math.round(fullW * easeOut(clamp01(progress / 0.6f)));
        int halfH = Math.round(BOX_HALF_H * (0.35f + 0.65f * easeOut(clamp01(progress / 0.4f))));
        if (boxW <= 0 || halfH <= 0) return;
        int top = -halfH;
        int bottom = halfH;

        // 選択中は外側にぼんやり光る枠を重ねる
        if (focus > 0f) {
            for (int g = 1; g <= 3; g++) {
                outline(guiGraphics, -g, top - g, boxW + g, bottom + g, argb(alpha * focus * 0.3f / g, tint));
            }
        }
        guiGraphics.fillGradient(0, top, boxW, bottom, argb(alpha * 0.78f, 0x1A1A2E), argb(alpha * 0.88f, 0x05050C));
        guiGraphics.fill(0, top, boxW, bottom, argb(alpha * focus * 0.18f, tint));
        // 選択中のボックスを光の帯が横切る
        if (focus > 0f) {
            int sx = Math.round((time % 1.4f) / 1.4f * (boxW + 24)) - 12;
            int x0 = Math.max(sx, 0);
            int x1 = Math.min(sx + 8, boxW);
            if (x1 > x0) guiGraphics.fill(x0, top + 1, x1, bottom - 1, argb(alpha * focus * 0.18f, 0xFFFFFF));
        }
        outline(guiGraphics, 0, top, boxW, bottom, argb(alpha * (0.35f + 0.65f * focus), lerpRgb(0x8A8A9A, tint, focus)));
        guiGraphics.fill(0, top, ACCENT_W, bottom, argb(alpha, lerpRgb(0x55556A, tint, focus)));
        if (focus > 0f) {
            corners(guiGraphics, -4, top - 4, boxW + 4, bottom + 4, 4, argb(alpha * focus, 0xFFFFFF));
        }
        if (flash > 0f) {
            guiGraphics.fill(0, top, boxW, bottom, argb(alpha * flash * 0.7f, 0xFFFFFF));
        }

        // 文字はボックスが開ききる少し前から出す
        float textAlpha = alpha * clamp01((progress - 0.35f) / 0.65f);
        drawText(guiGraphics, font, entry.label(), ACCENT_W + BOX_PAD_X, -4, textAlpha,
                lerpRgb(0xB0B0B8, 0xFFFFFF, focus));
        if (!entry.value().isEmpty() && textAlpha > 0.02f) {
            // 値は右寄せで、色付きの札の上に置く
            int vw = font.width(entry.value());
            int vx = boxW - BOX_PAD_X - vw;
            guiGraphics.fill(vx - 3, -6, vx + vw + 3, 6, argb(textAlpha * 0.22f, entry.color()));
            drawText(guiGraphics, font, entry.value(), vx, -4, textAlpha, entry.color());
        }
    }

    private static void renderTitle(GuiGraphics guiGraphics, Font font, float ms, int tint) {
        String label = "Sugoi Menu " + Math.floor(ms / 100) / 10;
        float e = easeOut(openness);
        int x = Math.round(10 - (1 - e) * 60);
        int y = 10;
        int x0 = x - 6;
        int x1 = x + font.width(label) + 6;
        guiGraphics.fillGradient(x0, y - 5, x1, y + 13, argb(0.78f * e, 0x1A1A2E), argb(0.88f * e, 0x05050C));
        outline(guiGraphics, x0, y - 5, x1, y + 13, argb(0.8f * e, tint));
        guiGraphics.fill(x0, y - 5, x0 + ACCENT_W, y + 13, argb(e, tint));
        drawText(guiGraphics, font, label, x + 1, y, e, 0xFFFFFF);
    }

    /** タイトルの下に、選択中の項目の説明と直前の操作結果を出す */
    private static void renderInfoPanel(GuiGraphics guiGraphics, Font font, float menuX, int h, long now, int tint) {
        List<GuardSettings.Entry> rows = rows();
        GuardSettings.Entry selected = rows.get(clampIndex(targetIndex, rows.size()));
        float e = easeOut(openness);
        int x = Math.round(10 - (1 - e) * 60);
        int y = 32;
        // メニューの列に被らない幅で折り返す
        int wrap = Math.max(80, Math.round(menuX) - 60);

        List<FormattedCharSequence> description = selected.description().isEmpty()
                ? List.of() : font.split(Component.literal(selected.description()), wrap);
        float messageAge = (now - messageNanos) / 1_000_000_000f;
        float messageAlpha = lastMessage.isEmpty() ? 0f : clamp01((MESSAGE_SECONDS - messageAge) / 0.5f);
        List<FormattedCharSequence> message = messageAlpha > 0f
                ? font.split(Component.literal(lastMessage), wrap) : List.of();

        // 説明が長いときに下へ突き抜けないよう、入る行数で切る
        int roomLines = Math.max(0, (h - EDGE_MARGIN - (y + 3) - 11
                - (message.isEmpty() ? 0 : 4 + message.size() * 10)) / 10);
        if (description.size() > roomLines) description = description.subList(0, roomLines);

        int innerW = font.width(selected.label());
        for (FormattedCharSequence line : description) innerW = Math.max(innerW, font.width(line));
        for (FormattedCharSequence line : message) innerW = Math.max(innerW, font.width(line));
        int height = 11 + description.size() * 10 + (message.isEmpty() ? 0 : 4 + message.size() * 10);

        int x0 = x - 6;
        int x1 = x + innerW + 6;
        int y0 = y - 5;
        int y1 = y + height + 3;
        guiGraphics.fillGradient(x0, y0, x1, y1, argb(0.72f * e, 0x1A1A2E), argb(0.85f * e, 0x05050C));
        outline(guiGraphics, x0, y0, x1, y1, argb(0.5f * e, tint));
        guiGraphics.fill(x0, y0, x0 + ACCENT_W, y1, argb(e, selected.color()));

        drawText(guiGraphics, font, selected.label(), x + 1, y, e, 0xFFFFFF);
        int lineY = y + 11;
        for (FormattedCharSequence line : description) {
            drawText(guiGraphics, font, line, x + 1, lineY, e, 0xA8A8B4);
            lineY += 10;
        }
        if (!message.isEmpty()) {
            lineY += 4;
            for (FormattedCharSequence line : message) {
                drawText(guiGraphics, font, line, x + 1, lineY, e * messageAlpha, GuardSettings.WARN);
                lineY += 10;
            }
        }
    }

    private static void drawText(GuiGraphics guiGraphics, Font font, String text, int x, int y, float alpha, int rgb) {
        drawText(guiGraphics, font, Component.literal(text).getVisualOrderText(), x, y, alpha, rgb);
    }

    private static void drawText(GuiGraphics guiGraphics, Font font, FormattedCharSequence text, int x, int y, float alpha, int rgb) {
        int a = Math.round(clamp01(alpha) * 255);
        // Font は alpha が 0-3 だと不透明扱いにしてしまうので描かない
        if (a > 4) {
            guiGraphics.drawString(font, text, x, y, (a << 24) | (rgb & 0xFFFFFF), true);
        }
    }

    private static void outline(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
        guiGraphics.fill(x0, y0, x1, y0 + 1, color);
        guiGraphics.fill(x0, y1 - 1, x1, y1, color);
        guiGraphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        guiGraphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    /** 四隅のカギ括弧 */
    private static void corners(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int len, int color) {
        guiGraphics.fill(x0, y0, x0 + len, y0 + 1, color);
        guiGraphics.fill(x0, y0 + 1, x0 + 1, y0 + len, color);
        guiGraphics.fill(x1 - len, y0, x1, y0 + 1, color);
        guiGraphics.fill(x1 - 1, y0 + 1, x1, y0 + len, color);
        guiGraphics.fill(x0, y1 - 1, x0 + len, y1, color);
        guiGraphics.fill(x0, y1 - len, x0 + 1, y1 - 1, color);
        guiGraphics.fill(x1 - len, y1 - 1, x1, y1, color);
        guiGraphics.fill(x1 - 1, y1 - len, x1, y1 - 1, color);
    }
}
