package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Tpsthings.MODID, existingFileHelper);
    }

    private static ResourceLocation rl(String namespace, String key) {
        return ResourceLocation.fromNamespaceAndPath(namespace, key);
    }
    private void singleUnchecked(String id, ResourceLocation texture) {
        getBuilder(id)
                .parent(new ModelFile.UncheckedModelFile(mcLoc("item/generated")))
                .texture("layer0", texture);
    }

    /** textures/item/<id>.png をそのまま使う素のアイテムモデル */
    private void ownTexture(String id) {
        singleUnchecked(id, modLoc("item/" + id));
    }

    @Override
    protected void registerModels() {
        ownTexture("hope_bio_fuel");
        ownTexture("hope_pellet");
        ownTexture("hope_substrate");
        ownTexture("hope_sheet");
        this.basicItem(ModItems.SPICY_SPICE.get());
        this.basicItem(ModItems.FLUORESCENT_LIGHT.get());
        // builtin/entity 親にすると BEWLR (PrismItemRenderer) が呼ばれる
        // display は vanilla の item/handheld と同じ値 (剣持ち)。BEWLR でも
        // ForgeHooksClient.handleCameraTransforms がモデルの transform を適用してくれる
        getBuilder("oo").parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .transforms()
                .transform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                        .rotation(0, -90, 55).translation(0, 4.0F, 0.5F).scale(0.85F, 0.85F, 0.85F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                        .rotation(0, 90, -55).translation(0, 4.0F, 0.5F).scale(0.85F, 0.85F, 0.85F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                        .rotation(0, -90, 25).translation(1.13F, 3.2F, 1.13F).scale(0.68F, 0.68F, 0.68F).end()
                .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                        .rotation(0, 90, -25).translation(1.13F, 3.2F, 1.13F).scale(0.68F, 0.68F, 0.68F).end()
                .end();
        // BEWLR 内でテクスチャを描くための元モデル (ModelEvent.RegisterAdditional で読み込む)
        singleUnchecked("oo_base", modLoc("item/oo"));
        this.basicItem(ModItems.QOL.get());
        this.basicItem(ModItems.QOLER.get());
        this.basicItem(ModItems.QOLEST.get());
        this.basicItem(ModItems.TEACUP.get());
        this.basicItem(ModItems.TUNA.get());
        this.basicItem(ModItems.TIME_FLUX_CRYSTAL.get());
        this.basicItem(ModItems.ACCELERATION_WAND.get());
        this.basicItem(ModItems.NOT_OO.get());
        this.basicItem(ModItems.MIXIN.get());
        // textures/item/<id>.png を読む。今入っているのはバニラ / Mekanism から写した仮の絵
        ownTexture("undefined_behavior");
        ownTexture("coremod");
        // コードの段
        ownTexture("bytecode");
        ownTexture("java");
        ownTexture("c");
        ownTexture("jvm");
        ownTexture("asm");
        ownTexture("shader");
        ownTexture("berwl");
        ownTexture("sugoi_menu");
        ownTexture("attack_module");
        ownTexture("defense_module");
        ownTexture("module_oo_unit");
        // 欠陥の段
        ownTexture("null_pointer");
        ownTexture("glitch");
        ownTexture("glitch_shard");
        // 資源の段
        ownTexture("compressed_cardboard_box");
        ownTexture("super_compressed_cardboard_box");
        ownTexture("compressed_oredictionificator");
        ownTexture("super_compressed_oredictionificator");
        // 合金の段
        ownTexture("enriched_netherite");
        ownTexture("alloy_tsuyosugi");
        ownTexture("alloy_yabasugi");
        ownTexture("alloy_egusugi");
        ownTexture("alloy_oo");
        // 無限の段
        ownTexture("infinity_ingot");
        ownTexture("eternity_ingot");
        ownTexture("unity_ingot");
        // 反物質の段
        ownTexture("antimatter_chunk");
        ownTexture("antimatter_ingot");
        ownTexture("antimatter_singularity");
        // QIO ドライブ
        ownTexture("qio_drive_quantum");
        ownTexture("qio_drive_cosmic");
        ownTexture("qio_drive_absurd");
        // builtin/entity 親にすると BEWLR (PrismItemRenderer) が呼ばれる
        getBuilder("prism").parent(new ModelFile.UncheckedModelFile("builtin/entity"));

        // for block

    }
}
