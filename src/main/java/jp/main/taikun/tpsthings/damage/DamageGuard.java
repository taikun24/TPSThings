package jp.main.taikun.tpsthings.damage;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 減 HP を引き起こした「呼び出し元メソッド」を署名単位で特定し、任意の署名を無効化する。
 *
 * ここが持つのは「どのメソッドが HP を減らしたか」という一般的な情報だけで、
 * 何を止めるかは実行時にコマンドから与える。特定の Mod を名指しする分岐は持たない。
 */
public final class DamageGuard {

    /** どの絞り所で捕まえたか。 */
    public enum Kind {
        HURT,
        /** setter を経由するかに関わらず、HP そのものが書き換わる瞬間。 */
        HEALTH_WRITE,
        /**
         * HP を経由せず、死亡処理そのものを呼びに来る経路。
         *
         * HP を 1 も削らずに殺せるので、HP を見張る関所には最後まで映らない。
         */
        DIE,
        /** HP を経由せず、エンティティごと消しにくる経路。 */
        REMOVE,
        /**
         * 世界の索引からエンティティを外す経路。
         *
         * 削除処理を通らずにここだけ叩かれると、生きているまま世界から居なくなる。
         * 一番外側の層で、ここを抜けられると打つ手が無い。
         */
        UNREGISTER,
        /** 位置を直接書き換えてくる経路。 */
        MOVE,
        /** 速度を直接書き換えてくる経路。 */
        VELOCITY
    }

    /** そのダメージ種別が、自動エスカレーションの判断材料になるか。 */
    private static boolean feedsEscalation(Kind kind) {
        // 移動は平常時も絶えず起きるので、HP 減少や削除と同じ扱いにはできない。
        // 混ぜると自動対処が移動系メソッドを潰しにいってしまう。
        return kind != Kind.MOVE && kind != Kind.VELOCITY;
    }

    /** スタックを遡る深さ。深すぎると重く、浅すぎると本当の呼び出し元を取り逃がす。 */
    private static final int MAX_DEPTH = 32;

    private static final String OWN_PACKAGE = "jp.main.taikun.tpsthings.";
    /** 末尾のドットが重要。これが無いと net.minecraftforge. まで巻き込む。 */
    private static final String VANILLA_PACKAGE = "net.minecraft.";
    /** 注入したハンドラは対象クラスのメソッドとして現れるので、名前で弾く。 */
    private static final String HANDLER_PREFIX = "tpsthings$";

    private static final StackWalker WALKER = StackWalker.getInstance();
    /**
     * 呼び出し元のクラスそのものを掴める方 (遅い)。
     *
     * フレームがバニラのクラス名を名乗っていても、中身が Mixin で持ち込まれたものなら
     * それは他所の Mod のコードでしかない。それを見分けるにはクラスが要る。
     */
    private static final StackWalker WALKER_CLASSES =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * 署名 → その中身を持ち込んだ Mixin のクラス名。空文字は「素のバニラ」。
     *
     * 一度調べたら変わらないので覚えておく。反射は毎回やるには高い。
     */
    private static final Map<String, String> OWNERS = new ConcurrentHashMap<>();
    private static final String NO_OWNER = "";

    /** 絞り所ごとの作動回数。関所が生きているかを実測で確かめる唯一の手段。 */
    private static final Map<Kind, java.util.concurrent.atomic.LongAdder> FIRED =
            new ConcurrentHashMap<>();
    /** 絞り所ごとの、最後に作動した時刻。 */
    private static final Map<Kind, Long> FIRED_AT = new ConcurrentHashMap<>();

