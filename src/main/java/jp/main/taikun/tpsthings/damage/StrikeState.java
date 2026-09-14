package jp.main.taikun.tpsthings.damage;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 貫通攻撃の相手が<b>実体そのものに載せている</b>不死のスイッチを、その場だけ倒す。
 *
 * <p>{@link StateProbe} (守る側で嘘の材料を実験で当てる) と {@link StrikeRosters}
 * (実体の外の名簿) の間を埋める。守る側の解析で分かったのは、無敵の判断材料が
 * <b>実体に混ぜ込まれた同期データの真偽値</b> (魂保護のフラグ等) や
 * <b>実体に生やした真偽値フィールド</b> (ムテキのフラグ等) に載っている作りが多いこと。
 * これらは注入や変換器が毎回見に行く<b>元</b>なので、注入と取り合うより元を倒す方が一手で済む。
 *
 * <p>触るのは<b>他所が持ち込んだものだけ</b>。同期データは「相手の Mod のクラスが
 * 静的フィールドに置いた鍵」で識別し、フィールドは「バニラでない名前 / 注入の印 ($)」で識別する。
 * バニラの真偽値には指一本触れない。倒したものは全部覚えておき、追撃が終わって相手が
 * 生き残っていれば元に戻す (倒し切れなかったのに戻さないと、無関係な仕組みを壊したことになる)。
 *
 * <p>特定の Mod を名指しする分岐は持たない。
 */
final class StrikeState {

    private static final String OWN_PACKAGE = "jp.main.taikun.tpsthings.";
    /** 外来の同期データ鍵の一覧を作り直す最短の間隔。読み込み済みクラスを全部舐めるので安くない。 */
    private static final long REBUILD_NANOS = 30_000_000_000L;

    /** 実体をどれだけ遡って真偽値フィールドを探すか。Entity / LivingEntity で足りる。 */
    private static final int MAX_FIELD_DEPTH = 6;

    private static volatile Set<Integer> foreignSyncedIds = Set.of();
    private static volatile int indexedClasses = -1;
    private static volatile long indexedAt = 0L;

    /** 倒した 1 件。戻すのに要るものを全部持つ。 */
    interface Change {
        void restore();
    }

    private StrikeState() {
    }

    /** 実体の不死スイッチを倒し、倒したものの一覧を返す。相手が生き残ったら {@link #restore} で戻す。 */
    static List<Change> neutralize(LivingEntity target) {
        List<Change> changes = new ArrayList<>();
        neutralizeSynced(target, changes);
        neutralizeFields(target, changes);
        return changes;
    }

    static void restore(List<Change> changes) {
        for (Change change : changes) {
            try {
                change.restore();
            } catch (Throwable ignored) {
                // 戻せないなら、そもそも倒せてもいない
            }
        }
    }

    // ---- 同期データの外来の真偽値 ------------------------------------------------

    private static void neutralizeSynced(LivingEntity target, List<Change> changes) {
        Map<?, ?> items = StateProbe.itemsOf(target.getEntityData());
        if (items == null) {
            return;
        }
        Set<Integer> foreign = foreignSyncedIds();
        for (Object raw : new ArrayList<>(items.values())) {
            if (!(raw instanceof SynchedEntityData.DataItem<?> item)) {
                continue;
            }
            try {
                if (!(item.getValue() instanceof Boolean flag) || !flag) {
                    continue;
                }
                if (!foreign.contains(item.getAccessor().getId())) {
                    continue; // バニラの同期データには触らない
                }
                setSynced(item, false);
                changes.add(() -> setSynced(item, true));
            } catch (Throwable skip) {
                // 読めない / 変えられない箱は候補から外れるだけ
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setSynced(SynchedEntityData.DataItem<?> item, boolean value) {
        try {
            ((SynchedEntityData.DataItem) item).setValue(value);
            item.setDirty(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 外来の同期データ鍵の id。相手の Mod のクラスが静的フィールドに置いた
     * {@link EntityDataAccessor} を集める。バニラ・Forge・JDK・この Mod のものは除く。
     */
    private static Set<Integer> foreignSyncedIds() {
        Class<?>[] loaded = MethodDisabler.loadedClasses();
        long now = System.nanoTime();
        if (loaded.length == indexedClasses || (indexedClasses >= 0 && now - indexedAt < REBUILD_NANOS)) {
            return foreignSyncedIds;
        }
        Set<Integer> found = new HashSet<>();
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
                        || !EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(null) instanceof EntityDataAccessor<?> accessor) {
                        found.add(accessor.getId());
                    }
                } catch (Throwable skip) {
                    // 読めない鍵は数えないだけ
                }
            }
        }
        foreignSyncedIds = found;
        indexedClasses = loaded.length;
        indexedAt = now;
        return found;
    }

    // ---- 実体に生やした外来の真偽値フィールド ------------------------------------

    private static void neutralizeFields(LivingEntity target, List<Change> changes) {
        int depth = 0;
        for (Class<?> type = target.getClass(); type != null && type != Object.class && depth < MAX_FIELD_DEPTH;
             type = type.getSuperclass(), depth++) {
            boolean vanillaType = GuardContext.isInfrastructure(type.getName());
            for (Field field : declaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != boolean.class) {
                    continue;
                }
                // バニラのクラスに素からある真偽値は触らない。混ぜ込まれた印 ($) があるものと、
                // そもそもバニラでないクラスが持つものだけ倒す
                if (vanillaType && !field.getName().contains("$")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (!field.getBoolean(target)) {
                        continue;
                    }
                    field.setBoolean(target, false);
                    changes.add(() -> {
                        try {
                            field.setBoolean(target, true);
                        } catch (Throwable ignored) {
                        }
                    });
                } catch (Throwable skip) {
                    // 読めない / 変えられないフィールドは候補から外れるだけ
                }
            }
        }
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable t) {
            return new Field[0];
        }
    }
}
