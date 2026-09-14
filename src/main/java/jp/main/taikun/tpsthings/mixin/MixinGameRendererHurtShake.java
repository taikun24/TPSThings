package jp.main.taikun.tpsthings.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.main.taikun.tpsthings.items.ItemOo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装備由来の被ダメージ時のカメラの揺れ無効。
 *
 * 揺れ (と死亡時の傾き) は hurtTime から毎フレーム {@code bobHurt} で視点に掛けられる。
 * hurtTime 自体は他の表示にも使われるので、値ではなく視点に掛ける口の側で折る。
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererHurtShake {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tpsthings$suppressHurtShake(PoseStack pose, float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().getCameraEntity() instanceof LivingEntity living && ItemOo.isWorn(living)) {
            ci.cancel();
        }
    }
}
