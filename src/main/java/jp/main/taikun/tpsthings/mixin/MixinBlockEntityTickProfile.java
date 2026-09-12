package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.profile.TickProfiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BlockEntity 1 つ分の tick を挟んで時間を測る。
 *
 * ticker の呼び出しそのものを包む。包む側の私有フィールドを {@code @Shadow} で覗くと、
 * 内部クラスの私有フィールドは refmap に載らず実行時の難読名と食い違って落ちる。
 * 引数として渡ってくるものだけで済ませれば、その問題が起きない。
 *
 * ここは全 BlockEntity が毎 tick 通る。止まっているときの費用が volatile の読み 1 回で
 * 済むように、判定は {@link TickProfiler#begin()} 側だけに持たせている。
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class MixinBlockEntityTickProfile {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick"
                            + "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void tpsthings$profileBlockEntityTick(BlockEntityTicker<BlockEntity> ticker,
                                                  Level level, BlockPos pos,
                                                  BlockState state, BlockEntity blockEntity) {
        long start = TickProfiler.begin();
        try {
            ticker.tick(level, pos, state, blockEntity);
        } finally {
            TickProfiler.endBlockEntity(start, blockEntity);
        }
    }
}
