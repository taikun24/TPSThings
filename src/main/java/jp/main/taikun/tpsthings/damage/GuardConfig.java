package jp.main.taikun.tpsthings.damage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ブロック中・無効化中の署名と各スイッチを config に残し、次回起動時に戻す。
 *
 * 手で入れたものと自動で入れたものを分けて持つ。分けずに畳むと、再起動をまたいだ瞬間に
 * {@code reset} が「自動で入れたものだけ取り消す」を守れなくなる。
 *
 * 保存するのは実行時に集まった文字列だけで、既定値として特定の署名は一切持たない。
 */
public final class GuardConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = Tpsthings.MODID + "-damageguard.json";

    /** 起動直後の復元中は保存しない。復元の途中経過で上書きしてしまうため。 */
    private static volatile boolean restoring = false;

    private GuardConfig() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    // ---- 保存 -------------------------------------------------------------------

    /** いまの状態を書き出す。状態を変えるコマンドのあとに毎回呼ぶ。 */
    public static void save() {
        if (restoring) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("watch", DamageGuard.isWatching());
        root.addProperty("motion", DamageGuard.isMotionGuard());
        root.addProperty("auto", AutoGuard.isEnabled());
        root.addProperty("stale", AutoGuard.isIgnoringStaleChains());
        root.addProperty("revert", AutoGuard.isRevertEnabled());
        root.addProperty("seal", HealthGuard.isSealed());
        root.addProperty("canon", ReaderGuard.isCanonical());
        root.addProperty("repair", RepairGuard.isEnabled());
        root.addProperty("notify", GuardNotice.modeName());
        root.addProperty("maxDepth", AutoGuard.getMaxDepth());
        root.addProperty("strikePlayers", PiercingStrike.isPlayersFullDepth());
        root.addProperty("strikeRestore", PiercingStrike.isRestoreOnFailure());
        root.addProperty("strikeForeign", PiercingStrike.isTouchingForeign());
        root.addProperty("probe", StateProbe.isEnabled());
        root.add("blocked", GSON.toJsonTree(new ArrayList<>(DamageGuard.blocked())));
        // disable は保存しない。本体ごと消す手を次の起動に持ち越すと、外れていたときに
        // 起動やワールド読み込みの時点で落ち、メニューを開いて戻す機会すら無くなる
        root.add("autoBlocked", GSON.toJsonTree(new ArrayList<>(AutoGuard.autoBlockedSignatures())));
        root.add("manualProtected", GSON.toJsonTree(new ArrayList<>(AutoGuard.manualProtectedIds())));

        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException failure) {
            GuardNotice.warn("設定を保存できませんでした: " + failure);
        }
    }

    // ---- 復元 -------------------------------------------------------------------

    /**
     * 保存された状態を戻す。
     *
     * disable はクラスがまだ読まれていなくても登録だけしておけば、
     * 読まれた時点で変換器が拾う。むしろ早い方が取りこぼしが少ない。
     */
    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception failure) {
            GuardNotice.warn("設定を読めませんでした: " + failure);
            return;
        }
        if (root == null) {
            return;
        }

        restoring = true;
        try {
            DamageGuard.setWatching(bool(root, "watch", false));
            DamageGuard.setMotionGuard(bool(root, "motion", false));
            AutoGuard.setEnabled(bool(root, "auto", true));
            AutoGuard.setIgnoreStaleChains(bool(root, "stale", false));
            AutoGuard.setReverting(bool(root, "revert", true));
            HealthGuard.setSealed(bool(root, "seal", false));
            RepairGuard.setEnabled(bool(root, "repair", true));
            PiercingStrike.setPlayersFullDepth(bool(root, "strikePlayers", false));
            PiercingStrike.setRestoreOnFailure(bool(root, "strikeRestore", false));
            PiercingStrike.setTouchingForeign(bool(root, "strikeForeign", true));
            StateProbe.setEnabled(bool(root, "probe", true));
            // 読み出しの正規化 (canon) は<b>復元しない</b>。
            //
            // 本体の取り合いは「最後に変換した者が勝つ」ゲームで、勝った瞬間に
            // 他所の Mod の生死の仕組みごと奪ってしまう手でもある。常時入れておく類の
            // 機構ではないので、要るときにその場で入れる扱いに降ろした。
            // コマンドからは今まで通り入れられる。
            if (bool(root, "canon", false)) {
                GuardNotice.info("読み出しの正規化は自動では戻しません (要るときだけ /"
                        + Tpsthings.MODID + " damage set canon true で入れてください)");
            }
            if (root.has("notify")) {
                GuardNotice.Mode mode = GuardNotice.parse(root.get("notify").getAsString());
                if (mode != null) {
                    GuardNotice.setMode(mode);
                }
            }
            if (root.has("maxDepth")) {
                AutoGuard.setMaxDepth(root.get("maxDepth").getAsInt());
            }

            Set<String> autoBlocked = strings(root, "autoBlocked");

            Set<String> blocked = strings(root, "blocked");
            blocked.forEach(DamageGuard::block);

            // disable は<b>手動も自動も持ち越さない</b>。
            //
            // disable はメソッドの本体を丸ごと捨てる手で、世界中のあらゆる呼び出しに効く。
            // 外れていたときに次の起動へ引き継ぐと、起動やワールド読み込みの途中で落ちて、
            // メニューもコマンドも使えないまま戻せなくなる。要るならその場で入れ直す。
            // (古い設定ファイルに残っている分は、読んで捨てるだけ)
            Set<String> staleDisabled = strings(root, "disabled");

            AutoGuard.markAutoBlocked(autoBlocked);
            AutoGuard.restoreManualProtected(strings(root, "manualProtected"));

            if (!blocked.isEmpty()) {
                GuardNotice.info("前回の設定を復元しました: block " + blocked.size() + " 件");
            }
            if (!staleDisabled.isEmpty()) {
                GuardNotice.info("前回の disable " + staleDisabled.size()
                        + " 件は破棄しました (disable は再起動をまたいで持ち越しません)");
            }
        } finally {
            restoring = false;
        }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    private static Set<String> strings(JsonObject root, String key) {
        Set<String> values = new LinkedHashSet<>();
        if (root.has(key) && root.get(key).isJsonArray()) {
            root.getAsJsonArray(key).forEach(element -> values.add(element.getAsString()));
        }
        return values;
    }
}
