package jp.main.taikun.tpsthings.damage;

import jp.main.taikun.tpsthings.mixin.AccessorPersistentEntitySectionManager;
import jp.main.taikun.tpsthings.mixin.AccessorServerLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 世界に<b>本当に居る</b>生き物を、検索の結果ではなく索引そのものから数え上げる。
 *
 * <p>{@link StrikeIndex} の裏返し。あちらは索引から相手を<b>外す</b>が、こちらは索引から相手を
 * <b>見つける</b>。狙う相手が分からなければ、どれだけ深い層を用意しても打つ機会が来ない。
 *
 * <p>世界に「そこに居る物を出せ」と訊く道 ({@code getEntitiesOfClass} /
 * {@code getEntity} / 当たり判定の探索) は、どれも<b>包める</b>。読み出しに濾し器を挟まれると、
 * 相手は世界に居るのに検索結果からだけ消える。そうなると当たり判定も照会も素通りするので、
 * こちらの入り口 (殴打のイベント・視野の円錐) には<b>相手が一度も渡ってこない</b> —
 * 層に降りる以前に、打つ対象が無い (実測: 索引にも区画にも居るのに、検索には一切出てこない相手が居た。
 * 打撃の記録が 1 行も出ないのが署名で、「耐えられた」と見分けがつかない)。
 *
 * <p>だから検索には頼らない。値が実際に載っている入れ物 — UUID 索引・番号索引・tick 一覧・区画 —
 * を直接読む。入れ物が包まれていれば {@link StrikeIndex} と同じやり方で<b>包みを剥がして</b>
 * 中の素の入れ物から拾う。濾し器は読み出しの口に付くので、一段内側には手が入っていない。
 *
 * <p>特定の Mod を名指しする分岐は持たない。
 */
public final class StrikeCensus {

    private StrikeCensus() {
    }

    /** 包みを剥がして降りる深さの上限。{@link StrikeIndex} と揃える。 */
    private static final int MAX_DEPTH = 6;
    /** 舐める物の総数の上限。索引から先は世界中に繋がっているので、必ず頭を打たせる。 */
    private static final int MAX_VISITS = 20_000;