    private static final Set<String> BLOCKED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Sighting> SIGHTINGS = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * この Mod 自身が起こした削除・HP 操作をやっている最中のスレッド。
     *
     * 即時リスポーンは内部で {@code Entity#remove} を呼ぶ。それを絞り所が拾うと
     * 「消されかけた」と誤認して自動対処が走り、しかもその remove を打ち消してしまう。
     * 打ち消された結果が「Force-added player with duplicate UUID」になる。
     */
    private static volatile Thread selfActor = null;

    private static volatile boolean watching = false;
    private static volatile boolean motionGuard = false;

    private DamageGuard() {
    }

    /** 観測された呼び出し元 1 件。 */
    public static final class Sighting {
        public final String signature;
        public final int seq;
        public volatile Kind lastKind;
        public volatile String lastVictim = "?";
        public volatile float lastAmount;
        public volatile long count;
        /** 最後に観測したときの呼び出し連鎖。梯子の途中の段を選ぶのに要る。 */
        public volatile List<String> lastFrames = List.of();

        private Sighting(String signature, int seq) {
            this.signature = signature;
            this.seq = seq;
        }
    }

    // ---- 絞り所から呼ばれる入口 -------------------------------------------------

    /**
     * この減 HP を止めるべきか。
     *
     * 監視オフかつブロックリストが空なら、スタックを一切歩かずに false を返す。
     * 平常時のコストをゼロにするための早期脱出。
     */
    public static boolean shouldBlock(Entity victim, Kind kind, float amount) {
        boolean guarded = AutoGuard.isProtected(victim);

        // 位置・速度は全エンティティが毎 tick 何度も通る。保護対象でなければ
        // スタックに一切触らずに抜ける。ここを通すと近くにエンティティが居るだけで重くなる。
        if (!feedsEscalation(kind) && !guarded) {
            return false;
        }

        // 通ったこと自体を数える。「何も出ない」ときに、関所が死んでいるのか
        // 誰も通っていないのかを切り分けられるのはこの数字だけ
        FIRED.computeIfAbsent(kind, key -> new java.util.concurrent.atomic.LongAdder()).increment();
        FIRED_AT.put(kind, System.currentTimeMillis());

        // 自分で起こしたものは自分の仕業として数えない。数えると、対処が対処を呼ぶ
        if (Thread.currentThread() == selfActor) {
            return false;
        }

        // 連鎖が要るのは記録するときだけ。可否だけ知りたいなら、当たった時点で
        // 打ち切って文字列の並びを組み立てない
        if (!watching && !guarded) {
            return !BLOCKED.isEmpty() && anyBlockedFrame();
        }

        List<String> frames = captureFrames();
        if (frames.isEmpty()) {
            return false;
        }

        boolean blocked = false;
        for (String frame : frames) {
            if (BLOCKED.contains(frame)) {
                blocked = true;
                break;
            }
        }

        if (guarded && feedsEscalation(kind)) {
            AutoGuard.observe(victim, kind, frames);
        }
        if (watching) {
            noteSighting(responsibleFrame(frames), frames, kind, victim, amount);
        }
        return blocked;
    }

    // ---- スタック解析 -----------------------------------------------------------

    /**
     * ブロック対象がスタックに居るかだけを見る。
     *
     * 最初に当たった時点で打ち切り、連鎖の並びを作らない。
     * 記録が要らない経路はこちらを通すことで、走査 1 回あたりの費用をかなり下げられる。
     */
    private static boolean anyBlockedFrame() {
        return WALKER.walk(stream -> stream
                .limit(MAX_DEPTH)
                .anyMatch(frame -> BLOCKED.contains(
                        frame.getClassName() + "#" + frame.getMethodName())));
    }

    /** 自分自身のフレームを除いた呼び出し元署名の並び。上が近い側。 */
    private static List<String> captureFrames() {
        return WALKER_CLASSES.walk(stream -> stream
                .limit(MAX_DEPTH)
                .filter(frame -> !frame.getClassName().startsWith(OWN_PACKAGE))
                // Mixin は注入したメソッドを handler$xxx$<元の名前> に改名する。
                // 頭だけを見ていたせいで、こちらの関所が「バニラのメソッド」の顔で
                // 容疑者に並び、自動対処が自分の関所を潰していた
                .filter(frame -> !frame.getMethodName().contains(HANDLER_PREFIX))
                .map(frame -> {
                    String signature = frame.getClassName() + "#" + frame.getMethodName();
                    // バニラを名乗るフレームだけ、中身の出所を調べる価値がある
                    if (frame.getClassName().startsWith(VANILLA_PACKAGE)) {
                        OWNERS.computeIfAbsent(signature,
                                key -> lookupOwner(frame.getDeclaringClass(), frame.getMethodName()));
                    }
                    return signature;
                })
                .filter(signature -> !isOwn(signature))
                .collect(Collectors.toList()));
    }

    /**
     * こちらが持ち込んだコードか。
     *
     * <p>名前だけでは足りない。Mixin で注入したメソッドは<b>対象クラスの一員</b>として、
     * バニラのクラス名と改名済みの名前 ({@code handler$…$tpsthings$…}) で現れる。
     * 名前と出所の両方で見る。
     */
    public static boolean isOwn(String signature) {
        if (signature.startsWith(OWN_PACKAGE) || signature.contains(HANDLER_PREFIX)) {
            return true;
        }
        String owner = OWNERS.get(signature);
        return owner != null && owner.startsWith(OWN_PACKAGE);
    }

    /**
     * そのメソッドが Mixin で持ち込まれたものなら、持ち込んだ側のクラス名。素なら null。
     *
     * <p>これが無いと、他の Mod が Mixin でバニラに注入したコードを「バニラの仕業」として
     * 除外してしまう。注入されたメソッドはバニラのクラスの一員として現れるので、
     * クラス名だけを見ている限り永久に他所の Mod だと分からない。
     *
     * <p>Mixin は合成したメソッドに出所の注釈を残す。それを読むだけなので、
     * 特定の Mod の知識は要らないし、Mixin を使う相手なら誰でも同じように名前が出る。
     */
    public static String mixinOwner(String signature) {
        String owner = OWNERS.get(signature);
        return owner == null || owner.isEmpty() ? null : owner;
    }

    private static String lookupOwner(Class<?> declaring, String method) {
        try {
            for (java.lang.reflect.Method candidate : declaring.getDeclaredMethods()) {
                if (!candidate.getName().equals(method)) {
                    continue;
                }
                for (java.lang.annotation.Annotation note : candidate.getDeclaredAnnotations()) {
                    // 注釈の型そのものには依存しない。Mixin の版が変わっても壊れないようにする
                    if (!note.annotationType().getName().endsWith(".MixinMerged")) {
                        continue;
                    }
                    Object owner = note.annotationType().getMethod("mixin").invoke(note);
                    return owner instanceof String name ? name : NO_OWNER;
                }
            }
        } catch (Throwable unavailable) {
            // 読めないなら素として扱う。ここで落ちると絞り所ごと道連れになる
        }
        return NO_OWNER;
    }

    /** 見た目はバニラでも、中身が他所から持ち込まれたものか。 */
    static boolean isForeign(String signature) {
        if (!signature.startsWith(VANILLA_PACKAGE) && !signature.startsWith("net.minecraftforge.")) {
            return true;
        }
        String owner = mixinOwner(signature);
        return owner != null && !owner.startsWith(OWN_PACKAGE);
    }

    /**
     * この書き込みを<b>直接</b>起こしたのが他所の Mod か。
     *
     * <p>「本人の通常移動は通し、外から書かれる速度だけ拒否する」ための判定。
     * 決めるのは<b>書き込みに一番近い、責任を負うフレーム 1 つ</b>:
     *
     * <ul>
     *   <li>素のバニラ (travel / aiStep など) に行き着いたら → 通常物理。通す。
     *       それより外側に他 Mod が挟まっていても (tick への @Redirect など)、
     *       物理の連鎖を経由している時点で直接の書き込みではない</li>
     *   <li>他所のクラス、またはバニラの名前を着た他所の Mixin ハンドラに
     *       行き着いたら → 直接書きに来ている。拒否</li>
     * </ul>
     *
     * <p>「連鎖のどこかに他所が居るか」で見ると、移動経路に @Redirect を挟むだけの
     * Mod が居る環境で<b>本人の通常移動まで全部拒否</b>してしまう。責任は連鎖の
     * 総和ではなく、一番近い書き手にだけ問う。
     *
     * <p>walk は安くないので、呼ぶ側で対象を絞ってから使うこと (着用者 1 人に限る、など)。
     */
    public static boolean foreignWriterOnStack() {
        if (isSelfAction()) {
            return false;
        }
        return Boolean.TRUE.equals(WALKER_CLASSES.walk(stream -> stream
                .limit(MAX_DEPTH)
                .map(frame -> {
                    String className = frame.getClassName();
                    // 自分の関所・自分のコードはまだ判断しない (次のフレームへ)
                    if (className.startsWith(OWN_PACKAGE)
                            || frame.getMethodName().contains(HANDLER_PREFIX)) {
                        return null;
                    }
                    if (className.startsWith(VANILLA_PACKAGE)) {
                        // 同じ値へ委譲するだけのオーバーロード (setDeltaMovement(DDD) →
                        // (Vec3) など) は書き手ではない。次のフレームに責任を問う
                        if (frame.getMethodName().contains("DeltaMovement")) {
                            return null;
                        }
                        String signature = className + "#" + frame.getMethodName();
                        OWNERS.computeIfAbsent(signature,
                                key -> lookupOwner(frame.getDeclaringClass(), frame.getMethodName()));
                        String owner = mixinOwner(signature);
                        // 素のバニラに行き着いた = 通常物理。ここで決めて打ち切る
                        return owner == null || owner.startsWith(OWN_PACKAGE)
                                ? Boolean.FALSE : Boolean.TRUE;
                    }
                    // イベントバス等の基盤は素通し。その上に乗っている書き手を見る
                    if (GuardContext.isInfrastructure(className)) {
                        return null;
                    }
                    return Boolean.TRUE; // 他所の Mod が直接書きに来ている
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(Boolean.FALSE)));
    }

    /**
     * 責任を負っていそうなフレーム。
     *
     * バニラでない最初のフレームを採る。Mixin で持ち込まれたものは、クラス名が
     * バニラでも「バニラでない」側に数える。全部バニラなら先頭を採る。
     */
    private static String responsibleFrame(List<String> frames) {
        for (String frame : frames) {
            if (isForeign(frame)) {
                return frame;
            }
        }
        return frames.get(0);
    }

    /** 表示用。出所が分かっているなら添える。 */
    public static String describe(String signature) {
        String owner = mixinOwner(signature);
        return owner == null ? signature : signature + "  ← " + owner;
    }

    /**
     * その絞り所が最後に作動してからの経過ミリ秒。一度も通っていなければ -1。
     *
     * <p>「守れなかった」ときに一番知りたいのは<b>関所が通ったのか、通ったのに
     * 素通ししたのか</b>。この 2 つは対処がまったく違うのに、結果だけ見ると同じに見える。
     */
    public static long sinceFired(Kind kind) {
        Long at = FIRED_AT.get(kind);
        return at == null ? -1L : System.currentTimeMillis() - at;
    }

    /** 絞り所ごとの作動回数。関所が生きているかを確かめるために出す。 */
    public static List<String> gateReport() {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Kind kind : Kind.values()) {
            var counter = FIRED.get(kind);
            long count = counter == null ? 0L : counter.sum();
            Long at = FIRED_AT.get(kind);
            lines.add(String.format("%-13s %8d 回  %s", kind, count,
                    at == null ? "(一度も通っていません)" : ((now - at) / 1000) + " 秒前"));
        }
        return lines;
    }

    private static void noteSighting(String signature, List<String> frames, Kind kind,
                                     Entity victim, float amount) {
        Sighting sighting = SIGHTINGS.computeIfAbsent(signature,
                key -> new Sighting(key, SEQ.getAndIncrement()));
        sighting.lastKind = kind;
        sighting.lastVictim = victim == null ? "?" : victim.getName().getString();
        sighting.lastAmount = amount;
        sighting.lastFrames = List.copyOf(frames);
        sighting.count++;
    }

    /** これまでに見たすべての署名。補完候補に使う。 */
    public static List<String> knownSignatures() {
        return SIGHTINGS.values().stream()
                .flatMap(sighting -> sighting.lastFrames.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    // ---- コマンドから触る面 -----------------------------------------------------

    /**
     * この Mod 自身の操作として実行する。
     *
     * この中で起きた削除や HP 変更は、絞り所も自動対処も素通しになる。
     */
    public static void runAsSelf(Runnable action) {
        Thread previous = selfActor;
        selfActor = Thread.currentThread();
        try {
            action.run();
        } finally {
            selfActor = previous;
        }
    }

    /** いま走っているのがこの Mod 自身の操作か。関所を素通しさせる判断に使う。 */
    public static boolean isSelfAction() {
        return Thread.currentThread() == selfActor;
    }

    public static boolean isWatching() {
        return watching;
    }

    public static void setWatching(boolean enabled) {
        watching = enabled;
    }

    /**
     * 位置・速度の絞り所を通すか。
     *
     * これらは平常の移動でも毎 tick 通る一番熱い経路なので、既定では丸ごと素通しにする。
     * 強制移動を追いたいときだけ入れる。
     */
    public static boolean isMotionGuard() {
        return motionGuard;
    }

    public static void setMotionGuard(boolean enabled) {
        motionGuard = enabled;
    }

    /** 観測順に並べた一覧。表示上の添字はこの並びの位置。 */
    public static List<Sighting> sightings() {
        List<Sighting> list = new ArrayList<>(SIGHTINGS.values());
        list.sort(Comparator.comparingInt(s -> s.seq));
        return list;
    }

    /** 添字または署名そのものを署名に解決する。解決できなければ null。 */
    public static String resolve(String indexOrSignature) {
        try {
            int index = Integer.parseInt(indexOrSignature.trim());
            List<Sighting> list = sightings();
            if (index < 0 || index >= list.size()) {
                return null;
            }
            return list.get(index).signature;
        } catch (NumberFormatException notAnIndex) {
            return indexOrSignature.contains("#") ? indexOrSignature.trim() : null;
        }
    }

    public static boolean block(String signature) {
        if (isOwn(signature)) {
            // 自分の関所を容疑者にすると、守るための仕組みを守るために潰すことになる
            GuardNotice.warn("自分の関所は block できません: " + signature);
            return false;
        }
        return BLOCKED.add(signature);
    }

    public static boolean unblock(String signature) {
        return BLOCKED.remove(signature);
    }

    public static Set<String> blocked() {
        return Set.copyOf(BLOCKED);
    }

    public static boolean isBlocked(String signature) {
        return BLOCKED.contains(signature);
    }

    public static void clearSightings() {
        SIGHTINGS.clear();
        SEQ.set(0);
    }
}
