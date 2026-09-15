package jp.main.taikun.tpsthings.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * メニューに並ぶ 1 行。
     *
     * @param group 入るグループの表示名。空ならグループに入れず一番上の階層に直接並ぶ
     */
    public record Entry(String id, String label, String value, int color, String description, String group) {

        public Entry(String id, String label, String value, int color, String description) {
            this(id, label, value, color, description, "");
        }

        public Entry withGroup(String group) {
            return new Entry(id, label, value, color, description, group);
        }
    }

    /** グループの並び順。ここに無いグループは後ろに回る。 */
    private static final List<String> GROUP_ORDER = List.of("防御", "自動対処の詳細", "攻撃", "記録・通知", "上級 (危険)");

    /** 項目 → グループ。並べ方はここだけで決める。 */
    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry("protect", "防御"),
            Map.entry("auto", "防御"),
            Map.entry("revert", "防御"),
            Map.entry("seal", "防御"),
            Map.entry("repair", "防御"),
            Map.entry("maxdepth", "自動対処の詳細"),
            Map.entry("stale", "自動対処の詳細"),
            Map.entry("reset", "自動対処の詳細"),
            Map.entry("strikeplayers", "攻撃"),
            Map.entry("strikerestore", "攻撃"),
            Map.entry("killall", "攻撃"),
            Map.entry("killself", "攻撃"),
            Map.entry("watch", "記録・通知"),
            Map.entry("motion", "記録・通知"),
            Map.entry("notify", "記録・通知"),
            Map.entry("unsafe", "上級 (危険)"),
            Map.entry("canon", "上級 (危険)"),
            Map.entry("probe", "上級 (危険)"),
            Map.entry("probereset", "上級 (危険)"),
            Map.entry("strikeforeign", "上級 (危険)"));

    /**
     * 表に従ってグループを付け、グループの順に並べ直す (同じグループの中は元の順)。
     * 表に無い項目はグループに入れず先頭に置く。
     */
    public static List<Entry> grouped(List<Entry> entries, Map<String, String> groupOfId, List<String> order) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            result.add(entry.withGroup(groupOfId.getOrDefault(entry.id(), "")));
        }
        result.sort(java.util.Comparator.comparingInt(entry -> {
            if (entry.group().isEmpty()) {
                return -1;
            }
            int index = order.indexOf(entry.group());
            return index < 0 ? order.size() : index;
        }));
        return result;
    }

    /** Unsafe の元栓。メニューで押すと、切る方向ならそのまま切り、入れる方向なら警告画面を出す。 */
    public static final String UNSAFE = "unsafe";
    /** 警告画面で同意したときだけ送られる、入れる方向の操作。 */
    public static final String UNSAFE_CONFIRMED = "unsafe_confirmed";

    /** 全殺害は 2 回押して打つ。1 回目からこの時間内の 2 回目だけを本物とみなす。 */
    private static final long KILL_ALL_CONFIRM_MS = 5000L;
    /** 全殺害の 1 回目を押した時刻 (人ごと)。 */
    private static final Map<UUID, Long> KILL_ALL_ARMED = new ConcurrentHashMap<>();

    private GuardSettings() {
    }

    private static boolean killAllArmed(ServerPlayer player) {
        Long at = KILL_ALL_ARMED.get(player.getUUID());
        return at != null && System.currentTimeMillis() - at <= KILL_ALL_CONFIRM_MS;
    }

    public static List<Entry> snapshot(ServerPlayer player) {
        List<Entry> entries = new ArrayList<>();
        entries.add(toggle(UNSAFE, "Unsafe / Java Agent", UnsafeSwitch.isEnabled(), WARN,
                "JVM の禁止を折って自分を Java Agent として付け、読み込み済みのクラスを書き換える手の元栓。"
                        + "disable・読み出しの正規化・関所の張り直し・外部 agent の深掘り・全クラスの走査がこれを使う。"
                        + "入れるときは警告画面が出る"));
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
        entries.add(toggle("probe", "読み出しの嘘の中和", StateProbe.isEnabled(), ON,
                "保護対象の生死の読み出しが嘘をついたら、材料の状態を実験で当てて無害な値に押さえ続ける。"
                        + "無関係な状態を押さえてしまったら切る (押さえている分も全部解除)"));
        int held = StateProbe.count();
        entries.add(new Entry("probereset", "中和を全部解除", held + " 件", held > 0 ? WARN : OFF,
                "いま押さえている嘘の出所を全部手放す (中和そのものは ON のまま、次に嘘が出たら探し直す)。"
                        + String.join(" / ", StateProbe.describe())));
        entries.add(toggle("strikeforeign", "貫通攻撃: 他の Mod の状態に触る", PiercingStrike.isTouchingForeign(), WARN,
                "耐えた相手に、他の Mod の名簿・スイッチ・門を打つ間だけ書き換えて打ち直す (生き残れば戻す)。"
                        + "切ると層を通すだけになる"));
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
        entries.add(toggle("strikerestore", "貫通攻撃: 失敗したら戻す", PiercingStrike.isRestoreOnFailure(), ON,
                "消しきれなかった相手を世界の索引へ戻す (動けるのにブロックが壊せない状態を残さない)"));
        entries.add(new Entry("reset", "自動措置を全部取り消す", "実行", WARN,
                "自動で適用した block / disable を取り消す (手動で入れたものは残る)"));
        entries.add(new Entry("killall", "(自機以外の)全エンティティ殺害",
                killAllArmed(player) ? "もう一度で実行" : "実行", WARN,
                "世界の索引に載っている生き物を、自分と保護対象以外すべて貫通攻撃で打つ。"
                        + "検索に映らない相手も拾う。取り返しがつかないので 5 秒以内に 2 回押して実行"));
        entries.add(new Entry("killself", "自身を殺害", "実行", WARN,
                "自分に貫通攻撃を打つ (装備型の不死の確認用)。保護対象のままだと、こちらの防御に拒否される"));
        return grouped(entries, GROUPS, GROUP_ORDER);
    }

    private static Entry toggle(String id, String label, boolean value, int onColor, String description) {
        return new Entry(id, label, value ? "ON" : "OFF", value ? onColor : OFF, description);
    }

    /** 元栓を入れ、その場で agent を確保して結果を返す。コマンドと共用。 */
    public static String enableUnsafe() {
        String saveFailure = UnsafeSwitch.setEnabled(true);
        String attachFailure = MethodDisabler.ensureReady();
        return "Unsafe / Java Agent: ON"
                + (attachFailure == null ? " (agent を確保しました)" : " (agent の確保に失敗: " + attachFailure + ")")
                + (saveFailure == null ? "" : " / 保存に失敗: " + saveFailure);
    }

    /** 元栓を切る。既に書き換えたものは再起動まで残ることを添える。コマンドと共用。 */
    public static String disableUnsafe() {
        boolean attached = MethodDisabler.isReady();
        String saveFailure = UnsafeSwitch.setEnabled(false);
        return "Unsafe / Java Agent: OFF"
                + (attached ? " (以後は使いません。既に書き換えたクラスと登録済みの変換器は再起動まで残ります)" : "")
                + (saveFailure == null ? "" : " / 保存に失敗: " + saveFailure);
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
            case UNSAFE -> {
                save = false; // 元栓は自分のファイルに保存する
                if (UnsafeSwitch.isEnabled()) {
                    result = disableUnsafe();
                } else {
                    // 警告画面を経ずに届いた入れる操作 (古いクライアント等) は通さない
                    result = "Unsafe / Java Agent を有効にするには、警告画面で同意してください";
                }
            }
            case UNSAFE_CONFIRMED -> {
                save = false;
                result = enableUnsafe();
            }
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
            case "probe" -> {
                int released = StateProbe.count();
                boolean next = !StateProbe.isEnabled();
                StateProbe.setEnabled(next);
                result = "読み出しの嘘の中和: " + onOff(next) + (next ? "" : " (押さえていた " + released + " 件を解除)");
            }
            case "probereset" -> {
                save = false;
                int released = StateProbe.count();
                StateProbe.reset();
                result = "押さえていた嘘の出所 " + released + " 件を解除しました";
            }
            case "strikeforeign" -> {
                boolean next = !PiercingStrike.isTouchingForeign();
                PiercingStrike.setTouchingForeign(next);
                result = "貫通攻撃: 他の Mod の状態に触る: " + onOff(next);
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
            case "strikerestore" -> {
                boolean next = !PiercingStrike.isRestoreOnFailure();
                PiercingStrike.setRestoreOnFailure(next);
                result = "貫通攻撃: 消しきれなければ戻す: " + onOff(next);
            }
            case "reset" -> {
                // コマンドの reset と同じく保存はしない
                result = "自動で適用した措置を " + AutoGuard.resetAll() + " 件取り消しました";
                save = false;
            }
            case "killall" -> {
                save = false;
                List<LivingEntity> targets = StrikeCensus.sweepTargets(player.serverLevel(), null, 0, player);
                if (targets.isEmpty()) {
                    KILL_ALL_ARMED.remove(player.getUUID());
                    result = "世界の索引に、打てる生き物は居ませんでした";
                } else if (!killAllArmed(player)) {
                    // 検索に映らない相手も数えるので、見えている数と合わないのが普通。先に数を見せる
                    KILL_ALL_ARMED.put(player.getUUID(), System.currentTimeMillis());
                    result = "世界の索引に打てる生き物が " + targets.size() + " 体います。5 秒以内にもう一度押すと打ちます";
                } else {
                    KILL_ALL_ARMED.remove(player.getUUID());
                    int down = PiercingStrike.strikeEach(player, targets);
                    result = "索引から " + targets.size() + " 体に打ちました (通った " + down + " 体)";
                }
            }
            case "killself" -> {
                save = false;
                // 保護対象のまま打つと「耐えた」に見えるが、耐えているのはこちらの関所
                boolean guarded = AutoGuard.isProtected(player);
                PiercingStrike.Result struck = PiercingStrike.strike(player, player);
                result = "自分に打ちました: " + struck
                        + (guarded ? " (保護対象のままなので、拒否されたならこちらの防御が理由です)" : "");
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
