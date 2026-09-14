package jp.main.taikun.tpsthings.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** クライアントの世界が毎 tick 回す実体の一覧。自機がここから落ちると、自機の tick は二度と来ない。 */
@Mixin(ClientLevel.class)
public interface AccessorClientLevel {

    @Accessor("tickingEntities")
    EntityTickList tpsthings$tickingEntities();
}
