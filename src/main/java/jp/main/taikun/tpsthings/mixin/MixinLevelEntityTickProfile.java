package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.profile.TickProfiler;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * エンティティ 1 体分の tick を挟んで時間を測る。
 *
 * {@code guardEntityTick} は例外を掴んで潰す作りなので、HEAD と RETURN に分けて挟むと
 * 例外時に開始時刻が残る。呼び出しそのものを包んでしまえばその心配がない。
 */
@Mixin(Level.class)
public abstract class MixinLevelEntityTickProfile {

    @SuppressWarnings("unchecked")
    @Redirect(
            method = "guardEntityTick",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private <T> void tpsthings$profileEntityTick(Consumer<T> consumer, Object entity) {
        long start = TickProfiler.begin();
        try {
            consumer.accept((T) entity);
        } finally {
            TickProfiler.endEntity(start, entity);
        }
    }
}
