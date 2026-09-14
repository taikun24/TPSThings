package jp.main.taikun.tpsthings;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 自分の世界に<b>自分以外</b>が入ってきたら断る。
 *
 * <p>専用サーバーは {@link SingleplayerGate} が起動の時点で止める。残るのは、シングルの世界を
 * LAN 公開 (や {@code /publish}) して他の人を入れる道で、これはサーバーが立ってからでないと分からない。
 * 入ってきた相手を、ここで切断する。クライアント側でも公開の画面は塞いでいるが、
 * コマンドからも公開できるので、受け入れる側でも必ず断る。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = Tpsthings.MODID)
public final class SingleplayerOnly {

    private SingleplayerOnly() {
    }

    /** 切断・警告の文言。相手が Mod を入れていなくても読めるよう、翻訳キーではなく文字列で送る。 */
    public static Component message() {
        return Component.literal(SingleplayerGate.TITLE + "\n\n" + SingleplayerGate.REASON);
    }

    /** 他の購読 (保護の同期など) より先に断る。入れてから追い出すまでの間に何も触らせない。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void refuseGuests(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (server.isDedicatedServer() || !server.isSingleplayerOwner(player.getGameProfile())) {
            player.connection.disconnect(message());
        }
    }
}
