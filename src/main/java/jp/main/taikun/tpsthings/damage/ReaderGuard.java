package jp.main.taikun.tpsthings.damage;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生死を答える読み出しを、同期データから導く正規の実装に戻す。
 *
 * <p>HP を 1 も減らさずに殺す最有力の手は、{@code getHealth()} / {@code isAlive()} /
 * {@code isDeadOrDying()} の<b>本体を書き換えて世界に嘘を教える</b>こと。値はどこも
 * 壊れていないので、値を見張るどの関所にも原理的に映らない。書き戻しも効かない —
 * 戻すべき値が最初から減っていないため。
 *
 * <p>そこで守るのは値ではなく<b>バニラの不変条件</b>: 「この 3 つの答えは
 * {@code DATA_HEALTH_ID} の同期データから導かれる」。これを満たす本体を retransform で
 * 上書きし直す。誰がどう書き換えていようと、後から登録した変換器が最後に走るので、
 * 最終的な本体はこちらのものになる。相手のコードにも状態にも触らない。
 *
 * <p><b>効かせるのは保護対象だけ。</b>この 3 つはバニラの中枢で、全部の生き物が毎 tick
 * 何度も通る。一律に正規化すると、生死に自前の仕組みを載せている Mod からその仕組みを
 * 丸ごと奪って、世界の側が壊れる (死体が消えない・誰も倒せない)。<b>元の本体には
 * 一切触らず</b>、頭に「保護対象なら正規の答えを返す」関所を挿すだけにする。
 * 保護対象以外は、届いた時点の本体 (他所の書き換え込み) をそのまま通る。
 *
 * <p><b>制約: retransform では本体の中身しか変えられない。</b>メソッドやフィールドを
 * 1 つでも増やす / 減らす / 名前や型を変えると、その瞬間に
 * {@code UnsupportedOperationException} で<b>変換そのものが丸ごと拒否される</b>。
 * しかも拒否されるのは登録した変換器を通る<b>全ての retransform</b> なので、
 * ここでメソッドを足すと {@link RepairGuard} の張り直しまで道連れに死ぬ。
 * 元の本体を別名で退避する作りが取れないのはこのため。
 *
 * <p>disable ({@link MethodDisabler}) と同じ agent 基盤に相乗りする。
 * 対象はバニラの 3 メソッドだけで、特定の Mod の知識は持たない。
 */
public final class ReaderGuard {

    private static final String TARGET_CLASS = "net.minecraft.world.entity.LivingEntity";
    /** 変換後の本体が呼ぶ先。この クラス自身の internal name。 */
    private static final String OWNER = "jp/main/taikun/tpsthings/damage/ReaderGuard";
    private static final String LIVING_DESC = "Lnet/minecraft/world/entity/LivingEntity;";

    private static volatile boolean canonical = false;
    /** いま列に並んでいる変換器。切るときに外すために持っておく。 */
    private static volatile Canonicalizer active;
    /** 実行時のメソッド名 (開発環境では official 名、本番では SRG 名) → 差し替え先ヘルパ。 */
    private static volatile Map<String, String> targets;

    private ReaderGuard() {
    }

    public static boolean isCanonical() {
        return canonical;
    }

