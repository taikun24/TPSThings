package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.PresenceGuard;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手元の自機が生きて tick しているかの報せを拾う。
 *
 * <p>入口の先頭で拾う。この先には移動を丸ごと引き受けて打ち切る関所
 * ({@link MixinServerMoveDirectControl}) があり、後ろに置くと報せを取りこぼす。
 * 先頭は通信スレッドからも一度通るが、届いたこと自体が証拠なのでそれで構わない。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerMoveClientPulse {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void tpsthings$clientPulse(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PresenceGuard.clientMoved(player);
    }
}
