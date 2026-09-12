package jp.main.taikun.tpsthings.damage;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 実行中の JVM 上で、指定した署名のメソッドを不発にする。
 *
 * 呼び出し口を塞ぐのではなく、呼び出す側のメソッドそのものを潰す。
 * 「書き込みを弾かれたか読み戻して確認し、駄目なら別手段で書く」という作りに対しては
 * 塞ぐ側が必ず負けるので、そういう相手にはこちらを使う。
 *
 * <p><b>本体を丸ごと捨てて既定値を返すだけにする。条件は付けない。</b>
 * 一度は「保護対象が絡むときだけ引き返す」形にしたが、それだと本体は最後まで走るので、
 * <b>関所を通らない経路を持つ相手には何も起きない</b>。呼ばれた事実そのものを消す
 * この形でしか止まらない手が実際にある。
 *
 * <p>そのぶん乱暴で、指定したメソッドは<b>世界中のあらゆる相手に対して</b>死ぬ。
 * だから<b>何を潰すかの選別が全ての責任を負う</b>。バニラの中枢を指定すれば世界が壊れる。
 * 自動でここまで進むのは、block で止まらなかったことを実測で確かめた段だけ。
 *
 * 対象は署名の文字列としてのみ扱う。何を潰すかはコマンドから与えられる。
 */
public final class MethodDisabler {

    /** クラス名 → 無効化するメソッド名の集合。 */
    private static final Map<String, Set<String>> DISABLED = new ConcurrentHashMap<>();

    private static volatile Instrumentation instrumentation;
    private static volatile boolean transformerRegistered;

    private MethodDisabler() {
    }

    /** agent 側から reflection で呼ばれる受け取り口。 */
    public static void acceptInstrumentation(Instrumentation received) {
        instrumentation = received;
    }

    public static boolean isReady() {
        return instrumentation != null;
    }

    /**
     * Instrumentation を確保する。まだなら自己アタッチを試みる。
     *
     * @return 失敗理由。成功なら null。
     */
    public static synchronized String ensureReady() {
        if (instrumentation != null) {
            return null;
        }
        String failure = SelfAttach.attach();
        if (failure != null) {
            return failure;
        }
        if (instrumentation == null) {
            return "アタッチは成功したが Instrumentation を受け取れていない";
        }
        return null;
    }

    /**
     * 署名 {@code クラス名#メソッド名} のメソッドを無効化する。
     * 同名オーバーロードは区別せず、まとめて潰す。
     *
     * @return 失敗理由。成功なら null。
     */
    public static synchronized String disable(String signature) {
        if (DamageGuard.isOwn(signature)) {
            // 守るための仕組みを潰したら、そこから先は無防備で戦うことになる。
            // しかも「効かなかったので 1 段外側へ」と進んでいくので、気づく機会も無い
            return "自分の関所は無効化できません: " + signature;
        }
        return apply(signature, true);
    }

    /** 無効化を解除して元のバイトコードに戻す。 */
    public static synchronized String restore(String signature) {
        return apply(signature, false);
    }

