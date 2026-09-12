package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HP の関所。同期データの出入口そのものに置く。
 *
 * {@code LivingEntity#setHealth} はここへ書きに来る入口の 1 つでしかないので、
 * setHealth を見張るだけでは自前のダメージ処理を持つ Mod を取り逃がす。
 * 判断は {@link HealthGuard} に委ね、ここは判断を持たない。
 *
 * <p>2 引数の {@code set} は 3 引数へ委譲するので、3 引数の側だけ見ればよい。
 */
@Mixin(SynchedEntityData.class)
public abstract class MixinSynchedEntityDataGuard {

    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD"), cancellable = true)
    private <T> void tpsthings$guardHealthWrite(EntityDataAccessor<T> key, T value, boolean force,
                                                CallbackInfo ci) {
        if (HealthGuard.shouldBlockWrite(entity, key, value)) {
            ci.cancel();
        }
    }

    /**
     * 読み出し側。既定では何もしない。
     *
     * 封印中だけ、保護対象の HP を最後に通した値で返す。書き込みを止めても、
     * 相手が HP を読んで別経路で殺しに来るなら止まらないため。
     */
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void tpsthings$floorHealthRead(EntityDataAccessor<T> key,
                                               CallbackInfoReturnable<T> cir) {
        Float floor = HealthGuard.floorFor(entity, key);
        if (floor != null) {
            @SuppressWarnings("unchecked")
            T value = (T) floor;
            cir.setReturnValue(value);
        }
    }
}
