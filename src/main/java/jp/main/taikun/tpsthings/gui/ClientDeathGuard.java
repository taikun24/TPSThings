package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.damage.AutoGuard;
import jp.main.taikun.tpsthings.damage.DamageGuard;
import jp.main.taikun.tpsthings.damage.GuardNotice;
import jp.main.taikun.tpsthings.damage.HealthGuard;
import jp.main.taikun.tpsthings.damage.StateProbe;
import jp.main.taikun.tpsthings.items.ItemOo;
import jp.main.taikun.tpsthings.mixin.AccessorClientLevel;
import jp.main.taikun.tpsthings.mixin.AccessorEntity;
import jp.main.taikun.tpsthings.mixin.AccessorEntityTickList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;

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

    /** 画面を閉じられるまでの時間がこれより短ければ、本人の操作ではないと見る。 */
    private static final long SPURIOUS_CLOSE_MS = 1000L;

    private static Screen openedScreen;
    private static long openedAt;

    /**
     * 開いた直後に閉じられた画面について、<b>誰が閉じたか</b>を残す。
     *
     * <p>閉じる道は多い (画面自身の tick、サーバからの閉じる通知、他 Mod の tick…) が、
     * どれも最後は {@code setScreen} に来る。ここで呼び出し元を撮れば、どの答えを見て
     * 閉じたのかを推測せずに済む。人の手で 1 秒以内に閉じることはまず無いので、そこだけ撮る。
     */
    public static void traceSetScreen(Screen next) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || (!AutoGuard.isProtected(player) && !ItemOo.isWorn(player))) {
            return;
        }
        long now = System.currentTimeMillis();
        Screen current = minecraft.screen;
        if (next != null) {
            if (next != current) {
                openedScreen = next;
                openedAt = now;
            }
            return;
        }
        if (current == null || current != openedScreen || now - openedAt > SPURIOUS_CLOSE_MS) {
            return;
        }
        StringBuilder path = new StringBuilder();
        StackTraceElement[] frames = new Throwable().getStackTrace();
        for (int i = 1; i < frames.length && i <= 16; i++) {
            if (path.length() > 0) {
                path.append(" <- ");
            }
            StackTraceElement frame = frames[i];
            path.append(frame.getClassName()).append('#').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
        }
        GuardNotice.warnThrottled("screen-close-trace", "開いて " + (now - openedAt) + " ミリ秒で画面 ("
                + current.getClass().getSimpleName() + ") が閉じられました。閉じた経路: " + path
                + " / " + StateProbe.diagnose(player));
    }

    /**
     * 入れ物の画面が「自機は死んでいる」として自分を閉じようとしたとき、拒むべきか。
     *
     * 判断は死亡画面と同じ形 — 値が実際に載っている箱の HP が正で、除去の印も立っていないなら、
     * その死は嘘。本人が閉じる操作 (Esc 等) はこの道を通らないので妨げない。
     */
    public static boolean refuseContainerClose() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !suppress() || HealthGuard.rawRemoved(player)) {
            return false;
        }
        GuardNotice.warnThrottled("container-close-refused", "入れ物の画面が「自機は死んでいる」として閉じようとしました。"
                + "実データでは生存しているので拒みました: " + StateProbe.diagnose(player));
        return true;
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
        LocalPlayer player = minecraft.player;
        if (player != null && minecraft.level != null) {
            // 嘘の見張りは自機の tick に頼らない。tick 一覧から外されていたら、そこで永久に止まる
            AutoGuard.watchClient(player);
            restorePresence(minecraft.level, player);
            restoreTicking(minecraft.level, player);
        }
        if (minecraft.screen instanceof DeathScreen && suppress()) {
            minecraft.setScreen(null);
        }
    }

    /**
     * 自機をクライアントの世界へ戻す。
     *
     * <p>自機に除去の印を立て、クライアントの世界の索引から外す相手が居る。サーバ側で
     * 載せ直しても、手元の自機は消えたままで操作も描画も戻らない。以前はサーバが
     * リスポーン扱いで作り直させていたが、それは世界の読み込み直しになり重い。
     *
     * <p>手元で直せば済む。バニラのクライアントで自機が消えるのは、リスポーン・次元移動・
     * 切断のときだけで、どれも {@code minecraft.player} ごと入れ替わるか世界ごと畳まれる。
     * <b>今の自機のまま、今の世界で、殺意のある印が立っている / 索引に居ない</b>なら、
     * 誰の仕業でも正規の後始末ではない。印を下ろし、バニラの登録口から入れ直す。
     */
    private static void restorePresence(ClientLevel level, LocalPlayer player) {
        if (!AutoGuard.isProtected(player) && !ItemOo.isWorn(player)) {
            return;
        }
        Entity.RemovalReason reason = ((AccessorEntity) player).tpsthings$getRemovalReason();
        boolean marked = reason != null && HealthGuard.isHostileRemoval(reason);
        boolean indexed = level.getEntity(player.getId()) == player;
        // 本当に死んだ自機はバニラの死亡処理に任せる
        if ((!marked && indexed) || HealthGuard.rawHealth(player) <= 0.0F) {
            return;
        }
        StringBuilder detail = new StringBuilder();
        DamageGuard.runAsSelf(() -> {
            if (!indexed) {
                try {
                    level.addPlayer(player.getId(), player);
                } catch (RuntimeException | LinkageError failure) {
                    detail.append(" (入れ直しに失敗: ").append(failure).append(')');
                }
            }
            // 登録口は同じ id の古い方を外すときに印を立て直すので、下ろすのは最後
            ((AccessorEntity) player).tpsthings$setRemovalReason(null);
        });
        GuardNotice.warnThrottled("client-presence-restore", "手元の自機が"
                + (marked ? "除去の印を立てられ" : "") + (indexed ? "" : (marked ? "、" : "") + "世界の索引から外され")
                + "ていました。手元で戻しました" + detail);
    }

    /**
     * 自機を tick 一覧に戻す。
     *
     * <p>バニラでは、世界に居る自機は必ず一覧に載っている。載っていないなら誰かが外したか、
     * 載せる入口を塞いでいる。自機の tick が来ないと、見張りも移動も画面の後始末も全部止まる。
     *
     * <p>入口 ({@code add}) から戻し、それも塞がれていたら一覧の Map に直接置く。
     * 呼ぶのはクライアント tick の終わりで、一覧を回している最中ではない。
     */
    private static void restoreTicking(ClientLevel level, LocalPlayer player) {
        if (!AutoGuard.isProtected(player) || HealthGuard.rawRemoved(player)) {
            return;
        }
        EntityTickList ticking = ((AccessorClientLevel) level).tpsthings$tickingEntities();
        if (ticking.contains(player)) {
            return;
        }
        ticking.add(player);
        boolean direct = false;
        if (!ticking.contains(player)) {
            ((AccessorEntityTickList) ticking).tpsthings$active().put(player.getId(), player);
            direct = true;
        }
        GuardNotice.warnThrottled("client-tick-restore", "自機がクライアントの tick 一覧から外されていました。"
                + (direct ? "入口を塞がれていたので一覧に直接" : "") + "戻しました");
    }
}
