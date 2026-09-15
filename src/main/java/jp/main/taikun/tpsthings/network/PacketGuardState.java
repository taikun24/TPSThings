package jp.main.taikun.tpsthings.network;

import jp.main.taikun.tpsthings.damage.GuardSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 即死対策の設定一覧と、直前の操作の結果。サーバ → クライアント。
 *
 * 表示に要るものは全部ここに載せる。クライアントは中身を解釈せずに並べるだけ。
 */
public record PacketGuardState(boolean permitted, List<GuardSettings.Entry> entries, String message) {

    /** コマンド /tpsthings damage と同じ。 */
    public static final int REQUIRED_PERMISSION = 2;

    public static void sendTo(ServerPlayer player, String message) {
        boolean permitted = player.hasPermissions(REQUIRED_PERMISSION);
        // おおの道具設定は自分の道具のことなので誰でも触れる。即死対策の設定だけ OP に限る
        List<GuardSettings.Entry> entries = new ArrayList<>(
                jp.main.taikun.tpsthings.items.OoToolSettings.snapshot(player));
        if (permitted) {
            entries.addAll(GuardSettings.snapshot(player));
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketGuardState(permitted, entries, message));
    }

    public static void encode(PacketGuardState packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.permitted());
        buffer.writeVarInt(packet.entries().size());
        for (GuardSettings.Entry entry : packet.entries()) {
            buffer.writeUtf(entry.id());
            buffer.writeUtf(entry.label());
            buffer.writeUtf(entry.value());
            buffer.writeInt(entry.color());
            buffer.writeUtf(entry.description());
            buffer.writeUtf(entry.group());
        }
        buffer.writeUtf(packet.message());
    }

    public static PacketGuardState decode(FriendlyByteBuf buffer) {
        boolean permitted = buffer.readBoolean();
        int size = buffer.readVarInt();
        List<GuardSettings.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new GuardSettings.Entry(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readInt(), buffer.readUtf(), buffer.readUtf()));
        }
        return new PacketGuardState(permitted, entries, buffer.readUtf());
    }

    public static void handle(PacketGuardState packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        // 描画側のクラスはクライアントにしか無いので、サーバで読み込まれないよう DistExecutor 越しに触る
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                jp.main.taikun.tpsthings.gui.SugoiMenuOverlay.acceptGuardState(
                        packet.permitted(), packet.entries(), packet.message())));
        ctx.setPacketHandled(true);
    }
}
