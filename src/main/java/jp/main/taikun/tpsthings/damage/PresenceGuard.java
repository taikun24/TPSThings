package jp.main.taikun.tpsthings.damage;

import jp.main.taikun.tpsthings.mixin.AccessorChunkMap;
import jp.main.taikun.tpsthings.mixin.AccessorEntity;
import jp.main.taikun.tpsthings.mixin.AccessorPersistentEntitySectionManager;
import jp.main.taikun.tpsthings.mixin.AccessorServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保護対象が世界の索引に載り続けているかを見張り、外されていたら載せ直す。
 *
 * <p>HP の書き戻し ({@link HealthGuard#revert}) の存在版。関所
 * ({@code EntityLookup#remove}) はメソッドの入口しか見張れないので、Accessor で
 * 索引の中身を直接いじる相手は素通りする。そこで発想を揃える —
 * <b>経路ではなく状態を守る</b>。どの経路で外されようと、載っているべきものが
 * 載っていなければ載せ直す。誰の仕業かは知らなくてよい。
 *
 * <p>見張る印は関所と同じで「削除されていないのに索引に居ない」。バニラの世界で
 * この組み合わせは起きない (居なくなるのは削除済みか、チャンクごと畳まれて
 * {@code isRemoved} が立った後だけ)。だから相手を名指しせずに異常だと言い切れる。
 *
 * <p>「削除されていない」も鵜呑みにしない。除去の印 ({@code removalReason}) はただの
 * フィールドで、印だけ直接立ててから索引を剥がせば、見張りは「正規の後始末」と見て
 * 手を引く。印の真偽は {@link #forgedRemoval} で見る。
 *
 * <p>索引から外された相手は tick が止まる。つまり<b>被害者自身の tick では
 * この攻撃を検出できない</b>。見張りはサーバ tick の側から回し、載せ直す先の実体は
 * まだ tick が回っていたうちに掴んでおいた強参照を使う。
 */
public final class PresenceGuard {

    /**
     * 載せ直すための実体の控え。
     *
     * 索引から外されると、世界からその実体を引く手段そのものが消える。
     * 弱参照では GC に回収された時点で復元が不可能になるので、強参照で持ち、
     * 削除か保護解除で必ず手放す。
     */
    private static final Map<UUID, LivingEntity> ANCHOR = new ConcurrentHashMap<>();
    /** 外され続けている連続 tick 数。始まりを一度だけ報告するために使う。 */
    private static final Map<UUID, Integer> STREAK = new ConcurrentHashMap<>();
    /** 載せ直した累計回数。どれだけ外され続けているかの目安。 */
    private static final Map<UUID, Integer> TOTAL = new ConcurrentHashMap<>();

    private PresenceGuard() {
    }

    /** 保護対象の tick から実体を控える。tick が来ている = いまは索引に居る。 */
    static void anchor(LivingEntity entity) {
        ANCHOR.put(entity.getUUID(), entity);
    }

    /**
     * 除去の印だけが、除去の関所を通らずに立てられているか。
     *
     * <p>保護対象への殺意のある除去 (KILLED / DISCARDED) は、除去の関所
     * ({@code setRemoved}) が必ず拒否する。自分の操作として通すのはリスポーンだけで、
     * そのとき古い実体は通信路から外れる ({@link #stale})。つまり
     * 「保護対象・古くない・殺意のある印」が揃うのは、印を関所の外から書かれたときだけ。
     *
     * <p>次元移動・チャンクの畳み・ログアウトの印は殺意が無いので、ここでは偽と言わない。
     */
    public static boolean forgedRemoval(Entity entity) {
        if (!entity.isRemoved() || !HealthGuard.isHostileRemoval(entity.getRemovalReason())) {
            return false;
        }
        if (!(entity instanceof LivingEntity living) || !AutoGuard.isProtected(living) || stale(living)) {
            return false;
        }
        if (entity instanceof ServerPlayer player) {
            // 一覧から外れた実体 (ログアウト済み) は、戻す先がもう無い
            MinecraftServer server = player.getServer();
            return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player;
        }
        return true;
    }

    /** サーバ tick の終わりに全員分を見回る。被害者の tick は止まっている前提で回す。 */
    public static void sweep() {
        if (ANCHOR.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, LivingEntity>> anchors = ANCHOR.entrySet().iterator();
        while (anchors.hasNext()) {
            LivingEntity entity = anchors.next().getValue();
            boolean forged = forgedRemoval(entity);
            // 削除済みは正規の後始末 (死亡・次元移動・チャンク畳み)。リスポーンで
            // 実体が作り直されたら、新しい方の tick が改めて控えを入れ直す
            if ((entity.isRemoved() && !forged) || !AutoGuard.isProtected(entity) || stale(entity)) {
                UUID id = entity.getUUID();
                anchors.remove();
                STREAK.remove(id);
                continue;
            }
            if (!AutoGuard.isReverting()) {
                continue;
            }
            if (forged) {
                // 印を下ろす。これで世界の側からも「削除されていない」に戻り、下の載せ直しが効く
                DamageGuard.runAsSelf(() -> ((AccessorEntity) entity).tpsthings$setRemovalReason(null));
                GuardNotice.send(entity, "除去の関所を通らずに除去の印だけを立てられていました。"
                        + "印を下ろして世界へ戻します", true);
                // 本人のクライアントからも自機を消す相手が居る。サーバで載せ直しても
                // 画面は戻らないので、作り直しの通知で組み立て直させる (連打は抑制付き)。
                //
                // 作り直しが通ったら、こちらが握っている実体は<b>古い方</b>になる。
                // そのまま載せ直すと同じ UUID が世界に 2 つ並び、後始末で世界の側が壊れる。
                // 新しい方は自分の tick で改めて控えられるので、ここは手を引く
                if (entity instanceof ServerPlayer player && RespawnGuard.rebuildClient(player)) {
                    anchors.remove();
                    STREAK.remove(entity.getUUID());
                    continue;
                }
            }
            // 上の作り直し以外にも、他所のリスポーンで置き換わることはある。
            // 載せ直す直前にもう一度確かめる (古い実体を送り返さないため)
            if (stale(entity)) {
                anchors.remove();
                STREAK.remove(entity.getUUID());
                continue;
            }
            // 死んでいる実体が索引から居なくなるのはリスポーンへの過渡状態。
            // 載せ直すと死体を世界へ送り返すことになる。死んだ相手の面倒は
            // 復帰処理の側が (世界の索引ではなく PlayerList 経由で) 見る。
            // ただし偽の除去を受けた相手は死亡処理を通っていない (通すなら印を偽る必要が無い)。
            // 索引から外されて自分の tick が止まると HP の書き戻しも届かないので、ここで戻す
            if (HealthGuard.rawHealth(entity) <= 0.0F) {
                float good = AutoGuard.knownGood(entity.getUUID());
                if (!forged || good <= 0.0F || HealthGuard.revert(entity, good) <= 0.0F) {
                    STREAK.remove(entity.getUUID());
                    continue;
                }
            }
            if (entity.level() instanceof ServerLevel level) {
                check(level, entity);
                checkExile(level, entity);
            }
        }
    }

    /**
     * 控えている実体が、リスポーンで置き換えられた後の古い方でないか。
     *
     * <p>索引を剥がす攻撃は {@code isRemoved} を立てない。その後に死んで実体が
     * 作り直されると、古い方は「削除されていないのに索引に居ない」を満たしたまま
     * 残り、載せ直しの条件に完全に一致してしまう。死んだ古い実体を送り返すと
     * 同じ UUID が世界に 2 つ並び、クライアントの移動が毎 tick 拒否される
     * (同じ位置に戻され続ける) 状態になる。
     *
     * <p>どちらが本物かは通信路が知っている。通信路が別の実体を指しているなら、
     * こちらが掴んでいる方が古い。復帰処理が使っているのと同じ見分け方。
     */
    static boolean stale(LivingEntity entity) {
        return entity instanceof ServerPlayer player
                && (player.connection == null || player.connection.player != player);
    }

    private static void check(ServerLevel level, LivingEntity entity) {
        UUID id = entity.getUUID();
        // 索引から本人が引けるか。別人が返るのも「引けない」に含める
        boolean indexed = level.getEntity(id) == entity;
        EntityTickList tickList = ((AccessorServerLevel) level).tpsthings$entityTickList();
        // tick 一覧はチャンクが tick 圏外に落ちると正規に抜ける。圏内なのに
        // 居ないときだけ異常。ここを見ないと、遠くの保護対象を毎 tick 誤修復する
        boolean starved = level.isPositionEntityTicking(entity.blockPosition())
                && !tickList.contains(entity);
        String severed = indexed ? severed(level, entity) : null;
        if (indexed && severed == null && !starved) {
            STREAK.remove(id);
            return;
        }

        String detail;
        if (indexed && severed == null) {
            // tick 一覧だけ抜かれている。存在はするが永久に動けない。一覧に足すだけ
            DamageGuard.runAsSelf(() -> tickList.add(entity));
            detail = "tick の一覧からだけ外されていました (存在はするが動けない状態)";
        } else {
            detail = rebuild(level, entity, indexed ? severed : "世界の索引から外されていました");
        }
        TOTAL.merge(id, 1, Integer::sum);
        if (STREAK.merge(id, 1, Integer::sum) == 1) {
            GuardNotice.send(entity, detail + "。載せ直しました。外されるたびに戻します", true);
        }
    }

    /**
     * 索引には居るのに、世界との繋がりのどこかが切られていないか。切られていればその中身。
     *
     * <ul>
     *   <li>後始末の繋がり ({@code levelCallback}) が NULL — 以後の移動も除去も索引に届かない</li>
     *   <li>追跡から外されている — 誰にも見えない。プレイヤーならチャンクも届かず操作不能になる</li>
     *   <li>プレイヤーの一覧から外されている — 範囲検索・睡眠判定・Mob の標的探しから消える</li>
     * </ul>
     * どれもバニラでは索引と同時にしか外れないので、索引に居るのに外れているなら異常。
     */
    private static String severed(ServerLevel level, LivingEntity entity) {
        if (((AccessorEntity) entity).tpsthings$levelCallback() == EntityInLevelCallback.NULL) {
            return "索引に居るまま、世界との後始末の繋がりを切られていました";
        }
        if (entity.getType().clientTrackingRange() != 0 && !tracked(level, entity)) {
            return "索引に居るまま、追跡 (周りへ姿を送る仕組み) から外されていました";
        }
        if (entity instanceof ServerPlayer player && !level.players().contains(player)) {
            return "索引に居るまま、プレイヤーの一覧から外されていました";
        }
        return null;
    }

    private static boolean tracked(ServerLevel level, Entity entity) {
        return ((AccessorChunkMap) level.getChunkSource().chunkMap).tpsthings$entityMap()
                .containsKey(entity.getId());
    }

    /**
     * 残骸を全部片付けてから、正面から登録し直す。
     *
     * <p>剥がし方は相手ごとに違い、どこが残っていてどこが消えているかは組み合わせ次第。
     * 残っている所だけ継ぎ足すと、台帳の重複で断られたり、追跡の二重登録で例外になったり、
     * 後始末の繋がりが切れたまま残ったりする。<b>一度ぜんぶ外して、バニラの登録を 1 回通す</b>方が、
     * 状態が必ずバニラの形に揃う。
     *
     * <p>登録はイベント無し版を使う。イベント経由だと正規 API のキャンセルで再登録そのものを
     * 拒否できてしまうし、蘇生したことを世界中に知らせる合図にもなる。
     */
    private static String rebuild(ServerLevel level, LivingEntity entity, String detail) {
        // 同じ UUID を別の実体が既に持っているなら、本物はそちら。こちらを足すと
        // 世界に同じ者が 2 人並び、次の後始末で世界の側が壊れる (実測で落ちた)
        Entity holder = level.getEntity(entity.getUUID());
        if (holder != null && holder != entity) {
            return detail + " (同じ UUID を別の実体が持っているので手を引きました)";
        }
        StringBuilder result = new StringBuilder(detail);
        DamageGuard.runAsSelf(() -> {
            purge(level, entity);
            try {
                PersistentEntitySectionManager<Entity> manager =
                        ((AccessorServerLevel) level).tpsthings$entityManager();
                if (!manager.addNewEntityWithoutEvent(entity)) {
                    result.append(" (再登録を断られました)");
                    return;
                }
                if (!entity.isAddedToWorld()) {
                    entity.onAddedToWorld();
                }
            } catch (RuntimeException | LinkageError failure) {
                // 直しきれなかったことは伝える。ここで投げると見回り全体が止まり、
                // 世界の tick ごと道連れになる
                result.append(" (載せ直しに失敗: ").append(failure).append(')');
            }
        });
        return result.toString();
    }

    /**
     * 世界がこの実体を数えている場所から、残っている分を外す。登録し直す直前にだけ使う。
     *
     * <p>区画は座標の場所だけでなく全部舐める。遠くへ飛ばしてから剥がす相手だと、
     * 載っている区画と座標がずれていて、座標から引いた区画には居ない。
     */
    private static void purge(ServerLevel level, LivingEntity entity) {
        AccessorServerLevel accessor = (AccessorServerLevel) level;
        PersistentEntitySectionManager<Entity> manager = accessor.tpsthings$entityManager();
        AccessorPersistentEntitySectionManager managerAccessor =
                (AccessorPersistentEntitySectionManager) (Object) manager;

        // 1 つずつ握りつぶす。相手にどこまで剥がされているかは分からず、
        // 「もう外れているものを外す」で世界の側が落ちることがある (実測)。
        // 片付けは best-effort でよく、ここで投げると世界の tick ごと道連れになる
        attempt(() -> accessor.tpsthings$entityTickList().remove(entity));
        attempt(() -> managerAccessor.tpsthings$visibleEntityStorage().remove(entity));
        attempt(() -> {
            EntitySectionStorage<EntityAccess> sections = managerAccessor.tpsthings$sectionStorage();
            for (long chunk : sections.getAllChunksWithExistingSections().toLongArray()) {
                sections.getExistingSectionsInChunk(chunk).forEach(section -> section.remove(entity));
            }
        });
        attempt(() -> managerAccessor.tpsthings$knownUuids().remove(entity.getUUID()));
        if (entity instanceof Mob mob) {
            attempt(() -> accessor.tpsthings$navigatingMobs().remove(mob));
        }
        // 追跡の二重登録は例外になる。残っていれば外してから登録に付け直させる
        if (tracked(level, entity)) {
            attempt(() -> level.getChunkSource().removeEntity(entity));
        }
        if (entity instanceof ServerPlayer player) {
            attempt(() -> level.players().removeIf(other -> other == player));
        }
    }

    /** 片付けの 1 手。失敗しても次の手へ進む。 */
    private static void attempt(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError failure) {
            GuardNotice.warnThrottled("presence-purge",
                    "索引の片付けの一手が失敗しました (続行します): " + failure);
        }
    }

    /**
     * 世界の外へ追放されていないか。
     *
     * <p>索引から外す前に、遠くの座標へ飛ばしてしまう手がある。索引には居るので
     * 存在の見張りには映らないのに、世界の誰からも届かない場所に置かれる —
     * 実質の隔離で、そのあと静かに消される。
     *
     * <p>印は<b>世界の縁の外に居ること</b>。縁の内側はバニラが移動を許す範囲なので、
     * 外に出ている時点で正規の移動ではない。誰が飛ばしたかは知らなくてよい。
     */
    private static void checkExile(ServerLevel level, LivingEntity entity) {
        UUID id = entity.getUUID();
        boolean exiled = !level.getWorldBorder().isWithinBounds(entity.blockPosition())
                || Math.abs(entity.getY()) > EXILE_HEIGHT;
        if (!exiled) {
            LAST_SANE.put(id, entity.position());
            return;
        }
        Vec3 back = LAST_SANE.get(id);
        if (back == null) {
            return; // 正常だった場所を知らない。戻す先が無いので触らない
        }
        DamageGuard.runAsSelf(() -> {
            if (entity instanceof ServerPlayer player && player.connection != null) {
                player.connection.teleport(back.x, back.y, back.z,
                        player.getYRot(), player.getXRot());
            } else {
                entity.teleportTo(back.x, back.y, back.z);
            }
        });
        GuardNotice.send(entity, "世界の外へ飛ばされていたので、直前の位置へ引き戻しました "
                + "(索引から外す前の隔離です)", true);
    }

    /** これより上下に離れていたら、正規の移動ではない。 */
    private static final double EXILE_HEIGHT = 3.0E7;
    /** 保護対象ごとの、最後に世界の中に居た位置。 */
    private static final Map<UUID, Vec3> LAST_SANE = new ConcurrentHashMap<>();

    /** 載せ直した累計。進捗表示用。 */
    public static int restoredCount(UUID id) {
        return TOTAL.getOrDefault(id, 0);
    }

    /** 保護対象から外れた相手の控えを手放す。 */
    public static void forget(UUID id) {
        ANCHOR.remove(id);
        LAST_SANE.remove(id);
        STREAK.remove(id);
        TOTAL.remove(id);
    }

    /** サーバ停止時に全部手放す。強参照なので、残すと世界ごと GC できなくなる。 */
    public static void reset() {
        ANCHOR.clear();
        LAST_SANE.clear();
        STREAK.clear();
        TOTAL.clear();
    }
}
