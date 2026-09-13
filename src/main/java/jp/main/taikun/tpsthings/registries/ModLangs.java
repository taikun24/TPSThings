package jp.main.taikun.tpsthings.registries;

import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public abstract class ModLangs extends LanguageProvider {
    public ModLangs(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }
    public static class JA extends ModLangs {
        public JA(PackOutput output) {
            super(output, Tpsthings.MODID, "ja_jp");
        }

        @Override
        protected void addTranslations() {
            add(ModBlocks.TIME_FLUX_COLLECTOR.getBlock(), "タイムフラックス収集機");
            add(ModBlocks.TIME_ACCELERATOR.getBlock(), "時流加速機");
            add("gui.tpsthings.minus", "-");
            add("gui.tpsthings.plus", "+");
            add(ModBlocks.DEBUG_ACCELERATOR.get(), "デバッグ用時流加速機");
            add(ModBlocks.TPS_GENERATOR.getBlock(), "TPS発電機");
            add(ModBlocks.LAG_GENERATOR.getBlock(), "ラグ発電機");
            add(ModItems.TIME_FLUX_CRYSTAL.get(), "タイムフラックスの結晶");
            add(ModGases.TIME_FLUX.getTranslationKey(), "タイムフラックス");
            add(ModItems.QOL.get(), "QOL");
            add(ModItems.QOLER.get(), "QOLer");
            add(ModItems.QOLEST.get(), "QOLest");
            add(ModItems.ACCELERATION_WAND.get(), "加速の杖");
            add(ModItems.PRISM.get(), "プリズム");
            add(ModItems.HOPE_BIO_FUEL.get(), "HOPEバイオ燃料");
            add(ModItems.HOPE_PELLET.get(), "HOPEペレット");
            add(ModItems.HOPE_SUBSTRATE.get(), "HOPE基質");
            add(ModItems.HOPE_SHEET.get(), "HOPEシート");
            add(ModItems.SPICY_SPICE.get(), "スパイス");
            add(ModItems.OO.get(), "おお");
            add(ModItems.NOT_OO.get(), "おおじゃないが");
            add(ModItems.TUNA.get(), "まぐろ");
            add(ModItems.TEACUP.get(), "ティーカップ");
            add(ModItems.FLUORESCENT_LIGHT.get(), "ライトセーバー");
            add(ModItems.MIXIN.get(), "Mixin");
            add(ModItems.UNDEFINED_BEHAVIOR.get(), "未定義動作");
            add(ModItems.COREMOD.get(), "CoreMod");
            // --- おお への系譜。コードの段 ---
            add(ModItems.BYTECODE.get(), "バイトコード");
            add(ModItems.JAVA.get(), "Java");
            add(ModItems.C.get(), "C");
            add(ModItems.JVM.get(), "JVM");
            add(ModItems.ASM.get(), "ASM");
            add(ModItems.SHADER.get(), "シェーダー");
            add(ModItems.BERWL.get(), "BERWL");
            add(ModItems.SUGOI_MENU.get(), "すごいメニュー");
            add(ModItems.ATTACK_MODULE.get(), "攻撃モジュール");
            add(ModItems.DEFENSE_MODULE.get(), "防衛モジュール");
            // --- 欠陥の段 ---
            add(ModItems.NULL_POINTER.get(), "NullPointer");
            add(ModItems.GLITCH.get(), "グリッチ");
            add(ModItems.GLITCH_SHARD.get(), "グリッチの欠片");
            // --- 資源の段 ---
            add(ModItems.COMPRESSED_CARDBOARD_BOX.get(), "濃縮ダンボール箱");
            add(ModItems.SUPER_COMPRESSED_CARDBOARD_BOX.get(), "超濃縮ダンボール箱");
            add(ModItems.COMPRESSED_OREDICTIONIFICATOR.get(), "濃縮鉱石統合機");
            add(ModItems.SUPER_COMPRESSED_OREDICTIONIFICATOR.get(), "超濃縮鉱石統合機");
            // --- 合金の段 ---
            add(ModItems.ENRICHED_NETHERITE.get(), "濃縮ネザライト");
            add(ModItems.ALLOY_TSUYOSUGI.get(), "つよすぎ合金");
            add(ModItems.ALLOY_YABASUGI.get(), "やばすぎ合金");
            add(ModItems.ALLOY_EGUSUGI.get(), "えぐすぎ合金");
            add(ModItems.ALLOY_OO.get(), "おお合金");
            // --- 無限の段 ---
            add(ModItems.INFINITY_INGOT.get(), "インフィニティインゴット");
            add(ModItems.ETERNITY_INGOT.get(), "エタニティインゴット");
            add(ModItems.UNITY_INGOT.get(), "ユニティインゴット");
            // --- 反物質の段 ---
            add(ModItems.ANTIMATTER_CHUNK.get(), "反物質の塊");
            add(ModItems.ANTIMATTER_INGOT.get(), "反物質インゴット");
            add(ModBlocks.ANTIMATTER_BLOCK.get(), "反物質ブロック");
            add(ModBlocks.COMPRESSED_ANTIMATTER_BLOCK.get(), "濃縮反物質ブロック");
            add(ModItems.ANTIMATTER_SINGULARITY.get(), "反物質シンギュラリティ");
            // --- QIO ドライブ ---
            add(ModItems.QIO_DRIVE_QUANTUM.get(), "量子QIOドライブ");
            add(ModItems.QIO_DRIVE_COSMIC.get(), "宇宙QIOドライブ");
            add(ModItems.QIO_DRIVE_ABSURD.get(), "桁違いQIOドライブ");
            add("tooltip.tpsthings.qio_drive.capacity", "%s 種類 / %s 個");
            add(ModGases.JAVA_TEA.getTranslationKey(), "Javaティーガス");
            add("fluid_type.tpsthings.java_tea", "液化Javaティーガス");
            add(ModInfuseTypes.NETHERITE.getTranslationKey(), "ネザライト");
            add(ModInfuseTypes.POLONIUM.getTranslationKey(), "ポロニウム");
            add(ModInfuseTypes.ANTIMATTER.getTranslationKey(), "反物質");
            add("key.categories.tpsthings", "TPS Things");
            add("key.tpsthings.sugoi_menu", "すごいメニュー");
            add(ModGases.HOPE_ETHYLENE.getTranslationKey(), "HOPEエチレン");
            add(ModGases.HOPE_HYDROGEN.getTranslationKey(), "HOPE水素");
            add(ModGases.HOPE_OXYGEN.getTranslationKey(), "HOPE酸素");
            add(ModGases.CARBON_DIOXIDE.getTranslationKey(), "二酸化炭素");
            add(ModGases.NOPE_GAS.getTranslationKey(), "気化NOPE");

            add("fluid_type.tpsthings.nope", "NOPE");
            add("fluid_type.tpsthings.hope_ethylene", "液化HOPEエチレン");

            add(ModContainerTypes.TIME_FLUX_COLLECTOR.getInternalRegistryName(), "タイムフラックス収集機");
            add(ModContainerTypes.TIME_ACCELERATOR.getInternalRegistryName(), "時流加速機");
            add(ModContainerTypes.TPS_GENERATOR.getInternalRegistryName(), "TPS発電機");
            add(ModContainerTypes.LAG_GENERATOR.getInternalRegistryName(), "ラグ発電機");

            // 層の呼び名。番号はレシピの順序のために内部に残るだけで、表には出さない
            add("tpsthings.layer.1", "減算層");
            add("tpsthings.layer.2", "刹那層");
            add("tpsthings.layer.3", "不可侵層");
            add("tpsthings.layer.4", "挙動層");
            add("tpsthings.layer.5", "合議層");
            add("tpsthings.layer.6", "生値層");
            add("tpsthings.layer.7", "虚偽層");
            add("tpsthings.layer.8", "終焉層");
            add("tpsthings.layer.9", "抹消層");
            add("tpsthings.layer.10", "索引層");

            // 進捗ツリー「おおへ」。題は素材に付いた二つ名、説明はどれも「手に入れた」だけ
            add("advancements.tpsthings.oo.root.title", "おおへ");
            add("advancements.tpsthings.oo.root.description", "土と木の板から\n始まってしまった");
            add("advancements.tpsthings.oo.obtained", "%s を手に入れた");
            add("advancements.tpsthings.oo.super_compressed_cardboard_box.title", "おのれBlockEntity");
            add("advancements.tpsthings.oo.super_compressed_oredictionificator.title", "クリエ合金？");
            add("advancements.tpsthings.oo.glitch.title", "脆弱性");
            add("advancements.tpsthings.oo.null_pointer.title", "ぬるぽ");
            add("advancements.tpsthings.oo.undefined_behavior.title", "ｶﾞｯ");
            add("advancements.tpsthings.oo.bytecode.title", "0x304A304A");
            add("advancements.tpsthings.oo.java.title", "Javaaaaa");
            add("advancements.tpsthings.oo.c.title", "優しい?");
            add("advancements.tpsthings.oo.jvm.title", "Javaaaaaaaaaaaaaaa");
            add("advancements.tpsthings.oo.mixin.title", "Mix死n");
            add("advancements.tpsthings.oo.asm.title", "低レベル");
            add("advancements.tpsthings.oo.coremod.title", "本番でやるな");
            add("advancements.tpsthings.oo.attack_module.title", "耐え");
            add("advancements.tpsthings.oo.defense_module.title", "絶え");
            add("advancements.tpsthings.oo.shader.title", "なんか付けとけば格好良くなるやつ");
            add("advancements.tpsthings.oo.berwl.title", "虚勢を張るための道具");
            add("advancements.tpsthings.oo.prism.title", "わーいぴかぴか...?");
            add("advancements.tpsthings.oo.sugoi_menu.title", "GuiGui");
            add("advancements.tpsthings.oo.infinity_ingot.title", "良くあるやつ");
            add("advancements.tpsthings.oo.eternity_ingot.title", "この流れ");
            add("advancements.tpsthings.oo.unity_ingot.title", "どこかで見た");
            add("advancements.tpsthings.oo.alloy_oo.title", "これはおお...なのか?");
            add("advancements.tpsthings.oo.qio_drive_absurd.title", "君も桁違いにならないか");
            add("advancements.tpsthings.oo.oo.title", "おお");
            add("advancements.tpsthings.oo.oo.description", "これはおおだろ");

            // --- コードから出る文言 (Component.literal をやめた分) ---
            add("itemGroup." + Tpsthings.MODID, "TPS Things");
            add("item.tpsthings.oo.description", "It's wow...");
            add("tooltip.tpsthings.oo", "おお");
            add("tooltip.tpsthings.oo.murmur.0", "It's wow...");
            add("tooltip.tpsthings.oo.murmur.1", "おぉ");
            add("tooltip.tpsthings.oo.murmur.2", "これはおおだろ");
            add("tooltip.tpsthings.oo.murmur.3", "おおじゃないが");
            add("message.tpsthings.debug_accelerator.speed", "速さ: %s");
            add("gui.tpsthings.accelerator.range", "範囲 %s (%s 角)");
            add("gui.tpsthings.accelerator.speed", "速さ x%s");
            add("gui.tpsthings.accelerator.no_target", "対象なし");
            add("gui.tpsthings.accelerator.targets", "%s 台 / %s");
            add("gui.tpsthings.accelerator.running", "稼働中");
            add("gui.tpsthings.accelerator.starved", "資源不足");
            add("gui.tpsthings.generator.tps_tab", "TPS: %s");
            add("gui.tpsthings.generator.output_tab", "出力: %s J/t");
            add("gui.tpsthings.generator.tps", "TPS %s");
            add("gui.tpsthings.generator.output", "%s J/t");
            add("gui.tpsthings.generator.idle", "回っていない");
            add("gui.tpsthings.tps_generator.condition", "軽いほど回る");
            add("gui.tpsthings.lag_generator.condition", "重いほど回る");

            addPoems();
        }

        /**
         * 貫通層の呼び名を持つ素材 (ItemLayer) のポエム。キーは「説明キー + .poem」、行は \n で区切る。
         * 表示は ItemPoemTooltip が担当する。
         */
        private void addPoems() {
            poem(ModItems.UNDEFINED_BEHAVIOR.get(), "仕様書の余白には\n何を書いてもいい\n鼻から悪魔が出ても");
            poem(ModItems.MIXIN.get(), "他人の体に\nそっと言葉を差し込む\n頭の一行だけで");
            poem(ModItems.COREMOD.get(), "クラスが生まれる前に\n名前を書き換える\n誰にも気づかれずに");
        }

        private void poem(net.minecraft.world.item.Item item, String text) {
            add(item.getDescriptionId() + ".poem", text);
        }
    }
    public static class EN extends ModLangs {
        public EN(PackOutput output) {
            super(output, Tpsthings.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add(ModBlocks.TIME_FLUX_COLLECTOR.getBlock(), "Time Flux Collector");
            add(ModBlocks.TIME_ACCELERATOR.getBlock(), "Time Accelerator");
            add(ModBlocks.DEBUG_ACCELERATOR.get(), "Debug Time Accelerator");
            add(ModBlocks.TPS_GENERATOR.getBlock(), "TPS Generator");
            add(ModBlocks.LAG_GENERATOR.getBlock(), "Lag Generator");

            add("gui.tpsthings.minus", "-");
            add("gui.tpsthings.plus", "+");

            add(ModItems.TIME_FLUX_CRYSTAL.get(), "Time Flux Crystal");
            add(ModItems.QOL.get(), "QOL");
            add(ModItems.QOLER.get(), "QOLer");
            add(ModItems.QOLEST.get(), "QOLest");
            add(ModItems.ACCELERATION_WAND.get(), "Wand of Acceleration");
            add(ModItems.PRISM.get(), "Prism");
            add(ModItems.HOPE_BIO_FUEL.get(), "HOPE Bio Fuel");
            add(ModItems.HOPE_PELLET.get(), "HOPE Pellet");
            add(ModItems.HOPE_SUBSTRATE.get(), "HOPE Substrate");
            add(ModItems.HOPE_SHEET.get(), "HOPE Sheet");
            add(ModItems.SPICY_SPICE.get(), "Spice");
            add(ModItems.OO.get(), "Oo");
            add(ModItems.NOT_OO.get(), "Not Quite Oo");
            add(ModItems.TUNA.get(), "Tuna");
            add(ModItems.TEACUP.get(), "Teacup");
            add(ModItems.FLUORESCENT_LIGHT.get(), "Fluorescent Light");
            add(ModItems.MIXIN.get(), "Mixin");
            add(ModItems.UNDEFINED_BEHAVIOR.get(), "Undefined Behavior");
            add(ModItems.COREMOD.get(), "CoreMod");
            // --- The line toward Oo. The code rungs ---
            add(ModItems.BYTECODE.get(), "Bytecode");
            add(ModItems.JAVA.get(), "Java");
            add(ModItems.C.get(), "C");
            add(ModItems.JVM.get(), "JVM");
            add(ModItems.ASM.get(), "ASM");
            add(ModItems.SHADER.get(), "Shader");
            add(ModItems.BERWL.get(), "BERWL");
            add(ModItems.SUGOI_MENU.get(), "Amazing Menu");
            add(ModItems.ATTACK_MODULE.get(), "Attack Module");
            add(ModItems.DEFENSE_MODULE.get(), "Defence Module");
            // --- The defect rungs ---
            add(ModItems.NULL_POINTER.get(), "NullPointer");
            add(ModItems.GLITCH.get(), "Glitch");
            add(ModItems.GLITCH_SHARD.get(), "Glitch Shard");
            // --- The resource rungs ---
            add(ModItems.COMPRESSED_CARDBOARD_BOX.get(), "Compressed Cardboard Box");
            add(ModItems.SUPER_COMPRESSED_CARDBOARD_BOX.get(), "Super Compressed Cardboard Box");
            add(ModItems.COMPRESSED_OREDICTIONIFICATOR.get(), "Compressed Oredictionificator");
            add(ModItems.SUPER_COMPRESSED_OREDICTIONIFICATOR.get(), "Super Compressed Oredictionificator");
            // --- The alloy rungs ---
            add(ModItems.ENRICHED_NETHERITE.get(), "Enriched Netherite");
            add(ModItems.ALLOY_TSUYOSUGI.get(), "Way Too Strong Alloy");
            add(ModItems.ALLOY_YABASUGI.get(), "Way Too Nasty Alloy");
            add(ModItems.ALLOY_EGUSUGI.get(), "Way Too Brutal Alloy");
            add(ModItems.ALLOY_OO.get(), "Oo Alloy");
            // --- The endless rungs ---
            add(ModItems.INFINITY_INGOT.get(), "Infinity Ingot");
            add(ModItems.ETERNITY_INGOT.get(), "Eternity Ingot");
            add(ModItems.UNITY_INGOT.get(), "Unity Ingot");
            // --- The antimatter rungs ---
            add(ModItems.ANTIMATTER_CHUNK.get(), "Antimatter Chunk");
            add(ModItems.ANTIMATTER_INGOT.get(), "Antimatter Ingot");
            add(ModBlocks.ANTIMATTER_BLOCK.get(), "Block of Antimatter");
            add(ModBlocks.COMPRESSED_ANTIMATTER_BLOCK.get(), "Compressed Block of Antimatter");
            add(ModItems.ANTIMATTER_SINGULARITY.get(), "Antimatter Singularity");
            // --- QIO drives ---
            add(ModItems.QIO_DRIVE_QUANTUM.get(), "Quantum QIO Drive");
            add(ModItems.QIO_DRIVE_COSMIC.get(), "Cosmic QIO Drive");
            add(ModItems.QIO_DRIVE_ABSURD.get(), "Absurd QIO Drive");
            add("tooltip.tpsthings.qio_drive.capacity", "%s types / %s items");
            add(ModGases.JAVA_TEA.getTranslationKey(), "Java Tea Gas");
            add("fluid_type.tpsthings.java_tea", "Liquid Java Tea Gas");
            add(ModInfuseTypes.NETHERITE.getTranslationKey(), "Netherite");
            add(ModInfuseTypes.POLONIUM.getTranslationKey(), "Polonium");
            add(ModInfuseTypes.ANTIMATTER.getTranslationKey(), "Antimatter");
            add("key.categories.tpsthings", "TPS Things");
            add("key.tpsthings.sugoi_menu", "Amazing Menu");
            add(ModGases.TIME_FLUX.getTranslationKey(), "Time Flux");
            add(ModGases.HOPE_ETHYLENE.getTranslationKey(), "HOPE Ethylene");
            add(ModGases.HOPE_HYDROGEN.getTranslationKey(), "HOPE Hydrogen");
            add(ModGases.HOPE_OXYGEN.getTranslationKey(), "HOPE Oxygen");
            add(ModGases.CARBON_DIOXIDE.getTranslationKey(), "Carbon Dioxide");
            add(ModGases.NOPE_GAS.getTranslationKey(), "Vaporised NOPE");
            add("fluid_type.tpsthings.nope", "NOPE");
            add("fluid_type.tpsthings.hope_ethylene", "Liquid HOPE Ethylene");

            add(ModContainerTypes.TIME_FLUX_COLLECTOR.getInternalRegistryName(), "Time Flux Collector");
            add(ModContainerTypes.TIME_ACCELERATOR.getInternalRegistryName(), "Time Accelerator");
            add(ModContainerTypes.TPS_GENERATOR.getInternalRegistryName(), "TPS Generator");
            add(ModContainerTypes.LAG_GENERATOR.getInternalRegistryName(), "Lag Generator");

            // 層の呼び名。番号は表に出さない
            add("tpsthings.layer.1", "Subtraction Layer");
            add("tpsthings.layer.2", "Instant Layer");
            add("tpsthings.layer.3", "Inviolable Layer");
            add("tpsthings.layer.4", "Behaviour Layer");
            add("tpsthings.layer.5", "Council Layer");
            add("tpsthings.layer.6", "Raw Value Layer");
            add("tpsthings.layer.7", "Falsehood Layer");
            add("tpsthings.layer.8", "Ending Layer");
            add("tpsthings.layer.9", "Erasure Layer");
            add("tpsthings.layer.10", "Index Layer");

            // The "Toward Oo" tree. Titles are the nicknames the materials carry
            add("advancements.tpsthings.oo.root.title", "Toward Oo");
            add("advancements.tpsthings.oo.root.description", "It began\nwith dirt and planks");
            add("advancements.tpsthings.oo.obtained", "Obtained %s");
            add("advancements.tpsthings.oo.super_compressed_cardboard_box.title", "Curse You, BlockEntity");
            add("advancements.tpsthings.oo.super_compressed_oredictionificator.title", "Creative-Only Alloy?");
            add("advancements.tpsthings.oo.glitch.title", "Vulnerability");
            add("advancements.tpsthings.oo.null_pointer.title", "NullPo");
            add("advancements.tpsthings.oo.undefined_behavior.title", "Gah");
            add("advancements.tpsthings.oo.bytecode.title", "0x304A304A");
            add("advancements.tpsthings.oo.java.title", "Javaaaaa");
            add("advancements.tpsthings.oo.c.title", "Friendly?");
            add("advancements.tpsthings.oo.jvm.title", "Javaaaaaaaaaaaaaaa");
            add("advancements.tpsthings.oo.mixin.title", "Mix-Death-n");
            add("advancements.tpsthings.oo.asm.title", "Low Level");
            add("advancements.tpsthings.oo.coremod.title", "Not in Production");
            add("advancements.tpsthings.oo.attack_module.title", "Endure");
            add("advancements.tpsthings.oo.defense_module.title", "Endure On");
            add("advancements.tpsthings.oo.shader.title", "Slap It On, It Looks Cool");
            add("advancements.tpsthings.oo.berwl.title", "A Tool for Bluffing");
            add("advancements.tpsthings.oo.prism.title", "Yay, Shiny...?");
            add("advancements.tpsthings.oo.sugoi_menu.title", "GuiGui");
            add("advancements.tpsthings.oo.infinity_ingot.title", "The Usual One");
            add("advancements.tpsthings.oo.eternity_ingot.title", "You Know Where This Goes");
            add("advancements.tpsthings.oo.unity_ingot.title", "Seen It Somewhere");
            add("advancements.tpsthings.oo.alloy_oo.title", "Is This... an Oo?");
            add("advancements.tpsthings.oo.qio_drive_absurd.title", "Care to Become Absurd Too?");
            add("advancements.tpsthings.oo.oo.title", "Oo");
            add("advancements.tpsthings.oo.oo.description", "Isn't it Oo?");

            // --- コードから出る文言 ---
            add("itemGroup." + Tpsthings.MODID, "TPS Things");
            add("item.tpsthings.oo.description", "It's wow...");
            add("tooltip.tpsthings.oo", "Oo");
            add("tooltip.tpsthings.oo.murmur.0", "It's wow...");
            add("tooltip.tpsthings.oo.murmur.1", "Ooh");
            add("tooltip.tpsthings.oo.murmur.2", "That has to be an oo");
            add("tooltip.tpsthings.oo.murmur.3", "Not quite oo");
            add("message.tpsthings.debug_accelerator.speed", "Speed: %s");
            add("gui.tpsthings.accelerator.range", "Range %s (%s across)");
            add("gui.tpsthings.accelerator.speed", "Speed x%s");
            add("gui.tpsthings.accelerator.no_target", "No target");
            add("gui.tpsthings.accelerator.targets", "%s machines / %s");
            add("gui.tpsthings.accelerator.running", "Running");
            add("gui.tpsthings.accelerator.starved", "Out of resources");
            add("gui.tpsthings.generator.tps_tab", "TPS: %s");
            add("gui.tpsthings.generator.output_tab", "Output: %s J/t");
            add("gui.tpsthings.generator.tps", "TPS %s");
            add("gui.tpsthings.generator.output", "%s J/t");
            add("gui.tpsthings.generator.idle", "Not turning");
            add("gui.tpsthings.tps_generator.condition", "The lighter, the faster");
            add("gui.tpsthings.lag_generator.condition", "The heavier, the faster");

            addPoems();
        }

        /** ja_jp と同じ位置に並ぶポエム。訳は意味を取って作り直している。 */
        private void addPoems() {
            poem(ModItems.UNDEFINED_BEHAVIOR.get(), "In the margin of the spec\nyou may write anything\neven demons out of your nose");
            poem(ModItems.MIXIN.get(), "Into someone else's body\nyou slip a quiet word\nwith one line at the head");
            poem(ModItems.COREMOD.get(), "Before the class is born\nrewrite its name\nwith no one noticing");
        }

        private void poem(net.minecraft.world.item.Item item, String text) {
            add(item.getDescriptionId() + ".poem", text);
        }
    }
}
