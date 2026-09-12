package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import mekanism.api.chemical.gas.Gas;
import mekanism.common.base.IChemicalConstant;
import mekanism.common.registration.impl.GasDeferredRegister;
import mekanism.common.registration.impl.GasRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public class ModGases {
    private static final GasDeferredRegister GAS_TYPES = new GasDeferredRegister(Tpsthings.MODID);
    public static final GasRegistryObject<Gas> TIME_FLUX = GAS_TYPES.register(ChemicalConstants.TIME_FLUX);
    public static final GasRegistryObject<Gas> HOPE_ETHYLENE = GAS_TYPES.register(ChemicalConstants.HOPE_ETHYLENE);
    public static final GasRegistryObject<Gas> HOPE_HYDROGEN = GAS_TYPES.register(ChemicalConstants.HOPE_HYDROGEN);
    public static final GasRegistryObject<Gas> HOPE_OXYGEN = GAS_TYPES.register(ChemicalConstants.HOPE_OXYGEN);
    public static final GasRegistryObject<Gas> NOPE_GAS = GAS_TYPES.register(ChemicalConstants.NOPE_GAS);
    public static final GasRegistryObject<Gas> CARBON_DIOXIDE = GAS_TYPES.register(ChemicalConstants.CARBON_DIOXIDE);
    public static void register(IEventBus eventBus) {
        GAS_TYPES.register(eventBus);
    }
    public enum ChemicalConstants implements IChemicalConstant {
        TIME_FLUX("time_flux", 0xFFFF00FF, 15, 300F, 1_254F),
        HOPE_ETHYLENE("hope_ethylene", 0xfff3e3ff, 15, 300F, 1_254F),
        HOPE_HYDROGEN("hope_hydrogen", 0xfffffef2, 15, 300F, 1_254F),
        HOPE_OXYGEN("hope_oxygen", 0xffff5ccb, 15, 300F, 1_254F),
        CARBON_DIOXIDE("carbon_dioxide", 0xffadadad, 15, 300F, 1_254F),
        NOPE_GAS("nope_gas", 0xff420027, 15, 300F, 1_254F);

        private final String name;
        private final int color;
        private final int lightLevel;
        private final float temperature;
        private final float density;


        ChemicalConstants(String name, int color, int lightLevel, float temperature, float density) {
            this.name = name;
            this.color = color;
            this.lightLevel = lightLevel;
            this.temperature = temperature;
            this.density = density;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getColor() {
            return color;
        }

        @Override
        public float getTemperature() {
            return temperature;
        }

        @Override
        public float getDensity() {
            return density;
        }

        @Override
        public int getLightLevel() {
            return lightLevel;
        }
    }
}
