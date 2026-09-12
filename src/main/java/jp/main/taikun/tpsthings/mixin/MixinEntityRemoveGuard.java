package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.AutoGuard;
import jp.main.taikun.tpsthings.damage.DamageGuard;
import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 削除の絞り所。
 *
 * HP を削るのではなくエンティティごと消しにくる経路は、HP を見ている限り永久に検出できない。
 * 消す側も止められるように、ここでも呼び出し元を特定してブロックリストと照合する。
 *
 * <p><b>関所は {@code remove} ではなく {@code setRemoved} に置く。</b>
 * {@code Entity.remove(reason)} は中身が {@code this.setRemoved(reason)} を呼ぶだけで、
 * 状態フリップ ({@code removalReason} 設定) も索引削除 ({@code levelCallback.onRemove})
 * も全部 {@code setRemoved} がやる。つまり {@code setRemoved} を直接呼ばれると
 * {@code remove} の @Inject は素通りする (実際にそう呼んでくる Mod が居る)。真の関所である
 * {@code setRemoved} の入口で押さえれば、{@code remove}・{@code discard}・直呼びを 1 点で捕まえ、
 * かつ removalReason が立つ<b>前</b>なので検知 ({@code onRemoveAttempt}) も正しく走る。
 */
@Mixin(Entity.class)
public abstract class MixinEntityRemoveGuard {

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        // 次元移動・チャンクの畳み・ログアウトも同じ削除を通る。それを観測に混ぜると、
        // 正規の移動経路が「消しに来た犯人」として自動対処の容疑者に並ぶ
        if (!HealthGuard.isHostileRemoval(reason)) {
            return;
        }
        Entity self = (Entity) (Object) this;
        // 誰の仕業かは封印中でも記録しておきたいので、先に絞り所を通す
        boolean blocked = DamageGuard.shouldBlock(self, DamageGuard.Kind.REMOVE, 0.0F);
        if (blocked || HealthGuard.shouldRefuseRemoval(self, reason)) {
            ci.cancel();
            return;
        }
        AutoGuard.onRemoveAttempt(self);
    }
}
