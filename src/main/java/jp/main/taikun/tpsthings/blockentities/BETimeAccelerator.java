package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.registries.ModBlocks;
import jp.main.taikun.tpsthings.registries.ModGases;
import jp.main.taikun.tpsthings.time.TickUtil;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IConfigurable;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.chemical.ChemicalTankHelper;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.chemical.GasInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 周囲の機械の時間を進める。タイムフラックスを消費する唯一の機械。
 *
 * 範囲と速さを画面から変えられる。どちらを上げても費用は素直に増える。
 * 上げるほど自分でサーバを重くしていくので、加速したいのか軽くしたいのかは
 * 置く側が決めることになる。
 */
public class BETimeAccelerator extends TileEntityMekanism implements IConfigurable {

    public static final int MIN_RANGE = 0;
    public static final int MAX_RANGE = 8;
    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 32;

    /** 加速 1 回 (対象 1 台を 1 tick 進める) あたりのエネルギー。 */
    private static final FloatingLong ENERGY_PER_STEP = FloatingLong.createConst(60L);
    /** 加速何回ぶんでタイムフラックス 1 mB を使うか。 */
    private static final int STEPS_PER_GAS = 20;
    /**
     * 対象を数え直す間隔。
     *
     * 範囲 8 だと 17^3 = 4913 マスを見ることになる。毎 tick やると、加速する前に
     * 自分が重さの原因になる。置かれた機械はそう頻繁には増減しない。
     */
    private static final int RESCAN_TICKS = 40;

    private IGasTank fluxTank;
    private MachineEnergyContainer<BETimeAccelerator> energyContainer;
    private GasInventorySlot fluxSlot;
    private EnergyInventorySlot energySlot;

    private int range = 2;
    private int speed = 4;

    /** 加速対象の位置。サーバ側だけが持つ。 */
    private List<BlockPos> targets = List.of();
    private int rescanIn = 0;

    /** 表示用。クライアントへ同期される。 */
    private int displayTargets = 0;
    private int displayRunning = 0;

    public BETimeAccelerator(BlockPos pos, BlockState state) {
        super(ModBlocks.TIME_ACCELERATOR, pos, state);
    }

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onRightClick(Player player) {
        return openGui(player);
    }

    // ---- 中身の口 ---------------------------------------------------------------

