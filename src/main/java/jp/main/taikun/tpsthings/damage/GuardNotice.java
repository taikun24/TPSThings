package jp.main.taikun.tpsthings.damage;

import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 関所からの報告をどこへ出すか。
 *
 * 報告は「何が起きたか」を伝えるためのもので、読み飛ばせないと逆に本物を隠す。
 * 段を進めるたびにチャットへ流すと、数十行で画面が埋まって、その中に一度だけ出る
 * 「関所を一度も通っていません」を見落とす。出し先と、同じ文言の抑制をここに集める。
 */
public final class GuardNotice {

    public enum Mode {
        /** チャット欄に残す。後から遡れるが、流量が多いと他が読めなくなる。 */
        CHAT,
        /** ホットバー上に短く出す。残らないので、詳細はログで追う前提。 */
        ACTIONBAR,
        /** 画面には出さず、ログだけに残す。 */
        LOG
    }

    /** アクションバーは 1 行しか無い。長い文はここで切って、続きはログに委ねる。 */
    private static final int ACTIONBAR_LIMIT = 60;
    /** 同じ文言をこの間隔より短く繰り返さない。 */
    private static final long REPEAT_MS = 3000L;

    private static volatile Mode mode = Mode.CHAT;

    /** 相手ごとの、最後に出した文言とその時刻。 */
    private static final Map<UUID, String> LAST_TEXT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_AT = new ConcurrentHashMap<>();
    /** {@link #warnThrottled} 用。鍵ごとの最終出力時刻。 */
    private static final Map<String, Long> THROTTLED_AT = new ConcurrentHashMap<>();

    private GuardNotice() {
    }

    /**
     * 画面に出すほどではない報告をログに残す。
     *
     * プレフィクスの書式をここに集める。各クラスが LOGGER を直接叩くと、
     * 書式も抑制もばらばらになって grep で追えなくなる。
     */
    public static void info(String message) {
        Tpsthings.LOGGER.info("[DamageGuard] {}", message);
    }

    public static void warn(String message) {
        Tpsthings.LOGGER.warn("[DamageGuard] {}", message);
    }

    /**
     * 連打される警告。同じ鍵は {@link #REPEAT_MS} に 1 回だけ出す。
     *
     * 関所が通してしまった報告などは 1 tick に何度も鳴るので、
     * そのまま出すと本物の 1 行がログの洪水に沈む。
     */
    public static void warnThrottled(String key, String message) {
        long now = System.currentTimeMillis();
        Long last = THROTTLED_AT.get(key);
        if (last != null && now - last < REPEAT_MS) {
            return;
        }
        THROTTLED_AT.put(key, now);
        warn(message);
    }

    public static void setMode(Mode value) {
        mode = value;
    }

    /** 設定値の文字列から解決する。読めなければ null。 */
    public static Mode parse(String text) {
        try {
            return Mode.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    public static String modeName() {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 報告を 1 件出す。
     *
     * ログには常に残す (抑制された分を除く)。原因を追う側は必ずログを読むので、
     * 画面の出し先をどう変えても、そこだけは細らせない。
     */
    public static void send(Entity victim, String message, boolean warning) {
        boolean repeat = victim != null && isRepeat(victim.getUUID(), message);
        if (!repeat) {
            Tpsthings.LOGGER.info("[DamageGuard] {}", message);
        }
        if (mode == Mode.LOG || !(victim instanceof Player player)) {
            return;
        }
        Component text = Component.literal("[DamageGuard] " + message)
                .withStyle(warning ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
        if (mode == Mode.ACTIONBAR) {
            // アクションバーは数秒で消える。同じ文言の再送は上書きになるだけなので抑制しない
            player.displayClientMessage(Component.literal(shorten(message))
                    .withStyle(warning ? ChatFormatting.YELLOW : ChatFormatting.AQUA), true);
        } else if (!repeat) {
            player.sendSystemMessage(text);
        }
    }

    private static String shorten(String message) {
        return message.length() <= ACTIONBAR_LIMIT
                ? message
                : message.substring(0, ACTIONBAR_LIMIT - 1) + "…";
    }

    private static boolean isRepeat(UUID id, String message) {
        long now = System.currentTimeMillis();
        String previous = LAST_TEXT.put(id, message);
        Long at = LAST_AT.put(id, now);
        return message.equals(previous) && at != null && now - at < REPEAT_MS;
    }

    public static void reset() {
        LAST_TEXT.clear();
        LAST_AT.clear();
        THROTTLED_AT.clear();
    }

    public static void forget(UUID id) {
        LAST_TEXT.remove(id);
        LAST_AT.remove(id);
    }
}
