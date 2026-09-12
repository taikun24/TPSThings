package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HP の関所の、一番下の段。値が実際に載っている箱そのものに置く。
 *
 * <p>{@code SynchedEntityData#set} は<b>入口の 1 つ</b>でしかない。番号引きの入れ物から
 * 箱を掴んで直接書けば、入口の関所は 1 つも通らずに HP が変わる。実測でそうなっていた —
 * 封印中・保護対象・自分の操作でない、の 3 つが揃っているのに値が
 * {@code -1.70} まで落ちていた。{@code setHealth} は 0 未満に丸めるので、
 * <b>負の値そのものが「入口を通っていない」証拠</b>になっている。
 *
 * <p>ここは持ち主を知らないので、判断は {@link HealthGuard} に委ねる。
 * 保護対象の箱かどうかは、毎 tick 控えておいた対応表で引く。
 */
@Mixin(SynchedEntityData.DataItem.class)
public abstract class MixinDataItemGuard {

    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardItemWrite(Object value, CallbackInfo ci) {
        if (HealthGuard.shouldBlockItemWrite(this, value)) {
            ci.cancel();
        }
    }
}
