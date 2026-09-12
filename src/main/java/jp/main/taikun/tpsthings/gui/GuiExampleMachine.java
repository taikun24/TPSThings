package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.blockentities.BEExampleMachine;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class GuiExampleMachine extends GuiMekanismTile<BEExampleMachine, MekanismTileContainer<BEExampleMachine>> {
    public GuiExampleMachine(MekanismTileContainer<BEExampleMachine> container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        drawString(guiGraphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}