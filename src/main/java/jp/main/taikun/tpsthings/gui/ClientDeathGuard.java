package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.damage.AutoGuard;
import jp.main.taikun.tpsthings.damage.HealthGuard;
import jp.main.taikun.tpsthings.items.ItemOo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * クライアント側に出る「嘘の死亡画面」を拒む。
 *
 * <p>サーバの関所は全部「クライアントはサーバの結果を映しているだけ」を前提にしている。
 * ところが死の報せは<b>クライアントの中だけでも成立する</b> — 読み出しを書き換えられた
 * 自機が「自分は死んでいる」と答えれば、それだけで死亡画面が開く。サーバでは生きているので、
 * リスポーンを押しても要求は捨てられ、タイトルへ戻るしかなくなる。
 *
 * <p>だから画面を出す側にも関所を置く。判断はサーバ側と同じ形 —
 * <b>値が実際に載っている箱の HP が正なら、その死は嘘</b>。
 *
 * <p>このクラスはクライアント側でしか触らない (サーバでは {@code Minecraft} を触れない)。
 */
public final class ClientDeathGuard {

    private ClientDeathGuard() {
    }

    /** いま死亡画面を拒むべきか。 */
    public static boolean suppress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        // シングルプレイでは保護一覧が両側から見える。見えない環境では装備で判断する
        if (!AutoGuard.isProtected(player) && !ItemOo.isWorn(player)) {
            return false;
        }
        return HealthGuard.rawHealth(player) > 0.0F;
    }

    /**
     * 既に開いてしまった死亡画面を閉じる。
     *
     * 守りが間に合う前に一度開かれることはある。開きっぱなしだと、本人は
     * 生きているのに操作できないままになる。
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DeathScreen && suppress()) {
            minecraft.setScreen(null);
        }
    }
}
