package jp.main.taikun.tpsthings.profile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlockEntity とエンティティが tick に使った時間を種類ごとに積む。
 *
 * 「何が重いか」を読んで当てるのではなく数字で出すための道具。種類を名指しする分岐は持たず、
 * 何が出てくるかは実行時に決まる。
 *
 * <p>加速で回した分は加速器側に乗る。{@code TickUtil} は ticker を直接呼ぶので、
 * この計測が挟まる {@code BoundTickingBlockEntity#tick} を通らないため。
 * 加速器 1 台がサーバに乗せている総コストが見たいので、これは意図した挙動。
 */
public final class TickProfiler {

    /** どの種類の tick を測ったか。 */
    public enum Scope {
        BLOCK_ENTITY,
        ENTITY
    }

    /** 種類 1 つ分の集計。サーバスレッドからしか触らないので同期は張らない。 */
    public static final class Entry {
        public final Scope scope;
        public final String id;
        long nanos;
        long count;
        long worstNanos;
        String worstWhere = "?";

        private Entry(Scope scope, String id) {
            this.scope = scope;
            this.id = id;
        }

        public long nanos() {
            return nanos;
        }

        public long count() {
            return count;
        }

        public long worstNanos() {
            return worstNanos;
        }

        /** 単発で一番時間を食った個体の居場所。見に行くための手がかり。 */
        public String worstWhere() {
            return worstWhere;
        }
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    /**
     * 種類 → 表示名の対応。
     *
     * レジストリ引きと文字列化を毎 tick やると、測るための費用が測る対象を超える。
     */
    private static final Map<BlockEntityType<?>, String> BE_NAMES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, String> ENTITY_NAMES = new ConcurrentHashMap<>();

    private static volatile boolean running = false;
    private static long ticks;

    private TickProfiler() {
    }

    // ---- 絞り所から呼ばれる入口 -------------------------------------------------

    /**
     * 計測の開始時刻。止まっているときは 0 を返し、{@link #end} が何もしない。
     *
     * 平常時の費用を volatile の読み 1 回に抑えるため、判定はここだけで済ませる。
     */
    public static long begin() {
        return running ? System.nanoTime() : 0L;
    }

    /** BlockEntity 1 回分の tick を計上する。 */
    public static void endBlockEntity(long start, BlockEntity blockEntity) {
        if (start == 0L || blockEntity == null) {
            return;
        }
        // クライアント側でも BlockEntity は tick する。混ぜると数字の意味が壊れる
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide()) {
            return;
        }
        String id = BE_NAMES.computeIfAbsent(blockEntity.getType(),
                type -> String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)));
        record(Scope.BLOCK_ENTITY, id, System.nanoTime() - start, blockEntity);
    }

    /** エンティティ 1 回分の tick を計上する。 */
    public static void endEntity(long start, Object subject) {
        if (start == 0L || !(subject instanceof Entity entity)) {
            return;
        }
        if (entity.level().isClientSide()) {
            return;
        }
        String id = ENTITY_NAMES.computeIfAbsent(entity.getType(),
                type -> String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        record(Scope.ENTITY, id, System.nanoTime() - start, entity);
    }

    /**
     * @param subject 居場所を文字列にする対象。最悪値を更新したときだけ触る
     */
    private static void record(Scope scope, String id, long elapsed, Object subject) {
        Entry entry = ENTRIES.computeIfAbsent(scope + "/" + id, key -> new Entry(scope, id));
        entry.nanos += elapsed;
        entry.count++;
        if (elapsed > entry.worstNanos) {
            entry.worstNanos = elapsed;
            entry.worstWhere = describe(subject);
        }
    }

    private static String describe(Object subject) {
        if (subject instanceof BlockEntity blockEntity) {
            var pos = blockEntity.getBlockPos();
            return dimension(blockEntity) + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }
        if (subject instanceof Entity entity) {
            return String.valueOf(entity.level().dimension().location())
                    + " " + (int) entity.getX() + " " + (int) entity.getY() + " " + (int) entity.getZ();
        }
        return "?";
    }

    private static String dimension(BlockEntity blockEntity) {
        return blockEntity.getLevel() == null
                ? "?"
                : String.valueOf(blockEntity.getLevel().dimension().location());
    }

    /** サーバ tick ごとに呼ばれる。1 tick あたりに直すための分母。 */
    public static void onServerTick() {
        if (running) {
            ticks++;
        }
    }

    // ---- コマンドから触る面 -----------------------------------------------------

    public static boolean isRunning() {
        return running;
    }

    public static void setRunning(boolean value) {
        running = value;
    }

    public static long elapsedTicks() {
        return ticks;
    }

    public static void reset() {
        ENTRIES.clear();
        ticks = 0;
    }

    /** 合計時間の降順。{@code scope} が null なら全部混ぜて並べる。 */
    public static List<Entry> top(Scope scope, int limit) {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (scope == null || entry.scope == scope) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.nanos).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    /** 集計対象すべての合計時間。割合を出すのに使う。 */
    public static long totalNanos(Scope scope) {
        long total = 0L;
        for (Entry entry : ENTRIES.values()) {
            if (scope == null || entry.scope == scope) {
                total += entry.nanos;
            }
        }
        return total;
    }
}
