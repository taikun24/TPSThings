package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.core.Core;

import java.util.function.Consumer;

public class ModFluidTypes {
    public static final ResourceLocation WATER_STILL = ResourceLocation.tryBuild("minecraft", "block/water_still");
    public static final ResourceLocation WATER_FLOW = ResourceLocation.tryBuild("minecraft", "block/water_flow");
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Tpsthings.MODID);
    public static final RegistryObject<FluidType> NOPE = FLUID_TYPES.register("nope", () -> new WaterTexturedFluidType(FluidType.Properties.create().canSwim(true).canDrown(true).canExtinguish(true),0xff420027));
    public static final RegistryObject<FluidType> HOPE_ETHYLENE = FLUID_TYPES.register("hope_ethylene", () -> new WaterTexturedFluidType(FluidType.Properties.create().canSwim(true).canDrown(true).canExtinguish(true),0xfff3e3ff));
    public static final RegistryObject<FluidType> JAVA_TEA = FLUID_TYPES.register("java_tea", () -> new WaterTexturedFluidType(FluidType.Properties.create().canSwim(true).canDrown(true).canExtinguish(true),0xff6b4423));
    public static class WaterTexturedFluidType extends FluidType {
        private final int color;
        public WaterTexturedFluidType(FluidType.Properties properties, int color) {
            super(properties);
            this.color = color;
        }
        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public int getTintColor() {
                    return color;
                }

                @Override
                public ResourceLocation getStillTexture() {
                    return WATER_STILL;
                }
                @Override
                public ResourceLocation getFlowingTexture() {
                    return WATER_FLOW;
                }
            });
        }
    }
    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
    }
}
