package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.AutoGuard;
import jp.main.taikun.tpsthings.damage.DamageGuard;
import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ダメージ経路の絞り所。バニラ側にだけ刺す。
 *
 * hurt は通常のダメージ経路を拾う。HP そのものの書き込みは、setHealth ではなく
 * {@link MixinSynchedEntityDataGuard} 側の一段下の層で拾う。setHealth はそこへの
 * 入口の 1 つでしかなく、見張っても入口を通らない相手に素通しされるため。
 *
 * どちらも「誰が呼んだか」の判断は {@link DamageGuard} に委ね、ここは判断を持たない。
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDamageGuard {

    /** HP を運んでいる鍵。private static なので、ここでしか掴めない。 */
    @Shadow
    @Final
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;

    /**
     * HP の鍵を関所へ預ける。
     *
     * これは Entity のコンストラクタから呼ばれるので、どの生き物であれ
     * 最初のダメージより確実に前に通る。
     */
    @Inject(method = "defineSynchedData", at = @At("HEAD"))
    private void tpsthings$captureHealthKey(CallbackInfo ci) {
        HealthGuard.rememberHealthKey(DATA_HEALTH_ID);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (DamageGuard.shouldBlock(self, DamageGuard.Kind.HURT, amount)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 死亡処理そのものの絞り所。
     *
     * HP を削らずに直接ここを呼ばれると、HP を見張る関所には何も映らない。
     * プレイヤーは {@code ServerPlayer} 側が親を呼ばずに全部やり直すので、
     * そちらは {@link MixinServerPlayerDeathGuard} が別に受け持つ。
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardDie(DamageSource source, CallbackInfo ci) {
        if (HealthGuard.shouldCancelDeath((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * 遺品を撒く処理。死亡処理の外から直接呼ばれると、死を拒否しても持ち物だけ失う。
     *
     * <p>装備由来の保護はここで剥がれる。守ったのに次の一撃で死ぬ、の原因になる。
     */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardDeathLoot(DamageSource source, CallbackInfo ci) {
        if (HealthGuard.shouldRefuseDeathLoot((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /** 死体の後始末。止めないと、死亡処理を拒否しても最後にここで消される。 */
    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardTickDeath(CallbackInfo ci) {
        if (HealthGuard.shouldRefuseDeathTick((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * 絞り所を通らずに HP が減っていないかを毎 tick 見張る。
     *
     * 呼び出し元ではなく実際の HP を見るので、reflection でのフィールド直書きも捕まる。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void tpsthings$watchHealth(CallbackInfo ci) {
        AutoGuard.onTick((LivingEntity) (Object) this);
    }
}