    /**
     * 正規化の入り切り。切ると次の retransform で元の (他所の書き換え込みの) 本体に戻る。
     *
     * @return 失敗理由。成功なら null。
     */
    public static synchronized String setCanonical(boolean value) {
        if (value == canonical) {
            return null;
        }
        String failure = MethodDisabler.ensureReady();
        if (failure != null) {
            return failure;
        }
        Instrumentation instrumentation = MethodDisabler.instrumentationOrNull();
        if (value && targets == null) {
            failure = resolveTargets();
            if (failure != null) {
                return failure;
            }
        }
        if (active == null) {
            active = new Canonicalizer();
            instrumentation.addTransformer(active, true);
        }
        canonical = value;
        try {
            instrumentation.retransformClasses(LivingEntity.class);
        } catch (Throwable t) {
            canonical = !value;
            return "retransform に失敗: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        GuardNotice.info("生死の読み出しを" + (value
                ? "保護対象だけ正規の実装に戻しました (getHealth / isAlive / isDeadOrDying)"
                : "元の本体に返しました"));
        return null;
    }

    /**
     * 3 メソッドの実行時の名前を SRG 経由で引く。
     *
     * 名前は環境で変わる (開発では official、本番では SRG)。SRG の番号はバニラの
     * メソッドの住所であって、特定の Mod の知識ではない。
     */
    private static String resolveTargets() {
        Map<String, String> resolved = new LinkedHashMap<>();
        String failure = resolve(resolved, "m_21223_", "canonicalHealth");   // getHealth()F
        if (failure == null) {
            failure = resolve(resolved, "m_6084_", "canonicalAlive");        // isAlive()Z
        }
        if (failure == null) {
            failure = resolve(resolved, "m_21224_", "canonicalDeadOrDying"); // isDeadOrDying()Z
        }
        if (failure != null) {
            return failure;
        }
        targets = resolved;
        return null;
    }

    private static String resolve(Map<String, String> resolved, String srgName, String helper) {
        try {
            Method method = ObfuscationReflectionHelper.findMethod(LivingEntity.class, srgName);
            resolved.put(method.getName(), helper);
            return null;
        } catch (Throwable t) {
            return "対象メソッドを特定できない: " + srgName + " (" + t.getMessage() + ")";
        }
    }

    // ---- 正規の実装 (変換後の本体から呼ばれる) ----------------------------------

    /**
     * この相手について、正規の実装で答えるか。
     *
     * <p><b>保護対象だけを正規化する。</b>この 3 つは全部の生き物が毎 tick 何度も通る
     * バニラの中枢で、ここを一律に書き換えるのは<b>世界全体のロボトミー</b>になる。
     * 生死の判定に自前の仕組みを載せている Mod は珍しくないので、一律に奪えば
     * その Mod の生死そのものが動かなくなる (死体が消えない・誰も倒せない)。
     *
     * <p>守りたいのは保護対象 1 体で、他の生き物の生死は他所の Mod の領分。
     * 保護対象以外は、書き換えられたままの本体をそのまま通す。
     */
    public static boolean shouldCanonicalize(LivingEntity living) {
        return AutoGuard.isProtected(living);
    }

    /**
     * getHealth() の正規の本体。
     *
     * 同期データを普通に読むだけ。読み出しの関所 (床の偽装) は同期データ側の
     * 絞り所が担っているので、ここで素通しにしても封印の偽装は壊れない。
     */
    public static float canonicalHealth(LivingEntity living) {
        EntityDataAccessor<Float> key = HealthGuard.healthKeyOrNull();
        if (key == null) {
            // 鍵は最初のエンティティ生成時に預かるので、ここへ来る前に必ずある。
            // 万一無いなら偽装のしようもないので、生きている扱いにして様子を見る
            return 1.0F;
        }
        return living.getEntityData().get(key);
    }

    /** isAlive() の正規の本体。バニラの定義そのまま: 削除されておらず HP が正。 */
    public static boolean canonicalAlive(LivingEntity living) {
        // isRemoved() も書き換えられる読み出しの 1 つ。正規の本体が嘘を経由したら意味がない
        return !HealthGuard.rawRemoved(living) && canonicalHealth(living) > 0.0F;
    }

    /** isDeadOrDying() の正規の本体。バニラの定義そのまま: HP が 0 以下。 */
    public static boolean canonicalDeadOrDying(LivingEntity living) {
        return canonicalHealth(living) <= 0.0F;
    }

    // ---- 変換本体 ---------------------------------------------------------------

    private static final class Canonicalizer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String internalName, Class<?> beingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (!canonical || internalName == null
                    || !TARGET_CLASS.equals(internalName.replace('/', '.'))) {
                return null;
            }
            Map<String, String> helpers = targets;
            if (helpers == null) {
                return null;
            }
            try {
                return rewrite(classfileBuffer, helpers);
            } catch (Throwable t) {
                // 壊れた class を返すよりは、正規化を諦めて元のまま通す方がまし
                GuardNotice.warn("読み出しの正規化に失敗: " + t);
                return null;
            }
        }

        /**
         * 3 メソッドの頭に「保護対象なら正規の答えを返す」関所を挿す。
         *
         * <p>元の本体には指一本触れない。ここに届いた時点のバイト列には先に並んでいる
         * 誰かの書き換えが既に入っているが、それは保護対象以外にそのまま通る。
         * 他所の Mod の生死の仕組みは一切壊れない。
         *
         * <p>メソッドの増減が許されないので、退避も新設もできない。<b>挿すだけ</b>が
         * retransform で取れる唯一の形になる。
         */
        private byte[] rewrite(byte[] original, Map<String, String> helpers) {
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);

            boolean touched = false;
            for (MethodNode method : node.methods) {
                String helper = helpers.get(method.name);
                // 同名の別物を巻き込まないよう、引数なしの getter だけに掛ける
                if (helper == null || !method.desc.startsWith("()")) {
                    continue;
                }
                // 本体が無いものには挿せない。static だと slot 0 が this ではない
                if ((method.access & (Opcodes.ACC_ABSTRACT
                        | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0) {
                    continue;
                }
                prepend(method, helper);
                touched = true;
            }
            if (!touched) {
                return null;
            }
            // COMPUTE_FRAMES は共通スーパークラスの解決でクラスロードを起こすので使わない。
            // 分岐は 1 つだけで、その合流点は局所変数も積みも入口と同じなので、
            // F_SAME を 1 枚自分で置けば足りる
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }

        /** {@code if (保護対象) return 正規の答え;} を本体の頭に挿す。 */
        private void prepend(MethodNode method, String helper) {
            Type returnType = Type.getReturnType(method.desc);

            InsnList prologue = new InsnList();
            LabelNode body = new LabelNode();
            prologue.add(new VarInsnNode(Opcodes.ALOAD, 0));
            prologue.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER,
                    "shouldCanonicalize", "(" + LIVING_DESC + ")Z", false));
            prologue.add(new JumpInsnNode(Opcodes.IFEQ, body));
            prologue.add(new VarInsnNode(Opcodes.ALOAD, 0));
            prologue.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, helper,
                    "(" + LIVING_DESC + ")" + returnType.getDescriptor(), false));
            prologue.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
            prologue.add(body);
            // 元の本体が既に先頭に frame を持っているなら、同じ位置に 2 枚置けない
            if (!startsWithFrame(method)) {
                prologue.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            }

            method.instructions.insert(prologue);
            // this 1 個 + 戻り値 (最大 2)
            method.maxStack = Math.max(method.maxStack, 2);
        }

        private boolean startsWithFrame(MethodNode method) {
            for (AbstractInsnNode node = method.instructions.getFirst();
                 node != null; node = node.getNext()) {
                if (node instanceof FrameNode) {
                    return true;
                }
                if (node instanceof LabelNode || node instanceof LineNumberNode) {
                    continue;
                }
                return false;
            }
            return false;
        }
    }
}
