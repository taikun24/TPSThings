package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.items.ItemFluorescentLight;
import jp.main.taikun.tpsthings.items.ItemLayer;
import jp.main.taikun.tpsthings.items.ItemMaterial;
import jp.main.taikun.tpsthings.items.ItemOo;
import jp.main.taikun.tpsthings.items.ItemPrism;
import jp.main.taikun.tpsthings.items.ItemQioDrive;
import mekanism.api.gear.IModuleHelper;
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
     // saturationMod は倍率。3 にすると飽和度 18 (エンチャント金リンゴ相当) になるので 0.6 に留める
     public static final RegistryObject<Item> TEACUP = ITEMS.register("teacup", ()->new Item(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(6).saturationMod(0.6F).alwaysEat().build())));
     public static final RegistryObject<Item> FLUORESCENT_LIGHT = ITEMS.register("fluorescent_light", ItemFluorescentLight::new);
     public static final RegistryObject<Item> TIME_FLUX_CRYSTAL = ITEMS.register("time_flux_crystal", ()->new FoilItem(new Item.Properties()));
     public static final RegistryObject<Item> ACCELERATION_WAND = ITEMS.register("acceleration_wand", ()->new Item(new  Item.Properties()));
     public static final RegistryObject<Item> PRISM = ITEMS.register("prism", ItemPrism::new);

     /*
      * おお への系譜。
      *
      * 底は「おおじゃないが」(土 + 木の板)。そこから製材と濃縮だけで低レベルへ降りていき、
      * 反物質側から来る資源と合流して、最後に 9x9 の卓で おお に組み上がる。
      *
      *   コードの段    バイトコード → Java → C → シェーダー → BERWL
      *                 バイトコード + Javaティーガス → JVM → Mixin → ASM → CoreMod
      *                 Mixin + ASM + CoreMod + 武器/盾 → 攻撃モジュール / 防衛モジュール
      *   欠陥の段      ぬるぽ → (製材) グリッチの欠片 + 5% 未定義動作 → (製材) バイトコード
      *   資源の段      ダンボール箱 / 鉱石統合機 を濃縮室で潰して グリッチの欠片 へ
      *   合金の段      原子合金 → つよすぎ → やばすぎ → えぐすぎ → おお合金
      *   無限の段      インフィニティ → エタニティ → ユニティ → プリズム → すごいメニュー
      *   反物質の段    反物質ペレット → 塊 → インゴット → ブロック → 濃縮ブロック → シンギュラリティ
      *
      * 貫通層 (penetration-layer-model) の呼び名を持つのは、関所の話に直接対応する 3 つだけ。
      * 残りは層を持たないただの素材。
      */
     // ツールチップのシェーダーは、Mixin と CoreMod だけ層のシェーダーではなく専用のもの (実績名の絵柄) を被せる
     public static final RegistryObject<Item> MIXIN = ITEMS.register("mixin", ()->new ItemLayer(7, false, "mixin", new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> UNDEFINED_BEHAVIOR = ITEMS.register("undefined_behavior", ()->new ItemLayer(4, true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> COREMOD = ITEMS.register("coremod", ()->new ItemLayer(9, true, "coremod", new Item.Properties().rarity(Rarity.EPIC)));

     // --- コードの段 ---
     public static final RegistryObject<Item> BYTECODE = ITEMS.register("bytecode", ()->new Item(new Item.Properties()));
     public static final RegistryObject<Item> JAVA = ITEMS.register("java", ()->new Item(new Item.Properties()));
     public static final RegistryObject<Item> C = ITEMS.register("c", ()->new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
     public static final RegistryObject<Item> JVM = ITEMS.register("jvm", ()->new FoilItem(new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> ASM = ITEMS.register("asm", ()->new ItemMaterial("asm", false, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> SHADER = ITEMS.register("shader", ()->new FoilItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
     public static final RegistryObject<Item> BERWL = ITEMS.register("berwl", ()->new ItemMaterial("berwl", false, new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> SUGOI_MENU = ITEMS.register("sugoi_menu", ()->new ItemMaterial("sugoi_menu", false, new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> ATTACK_MODULE = ITEMS.register("attack_module", ()->new ItemMaterial("attack_module", true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> DEFENSE_MODULE = ITEMS.register("defense_module", ()->new ItemMaterial("defense_module", true, new Item.Properties().rarity(Rarity.EPIC)));
     /** おおの力を MekaSuit (胴) / Meka-Tool に移す Mekanism のモジュール。名前は Mekanism の流儀 (module_○○_unit) に揃える */
     public static final RegistryObject<Item> MODULE_OO_UNIT = ITEMS.register("module_oo_unit",
             ()->IModuleHelper.INSTANCE.createModuleItem(ModModules.OO_UNIT, new Item.Properties()));

     // --- 欠陥の段 ---
     public static final RegistryObject<Item> NULL_POINTER = ITEMS.register("null_pointer", ()->new Item(new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> GLITCH = ITEMS.register("glitch", ()->new ItemMaterial("glitch", true, new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> GLITCH_SHARD = ITEMS.register("glitch_shard", ()->new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

     // --- 資源の段。濃縮室で押し潰していく ---
     public static final RegistryObject<Item> COMPRESSED_CARDBOARD_BOX = ITEMS.register("compressed_cardboard_box", ()->new Item(new Item.Properties()));
     public static final RegistryObject<Item> SUPER_COMPRESSED_CARDBOARD_BOX = ITEMS.register("super_compressed_cardboard_box", ()->new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
     public static final RegistryObject<Item> COMPRESSED_OREDICTIONIFICATOR = ITEMS.register("compressed_oredictionificator", ()->new Item(new Item.Properties()));
     public static final RegistryObject<Item> SUPER_COMPRESSED_OREDICTIONIFICATOR = ITEMS.register("super_compressed_oredictionificator", ()->new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

     // --- 合金の段 ---
     /** 吹込で 80 mB ずつ喰わせるための濃縮ネザライト。Mekanism の濃縮○○と同じ役割 */
     public static final RegistryObject<Item> ENRICHED_NETHERITE = ITEMS.register("enriched_netherite", ()->new Item(new Item.Properties()));
     public static final RegistryObject<Item> ALLOY_TSUYOSUGI = ITEMS.register("alloy_tsuyosugi", ()->new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
     public static final RegistryObject<Item> ALLOY_YABASUGI = ITEMS.register("alloy_yabasugi", ()->new Item(new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> ALLOY_EGUSUGI = ITEMS.register("alloy_egusugi", ()->new Item(new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> ALLOY_OO = ITEMS.register("alloy_oo", ()->new ItemMaterial("alloy_oo", true, new Item.Properties().rarity(Rarity.EPIC)));

     // --- 無限の段 ---
     public static final RegistryObject<Item> INFINITY_INGOT = ITEMS.register("infinity_ingot", ()->new ItemMaterial("infinity_ingot", true, new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> ETERNITY_INGOT = ITEMS.register("eternity_ingot", ()->new ItemMaterial("eternity_ingot", true, new Item.Properties().rarity(Rarity.EPIC)));
     public static final RegistryObject<Item> UNITY_INGOT = ITEMS.register("unity_ingot", ()->new ItemMaterial("unity_ingot", true, new Item.Properties().rarity(Rarity.EPIC)));

     // --- 反物質の段 ---
     public static final RegistryObject<Item> ANTIMATTER_CHUNK = ITEMS.register("antimatter_chunk", ()->new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
     public static final RegistryObject<Item> ANTIMATTER_INGOT = ITEMS.register("antimatter_ingot", ()->new Item(new Item.Properties().rarity(Rarity.RARE)));
     public static final RegistryObject<Item> ANTIMATTER_SINGULARITY = ITEMS.register("antimatter_singularity", ()->new FoilItem(new Item.Properties().rarity(Rarity.EPIC)));

     // --- QIO ドライブ。Mekanism の最上位 (超大質量) の先を 3 段だけ伸ばす ---
     public static final RegistryObject<Item> QIO_DRIVE_QUANTUM = ITEMS.register("qio_drive_quantum",
             ()->new ItemQioDrive(128L * 1000 * 1000 * 1000 * 1000, 512 * 1024));
     public static final RegistryObject<Item> QIO_DRIVE_COSMIC = ITEMS.register("qio_drive_cosmic",
             ()->new ItemQioDrive(512L * 1000 * 1000 * 1000 * 1000 * 1000, 64 * 1024 * 1024));
     // 種類数の 2G は int に入りきらないので、入る限界で止める。桁違いを名乗る側の都合で丸める
     public static final RegistryObject<Item> QIO_DRIVE_ABSURD = ITEMS.register("qio_drive_absurd",
             ()->new ItemQioDrive(8L * 1000 * 1000 * 1000 * 1000 * 1000 * 1000, Integer.MAX_VALUE));

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
