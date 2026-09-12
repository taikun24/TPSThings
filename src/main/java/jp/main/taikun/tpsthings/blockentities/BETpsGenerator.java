package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.registries.ModBlocks;
import jp.main.taikun.tpsthings.time.TpsMeter;
import mekanism.api.math.FloatingLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * サーバの調子が良いほど発電する。順風側。
 *
 * 20 TPS で最大、下がるほど落ちて、{@link #FLOOR_TPS} で止まる。
 * 素直に強いが、重くなると真っ先に死ぬので、これ 1 台に頼ると停電する。
 */
public class BETpsGenerator extends BETpsGeneratorBase {

    /** 20 TPS のときの出力。 */
    private static final FloatingLong PEAK_OUTPUT = FloatingLong.createConst(20_000L);
    /** これ以下では何も出ない。 */
    private static final float FLOOR_TPS = 10.0F;

    public BETpsGenerator(BlockPos pos, BlockState state) {
        super(ModBlocks.TPS_GENERATOR, pos, state);
    }

    @Override
    protected FloatingLong outputFor(float tps) {
        if (tps <= FLOOR_TPS) {
            return FloatingLong.ZERO;
        }
        float ratio = (tps - FLOOR_TPS) / (TpsMeter.NOMINAL_TPS - FLOOR_TPS);
        return PEAK_OUTPUT.multiply(Math.min(1.0F, ratio));
    }

    @Override
    public String getConditionText() {
        return "軽いほど回る";
    }
}
