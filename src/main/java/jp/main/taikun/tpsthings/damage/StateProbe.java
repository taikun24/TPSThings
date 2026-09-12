package jp.main.taikun.tpsthings.damage;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 生死の読み出しが嘘をついているとき、その嘘の出所を実験で突き止めて中和する。
 *
 * <p>読み出しの本体を書き換えられた相手には、{@link ReaderGuard} で本体を取り返す手がある。
 * だがあれは<b>変換器の並び順の取り合い</b>で、相手が掛け直せば決着がつかない。取り合いに
 * 勝てないなら、勝たなくても済む場所で戦う。
 *
 * <p>書き換えられた読み出しも、返す値をどこかから作っている。無から嘘は出てこない。
 * 差分にせよ影のフラグにせよ、<b>嘘の材料は必ず状態としてどこかに載っている</b>。
 * 材料を 0 に戻せば、本体が誰のものであろうと答えは真実に戻る。
 *
 * <p>誰が何を使っているかは知らないので、<b>実験で当てる</b>:
 *
 * <pre>
 *   1. 素性の知れない状態を 1 つ、無害な値 (0 / false) に置く
 *   2. 読み出しの嘘が消えたか見る
 *   3. 消えたらそれが出所。消えなければ元に戻して次へ
 * </pre>
 *
 * <p>当たった 1 件だけを以後ずっと中和する。外れたものは必ず戻すので、
 * 関係ない Mod の状態には結果として何も残らない。相手が誰かを一度も名指ししない。
 */
public final class StateProbe {

    private static final float EPSILON = GuardContext.EPSILON;
    /**
     * 実験の間隔。
     *
     * 嘘の検出は毎 tick 走る。実験はフィールドを舐めるので安くないし、
     * 相手が毎 tick 掛け直してくるなら、こちらも急ぐ理由がない。
     */
    private static final long INTERVAL_NANOS = 2_000_000_000L;

    /** 一度の実験で試す状態の上限。反射で無限に潜らないための足枷。 */
    private static final int MAX_TRIALS = 256;
    /** 押さえ続ける出所の上限。他所の状態を際限なく握らないための足枷。 */
    private static final int MAX_CULPRITS = 4;

    /**
     * 側 (サーバ / クライアント) ごとの実験の進み具合。
     *
     * <p>嘘はサーバとクライアントの<b>両方で別々に</b>つかれる。同じ手口でも材料は別の実体、
     * 別の静的な入れ物に載っているので、当たりも別々に覚える。押さえ続ける相手を取り違えると、
     * 触ってはいけない側のスレッドから触ることにもなる。
     */
    private static final class Side {
        private volatile long lastProbe = 0L;
        /** 実験を最後まで走らせて出所が見つからなかったか。 */
        private volatile boolean exhausted = false;
        /** 突き止めた出所。以後は毎 tick ここを押さえ続ける。 */
        private final List<Culprit> culprits = new CopyOnWriteArrayList<>();
        /** 実体の外を舐めるときの、次に見るクラスの位置。 */
        private volatile int externalCursor = 0;
        /** 前回の当たり以降に見たクラス数。一周したかを知るために数える。 */
        private volatile int scanned = 0;
        /** 実体の外を一周し終えたか。一周する前に「材料なし」と結論を出さないため。 */
        private volatile boolean lapComplete = false;
    }

    /** 1 回の実験で見るクラス数。全部を 1 回で舐めると tick が止まる。 */
    private static final int SCAN_BUDGET = 4000;

    private static final Side SERVER = new Side();
    private static final Side CLIENT = new Side();

    private static Side side(LivingEntity entity) {
        return entity.level() != null && entity.level().isClientSide() ? CLIENT : SERVER;
    }

    /** クラスごとの、素性の知れた同期データの番号。 */
    private static final Map<Class<?>, Set<Integer>> KNOWN_IDS = new ConcurrentHashMap<>();

    private static volatile Field itemsField;

    private StateProbe() {
    }

    // ---- 判定 -------------------------------------------------------------------

    /**
     * この相手について、生死の読み出しが嘘をついているか。
     *
     * <p>HP だけでなく生死判定も見る。差分を載せる手は HP のずれとして出るが、
     * 「死んだことにする」影のフラグは HP を 0 に見せる形でも生死だけを裏返す形でも
     * 効くので、片方だけ見ていると取り逃がす。
     */
    public static boolean isLying(LivingEntity entity) {
        return lying(entity);
    }

