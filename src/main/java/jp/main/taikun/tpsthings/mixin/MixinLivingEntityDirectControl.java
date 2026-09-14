package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.items.DirectControl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 直接操縦中は、自機の通常の移動 (重力・摩擦・move) を走らせない。
 *
 * 位置は tick の最後に {@code DirectControlClient} が決めるので、ここで動かしても上書きされるだけ。
 * 止めておくと、途中の移動に付いてくる他所の割り込みも起きない。
 * 他のプレイヤーやサーバ側の自機は動かし方が違うので、手元の自機だけを見る。
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDirectControl {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void tpsthings$directControl(Vec3 input, CallbackInfo ci) {
        if ((Object) this instanceof Player player && player.isLocalPlayer() && DirectControl.isActive(player)) {
            ci.cancel();
        }
    }
}
