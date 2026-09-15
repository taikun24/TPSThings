package jp.main.taikun.tpsthings.damage;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Unsafe と自己アタッチ Java Agent を使う手の元栓。<b>既定は切り</b>。
 *
 * <p>ここを通る手は JVM の禁止 ({@code ALLOW_ATTACH_SELF}) を折り、読み込み済みのクラスを
 * 実行中に書き換える。効けば強いが、外したときに壊すのは自分の世界だけでなく他所の Mod の
 * 中身まで及ぶ。だから本人が警告を読んで入れたときだけ動かす。
 *
 * <p>切ってあるときは、{@link SelfAttach} と {@link MethodDisabler} の入口が
 * 「agent は無い」と答える。上に乗っている手 (disable・読み出しの正規化・関所の張り直し・
 * 外部 agent の深掘り・全クラスを舐める走査) は、どれも agent が無いときの道を既に持っているので、
 * そちらへ落ちるだけで済む。
 *
 * <p>Mixin の設定を読む段階 ({@code AgentBootstrapPlugin}) から呼ぶので、
 * <b>Minecraft のクラスを一切参照しない</b>。保存先も {@link GuardConfig} とは別の小さなファイルにしてある
 * (あちらはゲームのクラスを読み込む)。
 */
public final class UnsafeSwitch {

    private static final String FILE_NAME = "tpsthings-unsafe.properties";
    private static final String KEY = "enabled";

    /** 切ってあるときに、agent を求めた側へ返す理由。 */
    public static final String REFUSAL = "Unsafe / Java Agent は無効です (すごいメニューか /tpsthings damage set unsafe で有効化)";

    /** 読み込み前は null。起動中に一度だけファイルから読む。 */
    private static volatile Boolean enabled;

    private UnsafeSwitch() {
    }

    public static boolean isEnabled() {
        Boolean current = enabled;
        if (current == null) {
            synchronized (UnsafeSwitch.class) {
                if (enabled == null) {
                    enabled = read();
                }
                current = enabled;
            }
        }
        return current;
    }

    /**
     * 切り替えて保存する。
     *
     * <p>切っても、既に書き換えたクラスと登録済みの変換器は<b>再起動まで残る</b>
     * (agent は外せない)。以後に新しく使わなくなるだけ。
     *
     * @return 保存の失敗理由。成功なら null
     */
    public static synchronized String setEnabled(boolean value) {
        enabled = value;
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.setProperty(KEY, String.valueOf(value));
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                properties.store(writer, "TPSThings: Unsafe / Java Agent を使う手の元栓 (既定 false)");
            }
            return null;
        } catch (Throwable failure) {
            return String.valueOf(failure);
        }
    }

    private static boolean read() {
        try {
            Path path = file();
            if (!Files.exists(path)) {
                return false;
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return Boolean.parseBoolean(properties.getProperty(KEY, "false").trim());
        } catch (Throwable unreadable) {
            // 読めないなら入れていないのと同じ扱い。危ない方へは倒さない
            return false;
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
