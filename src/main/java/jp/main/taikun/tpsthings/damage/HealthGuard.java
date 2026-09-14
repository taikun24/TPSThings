package jp.main.taikun.tpsthings.damage;

import jp.main.taikun.tpsthings.mixin.AccessorEntity;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HP の関所を、setter ではなく HP そのものが載っている入れ物の口に置く。
 *
 * {@code LivingEntity#setHealth} は、HP を {@code SynchedEntityData} に書きに行くための
 * 入口の 1 つでしかない。入口を見張ると、入口を通らずに書きに来る相手を丸ごと取り逃がす。
 * 自前のダメージ処理を持つ Mod は珍しくなく、その手のものは setHealth を経由せずに
 * 同期データへ直接書く。
 *
 * そこで関所を一段下げ、「HP という値が書き換わる瞬間」に置く。ここを通らずに HP を減らすには、
 * 入れ物のフィールドを直接叩くか、入れ物ごと差し替えるしかない。
 *
 * <p>読み出し側も塞げるようにしてある ({@link #setSealed}) 。書き込みだけ止めても、
 * 相手が HP を読んで「もう死んでいる」と判断して別経路で殺しに来るなら意味がないため。
 * ただし読み出しの偽装はゲーム全体に嘘をつく行為なので、既定では切ってある。
 *
 * <p>誰を止めるかは相変わらず実行時に決まる。特定の Mod を名指しする分岐は持たない。
 */
public final class HealthGuard {

    /**
     * HP を運んでいる鍵。
     *
     * 届いた書き込みが HP のものかを識別するのに要る。private static なので
     * 絞り所側から一度だけ預かる。
     */
    private static volatile EntityDataAccessor<Float> healthKey;

    /**
     * 保護対象の HP 減少を、呼び出し元を問わず全部拒否するか。
     *
     * 通常の DamageGuard は「誰の仕業か」を特定して、その署名だけを止める道具で、
     * これは性質が違う。原因が分からないまま死に続けるときの緊急避難として置く。
     */
    private static volatile boolean sealed = false;

    /** 保護対象ごとの、最後に通した HP。読み出しを偽装するときの床。 */
    private static final Map<UUID, Float> FLOOR = new ConcurrentHashMap<>();

    /**
     * いま読んでいるのは素の値か。
     *
     * 偽装した読み出しが自分自身に返ってくると、HP が減ったかどうかを永久に判定できなくなる。
     * 自分で読むときだけ床を外すための印。
     */
    private static final ThreadLocal<Boolean> RAW = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private HealthGuard() {
    }

    /**
     * 書き込みの関所が直近で反応したか。
     *
     * 関所は {@code @Inject} で刺してあるので、実行時に剥がされうる。剥がされたことは
     * 「何も起きない」という形でしか現れないので、生死は外から確かめるしかない。
     */
    private static volatile boolean writeGateSeen = false;

    /**
     * 書き込みの関所が生きているか、自分で 1 回書いて確かめる。
     *
     * <p>同じ値を書くので世界には何の影響も無い。関所が生きていれば必ず通るし、
     * 剥がされていれば何も反応しない。誰が剥がしたかは知らなくてよい。
     *
     * @return 生きていれば true。鍵がまだ無くて判定できないときも true
     */
    public static boolean writeGateAlive(LivingEntity living) {
        EntityDataAccessor<Float> health = healthKey;
        if (health == null) {
            return true;
        }
        writeGateSeen = false;
        float current = storedHealth(living);
        DamageGuard.runAsSelf(() -> living.getEntityData().set(health, current));
        return writeGateSeen;
    }

    /**
     * 保護対象の HP を実際に入れている箱。→ その持ち主。
     *
     * <p>{@code SynchedEntityData#set} は<b>入口の 1 つ</b>でしかない。値が実際に載るのは
     * その下の {@code DataItem} で、箱を直接掴んで書けば入口の関所は 1 つも通らない。
     * 実測でそれが起きていたので、関所を値のある場所まで下ろす。
     *
     * <p>箱は持ち主を知らないので、こちらで対応を控えておく。保護対象の分だけなので
     * 数は高々数個。同一性で引く (箱は equals を持たないので既定の同一性でよい)。
     */
    private static final Map<Object, WeakReference<LivingEntity>> HEALTH_ITEMS =
            new ConcurrentHashMap<>();

    /**
     * 保護対象の HP の箱を控える。毎 tick 呼ばれる前提で、変わっていなければ何もしない。
     *
     * リスポーンで実体が作り直されると箱も新しくなるので、控え直しが要る。
     */
    public static void trackHealthItem(LivingEntity living) {
        EntityDataAccessor<Float> health = healthKey;
        if (health == null) {
            return;
        }
        Map<?, ?> items = StateProbe.itemsOf(living.getEntityData());
        if (items == null) {
            return;
        }
        Object item = items.get(health.getId());
        if (item == null) {
            return;
        }
        WeakReference<LivingEntity> known = HEALTH_ITEMS.get(item);
        if (known != null && known.get() == living) {
            return; // 控え済み
        }
        // 実体が作り直されると箱も新しくなる。消えた実体の分は溜めない
        HEALTH_ITEMS.values().removeIf(reference -> {
            LivingEntity previous = reference.get();
            return previous == null || previous == living;
        });
        trackBox(item, living, 0);
    }

    /**
     * 箱を控える。<b>箱が別の箱を包んでいたら、包まれた側も控える</b>。
     *
     * <p>入れ物ごと自前のものに差し替え、外側の箱と内側の箱の両方に書く相手が居る。
     * 外側しか知らないと、値が実際に載っている内側への書き込みが関所を素通りする。
     * 「包み」は型で分かるので、相手が誰かは知らなくてよい。
     */
    private static void trackBox(Object item, LivingEntity living, int depth) {
        HEALTH_ITEMS.put(item, new WeakReference<>(living));
        if (depth >= MAX_BOX_DEPTH) {
            return;
        }
        for (java.lang.reflect.Field field : item.getClass().getDeclaredFields()) {
            if (!SynchedEntityData.DataItem.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object inner = field.get(item);
                if (inner != null && inner != item && !HEALTH_ITEMS.containsKey(inner)) {
                    trackBox(inner, living, depth + 1);
                }
            } catch (Throwable unreadable) {
                // 読めない包みは諦める。ここで落ちると控えそのものが止まる
            }
        }
    }

    /** 包みを辿る深さ。包みの包みまでで十分で、それ以上は追わない。 */
    private static final int MAX_BOX_DEPTH = 3;

    /**
     * 箱への直接の書き込みを拒否すべきか。入口を通らずに値だけ書きに来る相手への関所。
     *
     * <p>入口 ({@code set}) の関所と同じ判断を、同じ道具で行う。ここだけ独自の判断を
     * 持たせると、片方だけ通る抜け道がまた生まれる。
     */
    public static boolean shouldBlockItemWrite(Object item, Object value) {
        if (!(value instanceof Float next)) {
            return false;
        }
        WeakReference<LivingEntity> reference = HEALTH_ITEMS.get(item);
        LivingEntity owner = reference == null ? null : reference.get();
        if (owner == null) {
            return false; // 保護対象の HP の箱ではない
        }
        return shouldBlockWrite(owner, healthKey, next);
    }

    /** 絞り所から HP の鍵を預かる。最初の 1 回だけ効く。 */
    public static void rememberHealthKey(EntityDataAccessor<Float> key) {
        if (healthKey == null) {
            healthKey = key;
        }
    }

    /** 読み出しの正規化 ({@link ReaderGuard}) が正規の実装を組むのに使う。 */
    static EntityDataAccessor<Float> healthKeyOrNull() {
        return healthKey;
    }

    // ---- 書き込み ---------------------------------------------------------------

    /**
     * この書き込みを拒否すべきか。
     *
     * HP 以外の同期データは即座に抜ける。ここは全エンティティの全同期データが通るので、
     * 鍵の同一性判定より前に何もしてはいけない。
     */
    public static <T> boolean shouldBlockWrite(Entity owner, EntityDataAccessor<T> key, T value) {
        EntityDataAccessor<Float> health = healthKey;
        if (health == null || key != health) {
            return false;
        }
        if (!(owner instanceof LivingEntity living) || !(value instanceof Float next)) {
            return false;
        }
        // 関所が生きている証。剥がされていないかを外から確かめるのに使う
        writeGateSeen = true;
        // クライアント側はサーバから流れてきた結果を映しているだけ。ここで止めると表示だけが狂う
        if (!GuardContext.onServer(living)) {
            return false;
        }

        float current = storedHealth(living);
        if (next >= current) {
            // 減っていないので通す。ただし比較の元 (current) が既に 0 なら、
            // その 0 自体が関所を通らずに書かれたということ = 話は別の層にある
            if (current <= 0.0F && AutoGuard.isProtected(living)) {
                leak("HP の書き込み (既に 0 だった)",
                        String.format("%.2f → %.2f", current, next));
            }
            note(living, next); // 回復・据え置きは見ないが、床は上げておく
            return false;
        }

        boolean blocked = DamageGuard.shouldBlock(living, DamageGuard.Kind.HEALTH_WRITE, current - next);
        if (blocked) {
            return true;
        }
        // 自分で起こした書き込み (即時リスポーンなど) まで拒否すると、対処が対処を呼ぶ
        if (sealed && GuardContext.guardsAgainst(living)) {
            return true;
        }
        // 0 以下への書き込みだけは、書き戻しを待たずにその場で拒否する。
        // 書き戻しは tick の終わりに走るので、同じ tick のうちに「HP が 0 だから死んでいる」と
        // 判断して次の手に移る相手には間に合わない。死を拒否する方針なら、
        // その材料になる 0 も通さない (戻すのではなく、そもそも書かせない)
        if (next <= 0.0F && GuardContext.isSealedOrReverting() && GuardContext.guardsAgainst(living)) {
            return true;
        }
        // ここに保護対象が来るのは、上の 3 つのどれかが偽だったということ。
        // どれが偽かは結果を見ても分からないので、関所自身に言わせる
        if (AutoGuard.isProtected(living)) {
            leak("HP の書き込み", String.format("%.2f → %.2f", current, next));
        }
        note(living, next);
        return false;
    }

    /**
     * 保護対象への攻撃を関所が通してしまったときに、<b>なぜ通したのか</b>を残す。
     *
     * <p>「守れなかった」の原因は「関所を通っていない」「通ったが条件で外れた」の 2 つで、
     * 対処は正反対なのに、外から見た結果は同じ。ここが鳴れば後者だと確定する
     * (鳴らずに減っていれば前者)。連打されるので抑制付き。関所が違えば別の話なので、
     * 抑制の鍵は関所ごと。
     */
    private static void leak(String gate, String detail) {
        GuardNotice.warnThrottled(gate, gate + ": 保護対象なのに通しました (" + detail
                + ") seal=" + sealed + " self=" + DamageGuard.isSelfAction()
                + " reverting=" + AutoGuard.isReverting());
    }

    /**
     * この死亡処理を拒否すべきか。HP を一切削らずに殺しに来る経路への関所。
     *
     * {@code die} は普段 {@code hurt} から呼ばれるが、外から直接呼べば HP は満タンのまま
     * 死亡処理だけが走る。HP を見張る関所はこれを最後まで観測できない。
     * だから絞り所を死亡処理そのものにも置く。
     *
     * <p>ここを通すだけでも意味がある。止めなくても「誰が呼んだか」が記録に残るので、
     * それまで何をしても記録に現れなかった相手が、初めて名前を出す。
     */
    public static boolean shouldCancelDeath(LivingEntity victim) {
        // クライアント側の die は死亡画面の演出。止めても本体には届かない
        if (!GuardContext.onServer(victim)) {
            return false;
        }
        if (DamageGuard.shouldBlock(victim, DamageGuard.Kind.DIE, 0.0F)) {
            return true;
        }
        // 死の拒否を封印から切り離す。封印は既定 OFF なので、以前は「保護対象の素の HP が
        // 満タンなのに die を直接呼ばれる」型が通常状態で素通りしていた。
        // 素の HP が正なのに死ぬのはバニラでは起きない = それ自体が攻撃の署名
        if (GuardContext.guardsAgainst(victim)
                && (GuardContext.isSealedOrReverting() || storedHealth(victim) > 0.0F)) {
            // 拒否しただけだと HP 0 のまま生きている状態が残る。読み出しは偽装で
            // 隠せても、素の値を見に来る相手には死んで見えるので、床まで戻す
            restoreFloor(victim);
            // 拒否しても犯人探しは続ける。拒否した瞬間に観測をやめると、
            // HP も削除も通らない相手は一度も容疑者一覧に載らないまま殺し続けてくる
            AutoGuard.onDeathAttempt(victim);
            return true;
        }
        if (AutoGuard.isProtected(victim)) {
            leak("死亡処理", "raw=" + String.format("%.2f", storedHealth(victim)));
        }
        AutoGuard.onDeathAttempt(victim);
        return false;
    }

    /**
     * Forge イベント層 ({@code LivingDeathEvent}) で死を打ち消すべきか。
     *
     * <p>{@link #shouldCancelDeath} (die() の頭の関所) より 1 段浅い層。イベントを
     * 経由する正規の死はここで一番安く止まり、イベントを飛ばしてくる相手は
     * 深い側の関所が受け持つ。封印していなくても、保護対象なら常に効く。
     */
    public static boolean shouldCancelDeathEvent(LivingEntity victim) {
        return GuardContext.onServer(victim) && GuardContext.guardsAgainst(victim);
    }

    /**
     * 遺品を撒くのを拒否すべきか。
     *
     * <p>持ち物を落とす処理は死亡処理の中から呼ばれるが、外から直接呼ぶこともできる。
     * 死を拒否しているのに持ち物だけ撒かれると、装備由来の保護ごと剥がれて、
     * 次の一撃には無防備になる。<b>死を拒否する方針なら、死の後片付けも拒否する</b>。
     */
    public static boolean shouldRefuseDeathLoot(LivingEntity victim) {
        return GuardContext.onServer(victim) && GuardContext.guardsAgainst(victim)
                && GuardContext.isSealedOrReverting();
    }

    /**
     * 死体の後始末を拒否すべきか。
     *
     * 死亡処理を止めても、HP が 0 のままなら毎 tick ここが回って最後に消される。
     */
    public static boolean shouldRefuseDeathTick(LivingEntity victim) {
        if (!GuardContext.isSealedOrReverting() || !GuardContext.guardsAgainst(victim)) {
            return false;
        }
        if (!GuardContext.onServer(victim)) {
            return false;
        }
        restoreFloor(victim);
        return true;
    }

    /**
     * 減らされた HP を、減る前の値に書き戻す。
     *
     * <p>関所で弾くのとは性質が違う。弾くのは「書かせない」で、これは「書かせてから戻す」。
     * 戻す側には、<b>関所を通らない書き込みにも効く</b>という利点がある。どの経路で
     * 書かれようと、値が入れ物に入っている限り上から書き直せる。誰の仕業か分からなくてよい。
     *
     * <p>相手からは攻撃が通ったように見えるので、弾かれたと悟って別の手に切り替える
     * 作りの相手にも、切り替えの合図を与えずに済む。減った量もそのまま観測できるので、
     * 犯人探しの材料を細らせない。
     *
     * @return 書き戻したあとの素の HP
     */
    public static float revert(LivingEntity living, float target) {
        EntityDataAccessor<Float> health = healthKey;
        // 戻す先は 1 tick 前に実際に載っていた値。上限を計算し直す必要はないし、
        // 上限を問い合わせる経路まで書き換えられていたら、そちらを信じる方が危ない
        if (health == null || target <= 0.0F) {
            return storedHealth(living);
        }
        DamageGuard.runAsSelf(() -> living.getEntityData().set(health, target));
        FLOOR.put(living.getUUID(), target);
        return storedHealth(living);
    }

    /**
     * 素の HP を床まで戻す。
     *
     * <p>床は関所を通った書き込みでしか覚えないので、満タンのまま一度も HP が動いていない
     * 相手には無い。そこで何もしないと、死亡処理は拒否したのに HP 0 のまま次の tick まで
     * 宙吊りになり、その間に素の値を見に来る相手には死んで見える。最後に見た正の値で埋める。
     */
    private static void restoreFloor(LivingEntity living) {
        Float recorded = FLOOR.get(living.getUUID());
        float floor = recorded != null && recorded > 0.0F ? recorded : AutoGuard.knownGood(living.getUUID());
        if (floor <= 0.0F || storedHealth(living) >= floor) {
            return;
        }
        DamageGuard.runAsSelf(() -> living.setHealth(floor));
    }

    /**
     * このリスポーン (実体の作り直し) を拒否すべきか。
     *
     * <p>読み出しを乗っ取られてクライアントだけが「死んだ」と信じると、死亡画面から
     * リスポーン要求が飛んでくる。<b>生きている実体への作り直し要求は、嘘に基づく誤発注</b>。
     * 受けると同じ UUID の実体が二重になり、消し損ねた古い方が幽霊として残る。
     *
     * <p>断ったらクライアントに本当の HP を教え直す。誤発注の原因は向こうが
     * 死んだと思い込んでいることなので、原因の側も正す。
     *
     * @param keepEverything 生きたままの正規の作り直し (エンドからの帰還など) は true。
     *                       そちらは誤発注ではないので触らない
     */
    public static boolean shouldRefuseRespawn(ServerPlayer player, boolean keepEverything) {
        if (keepEverything || !GuardContext.guardsAgainst(player)) {
            return false;
        }
        if (!GuardContext.isSealedOrReverting()) {
            return false;
        }
        if (!rawAlive(player)) {
            REFUSED.remove(player.getUUID());
            return false; // 本当に死んでいる。正規のリスポーンなので通す
        }

        UUID id = player.getUUID();
        // 断りっぱなしにはしない。死亡画面から出る唯一の道がリスポーンなので、
        // 断り続けると「死ねないが操作もできない」詰みになる。訂正が届いても
        // なお要求してくるなら、こちらの訂正が届いていない。通す方がまし
        int refused = REFUSED.merge(id, 1, Integer::sum);
        if (refused > MAX_REFUSED_RESPAWNS) {
            REFUSED.remove(id);
            GuardNotice.send(player, "生きているのにリスポーン要求が続くので通しました。"
                    + "クライアント側が死亡画面から戻れていません", true);
            return false;
        }
        if (player.connection != null) {
            player.connection.send(new ClientboundSetHealthPacket(rawHealth(player),
                    player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
        }
        player.deathTime = 0;
        GuardNotice.send(player,
                "生きているのにリスポーンが要求されたので断りました。死んで見えるのは読み出しの嘘です", true);
        return true;
    }

    /** 同じ相手のリスポーン要求を続けて断ってよい回数。詰みを作らないための上限。 */
    private static final int MAX_REFUSED_RESPAWNS = 2;
    /** 相手ごとの、連続で断った回数。 */
    private static final Map<UUID, Integer> REFUSED = new ConcurrentHashMap<>();

    /**
     * この削除を拒否すべきか。
     *
     * HP だけ守っても、エンティティごと消されれば結果は同じになる。封印はこの経路も塞ぐ。
     *
     * <p>ただし理由は選ぶ。ディメンション移動もチャンクのアンロードも同じ削除を通るので、
     * 一律に拒否すると相手ではなく世界の側が壊れる。殺意のある 2 つだけを見る。
     */
    public static boolean shouldRefuseRemoval(Entity victim, Entity.RemovalReason reason) {
        // 除去の拒否は「読み偽装 (seal)」ではなく「保護対象か」で決める。
        // 以前は isSealedOrReverting を必須にしていたが、seal は既定 OFF・手動のみ。
        // それだと setRemoved を直接呼ぶ純粋除去が通常状態で素通りしていた
        // (「setRemove で負ける」の正体)。保護対象なら seal に関係なく殺意の理由を拒否する。
        // 自分の復帰処理は runAsSelf 内なので guardsAgainst が false になり巻き込まない。
        if (!GuardContext.onServer(victim) || !GuardContext.guardsAgainst(victim)) {
            return false;
        }
        return isHostileRemoval(reason);
    }

    /**
     * 殺意のある削除理由か。
     *
     * ディメンション移動もチャンクのアンロードも同じ削除を通るので、一律に拒否すると
     * 世界の側が壊れる。殺意のある 2 つだけを見る。
     */
    public static boolean isHostileRemoval(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED;
    }

    /**
     * 世界の索引から外すのを拒否すべきか。一番外側の層の関所。
     *
     * <p>HP も死亡処理も削除処理も全部素通りして、世界の登録だけを消せば、
     * エンティティは生きたまま存在しなくなる。ここが最後の関所になる。
     *
     * <p>目印になるのは<b>削除されていないのに索引から外されること</b>。
     * バニラがここへ来るのは、削除済みか、見えなくなったチャンクを畳むときだけで、
     * 生きて動いている相手を索引から外すことはない。だから、その組み合わせ自体が異常で、
     * 相手が誰かを知らなくても異常だと言い切れる。
     */
    public static boolean shouldRefuseUnregister(Entity victim) {
        if (!GuardContext.onServer(victim)) {
            return false;
        }
        // 削除済みの後始末は正規の経路。止めると世界が畳めなくなる。
        // 正規の除去 (チャンク畳み・ディメンション移動) は setRemoved が先に isRemoved を
        // 立ててからここへ来るので、isRemoved==true は通す。
        // 絞り所より先に見る。後にすると、正規の後始末の連鎖が自動対処の容疑者に並ぶ。
        // ただし印だけ直接書かれた偽の除去は正規ではない。印を立ててから索引を剥がすのが
        // この関所を黙らせる定石なので、印の真偽まで見る
        if (rawRemoved(victim) && !PresenceGuard.forgedRemoval(victim)) {
            return false;
        }
        if (DamageGuard.shouldBlock(victim, DamageGuard.Kind.UNREGISTER, 0.0F)) {
            return true;
        }
        if (!GuardContext.guardsAgainst(victim)) {
            return false;
        }
        AutoGuard.onUnregisterAttempt(victim);
        // ここまで来た = 保護対象が「削除されていないのに索引から外されようとしている」。
        // バニラの世界でこの組み合わせは起きない = それ自体が攻撃署名なので、seal に
        // 関係なく拒否する。以前は sealed を返していたが、seal 既定 OFF だと索引直削除
        // (setRemoved を通さず EntityLookup.remove を直接叩く型) が素通りしていた。
        return true;
    }

    // ---- 読み出し ---------------------------------------------------------------

    /**
     * 読み出しに返す床。偽装しないなら null。
     *
     * 書き込みを止めていれば素の値も下がらないので、ここが効くのは
     * 入れ物のフィールドを直接書かれたときだけ。つまり関所より下の層への備え。
     */
    public static Float floorFor(Entity owner, EntityDataAccessor<?> key) {
        if (!sealed) {
            return null;
        }
        EntityDataAccessor<Float> health = healthKey;
        if (health == null || key != health || RAW.get()) {
            return null;
        }
        if (!(owner instanceof LivingEntity living) || !AutoGuard.isProtected(living)) {
            return null;
        }
        return FLOOR.get(living.getUUID());
    }

    /**
     * 偽装を外して HP を読む。
     *
     * 減ったかどうかを見張る側は、必ずこちらを使う。
     *
     * <p>{@code getHealth()} は決して使わない。あれはただのメソッドで、書き換えられる。
     * 見張る側が書き換えられた読み出しを信じたら、何が起きても永久に気づけない。
     * HP が実際に載っているのは同期データの側なので、そちらを直接読む。
     */
    public static float rawHealth(LivingEntity living) {
        return storedHealth(living);
    }

    /**
     * 偽装に影響されない生死判定。
     *
     * <p>{@code isAlive()} / {@code isDeadOrDying()} もただのメソッドで、書き換えられる。
     * 「死んだことにする」書き換えを信じた側が復帰処理を回すと、実際には生きている
     * 相手を蘇らせ続ける無限ループになる。生死を<b>判断する</b>側は必ずこちらを使う。
     */
    public static boolean rawAlive(LivingEntity living) {
        // isRemoved() も印を直接書けば立つ。偽の印を信じると、生きている相手を作り直す (= 殺す)
        boolean removed = rawRemoved(living) && !PresenceGuard.forgedRemoval(living);
        return !removed && storedHealth(living) > 0.0F;
    }

    /**
     * 偽装に影響されない「除去済みか」。印のフィールドを直に読む。
     *
     * <p>{@code isRemoved()} もただのメソッドで、本体を書き換えれば印が無くても true を返せる。
     * それを信じると「削除済みだから死んでいる」で辻褄が合ってしまい、嘘そのものが見えなくなる
     * (画面は {@code isRemoved()} を見て勝手に閉じるのに、見張る側は一致していると判断する)。
     */
    public static boolean rawRemoved(Entity entity) {
        return ((AccessorEntity) entity).tpsthings$getRemovalReason() != null;
    }

    /**
     * 死亡演出だけ再生されている状態を戻す。
     *
     * <p>{@code deathTime} はただの公開フィールドで、進めるのに死亡処理は要らない。
     * これを直接進めれば、サーバ側では生きているのに<b>見た目だけ倒れて動けない</b>
     * 状態が作れる (倒れた自分の当たり判定が視線上に来るので、クリックが自分に当たる)。
     *
     * <p>正規の死では deathTime が進む間 HP は必ず 0 以下なので、
     * 「素の HP は正なのに deathTime が進んでいる」は演出だけの死と言い切れる。
     */
    public static void healDeathPose(LivingEntity living) {
        if (living.deathTime > 0 && rawAlive(living)) {
            living.deathTime = 0;
        }
    }

    /**
     * 世界に対して見えている方の HP。
     *
     * 自分の偽装は外して読む。ここが {@link #rawHealth} と食い違うなら、
     * 偽装しているのは自分ではない誰かということになる。
     */
    public static float visibleHealth(LivingEntity living) {
        return withRaw(living::getHealth);
    }

    private static float storedHealth(LivingEntity living) {
        EntityDataAccessor<Float> health = healthKey;
        if (health == null) {
            return living.getHealth();
        }
        // 値が実際に載っている箱から直に読む。{@code get} もただのメソッドで、
        // 頭に注入すれば嘘をつける — 実際に HP を 0 と返してくる相手が居る。
        // そこを信じると、見張る側が「本当は満タン」を永久に見られなくなる
        Float boxed = boxedHealth(living, health);
        if (boxed != null) {
            return boxed;
        }
        return withRaw(() -> living.getEntityData().get(health));
    }

    /** 箱の中身を直接読む。箱が引けないときだけ null。 */
    private static Float boxedHealth(LivingEntity living, EntityDataAccessor<Float> health) {
        Map<?, ?> items = StateProbe.itemsOf(living.getEntityData());
        if (items != null && items.get(health.getId()) instanceof SynchedEntityData.DataItem<?> item
                && item.getValue() instanceof Float value) {
            return value;
        }
        return null;
    }

    /**
     * 自分の偽装 (封印中の床) を外して読む。
     *
     * <p>見張る側が自分の嘘を他人の嘘と数えると、居もしない相手を探し続けることになる。
     */
    static <T> T withRaw(java.util.function.Supplier<T> body) {
        boolean previous = RAW.get();
        RAW.set(Boolean.TRUE);
        try {
            return body.get();
        } finally {
            RAW.set(previous);
        }
    }

    /**
     * まだ床が無ければ、いまの値を床にする。
     *
     * 床は関所を通った書き込みでしか上下しない。関所を通らずに減らされた値をここで
     * 拾ってしまうと、床が相手に引きずられて意味を失うので、既にあるときは触らない。
     */
    public static void seedFloor(LivingEntity living, float value) {
        if (sealed && value > 0.0F) {
            FLOOR.putIfAbsent(living.getUUID(), value);
        }
    }

    /** 床を覚える。保護対象以外は覚えない。全エンティティ分を抱えると際限がない。 */
    private static void note(LivingEntity living, float value) {
        if (value > 0.0F && AutoGuard.isProtected(living)) {
            FLOOR.put(living.getUUID(), value);
        }
    }

    // ---- 設定面 -----------------------------------------------------------------

    public static boolean isSealed() {
        return sealed;
    }

    public static void setSealed(boolean value) {
        sealed = value;
        if (!value) {
            FLOOR.clear();
        }
    }

    /** 保護対象から外れた、あるいは居なくなった相手を忘れる。 */
    public static void forget(UUID id) {
        FLOOR.remove(id);
        REFUSED.remove(id);
    }

    public static void reset() {
        FLOOR.clear();
        REFUSED.clear();
    }
}
