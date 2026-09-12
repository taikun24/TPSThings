package jp.main.taikun.tpsthings.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 打った本人への報告。一番しぶとかった 1 体が、どの層まで貫通を要したか。サーバ → 攻撃者。
 *
 * 文字列ではなく層の番号で送り、区切って出すのも音を鳴らすのもクライアントに任せる。
 *
 * @param count 打った数
 */
public record PacketStrikeReport(int count, int health, int death, int removal) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void encode(PacketStrikeReport packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.count());
        buffer.writeVarInt(packet.health());
        buffer.writeVarInt(packet.death());
        buffer.writeVarInt(packet.removal());
    }

    public static PacketStrikeReport decode(FriendlyByteBuf buffer) {
        return new PacketStrikeReport(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(PacketStrikeReport packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        // enqueueWork の中で投げた例外は誰にも拾われず消えるので、自分で記録する
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                jp.main.taikun.tpsthings.gui.StrikeEffects.acceptReport(packet);
            } catch (RuntimeException e) {
                LOGGER.error("貫通攻撃の報告の表示に失敗しました", e);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
