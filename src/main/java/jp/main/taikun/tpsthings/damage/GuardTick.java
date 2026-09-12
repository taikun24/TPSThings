package jp.main.taikun.tpsthings.damage;

import net.minecraft.server.MinecraftServer;

/**
 * 見張りをサーバの tick から回す入口。
 *
 * <p>索引から外された保護対象は自分の tick が止まるので、見回りは被害者ではなく
 * サーバ側から回すしかない。ただし<b>イベント越しに呼ぶだけでは足りない</b> —
 * Forge のイベントバスそのものを自前のものに差し替えて、tick のイベントを
 * 丸ごと捨てる相手が居る。捨てられると見張りは静かに止まり、止まったことにも気づけない。
 *
 * <p>そこで入口を 2 つ用意する。イベントからと、{@code MinecraftServer} の tick に
 * 直接刺した関所から。どちらから来ても中身は 1 回しか走らない (同じ tick 番号は弾く)。
 * バスを差し替えられても、サーバの tick そのものは止められない。
 */
public final class GuardTick {

    /** 最後に回した tick 番号。2 つの入口から二重に回さないための印。 */
    private static volatile int lastTick = -1;

    private GuardTick() {
    }

    public static void run(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int tick = server.getTickCount();
        if (tick == lastTick) {
            return;
        }
        lastTick = tick;
        // 見回りの 1 つが投げても、他の見回りと世界の tick を道連れにしない。
        // ここは世界を回す道の上なので、投げ抜けるとサーバごと落ちる (実測で落ちた)
        attempt("追撃", () -> PiercingStrike.tick(server));
        attempt("存在の見張り", PresenceGuard::sweep);
        attempt("関所の張り直し", () -> RepairGuard.check(server));
        attempt("復帰", () -> RespawnGuard.processRespawns(server));
    }

    private static void attempt(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError failure) {
            GuardNotice.warnThrottled("guard-tick-" + what,
                    what + "の見回りが失敗しました (続行します): " + failure);
        }
    }

    public static void reset() {
        lastTick = -1;
    }
}
