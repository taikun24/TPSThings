package jp.main.taikun.tpsthings.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 周りの世界を深層に寄せる。サーバ → 近くのクライアント。儀式の最中に送り続ける。
 *
 * @param ticks    暗さ・静けさ・ノイズを保つ長さ。0 以下なら、それらを即座に元へ戻す
 *                 (flash / impact / whiteout はその後に始める)
 * @param quiet    音を消す割合 (0〜1)。ノイズの色 (層の深さ) にも使う
 * @param dim      画面を暗くする強さ (0〜1)
 * @param noise    世界を崩す後処理の強さ (0〜1)
 * @param flash    貫通層シェーダーを索引層の深さで被せる長さ (tick)
 * @param impact   インパクトフレームを打つ長さ (tick)。1 tick ごとに白黒が入れ替わる
 * @param whiteout 画面を白く飛ばしてから戻す長さ (tick)
 * @param x        深層の源 (儀式の炉) の位置。ここから鳴る音は静けさに消されない
 */
public record PacketAbyss(int ticks, float quiet, float dim, float noise, int flash, int impact, int whiteout,
                          double x, double y, double z) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final PacketAbyss RESET = new PacketAbyss(0, 0.0F, 0.0F, 0.0F, 0, 0, 0);

    /** 源の位置は送る直前に {@link #from} で付ける。 */
    public PacketAbyss(int ticks, float quiet, float dim, float noise, int flash, int impact, int whiteout) {
        this(ticks, quiet, dim, noise, flash, impact, whiteout, 0.0, 0.0, 0.0);
    }

    public PacketAbyss from(double x, double y, double z) {
        return new PacketAbyss(ticks, quiet, dim, noise, flash, impact, whiteout, x, y, z);
    }

    public static void encode(PacketAbyss packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.ticks());
        buffer.writeFloat(packet.quiet());
        buffer.writeFloat(packet.dim());
        buffer.writeFloat(packet.noise());
        buffer.writeVarInt(packet.flash());
        buffer.writeVarInt(packet.impact());
        buffer.writeVarInt(packet.whiteout());
        buffer.writeDouble(packet.x());
        buffer.writeDouble(packet.y());
        buffer.writeDouble(packet.z());
    }

    public static PacketAbyss decode(FriendlyByteBuf buffer) {
        return new PacketAbyss(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(PacketAbyss packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        // enqueueWork の中で投げた例外は誰にも拾われず消えるので、自分で記録する
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                jp.main.taikun.tpsthings.gui.AbyssOverlay.accept(packet);
            } catch (RuntimeException e) {
                LOGGER.error("深層の演出に失敗しました", e);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
