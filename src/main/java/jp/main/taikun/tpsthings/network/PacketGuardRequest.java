package jp.main.taikun.tpsthings.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 即死対策の設定一覧をくれという要求。クライアント → サーバ。
 * SugoiMenu を開いたときに送る。
 */
public record PacketGuardRequest() {

    public static void encode(PacketGuardRequest packet, FriendlyByteBuf buffer) {
    }

    public static PacketGuardRequest decode(FriendlyByteBuf buffer) {
        return new PacketGuardRequest();
    }

    public static void handle(PacketGuardRequest packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                PacketGuardState.sendTo(sender, "");
            }
        });
        ctx.setPacketHandled(true);
    }
}
