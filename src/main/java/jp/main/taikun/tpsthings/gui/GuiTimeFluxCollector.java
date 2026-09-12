package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.blockentities.BETimeFluxCollector;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class GuiTimeFluxCollector extends GuiMekanismTile<BETimeFluxCollector, MekanismTileContainer<BETimeFluxCollector>> {
     public GuiTimeFluxCollector(MekanismTileContainer<BETimeFluxCollector> container, Inventory inv, Component title) {
         super(container, inv, title);
         titleLabelY = 5;
         inventoryLabelY += 2;
         dynamicSlots = true;
     }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 164, 15))
                .warning(WarningTracker.WarningType.NOT_ENOUGH_ENERGY, () -> {
                    MachineEnergyContainer<BETimeFluxCollector> energyContainer = tile.getEnergyContainer();
                    return energyContainer.getEnergyPerTick().greaterThan(energyContainer.getEnergy());
                });
        addRenderableWidget(new GuiGasGauge(() -> tile.chemicalTank, ()-> tile.getGasTanks(null), GaugeType.STANDARD, this, 6, 13))
                .warning(WarningTracker.WarningType.NO_SPACE_IN_OUTPUT, () -> tile.chemicalTank.getNeeded() <= 10);
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(),()-> tile.getEnergyContainer().getEnergyPerTick()));
    }

    @Override
     protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
         renderTitleText(guiGraphics);
         drawString(guiGraphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
         super.drawForegroundText(guiGraphics, mouseX, mouseY);
     }
}