    @Override
    public @Nullable IChemicalTankHolder<Gas, GasStack, IGasTank> getInitialGasTanks(IContentsListener listener) {
        ChemicalTankHelper<Gas, GasStack, IGasTank> builder = ChemicalTankHelper.forSide(this::getDirection);
        builder.addTank(fluxTank = ChemicalTankBuilder.GAS.input(
                        10L * FluidType.BUCKET_VOLUME, gas -> gas == ModGases.TIME_FLUX.getChemical(), listener),
                RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT,
                RelativeSide.BACK, RelativeSide.TOP);
        return builder.build();
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSide(this::getDirection);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener),
                RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT,
                RelativeSide.BACK, RelativeSide.TOP);
        return builder.build();
    }

    @Override
    protected @Nullable IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSide(this::getDirection);
        builder.addSlot(fluxSlot = GasInventorySlot.fill(fluxTank, listener, 28, 56),
                RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT,
                RelativeSide.BACK, RelativeSide.TOP);
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(
                        energyContainer, this::getLevel, listener, 152, 56),
                RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT,
                RelativeSide.BACK, RelativeSide.TOP);
        return builder.build();
    }

    public MachineEnergyContainer<BETimeAccelerator> getEnergyContainer() {
        return energyContainer;
    }

    public IGasTank getFluxTank() {
        return fluxTank;
    }

    // ---- 設定 -------------------------------------------------------------------

    public int getRange() {
        return range;
    }

    public int getSpeed() {
        return speed;
    }

    /** 画面から届いた値を反映する。範囲外は丸める。 */
    public void applySettings(int newRange, int newSpeed) {
        int clampedRange = Math.max(MIN_RANGE, Math.min(newRange, MAX_RANGE));
        int clampedSpeed = Math.max(MIN_SPEED, Math.min(newSpeed, MAX_SPEED));
        if (clampedRange != range) {
            range = clampedRange;
            rescanIn = 0; // 範囲が変わったら、覚えている対象は当てにならない
        }
        speed = clampedSpeed;
        setChanged();
    }

    /** いま範囲内にある加速対象の数。 */
    public int getDisplayTargets() {
        return displayTargets;
    }

    /** 実際に回っているか。費用が足りていないと 0 のままになる。 */
    public int getDisplayRunning() {
        return displayRunning;
    }

    /** 1 tick に消費するタイムフラックス。画面で見せるために公開する。 */
    public long getGasPerTick() {
        return gasFor(displayTargets * speed);
    }

    /** 1 tick に消費するエネルギー。 */
    public FloatingLong getEnergyPerTick() {
        return ENERGY_PER_STEP.multiply(displayTargets * speed);
    }

    private static long gasFor(int steps) {
        return steps <= 0 ? 0L : Math.max(1L, steps / STEPS_PER_GAS);
    }

    // ---- 動作 -------------------------------------------------------------------

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        fluxSlot.fillTank();
        energySlot.fillContainerOrConvert();

        if (rescanIn-- <= 0) {
            rescanIn = RESCAN_TICKS;
            targets = scanTargets();
        }
        displayTargets = targets.size();
        displayRunning = 0;

        if (!MekanismUtils.canFunction(this) || targets.isEmpty()) {
            setActive(false);
            return;
        }

        int steps = targets.size() * speed;
        long gasNeeded = gasFor(steps);
        FloatingLong energyNeeded = ENERGY_PER_STEP.multiply(steps);

        boolean affordable = fluxTank.getStored() >= gasNeeded
                && energyContainer.extract(energyNeeded, Action.SIMULATE, AutomationType.INTERNAL)
                        .equals(energyNeeded);
        if (!affordable) {
            setActive(false);
            return;
        }
        fluxTank.shrinkStack(gasNeeded, Action.EXECUTE);
        energyContainer.extract(energyNeeded, Action.EXECUTE, AutomationType.INTERNAL);

        for (BlockPos target : targets) {
            TickUtil.tick(level, target, speed);
        }
        displayRunning = 1;
        setActive(true);
    }

    /**
     * 範囲内で加速できるものを数え直す。
     *
     * BlockEntity を持たないブロックは進めようがないので落とす。
     * 加速器そのものは {@link TickUtil} 側の除外で弾かれるが、
     * ここで先に落としておけば無駄な呼び出しも消える。
     */
    private List<BlockPos> scanTargets() {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                worldPosition.offset(-range, -range, -range),
                worldPosition.offset(range, range, range))) {
            if (pos.equals(worldPosition) || !level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) == null) {
                continue;
            }
            if (TickUtil.BLACKLIST.contains(level.getBlockState(pos).getBlock())) {
                continue;
            }
            found.add(pos.immutable());
        }
        return found;
    }

    // ---- 保存と同期 -------------------------------------------------------------

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("tpsthings_range", range);
        tag.putInt("tpsthings_speed", speed);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("tpsthings_range")) {
            range = Math.max(MIN_RANGE, Math.min(tag.getInt("tpsthings_range"), MAX_RANGE));
        }
        if (tag.contains("tpsthings_speed")) {
            speed = Math.max(MIN_SPEED, Math.min(tag.getInt("tpsthings_speed"), MAX_SPEED));
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(this::getRange, value -> range = value));
        container.track(SyncableInt.create(this::getSpeed, value -> speed = value));
        container.track(SyncableInt.create(this::getDisplayTargets, value -> displayTargets = value));
        container.track(SyncableInt.create(this::getDisplayRunning, value -> displayRunning = value));
    }
}
