package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.blockentities.*;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntityTypes {
    public static final TileEntityTypeDeferredRegister BLOCK_ENTITY_TYPES = new TileEntityTypeDeferredRegister(Tpsthings.MODID);
    public static final DeferredRegister<BlockEntityType<?>> SIMPLE_BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Tpsthings.MODID);

    public static final TileEntityTypeRegistryObject<BEExampleMachine> EXAMPLE_MACHINE = BLOCK_ENTITY_TYPES
            .builder(ModBlocks.EXAMPLE_MACHINE, BEExampleMachine::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();
    public static final TileEntityTypeRegistryObject<BETimeFluxCollector> TIME_FLUX_COLLECTOR = BLOCK_ENTITY_TYPES
            .builder(ModBlocks.TIME_FLUX_COLLECTOR, BETimeFluxCollector::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();
    public static final TileEntityTypeRegistryObject<BETpsGenerator> TPS_GENERATOR = BLOCK_ENTITY_TYPES
            .builder(ModBlocks.TPS_GENERATOR, BETpsGenerator::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();
    public static final TileEntityTypeRegistryObject<BELagGenerator> LAG_GENERATOR = BLOCK_ENTITY_TYPES
            .builder(ModBlocks.LAG_GENERATOR, BELagGenerator::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();
    public static final TileEntityTypeRegistryObject<BETimeAccelerator> TIME_ACCELERATOR = BLOCK_ENTITY_TYPES
            .builder(ModBlocks.TIME_ACCELERATOR, BETimeAccelerator::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .build();

    public static final RegistryObject<BlockEntityType<BEDebugAccelerator>> DEBUG_ACCELERATOR
            = SIMPLE_BLOCK_ENTITY_TYPES.register("debug_accelerator", ()->BlockEntityType.Builder.of(BEDebugAccelerator::new, ModBlocks.DEBUG_ACCELERATOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<BEAbsoluteLifeAnchor>> ABSOLUTE_LIFE_ANCHOR
            = SIMPLE_BLOCK_ENTITY_TYPES.register("absolute_life_anchor", ()->BlockEntityType.Builder.of(BEAbsoluteLifeAnchor::new, ModBlocks.ABSOLUTE_LIFE_ANCHOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<BEAnnihilationChamber>> ANNIHILATION_CHAMBER
            = SIMPLE_BLOCK_ENTITY_TYPES.register("annihilation_chamber", ()->BlockEntityType.Builder.of(BEAnnihilationChamber::new, ModBlocks.ANNIHILATION_CHAMBER.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
        SIMPLE_BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
