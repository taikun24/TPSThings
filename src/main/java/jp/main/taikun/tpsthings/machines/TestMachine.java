package jp.main.taikun.tpsthings.machines;

import jp.main.taikun.tpsthings.blockentities.BEBaseMachine;
import jp.main.taikun.tpsthings.registries.MachineRegistry;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TestMachine extends BaseMachine<TestMachine.TestMachineBE> {
    @Override
    public String getId() {
        return "test_machine";
    }

    @Override
    public Class<TestMachineBE> getMachineClass() {
        return TestMachineBE.class;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<TestMachineBE> getMachineSupplier() {
        return TestMachineBE::new;
    }

    @Override
    public MenuScreens.ScreenConstructor<MekanismTileContainer<TestMachineBE>, GuiMekanismTile<TestMachineBE, MekanismTileContainer<TestMachineBE>>> getGuiSupplier() {
        return TestMachineGui::new;
    }

    public static class TestMachineBE extends BEBaseMachine {
        public TestMachineBE(BlockPos pos, BlockState state) {
            // ※注意: レジストリ登録のタイミングによっては MachineRegistry.INSTANCE がまだ空の場合があるため
            // 状況に応じて lazy に取得するか、通常の遅延登録オブジェクトを参照させてください。
            super(MachineRegistry.INSTANCE.getMachine("test_machine").machineBlock, pos, state);
        }
    }

    public static class TestMachineGui extends GuiMekanismTile<TestMachineBE, MekanismTileContainer<TestMachineBE>> {
        public TestMachineGui(MekanismTileContainer<TestMachineBE> container, Inventory inv, Component title) {
            super(container, inv, title);
        }

        @Override
        protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
            renderTitleText(guiGraphics);
            drawString(guiGraphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
            super.drawForegroundText(guiGraphics, mouseX, mouseY);
        }
    }
}