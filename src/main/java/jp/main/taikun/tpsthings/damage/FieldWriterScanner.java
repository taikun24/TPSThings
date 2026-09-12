package jp.main.taikun.tpsthings.damage;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 「このクラスのフィールドを直接書き換えているメソッドは誰か」をバイトコードから探す。
 *
 * 絞り所はメソッド呼び出ししか捕まえられない。setter を通さず putfield で直接書く相手には
 * Mixin が原理的に効かないので、書いている側を静的に見つけて {@link MethodDisabler} に渡す。
 *
 * 探すのはフィールドの持ち主クラスだけで、何を探すかは呼び出し側が文字列で与える。
 */
public final class FieldWriterScanner {

    private FieldWriterScanner() {
    }

    /** 1 件の書き込み箇所。 */
    public record Write(String writer, String field) {
        @Override
        public String toString() {
            return writer + " → " + field;
        }
    }

    /**
     * {@code ownerClassName} のフィールドに直接書いているメソッドを列挙する。
     *
     * @param ownerClassName 書き込まれる側のクラス (例: net.minecraft.world.entity.Entity)
     * @param limit          返す最大件数
     */
    public static List<Write> scan(String ownerClassName, int limit) {
        String owner = ownerClassName.replace('.', '/');
        List<Write> found = new ArrayList<>();

        for (Class<?> candidate : MethodDisabler.loadedClasses()) {
            if (found.size() >= limit) {
                break;
            }
            // バニラや JDK 自身の書き込みは当たり前すぎて雑音にしかならない
            if (GuardContext.isInfrastructure(candidate.getName())) {
                continue;
            }
            byte[] bytecode = bytecodeOf(candidate);
            if (bytecode == null) {
                continue;
            }
            collectWrites(bytecode, owner, found, limit);
        }
        return found;
    }

    /** クラス自身のローダから .class を読み直す。retransform 前の姿で構わない。 */
    private static byte[] bytecodeOf(Class<?> candidate) {
        String resource = "/" + candidate.getName().replace('.', '/') + ".class";
        try (InputStream in = candidate.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void collectWrites(byte[] bytecode, String owner, List<Write> found, int limit) {
        ClassNode node = new ClassNode();
        try {
            new ClassReader(bytecode).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        } catch (Throwable malformed) {
            return;
        }
        String writerClass = node.name.replace('/', '.');

        for (MethodNode method : node.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn.getOpcode() != Opcodes.PUTFIELD && insn.getOpcode() != Opcodes.PUTSTATIC) {
                    continue;
                }
                FieldInsnNode write = (FieldInsnNode) insn;
                if (!owner.equals(write.owner)) {
                    continue;
                }
                Write entry = new Write(writerClass + "#" + method.name, write.name);
                if (!found.contains(entry)) {
                    found.add(entry);
                }
                if (found.size() >= limit) {
                    return;
                }
                break; // 同じメソッドの 2 件目以降は要らない
            }
        }
    }
}
