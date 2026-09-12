package jp.main.taikun.tpsthings.mixin;

// https://github.com/Lapis256/TorcherinoCompat/blob/1.20.1/src/main/java/io/github/lapis256/torcherino_compat/mixin/AccessorTileEntityMultiblock.java
import mekanism.common.tile.prefab.TileEntityMultiblock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TileEntityMultiblock.class, remap = false)
public interface AccessorTileEntityMultiblock {
    @Accessor("isMaster")
    void setIsMaster(boolean isMaster);
}