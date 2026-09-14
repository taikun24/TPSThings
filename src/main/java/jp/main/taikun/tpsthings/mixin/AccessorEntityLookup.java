package jp.main.taikun.tpsthings.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * 世界の UUID 索引の<b>中身そのもの</b>への入口。
 *
 * <p>索引から外す仕事は {@code EntityLookup#remove} が持っているが、あれはただのメソッドで、
 * 頭で握り潰せば何事も無かったように戻ってくる — 例外も戻り値も無いので、呼んだ側からは
 * 成功と見分けがつかない。関所を張る側 ({@link MixinEntityLookupGuard}) が自分でやっている
 * ことなので、同じ手が自分に向いていないと考える理由が無い。
 *
 * <p>だから外せたかどうかはメソッドの結果ではなく<b>索引の状態</b>で確かめ、残っていれば
 * 値が実際に載っているこの 2 つの入れ物へ直接書く。守る側が
 * {@link AccessorPersistentEntitySectionManager} で索引を直に直すのと同じ深さ。
 */
@Mixin(EntityLookup.class)
public interface AccessorEntityLookup {

    /** UUID から実体を引く方の索引。{@code level.getEntity(UUID)} が見ているのはここ。 */
    @Accessor("byUuid")
    Map<UUID, EntityAccess> tpsthings$byUuid();

    /** エンティティ番号から引く方の索引。走査 (getAllEntities) もこちらを回す。 */
    @Accessor("byId")
    Int2ObjectMap<EntityAccess> tpsthings$byId();
}
