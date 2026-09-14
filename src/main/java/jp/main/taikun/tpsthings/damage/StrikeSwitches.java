package jp.main.taikun.tpsthings.damage;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 相手の Mod が<b>自分の関所を自分で通り抜けるために</b>置いている門を、その場だけ開ける。
 *
 * <p>{@link StrikeRosters} (実体の外の名簿) と {@link StrikeState} (実体に載ったスイッチ) の
 * third。守る側を書く立場になれば分かることだが、関所を張ると<b>自分の正規の処理まで止まる</b> —
 * ログアウト・次元移動・リスポーンでは自分で自分を消さなければならないのに、除去の関所が
 * それを拒否してしまう。だからどの実装も必ず「いまは自分の操作だから通してよい」という門を持つ。
 *
 * <p>その門はたいてい<b>スレッドに紐づいた真偽値</b>で置かれる。処理の間だけ立てて、終わったら
 * 下ろす使い方をするからで、これは {@link DamageGuard#runAsSelf} が自分の関所に対して
 * やっていることと同じ形をしている (こちらも同じ理由で同じ物を持っている)。
 *
 * <p>門を開けて打つと、相手の関所は<b>自分の正規の除去として</b>素通しする。入れ物を力ずくで
 * 抜くのと違って、相手の後始末も相手自身の手で正しく走るので、中途半端に壊れた状態が残らない。
 *
 * <p>触るのは<b>他所が持ち込んだ門だけ</b>で、開けたものは必ず元に戻す。スレッドに紐づくので、
 * 開けている間に影響が及ぶのはこの打撃を処理しているサーバスレッドの中だけ。
 *
 * <p>特定の Mod を名指しする分岐は持たない。
 */
final class StrikeSwitches {

    private static final String OWN_PACKAGE = "jp.main.taikun.tpsthings.";
    /** 門の一覧を作り直す最短の間隔。読み込み済みクラスを全部舐めるので安くない。 */
    private static final long REBUILD_NANOS = 30_000_000_000L;

    private static volatile List<Field> candidates = List.of();
    private static volatile int indexedClasses = -1;
    private static volatile long indexedAt = 0L;

    /** 開けた門 1 つ。戻すのに要るものを全部持つ。 */
    record Raised(ThreadLocal<Object> gate, Object previous, String label) {
    }

    private StrikeSwitches() {
    }

    /**
     * いま倒れている門を全部開ける。
     *
     * <p>既に開いている門は触らない (開けたのが自分だと勘違いして、後で閉じてしまわないため)。
     * 中身が真偽値でない門も触らない — 型を壊すと相手の処理そのものが落ちる。
     */
    @SuppressWarnings("unchecked")
    static List<Raised> raise() {
        List<Raised> raised = new ArrayList<>();
        for (Field field : candidates()) {
            try {
                if (!(field.get(null) instanceof ThreadLocal<?> gate)) {
                    continue;
                }
                Object current = gate.get();
                if (!(current instanceof Boolean open) || open) {
                    continue; // 真偽値でない / もう開いている
                }
                ThreadLocal<Object> raw = (ThreadLocal<Object>) gate;
                raw.set(Boolean.TRUE);
                raised.add(new Raised(raw, current,
                        field.getDeclaringClass().getSimpleName() + "#" + field.getName()));
            } catch (Throwable skip) {
                // 読めない / 変えられない門は候補から外れるだけ
            }
        }
        return raised;
    }

    /** 門になりうる静的フィールドの数。「門が無い」と「開けたが効かない」を診断で分けるのに使う。 */
    static int candidateCount() {
        return candidates().size();
    }

    /** 開けた門を必ず閉じる。打撃が例外で終わっても閉じる。 */
    static void lower(List<Raised> raised) {
        for (Raised entry : raised) {
            try {
                entry.gate().set(entry.previous());
            } catch (Throwable ignored) {
                // 閉じられない門は、そもそも開けてもいない
            }
        }
    }

    /**
     * 門になりうる静的フィールドの一覧。基盤とこの Mod のクラスは除く
     * (自分の「自分の操作中」の印を自分の矛で立てない)。
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
                if (!Modifier.isStatic(field.getModifiers())
                        || !ThreadLocal.class.isAssignableFrom(field.getType())) {
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
