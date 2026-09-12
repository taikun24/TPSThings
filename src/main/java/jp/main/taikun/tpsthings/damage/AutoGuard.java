package jp.main.taikun.tpsthings.damage;

import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 保護対象の HP が実際に減ったことを検出して、原因のメソッドを自動で止める。
 *
 * 絞り所でのキャンセルは、書き込みを弾かれたか読み戻して確認し別手段で書き直す作りには
 * 効かない。そこで「止めた」「まだ減っている」を観測しながら段階的に手を強くする:
 *
 * <pre>
 *   1. 呼び出し元を disable (メソッド本体を実行時に空にする)。
 *      disable できないものだけ block (絞り所でキャンセル) で代用
 *   2. それでも減るなら、呼び出し連鎖を 1 段外側へずらして 1 に戻る
 * </pre>
 *
 * 何を止めるかは常に実測から決まる。特定の相手を前提にした知識は持たない。
 */
public final class AutoGuard {

    private enum Stage {
        NONE,
        DISABLED
    }

    /** 段階を進めたあと、効果を見るために待つ tick 数。 */
    private static final int COOLDOWN_TICKS = 20;
    /** 1 つの保護対象について覚えておく連鎖の本数。 */
    private static final int MAX_CHAINS = 6;
    /**
     * 連鎖をこの tick 数より前に観測したものは、今回の HP 減少とは無関係とみなす。
     *
     * これが無いと、一度観測した連鎖が居座り続け、落下ダメージだろうが別の Mod だろうが
     * 「同じ相手の仕業」として扱ってしまう。
     */
    private static final int MAX_CHAIN_AGE_TICKS = 3;

    /** 遡る段数の上限として許す範囲。 */
    private static final int MIN_ALLOWED_DEPTH = 1;
    private static final int MAX_ALLOWED_DEPTH = 32;

    /**
     * 呼び出し連鎖を遡る上限。
     *
     * 外へ行くほど「ダメージを与えるメソッド」から「相手の挙動そのもの」に近づく。
     * 遡り切ると相手が行動不能になるので、既定では浅めに抑えて手動に委ねる。
     */
    private static volatile int maxDepth = 3;
    /** 浮動小数の誤差でHP減少と誤認しないための下限。 */
    private static final float EPSILON = GuardContext.EPSILON;

    private static final Set<UUID> PROTECTED = ConcurrentHashMap.newKeySet();
    /**
     * 手動 (コマンド) で指定された保護対象。
     *
     * 装備由来の同期 ({@link #setProtected}) は装備変更・ログイン・リスポーンのたびに
     * 上書きしてくるので、同じ集合に入れると手動指定が次のイベントで消える。
     * 保護対象かどうかは両方の和で判定する。
     */
    private static final Set<UUID> MANUAL = ConcurrentHashMap.newKeySet();
    /**
     * 保護対象ごとの、観測済みの呼び出し連鎖。
     *
     * 攻撃経路が枝分かれしている相手だと連鎖は 1 本では足りない。
     * 1 本しか持たないと、外へ遡った先の共通の親 (tick など) にしか行き着かず、
     * 相手を丸ごと止めてしまう。枝を枝のまま複数持って、浅い段で横に潰す。
     */
    private static final Map<UUID, List<List<String>>> CHAINS = new ConcurrentHashMap<>();
    /** 連鎖を最後に観測してからの経過 tick。古い連鎖に対して段を進めないため。 */
    private static final Map<UUID, Integer> CHAIN_AGE = new ConcurrentHashMap<>();
    /**
     * 関所を最後に通ってからの経過 tick。
     *
     * {@link #CHAIN_AGE} と違い、候補が 1 つも残らなかった場合も数える。
     * 「関所を通っていない」と「通ったが全部バニラだった」は原因が別物なので、
     * 同じ「観測できていません」で片付けると次の一手を間違える。
     */
    private static final Map<UUID, Integer> GATE_AGE = new ConcurrentHashMap<>();
    /**
     * 普通のダメージ経路 (素の hurt) を最後に通ってからの経過 tick。
     *
     * 普通に殴られただけの減少で「呼び出し元がバニラだけでした」と警告すると、
     * 毎回の被弾で画面が埋まり、本当に迂回してきた一件が埋もれる。
     */
    private static final Map<UUID, Integer> ORDINARY_AGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_HEALTH = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> DEPTH = new ConcurrentHashMap<>();
    private static final Map<UUID, Stage> STAGE = new ConcurrentHashMap<>();

    /** 自動で適用したものだけを覚えておく。手で入れたものを reset で巻き込まないため。 */
    private static final Set<String> AUTO_BLOCKED = ConcurrentHashMap.newKeySet();
    private static final Set<String> AUTO_DISABLED = ConcurrentHashMap.newKeySet();

