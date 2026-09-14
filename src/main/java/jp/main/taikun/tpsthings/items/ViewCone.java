package jp.main.taikun.tpsthings.items;

import jp.main.taikun.tpsthings.damage.StrikeCensus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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
    /**
     * 候補を拾う球を円錐より広げる幅。円錐は当たり箱の中心で測るが、索引から拾うときは
     * 足元の位置で測るので、大きな相手ほど両者がずれる。
     */
    private static final double CANDIDATE_MARGIN = 8.0;

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
        List<Mob> found = new ArrayList<>();
        for (LivingEntity living : candidates(player, range)) {
            if (living instanceof Mob mob && present.test(mob) && inCone(eye, look, mob, range)) {
                found.add(mob);
            }
        }
        return found;
    }

    /**
     * 視野円錐の中の<b>生き物すべて</b> (Mob だけでなくプレイヤーも)。振った本人は含めない。
     *
     * <p>無敵は他プレイヤーが装備で纏っていることがある。Mob しか狙わないと、装備で不死になった
     * プレイヤーには円錐が一生当たらない。プレイヤーも索引層まで通す設定のときにこちらを使う。
     */
    public static List<LivingEntity> livingInView(Player player, double range, Predicate<LivingEntity> present) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity living : candidates(player, range)) {
            // 振った本人は狙わない
            if (living != player && present.test(living) && inCone(eye, look, living, range)) {
                found.add(living);
            }
        }
        return found;
    }

    /**
     * 円錐に入りうる生き物。
     *
     * <p>サーバでは世界の検索 ({@code getEntitiesOfClass}) を<b>通さず</b>、索引から直接数える。
     * 検索は包める — 読み出しに濾し器を挟まれると、相手は世界に居るのに検索結果からだけ消え、
     * 円錐に一生入らない (実測: 照会にも当たり判定にも映らない相手が居た)。
     *
     * <p>そのぶん振るたびに索引を舐めるので、検索より重い。クライアントには読むべき索引が無く、
     * 見えている物だけが相手なので、今までどおり検索を使う。
     */
    private static List<LivingEntity> candidates(Player player, double range) {
        if (player.level() instanceof ServerLevel level) {
            return StrikeCensus.livingNear(level, player.getEyePosition(), range + CANDIDATE_MARGIN);
        }
        AABB area = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(LivingEntity.class, area, living -> true);
    }

    private static boolean inCone(Vec3 eye, Vec3 look, net.minecraft.world.entity.Entity target, double range) {
        Vec3 toward = target.getBoundingBox().getCenter().subtract(eye);
        double distance = toward.length();
        if (distance > range || distance < 1.0E-4) {
            return false;
        }
        // 単位ベクトル同士の内積 = なす角の cos。しきい値以上なら円錐の内側。
        return look.dot(toward.scale(1.0 / distance)) >= COS_LIMIT;
    }
}