    private static boolean lying(LivingEntity entity) {
        float raw = HealthGuard.rawHealth(entity);
        if (Math.abs(HealthGuard.visibleHealth(entity) - raw) >= EPSILON) {
            return true;
        }
        // 自分の偽装 (封印中の床) を外して聞く。外さないと、自分の嘘を
        // 他人の嘘として数え、居もしない相手の材料を永久に探し続けることになる
        return Boolean.TRUE.equals(HealthGuard.withRaw(() -> {
            try {
                return entity.isAlive() != HealthGuard.rawAlive(entity)
                        || entity.isDeadOrDying() != (raw <= 0.0F);
            } catch (Throwable t) {
                // 読み出しが落ちるなら、それは直っていない。嘘が消えたと数えてはいけない
                return true;
            }
        }));
    }

    // ---- 実験 -------------------------------------------------------------------

    /**
     * 嘘の出所を 1 件だけ突き止める。
     *
     * @return 見つけた出所の説明。見つからなかった / 間隔待ちなら null
     */
    static String probe(LivingEntity entity) {
        Side side = side(entity);
        long now = System.nanoTime();
        if (side.lastProbe != 0L && now - side.lastProbe < INTERVAL_NANOS) {
            return null;
        }
        side.lastProbe = now;
        if (!lying(entity)) {
            return null;
        }
        Culprit hit = probeSynched(entity);
        if (hit == null) {
            hit = probeFields(entity);
        }
        if (hit == null) {
            // 実体の外に材料を置く手がある。UUID を静的な名簿に載せるだけで、
            // 実体には何も書かない型がそれで、実体だけを探している限り永久に当たらない
            hit = probeExternal(entity);
        }
        if (hit == null) {
            // 実体の外は数回に分けて舐める。一周する前に「材料は無い」と結論を出さない
            side.exhausted = side.lapComplete;
            return null;
        }
        if (side.culprits.size() >= MAX_CULPRITS) {
            // これだけ押さえてもまだ嘘が出るなら、当てているのは材料ではない。
            // 際限なく他所の状態を握り続ける方が害が大きい
            side.exhausted = true;
            return null;
        }
        side.exhausted = false;
        side.lapComplete = false;
        side.scanned = 0;
        side.culprits.add(hit);
        GuardNotice.info("読み出しの嘘の出所を特定: " + hit.label());
        return hit.label();
    }

    /** 実験を走らせた上で出所が見つからなかったか。別の層へ移る合図。 */
    public static boolean isExhausted() {
        return SERVER.exhausted;
    }

    /**
     * 同期データを 1 つずつ無害な値に置いて試す。
     *
     * <p>2 周する。1 周目は<b>系譜が誰も名乗り出ていない番号</b>だけ — バニラの HP も、
     * その Mob 自身の Mod が持たせた値も、必ずクラスの系譜のどこかに静的な鍵として
     * 置かれているので、そこに無い番号は外から後付けされたものだと言い切れる。
     *
     * <p>2 周目は<b>真偽値なら素性を問わず全部</b>。<b>「系譜に居る = バニラのもの」は
     * Mixin 環境では成り立たない</b> — 注入された鍵は対象クラスの静的フィールドとして
     * 混ぜ込まれるので、系譜に堂々と並ぶ (名前に {@code $} すら入らない)。
     * メソッドがバニラの名前を着るのと同じことが、鍵にも起きている。
     *
     * <p>素性で切れないなら、実験そのものを証拠にするしかない。真偽値なら裏返して戻すだけで
     * 費用も副作用も小さく、そもそも<b>読み出しの嘘の材料はたいてい真偽値 1 個</b>なので、
     * 総当たりにしても実際に試す数はごくわずかで済む。
     */
    private static Culprit probeSynched(LivingEntity entity) {
        SynchedEntityData data = entity.getEntityData();
        Map<?, ?> items = itemsOf(data);
        if (items == null) {
            return null;
        }
        Set<Integer> known = KNOWN_IDS.computeIfAbsent(entity.getClass(), StateProbe::collectKnownIds);
        Culprit hit = trySynched(entity, items, known, false);
        return hit != null ? hit : trySynched(entity, items, known, true);
    }