    private static String apply(String signature, boolean disable) {
        int separator = signature.indexOf('#');
        if (separator <= 0 || separator == signature.length() - 1) {
            return "署名の形式が違う: " + signature + " (クラス名#メソッド名)";
        }
        String className = signature.substring(0, separator);
        String methodName = signature.substring(separator + 1);

        String failure = ensureReady();
        if (failure != null) {
            return failure;
        }
        registerTransformer();

        if (disable) {
            String unsafe = returnsReference(className, methodName);
            if (unsafe != null) {
                return unsafe;
            }
            DISABLED.computeIfAbsent(className, key -> ConcurrentHashMap.newKeySet()).add(methodName);
        } else {
            Set<String> methods = DISABLED.get(className);
            if (methods != null) {
                methods.remove(methodName);
                if (methods.isEmpty()) {
                    DISABLED.remove(className);
                }
            }
        }

        Class<?> loaded = findLoaded(className);
        if (loaded == null) {
            // 未ロードなら、ロード時に変換器が拾うので登録だけで足りる
            // (参照を返すメソッドは、そのとき変換器の側が避ける)
            return null;
        }
        if (!instrumentation.isModifiableClass(loaded)) {
            return "このクラスは変更できない: " + className;
        }
        try {
            instrumentation.retransformClasses(loaded);
        } catch (Throwable t) {
            return "retransform に失敗: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        return null;
    }

    private static void registerTransformer() {
        if (transformerRegistered) {
            return;
        }
        instrumentation.addTransformer(new NoOpTransformer(), true);
        transformerRegistered = true;
    }

    /** 同じ Instrumentation を他の変換器 ({@link ReaderGuard}) と共用する。 */
    static Instrumentation instrumentationOrNull() {
        return instrumentation;
    }

    /** ロード済みクラス一覧。走査系がバイトコードを見に行くために使う。 */
    static Class<?>[] loadedClasses() {
        if (instrumentation == null && ensureReady() != null) {
            return new Class<?>[0];
        }
        return instrumentation.getAllLoadedClasses();
    }

    /**
     * 参照 (オブジェクト・配列) を返すメソッドか。返すなら、潰せない理由を文字列で返す。
     *
     * <p>本体を捨てる手は「呼ばれた事実を消す」ためのものだが、<b>戻り値まで消せるわけではない</b>。
     * 参照を返すメソッドを空にすると null が返り、呼び出し側はそれを使って落ちる。
     * 守るつもりで相手の Mod を落とすのは、守れていないのと同じ
     * (自動クリッカーの処理を潰して NPE で世界ごと落とした実例がある)。
     *
     * <p>潰せないと分かれば、呼び出し側 (自動対処) は block で代用する。
     */
    private static String returnsReference(String className, String methodName) {
        Class<?> owner = findLoaded(className);
        if (owner == null) {
            return null; // まだ読まれていない。判断は変換器の側に委ねる
        }
        try {
            for (java.lang.reflect.Method method : owner.getDeclaredMethods()) {
                Class<?> type = method.getReturnType();
                if (method.getName().equals(methodName) && !type.isPrimitive()) {
                    return "参照 (" + type.getSimpleName() + ") を返すメソッドは空にできません"
                            + " — null が返って呼び出し側が落ちます";
                }
            }
        } catch (Throwable unreadable) {
            // 読めないなら変換器の側の判断に任せる
        }
        return null;
    }

    private static Class<?> findLoaded(String className) {
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (className.equals(loaded.getName())) {
                return loaded;
            }
        }
        return null;
    }

    /** いま無効化されている署名の一覧。 */
    public static Set<String> disabled() {
        Set<String> all = new LinkedHashSet<>();
        DISABLED.forEach((className, methods) ->
                methods.forEach(method -> all.add(className + "#" + method)));
        return all;
    }

    // ---- 変換本体 ---------------------------------------------------------------

    private static final class NoOpTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String internalName, Class<?> beingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (internalName == null) {
                return null;
            }
            Set<String> methods = DISABLED.get(internalName.replace('/', '.'));
            if (methods == null || methods.isEmpty()) {
                return null;
            }
            try {
                return rewrite(classfileBuffer, methods);
            } catch (Throwable t) {
                // 変換に失敗したら元のバイトコードを使わせる。壊れた class を返すよりましなので。
                return null;
            }
        }

        private byte[] rewrite(byte[] original, Set<String> methods) {
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);

            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!methods.contains(method.name)) {
                    continue;
                }
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    continue;
                }
                // 初期化子は頭に return を置けない (super の呼び出し前に this を触れない)。
                // そもそも生成そのものを潰すのは、守るのではなく世界を壊す手
                if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
                    continue;
                }
                // 参照を返すメソッドは空にすると null を返す。呼び出し側はそれを使って落ちる。
                // 呼ばれた事実は消せても、戻り値の約束までは消せない
                int sort = Type.getReturnType(method.desc).getSort();
                if (sort == Type.OBJECT || sort == Type.ARRAY) {
                    continue;
                }
                empty(method);
                changed = true;
            }
            if (!changed) {
                return null;
            }

            // COMPUTE_FRAMES は共通スーパークラスの解決でクラスロードを起こすため使わない。
            // 空にした本体は分岐が無いので frame も要らない。
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }

        /** 本体を丸ごと捨てて、既定値を返すだけにする。理由はクラスの Javadoc に。 */
        private void empty(MethodNode method) {
            Type returnType = Type.getReturnType(method.desc);

            method.instructions.clear();
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;

            int constant = defaultValueOpcode(returnType);
            if (constant != -1) {
                method.instructions.add(new InsnNode(constant));
            }
            method.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
            method.maxStack = Math.max(1, returnType.getSize());
        }

        /** 戻り値の既定値を積む命令。void なら -1。 */
        private int defaultValueOpcode(Type returnType) {
            return switch (returnType.getSort()) {
                case Type.VOID -> -1;
                case Type.LONG -> Opcodes.LCONST_0;
                case Type.FLOAT -> Opcodes.FCONST_0;
                case Type.DOUBLE -> Opcodes.DCONST_0;
                case Type.OBJECT, Type.ARRAY -> Opcodes.ACONST_NULL;
                default -> Opcodes.ICONST_0;
            };
        }
    }
}
