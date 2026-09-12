package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.machines.BaseMachine;
import jp.main.taikun.tpsthings.machines.TestMachine;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.function.Consumer;

public class MachineRegistry {
    private final Set<BaseMachine<? extends TileEntityMekanism>> machines;
    public static final MachineRegistry INSTANCE = new MachineRegistry();

    public MachineRegistry() {
        this.machines = Set.of(
                new TestMachine()
        );
    }
    public BaseMachine<? extends TileEntityMekanism> getMachine(String id) {
        for (BaseMachine<? extends TileEntityMekanism> machine : machines) {
            if (machine.getId().equals(id)) {
                return machine;
            }
        }
        return null;
    }
    public void forEachMachine(Consumer<BaseMachine<? extends TileEntityMekanism>> consumer) {
        machines.forEach(consumer);
    }
}
