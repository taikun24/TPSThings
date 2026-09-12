package jp.main.taikun.tpsthings.machines;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.common.MekanismLang;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;

public abstract class BaseMachine<BE extends TileEntityMekanism> {
    public abstract String getId();
    public abstract Class<BE> getMachineClass();
    public abstract BlockEntityType.BlockEntitySupplier<BE> getMachineSupplier();

    public BlockRegistryObject<BlockTile.BlockTileModel<BE, Machine<BE>>, BlockItem> machineBlock;
    public TileEntityTypeRegistryObject<BE> blockEntityType; // 型引数を適切に指定

    // blockEntityType が初期化されてから評価されるよう、遅延評価される部分で安全に使います
    public Machine<BE> machineBlockType = (Machine<BE>) Machine.MachineBuilder
            .createMachine(() -> blockEntityType, MekanismLang.DESCRIPTION_CRUSHER)
            .replace(Attributes.ACTIVE)
            .build();

    // 修正前: public ContainerTypeRegistryObject<? extends MekanismTileContainer<? extends TileEntityMekanism>> tileContainer;
// 修正後:
    public ContainerTypeRegistryObject<MekanismTileContainer<BE>> tileContainer;
    // 画面ファクトリを返すメソッドに変更
    public abstract MenuScreens.ScreenConstructor<MekanismTileContainer<BE>, GuiMekanismTile<BE, MekanismTileContainer<BE>>> getGuiSupplier();
}