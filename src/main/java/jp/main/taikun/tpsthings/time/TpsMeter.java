package jp.main.taikun.tpsthings.time;

/**
 * サーバの TPS と、その揺れ幅を測る。
 *
 * 「いま何 TPS か」だけでなく「さっきと比べてどれだけ変わったか」を持つ。
 * 止まった水からは発電できないのと同じで、落差ではなく<b>変化</b>を取り出したいので。
 *
 * <p>Minecraft の TPS は 20 が上限で、それより速くはならない。
 * 「上がる」は落ち込みから戻ってくる局面のことを指す。
 */
public final class TpsMeter {

    /** 名目上の TPS。1 tick 50ms。 */
    public static final float NOMINAL_TPS = 20.0F;
    private static final long NOMINAL_NANOS_PER_TICK = 50_000_000L;

    /** 直近の窓 (tick 数)。短い方が「いま」、長い方が「ならし」。 */
    private static final int SHORT_WINDOW = 20;
    private static final int LONG_WINDOW = 100;

    private static final long[] TICK_NANOS = new long[LONG_WINDOW];
    private static int cursor = 0;
    private static int filled = 0;
    private static long lastTickAt = 0L;

    private static volatile float instantTps = NOMINAL_TPS;
    private static volatile float averageTps = NOMINAL_TPS;

    private TpsMeter() {
    }

    /** サーバ tick の終わりごとに呼ぶ。 */
    public static void onServerTick() {
        long now = System.nanoTime();
        if (lastTickAt != 0L) {
            TICK_NANOS[cursor] = now - lastTickAt;
            cursor = (cursor + 1) % LONG_WINDOW;
            if (filled < LONG_WINDOW) {
                filled++;
            }
            instantTps = tpsOver(SHORT_WINDOW);
            averageTps = tpsOver(LONG_WINDOW);
        }
        lastTickAt = now;
    }

    /**
     * 直近 {@code window} tick の平均から TPS を出す。
     *
     * 1 tick が 50ms より速く終わってもサーバは次の tick まで待つので、
     * 20 を超える値は出ない。上振れは丸めておく。
     */
    private static float tpsOver(int window) {
        int count = Math.min(window, filled);
        if (count == 0) {
            return NOMINAL_TPS;
        }
        long total = 0L;
        for (int i = 1; i <= count; i++) {
            total += TICK_NANOS[Math.floorMod(cursor - i, LONG_WINDOW)];
        }
        long average = total / count;
        if (average <= NOMINAL_NANOS_PER_TICK) {
            return NOMINAL_TPS;
        }
        return (float) (1_000_000_000.0 / average);
    }

    /** 直近 1 秒ぶんの TPS。 */
    public static float instantTps() {
        return instantTps;
    }

    /** 直近 5 秒ぶんの TPS。 */
    public static float averageTps() {
        return averageTps;
    }

    /**
     * 「いま」と「ならし」の差。TPS がどれだけ動いているか。
     *
     * 落ち込んだ瞬間も、戻ってきた瞬間も、同じだけ大きくなる。
     * 重いまま安定してしまえば 0 に戻る。
     */
    public static float swing() {
        return Math.abs(instantTps - averageTps);
    }

    /** 落ち込みの深さ。20 からどれだけ下がっているか。 */
    public static float dip() {
        return Math.max(0.0F, NOMINAL_TPS - averageTps);
    }

    /** ワールドを抜けたときに持ち越さない。 */
    public static void reset() {
        cursor = 0;
        filled = 0;
        lastTickAt = 0L;
        instantTps = NOMINAL_TPS;
        averageTps = NOMINAL_TPS;
    }
}
