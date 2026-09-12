package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.items.ItemOo;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装備由来の keepInventory。
 *
 * ゲームルールを触らずに、対象のプレイヤーだけ持ち物を落とさないようにする。
 * ここで落とすのを止めておかないと、リスポーン時に引き継ごうにも中身が既に空になる。
 */
@Mixin(Player.class)
public abstract class MixinPlayerKeepInventory {

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void tpsthings$keepInventory(CallbackInfo ci) {
        if (ItemOo.isWorn((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
