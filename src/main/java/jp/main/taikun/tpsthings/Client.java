package jp.main.taikun.tpsthings;

import com.mojang.blaze3d.platform.InputConstants;
import jp.main.taikun.tpsthings.gui.SugoiMenuOverlay;
import jp.main.taikun.tpsthings.gui.ClientDeathGuard;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = Tpsthings.MODID, value = Dist.CLIENT)
public class Client {
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event){
        event.register(KeyBindings.SUGOI_MENU);
    }

    /**
     * 嘘の死亡画面を閉じる見回り。
     *
     * 出す口は塞いであるが、守りが間に合う前に一度開かれることはある。
     * 開きっぱなしだと、生きているのに操作できない状態が残る。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event){
        if (event.phase == TickEvent.Phase.END) {
            ClientDeathGuard.tick();
        }
    }
    private static boolean isHoldingOo(){
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return false;
        return localPlayer.getMainHandItem().is(ModItems.OO.get());
    }
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event){
        // 生のキー番号と比べると、キーコンフィグで割り当てを変えても付いてこない。割り当て側に判定させる
        if (KeyBindings.SUGOI_MENU.matches(event.getKey(), event.getScanCode())){
            if (event.getAction() == GLFW.GLFW_RELEASE) SugoiMenuOverlay.hide();
            else if (isHoldingOo() && event.getAction() == GLFW.GLFW_PRESS) SugoiMenuOverlay.show();

        }
    }
    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event){
        if (SugoiMenuOverlay.isEnabled() && event.getAction() == GLFW.GLFW_PRESS &&  event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            event.setCanceled(true);
            SugoiMenuOverlay.click();
        }

    }
    @SubscribeEvent
    public static void onWheelInput(InputEvent.MouseScrollingEvent event){
        if (SugoiMenuOverlay.isEnabled()){
            SugoiMenuOverlay.scroll(event.getScrollDelta());
            event.setCanceled(true);
        }
    }
    public static class KeyBindings {
        public static final KeyMapping SUGOI_MENU = new KeyMapping(
                "key.tpsthings.sugoi_menu",
                KeyConflictContext.IN_GAME,
                // GLFW_KEY_V はキーコード (KEYSYM)。SCANCODE 扱いにすると別の物理キーを指してしまう
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "key.categories.tpsthings"
        );
    }
}
