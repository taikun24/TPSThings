package jp.main.taikun.tpsthings.damage;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityLookup;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 剥がされた関所を、誰に剥がされたか知らないまま張り直す。
 *
 * <p>こちらの {@code @Inject} を実行時に剥がしてくる相手が居る。剥がされたことは
 * <b>「何も起きない」という形でしか現れない</b>ので、関所の内側からは永久に気づけない。
 * だから見張りは外 (サーバの tick) に置く。
 *
 * <p>直し方は retransform 一発で足りる。{@code retransformClasses} は
 * <b>{@code defineClass} に渡された元のバイト列</b>からチェーンを流し直すので:
 *
 * <ul>
 *   <li>Mixin は defineClass より前に適用される = 元のバイト列に<b>含まれる</b> → 復活する</li>
 *   <li>agent の書き換えは含まれない → <b>登録したままの変換器だけが再実行される</b></li>
 * </ul>
 *
 * <p>つまり「掛けてから外した」一発物の書き換えは、そのクラスを誰かが retransform した
 * 瞬間に消える。こちらの関所を剥がしにきた相手の仕事だけが巻き戻り、こちらの Mixin は
 * 元のバイト列にあるので戻ってくる。相手の名前も手口も知らずに直せる。
 *
 * <p>触るのは<b>自分が関所を張ったクラスだけ</b>。ここに並ぶのは自分の構造であって、
 * 相手についての知識ではない。他所のクラスまで巻き戻すと、他 Mod の正当な書き換えまで
 * 取り消してしまう。
 */
public final class RepairGuard {

    /** 自分の関所が刺さっているバニラのクラス。 */
    private static final Class<?>[] GATE_CLASSES = {
            LivingEntity.class,
            SynchedEntityData.class,
            // 箱の側の関所 (MixinDataItemGuard)。入口 (SynchedEntityData) と別クラスなので別枠
            SynchedEntityData.DataItem.class,
            Entity.class,
            EntityLookup.class,
            ServerPlayer.class,
            net.minecraft.world.entity.player.Player.class,
            PlayerList.class,
    };

    /** 確認の間隔。関所の生死はそう頻繁に変わらないし、確認自体も只ではない。 */
    private static final long CHECK_INTERVAL_NANOS = 5_000_000_000L;
    /** 張り直しの間隔。直した直後にもう一度直しても意味がない。 */
    private static final long REPAIR_INTERVAL_NANOS = 10_000_000_000L;
    /**
     * 毎 tick の関所が沈黙してよい時間。
     *
     * 保護対象が世界に居て tick が回っているなら、ここは毎 tick 通る。
     * 1 秒黙っているなら、通っていないのではなく通れなくなっている。
     */
    private static final long SILENCE_NANOS = 1_000_000_000L;
    /**
     * 張り直しの上限。
     *
     * 直しても直しても剥がされるなら、相手は張り直しに追従している。
     * 際限なく retransform を撃ち続ける方が害になる。
     */
    private static final int MAX_REPAIRS = 20;

    private static volatile boolean enabled = true;
    private static volatile long lastCheck = 0L;
    private static volatile long lastRepair = 0L;
    private static final AtomicInteger repairs = new AtomicInteger();

    private RepairGuard() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static int repairCount() {
        return repairs.get();
    }

    public static void reset() {
        lastCheck = 0L;
        lastRepair = 0L;
        repairs.set(0);
    }

    /**
     * 関所が生きているか確かめ、死んでいたら張り直す。
     *
     * サーバの tick の終わりから呼ぶ。関所の中から呼んではいけない —
     * 剥がされていたらその呼び出し自体が起きない。
     */
    public static void check(MinecraftServer server) {
        if (!enabled || AutoGuard.protectedCount() == 0) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastCheck < CHECK_INTERVAL_NANOS) {
            return;
        }
        lastCheck = now;

        ServerPlayer target = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AutoGuard.isProtected(player) && !player.isRemoved()) {
                target = player;
                break;
            }
        }
        if (target == null) {
            return; // 保護対象が世界に居ない。心拍が無いのは当たり前
        }

        List<String> dead = new ArrayList<>();
        // 毎 tick の関所。保護対象が tick しているのに反応が無いなら剥がされている
        if (AutoGuard.nanosSinceTickHook() > SILENCE_NANOS) {
            dead.add("毎 tick の見張り");
        }
        // 書き込みの関所。こちらは黙って待つのではなく、自分で 1 回書いて確かめる
        if (!HealthGuard.writeGateAlive(target)) {
            dead.add("HP 書き込みの関所");
        }
        if (dead.isEmpty()) {
            return;
        }
        repair(target, String.join(" / ", dead));
    }

    private static void repair(ServerPlayer target, String what) {
        long now = System.nanoTime();
        if (now - lastRepair < REPAIR_INTERVAL_NANOS && lastRepair != 0L) {
            return;
        }
        lastRepair = now;
        if (repairs.incrementAndGet() > MAX_REPAIRS) {
            GuardNotice.send(target, "関所が" + what
                    + "から剥がされていますが、張り直しても剥がし直されるので諦めます", true);
            return;
        }

        String failure = MethodDisabler.ensureReady();
        Instrumentation instrumentation = MethodDisabler.instrumentationOrNull();
        if (instrumentation == null) {
            GuardNotice.send(target, "関所 (" + what + ") が剥がされていますが、"
                    + "張り直す手段がありません (" + failure + ")", true);
            return;
        }

        List<String> repaired = new ArrayList<>();
        for (Class<?> gate : GATE_CLASSES) {
            if (!instrumentation.isModifiableClass(gate)) {
                continue;
            }
            try {
                instrumentation.retransformClasses(gate);
                repaired.add(gate.getSimpleName());
            } catch (Throwable t) {
                GuardNotice.warn(gate.getName() + " を張り直せませんでした: " + t);
            }
        }
        // send はログにも同じ内容を残すので、別文面で二重に書かない
        GuardNotice.send(target, what + "が剥がされていたので張り直しました ("
                + repairs.get() + " 回目: " + String.join(", ", repaired) + ")", true);
    }
}
