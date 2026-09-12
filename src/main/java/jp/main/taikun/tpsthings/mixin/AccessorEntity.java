package jp.main.taikun.tpsthings.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
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

    /** 世界の索引への後始末の繋がり。切られている (NULL) と、索引に居ても世界と縁が切れている。 */
    @Accessor("levelCallback")
    EntityInLevelCallback tpsthings$levelCallback();
}
