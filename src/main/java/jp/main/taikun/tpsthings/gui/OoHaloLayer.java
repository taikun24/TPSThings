package jp.main.taikun.tpsthings.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.main.taikun.tpsthings.items.ItemOo;
import net.minecraft.Util;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * おおを着ている者に被せる光。
 * - 頭上: 頭の動きについてくる静かな白の輪。光の粒が 3 つ巡る
 * - 背中: 表層〜索引層の 11 本の輪 (肩幅の 2 倍強) と、中心の淡い後光。深い輪ほど欠けて、欠け方がちらつく
 *
 * <p>描き方はアイテムの PrismItemRenderer と同じ加算の光。モデル空間は y が下向きで、1 = 16 ドット。
 */
public class OoHaloLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final float TAU = Mth.TWO_PI;

    /** 頭の上端 (首から 8 ドット) からさらに浮かせる高さ */
    private static final float HALO_Y = -0.72F;
    private static final float HALO_RADIUS = 0.30F;

    /** 背中の輪の中心。胴の中ほど、背中の面 (2 ドット) より後ろ */
    private static final float BACK_Y = 0.30F;
    /** 輪が首を振っても背中にめり込まないよう、半径に合わせて離す */
    private static final float BACK_Z = 0.50F;
    /** 背中の輪の半径。外側 (表層) → 内側 (索引層) */
    private static final float BACK_OUTER = 1.24F;
    private static final float BACK_INNER = 0.60F;

    public OoHaloLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!ItemOo.isWorn(entity) || entity.isInvisible()) {
            return;
        }
        float time = Util.getMillis() / 1000.0F;
        VertexConsumer buffer = buffers.getBuffer(PrismItemRenderer.PolyRenderType.POLY_OVERLAY);

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        renderHeadHalo(buffer, poseStack, time);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        renderBackRings(buffer, poseStack, time);
        poseStack.popPose();
    }

    private static void renderHeadHalo(VertexConsumer buffer, PoseStack poseStack, float time) {
        poseStack.pushPose();
        poseStack.translate(0.0F, HALO_Y + 0.02F * Mth.sin(time * 2.0F), 0.0F);
        // 少しだけ後ろに傾ける
        poseStack.mulPose(Axis.XP.rotationDegrees(-12.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 40.0F));
        Matrix4f matrix = poseStack.last().pose();

        float pulse = 0.5F + 0.5F * Mth.sin(time * 1.6F);
        // にじみ (太く薄い帯) → 芯 (細く明るい帯)
        ring(buffer, matrix, HALO_RADIUS, 0.055F, PrismItemRenderer.CALM, (int) (30 + 20 * pulse), 0, 0.0F, 0L, 48);
        ring(buffer, matrix, HALO_RADIUS, 0.018F, PrismItemRenderer.CALM, (int) (170 + 60 * pulse), 0, 0.0F, 0L, 48);

        for (int i = 0; i < 3; i++) {
            float angle = i * TAU / 3.0F;
            Matrix4f bead = new Matrix4f(matrix).translate(Mth.cos(angle) * HALO_RADIUS, 0, Mth.sin(angle) * HALO_RADIUS);
            PrismItemRenderer.octahedron(buffer, bead, 0.03F, 0.03F, 0xFFFFFF, 220, null);
        }
        poseStack.popPose();
    }

    private static void renderBackRings(VertexConsumer buffer, PoseStack poseStack, float time) {
        poseStack.pushPose();
        poseStack.translate(0.0F, BACK_Y, BACK_Z);

        // 中心の淡い後光 (背中の面 = XY 面に置く)
        float pulse = 0.5F + 0.5F * Mth.sin(time * 1.3F);
        Matrix4f flat = poseStack.last().pose();
        float glowRadius = 0.55F * (0.9F + 0.1F * pulse);
        final int discSegments = 32;
        for (int i = 0; i < discSegments; i++) {
            float a0 = i * TAU / discSegments;
            float a1 = (i + 1) * TAU / discSegments;
            PrismItemRenderer.quad(buffer, flat,
                    0, 0, 0, 0, 0, 0,
                    Mth.cos(a1) * glowRadius, Mth.sin(a1) * glowRadius, 0,
                    Mth.cos(a0) * glowRadius, Mth.sin(a0) * glowRadius, 0,
                    PrismItemRenderer.CALM, (int) (45 + 25 * pulse), PrismItemRenderer.CALM, 0);
        }

        // 表層 (外・途切れない) から索引層 (内・ちらつく欠け) まで 11 本
        for (int layer = 0; layer <= 10; layer++) {
            float depth = layer / 10.0F;
            float sign = layer % 2 == 0 ? 1.0F : -1.0F;
            poseStack.pushPose();
            // 背中の面に立てて、1 本ずつずれた周期で少しだけ首を振らせる
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F + Mth.sin(time * 0.7F + layer * 1.1F) * 8.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.cos(time * 0.5F + layer * 0.9F) * 6.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (20.0F + depth * 90.0F) * sign));
            float gapRatio = Math.max(0.0F, depth - 0.15F) * 0.55F;
            long frame = depth < 0.5F ? 0L : (long) (time * (2.0F + layer * 1.5F));
            int color = PrismItemRenderer.mixColor(PrismItemRenderer.SURFACE, PrismItemRenderer.ABYSS, depth);
            float radius = Mth.lerp(depth, BACK_OUTER, BACK_INNER);
            ring(buffer, poseStack.last().pose(), radius, 0.016F, color, 140, layer, gapRatio, frame, 96);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void ring(VertexConsumer buffer, Matrix4f matrix, float radius, float width, int color, int alpha,
                             int layer, float gapRatio, long frame, int segments) {
        for (int k = 0; k < segments; k++) {
            if (gapRatio > 0.0F && PrismItemRenderer.noise(k, layer, frame) < gapRatio) {
                continue;
            }
            float a0 = k * TAU / segments;
            float a1 = (k + 1) * TAU / segments;
            PrismItemRenderer.ringSegment(buffer, matrix, a0, a1, radius, width, color, alpha);
        }
    }
}
