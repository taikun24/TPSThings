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
    ModelFile idle = models().orientable("block/" + id,
            mcLoc("block/iron_block"), modLoc("block/" + id + "_front"), mcLoc("block/iron_block"));
    ModelFile active = models().orientable("block/" + id + "_active",
            mcLoc("block/iron_block"), modLoc("block/" + id + "_front_active"), mcLoc("block/iron_block"));

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
    ModelFile chamber = models().cubeBottomTop("annihilation_chamber",
            mcLoc("block/reinforced_deepslate_side"), mcLoc("block/reinforced_deepslate_bottom"), mcLoc("block/reinforced_deepslate_top"));
    simpleBlock(ModBlocks.ANNIHILATION_CHAMBER.get(), chamber);
    simpleBlockItem(ModBlocks.ANNIHILATION_CHAMBER.get(), chamber);
    machineWithItem(ModBlocks.TPS_GENERATOR.getBlock(), "tps_generator");
    machineWithItem(ModBlocks.LAG_GENERATOR.getBlock(), "lag_generator");
    machineWithItem(ModBlocks.TIME_ACCELERATOR.getBlock(), "time_accelerator");
    /*
    MachineRegistry.INSTANCE.forEachMachine(baseMachine -> {
        Block block = baseMachine.machineBlock.getBlock();

        getVariantBuilder(block).forAllStates(state -> {
            // 1. "active" プロパティの取得 (Boolean)
            boolean active = state.getProperties().stream()
                    .filter(p -> p.getName().equals("active") && p instanceof net.minecraft.world.level.block.state.properties.BooleanProperty)
                    .map(p -> state.getValue((net.minecraft.world.level.block.state.properties.BooleanProperty) p))
                    .findFirst()
                    .orElse(false);

            // 2. "facing" プロパティの取得 (Direction)
            net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);

            // 3. "fluid_logged" プロパティの取得 (Enum)
            // JSONの出力として "empty", "water", "lava" が文字列で入るため、持っているPropertyの値の値を文字列として取得します
            String fluidLogged = state.getProperties().stream()
                    .filter(p -> p.getName().equals("fluid_logged"))
                    .map(p -> state.getValue(p).toString().toLowerCase(java.util.Locale.ROOT))
                    .findFirst()
                    .orElse("empty"); // デフォルト値

            // アクティブ状態によるモデルファイルの切り替え
            String modelName = active ? baseMachine.getId() + "_active" : baseMachine.getId();

            // 向きによる回転角度の決定 (JSONに合わせて負数ではなく正数に丸める場合は 270 にします)
            int yRotation = switch (facing) {
                case NORTH -> 0;
                case SOUTH -> 180;
                case EAST  -> 90;
                case WEST  -> 270; // JSONの -90 と同じ回転になります（270推奨ですが、既存データ互換で必要なら -90 でも機能します）
                default    -> 0;
            };

            return ConfiguredModel.builder()
                    .modelFile(models().getExistingFile(modLoc("block/" + modelName)))
                    .rotationY(yRotation)
                    .build();
        });
    });*/
}
}
