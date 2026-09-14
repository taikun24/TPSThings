package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import mekanism.api.MekanismIMC;
import mekanism.common.registration.impl.ModuleDeferredRegister;
import mekanism.common.registration.impl.ModuleRegistryObject;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Mekanism のモジュール。
 *
 * <p>おおの力を MekaSuit / Meka-Tool に移すための 1 枚。モジュール自体は何もしない<b>印</b>で、
 * 「入っていて有効か」を {@link jp.main.taikun.tpsthings.items.OoEquivalent} が見て、
 * おおを着ている / 持っているのと同じに扱う。効果をモジュール側に二重に書くと、
 * おお本体を直したときに片方だけ古いまま残る。
 */
public final class ModModules {

    private static final ModuleDeferredRegister MODULES = new ModuleDeferredRegister(Tpsthings.MODID);

    /** MekaSuit のボディアーマーと Meka-Tool に入る (おおが効くのは胴と手なので)。 */
    public static final ModuleRegistryObject<?> OO_UNIT = MODULES.registerMarker("oo_unit",
            () -> ModItems.MODULE_OO_UNIT.get(), builder -> builder.rarity(Rarity.EPIC));

    private ModModules() {
    }

    public static void register(IEventBus bus) {
        MODULES.register(bus);
        bus.addListener(ModModules::enqueueIMC);
    }

    /** どの装備に入れられるかは Mekanism 側が IMC で受け付ける。 */
    private static void enqueueIMC(InterModEnqueueEvent event) {
        MekanismIMC.addMekaSuitBodyarmorModules(OO_UNIT);
        MekanismIMC.addMekaToolModules(OO_UNIT);
    }
}
