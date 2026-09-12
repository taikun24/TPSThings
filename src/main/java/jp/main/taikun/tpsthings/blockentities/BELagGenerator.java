package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.registries.ModBlocks;
import jp.main.taikun.tpsthings.time.TpsMeter;
import mekanism.api.math.FloatingLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * サーバの調子が悪いほど発電する。逆風側。
 *
 * 20 TPS では何も出ず、落ち込むほど出る。
 * {@link BETpsGenerator} と 2 台並べておくと、どちらに転んでも電気は止まらない。
 * 片方だけ置くと、その片方が一番欲しい局面で止まる。
 */
public class BELagGenerator extends BETpsGeneratorBase {

    /** 完全に停止 (0 TPS) したときの出力。 */
    private static final FloatingLong PEAK_OUTPUT = FloatingLong.createConst(20_000L);
    /** これ以上では何も出ない。平常運転を燃料にはできない。 */
    private static final float CEILING_TPS = 19.0F;

    public BELagGenerator(BlockPos pos, BlockState state) {
        super(ModBlocks.LAG_GENERATOR, pos, state);
    }

    @Override
    protected FloatingLong outputFor(float tps) {
        if (tps >= CEILING_TPS) {
            return FloatingLong.ZERO;
        }
        float ratio = (CEILING_TPS - tps) / CEILING_TPS;
        return PEAK_OUTPUT.multiply(Math.min(1.0F, ratio));
    }

    @Override
    public net.minecraft.network.chat.Component getConditionText() {
        return net.minecraft.network.chat.Component.translatable("gui.tpsthings.lag_generator.condition");
    }
}
