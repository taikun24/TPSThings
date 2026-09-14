package jp.main.taikun.tpsthings;

import net.minecraftforge.fml.loading.FMLLoader;

/**
 * マルチプレイを入口で断る。
 *
 * <p>この Mod は Unsafe と自己アタッチした Java Agent で、読み込み済みのクラスや他所の Mod の中身を
 * 書き換える。自分ひとりの世界なら、壊れても自分の世界だけで済む。だがサーバーでは、他のプレイヤーの
 * 接続・ワールド・他の Mod まで同じ手で触ることになる。だから複数人で遊ぶ場では最初から動かさない。
 *
 * <p>このクラスは <b>Minecraft のクラスを一切参照しない</b>。Mixin の設定を読む段階 (Agent を立てる前)
 * から呼ぶので、ここで Minecraft のクラスに触ると、Mixin が当たる前に読み込まれてしまう。
 * 画面や切断の文言として使う側 ({@link SingleplayerOnly}) は、ここの文字列だけを借りる。
 */
public final class SingleplayerGate {

    public static final String TITLE = "TPSThings はシングルプレイヤー専用です！！";
    public static final String REASON = "この Mod は Unsafe や Java Agent でゲームの中身を書き換えます。\n"
            + "サーバーでは他のプレイヤーやワールドまで巻き込んで壊しかねないので、マルチプレイでは使えません。\n"
            + "(TPSThings is singleplayer only)";

    private SingleplayerGate() {
    }

    /** 専用サーバーの上で動いているか。 */
    public static boolean isDedicatedServer() {
        return FMLLoader.getDist().isDedicatedServer();
    }

    /** 専用サーバーなら、ここで起動を止める。 */
    public static void refuseDedicatedServer() {
        if (isDedicatedServer()) {
            throw new IllegalStateException(TITLE + "\n" + REASON);
        }
    }
}
