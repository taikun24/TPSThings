package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.gui.ClientDeathGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 死亡画面を出す口の関所。
 *
 * <p>2 か所ある。外から死亡画面を渡してくる道 (死の報せのパケット) と、
 * 画面を閉じようとしたときに<b>自機が死んでいると答えたので代わりに開かれる</b>道。
 * 後者は毎 tick 繰り返されるので、閉じても閉じても開き直す。どちらも塞ぐ。
 *
 * <p>判断は {@link ClientDeathGuard} に委ね、ここは判断を持たない。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftDeathGuard {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void tpsthings$refuseDeathScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof DeathScreen && ClientDeathGuard.suppress()) {
            ci.cancel();
        }
    }

    /**
     * 画面を閉じる要求が死亡画面にすり替わるのを止める。
     *
     * <p>すり替えは「引数に死亡画面を代入する」形で起きるので、<b>代入された直後の値</b>を
     * 見て捨てる。null に戻せば、そのまま「画面を閉じる」として続く。
     *
     * <p>作る側 (コンストラクタ) を横取りして null を返す形は使えない —
     * Mixin が「コンストラクタの代わりに null を返した」と見て落とす。
     *
     * <p>生死の答えそのものを横取りする形も使えない。生死の読み出しは<b>呼び出し箇所ごと</b>
     * 書き換えられていることがあり、こちらが差し替えた戻り値の上から嘘を被せられる。
     * 嘘の結果 (出来上がった画面) を捨てる方が、誰の嘘かに依存しない。
     */
    @ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true, require = 0)
    private Screen tpsthings$dropSpuriousDeathScreen(Screen screen) {
        return screen instanceof DeathScreen && ClientDeathGuard.suppress() ? null : screen;
    }
}
