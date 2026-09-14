package jp.main.taikun.tpsthings.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 除去の印を、{@code setRemoved} を通らずに立てる入口。
 *
 * {@code setRemoved} の入口を塞がれていても、印そのものはただのフィールド。
 * 守る側は同じ口で、関所を通らずに立てられた印を下ろす。
 */
@Mixin(Entity.class)
public interface AccessorEntity {

    @Accessor("removalReason")
    void tpsthings$setRemovalReason(Entity.RemovalReason reason);

    /** 除去の印そのもの。{@code isRemoved()} はただのメソッドで、本体を書き換えれば嘘をつける。 */
    @Accessor("removalReason")
    Entity.RemovalReason tpsthings$getRemovalReason();

    /** 世界の索引への後始末の繋がり。切られている (NULL) と、索引に居ても世界と縁が切れている。 */
    @Accessor("levelCallback")
    EntityInLevelCallback tpsthings$levelCallback();

    // ---- 直接操縦の書き込み口。setPos / setDeltaMovement の入口を塞がれていても値そのものは置ける ----

    @Accessor("position")
    void tpsthings$setPosition(Vec3 position);

    @Accessor("blockPosition")
    void tpsthings$setBlockPosition(BlockPos position);

    @Accessor("chunkPosition")
    void tpsthings$setChunkPosition(ChunkPos position);

    @Accessor("feetBlockState")
    void tpsthings$setFeetBlockState(BlockState state);

    @Accessor("deltaMovement")
    void tpsthings$setDeltaMovement(Vec3 movement);
}
