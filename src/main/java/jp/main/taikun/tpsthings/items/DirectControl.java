package jp.main.taikun.tpsthings.items;

import jp.main.taikun.tpsthings.damage.DamageGuard;
import jp.main.taikun.tpsthings.mixin.AccessorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 直接操縦。位置と速度をキー入力だけから決めて、毎 tick 書き込む。
 *
 * <p>通常の移動は travel → move → collide と、他所の Mod が割り込める口をいくつも通る。
 * ここではそれを使わず、当たり判定もブロックの形だけを自前で集めて解く
 * (エンティティ由来の当たりや、collide に足された当たりは見ない)。
 *
 * <p>有効なのは、直接操縦を ON にしたおおを着ているときだけ。設定はおおの NBT に載るので、
 * クライアントとサーバが同じ答えを出す。
 */
public final class DirectControl {

    private DirectControl() {
    }

    public static boolean isActive(Player player) {
        if (player.isSpectator() || player.isPassenger() || player.isSleeping() || !ItemOo.isWorn(player)) {
            return false;
        }
        return OoToolSettings.isDirectControl(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    /** box を motion だけ動かしたときに、ブロックに当たるまでで実際に進める量。 */
    public static Vec3 collide(Level level, AABB box, Vec3 motion) {
        if (motion.lengthSqr() == 0.0D) {
            return Vec3.ZERO;
        }
        List<VoxelShape> shapes = blockShapes(level, box.expandTowards(motion));
        // 順番はバニラと揃える: 縦 → 大きい方の水平軸 → 残りの水平軸
        double y = Shapes.collide(Direction.Axis.Y, box, shapes, motion.y);
        box = box.move(0.0D, y, 0.0D);
        double x;
        double z;
        if (Math.abs(motion.x) < Math.abs(motion.z)) {
            z = Shapes.collide(Direction.Axis.Z, box, shapes, motion.z);
            box = box.move(0.0D, 0.0D, z);
            x = Shapes.collide(Direction.Axis.X, box, shapes, motion.x);
        } else {
            x = Shapes.collide(Direction.Axis.X, box, shapes, motion.x);
            box = box.move(x, 0.0D, 0.0D);
            z = Shapes.collide(Direction.Axis.Z, box, shapes, motion.z);
        }
        return new Vec3(x, y, z);
    }

    /** 足元のすぐ下にブロックがあるか。 */
    public static boolean isSupported(Level level, AABB box) {
        return collide(level, box, new Vec3(0.0D, -0.01D, 0.0D)).y > -0.01D;
    }

    private static List<VoxelShape> blockShapes(Level level, AABB area) {
        List<VoxelShape> shapes = new ArrayList<>();
        int minX = Mth.floor(area.minX - 1.0E-7D);
        int maxX = Mth.floor(area.maxX + 1.0E-7D);
        // フェンスのように 1 マスより背の高い形は下のマスから伸びてくる
        int minY = Mth.floor(area.minY - 1.0E-7D) - 1;
        int maxY = Mth.floor(area.maxY + 1.0E-7D);
        int minZ = Mth.floor(area.minZ - 1.0E-7D);
        int maxZ = Mth.floor(area.maxZ + 1.0E-7D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    // 読み込まれていない所は壁として扱う。中身の分からない所へ抜けて落ちないように
                    if (!level.hasChunkAt(cursor)) {
                        shapes.add(Shapes.block().move(x, y, z));
                        continue;
                    }
                    VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor, CollisionContext.empty());
                    if (!shape.isEmpty()) {
                        shapes.add(shape.move(x, y, z));
                    }
                }
            }
        }
        return shapes;
    }

    /**
     * 位置を置く。まず普通に setPos を通し (世界の索引への通知もそこで済む)、
     * それが効かなかったときだけフィールドへ直接書く。
     */
    public static void forcePosition(Entity entity, Vec3 position) {
        DamageGuard.runAsSelf(() -> entity.setPos(position.x, position.y, position.z));
        if (!entity.position().equals(position)) {
            AccessorEntity access = (AccessorEntity) entity;
            access.tpsthings$setPosition(position);
            BlockPos block = BlockPos.containing(position);
            if (!block.equals(entity.blockPosition())) {
                access.tpsthings$setBlockPosition(block);
                access.tpsthings$setFeetBlockState(null);
                ChunkPos chunk = new ChunkPos(block);
                if (!chunk.equals(entity.chunkPosition())) {
                    access.tpsthings$setChunkPosition(chunk);
                }
            }
            access.tpsthings$levelCallback().onMove();
        }
        entity.setBoundingBox(entity.getDimensions(entity.getPose()).makeBoundingBox(position));
    }

    /** 速度を置く。setDeltaMovement の入口は通さない。 */
    public static void forceVelocity(Entity entity, Vec3 velocity) {
        ((AccessorEntity) entity).tpsthings$setDeltaMovement(velocity);
    }
}
