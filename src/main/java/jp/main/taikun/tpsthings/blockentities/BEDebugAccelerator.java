package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.registries.ModBlockEntityTypes;
import jp.main.taikun.tpsthings.time.TickUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BEDebugAccelerator extends BlockEntity {
    public BEDebugAccelerator(BlockPos p_155229_, BlockState p_155230_) {
        super(ModBlockEntityTypes.DEBUG_ACCELERATOR.get(), p_155229_, p_155230_);
    }
    private int speed = 2;
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putInt("speed", speed);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        speed = nbt.getInt("speed");
    }
    public int changeSpeed(boolean back){
        if (back){
            if (speed >= 2)
                speed = speed / 2;
        } else {
            speed *= 2;
        }
        return speed;
    }
    public static <T extends BlockEntity> void tick(Level level, BlockPos blockPos, BlockState blockState, T t) {
        if(level.isClientSide()) return;
        TickUtil.tick(level, blockPos.above(), ((BEDebugAccelerator) t).speed);
    }
}
