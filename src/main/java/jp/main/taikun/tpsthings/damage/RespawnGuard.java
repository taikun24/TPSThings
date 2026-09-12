package jp.main.taikun.tpsthings.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 保護対象が死んでいたら自動で復帰させる。
 *
 * <p>死亡画面で止まるとダメージ源の観測が途切れるため。死亡イベントを経由しない
 * 殺され方もあるので、イベントではなく「生きているか」を毎 tick 見る。
 *
 * <p>サーバ tick の終わりに回すのが肝心。プレイヤーの tick 中に
 * {@code PlayerList#respawn} を呼ぶと、エンティティの反復中に世界から出し入れする
 * ことになり、古い方が残ったまま新しい方が足されて UUID が二重になる。
 */
public final class RespawnGuard {

    /** 自動リスポーンの間隔。連続で殺されたとき、同じ tick で何度も蘇らせない。 */
    private static final int RESPAWN_COOLDOWN_TICKS = 20;
    /** 復帰直後の無敵時間。着地する前にもう一度殺されると何も学べない。 */
    private static final int RESPAWN_GRACE_TICKS = 40;
    /**
     * 連続で即死し続けたら復帰を諦める回数。
     *
     * 無敵時間を貫通してくる相手に対しては、復帰させるほど同じ相手に殺され続ける。
     * 止め時を持たないと、死亡画面の代わりに無限の復帰ループが出来上がるだけになる。
     */
    private static final int RESPAWN_STREAK_LIMIT = 5;
    /** これだけ生き延びたら連続死の数え直し。 */
    private static final int STREAK_RESET_TICKS = 200;
    /** 何回目から現場へ戻すのをやめるか。戻すほど同じ場所で殺される。 */
    private static final int STOP_RETURNING_AT = 2;

    private static final Map<UUID, Integer> RESPAWN_COOLDOWN = new HashMap<>();
    /** 何回死んだか。死ぬことで学習する装備なので、回数そのものが進捗になる。 */
    private static final Map<UUID, Integer> DEATH_COUNT = new HashMap<>();
    /** 生き返れないまま連続で死んだ回数。 */
    private static final Map<UUID, Integer> RESPAWN_STREAK = new HashMap<>();
    /** 生き延びている tick 数。連続死を巻き戻す判断に使う。 */
    private static final Map<UUID, Integer> ALIVE_TICKS = new HashMap<>();
    /** 復帰を諦めた相手。生き延びるまで手を出さない。 */
    private static final Set<UUID> GAVE_UP = new HashSet<>();

    /**
     * 復帰させた直後に呼ぶ後処理。
     *
     * 飛行の復元など、装備側の都合はこの Mod のイベント側が登録する。
     * Guard は装備の仕様を知らないままでいたい。
     */
    private static volatile Consumer<ServerPlayer> reviveHook = player -> { };

    private RespawnGuard() {
    }

    public static void setReviveHook(Consumer<ServerPlayer> hook) {
        reviveHook = hook == null ? player -> { } : hook;
    }

    /** サーバ tick の終わりから呼ぶ。保護対象が死んでいたら復帰させる。 */
    public static void processRespawns(MinecraftServer server) {
        // respawn はプレイヤー一覧そのものを書き換える。反復しながらは触れない
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            UUID id = player.getUUID();
            if (!AutoGuard.isProtected(player)) {
                RESPAWN_STREAK.remove(id);
                GAVE_UP.remove(id);
                continue;
            }
            // 作り直しの連打を抑える数え。生きている相手にも進めないと、
            // 一度使った打ち止めが永久に解けない
            int spurious = SPURIOUS_COOLDOWN.getOrDefault(id, 0);
            if (spurious > 0) {
                SPURIOUS_COOLDOWN.put(id, spurious - 1);
            }
            // 通信路が別の実体を指しているなら、こちらが掴んでいる方が古い。
            // その状態で作り直すと、どちらが本物か分からなくなる
            if (PresenceGuard.stale(player)) {
                continue;
            }
            // isAlive() は使わない。読み出しを乗っ取られて「死んだことにされている」
            // だけの相手を蘇らせ続けると、実際には生きている実体を作り直しては
            // 死亡地点へ飛ばす無限ループになる。生死は同期データの側で判断する
            if (HealthGuard.rawAlive(player)) {
                if (ALIVE_TICKS.merge(id, 1, Integer::sum) >= STREAK_RESET_TICKS) {
                    RESPAWN_STREAK.remove(id);
                    GAVE_UP.remove(id);
                    ALIVE_TICKS.put(id, 0);
                }
                continue;
            }
            ALIVE_TICKS.put(id, 0);

            int cooldown = RESPAWN_COOLDOWN.getOrDefault(id, 0);
            if (cooldown > 0) {
                RESPAWN_COOLDOWN.put(id, cooldown - 1);
                continue;
            }
            if (GAVE_UP.contains(id)) {
                continue;
            }

            int streak = RESPAWN_STREAK.merge(id, 1, Integer::sum);
            if (streak > RESPAWN_STREAK_LIMIT) {
                GAVE_UP.add(id);
                GuardNotice.send(player, RESPAWN_STREAK_LIMIT
                        + " 回続けて即死したので自動復帰を止めました。"
                        + "手で復帰するか、安全な場所まで離れてください", true);
                continue;
            }
            revive(server, player, streak);
        }
    }

    /**
     * 生きているのに届いたリスポーン要求を、死なせずに受け止める。
     *
     * <p>断るだけでは死亡画面から出る道が無くなる (詰み)。だからといって通せば、
     * 嘘に基づいて本当に死ぬ。第三の道として<b>死を伴わない作り直し</b>を使う —
     * 持ち物も経験値も保ったまま実体を入れ替えるので、クライアントは新しい実体を受け取って
     * 画面を閉じ、こちらは何も失わない。嘘つきが実体に書いた印も、新しい実体には無い。
     *
     * @return 作り直した実体。要求が正当 (本当に死んでいる) なら null
     */
    public static ServerPlayer rebuildIfSpurious(ServerPlayer player) {
        if (player == null || !AutoGuard.isProtected(player) || !GuardContext.isSealedOrReverting()) {
            return null;
        }
        if (HealthGuard.rawHealth(player) <= 0.0F) {
            return null; // 本当に死んでいる。正規のリスポーンなので通す
        }
        UUID id = player.getUUID();
        int cooldown = SPURIOUS_COOLDOWN.getOrDefault(id, 0);
        if (cooldown > 0) {
            return null; // 連打されている。作り直しを重ねる方が壊れる
        }
        SPURIOUS_COOLDOWN.put(id, RESPAWN_COOLDOWN_TICKS);
        ServerPlayer revived = rebuildAlive(player);
        if (revived != null) {
            GuardNotice.send(revived, "生きているのにリスポーンが要求されました "
                    + "(クライアントが騙されています)。死なせずに作り直しました", true);
        }
        return revived;
    }

    /**
     * 死を伴わずに実体を作り直す。
     *
     * <p>クライアントの側で自機が消されたり死亡画面が焼き付いたりしたとき、
     * サーバ側で索引を直しても画面は戻らない。作り直しの通知だけが、
     * クライアントに世界を組み立て直させられる。
     *
     * @return 新しい実体。作り直せなければ null
     */
    public static ServerPlayer rebuildAlive(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || PresenceGuard.stale(player)) {
            return null;
        }
        float truth = HealthGuard.rawHealth(player);
        if (truth <= 0.0F) {
            truth = AutoGuard.knownGood(player.getUUID());
        }
        BlockPos spot = player.blockPosition();
        ServerLevel level = server.getLevel(player.level().dimension());
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        ServerPlayer[] holder = new ServerPlayer[1];
        // keepEverything = true。死としてではなく、エンドから帰るときと同じ扱いで作り直す
        DamageGuard.runAsSelf(() -> holder[0] = server.getPlayerList().respawn(player, true));
        ServerPlayer revived = holder[0];
        if (revived == null || revived == player) {
            return null;
        }
        if (revived.connection != null) {
            revived.connection.player = revived;
        }
        if (level != null) {
            revived.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, pitch);
        }
        // 引き継ぎは getHealth() を通る。偽装されていれば 0 が移ってくるので、素の値で上書きする
        float health = truth;
        if (health > 0.0F) {
            DamageGuard.runAsSelf(() -> revived.setHealth(health));
        }
        revived.invulnerableTime = RESPAWN_GRACE_TICKS;
        reviveHook.accept(revived);
        return revived;
    }

    /**
     * 索引から剥がされた相手の<b>クライアント側</b>を組み立て直す。
     *
     * <p>自機を消すパケットを本人へも送る相手が居る。サーバで載せ直しても、
     * クライアントには自分の実体が無いままで、操作も描画も戻らない。
     */
    static boolean rebuildClient(ServerPlayer player) {
        UUID id = player.getUUID();
        if (SPURIOUS_COOLDOWN.getOrDefault(id, 0) > 0) {
            return false;
        }
        SPURIOUS_COOLDOWN.put(id, RESPAWN_COOLDOWN_TICKS);
        return rebuildAlive(player) != null;
    }

    /** 作り直しの連打を抑える残り tick。 */
    private static final Map<UUID, Integer> SPURIOUS_COOLDOWN = new HashMap<>();

    private static void revive(MinecraftServer server, ServerPlayer player, int streak) {
        UUID id = player.getUUID();
        // 死んだ場所を先に控える。リスポーン後は初期スポーンへ飛ばされてしまい、
        // 現場から遠ざかると誰に殺されたのか観測できなくなる
        BlockPos deathSpot = player.blockPosition();
        ServerLevel deathLevel = server.getLevel(player.level().dimension());
        int deaths = DEATH_COUNT.merge(id, 1, Integer::sum);
        RESPAWN_COOLDOWN.put(id, RESPAWN_COOLDOWN_TICKS);

        // respawn は内部で Entity#remove を呼ぶ。素で通すと自分の絞り所がそれを
        // 「消されかけた」と誤認して自動対処を走らせてしまう
        ServerPlayer[] holder = new ServerPlayer[1];
        DamageGuard.runAsSelf(() -> holder[0] = server.getPlayerList().respawn(player, false));
        ServerPlayer revived = holder[0];
        if (revived == null) {
            return;
        }

        // respawn は新しい実体を作るが、通信路が指す先までは張り替えてくれない。
        // バニラはリスポーン要求を受けた側 (handleClientCommand) でこれを代入している。
        // ここを忘れると、通信路が消えたはずの古い実体を指したままになり、
        // 操作も描画もそちらへ流れて「透明で動かせないプレイヤー」が残る
        if (revived.connection != null) {
            revived.connection.player = revived;
        }

        boolean returnToSpot = streak < STOP_RETURNING_AT && deathLevel != null;
        if (returnToSpot) {
            revived.teleportTo(deathLevel,
                    deathSpot.getX() + 0.5, deathSpot.getY(), deathSpot.getZ() + 0.5,
                    revived.getYRot(), revived.getXRot());
        }
        revived.invulnerableTime = RESPAWN_GRACE_TICKS;
        reviveHook.accept(revived);
        // 死んだ以上、死亡処理の関所は「通って拒否できなかった」か「通っていない」かの
        // どちらか。この 2 つは対処が正反対なので、復帰のたびに言わせる
        long sinceDie = DamageGuard.sinceFired(DamageGuard.Kind.DIE);
        GuardNotice.send(revived, (returnToSpot ? "死亡地点に復帰しました" : "復帰しました")
                + " (" + deaths + " 回目"
                + (returnToSpot ? "" : " / 連続死のため現場には戻していません") + ")"
                + " ※死亡処理の関所は"
                + (sinceDie < 0 ? "一度も通っていません" : sinceDie + " ミリ秒前に通りました"), false);
    }

    /** ログアウトした相手の観測状態を手放す。 */
    public static void forget(UUID id) {
        SPURIOUS_COOLDOWN.remove(id);
        RESPAWN_COOLDOWN.remove(id);
        DEATH_COUNT.remove(id);
        RESPAWN_STREAK.remove(id);
        ALIVE_TICKS.remove(id);
        GAVE_UP.remove(id);
    }

    public static void reset() {
        SPURIOUS_COOLDOWN.clear();
        RESPAWN_COOLDOWN.clear();
        DEATH_COUNT.clear();
        RESPAWN_STREAK.clear();
        ALIVE_TICKS.clear();
        GAVE_UP.clear();
    }
}