    /**
     * @param boolPass 2 周目か。素性の知れた番号も、真偽値なら試す
     */
    private static Culprit trySynched(LivingEntity entity, Map<?, ?> items,
                                      Set<Integer> known, boolean boolPass) {
        int trials = 0;
        for (Object raw : new ArrayList<>(items.values())) {
            if (!(raw instanceof SynchedEntityData.DataItem<?> item) || ++trials > MAX_TRIALS) {
                continue;
            }
            EntityDataAccessor<?> key = item.getAccessor();
            Object value = item.getValue();
            if (boolPass) {
                // 1 周目で試していない = 素性が知れている番号のうち、真偽値だけ
                if (!(value instanceof Boolean) || !known.contains(key.getId())) {
                    continue;
                }
            } else if (known.contains(key.getId())) {
                continue;
            }
            Object neutral = neutral(value);
            if (neutral == null || neutral.equals(value)) {
                continue;
            }
            write(entity, key, neutral);
            boolean cured = !lying(entity);
            write(entity, key, value); // 当たりでも一度は必ず戻す。裏を取るため
            if (cured && lying(entity)) {
                // 置いたら消え、戻したら復活した。因果がある
                write(entity, key, neutral);
                return new SyncCulprit(key.getId(), neutral,
                        "同期データ #" + key.getId() + " (" + value + " → " + neutral + ")");
            }
        }
        return null;
    }