    private static volatile boolean enabled = false;
    /**
     * 減らされた HP をその tick のうちに書き戻すか。
     *
     * <p>自動対処が本来やることは「犯人を特定して止める」だが、特定には時間がかかるし、
     * そもそも特定できない層もある。その間ずっと倒れていては、観測すら続かない。
     * 書き戻しは<b>誰の仕業か分からなくても効く</b>ので、犯人探しの土台になる。
     *
     * <p>止めるのではなく戻すだけなので、相手のコードには一切触らない。
     * 効かなくなったら、そこで初めて相手を止めにいく段階へ進む。
     */
    private static volatile boolean revert = true;
    /** 相手ごとの、書き戻しが続いている tick 数。始まりを一度だけ伝えるために使う。 */
    private static final Map<UUID, Integer> REVERT_TICKS = new ConcurrentHashMap<>();
    /**
     * 最後に観測した正の HP。
     *
     * 書き戻しの戻し先は普段「1 tick 前の値」だが、基準を取り直した隙に 0 以下まで
     * 落とされると、1 tick 前の値そのものが 0 以下になる。書き戻しは 0 以下への復元を
     * 拒否するので、そのままだと「死亡処理は拒否され続けるが HP は負のまま」という
     * 宙吊りで止まる。そこからの戻り先として、正の値を別に覚えておく。
     */
    private static final Map<UUID, Float> KNOWN_GOOD = new ConcurrentHashMap<>();
    /** 相手ごとの、書き戻した HP の累計。どれだけ殴られているかの目安。 */
    private static final Map<UUID, Float> REVERT_TOTAL = new ConcurrentHashMap<>();
    /**
     * 連鎖の古さを問わずに段を進めるか。
     *
     * 既定では、直近に観測した呼び出し元が無い減少には手を出さない。落下ダメージを
     * 別の Mod のせいにしないための安全弁だが、絞り所を一切通らない相手を追うときは
     * この安全弁が邪魔になる。そういうときだけ外す。
     */
    private static volatile boolean ignoreStaleChains = false;

    /**
     * 毎 tick の関所が最後に反応した時刻。
     *
     * こちらの {@code @Inject} を実行時に剥がしてくる相手が居るので、関所そのものの
     * 生死を外から見張る必要がある。心拍が途絶えたら関所が剥がされた疑い。
     */
    private static volatile long lastTickHook = 0L;

    private AutoGuard() {
    }

    /**
     * 毎 tick の関所が最後に反応してからの経過。
     *
     * 一度も反応していないなら 0 を返す。まだ何も分からない状態を
     * 「剥がされた」と誤認させないため。
     */
    public static long nanosSinceTickHook() {
        long last = lastTickHook;
        return last == 0L ? 0L : System.nanoTime() - last;
    }

    // ---- 設定面 -----------------------------------------------------------------

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * いま書き戻しが効いているか。
     *
     * 死亡処理の関所は、これが立っているなら封印と同じ扱いで死を拒否する。
     * 書き戻しは tick の終わりに走るので、tick の途中で死なれるとそこで終わってしまう。
     */
    public static boolean isReverting() {
        return enabled && revert;
    }

    /**
     * 書き戻しのスイッチそのもの。
     *
     * {@link #isReverting} は自動対処が切れていれば false になる。設定として保存したり
     * 表示したりするのはスイッチの側で、そうしないと自動対処を切った瞬間に設定が消える。
     */
    public static boolean isRevertEnabled() {
        return revert;
    }

    public static void setReverting(boolean value) {
        revert = value;
        if (!value) {
            REVERT_TICKS.clear();
        }
    }

    public static boolean isIgnoringStaleChains() {
        return ignoreStaleChains;
    }

    public static void setIgnoreStaleChains(boolean value) {
        ignoreStaleChains = value;
    }

    public static int getMaxDepth() {
        return maxDepth;
    }

    /**
     * 遡る段数の上限を変える。
     *
     * 上げると止まる可能性は上がるが、相手の挙動そのものを潰して行動不能にする危険も上がる。
     *
     * @return 実際に設定された値 (範囲外は丸める)
     */
    public static int setMaxDepth(int value) {
        maxDepth = Math.max(MIN_ALLOWED_DEPTH, Math.min(value, MAX_ALLOWED_DEPTH));
        // 打ち止め済みの対象が新しい上限で再開できるようにする
        COOLDOWN.clear();
        return maxDepth;
    }

    /** 最後に観測した正の HP。まだ見ていなければ 0。 */
    static float knownGood(UUID id) {
        return KNOWN_GOOD.getOrDefault(id, 0.0F);
    }

    public static boolean isProtected(Entity entity) {
        if (entity == null || (PROTECTED.isEmpty() && MANUAL.isEmpty())) {
            return false;
        }
        UUID id = entity.getUUID();
        return PROTECTED.contains(id) || MANUAL.contains(id);
    }

    /** 装備由来の保護。装備の付け外しに追従して呼ばれるので、手動指定には触らない。 */
    public static void setProtected(Entity entity, boolean value) {
        UUID id = entity.getUUID();
        if (value) {
            PROTECTED.add(id);
        } else {
            PROTECTED.remove(id);
            if (!MANUAL.contains(id)) {
                forget(id);
            }
        }
    }

