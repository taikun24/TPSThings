package jp.main.taikun.tpsthings.damage;

import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * エンティティを tick させている側の呼び出し連鎖 (世界の根幹) を覚える。
 *
 * <p>自動対処は「関所から外へ向かって最初に出会う、バニラ以外のコード」を潰しにいく。
 * 攻撃の経路が全部バニラ (普通の Mob の殴り) だと、そこで出会うのは攻撃者ではなく
 * <b>tick を回している側に挟まった他 Mod のラッパー</b> ({@code EntityTickList} への
 * {@code @Redirect} など) になる。それを disable すると全エンティティの tick が止まる。
 *
 * <p>見分け方は名前ではなく実測: 生き物の tick の<b>祖先</b>として現れたフレームは、
 * 誰かを tick させるための道であって攻撃そのものではない。自動ではそこに触らない
 * (手動の disable は妨げない)。
 */
final class TickRoots {

    /** 起動直後はまだ何も知らないので、しばらくは毎回採る。 */
    private static final int WARMUP_SAMPLES = 256;
    /** 慣らしの後は、この数の tick に 1 回だけ採る。全エンティティが通る道なので。 */
    private static final int SAMPLE_MASK = 255;
    private static final int MAX_DEPTH = 64;
    /** tick の祖先はたかだか数百種。際限なく溜めないための上限。 */
    private static final int MAX_FRAMES = 4096;

    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Set<String> ROOTS = ConcurrentHashMap.newKeySet();

    // 競合しても採る頻度が少しずれるだけなので、同期はしない
    private static int counter;
    private static int samples;

    private TickRoots() {
    }

    /** 生き物の tick の中から呼ぶ。たまにだけスタックを採る。 */
    static void sample(Entity ticking) {
        int n = ++counter;
        if (samples >= WARMUP_SAMPLES && (n & SAMPLE_MASK) != 0) {
            return;
        }
        // クライアントの tick は別の道。サーバの連鎖と混ぜても役に立たない
        if (ticking.level().isClientSide() || ROOTS.size() >= MAX_FRAMES) {
            return;
        }
        samples++;
        WALKER.walk(stream -> {
            stream.limit(MAX_DEPTH)
                    .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                    .filter(signature -> !DamageGuard.isOwn(signature))
                    .forEach(ROOTS::add);
            return null;
        });
    }

    /** 生き物を tick させる道の上で見かけたフレームか。 */
    static boolean isRoot(String signature) {
        return ROOTS.contains(signature);
    }
}
