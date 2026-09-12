package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.items.ItemOo;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装備由来のノックバック無効。
 *
 * 攻撃のノックバックは {@code push} を通らず、{@code knockback} が
 * deltaMovement を直接書き換える。速度の加算を防ぐなら、この口も別に塞ぐ必要がある。
 * (属性の KNOCKBACK_RESISTANCE でも近いことはできるが、無視して直接
 * setDeltaMovement する Mod には効かないので、口の側で折る)
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityKnockbackImmunity {

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void tpsthings$deflectKnockback(double strength, double x, double z, CallbackInfo ci) {
        if (ItemOo.isWorn((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