    /**
     * 保護状態と自動対処の有無を、装備しているかどうかに合わせる。
     *
     * 装備変更・ログイン・リスポーン・次元移動のたびにイベント側から呼ばれる。
     * 手動指定 ({@link #setManuallyProtected}) はここでは触らない。
     *
     * <p>対象は {@link LivingEntity} で受ける。関所はどれも実体単位で動いているので、
     * 着ているのがプレイヤーかどうかは保護の条件にならない。プレイヤー限定にしていると
     * 「着せた Mob」を試し撃ちの的に出来ず、関所の検証がいつまでも本番でしか出来ない。
     */
    public static void syncEquipProtection(LivingEntity wearer, boolean wearing) {
        if (wearer.level().isClientSide()) {
            return;
        }
        setProtected(wearer, wearing);
        if (wearing) {
            setEnabled(true);
        } else if (protectedCount() == 0) {
            setEnabled(false);
        }
    }

    /** 手動 (コマンド) の保護。装備由来の同期に上書きされない。 */
    public static void setManuallyProtected(Entity entity, boolean value) {
        UUID id = entity.getUUID();
        if (value) {
            MANUAL.add(id);
        } else {
            MANUAL.remove(id);
            if (!PROTECTED.contains(id)) {
                forget(id);
            }
        }
    }

    public static boolean isManuallyProtected(Entity entity) {
        return entity != null && MANUAL.contains(entity.getUUID());
    }

    /** 手動保護の UUID。保存して次回に引き継ぐために要る。 */
    public static Set<String> manualProtectedIds() {
        Set<String> ids = new java.util.LinkedHashSet<>();
        MANUAL.forEach(id -> ids.add(id.toString()));
        return ids;
    }

    /** 保存されていた手動保護を覚え直す。 */
    public static void restoreManualProtected(Set<String> ids) {
        for (String id : ids) {
            try {
                MANUAL.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // 壊れた行は黙って捨てる。ここで落ちると復元全体が止まる
            }
        }
    }

    /** 保護対象 (装備由来 + 手動) の全 UUID。 */
    private static Set<UUID> allProtected() {
        if (MANUAL.isEmpty()) {
            return PROTECTED;
        }
        Set<UUID> all = new java.util.HashSet<>(PROTECTED);
        all.addAll(MANUAL);
        return all;
    }

    public static int protectedCount() {
        return allProtected().size();
    }

    private static void forget(UUID id) {
        HealthGuard.forget(id);
        GuardNotice.forget(id);
        PresenceGuard.forget(id);
        REVERT_TICKS.remove(id);
        REVERT_TOTAL.remove(id);
        KNOWN_GOOD.remove(id);
        CHAINS.remove(id);
        CHAIN_AGE.remove(id);
        GATE_AGE.remove(id);
        ORDINARY_AGE.remove(id);
        LAST_HEALTH.remove(id);
        COOLDOWN.remove(id);
        DEPTH.remove(id);
        STAGE.remove(id);
    }

    /** 保護対象ごとの現在の進捗。 */
    public static List<String> describeProgress() {
        List<String> lines = new ArrayList<>();
        for (UUID id : allProtected()) {
            List<List<String>> chains = CHAINS.getOrDefault(id, List.of());
            int depth = DEPTH.getOrDefault(id, 0);
            Stage stage = STAGE.getOrDefault(id, Stage.NONE);
            List<String> targets = new ArrayList<>();
            for (List<String> chain : chains) {
                if (depth < chain.size() && !targets.contains(chain.get(depth))) {
                    targets.add(chain.get(depth));
                }
            }
            float reverted = REVERT_TOTAL.getOrDefault(id, 0.0F);
            int restored = PresenceGuard.restoredCount(id);
            lines.add(id + " → " + stage + " / 深さ " + depth
                    + " / 枝 " + chains.size() + " 本"
                    + (reverted > 0.0F ? String.format(" / 書き戻し %.1f", reverted) : "")
                    + (restored > 0 ? " / 載せ直し " + restored + " 回" : "")
                    + " / 対象 "
                    + (targets.isEmpty() ? "(候補なし)" : String.join(", ", targets)));
        }
        return lines;
    }

    // ---- 観測 -------------------------------------------------------------------

