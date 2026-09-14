package jp.main.taikun.tpsthings.items;

import jp.main.taikun.tpsthings.registries.ModItems;
import jp.main.taikun.tpsthings.registries.ModModules;
import mekanism.api.gear.IModuleHelper;
import mekanism.common.content.gear.IModuleContainerItem;
import net.minecraft.world.item.ItemStack;

/**
 * 「これはおおか」の判定を 1 箇所に集める。
 *
 * <p>おお本体に加えて、おおモジュールを入れて<b>有効にしている</b> Mekanism の装備
 * (MekaSuit の胴・Meka-Tool) もおおとして数える。おおの効果はどれも「おおを着ている / 持っている」を
 * 見て走るので、判定をここへ寄せれば、効果の側を書き換えずにそのままモジュールへ移る。
 *
 * <p>無効に切り替えたモジュールは数えない。Mekanism の他のモジュールと同じく、
 * 入れたまま止められる方が持ち主にとって自然なので。
 */
public final class OoEquivalent {

    private OoEquivalent() {
    }

    public static boolean isOo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.OO.get())) {
            return true;
        }
        // モジュールを持てる入れ物だけ中身を読む。装備の判定は毎 tick・描画のたびに呼ばれるので、
        // 関係の無い道具でまで NBT を解かない
        if (!(stack.getItem() instanceof IModuleContainerItem)) {
            return false;
        }
        try {
            return IModuleHelper.INSTANCE.isEnabled(stack, ModModules.OO_UNIT);
        } catch (RuntimeException | LinkageError unreadable) {
            return false;
        }
    }
}
