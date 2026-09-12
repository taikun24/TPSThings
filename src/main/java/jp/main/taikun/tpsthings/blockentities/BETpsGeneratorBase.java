package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.time.TpsMeter;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IConfigurable;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableFloat;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.CableUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * サーバの調子そのものを燃料にする発電機の土台。
 *
 * 何も投入しない。TPS を読んで、そこから発電する。
 * 「どういう TPS のときに発電するか」だけを {@link #outputFor(float)} で差し替える。
 */
public abstract class BETpsGeneratorBase extends TileEntityMekanism implements IConfigurable {

    /** 蓄えておける量。 */
    private static final FloatingLong CAPACITY = FloatingLong.createConst(4_000_000L);

    private BasicEnergyContainer energyContainer;
    private EnergyInventorySlot energySlot;

    /** 表示用。クライアントへ同期されるが、判断には使わない。 */
    private float displayTps = TpsMeter.NOMINAL_TPS;
    private float displayOutput = 0.0F;

    protected BETpsGeneratorBase(IBlockProvider blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }

    /**
     * その TPS のとき 1 tick に何 J 出すか。
     *
     * @param tps ならした TPS (0 〜 20)
     */
    protected abstract FloatingLong outputFor(float tps);

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onRightClick(Player player) {
        return openGui(player);
    }

    // ---- 中身の口 ---------------------------------------------------------------

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSide(this::getDirection);
        builder.addContainer(energyContainer = BasicEnergyContainer.output(CAPACITY, listener),
                RelativeSide.BACK, RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT,
                RelativeSide.TOP, RelativeSide.FRONT);
        return builder.build();
    }

    @Override
    protected @Nullable IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSide(this::getDirection);
        builder.addSlot(energySlot = EnergyInventorySlot.drain(energyContainer, listener, 143, 35),
                RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT,
                RelativeSide.TOP, RelativeSide.BACK);
        return builder.build();
    }

    public BasicEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    // ---- 動作 -------------------------------------------------------------------

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        energySlot.drainContainer();

        float tps = TpsMeter.averageTps();
        displayTps = tps;

        FloatingLong produced = FloatingLong.ZERO;
        if (MekanismUtils.canFunction(this)) {
            FloatingLong wanted = outputFor(tps);
            if (!wanted.isZero()) {
                // 入る分しか作らない。溢れさせても意味がない
                FloatingLong room = energyContainer.getMaxEnergy().subtract(energyContainer.getEnergy());
                FloatingLong target = wanted.smallerThan(room) ? wanted : room;
                if (!target.isZero()) {
                    produced = target.subtract(
                            energyContainer.insert(target, Action.EXECUTE, AutomationType.INTERNAL));
                }
            }
        }
        displayOutput = produced.floatValue();
        setActive(!produced.isZero());

        // 溜め込んでも仕方がないので、繋がっている先へ出せるだけ出す
        CableUtils.emit(energyContainer, this);
    }

    // ---- 表示 -------------------------------------------------------------------

    public float getDisplayTps() {
        return displayTps;
    }

    public float getDisplayOutput() {
        return displayOutput;
    }

    /** 画面に出す一言。何をしているときに回るのかが伝わらないと理不尽に見える。 */
    public abstract String getConditionText();

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableFloat.create(this::getDisplayTps, value -> displayTps = value));
        container.track(SyncableFloat.create(this::getDisplayOutput, value -> displayOutput = value));
    }
}
