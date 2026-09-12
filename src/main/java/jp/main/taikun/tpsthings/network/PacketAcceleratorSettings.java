package jp.main.taikun.tpsthings.network;

import jp.main.taikun.tpsthings.blockentities.BETimeAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 加速器の範囲と速さを変える要求。クライアント → サーバ。
 *
 * 値はサーバ側で必ず丸める。送られてきた数をそのまま信じると、
 * 画面を書き換えるだけで任意の範囲を回せてしまう。
 */
public record PacketAcceleratorSettings(BlockPos pos, int range, int speed) {

    /** 届いた位置がプレイヤーからこれ以上離れていたら無視する。 */
    private static final double MAX_DISTANCE_SQR = 64.0;

    public static void encode(PacketAcceleratorSettings packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeVarInt(packet.range());
        buffer.writeVarInt(packet.speed());
    }

    public static PacketAcceleratorSettings decode(FriendlyByteBuf buffer) {
        return new PacketAcceleratorSettings(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(PacketAcceleratorSettings packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            Level level = sender.level();
            // 未読み込みの位置を触ると、そこだけチャンクを読み込ませられてしまう
            if (!level.isLoaded(packet.pos())
                    || sender.distanceToSqr(packet.pos().getCenter()) > MAX_DISTANCE_SQR) {
                return;
            }
            if (level.getBlockEntity(packet.pos()) instanceof BETimeAccelerator accelerator) {
                accelerator.applySettings(packet.range(), packet.speed());
            }
        });
        ctx.setPacketHandled(true);
    }
}
