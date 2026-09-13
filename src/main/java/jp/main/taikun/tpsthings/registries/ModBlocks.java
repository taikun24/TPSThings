package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.blockentities.BETimeAccelerator;
import jp.main.taikun.tpsthings.blockentities.BETimeFluxCollector;
import jp.main.taikun.tpsthings.blockentities.BELagGenerator;
import jp.main.taikun.tpsthings.blockentities.BETpsGenerator;
import jp.main.taikun.tpsthings.blocks.BlockDebugAccelerator;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.resource.BlockResourceInfo;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
        private static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(Tpsthings.MODID);
        public static final DeferredRegister<Block> SIMPLE_BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Tpsthings.MODID);
        public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tpsthings.MODID);
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
        /** 反物質の保管形。インゴット 9 個で 1 つ、9 つ重ねると濃縮側になる */
        public static final RegistryObject<Block> ANTIMATTER_BLOCK = registerBlockWithItem("antimatter_block",
                        () -> new Block(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).lightLevel(state -> 7)));
        public static final RegistryObject<Block> COMPRESSED_ANTIMATTER_BLOCK = registerBlockWithItem("compressed_antimatter_block",
                        () -> new Block(BlockBehaviour.Properties.of().strength(25.0F, 1200.0F).lightLevel(state -> 11)));
        public static void register(IEventBus bus) {
                BLOCKS.register(bus);
                SIMPLE_BLOCKS.register(bus);
                BLOCK_ITEMS.register(bus);
        }
}