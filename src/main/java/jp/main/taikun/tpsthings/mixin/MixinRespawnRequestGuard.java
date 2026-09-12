package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.RespawnGuard;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 嘘の死亡画面からのリスポーン要求を、詰みにせず受け止める。
 *
 * <p>生きている相手にクライアントだけ死亡画面を出されると、リスポーンを押しても
 * バニラが「サーバでは HP が残っている」と要求を捨てる。<b>死ねないが操作もできない</b>
 * 詰みはここで生まれる。守った結果が詰みなら守れていない。
 *
 * <p>「保護対象が、素の HP が正のままリスポーンを要求してくる」はバニラでは起きない
 * 組み合わせで、それ自体が「クライアントが騙されている」という署名になる。
 * 死なせずに実体を作り直して画面を閉じさせ、本当の HP を送り直す。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinRespawnRequestGuard {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardRespawnRequest(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
            return;
        }
        ServerPlayer revived = RespawnGuard.rebuildIfSpurious(player);
        if (revived != null) {
            player = revived;
            ci.cancel();
        }
    }
}
