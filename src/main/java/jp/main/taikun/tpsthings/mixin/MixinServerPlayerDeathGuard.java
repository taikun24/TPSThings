package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * プレイヤーの死亡処理の絞り所。
 *
 * {@code ServerPlayer#die} は親を呼ばずに死亡処理を全部自前でやり直すので、
 * {@link MixinLivingEntityDamageGuard} に刺した関所はプレイヤーには効かない。
 * 同じ判断をここにも通す。判断は共有していて、この階層に固有の知識は持たない。
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerDeathGuard {

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardDie(DamageSource source, CallbackInfo ci) {
        if (HealthGuard.shouldCancelDeath((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
