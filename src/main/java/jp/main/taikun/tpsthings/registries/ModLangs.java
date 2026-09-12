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
            add(Tpsthings.TAB.getId().toLanguageKey(), "TPS Things");

            add(ModItems.FROZEN_TICK.get(), "凍結した1tick");
            add(ModItems.INVULNERABLE_FLAG.get(), "無敵フラグ");
            add(ModItems.CANCELLED_EVENT.get(), "キャンセル済みイベント");
            add(ModItems.RAW_HEALTH.get(), "剥き出しの体力値");
            add(ModItems.LYING_READER.get(), "嘘つきの読み出し");
            add(ModItems.DEATH_HOOK.get(), "握り潰された死");
            add(ModItems.REMOVAL_VETO.get(), "除去の拒否権");
            add(ModItems.ERASED_INDEX.get(), "消された索引");
            add("tooltip.tpsthings.layer", "貫通層 L%s");
            add("jei.tpsthings.annihilation", "対消滅炉");
            add("jei.tpsthings.annihilation.nothing", "何も出ない");
            add("jei.tpsthings.annihilation.info",
                    "対消滅炉の上面に材料をまとめて投げ込むと反応します。結果は抽選で、外れると何も残りません。");
            add("jei.tpsthings.ritual.info",
                    "おおは作業台では作れません。対消滅炉の上に、次の素材を上から順に 1 つずつ投げ込みます。"
                            + "始まりの反HOPEシートは、他に何も無い上面に 1 枚だけ置いてください。"
                            + "先の層の素材を混ぜると失敗して「おおじゃないが」が残り、1 分間なにも投げ込まないと儀式は途切れます。");
            add("jei.tpsthings.ritual.step", "L%s %s");

            // 進捗ツリー「貫通層」。各段の題は層番号 + アイテム名、説明はそのアイテムのポエムを直接引く
            add("advancements.tpsthings.layer.title", "L%s %s");
            add("advancements.tpsthings.layer.root.title", "貫通層");
            add("advancements.tpsthings.layer.root.description", "希望の表面から\n世界の索引の底まで");
            add("advancements.tpsthings.layer.oo.description", "全ての層を降りきって\n出てきた言葉は それだけ");

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
        protected void addTranslations() {/*
            add(ModBlocks.EXAMPLE_MACHINE.getBlock(), "Example Machine");
            add(ModBlocks.TIME_FLUX_COLLECTOR.getBlock(), "Time Flux Collector");
            add(ModBlocks.TIME_ACCELERATOR.getBlock(), "Time Accelerator");
            add(ModBlocks.DEBUG_ACCELERATOR.get(), "Debug Accelerator");
            add(ModGases.TIME_FLUX.getTranslationKey(), "Time Flux");
            add(ModItems.HOPE_BIO_FUEL.get(), "HOPE Fuel");
            add(ModItems.HOPE_PELLET.get(), "HOPE Pellet");
            add(ModItems.HOPE_SUBSTRATE.get(), "HOPE Substrate");
            add(ModItems.HOPE_SHEET.get(), "HOPE Sheet");
            add(ModContainerTypes.TIME_FLUX_COLLECTOR.getInternalRegistryName(), "Time Flux Collector");
            add(Tpsthings.TAB.getId().toLanguageKey(), "TPS Things");*/
        }
    }
}
