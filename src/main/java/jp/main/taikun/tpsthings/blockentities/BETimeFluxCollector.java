package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.registries.ModBlocks;
import jp.main.taikun.tpsthings.registries.ModGases;
import mekanism.api.*;
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
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.chemical.GasInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;

// sorry, i used some code from mekmm (Mekanism: More Machines) for this, but i don't know how to do it without copying some of the code, so i hope it's fine
// mekmm is MIT licensed, so it should be fine to use the code as long as i give credit, which i am doing here
public class BETimeFluxCollector extends TileEntityMekanism implements IConfigurable {
    private MachineEnergyContainer<BETimeFluxCollector> energyContainer;
    public IGasTank chemicalTank;
    GasInventorySlot chemicalSlot;
    EnergyInventorySlot energySlot;

    private boolean usedEnergy = false;
    private boolean notBlocking = true;
    private static final int BASE_OUTPUT_RATE = 10;
    private static final int BASE_TICKS_REQUIRED = 19;
    public int operatingTicks = 0;
    private int outputRate = BASE_OUTPUT_RATE;
    public int ticksRequired = BASE_TICKS_REQUIRED;

    public BETimeFluxCollector( BlockPos pos, BlockState state) {
        super(ModBlocks.TIME_FLUX_COLLECTOR, pos, state);
    }

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        return null;
    }

    @Override
    public InteractionResult onRightClick(Player player) {
        return null;
    }

    @Override
    public @Nullable IChemicalTankHolder<Gas, GasStack, IGasTank> getInitialGasTanks(IContentsListener listener) {
        ChemicalTankHelper<Gas, GasStack, IGasTank> builder = ChemicalTankHelper.forSide(this::getDirection);
        builder.addTank(chemicalTank = ChemicalTankBuilder.GAS.output(10 * FluidType.BUCKET_VOLUME, listener), RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT, RelativeSide.BACK);
        return builder.build();
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSide(this::getDirection);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener), RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT, RelativeSide.BACK);
        return builder.build();
    }

    @Override
    protected @Nullable IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSide(this::getDirection);
        builder.addSlot(chemicalSlot = GasInventorySlot.drain(chemicalTank, listener, 28, 35), RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT, RelativeSide.BACK);
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer,this::getLevel, listener, 152, 35), RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT, RelativeSide.BACK);
        return builder.build();
    }

    public MachineEnergyContainer<BETimeFluxCollector> getEnergyContainer() {
        return energyContainer;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        chemicalSlot.drainTank();
        FloatingLong clientEnergyUsed = FloatingLong.ZERO;
        if (MekanismUtils.canFunction(this) && (chemicalTank.isEmpty() || estimateIncrementAmount() <= chemicalTank.getNeeded())) {
            FloatingLong energyPerTick = energyContainer.getEnergyPerTick();
            if (energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL).equals(energyPerTick)) {
                operatingTicks++;
                if (operatingTicks >= ticksRequired) {
                    operatingTicks = 0;
                    if (suck(worldPosition.relative(Direction.UP))) {
                        if (clientEnergyUsed.isZero()) {
                            // If it didn't already have an active type (hasn't used energy this tick), then extract
                            // energy
                            clientEnergyUsed = energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                        }
                    }
                }
            }
        }
        usedEnergy = !clientEnergyUsed.isZero();
        if (!chemicalTank.isEmpty()) {
            ChemicalUtil.emit(Collections.singleton(Direction.UP), chemicalTank, this, outputRate);
        }
    }
    private boolean suck(BlockPos pos) {
        Optional<BlockState> state = WorldUtils.getBlockState(level, pos);
        if (state.isPresent()) {
            BlockState blockState = state.get();
            Block block = blockState.getBlock();
            if (isAir(block)) {
                GasStack gasStack = new GasStack(ModGases.TIME_FLUX, estimateIncrementAmount());
                chemicalTank.insert(gasStack, Action.EXECUTE, AutomationType.INTERNAL);
                return true;
            }
        }
        return false;
    }
    private boolean isAir(Block block) {
        return notBlocking = block == Blocks.AIR;
    }
    public int estimateIncrementAmount() {
        return 1;
    }
    public boolean usedEnergy(){
        return usedEnergy;
    }
    public boolean isNotBlocking(){
        return notBlocking;
    }

    @Override
    public void recalculateUpgrades(Upgrade upgrade) {
        super.recalculateUpgrades(upgrade);
        if (upgrade == Upgrade.SPEED) {
            ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
            outputRate = BASE_OUTPUT_RATE * (1 + upgradeComponent.getUpgrades(Upgrade.SPEED));
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::usedEnergy, value -> usedEnergy = value));
        container.track(SyncableBoolean.create(this::isNotBlocking, value -> notBlocking = value));
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);
        operatingTicks = nbt.getInt(NBTConstants.PROGRESS);
    }
}
