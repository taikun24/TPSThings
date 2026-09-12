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

    @Override
    protected void registerModels() {
        singleUnchecked("hope_bio_fuel",   rl("mekanism", "item/bio_fuel"));
        singleUnchecked("hope_pellet",     rl("mekanism", "item/hdpe_pellet"));
        singleUnchecked("hope_substrate",  rl("mekanism", "item/substrate"));
        singleUnchecked("hope_sheet",      rl("mekanism", "item/hdpe_sheet"));
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
        this.basicItem(ModItems.CAT_TEASER.get());
        this.basicItem(ModItems.ACCELERATION_WAND.get());
        this.basicItem(ModItems.NOT_OO.get());
        this.basicItem(ModItems.MIXIN.get());
        // 専用テクスチャができるまでの仮。undefined_shard はモデルも持たせない
        // 反 HOPE シートは HOPE シートと同じ絵。見分けは ClientRegister の色で付ける
        singleUnchecked("anti_hope_sheet",    rl("mekanism", "item/hdpe_sheet"));
        singleUnchecked("undefined_behavior", rl("minecraft", "item/dragon_breath"));
        singleUnchecked("coremod",            rl("minecraft", "item/knowledge_book"));
        singleUnchecked("java_agent",         rl("minecraft", "item/enchanted_book"));
        singleUnchecked("frozen_tick",        rl("minecraft", "item/clock_00"));
        singleUnchecked("invulnerable_flag",  rl("minecraft", "item/phantom_membrane"));
        singleUnchecked("cancelled_event",    rl("minecraft", "item/barrier"));
        singleUnchecked("raw_health",         rl("minecraft", "item/redstone"));
        singleUnchecked("lying_reader",       rl("minecraft", "item/spyglass"));
        singleUnchecked("death_hook",         rl("minecraft", "item/totem_of_undying"));
        singleUnchecked("removal_veto",       rl("minecraft", "item/structure_void"));
        singleUnchecked("erased_index",       rl("minecraft", "item/echo_shard"));
        // builtin/entity 親にすると BEWLR (PrismItemRenderer) が呼ばれる
        getBuilder("prism").parent(new ModelFile.UncheckedModelFile("builtin/entity"));

        // for block

    }
}
