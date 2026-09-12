package jp.main.taikun.tpsthings.network;

import jp.main.taikun.tpsthings.damage.GuardSettings;
import jp.main.taikun.tpsthings.items.OoToolSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 即死対策の設定を 1 項目操作する要求。クライアント → サーバ。
 *
 * 権限はサーバ側で必ず見る。コマンド (/tpsthings damage) と同じ OP レベル 2 を要求しないと、
 * メニューが裏口になる。
 */
public record PacketGuardChange(String id, int direction) {

    public static void encode(PacketGuardChange packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.id(), 64);
        buffer.writeVarInt(packet.direction());
    }

    public static PacketGuardChange decode(FriendlyByteBuf buffer) {
        return new PacketGuardChange(buffer.readUtf(64), buffer.readVarInt());
    }

    public static void handle(PacketGuardChange packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            // おおの道具設定は本人の持ち物だけを変えるので、権限は要らない
            if (packet.id().startsWith(OoToolSettings.PREFIX)) {
                String result = OoToolSettings.apply(sender, packet.id(), packet.direction() < 0 ? -1 : 1);
                PacketGuardState.sendTo(sender, result == null ? "" : result);
                return;
            }
            if (!sender.hasPermissions(PacketGuardState.REQUIRED_PERMISSION)) {
                PacketGuardState.sendTo(sender, "");
                return;
            }
            String result = GuardSettings.apply(sender, packet.id(), packet.direction() < 0 ? -1 : 1);
            PacketGuardState.sendTo(sender, result == null ? "" : result);
        });
        ctx.setPacketHandled(true);
    }
}
