package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.blockentities.BETimeAccelerator;
import jp.main.taikun.tpsthings.blockentities.BETimeFluxCollector;
import jp.main.taikun.tpsthings.blockentities.BELagGenerator;
import jp.main.taikun.tpsthings.blockentities.BETpsGenerator;
import jp.main.taikun.tpsthings.blockentities.BEExampleMachine;
import mekanism.api.math.FloatingLong;
import mekanism.common.MekanismLang;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.content.blocktype.Machine;

public class ModBlockTypes {
    public static final Machine<BEExampleMachine> EXAMPLE_MACHINE = Machine.MachineBuilder
            .createMachine(() -> ModBlockEntityTypes.EXAMPLE_MACHINE, MekanismLang.DESCRIPTION_CRUSHER)
            .withGui(() -> ModContainerTypes.EXAMPLE_MACHINE)
            .replace(Attributes.ACTIVE)
            .build();
    public static final Machine<BETimeFluxCollector> TIME_FLUX_COLLECTOR = Machine.MachineBuilder
            .createMachine(() -> ModBlockEntityTypes.TIME_FLUX_COLLECTOR, MekanismLang.DESCRIPTION_CRUSHER)
            .withGui(() -> ModContainerTypes.TIME_FLUX_COLLECTOR)
            .withEnergyConfig(() -> FloatingLong.create(1000000f))
            .replace(Attributes.ACTIVE)
            .build();
    public static final Machine<BETpsGenerator> TPS_GENERATOR = Machine.MachineBuilder
            .createMachine(() -> ModBlockEntityTypes.TPS_GENERATOR, MekanismLang.DESCRIPTION_CRUSHER)
            .withGui(() -> ModContainerTypes.TPS_GENERATOR)
            .replace(Attributes.ACTIVE)
            .build();
    public static final Machine<BELagGenerator> LAG_GENERATOR = Machine.MachineBuilder
            .createMachine(() -> ModBlockEntityTypes.LAG_GENERATOR, MekanismLang.DESCRIPTION_CRUSHER)
            .withGui(() -> ModContainerTypes.LAG_GENERATOR)
            .replace(Attributes.ACTIVE)
            .build();
    public static final Machine<BETimeAccelerator> TIME_ACCELERATOR = Machine.MachineBuilder
            .createMachine(() -> ModBlockEntityTypes.TIME_ACCELERATOR, MekanismLang.DESCRIPTION_CRUSHER)
            .withGui(() -> ModContainerTypes.TIME_ACCELERATOR)
            .withEnergyConfig(() -> FloatingLong.create(4000000f))
            .replace(Attributes.ACTIVE)
            .build();
}
