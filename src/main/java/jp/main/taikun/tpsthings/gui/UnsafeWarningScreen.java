package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.damage.GuardSettings;
import jp.main.taikun.tpsthings.network.ModNetwork;
import jp.main.taikun.tpsthings.network.PacketGuardChange;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/**
 * Unsafe / Java Agent を入れる前の警告画面。
 *
 * <p>同意したときだけサーバへ入れる操作を送る。サーバ側も、この画面を経ない入れる操作は通さない。
 * ボタンは少し待たないと押せない — メニューを連打している最中に、読まずに通り抜けさせないため。
 */
public final class UnsafeWarningScreen {

    /** ボタンが押せるようになるまでの tick。 */
    private static final int READ_DELAY_TICKS = 60;

    private UnsafeWarningScreen() {
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        // メニューはキーを押している間だけ開く。画面を出すとキーを離した合図が届かないことがあるので先に閉じる
        SugoiMenuOverlay.hide();
        ConfirmScreen screen = new ConfirmScreen(agreed -> {
            if (agreed) {
                ModNetwork.CHANNEL.sendToServer(new PacketGuardChange(GuardSettings.UNSAFE_CONFIRMED, 1));
            }
            minecraft.setScreen(null);
        },
                Component.literal("⚠ Unsafe / Java Agent を有効にしますか？").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal(String.join("\n",
                        "有効にすると TPSThings は次のことをします:",
                        "・sun.misc.Unsafe で JVM の自己アタッチ禁止を書き換える",
                        "・自分自身を Java Agent として付け、読み込み済みのクラスを実行中に書き換える",
                        "・他の Mod のメソッドを潰したり、全クラスの静的な状態を読み書きしたりする",
                        "",
                        "外れればクラッシュ・ワールドの破損・他の Mod の故障が起こり得ます。",
                        "OFF に戻しても、書き換えたクラスはゲームを再起動するまで元に戻りません。",
                        "大事なワールドではバックアップを取ってから使ってください。")),
                Component.literal("理解して有効にする").withStyle(ChatFormatting.RED),
                Component.literal("やめる"));
        screen.setDelay(READ_DELAY_TICKS);
        minecraft.setScreen(screen);
    }
}
