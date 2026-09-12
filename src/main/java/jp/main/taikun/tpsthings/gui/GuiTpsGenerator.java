package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.blockentities.BETpsGeneratorBase;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * TPS 発電機の画面。順風側と逆風側で共通。
 *
 * いまの TPS と出力を出す。出ていないときに「なぜ出ていないのか」が読めないと、
 * 壊れているのか仕様なのか分からなくなる。
 */
public class GuiTpsGenerator<BE extends BETpsGeneratorBase>
        extends GuiMekanismTile<BE, MekanismTileContainer<BE>> {

    public GuiTpsGenerator(MekanismTileContainer<BE> container, Inventory inv, Component title) {
        super(container, inv, title);
        titleLabelY = 5;
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 164, 15));
        // 発電機なので MachineEnergyContainer ではない。タブには自前の文言を出す
        addRenderableWidget(new GuiEnergyTab(this, () -> List.of(
                Component.translatable("gui.tpsthings.generator.tps_tab", String.format("%.2f", tile.getDisplayTps())),
                Component.translatable("gui.tpsthings.generator.output_tab", String.format("%.0f", tile.getDisplayOutput())),
                tile.getConditionText())));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        drawString(guiGraphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());

        float tps = tile.getDisplayTps();
        float output = tile.getDisplayOutput();
        ChatFormatting tpsColor = tps >= 19.0F ? ChatFormatting.GREEN
                : tps >= 15.0F ? ChatFormatting.YELLOW : ChatFormatting.RED;

        drawString(guiGraphics, Component.translatable("gui.tpsthings.generator.tps", String.format("%.2f", tps))
                .withStyle(tpsColor), 20, 22, titleTextColor());
        drawString(guiGraphics, (output > 0.0F
                        ? Component.translatable("gui.tpsthings.generator.output", String.format("%.0f", output))
                        : Component.translatable("gui.tpsthings.generator.idle"))
                .withStyle(output > 0.0F ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY),
                20, 34, titleTextColor());
        drawString(guiGraphics, tile.getConditionText().copy()
                .withStyle(ChatFormatting.GRAY), 20, 46, titleTextColor());

        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
