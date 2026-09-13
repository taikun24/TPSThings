package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.blockentities.BETimeFluxCollector;
import jp.main.taikun.tpsthings.blockentities.BELagGenerator;
import jp.main.taikun.tpsthings.blockentities.BETimeAccelerator;
import jp.main.taikun.tpsthings.blockentities.BETpsGenerator;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public class ModContainerTypes {
    public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister(Tpsthings.MODID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BETimeFluxCollector>> TIME_FLUX_COLLECTOR = CONTAINER_TYPES.register(ModBlocks.TIME_FLUX_COLLECTOR, BETimeFluxCollector.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BETpsGenerator>> TPS_GENERATOR = CONTAINER_TYPES.register(ModBlocks.TPS_GENERATOR, BETpsGenerator.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BELagGenerator>> LAG_GENERATOR = CONTAINER_TYPES.register(ModBlocks.LAG_GENERATOR, BELagGenerator.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BETimeAccelerator>> TIME_ACCELERATOR = CONTAINER_TYPES.register(ModBlocks.TIME_ACCELERATOR, BETimeAccelerator.class);

    public static void register(IEventBus eventBus) {
        CONTAINER_TYPES.register(eventBus);
    }
}