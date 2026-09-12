package jp.main.taikun.tpsthings;


import jp.main.taikun.tpsthings.gui.SugoiMenuOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = Tpsthings.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onOverlayRegistry(final RegisterGuiOverlaysEvent event) {
        Tpsthings.LOGGER.info("Registering GUI overlay");
        event.registerAboveAll("sugoi_menu", new SugoiMenuOverlay());
    }
}
