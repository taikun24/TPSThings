package jp.main.taikun.tpsthings.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

/**
 * UUID の台帳だけ残して索引を剥がされたときの、部分修復用の入口。
 *
 * 台帳 ({@code knownUuids}) にまだ載っている相手は {@code addNewEntityWithoutEvent} が
 * 重複扱いで断ってくるので、正面からの再登録が使えない。剥がされたのが索引だけなら、
 * 直すのも索引だけでよい。
 *
 * <p>貫通攻撃は同じ入れ物を逆向きに使う (区画と台帳からも外す)。
 */
@Mixin(PersistentEntitySectionManager.class)
public interface AccessorPersistentEntitySectionManager {

    @Accessor("visibleEntityStorage")
    EntityLookup<EntityAccess> tpsthings$visibleEntityStorage();

    @Accessor("sectionStorage")
    EntitySectionStorage<EntityAccess> tpsthings$sectionStorage();

    @Accessor("knownUuids")
    Set<UUID> tpsthings$knownUuids();
}
