package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.items.ItemOo;
import jp.main.taikun.tpsthings.items.OoEquivalent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * おおモジュールを入れた道具でも、振っただけで視野の相手を打つ。
 *
 * <p>おお本体の振りはアイテムの {@code onEntitySwing} で受けているが、あれは<b>そのアイテム自身の</b>
 * 上書きなので、他所の道具 (Meka-Tool) にモジュールを入れても呼ばれない。振りを知らせる Forge の
 * イベントもサーバ側には無い。だから振りそのもの ({@code swing} の先頭 — 同じ場所で
 * {@code onEntitySwing} が呼ばれる) に口を足す。
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityOoSwing {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void tpsthings$ooModuleSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (hand != InteractionHand.MAIN_HAND || self.level().isClientSide() || !(self instanceof Player player)) {
            return;
        }
        ItemStack stack = self.getItemInHand(hand);
        // おお本体は onEntitySwing の側で打つ。ここでも打つと 1 回の振りで二度打つ
        if (stack.getItem() instanceof ItemOo || !OoEquivalent.isOo(stack)) {
            return;
        }
        ItemOo.sweep(player);
    }
}
