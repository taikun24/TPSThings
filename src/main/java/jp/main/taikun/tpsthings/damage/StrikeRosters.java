package jp.main.taikun.tpsthings.damage;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 貫通攻撃の相手を<b>名指しで守っている静的な名簿</b>から、相手を外す。
 *
 * <p>{@link StateProbe} の裏返し。守る側で「嘘の材料は必ず状態としてどこかに載っている」と
 * 分かった。無敵も同じで、実体の外に「この UUID は殺させない」という名簿を持つ作りが実際にある
 * (関所の注入も読み出しの書き換えも、全部その名簿を見て判断している)。
 * 注入や変換器と取り合うより、それらが見に行く<b>材料</b>を抜く方が一手で済む。
 *
 * <p>名簿かどうかは知らないので、相手の UUID か実体そのものを含む静的な入れ物を全部候補にする。
 * エンティティ番号は使わない (ただの整数なので、無関係な数の集合と偶然一致する)。
 * 外したものは追撃が持ち、終わったときに相手が生き残っていれば戻す。相手が居なくなっていれば、
 * その記載は既に宙に浮いた古いものなので戻さない。
 */
final class StrikeRosters {

    private static final String OWN_PACKAGE = "jp.main.taikun.tpsthings.";
    /** 候補の一覧を作り直す最短の間隔。読み込み済みクラスを全部舐めるので安くない。 */
    private static final long REBUILD_NANOS = 30_000_000_000L;
    /** これより大きい入れ物は名簿ではなく、中身を舐める費用だけがかかる。 */
    private static final int MAX_ROSTER_SIZE = 100_000;

    private static volatile List<Field> candidates = List.of();
    private static volatile int indexedClasses = -1;
    private static volatile long indexedAt = 0L;

    /** 外した 1 件。戻すのに要るものを全部持つ。 */
    record Taken(Object listed, Object mark, Object previous, String label) {
    }

    private static final Object ABSENT = new Object();

    private StrikeRosters() {
    }

    /**
     * 相手を名指ししている名簿から外す。
     *
     * @param already この追撃で既に外したもの。同じ記載を二重に数えないため
     * @return 今回新しく外したもの
     */
    static List<Taken> strip(LivingEntity target, List<Taken> already) {
        Object[] marks = {target.getUUID(), target};
        List<Taken> taken = new ArrayList<>();
        for (Field field : candidates()) {
            Object listed;
            try {
                listed = field.get(null);
            } catch (Throwable unreadable) {
                continue;
            }
            if (listed == null || tooLarge(listed)) {
                continue;
            }
            for (Object mark : marks) {
                Object previous = take(listed, mark);
                if (previous == ABSENT) {
                    continue;
                }
                boolean known = already.stream()
                        .anyMatch(entry -> entry.listed() == listed && entry.mark().equals(mark));
                if (!known) {
                    taken.add(new Taken(listed, mark, previous,
                            field.getDeclaringClass().getSimpleName() + "#" + field.getName()));
                }
            }
        }
        return taken;
    }

    /** 外したものを戻す。相手が戻る間に自分で載せ直していたら、そちらを優先して触らない。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void restore(List<Taken> taken) {
        for (Taken entry : taken) {
            try {
                if (entry.listed() instanceof Map map) {
                    if (!map.containsKey(entry.mark())) {
                        map.put(entry.mark(), entry.previous());
                    }
                } else if (entry.listed() instanceof Collection collection) {
                    if (!collection.contains(entry.mark())) {
                        collection.add(entry.mark());
                    }
                }
            } catch (Throwable immutable) {
                // 戻せない入れ物は、そもそも外せてもいない
            }
        }
    }

    /**
     * 診断用: 相手を名指ししている静的な入れ物の名前を、<b>外せたかに関わらず</b>全部挙げる。
     *
     * <p>{@link #strip} は外せたものだけを報せるので、「名指ししているのに外せない」入れ物が
     * あっても表に出ない。無敵が抜けない相手を調べるとき、材料がそもそも見えているのか、
     * 見えているのに外せていないのかを分けるために使う。
     */
    static List<String> naming(LivingEntity target) {
        Object[] marks = {target.getUUID(), target};
        List<String> names = new ArrayList<>();
        for (Field field : candidates()) {
            Object listed;
            try {
                listed = field.get(null);
            } catch (Throwable unreadable) {
                continue;
            }
            if (listed == null || tooLarge(listed)) {
                continue;
            }
            for (Object mark : marks) {
                boolean has = false;
                try {
                    if (listed instanceof Map<?, ?> map) {
                        has = map.containsKey(mark);
                    } else if (listed instanceof Collection<?> collection) {
                        has = collection.contains(mark);
                    }
                } catch (Throwable ignored) {
                    // 比較で落ちる入れ物は名指しの判定ができないだけ
                }
                if (has) {
                    names.add(field.getDeclaringClass().getSimpleName() + "#" + field.getName()
                            + (mark instanceof java.util.UUID ? "(UUID)" : "(実体)"));
                    break;
                }
            }
        }
        return names;
    }

    private static boolean tooLarge(Object listed) {
        try {
            return listed instanceof Collection<?> collection ? collection.size() > MAX_ROSTER_SIZE
                    : listed instanceof Map<?, ?> map && map.size() > MAX_ROSTER_SIZE;
        } catch (Throwable unusable) {
            return true;
        }
    }

    /** 入れ物から外す。入っていなければ {@link #ABSENT}、Map なら外した値を返す。 */
    private static Object take(Object listed, Object mark) {
        try {
            if (listed instanceof Map<?, ?> map) {
                return map.containsKey(mark) ? map.remove(mark) : ABSENT;
            }
            if (listed instanceof Collection<?> collection) {
                return collection.remove(mark) ? null : ABSENT;
            }
        } catch (Throwable immutable) {
            // 変えさせてもらえない入れ物 (不変・相手の equals の都合) は候補から外れるだけ
        }
        return ABSENT;
    }

    /**
     * 名簿になりうる静的フィールドの一覧。基盤とこの Mod のクラスは除く
     * (自分の保護の名簿を自分の矛で抜かないため)。
     */
    private static List<Field> candidates() {
        Class<?>[] loaded = MethodDisabler.loadedClasses();
        long now = System.nanoTime();
        if (loaded.length == indexedClasses || (indexedClasses >= 0 && now - indexedAt < REBUILD_NANOS)) {
            return candidates;
        }
        List<Field> found = new ArrayList<>();
        for (Class<?> owner : loaded) {
            if (owner == null || owner.isArray() || owner.isPrimitive()) {
                continue;
            }
            String name = owner.getName();
            if (name.startsWith(OWN_PACKAGE) || GuardContext.isInfrastructure(name)) {
                continue;
            }
            Field[] declared;
            try {
                declared = owner.getDeclaredFields();
            } catch (Throwable unreadable) {
                continue;
            }
            for (Field field : declared) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> type = field.getType();
                if (!Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                } catch (Throwable closed) {
                    continue;
                }
                found.add(field);
            }
        }
        candidates = found;
        indexedClasses = loaded.length;
        indexedAt = now;
        return found;
    }
}
