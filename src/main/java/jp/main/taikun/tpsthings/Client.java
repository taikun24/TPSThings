package jp.main.taikun.tpsthings;

import com.mojang.blaze3d.platform.InputConstants;
import jp.main.taikun.tpsthings.gui.SugoiMenuOverlay;
import jp.main.taikun.tpsthings.gui.ClientDeathGuard;
import jp.main.taikun.tpsthings.gui.DirectControlClient;
import jp.main.taikun.tpsthings.items.ItemOo;
import jp.main.taikun.tpsthings.items.OoEquivalent;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = Tpsthings.MODID, value = Dist.CLIENT)
public class Client {
    /**
     * 嘘の死亡画面を閉じる見回り。
     *
     * 出す口は塞いであるが、守りが間に合う前に一度開かれることはある。
     * 開きっぱなしだと、生きているのに操作できない状態が残る。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event){
        if (event.phase == TickEvent.Phase.END) {
            // tick の最後に置く。途中で誰が自機を動かしても、ここで決めた位置が残る
            DirectControlClient.tick();
            ClientDeathGuard.tick();
        }
    }
    /**
     * メニューを開けるか。おおを<b>手に持っている</b>か、<b>胸に着ている</b>か。
     *
     * <p>手だけを見ていると、防具として着ている間はメニューが開けない。着ている間こそ
     * 防御の設定を触りたいのに、そのためにわざわざ脱いで持ち替えることになる。
     * 着ているかどうかの判定は装備由来の挙動と同じ {@link ItemOo#isWorn} に揃える。
     */
    private static boolean canOpenMenu(){
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return false;
        return OoEquivalent.isOo(localPlayer.getMainHandItem()) || ItemOo.isWorn(localPlayer);
    }
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event){
        // 生のキー番号と比べると、キーコンフィグで割り当てを変えても付いてこない。割り当て側に判定させる
        if (KeyBindings.SUGOI_MENU.matches(event.getKey(), event.getScanCode())){
            if (event.getAction() == GLFW.GLFW_RELEASE) SugoiMenuOverlay.hide();
            else if (canOpenMenu() && event.getAction() == GLFW.GLFW_PRESS) SugoiMenuOverlay.show();

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