    /**
     * 後付けされたフィールドを 1 つずつ無害な値に置いて試す。
     *
     * <p>同期データに乗らない嘘もある。実体に生やしたフラグや、実体がぶら下げている
     * 小箱の中の値がそれで、こちらはネットワークに出ないぶん見つけにくい。
     *
     * <p>後付けかどうかは名前で見分ける。注入で生えたフィールドは名前に {@code $} を
     * 持つので、バニラのクラスに居てもバニラのものではないと分かる。小箱の側は、
     * 型がバニラでも JDK でもない時点で外から持ち込まれたもの。
     */
    private static Culprit probeFields(LivingEntity entity) {
        int trials = 0;
        for (Class<?> type = entity.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || ++trials > MAX_TRIALS) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType == boolean.class && field.getName().indexOf('$') >= 0) {
                    Culprit hit = tryField(entity, null, field, Boolean.FALSE,
                            type.getSimpleName() + "#" + field.getName());
                    if (hit != null) {
                        return hit;
                    }
                } else if (!fieldType.isPrimitive() && foreign(fieldType)) {
                    Culprit hit = probeNested(entity, field);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
        }
        return probeFlags(entity);
    }

    /**
     * 実体が持つ真偽値を、素性を問わず全部試す。
     *
     * <p>1 周目は「注入で生えた印 ({@code $} を含む名前)」だけを見ていた。だが混ぜ込む側は
     * 名前を選べるし、<b>バニラが元から持っている旗</b> (死亡済みの印など) を立てるだけで
     * 済ませる手もある。名前で素性を切ろうとする限り、そこは永久に死角になる。
     *
     * <p>真偽値は裏返して戻すだけなので安く可逆で、そもそも読み出しの嘘の材料は
     * たいてい真偽値 1 個。素性で切らずに<b>実験そのものを証拠にする</b>。
     */
    private static Culprit probeFlags(LivingEntity entity) {
        int trials = 0;
        for (Class<?> type = entity.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != boolean.class
                        || field.getName().indexOf('$') >= 0 || ++trials > MAX_TRIALS) {
                    continue; // $ 付きは 1 周目で試し済み
                }
                Culprit hit = tryField(entity, null, field, Boolean.FALSE,
                        type.getSimpleName() + "#" + field.getName());
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * 実体の外 — 静的な名簿に載せられていないかを試す。
     *
     * <p>実体には何も書かず、<b>UUID を静的な集合に足すだけ</b>で殺す手がある。
     * 書き換えられた読み出しがその集合を見に行くので、実体をいくら調べても材料は出てこない。
     * 名簿はエンティティごとではなく Mod ごとに 1 つなので、こちらも実体の外を探すしかない。
     *
     * <p>探し方は素性ではなく<b>中身</b>で決める: 基盤でも自分のものでもないクラスの静的な
     * 入れ物のうち、<b>この相手を名指ししているもの</b> (UUID・実体そのもの・エンティティ番号の
     * どれかを含むもの) だけが候補。名指ししていない入れ物には触らない。
     *
     * <p>実験は他と同じ — 外して、嘘が消えるか見て、戻して、復活するか見る。
     * 外れなら必ず元通りにするので、関係ない Mod には何も残らない。
     */
    private static Culprit probeExternal(LivingEntity entity) {
        if (!MethodDisabler.isReady()) {
            return null; // agent がまだ無い。実体の外は見られない
        }
        Class<?>[] loaded = MethodDisabler.loadedClasses();
        Object[] marks = {entity.getUUID(), entity, entity.getId()};
        Side side = side(entity);
        int trials = 0;
        // 読み込み済みクラスは数千ある。1 回で舐め切ろうとすると tick を止めるので、
        // 前回の続きから決まった数だけ見る。数回の実験で一周する
        int start = loaded.length == 0 ? 0 : Math.floorMod(side.externalCursor, loaded.length);
        int steps = Math.min(loaded.length, SCAN_BUDGET);
        side.scanned += steps;
        if (side.scanned >= loaded.length) {
            side.lapComplete = true;
            side.scanned = 0;
        }
        for (int step = 0; step < steps; step++) {
            int index = (start + step) % loaded.length;
            side.externalCursor = index + 1;
            Class<?> owner = loaded[index];
            if (owner == null || GuardContext.isInfrastructure(owner.getName())) {
                continue;
            }
            for (Field field : declaredFields(owner)) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!Collection.class.isAssignableFrom(field.getType())
                        && !Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object listed = read(field, null);
                Object mark = naming(listed, marks);
                // 名指ししている入れ物だけを実際に触る。見るだけの分は数えない
                // (数えると、大きな Mod 構成では相手の名簿に辿り着く前に打ち止めになる)
                if (mark == null || ++trials > MAX_TRIALS) {
                    continue;
                }
                Culprit hit = tryExternal(entity, field, listed, mark,
                        owner.getSimpleName() + "#" + field.getName());
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** この入れ物が相手を名指ししているか。しているなら、その名指しに使われている印。 */
    private static Object naming(Object listed, Object[] marks) {
        try {
            for (Object mark : marks) {
                if (listed instanceof Collection<?> collection && collection.contains(mark)) {
                    return mark;
                }
                if (listed instanceof Map<?, ?> map && map.containsKey(mark)) {
                    return mark;
                }
            }
        } catch (Throwable unusable) {
            // 比較で落ちる入れ物 (相手の equals の都合) は候補から外れるだけ
        }
        return null;
    }

    /** 名簿から 1 つ外して、嘘が消えるか見る。消えなければ必ず戻す。 */
    private static Culprit tryExternal(LivingEntity entity, Field field, Object listed,
                                       Object mark, String label) {
        Object previous = remove(listed, mark);
        if (previous == ABSENT) {
            return null;
        }
        boolean cured = !lying(entity);
        restore(listed, mark, previous); // 当たりでも一度は必ず戻す。裏を取るため
        if (!cured || !lying(entity)) {
            return null;
        }
        remove(listed, mark);
        return new ExternalCulprit(field, mark, label + " (" + describeMark(mark) + " を外した)");
    }

    private static String describeMark(Object mark) {
        return mark instanceof Integer ? "エンティティ番号" : mark instanceof UUID ? "UUID" : "実体";
    }

    /** 入れ物から外す。入っていなければ {@link #ABSENT}、Map なら外した値を返す。 */
    private static Object remove(Object listed, Object mark) {
        try {
            if (listed instanceof Map<?, ?> map) {
                return map.containsKey(mark) ? ((Map<?, ?>) map).remove(mark) : ABSENT;
            }
            if (listed instanceof Collection<?> collection) {
                return collection.remove(mark) ? null : ABSENT;
            }
        } catch (Throwable immutable) {
            // 変えさせてもらえない入れ物は候補から外れるだけ
        }
        return ABSENT;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restore(Object listed, Object mark, Object previous) {
        try {
            if (listed instanceof Map map) {
                map.put(mark, previous);
            } else if (listed instanceof Collection collection) {
                collection.add(mark);
            }
        } catch (Throwable immutable) {
            // 戻せないなら、そもそも外せてもいない
        }
    }

    /** 「そもそも入っていなかった」を表す印。null と区別するために要る。 */
    private static final Object ABSENT = new Object();

    private static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (Throwable unreadable) {
            return null;
        }
    }

    /** 実体がぶら下げている小箱の中を 1 段だけ覗く。 */
    private static Culprit probeNested(LivingEntity entity, Field outer) {
        Object box;
        try {
            outer.setAccessible(true);
            box = outer.get(entity);
        } catch (Throwable t) {
            return null;
        }
        if (box == null || !foreign(box.getClass())) {
            return null;
        }
        for (Class<?> type = box.getClass(); type != null && foreign(type); type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object neutral = neutralFor(field.getType());
                if (neutral == null) {
                    continue;
                }
                Culprit hit = tryField(entity, outer, field, neutral,
                        outer.getName() + "." + field.getName());
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * 1 つ置き換えて、嘘が消えるか見る。消えなければ必ず元に戻す。
     *
     * @param outer null なら実体そのもののフィールド。そうでなければ小箱の中
     */
    private static Culprit tryField(LivingEntity entity, Field outer, Field target,
                                    Object neutral, String label) {
        Object owner;
        Object original;
        try {
            owner = outer == null ? entity : outer.get(entity);
            if (owner == null) {
                return null;
            }
            target.setAccessible(true);
            original = target.get(owner);
            if (neutral.equals(original)) {
                return null;
            }
            target.set(owner, neutral);
        } catch (Throwable t) {
            // final だったり触らせてもらえなかったり。試せないものは候補から外れるだけ
            return null;
        }
        boolean cured = !lying(entity);
        try {
            target.set(owner, original); // 当たりでも一度は必ず戻す。裏を取るため
        } catch (Throwable t) {
            return null;
        }
        if (!cured || !lying(entity)) {
            return null; // 置いても消えなかった / 戻しても復活しなかった。因果が無い
        }
        try {
            target.set(owner, neutral);
        } catch (Throwable t) {
            return null;
        }
        return new FieldCulprit(outer, target, neutral,
                label + " (" + original + " → " + neutral + ")");
    }

    // ---- 押さえ続ける -----------------------------------------------------------

    /**
     * 突き止めた出所を、いまの値に関わらず無害な値に戻す。
     *
     * <p>相手が毎 tick 掛け直してくるなら、こちらも毎 tick 戻す。ここは変換器の
     * 並び順と違って先着順ではないので、続けている限り最後はこちらの値になる。
     */
    static void enforce(LivingEntity entity) {
        Side side = side(entity);
        if (side.culprits.isEmpty()) {
            return;
        }
        for (Culprit culprit : side.culprits) {
            try {
                culprit.neutralize(entity);
            } catch (Throwable ignored) {
                // 相手の実体が別のクラスなら、そのフィールドは無い。それだけのこと
            }
        }
    }

    /** status 用。いま押さえている出所。 */
    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        SERVER.culprits.forEach(culprit -> lines.add("中和中 " + culprit.label()));
        CLIENT.culprits.forEach(culprit -> lines.add("中和中 (クライアント) " + culprit.label()));
        return lines;
    }

    public static int count() {
        return SERVER.culprits.size() + CLIENT.culprits.size();
    }

    public static void reset() {
        for (Side side : new Side[]{SERVER, CLIENT}) {
            side.culprits.clear();
            side.lastProbe = 0L;
            side.exhausted = false;
        }
        KNOWN_IDS.clear();
    }

    // ---- 出所 -------------------------------------------------------------------

    private interface Culprit {
        String label();

        void neutralize(LivingEntity entity);
    }

    private record SyncCulprit(int id, Object neutral, String label) implements Culprit {

        @Override
        public void neutralize(LivingEntity entity) {
            Map<?, ?> items = itemsOf(entity.getEntityData());
            if (items == null || !(items.get(id) instanceof SynchedEntityData.DataItem<?> item)) {
                return;
            }
            if (!neutral.equals(item.getValue())) {
                write(entity, item.getAccessor(), neutral);
            }
        }
    }

    /**
     * 実体の外の名簿に載せられている印。毎 tick 外し続ける。
     *
     * 相手が毎 tick 載せ直してくるなら、こちらも毎 tick 外すだけのこと。
     * 名簿の書き合いは先着順ではないので、続けている限り最後はこちらの状態になる。
     */
    private record ExternalCulprit(Field field, Object mark, String label) implements Culprit {

        @Override
        public void neutralize(LivingEntity entity) {
            Object listed = read(field, null);
            if (listed == null) {
                return;
            }
            // 別の相手の分まで外さない。印はこの実体を名指ししていたものだけ
            remove(listed, mark);
        }
    }

    private record FieldCulprit(Field outer, Field target, Object neutral, String label)
            implements Culprit {

        @Override
        public void neutralize(LivingEntity entity) {
            try {
                Object owner = outer == null ? entity : outer.get(entity);
                if (owner == null || !target.getDeclaringClass().isInstance(owner)) {
                    return;
                }
                if (!neutral.equals(target.get(owner))) {
                    target.set(owner, neutral);
                }
            } catch (Throwable ignored) {
                // 相手の実体が別のクラスなら、そのフィールドは無い。それだけのこと
            }
        }
    }

    // ---- 道具 -------------------------------------------------------------------

    /**
     * この生き物の系譜が名乗っている同期データの番号を集める。
     *
     * バニラも、その Mob を追加した Mod も、鍵はクラスの静的フィールドに置く。
     * 注入で生えた鍵 ({@code $} を含む名前) は系譜に居ても外から来たものなので数えない。
     */
    private static Set<Integer> collectKnownIds(Class<?> type) {
        Set<Integer> ids = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : declaredFields(current)) {
                if (!Modifier.isStatic(field.getModifiers())
                        || !EntityDataAccessor.class.isAssignableFrom(field.getType())
                        || field.getName().indexOf('$') >= 0) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(null) instanceof EntityDataAccessor<?> key) {
                        ids.add(key.getId());
                    }
                } catch (Throwable ignored) {
                    // 読めない鍵は素性不明のまま。実験の候補に回るだけで害はない
                }
            }
        }
        return ids;
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable t) {
            return new Field[0];
        }
    }

    /** バニラでも JDK でもこの Mod でもない = 外から持ち込まれたもの。 */
    private static boolean foreign(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isArray() || type.isEnum()) {
            return false;
        }
        return !GuardContext.isInfrastructure(type.getName());
    }

    /** その値の「何も足していない」状態。扱えない型は null。 */
    private static Object neutral(Object value) {
        if (value instanceof Float) {
            return 0.0F;
        }
        if (value instanceof Double) {
            return 0.0D;
        }
        if (value instanceof Integer) {
            return 0;
        }
        if (value instanceof Byte) {
            return (byte) 0;
        }
        if (value instanceof Short) {
            return (short) 0;
        }
        if (value instanceof Long) {
            return 0L;
        }
        if (value instanceof Boolean) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** フィールドの型から無害な値を決める。数と真偽だけを扱う。 */
    private static Object neutralFor(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.FALSE;
        }
        if (type == float.class || type == Float.class) {
            return 0.0F;
        }
        if (type == double.class || type == Double.class) {
            return 0.0D;
        }
        return null;
    }

    /**
     * 同期データの入れ物そのものを引く。
     *
     * 名前ではなく型で探す。{@code SynchedEntityData} の中で {@code Map} なのは
     * 番号引きの入れ物ひとつだけなので、マッピングの綴りを知らなくても当たる。
     */
    static Map<?, ?> itemsOf(SynchedEntityData data) {
        Field field = itemsField;
        if (field == null) {
            for (Field candidate : declaredFields(SynchedEntityData.class)) {
                if (Modifier.isStatic(candidate.getModifiers())
                        || !Map.class.isAssignableFrom(candidate.getType())) {
                    continue;
                }
                try {
                    candidate.setAccessible(true);
                } catch (Throwable t) {
                    return null;
                }
                field = candidate;
                itemsField = candidate;
                break;
            }
        }
        if (field == null) {
            return null;
        }
        try {
            return (Map<?, ?>) field.get(data);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 自分の書き込みとして通す。関所に自分で引っかかっては意味がない。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void write(LivingEntity entity, EntityDataAccessor<?> key, Object value) {
        DamageGuard.runAsSelf(() -> entity.getEntityData().set((EntityDataAccessor) key, value));
    }
}
