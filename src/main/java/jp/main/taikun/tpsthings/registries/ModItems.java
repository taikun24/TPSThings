package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.items.ItemCatTeaser;
import jp.main.taikun.tpsthings.items.ItemFluorescentLight;
import jp.main.taikun.tpsthings.items.ItemLayer;
import jp.main.taikun.tpsthings.items.ItemOo;
import jp.main.taikun.tpsthings.items.ItemPrism;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
     private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tpsthings.MODID);
     public static final RegistryObject<Item> QOL = ITEMS.register("qol", () -> new Item(new Item.Properties()));
     public static final RegistryObject<Item> QOLER = ITEMS.register("qoler", () -> new Item(new Item.Properties()));
     public static final RegistryObject<Item> QOLEST = ITEMS.register("qolest", () -> new Item(new Item.Properties()));
     public static final RegistryObject<Item> HOPE_BIO_FUEL = ITEMS.register("hope_bio_fuel", ()->new FoilItem(new Item.Properties()));
     public static final RegistryObject<Item> HOPE_SUBSTRATE = ITEMS.register("hope_substrate", ()->new FoilItem(new Item.Properties()));
     public static final RegistryObject<Item> HOPE_PELLET =  ITEMS.register("hope_pellet", ()->new FoilItem(new Item.Properties()));
     public static final RegistryObject<Item> HOPE_SHEET =   ITEMS.register("hope_sheet", ()->new FoilItem(new Item.Properties()));
     public static final RegistryObject<Item> SPICY_SPICE =  ITEMS.register("spicy_spice", ()->new Item(new Item.Properties()));
     public static final RegistryObject<Item> OO = ITEMS.register("oo", ItemOo::new);
     public static final RegistryObject<Item> NOT_OO = ITEMS.register("not_oo", ()->new Item(new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> TUNA = ITEMS.register("tuna", ()->new Item(new Item.Properties()));
     public static final RegistryObject<Item> TEACUP = ITEMS.register("teacup", ()->new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(3).alwaysEat().build())));
     public static final RegistryObject<Item> FLUORESCENT_LIGHT = ITEMS.register("fluorescent_light", ItemFluorescentLight::new);
     public static final RegistryObject<Item> TIME_FLUX_CRYSTAL = ITEMS.register("time_flux_crystal", ()->new FoilItem(new Item.Properties()));
     public static final RegistryObject<Item> CAT_TEASER = ITEMS.register("cat_teaser", ItemCatTeaser::new);
     public static final RegistryObject<Item> ACCELERATION_WAND = ITEMS.register("acceleration_wand", ()->new Item(new  Item.Properties()));
     public static final RegistryObject<Item> PRISM = ITEMS.register("prism", ItemPrism::new);
     /*
      * おお への系譜。貫通層 (penetration-layer-model) を表層から索引層まで 1 段ずつ降りる。
      * 下から順に人間の言葉を失っていき、完成品だけが感嘆に戻る。
      *
      *  減算層    反HOPEシート           加圧反応室   HOPEシート + NOPE
      *  刹那層    未定義の欠片           対消滅炉     HOPE + 反HOPE (1/5)。lang もモデルも意図的に登録しない
      *  刹那層    凍結した1tick          化学注入室   欠片 + タイムフラックス          … 無敵時間
      *  不可侵層  無敵フラグ             浄化室       凍結した1tick + HOPE酸素        … 無敵判定
      *  挙動層    未定義動作             対消滅炉     欠片×2 + 無敵フラグ×2 + 結晶
      *  合議層    キャンセル済みイベント 作業台       未定義動作 + 反HOPE×4 + おおじゃないが×4
      *  生値層    剥き出しの体力値       加圧反応室   キャンセル済みイベント + NOPE + HOPE水素
      *  虚偽層    嘘つきの読み出し       圧縮機       剥き出しの体力値 + 気化NOPE
      *  虚偽層    Mixin                  作業台       体力値 + 読み出し + 未定義動作 + プリズム
      *  終焉層    握り潰された死         結合機       Mixin + キャンセル済みイベント×2
      *  抹消層    除去の拒否権           対消滅炉     握り潰された死 + 凍結した1tick×4
      *  抹消層    CoreMod                作業台
      *  索引層    消された索引           精密製材機   除去の拒否権 → 索引 (+欠片 25%)
      *  索引層    Java Agent             作業台
      *  --        おお                   対消滅炉 (儀式)  減算層から索引層までを層の順に 1 つずつ投げ込む (BEAnnihilationChamber.RITUAL)
      */
     public static final RegistryObject<Item> MIXIN = ITEMS.register("mixin", ()->new ItemLayer(7, false, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> UNDEFINED_SHARD = ITEMS.register("undefined_shard", ()->new ItemLayer(2, false, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> ANTI_HOPE_SHEET = ITEMS.register("anti_hope_sheet", ()->new ItemLayer(1, true, new Item.Properties()));
     public static final RegistryObject<Item> UNDEFINED_BEHAVIOR = ITEMS.register("undefined_behavior", ()->new ItemLayer(4, true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> COREMOD = ITEMS.register("coremod", ()->new ItemLayer(9, true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> JAVA_AGENT = ITEMS.register("java_agent", ()->new ItemLayer(10, true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> FROZEN_TICK = ITEMS.register("frozen_tick", ()->new ItemLayer(2, false, new Item.Properties().rarity(Rarity.UNCOMMON)));
     public static final RegistryObject<Item> INVULNERABLE_FLAG = ITEMS.register("invulnerable_flag", ()->new ItemLayer(3, false, new Item.Properties().rarity(Rarity.UNCOMMON)));
     public static final RegistryObject<Item> CANCELLED_EVENT = ITEMS.register("cancelled_event", ()->new ItemLayer(5, false, new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> RAW_HEALTH = ITEMS.register("raw_health", ()->new ItemLayer(6, false, new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> LYING_READER = ITEMS.register("lying_reader", ()->new ItemLayer(7, true, new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> DEATH_HOOK = ITEMS.register("death_hook", ()->new ItemLayer(8, true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> REMOVAL_VETO = ITEMS.register("removal_veto", ()->new ItemLayer(9, true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> ERASED_INDEX = ITEMS.register("erased_index", ()->new ItemLayer(10, true, new Item.Properties().rarity(Rarity.EPIC)));
     /*
     * Mixin
     *
     * */
     private static class FoilItem extends Item {
         public FoilItem(Properties properties) {
             super(properties);
         }
         @Override
         public boolean isFoil(ItemStack stack) {
             return true;
         }
     }
     public static void register(IEventBus bus) {
         ITEMS.register(bus);
     }

}
