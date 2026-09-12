package jp.main.taikun.tpsthings.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 貫通攻撃が 1 体に通った結果。サーバ → 周りのクライアント。
 *
 * 相手は索引から外されて手元でも消えるので、実体ではなく位置と大きさで送る。
 *
 * @param hp      打つ前の体力 (同期データの生値)
 * @param health  HP が 0 になった層。値の意味は PiercingStrike.Result と同じ
 * @param death   死亡処理が済んだ層
 * @param removal 世界から消えた層
 */
public record PacketStrikeEffect(double x, double y, double z, float width, float height, float hp,
                                 int health, int death, int removal) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void encode(PacketStrikeEffect packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x());
        buffer.writeDouble(packet.y());
        buffer.writeDouble(packet.z());
        buffer.writeFloat(packet.width());
        buffer.writeFloat(packet.height());
        buffer.writeFloat(packet.hp());
        buffer.writeVarInt(packet.health());
        buffer.writeVarInt(packet.death());
        buffer.writeVarInt(packet.removal());
    }

    public static PacketStrikeEffect decode(FriendlyByteBuf buffer) {
        return new PacketStrikeEffect(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(PacketStrikeEffect packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        // enqueueWork の中で投げた例外は誰にも拾われず消えるので、自分で記録する
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                jp.main.taikun.tpsthings.gui.StrikeEffects.acceptEffect(packet);
            } catch (RuntimeException e) {
                LOGGER.error("貫通攻撃の演出に失敗しました", e);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
