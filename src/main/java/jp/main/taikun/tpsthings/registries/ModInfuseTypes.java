package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.common.registration.impl.InfuseTypeDeferredRegister;
import mekanism.common.registration.impl.InfuseTypeRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 吹込 (金属注入機) で合金に喰わせる素。Mekanism の炭素・レッドストーンと同じ扱い方をする。
 *
 * <p>ネザライトだけは粉から 10 mB、濃縮ネザライトから 80 mB の 2 経路を持つ
 * (Mekanism の炭素/ダイヤと同じ形)。ポロニウムと反物質はペレットから 80 mB だけ。
 */
public class ModInfuseTypes {
    private static final InfuseTypeDeferredRegister INFUSE_TYPES = new InfuseTypeDeferredRegister(Tpsthings.MODID);

    public static final InfuseTypeRegistryObject<InfuseType> NETHERITE = INFUSE_TYPES.register("netherite", 0xFF4B4038);
    public static final InfuseTypeRegistryObject<InfuseType> POLONIUM = INFUSE_TYPES.register("polonium", 0xFF00F0C0);
    public static final InfuseTypeRegistryObject<InfuseType> ANTIMATTER = INFUSE_TYPES.register("antimatter", 0xFFEA00FF);

    public static void register(IEventBus bus) {
        INFUSE_TYPES.register(bus);
    }
}
