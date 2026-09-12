package jp.main.taikun.tpsthings.gui;

import jp.main.taikun.tpsthings.blockentities.BETimeAccelerator;
import jp.main.taikun.tpsthings.network.ModNetwork;
import jp.main.taikun.tpsthings.network.PacketAcceleratorSettings;
import mekanism.api.text.ILangEntry;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * 時流加速機の画面。
 *
 * 範囲と速さを 4 つのボタンで動かす。押した結果はサーバで丸められて返ってくるので、
 * ここで上限を守れなくても壊れはしない。
 */
public class GuiTimeAccelerator extends GuiMekanismTile<BETimeAccelerator, MekanismTileContainer<BETimeAccelerator>> {

    /** ボタンの見出し。Mekanism の部品が翻訳キーを要求するので、薄く包む。 */
    private record Label(String key) implements ILangEntry {
        @Override
        public String getTranslationKey() {
            return key;
        }
    }

    private static final Label MINUS = new Label("gui.tpsthings.minus");
    private static final Label PLUS = new Label("gui.tpsthings.plus");

    public GuiTimeAccelerator(MekanismTileContainer<BETimeAccelerator> container, Inventory inv, Component title) {
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
                    MachineEnergyContainer<BETimeAccelerator> energy = tile.getEnergyContainer();
                    return tile.getEnergyPerTick().greaterThan(energy.getEnergy());
                });
        addRenderableWidget(new GuiGasGauge(tile::getFluxTank, () -> tile.getGasTanks(null),
                GaugeType.STANDARD, this, 6, 13))
                .warning(WarningTracker.WarningType.NO_MATCHING_RECIPE, () -> tile.getFluxTank().isEmpty());

        addRenderableWidget(new TranslationButton(this, 100, 22, 14, 14, MINUS,
                () -> send(tile.getRange() - 1, tile.getSpeed())));
        addRenderableWidget(new TranslationButton(this, 140, 22, 14, 14, PLUS,
                () -> send(tile.getRange() + 1, tile.getSpeed())));
        addRenderableWidget(new TranslationButton(this, 100, 40, 14, 14, MINUS,
                () -> send(tile.getRange(), halved(tile.getSpeed()))));
        addRenderableWidget(new TranslationButton(this, 140, 40, 14, 14, PLUS,
                () -> send(tile.getRange(), doubled(tile.getSpeed()))));

        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getEnergyPerTick));
    }

    /** 速さは倍々で動かす。1 ずつだと 32 まで遠い。 */
    private static int doubled(int speed) {
        return Math.min(BETimeAccelerator.MAX_SPEED, speed * 2);
    }

    private static int halved(int speed) {
        return Math.max(BETimeAccelerator.MIN_SPEED, speed / 2);
    }

    private void send(int range, int speed) {
        ModNetwork.CHANNEL.sendToServer(
                new PacketAcceleratorSettings(tile.getBlockPos(), range, speed));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        drawString(guiGraphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());

        int range = tile.getRange();
        int diameter = range * 2 + 1;
        drawString(guiGraphics, Component.literal("範囲 " + range + " (" + diameter + "角)")
                .withStyle(ChatFormatting.WHITE), 40, 26, titleTextColor());
        drawString(guiGraphics, Component.literal("速さ x" + tile.getSpeed())
                .withStyle(ChatFormatting.WHITE), 40, 44, titleTextColor());

        int targets = tile.getDisplayTargets();
        boolean running = tile.getDisplayRunning() != 0;
        drawString(guiGraphics, Component.literal(targets == 0
                        ? "対象なし"
                        : targets + " 台 / " + (running ? "稼働中" : "資源不足"))
                .withStyle(targets == 0 ? ChatFormatting.DARK_GRAY
                        : running ? ChatFormatting.GREEN : ChatFormatting.RED), 40, 62, titleTextColor());

        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
