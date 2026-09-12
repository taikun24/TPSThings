package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.GuardTick;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 見張りをイベントバスに頼らずに回すための足場。
 *
 * <p>イベントバスは差し替えられる。差し替えられた瞬間に見張りは止まり、
 * 止まったこと自体が見えなくなる。サーバの tick そのものに刺しておけば、
 * バスに何をされても見回りは回り続ける。
 */
@Mixin(MinecraftServer.class)
public abstract class MixinServerTickGuard {

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void tpsthings$guardTick(CallbackInfo ci) {
        GuardTick.run((MinecraftServer) (Object) this);
    }
}
