package jp.main.taikun.tpsthings.damage;

import net.minecraft.world.entity.Entity;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敵対 Mod の「自己アタッチ型 Java Agent」の痕跡を炙り出す。
 *
 * <p><b>止められはしない。見つけるだけ。</b> 相手の agent が先に走ってしまえば、
 * 他 agent の変換器を JVM から剥がす手段は無いし、既に書き換えられたクラスを
 * 元に戻す一般的な口も無い。だからこの関所の役目は「攻撃を止める」ではなく、
 * <b>関所に映らない殺し方の出所を名指しする</b>ことにある。
 * getHealth の嘘そのものは {@link StateProbe} が捕まえる。こちらはその一段上、
 * 「誰がその嘘をつく道具を持ち込んだか」を見る。
 *
 * <p>調べる痕跡は、想定敵 (NoSugar / Omni-Mobs / Forbidden Things) に共通する 3 つ:
 * <ol>
 *   <li>自己アタッチ許可フラグ ({@code HotSpotVirtualMachine.ALLOW_ATTACH_SELF}) が、
 *       こちらの操作でも JVM 引数でもなく立っている</li>
 *   <li>素性の知れない LaunchPlugin が ModLauncher に差し込まれている</li>
 *   <li>{@code jdk.internal.reflect.Reflection} のフィルタで、こちらのクラスが
 *       reflection 不可にされている (Forbidden Things の denyReflection 型)</li>
 * </ol>
 *
 * <p>自分自身の agent を敵と取り違えないことが最優先。自己アタッチ許可は
 * {@link SelfAttach} が「自分で立てたか」を覚えているので区別できる。LaunchPlugin は
 * こちらは差し込まないので、自パッケージ・基盤パッケージ以外は全部よそ者でよい。
 * 読めない環境では黙る — 確認できないことを異常と言うと、それ自体が誤検知になる。
 *
 * <p>{@link #scanInstrumentation} はさらに一段深い検証。上の 3 つが「足跡」なのに対し、
 * こちらは<b>敵の {@link Instrumentation} 実体そのものを掴めるか</b>を試す。
 * agent は {@code premain}/{@code agentmain} で Instrumentation を受け取り、塗り直しを
 * 続ける以上どこかに保持しているはず — こちらは自前 agent 経由で
 * {@code getAllLoadedClasses()} を持つので、全クラスの静的フィールドを舐めて
 * <b>自分のではない Instrumentation</b> を拾える。掴めればその変換器の数と、
 * 基盤でも自分でもない<b>外部変換器のクラス名</b>まで名指しできる。これが取れるかどうかが、
 * 将来「敵の変換器を外して塗り返す」攻め手が現実的かの分かれ目になる (取れなければ絵に描いた餅)。
 * 内部フィールドは JDK 実装依存なので、読めなければやはり黙る。
 */
public final class AttachGuard {

    /**
     * よそ者ではないと分かっている非基盤の LaunchPlugin。
     *
     * MixinExtras のような正規のライブラリは自分のパッケージで LaunchPlugin を登録するので、
     * 基盤扱いから漏れて容疑者に並ぶ。名前で分かるものは先に除いて雑音を減らす。
     */
    private static final List<String> KNOWN_GOOD_PLUGINS = List.of(
            "com.llamalad7.mixinextras");

    private AttachGuard() {
    }

    /**
     * 痕跡を調べる。見つかった疑わしいものを 1 件 1 行で返す。空なら異常なし。
     *
     * 状態を読むだけで何も書き換えないので、いつ何度呼んでも安全。
     */
    public static List<String> scan() {
        List<String> findings = new ArrayList<>();
        checkAllowAttachSelf(findings);
        checkLaunchPlugins(findings);
        checkReflectionFilter(findings);
        return findings;
    }

    /**
     * 敵の {@link Instrumentation} 実体を実際に掴みにいく深掘り検証。
     *
     * <p>全ロード済みクラスの静的フィールドを舐めて、<b>自分のではない Instrumentation</b>
     * を探す。見つかれば、それは別 agent の実体 (Instrumentation は agent 1 本につき 1 つ)。
     * その中の変換器管理を反射で開いて、変換器の総数と、基盤でも自分でもない
     * <b>外部変換器のクラス名</b>を報告する。相手が基盤の Instrumentation に相乗りして
     * 変換器だけ差し込む形 (自前の実体を持たない) も、この「外部変換器」で拾える。
     *
     * <p>クラス一覧を得るには自前 agent の Instrumentation が要る。{@code mayAttach} が真なら
     * 未確保のとき自己アタッチを試みる (コマンドからの明示的な検証はこちら)。偽なら未確保の
     * まま何もしない (起動時のログはゲームを触りたくないのでこちら)。
     *
     * @param mayAttach 自前 agent が未確保なら自己アタッチしてよいか
     * @return 見つかった疑わしい実体・外部変換器の説明。空なら異常なし (または読めなかった)
     */
    public static List<String> scanInstrumentation(boolean mayAttach) {
        List<String> findings = new ArrayList<>();

        Instrumentation self = MethodDisabler.instrumentationOrNull();
        if (self == null) {
            if (!mayAttach || MethodDisabler.ensureReady() != null) {
                return findings; // 自前の実体が無ければクラス一覧すら得られない。黙る
            }
            self = MethodDisabler.instrumentationOrNull();
            if (self == null) {
                return findings;
            }
        }

        Object unsafe = SelfAttach.unsafeOrNull();
        if (unsafe == null) {
            return findings; // Unsafe が取れない環境では内部を読めない。黙る
        }

        IdentityHashMap<Instrumentation, String> foreign = discoverForeign(unsafe, self);
        for (Map.Entry<Instrumentation, String> entry : foreign.entrySet()) {
            String holder = entry.getValue();
            List<ClassFileTransformer> transformers = managerTransformers(unsafe, entry.getKey());
            Set<String> foreignNames = new LinkedHashSet<>();
            for (ClassFileTransformer transformer : transformers) {
                String name = transformer.getClass().getName();
                if (!GuardContext.isInfrastructure(name) && !isKnownGood(name)) {
                    foreignNames.add(name);
                }
            }

            boolean holderSuspicious = !GuardContext.isInfrastructure(holder);
            if (!holderSuspicious && foreignNames.isEmpty()) {
                // 基盤が保持し、外部変換器も載っていない = 基盤自身の正規 agent。無害なので黙る
                continue;
            }

            StringBuilder line = new StringBuilder();
            if (holderSuspicious) {
                line.append("外部 agent の Instrumentation を確保: ").append(holder)
                        .append(" が保持しています (別 agent の実体)");
            } else {
                line.append("基盤の Instrumentation (").append(holder)
                        .append(") に外部から変換器が差し込まれています");
            }
            line.append("。変換器 ").append(transformers.size()).append(" 個");
            if (!foreignNames.isEmpty()) {
                line.append("、うち基盤・自分以外 ").append(foreignNames.size())
                        .append(" 個: ").append(String.join(", ", foreignNames));
                line.append(" (剥がすには /tpsthings damage agentstrip <クラス名>)");
            }
            findings.add(line.toString());
        }
        return findings;
    }

    /**
     * 掴んだ敵の変換器を剥がして、書き換えられたクラスを retransform で元に戻す。
     *
     * <p><b>剥がす対象は人が名前で明示する。</b> 「読める ≠ 敵」— 軽量化 Mod のような
     * 正規の agent も同じように見えてしまい、名前だけでは区別できない。名指しでしか
     * 剥がさないことが唯一の安全弁になる。基盤・自分の変換器は名前で来ても拒否する。
     *
     * <p>手順は 2 段。(1) その変換器を持つ Instrumentation の {@code removeTransformer} で
     * 登録を外す — これで<b>今後の適用</b>が止まる。(2) 既にロード済みのクラスは書き換わった
     * ままなので、こちらの Instrumentation で {@code retransformClasses} し、元バイトから
     * 組み直す — 外した変換器はもう走らないので、相手の変更だけが消える。
     *
     * <p>限界を隠さない: 相手が変換器を<b>再登録・再アタッチ</b>すれば元に戻る (これは
     * 一発では終わらない競争)。また retransform 非対応で登録された変換は、既ロード分を
     * 戻せない (今後を止めるだけ)。索引直削除など変換器の外の経路には一切効かない。
     *
     * @param targetClassName 剥がす変換器のクラス名 (agentscan が表示するもの)
     * @param allClasses      retransform をエンティティに絞らず全クラスに広げるか
     * @return 実行結果の説明
     */
    public static List<String> stripTransformer(String targetClassName, boolean allClasses) {
        List<String> report = new ArrayList<>();
        if (targetClassName == null || targetClassName.isBlank()) {
            report.add("剥がす変換器のクラス名を指定してください (agentscan で名前が出ます)");
            return report;
        }
        if (GuardContext.isInfrastructure(targetClassName) || isKnownGood(targetClassName)) {
            report.add("基盤・自分・既知の正規ライブラリの変換器は剥がせません: " + targetClassName);
            return report;
        }

        Instrumentation self = MethodDisabler.instrumentationOrNull();
        if (self == null) {
            if (MethodDisabler.ensureReady() != null
                    || (self = MethodDisabler.instrumentationOrNull()) == null) {
                report.add("自前 agent を確保できないため剥がせません");
                return report;
            }
        }
        Object unsafe = SelfAttach.unsafeOrNull();
        if (unsafe == null) {
            report.add("Unsafe が取れないため内部を操作できません");
            return report;
        }

        int removed = 0;
        for (Map.Entry<Instrumentation, String> entry : discoverForeign(unsafe, self).entrySet()) {
            Instrumentation owner = entry.getKey();
            for (ClassFileTransformer transformer : managerTransformers(unsafe, owner)) {
                if (!transformer.getClass().getName().equals(targetClassName)) {
                    continue;
                }
                try {
                    if (owner.removeTransformer(transformer)) {
                        removed++;
                        report.add("外しました: " + targetClassName
                                + " (" + entry.getValue() + " の Instrumentation から)");
                    } else {
                        report.add("見つけたが removeTransformer が false を返しました: " + targetClassName);
                    }
                } catch (Throwable ex) {
                    report.add("removeTransformer で例外: "
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        }

        if (removed == 0) {
            report.add("一致する変換器が見つかりませんでした: " + targetClassName
                    + " (agentscan の表示と綴りが合っているか確認してください)");
            return report;
        }

        int[] counts = revert(self, allClasses);
        report.add("retransform: 対象 " + counts[0] + " クラス中 " + counts[1] + " 成功"
                + (counts[2] > 0 ? " / " + counts[2] + " 失敗" : "")
                + (allClasses ? " (全クラス)" : " (エンティティのみ)"));
        report.add("注意: 相手が再登録・再アタッチすれば元に戻る。retransform 非対応で"
                + "登録された変換は既ロード分を戻せない (今後の適用だけ止まる)");
        return report;
    }

    /**
     * <b>決定実験用</b>: 掴めた外部変換器を<b>全部</b>剥がし、全クラスを retransform する。
     *
     * <p>目的は「殺しの本線が変換器にあるのか、それとも変換器の外 (HP 直書き・索引削除) か」を
     * 一発で白黒つけること。これで kill が止まれば変換器が効いている、変わらなければ本線は
     * 変換器の外にあると確定できる。
     *
     * <p><b>巻き添え前提。</b> 軽量化 Mod など正規 agent の変換器も一緒に外れる。名前で
     * 区別できない以上これは避けられないので、常用ではなく実験の一手として使う。
     * 基盤・自分・既知の正規ライブラリだけは外さない。
     */
    public static List<String> stripAllForeign() {
        List<String> report = new ArrayList<>();

        Instrumentation self = MethodDisabler.instrumentationOrNull();
        if (self == null) {
            if (MethodDisabler.ensureReady() != null
                    || (self = MethodDisabler.instrumentationOrNull()) == null) {
                report.add("自前 agent を確保できないため剥がせません");
                return report;
            }
        }
        Object unsafe = SelfAttach.unsafeOrNull();
        if (unsafe == null) {
            report.add("Unsafe が取れないため内部を操作できません");
            return report;
        }

        Set<String> removedNames = new LinkedHashSet<>();
        int removed = 0;
        for (Map.Entry<Instrumentation, String> entry : discoverForeign(unsafe, self).entrySet()) {
            Instrumentation owner = entry.getKey();
            for (ClassFileTransformer transformer : managerTransformers(unsafe, owner)) {
                String name = transformer.getClass().getName();
                if (GuardContext.isInfrastructure(name) || isKnownGood(name)) {
                    continue; // 基盤・自分・既知正規は外さない
                }
                try {
                    if (owner.removeTransformer(transformer)) {
                        removed++;
                        removedNames.add(name);
                    }
                } catch (Throwable ex) {
                    report.add("removeTransformer 例外 (" + name + "): " + ex.getClass().getSimpleName());
                }
            }
        }

        if (removed == 0) {
            report.add("剥がせる外部変換器は見つかりませんでした (掴めていない可能性)");
            return report;
        }
        report.add("外した外部変換器 " + removed + " 個: " + String.join(", ", removedNames));

        int[] counts = revert(self, true);
        report.add("retransform: 対象 " + counts[0] + " クラス中 " + counts[1] + " 成功"
                + (counts[2] > 0 ? " / " + counts[2] + " 失敗" : "") + " (全クラス)");
        report.add("巻き添え注意: 正規 agent の変換器も外れています。相手が再登録すれば戻ります。"
                + "これで kill が止まらなければ、殺しの本線は変換器の外 (HP 直書き・索引削除) です");
        return report;
    }

    /** 書き換えられたクラスを元バイトから組み直す。{@code {対象数, 成功数, 失敗数}}。 */
    private static int[] revert(Instrumentation self, boolean allClasses) {
        List<Class<?>> targets = new ArrayList<>();
        for (Class<?> loaded : self.getAllLoadedClasses()) {
            if (loaded == null || !self.isModifiableClass(loaded)) {
                continue;
            }
            if (allClasses || Entity.class.isAssignableFrom(loaded)) {
                targets.add(loaded);
            }
        }
        int ok = 0;
        int fail = 0;
        // 1 つの変更不能クラスでバッチ全体が飛ばないよう小分けにし、失敗時は 1 個ずつ試す
        final int batch = 128;
        for (int i = 0; i < targets.size(); i += batch) {
            List<Class<?>> slice = targets.subList(i, Math.min(i + batch, targets.size()));
            try {
                self.retransformClasses(slice.toArray(new Class<?>[0]));
                ok += slice.size();
            } catch (Throwable t) {
                for (Class<?> one : slice) {
                    try {
                        self.retransformClasses(one);
                        ok++;
                    } catch (Throwable t2) {
                        fail++;
                    }
                }
            }
        }
        return new int[]{targets.size(), ok, fail};
    }

    /** 静的フィールドに保持された、自分以外の Instrumentation 実体を集める (holder は代表 1 つ)。 */
    private static IdentityHashMap<Instrumentation, String> discoverForeign(Object unsafe, Instrumentation self) {
        IdentityHashMap<Instrumentation, String> foreign = new IdentityHashMap<>();
        for (Class<?> owner : self.getAllLoadedClasses()) {
            if (owner == null || GuardContext.isOwnClass(owner.getName())) {
                continue;
            }
            Field[] fields;
            try {
                fields = owner.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())
                        || !Instrumentation.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object value = staticValue(unsafe, field);
                if (!(value instanceof Instrumentation instrumentation) || instrumentation == self) {
                    continue;
                }
                String holder = owner.getName() + "#" + field.getName();
                String prev = foreign.get(instrumentation);
                // 名指しの手がかりとしては、基盤より Mod 自身の holder の方が有用
                if (prev == null || (GuardContext.isInfrastructure(prev)
                        && !GuardContext.isInfrastructure(holder))) {
                    foreign.put(instrumentation, holder);
                }
            }
        }
        return foreign;
    }

    /**
     * 自己アタッチ許可フラグの素性を見る。
     *
     * 「今 true か」だけでは足りない — こちらの agent も true にするので、
     * それだけだと自分を指してしまう。SelfAttach が覚えている
     * 「自分で立てたか」「触る前から立っていたか」で切り分ける。
     */
    private static void checkAllowAttachSelf(List<String> findings) {
        // 一番強い証拠: こちらが触る前から (JVM 引数の説明も無く) 立っていた
        if (SelfAttach.foreignEnabledBeforeUs()) {
            findings.add("自己アタッチ許可 (ALLOW_ATTACH_SELF) が、こちらより先に立てられていました。"
                    + "別の Mod が起動最早期に Java Agent を自己アタッチした足跡です");
            return;
        }
        Boolean state = SelfAttach.allowAttachSelfState();
        if (state == null) {
            return; // 読めない環境では判断しない
        }
        // true なのに自分で立てておらず、JVM 引数でも説明がつかない = よそが立てた
        if (state && !SelfAttach.enabledByUs()
                && !isAllowedByJvmArg()) {
            findings.add("自己アタッチ許可 (ALLOW_ATTACH_SELF) が、こちらの操作以外で有効になっています。"
                    + "別の Mod が Java Agent を自己アタッチしようとした痕跡です");
        }
    }

    private static boolean isAllowedByJvmArg() {
        String value = System.getProperty("jdk.attach.allowAttachSelf");
        return value != null && (value.isEmpty() || Boolean.parseBoolean(value));
    }

    /**
     * ModLauncher に差し込まれた LaunchPlugin を調べる。
     *
     * Forbidden Things は {@code Launcher.launchPlugins} の Map に自作 LaunchPlugin を
     * 直接 put して、全クラスの変換フェーズに割り込む。正規の LaunchPlugin は
     * Forge / ModLauncher / Mixin の基盤パッケージから来るので、それ以外の素性の
     * クラスがここに居れば、coremod ないし agent 系の Mod を疑ってよい。
     */
    private static void checkLaunchPlugins(List<String> findings) {
        try {
            Class<?> launcherClass = Class.forName("cpw.mods.modlauncher.Launcher");
            Object launcher = launcherClass.getField("INSTANCE").get(null);
            Field pluginsField = launcherClass.getDeclaredField("launchPlugins");
            pluginsField.setAccessible(true);
            Object handler = pluginsField.get(launcher);

            Field mapField = handler.getClass().getDeclaredField("plugins");
            mapField.setAccessible(true);
            if (!(mapField.get(handler) instanceof Map<?, ?> plugins)) {
                return;
            }
            for (Map.Entry<?, ?> entry : plugins.entrySet()) {
                Object plugin = entry.getValue();
                if (plugin == null) {
                    continue;
                }
                String pluginClass = plugin.getClass().getName();
                if (GuardContext.isInfrastructure(pluginClass) || isKnownGood(pluginClass)) {
                    continue;
                }
                findings.add("素性の知れない LaunchPlugin \"" + entry.getKey() + "\" ("
                        + pluginClass + ") が ModLauncher に差し込まれています。"
                        + "全クラスのバイトコードに割り込める位置です");
            }
        } catch (Throwable t) {
            // モジュール境界などで読めないことがある。読めない = 異常ではないので黙る
        }
    }

    private static boolean isKnownGood(String className) {
        for (String prefix : KNOWN_GOOD_PLUGINS) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code jdk.internal.reflect.Reflection} のフィルタ汚染を見る (best-effort)。
     *
     * Forbidden Things の denyReflection は、このフィルタに自 Mod のクラスを載せて
     * 他 Mod からの reflection を弾く。裏を返せば、<b>こちらのクラスがフィルタに
     * 載っていたら</b>、こちらの検査・修復を封じにきている Mod が居る。
     *
     * <p>ただし {@code jdk.internal.reflect} は普通に閉じているので、多くの環境では
     * そもそもこのフィールドを読めない。読めなければ黙る (できないことを異常とは言わない)。
     */
    private static void checkReflectionFilter(List<String> findings) {
        try {
            Class<?> reflection = Class.forName("jdk.internal.reflect.Reflection");
            Field field = reflection.getDeclaredField("fieldFilterMap");
            field.setAccessible(true);
            if (!(field.get(null) instanceof Map<?, ?> map)) {
                return;
            }
            for (Object key : map.keySet()) {
                if (key instanceof Class<?> clazz
                        && GuardContext.isOwnClass(clazz.getName())) {
                    findings.add("自分のクラス " + clazz.getName() + " に reflection フィルタが"
                            + "掛けられています。こちらの検査・修復を封じようとする Mod "
                            + "(Forbidden Things 型) の痕跡です");
                }
            }
        } catch (Throwable t) {
            // jdk.internal.reflect は普通は開いていない。読めないのが通常なので黙る
        }
    }

    /**
     * agent の {@code InstrumentationImpl} が抱える変換器<b>オブジェクト</b>を集める。
     *
     * <p>{@code InstrumentationImpl} 内の変換器管理 ({@code TransformerManager}) と、その中の
     * 変換器配列はフィールド名が JDK 実装依存なので、<b>名前ではなく型で辿る</b> —
     * 値のクラスが {@code sun.instrument.TransformerManager} のフィールド、その中の配列、
     * 配列要素 (もしくはその中) の {@link ClassFileTransformer}、という順に見る。
     *
     * <p>名前を数えるだけでなく実体を返すのは、剥がす ({@code removeTransformer}) に
     * この同じオブジェクトを渡す必要があるため。読めなければ空リスト。
     */
    private static List<ClassFileTransformer> managerTransformers(Object unsafe, Object instrumentation) {
        List<ClassFileTransformer> result = new ArrayList<>();
        try {
            for (Field managerField : instrumentation.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(managerField.getModifiers()) || managerField.getType().isPrimitive()) {
                    continue;
                }
                Object manager = instanceValue(unsafe, instrumentation, managerField);
                if (manager == null
                        || !"sun.instrument.TransformerManager".equals(manager.getClass().getName())) {
                    continue;
                }
                for (Field arrayField : manager.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(arrayField.getModifiers()) || arrayField.getType().isPrimitive()) {
                        continue;
                    }
                    Object array = instanceValue(unsafe, manager, arrayField);
                    if (array == null || !array.getClass().isArray()) {
                        continue;
                    }
                    int length = Array.getLength(array);
                    for (int i = 0; i < length; i++) {
                        Object info = Array.get(array, i);
                        ClassFileTransformer transformer = extractTransformer(unsafe, info);
                        if (transformer != null) {
                            result.add(transformer);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // 内部が読めなければそこまで。集まった分だけ返す
        }
        return result;
    }

    /** 配列要素から実際の変換器を取り出す。要素自体が変換器の JDK もあれば、包み (TransformerInfo) の JDK もある。 */
    private static ClassFileTransformer extractTransformer(Object unsafe, Object info) {
        if (info == null) {
            return null;
        }
        if (info instanceof ClassFileTransformer direct) {
            return direct;
        }
        for (Field field : info.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                continue;
            }
            if (instanceValue(unsafe, info, field) instanceof ClassFileTransformer transformer) {
                return transformer;
            }
        }
        return null;
    }

    /** 静的参照フィールドの値を Unsafe オフセット経由で読む (跨モジュールでも弾かれない)。 */
    private static Object staticValue(Object unsafe, Field field) {
        try {
            Object base = invoke(unsafe, "staticFieldBase", new Class<?>[]{Field.class}, field);
            long offset = (long) invoke(unsafe, "staticFieldOffset", new Class<?>[]{Field.class}, field);
            return invoke(unsafe, "getObject",
                    new Class<?>[]{Object.class, long.class}, base, offset);
        } catch (Throwable t) {
            return null;
        }
    }

    /** インスタンス参照フィールドの値を Unsafe オフセット経由で読む。 */
    private static Object instanceValue(Object unsafe, Object owner, Field field) {
        try {
            long offset = (long) invoke(unsafe, "objectFieldOffset", new Class<?>[]{Field.class}, field);
            return invoke(unsafe, "getObject",
                    new Class<?>[]{Object.class, long.class}, owner, offset);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... args)
            throws Exception {
        return target.getClass().getMethod(method, types).invoke(target, args);
    }
}
