package jp.main.taikun.tpsthings.damage;

import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * 関所群が共有する横断的な判定の置き場。
 *
 * <p>「保護対象か」「封印中か」「自分のコードか」といった判定は、以前は各クラスが
 * 自前のコピーを持っていた。コピーは中身が少しずつ食い違い、片方にしか効かない
 * 除外や、片方だけ通る抜け道を作る。判定はここの 1 実装だけにして、各関所は
 * 呼ぶだけにする。
 *
 * <p>状態そのもの (保護一覧・封印フラグ) の持ち主は今まで通り
 * {@link AutoGuard} / {@link HealthGuard}。ここは判定の合成だけを担う。
 */
public final class GuardContext {

    /** 浮動小数の誤差で HP 減少と誤認しないための下限。 */
    public static final float EPSILON = 1.0E-3F;

    /** この Mod 自身のコード。 */
    private static final String OWN_PREFIX = "jp.main.taikun.";

    /**
     * 基盤側のコード。バニラ・Forge・JDK・起動基盤・バニラが同梱するライブラリ。
     *
     * <p>自動対処の除外 (潰すとゲームより先に基盤が壊れる)、走査の雑音除去、
     * 「外から持ち込まれた型か」の判定が、全部この 1 つのリストを見る。
     * 以前は 3 つのコピーが微妙に違う中身で並んでいて、片方に足した除外が
     * 他方に効かない構造だった。
     *
     * <p>ここに載る = 容疑者にしない、なので<b>広い分には安全側</b>に倒れる。
     */
    private static final List<String> PLATFORM_PREFIXES = List.of(
            "net.minecraft.",
            "net.minecraftforge.",
            "java.",
            "jdk.",
            "sun.",
            "com.sun.",
            "com.mojang.",
            "org.spongepowered.",
            "cpw.mods.",
            // バニラ同梱のライブラリ
            "it.unimi.",
            "org.joml.",
            "org.apache.",
            "io.netty.",
            "org.lwjgl.",
            "oshi.",
            // ASM (自分の変換基盤)
            "org.objectweb.",
            // 起動基盤。ここまで遡ると、ゲームではなくランチャーを潰しにいく
            "org.prismlauncher.",
            "io.github.zekerzhayard.",
            "net.fabricmc."
    );

    private GuardContext() {
    }

    /** この Mod 自身のクラス (または署名) か。 */
    public static boolean isOwnClass(String name) {
        return name.startsWith(OWN_PREFIX);
    }

    /**
     * 基盤側 (バニラ / Forge / JDK / 起動基盤 / この Mod 自身) のクラスか。
     *
     * 容疑者リストから除く判定。クラス名でも {@code クラス名#メソッド名} の署名でも使える
     * (接頭辞一致なので後ろに何が付いていても同じ)。
     * ただし<b>名前だけの判定</b>であることに注意 — Mixin で持ち込まれた中身は
     * バニラの名前を着るので、出所 ({@link DamageGuard#mixinOwner}) と併用すること。
     */
    public static boolean isInfrastructure(String name) {
        if (isOwnClass(name)) {
            return true;
        }
        for (String prefix : PLATFORM_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 封印相当の保護が効いているか。
     *
     * 明示の封印 ({@code seal}) だけでなく、書き戻しが効いている間も死亡・削除の
     * 関所は同じ扱いで拒否する。書き戻しは tick の終わりに走るので、tick の途中で
     * 死なれるとそこで終わってしまうため。
     */
    public static boolean isSealedOrReverting() {
        return HealthGuard.isSealed() || AutoGuard.isReverting();
    }

    /**
     * この相手への攻撃的な操作を関所が守るべきか。
     *
     * 保護対象であり、かつ自分 (この Mod) の操作の最中でないこと。
     * 自分の復帰処理などを拒否すると、対処が対処を呼ぶ。
     */
    public static boolean guardsAgainst(Entity victim) {
        return !DamageGuard.isSelfAction() && AutoGuard.isProtected(victim);
    }

    /** サーバ側の実体か。クライアント側はサーバの結果を映しているだけなので関所は見ない。 */
    public static boolean onServer(Entity entity) {
        return entity.level() != null && !entity.level().isClientSide();
    }
}
