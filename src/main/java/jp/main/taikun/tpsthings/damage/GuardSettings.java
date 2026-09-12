package jp.main.taikun.tpsthings.damage;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * SugoiMenu から触れる即死対策の設定。
 *
 * 表示名・現在値・説明はここで組んでクライアントへ送る。クライアントは受け取った一覧を
 * 並べて、選ばれた id を送り返すだけで、設定の中身を知らない。
 * 変え方はコマンド ({@link DamageGuardCommand}) と揃えてある。
 */
public final class GuardSettings {

    public static final int ON = 0x55FF55;
    public static final int OFF = 0x9A9AA6;
    public static final int WARN = 0xFFD84A;
    public static final int INFO = 0x7FD8FF;

    /** メニューに並ぶ 1 行。 */
    public record Entry(String id, String label, String value, int color, String description) {
    }

    private GuardSettings() {
    }

    public static List<Entry> snapshot(ServerPlayer player) {
        List<Entry> entries = new ArrayList<>();
        entries.add(toggle("protect", "自分を保護", AutoGuard.isManuallyProtected(player), ON,
                "装備に関係なく、外すまで自分を保護対象にする (入れると自動対処も有効になる)"));
        entries.add(toggle("auto", "自動対処", AutoGuard.isEnabled(), ON,
                "保護対象の HP 減少を検出して、呼び出し元を段階的に止める"));
        entries.add(toggle("revert", "HP 書き戻し", AutoGuard.isRevertEnabled(), ON,
                "減らされた HP をその tick のうちに書き戻す (自動対処が有効なときだけ効く)"));
        entries.add(toggle("seal", "封印", HealthGuard.isSealed(), WARN,
                "保護対象の HP 減少と殺意のある削除を、呼び出し元を問わず全部拒否する"));
        entries.add(toggle("canon", "読み出しの正規化", ReaderGuard.isCanonical(), WARN,
                "getHealth / isAlive / isDeadOrDying を正規の実装に戻す (再起動では戻さない)"));
        entries.add(toggle("repair", "関所の張り直し", RepairGuard.isEnabled(), ON,
                "剥がされた関所を自動で張り直す"));
        entries.add(new Entry("notify", "報告の出し先", GuardNotice.modeName(), INFO,
                "chat / actionbar / log を切り替える (ログには常に残る)。Shift+右クリックで逆順"));
        int depth = AutoGuard.getMaxDepth();
        entries.add(new Entry("maxdepth", "遡る段数の上限", String.valueOf(depth), depth >= 5 ? WARN : INFO,
                "右クリックで +1、Shift+右クリックで -1。深くすると相手を行動不能にしやすくなる"));
        entries.add(toggle("stale", "古い連鎖でも進める", AutoGuard.isIgnoringStaleChains(), WARN,
                "直近に観測した呼び出し元が無くても段を進める (無関係なダメージを犯人にしやすくなる)"));
        entries.add(toggle("watch", "呼び出し元の記録", DamageGuard.isWatching(), ON,
                "HP を減らした呼び出し元を記録する (/tpsthings damage log list で見る)"));
        entries.add(toggle("motion", "位置・速度の絞り所", DamageGuard.isMotionGuard(), ON,
                "強制移動を watch / block できるようにする"));
        entries.add(toggle("strikeplayers", "貫通攻撃: プレイヤーも索引層", PiercingStrike.isPlayersFullDepth(), WARN,
                "OO の即死攻撃をプレイヤーにも除去・索引の層まで打つ (死を拒否した相手は再接続まで動けなくなる)"));
        entries.add(new Entry("reset", "自動措置を全部取り消す", "実行", WARN,
                "自動で適用した block / disable を取り消す (手動で入れたものは残る)"));
        return entries;
    }

    private static Entry toggle(String id, String label, boolean value, int onColor, String description) {
        return new Entry(id, label, value ? "ON" : "OFF", value ? onColor : OFF, description);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    /**
     * 1 項目を操作する。トグルは反転、選択肢と数値は direction (+1 / -1) の向きに進める。
     *
     * @return 画面に出す結果の文。知らない id なら null
     */
    public static String apply(ServerPlayer player, String id, int direction) {
        String result;
        boolean save = true;
        switch (id) {
            case "protect" -> {
                // 装備由来の同期に上書きされないよう、コマンドと同じく手動枠に入れる
                boolean next = !AutoGuard.isManuallyProtected(player);
                AutoGuard.setManuallyProtected(player, next);
                if (next) {
                    AutoGuard.setEnabled(true);
                }
                result = next ? "保護対象に追加しました" : "手動の保護から外しました";
            }
            case "auto" -> {
                boolean next = !AutoGuard.isEnabled();
                AutoGuard.setEnabled(next);
                result = "自動対処: " + onOff(next);
            }
            case "revert" -> {
                boolean next = !AutoGuard.isRevertEnabled();
                AutoGuard.setReverting(next);
                result = "HP 書き戻し: " + onOff(next);
            }
            case "seal" -> {
                boolean next = !HealthGuard.isSealed();
                HealthGuard.setSealed(next);
                result = "封印: " + onOff(next)
                        + (next && AutoGuard.protectedCount() == 0 ? " (保護対象が居ないので、いまは何も起きません)" : "");
            }
            case "canon" -> {
                boolean next = !ReaderGuard.isCanonical();
                String failure = ReaderGuard.setCanonical(next);
                if (failure != null) {
                    result = "正規化できませんでした: " + failure;
                    save = false;
                } else {
                    result = "読み出しの正規化: " + onOff(next);
                }
            }
            case "repair" -> {
                boolean next = !RepairGuard.isEnabled();
                RepairGuard.setEnabled(next);
                result = "関所の張り直し: " + onOff(next);
            }
            case "notify" -> {
                GuardNotice.Mode[] modes = GuardNotice.Mode.values();
                GuardNotice.Mode current = GuardNotice.parse(GuardNotice.modeName());
                GuardNotice.setMode(modes[Math.floorMod(current.ordinal() + direction, modes.length)]);
                result = "報告の出し先: " + GuardNotice.modeName();
            }
            case "maxdepth" -> result = "遡る段数の上限: " + AutoGuard.setMaxDepth(AutoGuard.getMaxDepth() + direction);
            case "stale" -> {
                boolean next = !AutoGuard.isIgnoringStaleChains();
                AutoGuard.setIgnoreStaleChains(next);
                result = "古い連鎖でも進める: " + onOff(next);
            }
            case "watch" -> {
                boolean next = !DamageGuard.isWatching();
                DamageGuard.setWatching(next);
                result = "呼び出し元の記録: " + onOff(next);
            }
            case "motion" -> {
                boolean next = !DamageGuard.isMotionGuard();
                DamageGuard.setMotionGuard(next);
                result = "位置・速度の絞り所: " + onOff(next);
            }
            case "strikeplayers" -> {
                boolean next = !PiercingStrike.isPlayersFullDepth();
                PiercingStrike.setPlayersFullDepth(next);
                result = "貫通攻撃をプレイヤーにも索引層まで: " + onOff(next);
            }
            case "reset" -> {
                // コマンドの reset と同じく保存はしない
                result = "自動で適用した措置を " + AutoGuard.resetAll() + " 件取り消しました";
                save = false;
            }
            default -> {
                return null;
            }
        }
        if (save) {
            GuardConfig.save();
        }
        GuardNotice.info(player.getGameProfile().getName() + " がメニューから変更: " + result);
        return result;
    }
}
