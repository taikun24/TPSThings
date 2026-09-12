package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.blockentities.BETimeAccelerator;
import jp.main.taikun.tpsthings.blockentities.BETimeFluxCollector;
import jp.main.taikun.tpsthings.blockentities.BELagGenerator;
import jp.main.taikun.tpsthings.blockentities.BETpsGenerator;
import jp.main.taikun.tpsthings.blockentities.BEExampleMachine;
import jp.main.taikun.tpsthings.blocks.BlockAbsoluteLifeAnchor;
import jp.main.taikun.tpsthings.blocks.BlockAnnihilationChamber;
import jp.main.taikun.tpsthings.blocks.BlockDebugAccelerator;
import jp.main.taikun.tpsthings.blocks.BlockExtendedCrafter;
import jp.main.taikun.tpsthings.machines.BaseMachine;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.resource.BlockResourceInfo;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
        private static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(Tpsthings.MODID);
        public static final DeferredRegister<Block> SIMPLE_BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Tpsthings.MODID);
        public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tpsthings.MODID);
        public static final BlockRegistryObject<BlockTile.BlockTileModel<BEExampleMachine, Machine<BEExampleMachine>>, BlockItem> EXAMPLE_MACHINE =
                        BLOCKS.register("example_machine", () -> new BlockTile.BlockTileModel<>(ModBlockTypes.EXAMPLE_MACHINE, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor())));
        public static final BlockRegistryObject<BlockTile.BlockTileModel<BETimeFluxCollector, Machine<BETimeFluxCollector>>, BlockItem> TIME_FLUX_COLLECTOR =
                        BLOCKS.register("time_flux_collector", () -> new BlockTile.BlockTileModel<>(ModBlockTypes.TIME_FLUX_COLLECTOR, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor())));
        public static final BlockRegistryObject<BlockTile.BlockTileModel<BETimeAccelerator, Machine<BETimeAccelerator>>, BlockItem> TIME_ACCELERATOR =
                        BLOCKS.register("time_accelerator", () -> new BlockTile.BlockTileModel<>(ModBlockTypes.TIME_ACCELERATOR, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor())));

        public static final BlockRegistryObject<BlockTile.BlockTileModel<BETpsGenerator, Machine<BETpsGenerator>>, BlockItem> TPS_GENERATOR =
                        BLOCKS.register("tps_generator", () -> new BlockTile.BlockTileModel<>(ModBlockTypes.TPS_GENERATOR, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor())));
        public static final BlockRegistryObject<BlockTile.BlockTileModel<BELagGenerator, Machine<BELagGenerator>>, BlockItem> LAG_GENERATOR =
                        BLOCKS.register("lag_generator", () -> new BlockTile.BlockTileModel<>(ModBlockTypes.LAG_GENERATOR, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor())));

        private static RegistryObject<Block> registerBlockWithItem(String name, Supplier<? extends Block> blockSupplier) {
                RegistryObject<Block> block = SIMPLE_BLOCKS.register(name, blockSupplier);
                BLOCK_ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
                return block;
        }
        public static final RegistryObject<Block> DEBUG_ACCELERATOR = registerBlockWithItem("debug_accelerator", BlockDebugAccelerator::new);
        public static final RegistryObject<Block> ABSOLUTE_LIFE_ANCHOR = registerBlockWithItem("absolute_life_anchor", BlockAbsoluteLifeAnchor::new);
        public static final RegistryObject<Block> EXTENDED_CRAFTER = registerBlockWithItem("extended_crafter", BlockExtendedCrafter::new);
        public static final RegistryObject<Block> ANNIHILATION_CHAMBER = registerBlockWithItem("annihilation_chamber", BlockAnnihilationChamber::new);
        public static void register(IEventBus bus) {
                // ループ内からジェネリックメソッドを呼び出す
                MachineRegistry.INSTANCE.forEachMachine(ModBlocks::registerSingleMachine);

                BLOCKS.register(bus);
                SIMPLE_BLOCKS.register(bus);
                BLOCK_ITEMS.register(bus);
        }
        // 追加するヘルパーメソッド: ここで <BE> を明示することで、Javaの型推論を助けます
        private static <BE extends TileEntityMekanism> void registerSingleMachine(BaseMachine<BE> machine) {
                machine.machineBlock = BLOCKS.register(machine.getId(),
                        () -> new BlockTile.BlockTileModel<>(machine.machineBlockType,
                                properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor()))
                );
        }
}