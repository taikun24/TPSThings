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
 * - おお: 元のアイテムテクスチャ (oo_base モデル) の上に、L0〜L10 の 11 枚の殻を入れ子に重ねる。
 *   インベントリでは殻を大きくしてスロットからはみ出させ、テクスチャより手前に半透明で描く
 * RenderType.lightning() は POSITION_COLOR + 半透明加算なので、テクスチャ無しの
 * 発光ポリゴンにちょうどよい。
 */
public class PrismItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final Lazy<PrismItemRenderer> INSTANCE = Lazy.of(PrismItemRenderer::new);

    /** 八面体: 赤道半径と上下の頂点の高さ */
    private static final float RADIUS = 0.28F;
    private static final float HEIGHT = 0.42F;

    /** おおの殻: L0 (外側) から L10 (内側) までの 11 枚。スロットの端が 0.5 */
    private static final int SHELL_COUNT = 11;
    private static final float SHELL_OUTER = 0.46F;
    private static final float SHELL_INNER = 0.06F;
    private static final float SHELL_OUTER_GUI = 0.85F;
    private static final float SHELL_INNER_GUI = 0.10F;
    /** インベントリでの殻の不透明度 (0〜255)。外側 → 内側。奥の面はさらに GUI_BACK_FACE 倍 */
    private static final float GUI_ALPHA_OUTER = 36.0F;
    private static final float GUI_ALPHA_INNER = 10.0F;
    private static final float GUI_BACK_FACE = 0.4F;

    /** 表層のシアンと深層の赤紫。ツールチップの tooltip_layer / tooltip_oo と同じ色 */
    private static final int SURFACE = 0x1ACCE6;
    private static final int ABYSS = 0xE6146A;

    /** おおの元テクスチャの縮小率。インベントリでは殻が主役なので小さく */
    private static final float BASE_SCALE = 0.65F;
    private static final float BASE_SCALE_GUI = 0.5F;

    private static final class PolyRenderType extends RenderType {
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
            boolean gui = context == ItemDisplayContext.GUI;
            renderBaseModel(stack, poseStack, bufferSource, packedLight, packedOverlay, gui ? BASE_SCALE_GUI : BASE_SCALE);
            if (gui && bufferSource instanceof MultiBufferSource.BufferSource batched) {
                // テクスチャは固定バッファに溜まり、殻 (都度描き出されるバッファ) より後に描かれて上を覆っていた。
                // 先に描き切って、殻を必ず上に重ねる
                batched.endBatch();
            }
            renderShells(poseStack, bufferSource, time, gui);
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
     * おおの殻。外側 (L0) のシアンから内側 (L10) の赤紫へ、深い殻ほど速く回り、1 枚ごとに向きが入れ替わる。
     * 最奥には静かな白の核を置く。
     *
     * <p>インベントリでは半透明を奥から順に重ねる: 全ての殻の奥の面を外から内へ → 核 → 手前の面を内から外へ。
     * 凸な殻が入れ子になっているだけなので、面ごとのソートをしなくてもこの順で前後が正しくなる。
     */
    private void renderShells(PoseStack poseStack, MultiBufferSource bufferSource, float time, boolean gui) {
        VertexConsumer buffer = bufferSource.getBuffer(gui ? PolyRenderType.POLY_GUI : PolyRenderType.POLY_OVERLAY);
        float outer = gui ? SHELL_OUTER_GUI : SHELL_OUTER;
        float inner = gui ? SHELL_INNER_GUI : SHELL_INNER;

        Matrix4f[] matrices = new Matrix4f[SHELL_COUNT];
        for (int layer = 0; layer < SHELL_COUNT; layer++) {
            float speed = (18.0F + layer * 14.0F) * (layer % 2 == 0 ? 1.0F : -1.0F);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * speed + layer * 33.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(time * speed * 0.5F + layer * 21.0F));
            matrices[layer] = new Matrix4f(poseStack.last().pose());
            poseStack.popPose();
        }

        if (gui) {
            for (int layer = 0; layer < SHELL_COUNT; layer++) {
                shell(buffer, matrices[layer], layer, outer, inner, true, false);
            }
        } else {
            for (int layer = 0; layer < SHELL_COUNT; layer++) {
                shell(buffer, matrices[layer], layer, outer, inner, false, null);
            }
        }

        // 降りきった果ての白。ゆっくり脈打つ
        float pulse = 0.5F + 0.5F * Mth.sin(time * 2.0F);
        float core = (gui ? 0.07F : 0.035F) + 0.01F * pulse;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 90.0F));
        octahedron(buffer, poseStack.last().pose(), core, core * 1.3F, 0xFFFFFF, (int) (170 + 60 * pulse), null);
        poseStack.popPose();

        if (gui) {
            for (int layer = SHELL_COUNT - 1; layer >= 0; layer--) {
                shell(buffer, matrices[layer], layer, outer, inner, true, true);
            }
        }
    }

    private static void shell(VertexConsumer buffer, Matrix4f matrix, int layer, float outer, float inner,
                              boolean gui, @Nullable Boolean front) {
        float depth = layer / (float) (SHELL_COUNT - 1);
        float radius = Mth.lerp(depth, outer, inner);
        int alpha;
        if (gui) {
            // 半透明は中心で 22 枚 (11 枚の手前と奥) 重なる。外側の輪郭だけ残し、内側と奥の面は薄く
            float base = Mth.lerp(depth, GUI_ALPHA_OUTER, GUI_ALPHA_INNER);
            alpha = (int) (Boolean.FALSE.equals(front) ? base * GUI_BACK_FACE : base);
        } else {
            alpha = (int) Mth.lerp(depth, 28.0F, 60.0F);
        }
        octahedron(buffer, matrix, radius, radius * 1.15F, mixColor(SURFACE, ABYSS, depth), alpha, front);
    }

    /**
     * 八面体。面ごとに明るさを変えて、形 (稜線) が読めるようにする。
     *
     * @param front null なら全ての面、true なら手前の面だけ、false なら奥の面だけ。
     *              GUI では z が大きいほど手前
     */
    private static void octahedron(VertexConsumer buffer, Matrix4f matrix, float radius, float height,
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

    private static int mixColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
