package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

public class ModFluids {
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, Tpsthings.MODID);


    public static final RegistryObject<FlowingFluid> NOPE = FLUIDS.register("nope", ()->new ForgeFlowingFluid.Source(ModFluids.NOPE_PROPERTIES));
    public static final RegistryObject<FlowingFluid> NOPE_FLOWING = FLUIDS.register("nope_flowing", ()->new ForgeFlowingFluid.Flowing(ModFluids.NOPE_PROPERTIES));
    public static final RegistryObject<Fluid> HOPE_ETHYLENE = FLUIDS.register("hope_ethylene", ()->new ForgeFlowingFluid.Source(ModFluids.HOPE_ETHYLENE_PROPERTIES));
    public static final RegistryObject<Fluid> HOPE_ETHYLENE_FLOWING = FLUIDS.register("hope_ethylene_flowing", ()->new ForgeFlowingFluid.Flowing(ModFluids.HOPE_ETHYLENE_PROPERTIES));

    private static final ForgeFlowingFluid.Properties NOPE_PROPERTIES = new ForgeFlowingFluid.Properties(
            ()->ModFluidTypes.NOPE.getHolder().get().get(),
            NOPE::get,
            NOPE_FLOWING::get
    );
    private static final ForgeFlowingFluid.Properties HOPE_ETHYLENE_PROPERTIES = new ForgeFlowingFluid.Properties(
            ()->ModFluidTypes.HOPE_ETHYLENE.getHolder().get().get(),
            HOPE_ETHYLENE::get,
            HOPE_ETHYLENE_FLOWING::get
    );
    public static void register(IEventBus bus) {
        FLUIDS.register(bus);
    }


}
