package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.gui.AbyssOverlay;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 儀式の画面演出 (AbyssOverlay) を描く場所。
 *
 * Forge の RenderGuiEvent は Gui.render の中で出るので、F1 で HUD を隠すと来ず、演出が丸ごと消えていた。
 * バニラが自前の後処理を掛ける場所と、HUD と画面の間に直接差し込む。
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererAbyss {

    /** 世界を描いてバニラの後処理を掛けた直後、描き込み先を戻す手前 (render 内で bindWrite を呼ぶのはここだけ) */
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void tpsthings$afterWorld(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        AbyssOverlay.renderWorld(partialTick);
    }

    /** HUD を描き終えて、読み込み画面や Screen を描く手前 */
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;",
            ordinal = 0))
    private void tpsthings$afterHud(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            AbyssOverlay.renderScreen(partialTick);
        }
    }
}
