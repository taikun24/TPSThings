package jp.main.taikun.tpsthings.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * tick 一覧の中身そのもの。
 *
 * {@code add} の入口は頭で塞げるが、一覧はただの Map。入口を塞がれていても値は置ける。
 */
@Mixin(EntityTickList.class)
public interface AccessorEntityTickList {

    @Accessor("active")
    Int2ObjectMap<Entity> tpsthings$active();
}
