package jp.main.taikun.tpsthings.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * 世界がエンティティを数え上げるときに使う 2 つの入れ物への入口。
 *
 * どちらも private で、公開 API には「載せ直す」ための口が無い。
 * 外す側の Mod は Accessor で自由にここへ手を入れてくるので、
 * 戻す側も同じ深さまで降りられなければ勝負にならない。
 */
@Mixin(ServerLevel.class)
public interface AccessorServerLevel {

    @Accessor("entityTickList")
    EntityTickList tpsthings$entityTickList();

    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> tpsthings$entityManager();

    /** 経路探索中の Mob の一覧。索引から直接外すときに、ここにだけ残ると後始末が漏れる。 */
    @Accessor("navigatingMobs")
    Set<Mob> tpsthings$navigatingMobs();
}
