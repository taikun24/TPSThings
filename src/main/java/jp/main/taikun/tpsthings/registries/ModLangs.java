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
            add(ModBlocks.EXAMPLE_MACHINE.getBlock(), "例の機械");
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
            add(ModItems.CAT_TEASER.get(), "猫じゃらし");
            add(ModItems.MIXIN.get(), "Mixin");
            add(ModItems.ANTI_HOPE_SHEET.get(), "反HOPEシート");
            add(ModItems.UNDEFINED_BEHAVIOR.get(), "未定義動作");
            add(ModItems.COREMOD.get(), "CoreMod");
            add(ModItems.JAVA_AGENT.get(), "Java Agent");
            // UNDEFINED_SHARD は意図的に翻訳を入れない (生の翻訳キーが出るのが正しい姿)
            add(ModBlocks.ANNIHILATION_CHAMBER.get(), "対消滅炉");
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

            add(ModItems.FROZEN_TICK.get(), "凍結した1tick");
            add(ModItems.INVULNERABLE_FLAG.get(), "無敵フラグ");
            add(ModItems.CANCELLED_EVENT.get(), "キャンセル済みイベント");
            add(ModItems.RAW_HEALTH.get(), "剥き出しの体力値");
            add(ModItems.LYING_READER.get(), "嘘つきの読み出し");
            add(ModItems.DEATH_HOOK.get(), "握り潰された死");
            add(ModItems.REMOVAL_VETO.get(), "除去の拒否権");
            add(ModItems.ERASED_INDEX.get(), "消された索引");
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
            add("jei.tpsthings.annihilation", "対消滅炉");
            add("jei.tpsthings.annihilation.nothing", "何も出ない");
            add("jei.tpsthings.annihilation.info",
                    "対消滅炉の上面に材料をまとめて投げ込むと反応します。結果は抽選で、外れると何も残りません。");
            add("jei.tpsthings.ritual.info",
                    "おおは作業台では作れません。対消滅炉の上に、次の素材を上から順に 1 つずつ投げ込みます。"
                            + "始まりの反HOPEシートは、他に何も無い上面に 1 枚だけ置いてください。"
                            + "先の層の素材を混ぜると失敗して「おおじゃないが」が残り、1 分間なにも投げ込まないと儀式は途切れます。");
            add("jei.tpsthings.ritual.step", "%s %s");

            // 進捗ツリー「貫通層」。各段の題は層の名前 + アイテム名、説明はそのアイテムのポエムを直接引く
            add("advancements.tpsthings.layer.title", "%s %s");
            add("advancements.tpsthings.layer.root.title", "貫通層");
            add("advancements.tpsthings.layer.root.description", "希望の表面から\n世界の索引の底まで");
            add("advancements.tpsthings.layer.oo.description", "全ての層を降りきって\n出てきた言葉は それだけ");

            // --- コードから出る文言 (Component.literal をやめた分) ---
            add("itemGroup." + Tpsthings.MODID, "TPS Things");
            add("item.tpsthings.cat_teaser.description", "しゃかしゃか");
            add("message.tpsthings.cat_teaser.none", "だれも見ていない");
            add("message.tpsthings.cat_teaser.charmed", "%s 匹が気になっている");
            add("item.tpsthings.oo.description", "It's wow...");
            add("tooltip.tpsthings.oo", "おお");
            add("tooltip.tpsthings.oo.murmur.0", "It's wow...");
            add("tooltip.tpsthings.oo.murmur.1", "おぉ");
            add("tooltip.tpsthings.oo.murmur.2", "これはおおだろ");
            add("tooltip.tpsthings.oo.murmur.3", "おおじゃないが");
            add("message.tpsthings.ritual.complete", "おお");
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
         * おお への系譜に並ぶ中間素材 (ItemLayer) のポエム。キーは「説明キー + .poem」、行は \n で区切る。
         * 表示は ItemPoemTooltip が担当する。
         */
        private void addPoems() {
            // UNDEFINED_SHARD は名前と同じく入れない。生のキーが出るのが正しい姿
            poem(ModItems.ANTI_HOPE_SHEET.get(), "希望を裏返すと\n絶望ではなく\n何も起きないが残る");
            poem(ModItems.FROZEN_TICK.get(), "殴られてから十の刻み\n世界が待ってくれる\nわずかな猶予");
            poem(ModItems.INVULNERABLE_FLAG.get(), "真偽値ひとつで\n刃は届かなくなる\n薄い 薄い旗");
            poem(ModItems.UNDEFINED_BEHAVIOR.get(), "仕様書の余白には\n何を書いてもいい\n鼻から悪魔が出ても");
            poem(ModItems.CANCELLED_EVENT.get(), "呼ばれた声は\n誰にも届かなかった\nキャンセル済み");
            poem(ModItems.RAW_HEALTH.get(), "包み紙を剥がした\n体力という数字は\n思ったより脆い");
            poem(ModItems.LYING_READER.get(), "尋ねるたびに\n満タンだと答える\n優しい嘘");
            poem(ModItems.MIXIN.get(), "他人の体に\nそっと言葉を差し込む\n頭の一行だけで");
            poem(ModItems.DEATH_HOOK.get(), "終わりの手続きを\n途中で握り潰す\n死はまだ来ない");
            poem(ModItems.REMOVAL_VETO.get(), "消えろと言われて\n首を横に振った\nそれだけのこと");
            poem(ModItems.COREMOD.get(), "クラスが生まれる前に\n名前を書き換える\n誰にも気づかれずに");
            poem(ModItems.ERASED_INDEX.get(), "世界の名簿から\n名前が消えても\nまだ息をしている");
            poem(ModItems.JAVA_AGENT.get(), "読み込まれるより先に\nそこにいた\n最初から ずっと");
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
            add(ModBlocks.EXAMPLE_MACHINE.getBlock(), "Example Machine");
            add(ModBlocks.TIME_FLUX_COLLECTOR.getBlock(), "Time Flux Collector");
            add(ModBlocks.TIME_ACCELERATOR.getBlock(), "Time Accelerator");
            add(ModBlocks.DEBUG_ACCELERATOR.get(), "Debug Time Accelerator");
            add(ModBlocks.TPS_GENERATOR.getBlock(), "TPS Generator");
            add(ModBlocks.LAG_GENERATOR.getBlock(), "Lag Generator");
            add(ModBlocks.ANNIHILATION_CHAMBER.get(), "Annihilation Chamber");

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
            add(ModItems.CAT_TEASER.get(), "Cat Teaser");
            add(ModItems.MIXIN.get(), "Mixin");
            add(ModItems.ANTI_HOPE_SHEET.get(), "Anti-HOPE Sheet");
            add(ModItems.UNDEFINED_BEHAVIOR.get(), "Undefined Behavior");
            add(ModItems.COREMOD.get(), "CoreMod");
            add(ModItems.JAVA_AGENT.get(), "Java Agent");
            add(ModItems.FROZEN_TICK.get(), "Frozen Tick");
            add(ModItems.INVULNERABLE_FLAG.get(), "Invulnerability Flag");
            add(ModItems.CANCELLED_EVENT.get(), "Cancelled Event");
            add(ModItems.RAW_HEALTH.get(), "Raw Health Value");
            add(ModItems.LYING_READER.get(), "Lying Getter");
            add(ModItems.DEATH_HOOK.get(), "Swallowed Death");
            add(ModItems.REMOVAL_VETO.get(), "Veto on Removal");
            add(ModItems.ERASED_INDEX.get(), "Erased Index");
            // UNDEFINED_SHARD は ja_jp と同じく入れない (生の翻訳キーが出るのが正しい姿)

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

            add("jei.tpsthings.annihilation", "Annihilation Chamber");
            add("jei.tpsthings.annihilation.nothing", "Nothing comes out");
            add("jei.tpsthings.annihilation.info",
                    "Throw the ingredients together onto the top face of the annihilation chamber to make it react. "
                            + "The result is drawn at random, and a bad draw leaves nothing behind.");
            add("jei.tpsthings.ritual.info",
                    "Oo cannot be made at a crafting table. Throw the following onto the annihilation chamber "
                            + "one at a time, from the top of the list down. Start by placing a single anti-HOPE sheet "
                            + "on an otherwise empty top face. Mixing in an ingredient from a deeper layer fails the "
                            + "ritual and leaves \"not quite oo\" behind, and throwing nothing for a minute breaks it off.");
            add("jei.tpsthings.ritual.step", "%s %s");

            // 進捗ツリー「貫通層」
            add("advancements.tpsthings.layer.title", "%s %s");
            add("advancements.tpsthings.layer.root.title", "Penetration Layers");
            add("advancements.tpsthings.layer.root.description", "From the surface of hope\nto the floor of the world's index");
            add("advancements.tpsthings.layer.oo.description", "Every layer descended\nand the word that came out was only that");

            // --- コードから出る文言 ---
            add("itemGroup." + Tpsthings.MODID, "TPS Things");
            add("item.tpsthings.cat_teaser.description", "shakashaka");
            add("message.tpsthings.cat_teaser.none", "Nobody is watching");
            add("message.tpsthings.cat_teaser.charmed", "%s of them are interested");
            add("item.tpsthings.oo.description", "It's wow...");
            add("tooltip.tpsthings.oo", "Oo");
            add("tooltip.tpsthings.oo.murmur.0", "It's wow...");
            add("tooltip.tpsthings.oo.murmur.1", "Ooh");
            add("tooltip.tpsthings.oo.murmur.2", "That has to be an oo");
            add("tooltip.tpsthings.oo.murmur.3", "Not quite oo");
            add("message.tpsthings.ritual.complete", "Oo");
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
            poem(ModItems.ANTI_HOPE_SHEET.get(), "Turn hope inside out\nand what is left is not despair\nbut nothing happening at all");
            poem(ModItems.FROZEN_TICK.get(), "Ten ticks after the blow\nthe world waits for you\na very small mercy");
            poem(ModItems.INVULNERABLE_FLAG.get(), "One boolean\nand the blade no longer reaches\na thin, thin flag");
            poem(ModItems.UNDEFINED_BEHAVIOR.get(), "In the margin of the spec\nyou may write anything\neven demons out of your nose");
            poem(ModItems.CANCELLED_EVENT.get(), "The voice that was called\nreached no one\ncancelled");
            poem(ModItems.RAW_HEALTH.get(), "Stripped of its wrapping\nthe number called health\nis frailer than you thought");
            poem(ModItems.LYING_READER.get(), "Every time you ask\nit answers that you are full\na kind lie");
            poem(ModItems.MIXIN.get(), "Into someone else's body\nyou slip a quiet word\nwith one line at the head");
            poem(ModItems.DEATH_HOOK.get(), "The closing procedure\ncrushed halfway through\ndeath is not here yet");
            poem(ModItems.REMOVAL_VETO.get(), "Told to disappear\nit shook its head\nand that was all");
            poem(ModItems.COREMOD.get(), "Before the class is born\nrewrite its name\nwith no one noticing");
            poem(ModItems.ERASED_INDEX.get(), "Struck from the world's register\nthe name is gone\nand it is still breathing");
            poem(ModItems.JAVA_AGENT.get(), "It was there\nbefore anything was loaded\nfrom the very beginning");
        }

        private void poem(net.minecraft.world.item.Item item, String text) {
            add(item.getDescriptionId() + ".poem", text);
        }
    }
}
