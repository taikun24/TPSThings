package jp.main.taikun.tpsthings.gui;

import com.mojang.realmsclient.RealmsMainScreen;
import jp.main.taikun.tpsthings.SingleplayerGate;
import jp.main.taikun.tpsthings.SingleplayerOnly;
import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアント側で、マルチプレイへの道を塞ぐ。
 *
 * <p>理由は {@link SingleplayerGate} に書いた通り。塞ぎ方は 2 段:
 * <ul>
 *   <li>マルチプレイ・Realms・LAN 公開の画面を開こうとしたら、代わりに断りの画面を出す
 *       (つながる前に止めるのが一番安全)。
 *   <li>それでも本物の接続でログインしてきたら (起動引数のクイックプレイなど、画面を通らない道)、
 *       その場で切る。自分の世界はメモリ上の接続なので、こちらには掛からない。
 * </ul>
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = Tpsthings.MODID, value = Dist.CLIENT)
public final class SingleplayerOnlyClient {

    private SingleplayerOnlyClient() {
    }

    @SubscribeEvent
    public static void refuseMultiplayerScreens(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (!(next instanceof JoinMultiplayerScreen
                || next instanceof RealmsMainScreen
                || next instanceof ShareToLanScreen)) {
            return;
        }
        // 閉じたら開く前の画面 (タイトル・ポーズメニュー) へ戻す
        Screen back = event.getCurrentScreen();
        event.setNewScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(back),
                Component.literal(SingleplayerGate.TITLE),
                Component.literal(SingleplayerGate.REASON)));
    }

    @SubscribeEvent
    public static void refuseRemoteLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Connection connection = event.getConnection();
        if (connection == null || connection.isMemoryConnection()) {
            return;
        }
        // ログイン処理の最中に切ると後続の処理が壊れた接続を触るので、次の番に回す
        Minecraft.getInstance().execute(() -> connection.disconnect(SingleplayerOnly.message()));
    }
}
