package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.gui.SugoiMenuOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import net.minecraft.client.server.IntegratedServer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SugoiMenu を出している間、シングルプレイならゲームをポーズする。
 *
 * バニラは runTick の末尾で「ポーズ画面が開いているか」だけを見て pause を決め直すので、
 * 画面 (Screen) ではないオーバーレイは毎フレーム解除される。その判定の直前に状態を控え、
 * 直後にメニューが出ていれば解除を巻き戻す。判定式そのものは局所変数なので触らない
 * (序数指定の ModifyVariable は Forge のパッチ次第でずれる)。
 *
 * マルチプレイと LAN 公開中はバニラのポーズ画面と同じく止めない。止められない。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftMenuPause {

    @Shadow private volatile boolean pause;
    @Shadow private float pausePartialTick;
    @Shadow @Final private Timer timer;

    @Shadow public abstract boolean hasSingleplayerServer();

    @Shadow @Nullable public abstract IntegratedServer getSingleplayerServer();

    /** サーバスレッドが isPaused() を読むので volatile */
    @Unique private static volatile boolean tpsthings$menuPause;
    @Unique private boolean tpsthings$wasPaused;
    @Unique private float tpsthings$savedPausePartialTick;
    @Unique private float tpsthings$savedTimerPartialTick;

    /** バニラのポーズ判定 (runTick 内で hasSingleplayerServer を呼ぶのはここだけ) の直前 */
    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;hasSingleplayerServer()Z"))
    private void tpsthings$beforePauseCheck(boolean renderLevel, CallbackInfo ci) {
        tpsthings$wasPaused = this.pause;
        tpsthings$savedPausePartialTick = this.pausePartialTick;
        tpsthings$savedTimerPartialTick = this.timer.partialTick;
    }

    /** ポーズ判定の直後 (runTick 内 4 回目の Util.getNanos の手前) */
    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/Util;getNanos()J", ordinal = 3))
    private void tpsthings$afterPauseCheck(boolean renderLevel, CallbackInfo ci) {
        IntegratedServer server = getSingleplayerServer();
        boolean want = SugoiMenuOverlay.isEnabled() && hasSingleplayerServer()
                && server != null && !server.isPublished();
        tpsthings$menuPause = want;
        if (!want) {
            return;
        }
        if (tpsthings$wasPaused) {
            // バニラが「ポーズ画面が無い」と見て解除した分を元に戻す。
            // 戻さないと補間位置が毎フレーム進み、止まった Mob が前後に揺れる
            this.pausePartialTick = tpsthings$savedPausePartialTick;
            this.timer.partialTick = tpsthings$savedTimerPartialTick;
        } else {
            // 止めた瞬間の補間位置で固める
            this.pausePartialTick = this.timer.partialTick;
        }
        this.pause = true;
    }

    /**
     * 上の 2 点の間でバニラが一瞬 pause=false にするので、別スレッド (内蔵サーバ) が
     * その瞬間を読むと 1 tick 進んで「Saving and pausing game...」が走り直す。読み出し側も塞ぐ。
     */
    @Inject(method = "isPaused", at = @At("HEAD"), cancellable = true)
    private void tpsthings$isPaused(CallbackInfoReturnable<Boolean> cir) {
        if (tpsthings$menuPause) {
            cir.setReturnValue(true);
        }
    }
}
