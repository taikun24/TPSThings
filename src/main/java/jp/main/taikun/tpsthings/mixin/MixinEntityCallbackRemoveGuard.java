package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 索引から外す仕事を実際にやっている場所の関所。
 *
 * <p>{@code Entity#setRemoved} は「後始末の繋がりへ知らせる」だけで、区画から外し、
 * tick と追跡を止め、台帳から消すのは<b>この繋がりの側</b>。繋がりを直接叩けば
 * {@code setRemoved} の関所も、{@code EntityLookup#remove} の関所も素通りする
 * (後者はこの中で追跡停止が済んだ<b>後</b>に来るので、着いた時には手遅れになる)。
 *
 * <p>判断は除去の関所と同じものを使う。ここだけ独自の判断を持たせると、
 * 片方だけ通る抜け道がまた生まれる。
 */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public abstract class MixinEntityCallbackRemoveGuard {

    /** Forge が足している、実体そのものへの参照 (この繋がりの持ち主)。 */
    @Shadow
    @Final
    private Entity realEntity;

    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardCallbackRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (realEntity != null && HealthGuard.shouldRefuseRemoval(realEntity, reason)) {
            ci.cancel();
        }
    }
}
