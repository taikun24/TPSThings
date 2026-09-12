package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.OutboundGuard;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 出ていくパケットの関所。保護対象本人への「死の報せ」だけを見る。
 *
 * <p>置き場所は通信路そのもの。1 段上 ({@code ServerGamePacketListenerImpl#send}) に
 * 置くと、そこを飛ばして通信路へ直接書く相手に素通りされる。
 *
 * <p>判断は {@link OutboundGuard} に委ね、ここは判断を持たない。
 */
@Mixin(Connection.class)
public abstract class MixinConnectionOutbound {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardOutbound(Packet<?> packet, PacketSendListener listener,
                                         CallbackInfo ci) {
        Connection self = (Connection) (Object) this;
        if (!(self.getPacketListener() instanceof ServerGamePacketListenerImpl game)) {
            return;
        }
        Packet<?> allowed = OutboundGuard.filter(game.player, packet);
        if (allowed == packet) {
            return;
        }
        ci.cancel();
        if (allowed != null) {
            // 差し替えた方を同じ口から送り直す。直した後の値はもう嘘ではないので素通りする
            self.send(allowed, listener);
        }
    }
}
