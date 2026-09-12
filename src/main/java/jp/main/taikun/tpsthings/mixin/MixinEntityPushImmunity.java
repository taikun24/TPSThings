package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.DamageGuard;
import jp.main.taikun.tpsthings.items.ItemOo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装備由来の押し出し無効。
 *
 * 他エンティティとの接触も、外から加算される速度も、最後は {@code push} に集まる。
 * 着用者側の push だけを折るので、こちらが相手を押す分はそのまま残る。
 * 接触はクライアント側でも計算されるため、サーバ限定にはしない
 * (装備は同期されているので isWorn は両側で同じ答えを返す)。
 */
@Mixin(Entity.class)
public abstract class MixinEntityPushImmunity {

    /** 速度の加算 (接触・パンチ・一部の爆風など、push 経由のもの全部)。 */
    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void tpsthings$deflectPush(double x, double y, double z, CallbackInfo ci) {
        if ((Entity) (Object) this instanceof LivingEntity living && ItemOo.isWorn(living)) {
            ci.cancel();
        }
    }

    /** エンティティ同士の接触。呼ばれた側 (this) が着用者なら押されない。 */
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void tpsthings$deflectCollision(Entity other, CallbackInfo ci) {
        if ((Entity) (Object) this instanceof LivingEntity living && ItemOo.isWorn(living)) {
            ci.cancel();
        }
    }

    /**
     * 速度そのものの書き換え。
     *
     * ここは本人の通常移動 (travel / 摩擦 / 重力) も毎 tick 通るので、丸ごと折ると
     * 着用者自身が動けなくなる。呼び出し連鎖に他所の Mod のコードが居るときだけ拒否し、
     * バニラの物理と自分の書き戻しはそのまま通す。スタックを歩くのは安くないので、
     * 先に着用者かどうかで絞ってから見る。
     */
    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), cancellable = true)
    private void tpsthings$deflectDeltaMovement(Vec3 movement, CallbackInfo ci) {
        if ((Entity) (Object) this instanceof LivingEntity living && ItemOo.isWorn(living)
                && DamageGuard.foreignWriterOnStack()) {
            ci.cancel();
        }
    }
}
