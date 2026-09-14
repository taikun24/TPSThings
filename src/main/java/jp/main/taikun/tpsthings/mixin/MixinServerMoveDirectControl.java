package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.DamageGuard;
import jp.main.taikun.tpsthings.items.DirectControl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 直接操縦中のプレイヤーから届いた位置を、そのまま受け入れる。
 *
 * バニラはここでサーバ側の move (Mod の当たり判定込み) をやり直し、ずれていれば押し戻す。
 * 直接操縦の位置はクライアントがブロックの形だけで決めたものなので、やり直すと必ず食い違う。
 * テレポート確認待ちの間も受け入れる (確認が来ればサーバは一度そこへ飛ばすが、次の位置で戻る)。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerMoveDirectControl {

    @Shadow
    public ServerPlayer player;
    @Shadow
    private boolean clientIsFloating;
    @Shadow
    private double lastGoodX;
    @Shadow
    private double lastGoodY;
    @Shadow
    private double lastGoodZ;

    // ensureRunningOnSameThread は通信スレッドでは例外で抜けるので、ここより後はサーバスレッドだけが通る
    @Inject(method = "handleMovePlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void tpsthings$acceptDirectControl(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (player.wonGame || !packet.hasPosition() || !DirectControl.isActive(player)) {
            return;
        }
        double x = packet.getX(0.0D);
        double y = packet.getY(0.0D);
        double z = packet.getZ(0.0D);
        float yRot = packet.getYRot(player.getYRot());
        float xRot = packet.getXRot(player.getXRot());
        // 壊れた値の切断はバニラに任せる
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || !Float.isFinite(yRot) || !Float.isFinite(xRot)) {
            return;
        }
        ci.cancel();
        Vec3 position = new Vec3(Mth.clamp(x, -3.0E7D, 3.0E7D), Mth.clamp(y, -2.0E7D, 2.0E7D),
                Mth.clamp(z, -3.0E7D, 3.0E7D));
        DamageGuard.runAsSelf(() -> player.absMoveTo(position.x, position.y, position.z,
                Mth.wrapDegrees(yRot), Mth.wrapDegrees(xRot)));
        DirectControl.forcePosition(player, position);
        player.setOnGround(packet.isOnGround());
        player.resetFallDistance();
        player.serverLevel().getChunkSource().move(player);
        // 浮いている判定で飛行キックされないように。OFF に戻したときの押し戻しの基準も今の位置にする
        clientIsFloating = false;
        lastGoodX = position.x;
        lastGoodY = position.y;
        lastGoodZ = position.z;
    }
}
