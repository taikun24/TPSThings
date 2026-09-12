package jp.main.taikun.tpsthings.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** 後処理の各パスに uniform を毎フレーム流し込むため。1.20.1 の PostChain は uniform を外から設定できない。 */
@Mixin(PostChain.class)
public interface AccessorPostChain {
    @Accessor("passes")
    List<PostPass> tpsthings$getPasses();
}
