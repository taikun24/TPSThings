package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.DamageGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 強制移動の絞り所。
 *
 * 位置は {@code setPosRaw}、速度は {@code setDeltaMovement} に集まる。
 * 移動系はどちらもバニラの通常移動で毎 tick 通る一番熱い経路なので、
 * {@link DamageGuard#isMotionGuard()} が入っていないときは即座に抜ける。
 */
@Mixin(Entity.class)
public abstract class MixinEntityMotionGuard {

    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardPosition(double x, double y, double z, CallbackInfo ci) {
        if (!DamageGuard.isMotionGuard()) {
            return;
        }
        if (DamageGuard.shouldBlock((Entity) (Object) this, DamageGuard.Kind.MOVE, 0.0F)) {
            ci.cancel();
        }
    }

    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardVelocity(Vec3 movement, CallbackInfo ci) {
        if (!DamageGuard.isMotionGuard()) {
            return;
        }
        if (DamageGuard.shouldBlock((Entity) (Object) this, DamageGuard.Kind.VELOCITY, 0.0F)) {
            ci.cancel();
        }
    }
}
