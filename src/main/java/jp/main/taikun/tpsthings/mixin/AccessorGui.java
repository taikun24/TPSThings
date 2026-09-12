package jp.main.taikun.tpsthings.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface AccessorGui {
    @Accessor("toolHighlightTimer")
    int tpsthings$getToolHighlightTimer();

    @Accessor("lastToolHighlight")
    ItemStack tpsthings$getLastToolHighlight();
}