    /**
     * 世界の索引に載っている生き物を全部返す。
     *
     * <p>検索に出てこない相手も含む。逆に言えば<b>検索より多く返る</b>ので、
     * 呼ぶ側が範囲と相手を必ず絞ること。
     */
    public static List<LivingEntity> living(ServerLevel level) {
        Set<LivingEntity> found = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] budget = {MAX_VISITS};
        AccessorServerLevel accessor = (AccessorServerLevel) level;
        try {
            AccessorPersistentEntitySectionManager manager =
                    (AccessorPersistentEntitySectionManager) (Object) accessor.tpsthings$entityManager();
            // UUID 索引・番号索引。検索の入口はここを見るが、濾し器は入口側に付く
            harvest(manager.tpsthings$visibleEntityStorage(), found, seen, budget, 0);
            // 区画。位置の通知がずれている相手は索引から落ちていても区画には残る
            harvest(manager.tpsthings$sectionStorage(), found, seen, budget, 0);
        } catch (Throwable unreadable) {
            // 索引に手が届かない構成でも、下の tick 一覧だけで数えられる分は数える
        }
        try {
            harvest(accessor.tpsthings$entityTickList(), found, seen, budget, 0);
        } catch (Throwable unreadable) {
            // 同上
        }
        return new ArrayList<>(found);
    }

    /**
     * {@code center} から {@code radius} の中に居る生き物。
     *
     * <p>位置は実体そのものから読む。座標まで嘘をつかれていれば範囲から外れるが、
     * そのときは範囲を広げれば済む (全域は {@link #living} で取れる)。
     */
    public static List<LivingEntity> livingNear(ServerLevel level, Vec3 center, double radius) {
        double squared = radius * radius;
        List<LivingEntity> near = new ArrayList<>();
        for (LivingEntity body : living(level)) {
            try {
                if (body.position().distanceToSqr(center) <= squared) {
                    near.add(body);
                }
            } catch (Throwable unreadable) {
                // 位置が読めない相手は範囲で切らずに入れておく。取り逃がすより良い
                near.add(body);
            }
        }
        return near;
    }

    /**
     * 掃討で打つ相手。撃った本人と保護対象は外す。
     *
     * <p>コマンドとメニューで同じ相手を選ぶために 1 箇所に置く。外す条件が 2 つに分かれると、
     * 片方だけ自分や守っている相手を巻き込む。
     *
     * @param center null なら世界全域 ({@code radius} は見ない)
     * @param self   撃った本人。居なければ null
     */
    public static List<LivingEntity> sweepTargets(ServerLevel level, Vec3 center, double radius, Entity self) {
        List<LivingEntity> pool = center == null || radius <= 0 ? living(level) : livingNear(level, center, radius);
        return pool.stream()
                .filter(body -> body != self)
                .filter(body -> !AutoGuard.isProtected(body))
                .toList();
    }

    /**
     * {@code holder} が抱えている入れ物から生き物を拾う。
     *
     * <p>{@link StrikeIndex#purge} と同じ形で歩く。違うのは、外すのではなく拾うことと、
     * 入れ物の<b>中身の物</b>にも降りること (区画置き場 → 区画 → 中の一覧、と辿るため)。
     */
    private static void harvest(Object holder, Set<LivingEntity> found, Set<Object> seen,
                                int[] budget, int depth) {
        if (holder == null || depth > MAX_DEPTH || budget[0] <= 0 || !seen.add(holder)) {
            return;
        }
        budget[0]--;
        for (Class<?> type = holder.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || budget[0] <= 0) {
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
                if (held instanceof Map<?, ?> map) {
                    gather(values(map), found, seen, budget, depth);
                    if (handmade(held)) {
                        harvest(held, found, seen, budget, depth + 1);
                    }
                } else if (held instanceof Collection<?> collection) {
                    gather(snapshot(collection), found, seen, budget, depth);
                    if (handmade(held)) {
                        harvest(held, found, seen, budget, depth + 1);
                    }
                } else if (field.getType().isInstance(holder) || handmade(held)) {
                    // 包み (自分と同じ型を抱えている) と、自前の入れ物の中の自前の物。
                    // 区画置き場 → 区画 → 中の一覧はこの道で辿り着く
                    harvest(held, found, seen, budget, depth + 1);
                }
            }
        }
    }

    /** 拾った中身を仕分ける。生き物なら数え、そうでない自前の物なら一段降りる。 */
    private static void gather(List<?> items, Set<LivingEntity> found, Set<Object> seen,
                               int[] budget, int depth) {
        for (Object item : items) {
            if (budget[0] <= 0) {
                return;
            }
            if (item instanceof LivingEntity body) {
                found.add(body);
            } else if (item != null && !(item instanceof Entity) && handmade(item)) {
                harvest(item, found, seen, budget, depth + 1);
            }
        }
    }

    /**
     * 中身を<b>写して</b>から返す。
     *
     * <p>索引は別のところから書き換えられる。読んでいる最中に変えられると例外で全部落ちるので、
     * 写せなかった入れ物はその 1 つだけ諦める。
     */
    private static List<Object> snapshot(Collection<?> collection) {
        try {
            return new ArrayList<>(collection);
        } catch (Throwable moving) {
            return List.of();
        }
    }

    private static List<Object> values(Map<?, ?> map) {
        try {
            return new ArrayList<>(map.values());
        } catch (Throwable moving) {
            return List.of();
        }
    }

    /**
     * 素の入れ物ではなく、<b>誰かが作った入れ物</b>か。
     *
     * <p>{@link StrikeIndex} と同じ判定。素の入れ物 (JDK・fastutil・Guava) は中身が配列なので
     * 降りる必要が無く、危ないのは自前の入れ物の方 — 読み出しに濾し器を挟んでおいて、
     * 本物の一覧は一段内側に持っている作りが実際にある。
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
}
