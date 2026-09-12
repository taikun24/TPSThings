package jp.main.taikun.tpsthings.items;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * 「いま画面に映っている Mob」を集める。
 *
 * 当たり判定を経由しないので、相手がどれだけ速く動いていても取り逃がさない。
 * 集めて何をするかは呼び出し側が決める。
 */
public final class ViewCone {

    /** 視野円錐の半頂角。画面に映っている範囲より少し広めに取ってある。 */
    private static final double HALF_ANGLE_DEGREES = 45.0;
    private static final double COS_LIMIT = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));

    private ViewCone() {
    }

    /**
     * @param range 円錐の長さ。広げるほど当たりやすくなるが、走査する箱も立方で効くので重くなる
     */
    public static List<Mob> mobsInView(Player player, double range) {
        return mobsInView(player, range, Mob::isAlive);
    }

    /**
     * @param present 数に入れる相手の条件。{@code isAlive()} は偽装されうるので、
     *                それを信じたくない呼び出し側は自分で渡す
     */
    public static List<Mob> mobsInView(Player player, double range, Predicate<Mob> present) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        AABB area = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(Mob.class, area, mob -> {
            if (!present.test(mob)) {
                return false;
            }
            Vec3 toward = mob.getBoundingBox().getCenter().subtract(eye);
            double distance = toward.length();
            if (distance > range || distance < 1.0E-4) {
                return false;
            }
            // 単位ベクトル同士の内積 = なす角の cos。しきい値以上なら円錐の内側。
            return look.dot(toward.scale(1.0 / distance)) >= COS_LIMIT;
        });
    }
}
