package jp.main.taikun.tpsthings.gui;

import com.mojang.blaze3d.platform.InputConstants;
import jp.main.taikun.tpsthings.items.DirectControl;
import jp.main.taikun.tpsthings.items.OoToolSettings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * 直接操縦のクライアント側。tick の最後に、キーの状態から次の位置を決めて書き込む。
 *
 * <p>位置は自機が持っている値を毎 tick 読み直さず、ここで覚えている値から積み上げる。
 * その間に何が書き換えても (テレポート・速度の同期・他所の Mod の移動) 次の tick で上書きされる。
 * 受け入れたい移動があるときは、直接操縦を一度 OFF → ON にすると今の位置から数え直す。
 *
 * <p>入力も KeyMapping の押下状態ではなく、割り当てられた物理キーを GLFW に直接聞く
 * (押下状態のほうは外から書き換えられる)。
 */
public final class DirectControlClient {

    private static final double WALK_SPEED = 0.8D;
    private static final double SPRINT_SPEED = 2.0D;
    // 自機が作り直された直後 (リスポーン・次元移動) は、サーバからの位置が届くまで数え直しを続ける
    private static final int SEED_TICKS = 10;

    // 慣性ありのとき、1 tick で入力の速度との差をどれだけ詰めるか (0..1)
    private static final double INERTIA_RESPONSE = 0.2D;

    private static LocalPlayer owner;
    private static Vec3 position;
    private static Vec3 velocity = Vec3.ZERO;
    private static int seedTicks;

    private DirectControlClient() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !DirectControl.isActive(player)) {
            owner = null;
            return;
        }
        if (player != owner) {
            owner = player;
            seedTicks = SEED_TICKS;
        }
        if (seedTicks > 0) {
            seedTicks--;
            position = player.position();
            velocity = Vec3.ZERO;
            return;
        }

        Vec3 target = minecraft.screen == null ? input(minecraft, player) : Vec3.ZERO;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        // 慣性ありなら、入力から決まる速度へ毎 tick 一定の割合で寄せる (離したときも同じ割合で止まる)
        Vec3 wanted = OoToolSettings.isDirectInertia(chest)
                ? velocity.add(target.subtract(velocity).scale(INERTIA_RESPONSE))
                : target;
        AABB box = player.getDimensions(player.getPose()).makeBoundingBox(position);
        Vec3 moved = DirectControl.collide(player.level(), box, wanted);
        // 壁に当たった軸の速度はそこで消える
        velocity = moved;
        Vec3 previous = position;
        position = previous.add(moved);

        // 描画は xo → 現在位置で補間されるので、前の tick の位置を置き直しておく
        player.xo = player.xOld = previous.x;
        player.yo = player.yOld = previous.y;
        player.zo = player.zOld = previous.z;
        DirectControl.forcePosition(player, position);
        DirectControl.forceVelocity(player, moved);
        player.horizontalCollision = moved.x != wanted.x || moved.z != wanted.z;
        player.verticalCollision = moved.y != wanted.y;
        player.setOnGround(DirectControl.isSupported(player.level(), box.move(moved)));
        player.resetFallDistance();

        // 通常の送信は tick の途中 (上書きの前) に済んでいるので、確定した位置をもう一度送る
        player.connection.send(new ServerboundMovePlayerPacket.PosRot(position.x, position.y, position.z,
                player.getYRot(), player.getXRot(), player.onGround()));
    }

    private static Vec3 input(Minecraft minecraft, LocalPlayer player) {
        Options options = minecraft.options;
        long window = minecraft.getWindow().getWindow();
        double forward = axis(held(window, options.keyUp), held(window, options.keyDown));
        double strafe = axis(held(window, options.keyLeft), held(window, options.keyRight));
        double vertical = axis(held(window, options.keyJump), held(window, options.keyShift));
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 1.0D) {
            forward /= length;
            strafe /= length;
        }
        double speed = held(window, options.keySprint) ? SPRINT_SPEED : WALK_SPEED;
        // 向きの回し方はバニラの getInputVector と同じ
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        return new Vec3((strafe * cos - forward * sin) * speed, vertical * speed,
                (forward * cos + strafe * sin) * speed);
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1.0D : 0.0D) - (negative ? 1.0D : 0.0D);
    }

    private static boolean held(long window, KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            // スキャンコード指定は GLFW に直接聞けないので、割り当て側の状態に任せる
            case SCANCODE -> mapping.isDown();
        };
    }
}
