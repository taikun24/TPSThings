package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import mekanism.common.block.attribute.Attributes;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, Tpsthings.MODID, exFileHelper);
    }
/*
* {
  "variants": {
    "active=false,facing=north,fluid_logged=empty": { "model": "tpsthings:block/example_machine" },
    "active=false,facing=south,fluid_logged=empty": { "model": "tpsthings:block/example_machine", "y": 180 },
    "active=false,facing=east,fluid_logged=empty": { "model": "tpsthings:block/example_machine", "y": 90 },
    "active=false,facing=west,fluid_logged=empty": { "model": "tpsthings:block/example_machine", "y": -90 },
    "active=true,facing=north,fluid_logged=empty": { "model": "tpsthings:block/example_machine_active" },
    "active=true,facing=south,fluid_logged=empty": { "model": "tpsthings:block/example_machine_active", "y": 180 },
    "active=true,facing=east,fluid_logged=empty": { "model": "tpsthings:block/example_machine_active", "y": 90 },
    "active=true,facing=west,fluid_logged=empty": { "model": "tpsthings:block/example_machine_active", "y": -90 },
    "active=false,facing=north,fluid_logged=water": { "model": "tpsthings:block/example_machine" },
    "active=false,facing=south,fluid_logged=water": { "model": "tpsthings:block/example_machine", "y": 180 },
    "active=false,facing=east,fluid_logged=water": { "model": "tpsthings:block/example_machine", "y": 90 },
    "active=false,facing=west,fluid_logged=water": { "model": "tpsthings:block/example_machine", "y": -90 },
    "active=true,facing=north,fluid_logged=water": { "model": "tpsthings:block/example_machine_active" },
    "active=true,facing=south,fluid_logged=water": { "model": "tpsthings:block/example_machine_active", "y": 180 },
    "active=true,facing=east,fluid_logged=water": { "model": "tpsthings:block/example_machine_active", "y": 90 },
    "active=true,facing=west,fluid_logged=water": { "model": "tpsthings:block/example_machine_active", "y": -90 },
    "active=false,facing=north,fluid_logged=lava": { "model": "tpsthings:block/example_machine" },
    "active=false,facing=south,fluid_logged=lava": { "model": "tpsthings:block/example_machine", "y": 180 },
    "active=false,facing=east,fluid_logged=lava": { "model": "tpsthings:block/example_machine", "y": 90 },
    "active=false,facing=west,fluid_logged=lava": { "model": "tpsthings:block/example_machine", "y": -90 },
    "active=true,facing=north,fluid_logged=lava": { "model": "tpsthings:block/example_machine_active" },
    "active=true,facing=south,fluid_logged=lava": { "model": "tpsthings:block/example_machine_active", "y": 180 },
    "active=true,facing=east,fluid_logged=lava": { "model": "tpsthings:block/example_machine_active", "y": 90 },
    "active=true,facing=west,fluid_logged=lava": { "model": "tpsthings:block/example_machine_active", "y": -90 }
  }
}
* */
private void simpleBlockWithItem(Block block) {
    ModelFile model = cubeAll(block);
    simpleBlock(block, model);
    simpleBlockItem(block, model);
}
/**
 * Mekanism 機械 1 台分の状態とモデルを作る。
 *
 * 状態の顔ぶれ (active / facing / fluid_logged) はブロック側が持っているものをそのまま
 * 舐める。手で書き並べるとプロパティが 1 つ増えただけで読み込みに失敗するので。
 */
private void machineWithItem(Block block, String id) {
    // 絵は textures/block/<id>_side / _front / _front_active
    ModelFile idle = models().orientable("block/" + id,
            modLoc("block/" + id + "_side"), modLoc("block/" + id + "_front"), modLoc("block/" + id + "_side"));
    ModelFile active = models().orientable("block/" + id + "_active",
            modLoc("block/" + id + "_side"), modLoc("block/" + id + "_front_active"), modLoc("block/" + id + "_side"));

    getVariantBuilder(block).forAllStates(state -> {
        boolean running = state.getProperties().stream()
                .filter(property -> property.getName().equals("active"))
                .findFirst()
                .map(property -> Boolean.TRUE.equals(state.getValue(property)))
                .orElse(false);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        int rotation = switch (facing) {
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> 0;
        };
        return ConfiguredModel.builder()
                .modelFile(running ? active : idle)
                .rotationY(rotation)
                .build();
    });
    simpleBlockItem(block, idle);
}

@Override
protected void registerStatesAndModels() {
    simpleBlockWithItem(ModBlocks.DEBUG_ACCELERATOR.get());
    machineWithItem(ModBlocks.TPS_GENERATOR.getBlock(), "tps_generator");
    machineWithItem(ModBlocks.LAG_GENERATOR.getBlock(), "lag_generator");
    machineWithItem(ModBlocks.TIME_ACCELERATOR.getBlock(), "time_accelerator");
    machineWithItem(ModBlocks.TIME_FLUX_COLLECTOR.getBlock(), "time_flux_collector");
    simpleBlockWithItem(ModBlocks.ANTIMATTER_BLOCK.get());
    simpleBlockWithItem(ModBlocks.COMPRESSED_ANTIMATTER_BLOCK.get());

}
}
