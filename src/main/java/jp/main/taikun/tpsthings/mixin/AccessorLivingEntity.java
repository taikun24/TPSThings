package jp.main.taikun.tpsthings.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 貫通攻撃が {@code setHealth} / {@code isDeadOrDying} を通らずに状態を読み書きするための入口。
 *
 * どちらもメソッドは上書き・偽装されうるが、値の置き場所は変えられない。
 */
@Mixin(LivingEntity.class)
public interface AccessorLivingEntity {

    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> tpsthings$healthId() {
        throw new AssertionError();
    }

    /** 死亡処理が最後まで通ったかの印。{@code die} が拒否されると立たない。 */
    @Accessor("dead")
    boolean tpsthings$dead();
}
