package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.blockentities.BETimeFluxCollector;
import jp.main.taikun.tpsthings.blockentities.BELagGenerator;
import jp.main.taikun.tpsthings.blockentities.BETimeAccelerator;
import jp.main.taikun.tpsthings.blockentities.BETpsGenerator;
import jp.main.taikun.tpsthings.blockentities.BEExampleMachine;
import jp.main.taikun.tpsthings.machines.BaseMachine;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraftforge.eventbus.api.IEventBus;

public class ModContainerTypes {
    public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister(Tpsthings.MODID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BEExampleMachine>> EXAMPLE_MACHINE = CONTAINER_TYPES.register(ModBlocks.EXAMPLE_MACHINE, BEExampleMachine.class);
    public static final ContainerTypeRegistryObject<MekanismTileContainer<BETimeFluxCollector>> TIME_FLUX_COLLECTOR = CONTAINER_TYPES.register(ModBlocks.TIME_FLUX_COLLECTOR, BETimeFluxCollector.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BETpsGenerator>> TPS_GENERATOR = CONTAINER_TYPES.register(ModBlocks.TPS_GENERATOR, BETpsGenerator.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BELagGenerator>> LAG_GENERATOR = CONTAINER_TYPES.register(ModBlocks.LAG_GENERATOR, BELagGenerator.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<BETimeAccelerator>> TIME_ACCELERATOR = CONTAINER_TYPES.register(ModBlocks.TIME_ACCELERATOR, BETimeAccelerator.class);

    public static void register(IEventBus eventBus) {
        // ヘルパーメソッドを介して代入・登録を行う
        MachineRegistry.INSTANCE.forEachMachine(ModContainerTypes::registerContainerHelper);

        CONTAINER_TYPES.register(eventBus);
    }

    // 型キャプチャを一致させるためのヘルパーメソッド
    private static <BE extends TileEntityMekanism> void registerContainerHelper(BaseMachine<BE> machine) {
        machine.tileContainer = CONTAINER_TYPES.register(machine.machineBlock, machine.getMachineClass());
    }
}