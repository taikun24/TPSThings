package jp.main.taikun.tpsthings.damage;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

/**
 * 自己アタッチした agent の入口。
 *
 * これは一時 jar 経由でシステムクラスローダに読まれるので、Mod 側のクラスとは
 * 別インスタンスになる。よって Mod のクラスを直接参照できず、
 * ロード済みクラス一覧から受け取り口を探して reflection で渡す。
 */
public final class AgentEntryPoint {

    private static final String RECEIVER = "jp.main.taikun.tpsthings.damage.MethodDisabler";
    private static final String RECEIVER_METHOD = "acceptInstrumentation";

    private AgentEntryPoint() {
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (!RECEIVER.equals(loaded.getName())) {
                continue;
            }
            try {
                Method receiver = loaded.getDeclaredMethod(RECEIVER_METHOD, Instrumentation.class);
                receiver.setAccessible(true);
                receiver.invoke(null, instrumentation);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
            return;
        }
    }

    public static void premain(String args, Instrumentation instrumentation) {
        agentmain(args, instrumentation);
    }
}
