package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.HealthGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 実体の作り直しの絞り所。
 *
 * リスポーンは「死んだ実体を新しい実体に置き換える」処理で、生きている実体に対して
 * 走らせるものではない。読み出しを乗っ取られてクライアントだけが死んだと信じたとき、
 * ここへ誤発注が届く。受けると同じ UUID の実体が二重になる。
 *
 * <p>断るときは古い (生きている本物の) 実体をそのまま返す。呼び出し側は返ってきた
 * 実体を新しい本体として扱うので、これで何も起きなかったのと同じになる。
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerListRespawnGuard {

    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void tpsthings$refuseSpuriousRespawn(ServerPlayer player, boolean keepEverything,
                                                 CallbackInfoReturnable<ServerPlayer> cir) {
        if (HealthGuard.shouldRefuseRespawn(player, keepEverything)) {
            cir.setReturnValue(player);
        }
    }
}
