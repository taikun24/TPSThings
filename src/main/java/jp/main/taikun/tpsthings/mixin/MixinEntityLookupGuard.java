package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界の索引の絞り所。関所として置ける一番外側。
 *
 * ここから外されたエンティティは、HP がいくつであろうと、削除されていなかろうと、
 * 世界にとって存在しなくなる。HP を守る関所も死亡処理の関所も、ここを抜けられたら
 * 何も守っていないのと同じになる。
 *
 * <p>バニラがここへ来る道は 1 本しかない ({@code PersistentEntitySectionManager})。
 * 直接呼びに来るものは、それだけで正規の経路を外れている。
 */
@Mixin(EntityLookup.class)
public abstract class MixinEntityLookupGuard {

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void tpsthings$guardUnregister(EntityAccess entity, CallbackInfo ci) {
        if (entity instanceof Entity victim && HealthGuard.shouldRefuseUnregister(victim)) {
            ci.cancel();
        }
    }
}