    /** 絞り所から、保護対象が殴られた/消されかけたときの呼び出し連鎖を受け取る。 */
    static void observe(Entity victim, DamageGuard.Kind kind, List<String> frames) {
        UUID id = victim.getUUID();
        // 候補が残らなくても「関所は通った」ことは覚える。通っていないのか、
        // 通ったが全部除外対象だったのかで、次に取るべき手がまったく違う
        GATE_AGE.put(id, 0);

        // 普通のダメージ経路 (hurt) を通ってきたものは、書き戻しと死亡の拒否で防げる。
        // 相手のコードを潰しにいくのは、その経路を迂回してきたものだけにする。
        // 経路がバニラだけだと、連鎖で最初に出会う他所のコードは攻撃者ではなく
        // tick を回している側になり、それを潰すと世界ごと止まる
        if (kind == DamageGuard.Kind.HURT) {
            rememberHurtFrame(frames);
            ORDINARY_AGE.put(id, 0);
            return;
        }
        if (withinOrdinaryDamage(frames)) {
            ORDINARY_AGE.put(id, 0);
            return;
        }

        List<String> candidates = candidates(frames);
        if (candidates.isEmpty()) {
            return;
        }
        CHAIN_AGE.put(id, 0);
        List<List<String>> chains = CHAINS.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>());
        if (chains.contains(candidates)) {
            return;
        }
        if (chains.size() >= MAX_CHAINS) {
            chains.remove(0);
        }
        chains.add(candidates);
    }

    /**
     * hurt の関所が刺さっている素のバニラのフレーム (署名)。
     *
     * 名前を決め打ちしない (開発環境と本番で名前が違う)。関所が実際に反応したときの
     * 連鎖の先頭がそれなので、実測で覚える。
     */
    private static volatile String hurtFrame;

    /**
     * 普通の攻撃が通る素の道 (署名の集合)。<b>実測で覚える</b>。
     *
     * <p>普通に殴られたとき、絞り所から外へ向かって<b>他所のコードに出会うまで</b>に
     * 通った素のフレームがそれ。殴った相手が誰であっても、殴る道そのものは変わらない。
     *
     * <p>ここから外側は「攻撃を頼んだ側」で、中身がどうなっているかは知らない。
     * 名前は決め打ちしない (開発環境と本番で変わる)。
     */
    private static final Set<String> ORDINARY_PATH = ConcurrentHashMap.newKeySet();

    /** 覚えすぎない。普通の攻撃の道はそう長くないし、長い分だけ誤って切る危険が増える */
    private static final int MAX_ORDINARY_PATH = 24;

    private static void rememberHurtFrame(List<String> frames) {
        if (frames.isEmpty()) {
            return;
        }
        String first = frames.get(0);
        // Mixin で本体ごと差し替えられているなら、それは普通の経路ではない
        if (hurtFrame == null && GuardContext.isInfrastructure(first)
                && DamageGuard.mixinOwner(first) == null) {
            hurtFrame = first;
        }
        // 他所のコードに出会うまでが「普通の攻撃の道」。出会った先は頼んだ側なので覚えない。
        // ここを越えて覚えると、世界を回している道まで「普通の道」になって境界が消える
        for (String frame : frames) {
            if (isForeignCode(frame)) {
                return;
            }
            if (ORDINARY_PATH.size() < MAX_ORDINARY_PATH) {
                ORDINARY_PATH.add(frame);
            }
        }
    }

    /**
     * この書き込み/死亡処理は、普通のダメージ処理 (素の hurt) の内側で起きたか。
     *
     * <p>関所から外へ向かって、他所のコードに出会う<b>前に</b>素の hurt に行き着いたら、
     * 起こしたのはバニラのダメージ処理そのもの。hurt の中で他所のイベントハンドラや
     * 注入が HP を直接書いたなら、先に他所のコードに出会うので、こちらには入らない。
     * 責任は連鎖の総和ではなく、一番近い書き手にだけ問う。
     */
    private static boolean withinOrdinaryDamage(List<String> frames) {
        String hurt = hurtFrame;
        if (hurt == null) {
            return false;
        }
        for (String frame : frames) {
            if (frame.equals(hurt) || ORDINARY_PATH.contains(frame)) {
                return true;
            }
            if (isForeignCode(frame)) {
                return false;
            }
        }
        return false;
    }

    /** 自分のでも素の基盤のでもないコードか。 */
    private static boolean isForeignCode(String frame) {
        // 自分の関所は絶対に容疑者にしない。Mixin で注入したメソッドはバニラの
        // クラス名を着るので、出所を見る側の判定にも同じ穴が開いていた
        if (DamageGuard.isOwn(frame)) {
            return false;
        }
        // バニラの名前を着ていても、中身が他所から持ち込まれたものなら他所のコード。
        // ここで一律に除いていたせいで、Mixin で注入してくる相手は
        // 「呼び出し元はバニラだけ」として毎回取り逃がしていた
        return !GuardContext.isInfrastructure(frame) || DamageGuard.mixinOwner(frame) != null;
    }

    /** 自動で潰してよいか。他所のコードでも、生き物を tick させる道の上に居るものは潰さない。 */
    private static boolean isSuspect(String frame) {
        return isForeignCode(frame) && !TickRoots.isRoot(frame);
    }

    /**
     * 自動で潰してよいフレームだけを、絞り所に近い順に残す。
     *
     * <p><b>普通の攻撃の道に行き着いたら、そこで打ち切る。</b>
     * 連鎖には「嘘をついた側」と「攻撃を頼んだだけの側」が一本に並んでいて、
     * 外へ遡るだけでは後者に行き着く。自動クリッカーのように<b>ただ普通に殴っただけ</b>の
     * 相手の仕組みを潰してしまうのはこれが原因で、実際にクリッカーが動かなくなった。
     *
     * <p>普通の攻撃の道より内側に他所のコードが居るなら、嘘をついたのはそいつ。
     * 居ないときだけ外側を見る (道を通らずに殺しに来る相手はそもそも境界を持たない)。
     */
    private static List<String> candidates(List<String> frames) {
        List<String> result = new ArrayList<>();
        for (String frame : frames) {
            if (!result.isEmpty() && ORDINARY_PATH.contains(frame)) {
                break; // ここから外は、普通の攻撃を頼んだだけの側
            }
            if (isSuspect(frame) && !result.contains(frame)) {
                result.add(frame);
            }
        }
        return result;
    }

    /**
     * 保護対象の tick ごとに呼ばれる。HP が実際に減っていたら段階を進める。
     *
     * 「誰が呼んだか」ではなく「実際に減ったか」で判定するので、
     * reflection でのフィールド直書きのように絞り所を通らない経路も検出できる。
     */
    public static void onTick(LivingEntity entity) {
        // 何が「世界を tick させる道」かを覚えておく。自動対処がそこを潰すと世界ごと止まる。
        // 保護対象かどうかに関係なく採る (攻撃してくるのは保護対象以外)
        TickRoots.sample(entity);
        // 全エンティティが毎 tick 通るので、最初の判定はできるだけ安く済ませる
        if (!isProtected(entity)) {
            return;
        }
        UUID id = entity.getUUID();
        // 死亡演出の巻き戻しはクライアント側でも行う。deathTime を直接進めて
        // 「死んだふり」だけさせる手はクライアント側の値で完結するため
        // (シングルプレイでは静的な保護一覧が両側から見えるので、これで届く)
        HealthGuard.healDeathPose(entity);
        if (entity.level().isClientSide()) {
            // 嘘はクライアントの中だけでもつける。自機が「自分は死んでいる」と答えれば、
            // サーバが何と言おうと画面は死に、操作も止まる。材料探しは両側で回す
            StateProbe.enforce(entity);
            if (StateProbe.isLying(entity)) {
                StateProbe.probe(entity);
            }
            return;
        }
        // 関所そのものが生きている証。ここが止まったら剥がされた疑いになる
        lastTickHook = System.nanoTime();
        // tick が来ているうちに実体を控える。索引から外されて tick が止まってからでは、
        // 世界からこの実体を引く手段そのものが無くなっている
        PresenceGuard.anchor(entity);
        // HP が実際に載っている箱を控える。入口を通らずに箱を直接書きに来る相手には、
        // 箱の側に関所を置くしかなく、箱は自分の持ち主を知らない
        HealthGuard.trackHealthItem(entity);
        // 嘘の材料が特定できているなら、読む前に無害な値へ戻しておく。
        // 相手が毎 tick 掛け直してくるなら、こちらも毎 tick 戻すだけのこと
        StateProbe.enforce(entity);

        // 読み出しは信用しない。見張る側が偽装を信じたら、永久に減少を検出できない
        float now = HealthGuard.rawHealth(entity);
        HealthGuard.seedFloor(entity, now);
        checkReader(entity, now);
        if (now > EPSILON) {
            KNOWN_GOOD.put(id, now);
        }
        Float before = LAST_HEALTH.get(id);
        if (before == null || !enabled) {
            LAST_HEALTH.put(id, now);
            return;
        }

        // 減った分は、犯人が分かる前に取り戻しておく。犯人探しはそのまま続ける。
        // 倒れてからでは、そもそも観測を続けられない
        float lost = before - now;
        if (revert && now <= EPSILON) {
            // 0 以下まで落とされている。1 tick 前の値まで 0 以下なら、
            // 最後に見た正の値まで戻す。ここを埋めないと宙吊りが残る
            float target = before > EPSILON ? before : KNOWN_GOOD.getOrDefault(id, 0.0F);
            if (target > EPSILON) {
                float sunk = target - now;
                now = HealthGuard.revert(entity, target);
                noteReverted(entity, sunk);
            }
        } else if (lost > EPSILON && revert) {
            now = HealthGuard.revert(entity, before);
            noteReverted(entity, lost);
        } else if (lost <= EPSILON) {
            REVERT_TICKS.remove(id);
        }
        LAST_HEALTH.put(id, now);

        CHAIN_AGE.merge(id, 1, Integer::sum);
        GATE_AGE.merge(id, 1, Integer::sum);
        ORDINARY_AGE.merge(id, 1, Integer::sum);

        int cooldown = COOLDOWN.getOrDefault(id, 0);
        if (cooldown > 0) {
            COOLDOWN.put(id, cooldown - 1);
            return;
        }
        if (lost <= EPSILON) {
            return; // 減っていない = いまの手が効いている
        }
        escalate(entity, lost);
    }

    /**
     * 封印中に減ったなら、それは<b>関所を通っていない</b>ということ。
     *
     * <p>封印は保護対象の減 HP を無条件で拒否する。つまり封印中に値が減った時点で、
     * その書き込みは {@code SynchedEntityData#set} を通っていない — 通ったなら
     * 拒否されている。犯人探しをする前に、<b>どの層の話なのか</b>がここで決まる。
     *
     * <p>関所が最後に作動した時刻も添える。「一度も通っていない」なら関所を剥がされたか
     * 別経路、「たった今通ったのに減った」なら通したこちらの判断が間違っている。
     */
    private static String bypassNote() {
        if (!HealthGuard.isSealed()) {
            return "";
        }
        long since = DamageGuard.sinceFired(DamageGuard.Kind.HEALTH_WRITE);
        return " ※封印中に減っています = この書き込みは HP の関所を通っていません ("
                + (since < 0 ? "関所は一度も作動していません"
                        : "関所の最終作動は " + since + " ミリ秒前") + ")";
    }

    /**
     * 書き戻したことを記録する。
     *
     * 毎 tick 報告すると、書き戻しが効いている間じゅう画面が埋まる。効いているなら
     * 本人は困っていないので、始まりだけ伝えて、あとは status で数えられれば足りる。
     */
    private static void noteReverted(LivingEntity entity, float lost) {
        UUID id = entity.getUUID();
        REVERT_TOTAL.merge(id, lost, Float::sum);
        Integer ticks = REVERT_TICKS.put(id, REVERT_TICKS.getOrDefault(id, 0) + 1);
        if (ticks == null) {
            report(entity, String.format(
                    "HP が %.2f 減ったので書き戻しました。犯人が分かるまで倒れないようにします%s",
                    lost, bypassNote()), false);
        }
    }

    /**
     * HP の読み出しそのものが乗っ取られていないかを見る。
     *
     * <p>HP を減らす代わりに「HP を読む処理」を書き換えてしまえば、値は満タンのまま
     * 世界の側が勝手に死んだと判断する。この手は、値を見張る関所には原理的に映らない。
     * 書き込みも削除も死亡処理も一度も通らないまま死ぬのは、大抵これ。
     *
     * <p>見分け方は単純で、<b>同期データの値と {@code getHealth()} の答えを突き合わせる</b>。
     * 素のバニラなら必ず一致する。ずれているなら、ずらしている誰かが居る。
     *
     * <p>ずれを見つけたら、その場で正規化 ({@link ReaderGuard}) を入れる。嘘が見えて
     * いるのに手順だけ案内して立ち去ると、その間じゅう世界は騙され続ける。起動時の
     * 復元が静かに失敗していても、ここが生きている限り実戦の一発目で復旧できる。
     */
    private static void checkReader(LivingEntity entity, float stored) {
        // HP だけでなく生死の答えも見る。HP を合わせたまま「死んでいることにする」
        // 手があるので、HP の一致だけで足切りすると、その手を丸ごと取り逃がす
        if (!StateProbe.isLying(entity)) {
            return;
        }
        float visible = HealthGuard.visibleHealth(entity);
        String base = Math.abs(visible - stored) >= EPSILON
                ? String.format(
                        "HP の読み出しが同期データと食い違っています (世界からは %.2f / 実際は %.2f)。",
                        visible, stored)
                : "生死の答えが同期データと食い違っています (世界からは"
                        + (HealthGuard.rawAlive(entity) ? "死亡" : "生存")
                        + "扱い / 実際は" + (HealthGuard.rawAlive(entity) ? "生存" : "死亡") + ")。";

        // 先に状態を疑う。書き換えられた読み出しも、返す値をどこかの状態から作っている。
        // 材料を止めれば、本体を取り返さなくても嘘は消える。本体の取り合いと違って、
        // ここは先着順ではないので、続けている限り最後はこちらの値になる
        String source = StateProbe.probe(entity);
        if (source != null) {
            report(entity, base + "嘘の材料を実験で突き止めました: " + source
                    + "。以後は毎 tick 中和します", false);
            return;
        }
        if (!StateProbe.isExhausted()) {
            return; // 実験の途中。当たるまでは黙っている
        }

        // 材料が見つからない = 読み出しの本体そのものが嘘。
        //
        // ここで本体を取り返しに行くのは<b>やめた</b> (canon)。本体の取り合いは
        // 「最後に変換した者が勝つ」ゲームで、相手が掛け直せば終わらないし、
        // 勝った瞬間に他所の Mod の生死の仕組みごと奪ってしまう。
        // 呼び出し側で答えを直す形に作り直す。それまではここは黙って報告だけする。
        report(entity, base + "値ではなく読み出しの本体そのものが書き換えられています。"
                + "材料は見つかりませんでした (他の層で守ります)", true);
    }

    /**
     * 保護対象が世界の索引から外されかけたときに呼ばれる。
     *
     * 削除されていないのに索引から外れるのは、正規の経路には無い動き。
     * 疑わしいではなく、その時点で異常だと言い切ってよい。
     */
    public static void onUnregisterAttempt(Entity victim) {
        if (!enabled || !isProtected(victim)) {
            return;
        }
        UUID id = victim.getUUID();
        if (COOLDOWN.getOrDefault(id, 0) > 0) {
            return;
        }
        escalate(victim, "削除されていないのに世界の索引から外されかけました", false);
    }

    /**
     * 保護対象が消されかけたときに呼ばれる。
     *
     * 削除は HP を経由しないので tick ごとの HP 監視では永久に気づけない。
     * 検出した時点で連鎖も手元にあるので、その場で段を進める。
     */
    public static void onRemoveAttempt(Entity victim) {
        // 即時リスポーンは内部で削除を通る。自分の復帰処理を犯人扱いすると、
        // 「消されかけました」が復帰のたびに出続けて、本物の削除が埋もれる
        if (!enabled || !GuardContext.onServer(victim) || !GuardContext.guardsAgainst(victim)) {
            return;
        }
        // 既に死んでいる相手が消えるのは、殺されたのではなく後始末。
        // リスポーンはここを通るので、除かないと復帰のたびに犯人探しが走る。
        // 生死は素の値で見る。getHealth() を信じると「死んだことにされた」相手の
        // 削除まで後始末扱いになり、一番見たい削除だけを取り逃がす
        if (victim instanceof LivingEntity living && HealthGuard.rawHealth(living) <= 0.0F) {
            return;
        }
        UUID id = victim.getUUID();
        int cooldown = COOLDOWN.getOrDefault(id, 0);
        if (cooldown > 0) {
            return;
        }
        escalate(victim, cause(0.0F), false);
    }

    /**
     * 保護対象の死亡処理が呼ばれたときに呼ばれる。
     *
     * HP を削らずに直接呼ばれた死は、HP の監視にも削除の監視にも一切映らない。
     * ただし普通に死んだときもここを通るので、犯人が見当たらないときは黙る。
     * 毎回の死を報告すると、本当に死亡処理を直接叩いてきた一件が埋もれる。
     */
    public static void onDeathAttempt(Entity victim) {
        if (!enabled || !GuardContext.guardsAgainst(victim)) {
            return;
        }
        UUID id = victim.getUUID();
        if (COOLDOWN.getOrDefault(id, 0) > 0) {
            return;
        }
        // 普通に殴られて死にかけただけなら、犯人探しをしてはいけない。
        // 拒否と書き戻しで足りているのに相手のコードを潰しにいくと、
        // 普通の攻撃をしただけの相手 (自動クリッカー等) の仕組みを壊す。
        // 直近に素のダメージ経路を通っているかどうかで切り分ける
        if (ORDINARY_AGE.getOrDefault(id, Integer.MAX_VALUE) <= MAX_CHAIN_AGE_TICKS) {
            return;
        }
        escalate(victim, "死亡処理を直接呼ばれました", true);
    }

    private static void escalate(Entity victim, float lost) {
        escalate(victim, cause(lost), false);
    }

    /**
     * @param quietIfUnknown 犯人を特定できなかったときに黙るか。
     *                       普通の死や普通の削除でも通る経路では、これを立てる。
     */
    private static void escalate(Entity victim, String cause, boolean quietIfUnknown) {
        UUID id = victim.getUUID();
        List<List<String>> chains = CHAINS.getOrDefault(id, List.of());
        int depth = DEPTH.getOrDefault(id, 0);
        // 直近に普通のダメージ経路を通っている = 書き戻しと死亡の拒否の受け持ち。
        // ただし迂回してきた連鎖も同時に観測しているなら、そちらは追う
        boolean ordinary = ORDINARY_AGE.getOrDefault(id, Integer.MAX_VALUE) <= MAX_CHAIN_AGE_TICKS;
        boolean fresh = CHAIN_AGE.getOrDefault(id, Integer.MAX_VALUE) <= MAX_CHAIN_AGE_TICKS;
        if (ordinary && (chains.isEmpty() || !fresh)) {
            return;
        }

        if (chains.isEmpty()) {
            if (quietIfUnknown) {
                return;
            }
            boolean gateFired = GATE_AGE.getOrDefault(id, Integer.MAX_VALUE) <= MAX_CHAIN_AGE_TICKS;
            report(victim, cause + "が、" + (gateFired
                    // 関所は通った。スタックは本当にバニラだけ (Mixin 由来も含めて見ている)
                    ? "呼び出し元がバニラ / Forge だけでした。"
                            + "別スレッドか、イベント越しに間接的に呼ばれている可能性があります"
                    // 関所を 1 つも通らずに減っている。同期データへの書き込みですらない
                    : "HP の関所を一度も通っていません。"
                            + "フィールド直書きなど、関所より下の層の可能性があります"), true);
            COOLDOWN.put(id, COOLDOWN_TICKS);
            return;
        }
        if (!ignoreStaleChains && CHAIN_AGE.getOrDefault(id, Integer.MAX_VALUE) > MAX_CHAIN_AGE_TICKS) {
            if (quietIfUnknown) {
                return;
            }
            // 記録済みの連鎖は今回の減少とは無関係。古い相手のせいにしない
            report(victim, cause + "が、直近に観測した呼び出し元がありません。"
                    + "別の原因の可能性が高いので何もしません", true);
            COOLDOWN.put(id, COOLDOWN_TICKS);
            return;
        }

        int longest = chains.stream().mapToInt(List::size).max().orElse(0);
        int limit = Math.min(longest, maxDepth);
        if (depth >= limit) {
            return; // 打ち止め済み。報告は打ち止めた時に一度だけ出している
        }

        // 同じ深さの枝を横断して集める。枝分かれした攻撃経路を、共通の親まで
        // 遡らずに浅い段で並べて潰すため
        List<String> targets = new ArrayList<>();
        for (List<String> chain : chains) {
            // 記録した後で tick の道だと分かったものは、ここで外す
            if (depth < chain.size() && TickRoots.isRoot(chain.get(depth))) {
                continue;
            }
            if (depth < chain.size() && !targets.contains(chain.get(depth))) {
                targets.add(chain.get(depth));
            }
        }
        if (targets.isEmpty()) {
            DEPTH.put(id, depth + 1);
            return;
        }

        String candidate = String.join(", ", targets);
        Stage stage = STAGE.getOrDefault(id, Stage.NONE);

        switch (stage) {
            case NONE -> {
                // block (関所でのキャンセル) は関所を通る攻撃にしか効かないので、
                // 段を刻まず最初から disable で行く。disable できなかったものだけ
                // block で代用して、少なくとも関所越しの分は止める
                List<String> failures = new ArrayList<>();
                for (String target : targets) {
                    String failure = MethodDisabler.disable(target);
                    if (failure == null) {
                        AUTO_DISABLED.add(target);
                    } else {
                        DamageGuard.block(target);
                        AUTO_BLOCKED.add(target);
                        failures.add(target + " (" + failure + ")");
                    }
                }
                STAGE.put(id, Stage.DISABLED);
                report(victim, failures.isEmpty()
                        ? cause + "。disable しました: " + candidate
                        : cause + "。disable できなかったものは block で代用しました: "
                                + String.join(" / ", failures),
                        !failures.isEmpty());
            }
            case DISABLED -> {
                // この段は効かなかった。副作用を残さないよう、必ず戻してから外へ移る
                targets.forEach(AutoGuard::rollback);
                if (depth + 1 >= limit) {
                    DEPTH.put(id, limit);
                    STAGE.put(id, Stage.NONE);
                    report(victim, "自動で遡れる範囲 (" + limit + " 段) を使い切りました。"
                            + "これ以上外側は相手の挙動そのものに近く、行動不能にしかねないので自動では触りません。"
                            + " /" + Tpsthings.MODID + " damage log trace で手動で選んでください", true);
                } else {
                    DEPTH.put(id, depth + 1);
                    STAGE.put(id, Stage.NONE);
                    report(victim, candidate + " は効かなかったので戻しました。1 段外側を試します", false);
                }
            }
        }
        COOLDOWN.put(id, COOLDOWN_TICKS);
        // 自動で入れたものも次回に引き継ぐ。手で入れ直させるのは筋が悪い
        GuardConfig.save();
    }

    /** 自動で適用した措置を 1 つ取り消す。手で入れたものには触らない。 */
    private static void rollback(String signature) {
        if (AUTO_DISABLED.remove(signature)) {
            MethodDisabler.restore(signature);
        }
        if (AUTO_BLOCKED.remove(signature)) {
            DamageGuard.unblock(signature);
        }
    }

    /**
     * 自動で適用した措置をすべて取り消し、進捗も白紙に戻す。
     *
     * 相手が行動不能になったなど、やりすぎた時の戻し口。
     *
     * @return 取り消した件数
     */
    public static int resetAll() {
        int reverted = 0;
        for (String signature : Set.copyOf(AUTO_DISABLED)) {
            MethodDisabler.restore(signature);
            AUTO_DISABLED.remove(signature);
            reverted++;
        }
        for (String signature : Set.copyOf(AUTO_BLOCKED)) {
            DamageGuard.unblock(signature);
            AUTO_BLOCKED.remove(signature);
            reverted++;
        }
        DEPTH.clear();
        STAGE.clear();
        COOLDOWN.clear();
        // 実験で当てた中和も自動でやったことのうち。巻き戻すなら一緒に外す
        StateProbe.reset();
        GuardConfig.save();
        return reverted;
    }

    /** 自動で入れた block の署名。保存して次回に引き継ぐために要る。 */
    public static Set<String> autoBlockedSignatures() {
        return Set.copyOf(AUTO_BLOCKED);
    }

    /** 自動で入れた disable の署名。 */
    public static Set<String> autoDisabledSignatures() {
        return Set.copyOf(AUTO_DISABLED);
    }

    /**
     * 前回自動で入れた block を「自動で入れたもの」として覚え直す。
     *
     * 実際の適用は呼び出し側が済ませている前提。ここで印だけ戻しておかないと、
     * 再起動後の {@code reset} が前回の自動措置を取り消せなくなる。
     * disable は次の起動に持ち越さない方針なので、覚え直すのは block だけ。
     */
    public static void markAutoBlocked(Set<String> blocked) {
        AUTO_BLOCKED.addAll(blocked);
    }

    /** 自動で適用中の措置。 */
    public static List<String> autoApplied() {
        List<String> lines = new ArrayList<>();
        AUTO_DISABLED.forEach(signature -> lines.add("disable " + signature));
        AUTO_BLOCKED.forEach(signature -> lines.add("block   " + signature));
        return lines;
    }

    /** 何が起きて段を進めたのか。HP 減少と削除では原因の書き方が違う。 */
    private static String cause(float lost) {
        return lost > 0.0F
                ? "HP が " + String.format("%.2f", lost) + " 減りました"
                : "消されかけました";
    }

    private static void report(Entity victim, String message, boolean warning) {
        GuardNotice.send(victim, message, warning);
    }
}
