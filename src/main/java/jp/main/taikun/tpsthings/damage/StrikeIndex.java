package jp.main.taikun.tpsthings.damage;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 世界の索引を担う物が<b>抱えている入れ物</b>から、相手を外す。
 *
 * <p>{@link StrikeRosters} の兄弟。あちらは実体の<b>外</b>にある静的な名簿を相手にするが、
 * こちらは世界の索引そのもの (UUID 索引・tick 一覧・区画・UUID 台帳) を相手にする。
 *
 * <p>索引はバニラの入れ物だけとは限らない。索引の持ち主に<b>入れ物を生やされる</b>ことがあり、
 * そうすると相手は素の索引に一度も載らないまま、そちらへ隠される。読み出しの方
 * ({@code getEntity} / {@code getEntities} / {@code size}) に混ぜて返すようにしておけば、
 * 世界から見れば今までどおり存在したままになる。素の入れ物だけ外しても、そういう相手には
 * 一生届かない (実測: 除去の印も tick 一覧も区画も通っているのに UUID 索引だけ落ちなかった)。
 *
 * <p>だから入れ物を名指ししない。索引を担う物が持っている入れ物を<b>型で</b>見つけて、
 * そこから相手を外す。鍵が UUID でも番号でも、値が実体そのものでも外せるように全部試す。
 * 消すのは<b>相手に当たる記載だけ</b>で、入れ物ごと壊すことはしない。
 *
 * <p>特定の Mod を名指しする分岐は持たない。
 */
final class StrikeIndex {

    private StrikeIndex() {
    }

    /** 包みを剥がして降りる深さの上限。包みの包みまでは見るが、世界中を舐めには行かない。 */
    private static final int MAX_WRAP_DEPTH = 6;

    /**
     * {@code holder} が抱えている入れ物から相手を外す。
     *
     * @return 舐めた入れ物の数。0 なら索引を持っていない物を渡している
     */
    static int purge(Object holder, LivingEntity target) {
        return purge(holder, target, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int purge(Object holder, LivingEntity target, int depth, Set<Object> seen) {
        if (holder == null || depth > MAX_WRAP_DEPTH || !seen.add(holder)) {
            return 0;
        }
        UUID uuid = target.getUUID();
        Integer id = target.getId();
        int touched = 0;
        for (Class<?> type = holder.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object held;
                try {
                    field.setAccessible(true);
                    held = field.get(holder);
                } catch (Throwable closed) {
                    continue;
                }
                if (held == null || held == holder) {
                    continue;
                }
                if (held instanceof Map) {
                    Map container = (Map) held;
                    touched++;
                    attempt(() -> container.remove(uuid));
                    attempt(() -> container.remove(id));
                    attempt(() -> container.values().removeIf(value -> value == target));
                    if (handmade(held)) {
                        touched += purge(held, target, depth + 1, seen);
                    }
                } else if (held instanceof Collection) {
                    Collection container = (Collection) held;
                    touched++;
                    // 入れ物自身の remove を先に通す。区画の入れ物のように、
                    // 中で複数の一覧に載せている物は自分の手で外させないと片方が残る
                    attempt(() -> container.remove(target));
                    attempt(() -> container.remove(uuid));
                    attempt(() -> container.removeIf(value -> value == target || uuid.equals(value)));
                    if (handmade(held)) {
                        touched += purge(held, target, depth + 1, seen);
                    }
                } else if (field.getType().isInstance(holder)) {
                    // 自分と同じ種類の物を抱えている = これは<b>包み</b>で、本体は中にある。
                    // 索引を丸ごと包んで、出入りも読み出しも中へ流す作りが実際にある
                    // (実測: 区画が包まれていて、入れ物は一段内側にあった)。
                    // 降りるのは包みの形のときだけ — 何でも辿ると世界中を舐めに行ってしまう
                    touched += purge(held, target, depth + 1, seen);
                }
            }
        }
        return touched;
    }

    /**
     * いま<b>どの入れ物</b>が相手を抱えているか。診断用。
     *
     * <p>{@link #purge} で外したのに、世界がまだ相手を数えていることがある。そのとき知りたいのは
     * 「外し漏れた入れ物があるのか」「入れ物には居ないのに読み出しだけが混ぜて返しているのか」の
     * 切り分けで、これは外側からは同じ「まだ居る」にしか見えない。見つけた場所を名前で返す。
     */
    static List<String> locate(Object holder, LivingEntity target) {
        List<String> found = new ArrayList<>();
        locate(holder, target, 0, Collections.newSetFromMap(new IdentityHashMap<>()), "", found);
        return found;
    }

    private static void locate(Object holder, LivingEntity target, int depth, Set<Object> seen,
                               String path, List<String> found) {
        if (holder == null || depth > MAX_WRAP_DEPTH || !seen.add(holder)) {
            return;
        }
        UUID uuid = target.getUUID();
        Integer id = target.getId();
        for (Class<?> type = holder.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object held;
                try {
                    field.setAccessible(true);
                    held = field.get(holder);
                } catch (Throwable closed) {
                    continue;
                }
                if (held == null || held == holder) {
                    continue;
                }
                String name = path + type.getSimpleName() + "#" + field.getName();
                if (held instanceof Map<?, ?> map) {
                    boolean has = false;
                    try {
                        has = map.containsKey(uuid) || map.containsKey(id) || map.containsValue(target);
                    } catch (Throwable unreadable) {
                        // 比べられない入れ物は判定できないだけ
                    }
                    if (has) {
                        found.add(name);
                    }
                } else if (held instanceof Collection<?> collection) {
                    boolean has = false;
                    try {
                        has = collection.contains(target) || collection.contains(uuid);
                    } catch (Throwable unreadable) {
                        // 同上
                    }
                    if (has) {
                        found.add(name);
                    }
                } else if (field.getType().isInstance(holder)) {
                    locate(held, target, depth + 1, seen, name + " → ", found);
                }
            }
        }
    }

    /**
     * 素の入れ物ではなく、<b>誰かが作った入れ物</b>か。
     *
     * <p>素の入れ物 (JDK・fastutil・Guava) は中身が配列なので、降りても名前では辿れないし、
     * 降りる必要も無い — 自分の {@code remove} が必ず効く。
     *
     * <p>危ないのは自前の入れ物の方で、中に<b>本物の一覧を何本も抱えている</b>ことがある。
     * 外向きの {@code remove} は等値 ({@code equals}) で消す作りが多く、読み出しを書き換えて
     * くる相手の前ではそれ自体が信用できない。反復子を読み出し専用にしてある物もあり、
     * その場合こちらの同一性による削除は例外になって<b>黙って何も起きない</b>
     * (実測: 区画の入れ物がこれで、外したつもりのまま世界に数えられ続けていた)。
     *
     * <p>だから自前の入れ物には一段降りて、中の素の一覧から<b>同一性で</b>外す。
     */
    private static boolean handmade(Object container) {
        String name = container.getClass().getName();
        return !name.startsWith("java.")
                && !name.startsWith("it.unimi.")
                && !name.startsWith("com.google.common.");
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable unreadable) {
            return new Field[0];
        }
    }

    /** 外せない入れ物 (不変・読み出し専用の反復子) は、そこだけ諦めて次へ進む。 */
    private static void attempt(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError immutable) {
            // 変えさせてもらえない入れ物は、そもそも外せてもいない
        }
    }
}
