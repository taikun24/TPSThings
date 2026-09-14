package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.gui.ClientDeathGuard;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 入れ物の画面が「自機は死んでいる」と判断して自分を閉じる口の関所。
 *
 * <p>画面は毎 tick {@code isAlive() && !isRemoved()} を聞き、偽なら閉じる。この問いの答えは
 * <b>呼び出し箇所ごと</b>差し替えられることがあり、しかも差し替えはクラスの生のバイト列の段階
 * (Mixin より前) で入るので、こちらがどこから聞き直しても同じ嘘は観測できない。
 *
 * <p>だから答えではなく<b>結果 (閉じる呼び出し)</b> の側で拒む。死亡画面の関所
 * ({@link MixinMinecraftDeathGuard}) と同じ考え方で、誰の嘘かに依存しない。
 * 閉じなかった場合は、嘘が無ければ走っていたはずの画面の毎 tick 処理を代わりに回す。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreenDeathGuard {

    @Shadow
    protected abstract void containerTick();

    @Inject(method = "tick", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;closeContainer()V"))
    private void tpsthings$refuseSpuriousClose(CallbackInfo ci) {
        if (ClientDeathGuard.refuseContainerClose()) {
            containerTick();
            ci.cancel();
        }
    }
}
