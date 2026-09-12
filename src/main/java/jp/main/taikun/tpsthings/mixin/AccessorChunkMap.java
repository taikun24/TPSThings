package jp.main.taikun.tpsthings.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 追跡 (周りのクライアントへ姿を送る仕組み) の一覧への入口。
 *
 * 索引に居ても追跡から外されていれば、誰からも見えず、プレイヤーならチャンクも届かない。
 * 公開 API には「追跡されているか」を問う口が無い。
 */
@Mixin(ChunkMap.class)
public interface AccessorChunkMap {

    /** 値の型 (TrackedEntity) は外から見えないので、中身は問わない。鍵 (エンティティ id) だけ使う。 */
    @Accessor("entityMap")
    Int2ObjectMap<?> tpsthings$entityMap();
}
