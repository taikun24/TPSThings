package jp.main.taikun.tpsthings.damage;

import com.sun.tools.attach.VirtualMachine;
import jp.main.taikun.tpsthings.Tpsthings;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * 動作中の JVM に自分自身を agent としてアタッチし、{@link java.lang.instrument.Instrumentation} を取る。
 *
 * Java 9 以降、自己アタッチは既定で禁止されている。JVM 引数を足さずに済ませるため、
 * 判定に使われる {@code HotSpotVirtualMachine.ALLOW_ATTACH_SELF} を Unsafe で直接書き換える。
 * agent jar はビルド成果物として持たず、自分の中の {@link AgentEntryPoint} を
 * 一時 jar に詰め直して使う。
 */
final class SelfAttach {

    private static final String AGENT_CLASS = AgentEntryPoint.class.getName();
    private static final String AGENT_RESOURCE = "AgentEntryPoint.class";
    private static final String HOTSPOT_VM = "sun.tools.attach.HotSpotVirtualMachine";
    private static final String ALLOW_SELF_FIELD = "ALLOW_ATTACH_SELF";

    /** 自己アタッチ許可フラグを自分で立てたか。敵の agent と自分を区別するのに使う。 */
    private static volatile boolean enabledByUs = false;
    /**
     * こちらが触る前から許可フラグが立っていたか。
     *
     * 既定は false (JVM 引数 {@code -Djdk.attach.allowAttachSelf} が無い限り)。
     * 触る前から true なら、先に別の誰かが折っている = 外部 agent の足跡。
     * ただし JVM 引数で明示的に許可されている場合はこの限りではないので、そこは除く。
     */
    private static volatile boolean foreignEnabledBeforeUs = false;

    private SelfAttach() {
    }

    /** 自己アタッチ許可を自分で立てたか。 */
    static boolean enabledByUs() {
        return enabledByUs;
    }

    /** こちらが触る前から (かつ JVM 引数の説明もつかず) 許可が立っていたか。 */
    static boolean foreignEnabledBeforeUs() {
        return foreignEnabledBeforeUs;
    }

    /** {@code ALLOW_ATTACH_SELF} の今の値。読めなければ null。 */
    static Boolean allowAttachSelfState() {
        if (!UnsafeSwitch.isEnabled()) {
            return null; // 読むにも Unsafe が要る。切ってあるなら読めないのと同じ
        }
        try {
            Class<?> hotspotVm = Class.forName(HOTSPOT_VM);
            Field allowSelf = hotspotVm.getDeclaredField(ALLOW_SELF_FIELD);
            Object unsafe = theUnsafe();
            Object base = unsafe.getClass()
                    .getMethod("staticFieldBase", Field.class).invoke(unsafe, allowSelf);
            long offset = (long) unsafe.getClass()
                    .getMethod("staticFieldOffset", Field.class).invoke(unsafe, allowSelf);
            return (boolean) unsafe.getClass()
                    .getMethod("getBoolean", Object.class, long.class).invoke(unsafe, base, offset);
        } catch (Throwable t) {
            return null;
        }
    }

    /** JVM 引数 {@code jdk.attach.allowAttachSelf} が自己アタッチを許可しているか。 */
    private static boolean allowedByJvmArg() {
        String value = System.getProperty("jdk.attach.allowAttachSelf");
        return value != null && (value.isEmpty() || Boolean.parseBoolean(value));
    }

    /**
     * アタッチを試みる。すでに済んでいれば何もしない。
     *
     * @return 失敗理由。成功なら null。
     */
    static String attach() {
        if (!UnsafeSwitch.isEnabled()) {
            return UnsafeSwitch.REFUSAL;
        }
        try {
            allowSelfAttach();
        } catch (Throwable t) {
            return "自己アタッチの許可に失敗: " + describe(t);
        }

        File agentJar;
        try {
            agentJar = writeAgentJar();
        } catch (Throwable t) {
            return "agent jar の生成に失敗: " + describe(t);
        }

        try {
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
            try {
                vm.loadAgent(agentJar.getAbsolutePath());
            } finally {
                vm.detach();
            }
        } catch (Throwable t) {
            return "agent のアタッチに失敗: " + describe(t);
        }
        return null;
    }

    /**
     * 自己アタッチ禁止フラグを折る。
     *
     * setAccessible では静的 final を書けないので Unsafe のオフセット経由で叩く。
     */
    private static void allowSelfAttach() throws Exception {
        Class<?> hotspotVm = Class.forName(HOTSPOT_VM);
        Field allowSelf = hotspotVm.getDeclaredField(ALLOW_SELF_FIELD);
        Object unsafe = theUnsafe();

        Object base = unsafe.getClass()
                .getMethod("staticFieldBase", Field.class)
                .invoke(unsafe, allowSelf);
        long offset = (long) unsafe.getClass()
                .getMethod("staticFieldOffset", Field.class)
                .invoke(unsafe, allowSelf);

        // 触る前の値を控える。既定 false のはずのフラグが、JVM 引数の説明も無く
        // 既に立っているなら、先に別の agent が折っている
        boolean before = (boolean) unsafe.getClass()
                .getMethod("getBoolean", Object.class, long.class)
                .invoke(unsafe, base, offset);
        if (before && !allowedByJvmArg()) {
            foreignEnabledBeforeUs = true;
        }

        unsafe.getClass()
                .getMethod("putBoolean", Object.class, long.class, boolean.class)
                .invoke(unsafe, base, offset, true);
        enabledByUs = true;
    }

    private static Object theUnsafe() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return field.get(null);
    }

    /**
     * 共有の {@code sun.misc.Unsafe}。取れなければ null。
     *
     * 敵の {@code InstrumentationImpl} の内部フィールドは跨モジュールで
     * {@code setAccessible} が弾かれるため、{@link AttachGuard} が
     * オフセット経由で読むのに使う。
     */
    static Object unsafeOrNull() {
        if (!UnsafeSwitch.isEnabled()) {
            return null;
        }
        try {
            return theUnsafe();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 自分の中の agent クラスを、マニフェスト付きの一時 jar に詰め直す。 */
    private static File writeAgentJar() throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Agent-Class", AGENT_CLASS);
        attributes.putValue("Premain-Class", AGENT_CLASS);
        attributes.putValue("Can-Retransform-Classes", "true");
        attributes.putValue("Can-Redefine-Classes", "true");

        File jar = File.createTempFile(Tpsthings.MODID + "_agent", ".jar");
        jar.deleteOnExit();

        try (InputStream source = SelfAttach.class.getResourceAsStream(AGENT_RESOURCE)) {
            if (source == null) {
                throw new IllegalStateException(AGENT_RESOURCE + " が自分の jar から読めない");
            }
            try (JarOutputStream out = new JarOutputStream(new java.io.FileOutputStream(jar), manifest)) {
                out.putNextEntry(new JarEntry(AGENT_CLASS.replace('.', '/') + ".class"));
                copy(source, out);
                out.closeEntry();
            }
        }
        return jar;
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
