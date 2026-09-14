package jp.main.taikun.tpsthings;


import jp.main.taikun.tpsthings.gui.SugoiMenuOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = Tpsthings.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onOverlayRegistry(final RegisterGuiOverlaysEvent event) {
        Tpsthings.LOGGER.info("Registering GUI overlay");
        event.registerAboveAll("sugoi_menu", new SugoiMenuOverlay());
    }

    /**
     * キーコンフィグ画面への登録。
     *
     * このイベントは MOD バス側で出る。FORGE バスで待っていると呼ばれず、キー自体は効くのに
     * 設定画面の一覧に出てこなかった。
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(Client.KeyBindings.SUGOI_MENU);
    }
}
