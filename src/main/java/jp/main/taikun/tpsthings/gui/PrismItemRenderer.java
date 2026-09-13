package jp.main.taikun.tpsthings.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import jp.main.taikun.tpsthings.items.ItemOo;
import jp.main.taikun.tpsthings.registries.ClientRegister;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.Lazy;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * カスタムアイテム描画。
 * - プリズム: 回転する八面体のみ
 * - おお: 元のアイテムテクスチャ (oo_base モデル) を、表層〜索引層の 11 本の輪が天球儀のように取り巻き、
 *   背後に静かな白の後光を置く。インベントリでは輪がスロットから大きくはみ出し、
 *   奥半分はテクスチャの後ろ、手前半分は前に描く
 * RenderType.lightning() は POSITION_COLOR + 半透明加算なので、テクスチャ無しの
 * 発光ポリゴンにちょうどよい。
 */
public class PrismItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final Lazy<PrismItemRenderer> INSTANCE = Lazy.of(PrismItemRenderer::new);

    /** 八面体: 赤道半径と上下の頂点の高さ */
    private static final float RADIUS = 0.28F;
    private static final float HEIGHT = 0.42F;

    private static final float TAU = Mth.TWO_PI;

    /**
     * おおの輪: 表層 (外側) から索引層 (内側) までの 11 本。スロットの端が 0.5 なので、
     * インベントリでは外側の輪はスロット 3 つ分ほどまではみ出す。
     * 中心は空けておき、おおのテクスチャが正面から見えるようにする。
     */
    private static final int RING_COUNT = 11;
    private static final int RING_SEGMENTS = 72;
    private static final float RING_OUTER = 0.80F;
    private static final float RING_INNER = 0.40F;
    private static final float RING_WIDTH = 0.016F;
    private static final float RING_OUTER_GUI = 1.75F;
    private static final float RING_INNER_GUI = 0.62F;
    private static final float RING_WIDTH_GUI = 0.035F;

    /** おおの背後の後光の半径 */
    private static final float HALO_RADIUS = 0.42F;
    private static final float HALO_RADIUS_GUI = 0.85F;

    /** 表層のシアンと深層の赤紫。ツールチップの tooltip_layer / tooltip_oo と同じ色 */
    static final int SURFACE = 0x1ACCE6;
    static final int ABYSS = 0xE6146A;
    /** 降りきった果ての静かな白 */
    static final int CALM = 0xE8EEFF;

    /** おおの元テクスチャの縮小率 */
    private static final float BASE_SCALE = 0.65F;
    private static final float BASE_SCALE_GUI = 0.75F;

    static final class PolyRenderType extends RenderType {
        private PolyRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        /**
         * 手に持ったとき・落ちているとき用。lightning と同じ POSITION_COLOR + 加算ブレンドだが、
         * 深度バッファに書き込まない (COLOR_WRITE)。lightning は深度を書くため、
         * ポリゴンの奥にあるテクスチャが深度テストで消えてしまう。
         * 加算ブレンドは描画順に依存しないのでソートも不要。
         */
        static final RenderType POLY_OVERLAY = create("tpsthings_poly_overlay",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        /**
         * インベントリ用。明るい背景では加算だと白く飛んで色が見えないので、普通の半透明で重ねる。
         * 深度を見ないのでテクスチャにも隣のスロットにも隠れない。重なりの順番は描く側で奥から並べる。
         */
        static final RenderType POLY_GUI = create("tpsthings_poly_gui",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    public PrismItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float time = Util.getMillis() / 1000.0F;
        if (stack.getItem() instanceof ItemOo) {
            Matrix4f[] rings = ringMatrices(poseStack, time);
            if (context == ItemDisplayContext.GUI && bufferSource instanceof MultiBufferSource.BufferSource batched) {
                // インベントリは深度を見ないので、奥から順に描いて前後を作る:
                // 後光 → 輪の奥半分 → おおのテクスチャ → 輪の手前半分。輪がおおの周りを回って見える。
                // テクスチャは固定バッファに溜まって後から描かれるので、段ごとに描き切る
                VertexConsumer back = batched.getBuffer(PolyRenderType.POLY_GUI);
                renderHalo(back, poseStack, time, true);
                renderRings(back, rings, time, true, false);
                batched.endBatch(PolyRenderType.POLY_GUI);
                renderBaseModel(stack, poseStack, bufferSource, packedLight, packedOverlay, BASE_SCALE_GUI);
                batched.endBatch();
                renderRings(batched.getBuffer(PolyRenderType.POLY_GUI), rings, time, true, true);
            } else {
                // 手に持った時・落ちている時は加算の光なので、順番を気にせず全部描く
                renderBaseModel(stack, poseStack, bufferSource, packedLight, packedOverlay, BASE_SCALE);
                VertexConsumer glow = bufferSource.getBuffer(PolyRenderType.POLY_OVERLAY);
                renderHalo(glow, poseStack, time, false);
                renderRings(glow, rings, time, false, null);
            }
        } else {
            renderOctahedron(poseStack, bufferSource, time);
        }
    }

    /**
     * 元のアイテムテクスチャ (oo_base) を描く。
     * ItemRenderer.render は -0.5 平行移動を再度行うので、中心に戻してから渡す。
     * ItemDisplayContext.NONE なら表示トランスフォームの二重適用も起きない。
     */
    private static void renderBaseModel(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
                                        int packedLight, int packedOverlay, float scale) {
        BakedModel base = Minecraft.getInstance().getModelManager().getModel(ClientRegister.OO_BASE_MODEL);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.NONE, false,
                poseStack, bufferSource, packedLight, packedOverlay, base);
        poseStack.popPose();
    }

    private void renderOctahedron(PoseStack poseStack, MultiBufferSource bufferSource, float time) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 60.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(PolyRenderType.POLY_OVERLAY);

        Vector3f top = new Vector3f(0, HEIGHT, 0);
        Vector3f bottom = new Vector3f(0, -HEIGHT, 0);
        Vector3f[] equator = equator(RADIUS);

        for (int i = 0; i < 4; i++) {
            Vector3f a = equator[i];
            Vector3f b = equator[(i + 1) % 4];
            triangle(buffer, matrix, top, a, b, faceColor(i, 8, time), 200);
            triangle(buffer, matrix, bottom, b, a, faceColor(i + 4, 8, time), 200);
        }

        poseStack.popPose();
    }

    /**
     * 輪 1 本ずつの姿勢。天球儀のように傾きの軸を 1 本ごとにずらし、全体をゆっくり歳差させる。
     * 深い輪ほど自分の面の中で速く回り (欠けと光の粒が流れる)、1 本ごとに向きが入れ替わる。
     */
    private static Matrix4f[] ringMatrices(PoseStack poseStack, float time) {
        Matrix4f[] matrices = new Matrix4f[RING_COUNT];
        for (int layer = 0; layer < RING_COUNT; layer++) {
            float depth = layer / (float) (RING_COUNT - 1);
            float sign = layer % 2 == 0 ? 1.0F : -1.0F;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (8.0F + depth * 30.0F) * sign + layer * 32.7F));
            poseStack.mulPose(Axis.XP.rotationDegrees(58.0F + layer * 23.0F + Mth.sin(time * 0.6F + layer) * 12.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(layer * 41.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (25.0F + depth * 160.0F) * sign));
            matrices[layer] = new Matrix4f(poseStack.last().pose());
            poseStack.popPose();
        }
        return matrices;
    }

    /**
     * 11 本の輪。外側 (表層) のシアンから内側 (索引層) の赤紫へ。
     * 表層の輪は途切れない円で、深くなるほど欠けが増え、深層では欠ける位置がちらついて入れ替わる
     * (ツールチップの「深いほど崩れる」と同じ文法)。各輪を白い光の粒が尾を引いて周回する。
     *
     * @param front null なら全部、true なら手前半分だけ、false なら奥半分だけ。GUI では z が大きいほど手前
     */
    private static void renderRings(VertexConsumer buffer, Matrix4f[] matrices, float time, boolean gui,
                                    @Nullable Boolean front) {
        float outer = gui ? RING_OUTER_GUI : RING_OUTER;
        float inner = gui ? RING_INNER_GUI : RING_INNER;
        float width = gui ? RING_WIDTH_GUI : RING_WIDTH;
        for (int layer = 0; layer < RING_COUNT; layer++) {
            float depth = layer / (float) (RING_COUNT - 1);
            float radius = Mth.lerp(depth, outer, inner);
            int color = mixColor(SURFACE, ABYSS, depth);
            Matrix4f matrix = matrices[layer];
            float centerZ = matrix.transformPosition(new Vector3f()).z;

            // 欠け: 表層 2 本は無し。中層は位置が固定の破線、深層は入れ替わり続ける
            float gapRatio = Math.max(0.0F, depth - 0.15F) * 0.55F;
            long frame = depth < 0.5F ? 0L : (long) (time * (2.0F + layer * 1.5F));

            // 光の粒の角度。輪ごとに速さと出発点をずらす
            float bead = Mth.positiveModulo(time * (0.25F + depth * 0.8F) + layer * 0.37F, 1.0F) * TAU;

            for (int k = 0; k < RING_SEGMENTS; k++) {
                if (gapRatio > 0.0F && noise(k, layer, frame) < gapRatio) {
                    continue;
                }
                float a0 = k * TAU / RING_SEGMENTS;
                float a1 = (k + 1) * TAU / RING_SEGMENTS;
                float mid = (a0 + a1) * 0.5F;
                if (front != null) {
                    float z = matrix.transformPosition(new Vector3f(Mth.cos(mid) * radius, 0, Mth.sin(mid) * radius)).z;
                    if ((z > centerZ) != front) {
                        continue;
                    }
                }
                // 粒の後ろ (通り過ぎた側) ほど尾が薄れる
                float behind = Mth.positiveModulo(bead - mid, TAU);
                float tail = (float) Math.exp(-behind * 2.2F);
                int segmentColor = mixColor(color, 0xFFFFFF, tail * 0.85F);
                int alpha;
                if (gui) {
                    alpha = Boolean.FALSE.equals(front) ? 70 : 170;
                } else {
                    alpha = 110;
                }
                alpha = Math.min(255, alpha + (int) (tail * 85.0F));
                ringSegment(buffer, matrix, a0, a1, radius, width, segmentColor, alpha);
            }

            // 粒の頭
            float bx = Mth.cos(bead) * radius;
            float bz = Mth.sin(bead) * radius;
            Matrix4f beadMatrix = new Matrix4f(matrix).translate(bx, 0, bz);
            if (front == null || (beadMatrix.transformPosition(new Vector3f()).z > centerZ) == front) {
                float size = width * 2.4F;
                octahedron(buffer, beadMatrix, size, size, 0xFFFFFF, gui ? 240 : 200, null);
            }
        }
    }

    /**
     * 輪の 1 区間。面の中に寝かせた帯と、面に垂直に立てた帯を十字に重ねる。
     * 片方だけだと、輪を真横から見たときに消えてしまう。
     */
    static void ringSegment(VertexConsumer buffer, Matrix4f matrix, float a0, float a1,
                                    float radius, float width, int color, int alpha) {
        float c0 = Mth.cos(a0);
        float s0 = Mth.sin(a0);
        float c1 = Mth.cos(a1);
        float s1 = Mth.sin(a1);
        float rIn = radius - width;
        float rOut = radius + width;
        quad(buffer, matrix,
                c0 * rIn, 0, s0 * rIn, c0 * rOut, 0, s0 * rOut,
                c1 * rOut, 0, s1 * rOut, c1 * rIn, 0, s1 * rIn, color, alpha, color, alpha);
        quad(buffer, matrix,
                c0 * radius, -width, s0 * radius, c0 * radius, width, s0 * radius,
                c1 * radius, width, s1 * radius, c1 * radius, -width, s1 * radius, color, alpha, color, alpha);
    }

    /**
     * おおの背後の後光。静かな白の円盤がゆっくり脈打ち、細い光の筋がゆっくり回る。
     * テクスチャと同じ面 (XY) に、少し奥へずらして置く。
     */
    private static void renderHalo(VertexConsumer buffer, PoseStack poseStack, float time, boolean gui) {
        float pulse = 0.5F + 0.5F * Mth.sin(time * 1.6F);
        float radius = (gui ? HALO_RADIUS_GUI : HALO_RADIUS) * (0.9F + 0.1F * pulse);
        int centerAlpha = gui ? (int) (110 + 50 * pulse) : (int) (60 + 30 * pulse);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, gui ? 0.40F : 0.44F);
        Matrix4f matrix = poseStack.last().pose();

        // 円盤: 中心が明るく、縁で消える
        final int discSegments = 32;
        for (int i = 0; i < discSegments; i++) {
            float a0 = i * TAU / discSegments;
            float a1 = (i + 1) * TAU / discSegments;
            quad(buffer, matrix,
                    0, 0, 0, 0, 0, 0,
                    Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0, Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0,
                    CALM, centerAlpha, CALM, 0);
        }

        // 光の筋: 12 本、長さがそれぞれ別の周期で伸び縮みする
        final int rays = 12;
        for (int i = 0; i < rays; i++) {
            float angle = i * TAU / rays + time * 0.2F;
            float spread = 0.045F;
            float length = radius * (1.25F + 0.45F * Mth.sin(time * 2.0F + i * 1.7F));
            quad(buffer, matrix,
                    0, 0, 0, 0, 0, 0,
                    Mth.cos(angle + spread) * length, Mth.sin(angle + spread) * length, 0,
                    Mth.cos(angle - spread) * length, Mth.sin(angle - spread) * length, 0,
                    CALM, (int) (centerAlpha * 0.7F), CALM, 0);
        }
        poseStack.popPose();
    }

    /** 四角形 1 枚。最初の 2 頂点を inner の色、残りを outer の色で塗る (中心から縁へのぼかし用) */
    static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             int innerColor, int innerAlpha, int outerColor, int outerAlpha) {
        int ir = (innerColor >> 16) & 0xFF;
        int ig = (innerColor >> 8) & 0xFF;
        int ib = innerColor & 0xFF;
        int or = (outerColor >> 16) & 0xFF;
        int og = (outerColor >> 8) & 0xFF;
        int ob = outerColor & 0xFF;
        buffer.vertex(matrix, x0, y0, z0).color(ir, ig, ib, innerAlpha).endVertex();
        buffer.vertex(matrix, x1, y1, z1).color(ir, ig, ib, innerAlpha).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(or, og, ob, outerAlpha).endVertex();
        buffer.vertex(matrix, x3, y3, z3).color(or, og, ob, outerAlpha).endVertex();
    }

    /** 区間の位置・輪・フレームから 0〜1 の値を決める。ItemLayer の名前の崩れと同じ混ぜ方 */
    static float noise(int segment, int layer, long frame) {
        long h = segment * 0x9E3779B97F4A7C15L + layer * 0xD6E8FEB86659FD93L + frame * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * 八面体。面ごとに明るさを変えて、形 (稜線) が読めるようにする。
     *
     * @param front null なら全ての面、true なら手前の面だけ、false なら奥の面だけ。
     *              GUI では z が大きいほど手前
     */
    static void octahedron(VertexConsumer buffer, Matrix4f matrix, float radius, float height,
                                   int color, int alpha, @Nullable Boolean front) {
        Vector3f top = new Vector3f(0, height, 0);
        Vector3f bottom = new Vector3f(0, -height, 0);
        Vector3f[] equator = equator(radius);
        float centerZ = matrix.transformPosition(new Vector3f()).z;
        for (int i = 0; i < 4; i++) {
            Vector3f a = equator[i];
            Vector3f b = equator[(i + 1) % 4];
            face(buffer, matrix, top, a, b, shade(color, 1.0F - i * 0.1F), alpha, front, centerZ);
            face(buffer, matrix, bottom, b, a, shade(color, 0.7F - i * 0.1F), alpha, front, centerZ);
        }
    }

    private static void face(VertexConsumer buffer, Matrix4f matrix, Vector3f v1, Vector3f v2, Vector3f v3,
                             int color, int alpha, @Nullable Boolean front, float centerZ) {
        if (front != null) {
            float z = (matrix.transformPosition(new Vector3f(v1)).z
                    + matrix.transformPosition(new Vector3f(v2)).z
                    + matrix.transformPosition(new Vector3f(v3)).z) / 3.0F;
            if ((z > centerZ) != front) {
                return;
            }
        }
        triangle(buffer, matrix, v1, v2, v3, color, alpha);
    }

    private static Vector3f[] equator(float radius) {
        return new Vector3f[]{
                new Vector3f(radius, 0, 0),
                new Vector3f(0, 0, radius),
                new Vector3f(-radius, 0, 0),
                new Vector3f(0, 0, -radius),
        };
    }

    /** RenderType.lightning() は QUADS なので、最後の頂点を重複させて三角形として出す */
    private static void triangle(VertexConsumer buffer, Matrix4f matrix,
                                 Vector3f v1, Vector3f v2, Vector3f v3, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        buffer.vertex(matrix, v1.x, v1.y, v1.z).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, v2.x, v2.y, v2.z).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, v3.x, v3.y, v3.z).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, v3.x, v3.y, v3.z).color(r, g, b, alpha).endVertex();
    }

    private static int faceColor(int faceIndex, int faceCount, float time) {
        float hue = Mth.positiveModulo(time * 0.25F + (float) faceIndex / faceCount, 1.0F);
        return Mth.hsvToRgb(hue, 0.75F, 1.0F);
    }

    private static int shade(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    static int mixColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
