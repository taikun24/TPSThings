package jp.main.taikun.tpsthings.damage;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@link DamageGuard} の操作面。
 *
 * watch で呼び出し元を集め、list で番号を見て、block で番号を指定して止める。
 * 止める対象はここでは一切決め打ちしない。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = Tpsthings.MODID)
public final class DamageGuardCommand {

    private DamageGuardCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        // 観測したものを見る側。犯人を探している間はこの下だけで済む
        LiteralArgumentBuilder<CommandSourceStack> log = Commands.literal("log")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("trace")
                        .then(Commands.argument("index", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        DamageGuard.sightings().stream().map(s -> s.signature).toList(), builder))
                                .executes(ctx -> trace(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "index")))))
                .then(Commands.literal("gate")
                        .executes(ctx -> gate(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("writers")
                        .then(Commands.argument("owner", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("net.minecraft.world.entity.Entity",
                                                "net.minecraft.world.entity.LivingEntity"), builder))
                                .executes(ctx -> writers(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "owner")))));

        // 止める側。block と disable は強さが違うだけで役目は同じなので隣に置く
        LiteralArgumentBuilder<CommandSourceStack> stop = Commands.literal("stop")
                .then(Commands.literal("list")
                        .executes(ctx -> stopped(ctx.getSource())))
                .then(Commands.literal("block")
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        DamageGuard.sightings().stream().map(s -> s.signature).toList(), builder))
                                .executes(ctx -> setBlocked(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"), true))))
                .then(Commands.literal("unblock")
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        DamageGuard.blocked(), builder))
                                .executes(ctx -> setBlocked(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"), false))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        DamageGuard.knownSignatures(), builder))
                                .executes(ctx -> setDisabled(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"), true))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        MethodDisabler.disabled(), builder))
                                .executes(ctx -> setDisabled(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"), false))));

        // 設定。一度決めたらしばらく触らないものをまとめる
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
                .then(Commands.literal("motion")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> motion(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("auto")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> auto(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("revert")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> revert(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("stale")
                        .then(Commands.argument("ignore", BoolArgumentType.bool())
                                .executes(ctx -> stale(ctx.getSource(), BoolArgumentType.getBool(ctx, "ignore")))))
                .then(Commands.literal("notify")
                        .executes(ctx -> showNotify(ctx.getSource()))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("chat", "actionbar", "log"), builder))
                                .executes(ctx -> notify(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("seal")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> seal(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("canon")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> canon(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("repair")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> repair(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("strikeplayers")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> strikePlayers(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("strikerestore")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> strikeRestore(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("maxdepth")
                        .executes(ctx -> showMaxDepth(ctx.getSource()))
                        .then(Commands.argument("depth", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> setMaxDepth(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "depth")))));

        LiteralArgumentBuilder<CommandSourceStack> damage = Commands.literal("damage")
                .then(Commands.literal("watch")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> watch(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("protect")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> protect(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("agentscan")
                        .executes(ctx -> agentScan(ctx.getSource())))
                .then(Commands.literal("agentstrip")
                        .then(Commands.literal("allforeign")
                                .executes(ctx -> agentStripAll(ctx.getSource())))
                        .then(Commands.argument("transformer", StringArgumentType.string())
                                .executes(ctx -> agentStrip(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "transformer"), false))
                                .then(Commands.literal("all")
                                        .executes(ctx -> agentStrip(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "transformer"), true)))))
                .then(Commands.literal("dummy")
                        .executes(ctx -> dummy(ctx.getSource()))
                        .then(Commands.literal("clear")
                                .executes(ctx -> dummyClear(ctx.getSource()))))
                .then(Commands.literal("strikeself")
                        .executes(ctx -> strikeSelf(ctx.getSource())))
                // 設定ではなく実行なので set の下ではなくここ (strikeself / dummy / reset と同じ)
                .then(Commands.literal("strikeall")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> strikeAll(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"), false))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> strikeAll(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius"), true)))))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource())))
                .then(log)
                .then(stop)
                .then(set);

        event.getDispatcher().register(
                Commands.literal(Tpsthings.MODID)
                        .requires(source -> source.hasPermission(2))
                        .then(damage));
    }

    /**
     * 索引に載っている生き物を、範囲ごと打つ。
     *
     * <p>相手が<b>検索から消えている</b>ときの最後の手段。読み出しに濾し器を挟まれると、
     * 当たり判定も照会も素通りするので、殴打も視野の円錐も相手に届かない — 層に降りる以前に
     * 打つ対象が無い。{@link StrikeCensus} は検索を通さず索引そのものを読むので、
     * そこに居る限りは掴める。
     *
     * <p>打つ相手をこちらで選べないぶん、巻き添えが出る。だから<b>確認を挟む</b>:
     * 一度目は何に当たるかを数えて見せるだけで、打つのは {@code confirm} を付けたときだけ。
     * 保護対象と、撃った本人は外す。
     */
    private static int strikeAll(CommandSourceStack source, int radius, boolean confirm) {
        ServerLevel level = source.getLevel();
        Vec3 center = source.getPosition();
        Entity self = source.getEntity();
        List<LivingEntity> targets = StrikeCensus.sweepTargets(level, center, radius, self);
        if (targets.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "半径 " + radius + " の索引に、打てる生き物は居ませんでした")
                    .withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        if (!confirm) {
            // 検索に出てこない相手を含むので、見えている数と合わないのが普通。
            // 何に当たるのかを先に見せてからでないと、取り返しがつかない
            String sample = targets.stream()
                    .limit(8)
                    .map(body -> body.getName().getString())
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            source.sendSuccess(() -> Component.literal(
                    "半径 " + radius + " の索引に生き物が " + targets.size() + " 体います (" + sample
                            + (targets.size() > 8 ? ", …" : "") + ")。"
                            + "打つなら /" + Tpsthings.MODID + " damage strikeall " + radius + " confirm")
                    .withStyle(ChatFormatting.YELLOW), true);
            return targets.size();
        }
        Player attacker = self instanceof Player player ? player : null;
        int struck = targets.size();
        int down = PiercingStrike.strikeEach(attacker, targets);
        source.sendSuccess(() -> Component.literal(
                "索引から " + struck + " 体に打ちました (通った " + down + " 体)")
                .withStyle(ChatFormatting.GREEN), true);
        GuardNotice.info("貫通攻撃: 掃討 — 半径 " + radius + " の索引から " + struck
                + " 体に打ちました (通った " + down + " 体)。検索に出ない相手も索引から拾っています");
        return struck;
    }

    /**
     * 自分に打つ。
     *
     * <p>装備型の不死を確かめるとき、相手役を用意せずに済む — 着たまま撃てば、その装備が
     * 層のどこで止めるかがそのまま出る。殴打の入り口 (当たり判定・視野の円錐) を通らないので、
     * <b>検索から消える相手でも必ず打てる</b>のも狙い。
     *
     * <p>自分の防御が効いていると当然のように拒否されるが、それは相手の装備が強いのではなく
     * こちらが守っているだけ。見分けがつかないと測定にならないので、保護されているなら先に言う。
     */
    private static int strikeSelf(CommandSourceStack source) {
        if (!(source.getEntity() instanceof LivingEntity self)) {
            source.sendFailure(Component.literal(
                    "自分が世界に居ないので打てません (コンソールからは撃てません)"));
            return 0;
        }
        if (AutoGuard.isProtected(self)) {
            // ここを黙って打つと「耐えた」に見えるが、耐えているのは自分の関所の方
            source.sendSuccess(() -> Component.literal(
                    "いまの自分は保護対象です。拒否されても相手の装備ではなく、こちらの防御が理由になります"
                            + " (外すなら /" + Tpsthings.MODID + " damage protect false)")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        Player attacker = self instanceof Player player ? player : null;
        PiercingStrike.Result result = PiercingStrike.strike(attacker, self);
        source.sendSuccess(() -> Component.literal("自分に打ちました: " + result)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int watch(CommandSourceStack source, boolean enabled) {
        DamageGuard.setWatching(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(
                enabled ? "呼び出し元の記録を開始しました" : "呼び出し元の記録を停止しました")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<DamageGuard.Sighting> sightings = DamageGuard.sightings();
        if (sightings.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    DamageGuard.isWatching()
                            ? "まだ何も記録されていません"
                            : "記録がありません (/" + Tpsthings.MODID + " damage watch true で開始)")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (int i = 0; i < sightings.size(); i++) {
            DamageGuard.Sighting sighting = sightings.get(i);
            int index = i;
            source.sendSuccess(() -> Component.literal(
                    "[" + index + "] " + DamageGuard.describe(sighting.signature))
                    .withStyle(DamageGuard.isBlocked(sighting.signature)
                            ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE)
                    .append(Component.literal(
                            "  " + sighting.lastKind + " x" + sighting.count
                                    + " → " + sighting.lastVictim
                                    + " (" + String.format("%.1f", sighting.lastAmount) + ")"
                                    + (DamageGuard.isBlocked(sighting.signature) ? " [blocked]" : ""))
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return sightings.size();
    }

    /**
     * いま止めているものを一覧する。
     *
     * block と disable を分けて出していたが、探すときは「何を止めているか」を
     * まとめて見たい。強さの違いは行頭に出す。
     */
    private static int stopped(CommandSourceStack source) {
        var blocked = DamageGuard.blocked();
        var disabled = MethodDisabler.disabled();
        if (blocked.isEmpty() && disabled.isEmpty()) {
            source.sendSuccess(() -> Component.literal("止めているものはありません"
                    + (MethodDisabler.isReady() ? "" : " (agent 未アタッチ)"))
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        disabled.forEach(signature -> source.sendSuccess(
                () -> Component.literal("disable " + signature).withStyle(ChatFormatting.RED), false));
        blocked.forEach(signature -> source.sendSuccess(
                () -> Component.literal("block   " + signature).withStyle(ChatFormatting.GOLD), false));
        return blocked.size() + disabled.size();
    }

    private static int setBlocked(CommandSourceStack source, String target, boolean block) {
        String signature = DamageGuard.resolve(target);
        if (signature == null) {
            source.sendFailure(Component.literal(
                    "解決できません: " + target + " (list の番号か クラス名#メソッド名 を指定)"));
            return 0;
        }
        boolean changed = block ? DamageGuard.block(signature) : DamageGuard.unblock(signature);
        if (changed) {
            GuardConfig.save();
        }
        if (!changed) {
            source.sendSuccess(() -> Component.literal(
                    signature + " は既に" + (block ? "ブロック済み" : "未ブロック") + "です")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                (block ? "ブロックしました: " : "解除しました: ") + signature)
                .withStyle(block ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        return 1;
    }

    private static int motion(CommandSourceStack source, boolean enabled) {
        DamageGuard.setMotionGuard(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "位置・速度の絞り所を有効にしました (強制移動を watch/block できます)"
                : "位置・速度の絞り所を無効にしました")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int auto(CommandSourceStack source, boolean enabled) {
        AutoGuard.setEnabled(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "自動対処を有効にしました (protect した対象の HP 減少を検出して段階的に止めます)"
                : "自動対処を無効にしました")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 減らされた HP をその tick のうちに書き戻す。
     *
     * 自動対処の中で唯一、犯人が分からなくても効く手。相手のコードには触らないので、
     * block や disable のように相手を行動不能にする副作用も無い。
     */
    private static int revert(CommandSourceStack source, boolean enabled) {
        AutoGuard.setReverting(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "減らされた HP を書き戻します (自動対処が有効な保護対象のみ)"
                : "書き戻しをやめました (犯人を特定して止める手だけになります)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 生死を答える読み出しを正規の実装に戻す。
     *
     * getHealth() / isAlive() / isDeadOrDying() の本体を書き換えて「死んだことにする」
     * 相手への対抗。値ではなく仕組みを直すので、関所にも書き戻しにも映らない層に効く。
     */
    private static int canon(CommandSourceStack source, boolean enabled) {
        String failure = ReaderGuard.setCanonical(enabled);
        if (failure != null) {
            source.sendFailure(Component.literal("正規化できませんでした: " + failure)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "保護対象の生死の読み出しを正規の実装に戻しました (getHealth / isAlive / "
                        + "isDeadOrDying は同期データから答えます。保護対象以外は元のまま)"
                : "読み出しを元の本体に返しました (他所の書き換えも復活します)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 剥がされた関所の張り直しを入り切りする。
     *
     * retransform は元のバイト列からやり直すので、他所が一発物で掛けた書き換えを
     * 巻き戻す。こちらの Mixin は元のバイト列に含まれるので戻ってくる。
     */
    private static int repair(CommandSourceStack source, boolean enabled) {
        RepairGuard.setEnabled(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "剥がされた関所を自動で張り直します"
                : "関所の張り直しをやめました (剥がされたままになります)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 貫通攻撃をプレイヤーにも抹消層・索引層まで打つか。
     *
     * 死亡処理が通っていればリスポーンで戻れるが、死を拒否した相手は
     * 世界から剥がされたまま再接続まで動けなくなる。
     */
    private static int strikePlayers(CommandSourceStack source, boolean enabled) {
        PiercingStrike.setPlayersFullDepth(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "貫通攻撃をプレイヤーにも索引層 (除去・索引) まで打ちます"
                : "貫通攻撃はプレイヤーには終焉層 (死) までにします")
                .withStyle(enabled ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 消しきれなかった相手を、世界の索引へ戻すか。
     *
     * 戻さないと、tick 一覧と索引からだけ外れた相手が「動けるのにブロックが壊せない」
     * 中途半端な状態で残る。無力化と見るか壊したと見るかは使う側が決める。
     */
    private static int strikeRestore(CommandSourceStack source, boolean enabled) {
        PiercingStrike.setRestoreOnFailure(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "貫通攻撃で消しきれなかった相手は、世界の索引へ戻します"
                : "貫通攻撃で消しきれなくても、外した索引は戻しません (今までどおり)")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
        return 1;
    }

    /**
     * 「直近に観測した呼び出し元がありません」で止まるのをやめる。
     *
     * 既定では、古い連鎖に対して段を進めない。落下ダメージなど無関係な減少を
     * 直前に見た相手のせいにしないための安全弁だが、絞り所を一切通らない経路を
     * 追いかけるときは、この安全弁のせいで永久に前へ進めなくなる。
     */
    private static int stale(CommandSourceStack source, boolean ignore) {
        AutoGuard.setIgnoreStaleChains(ignore);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(ignore
                ? "古い呼び出し元でも段を進めます (無関係なダメージを誤って犯人にする可能性が上がります)"
                : "直近に観測した呼び出し元が無いときは何もしません (既定)")
                .withStyle(ignore ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 各絞り所が何回通ったか。
     *
     * 「何も記録されない」には二通りある。関所が生きていて誰も通っていないのか、
     * 関所そのものが刺さっていないのか。この数字だけがそれを分ける。
     * 普通に遊んでいれば HURT は必ず伸びるので、0 のままなら関所の側が死んでいる。
     */
    private static int gate(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("絞り所の作動回数")
                .withStyle(ChatFormatting.AQUA), false);
        DamageGuard.gateReport().forEach(line -> source.sendSuccess(
                () -> Component.literal("  " + line).withStyle(ChatFormatting.WHITE), false));
        return 1;
    }

    /**
     * 報告の出し先を変える。
     *
     * 段を進めるたびに報告が出るので、チャットのままだと他の会話が流れる。
     * どこへ出しても、ログには必ず残るようにしてある。
     */
    private static int notify(CommandSourceStack source, String modeText) {
        GuardNotice.Mode mode = GuardNotice.parse(modeText);
        if (mode == null) {
            source.sendFailure(Component.literal(
                    "不明な出し先: " + modeText + " (chat / actionbar / log)"));
            return 0;
        }
        GuardNotice.setMode(mode);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(switch (mode) {
            case CHAT -> "報告をチャットに出します (既定)";
            case ACTIONBAR -> "報告をアクションバーに出します (長い文は切れます。全文はログにあります)";
            case LOG -> "報告を画面に出しません (ログには残ります)";
        }).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int showNotify(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "報告の出し先: " + GuardNotice.modeName())
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    /**
     * 保護対象の HP 減少を、呼び出し元を問わず全部拒否する。
     *
     * DamageGuard 本来の「犯人を特定して、その署名だけ止める」とは性質が違う。
     * 原因が分からないまま死に続けるときの緊急避難として置いてある。
     * 読み出し側も最後に通した値で返すようになるので、HP を読んで殺しに来る経路も塞がる。
     */
    private static int seal(CommandSourceStack source, boolean enabled) {
        HealthGuard.setSealed(enabled);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "保護対象を封印しました (HP の減少と、殺意のある削除を拒否します)"
                : "封印を解除しました")
                .withStyle(enabled ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
        if (enabled && AutoGuard.protectedCount() == 0) {
            source.sendSuccess(() -> Component.literal(
                    "  保護対象が居ないので、いまは何も起きません (/" + Tpsthings.MODID
                            + " damage protect true)")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    /**
     * 出した的の UUID。片付けのときだけ使う。
     *
     * 保護一覧とは別に持つ。保護は「守る対象」の話で、こちらは「こちらが出した物」の話。
     * 混ぜると、手動保護した本物の Mob まで片付けで消してしまう。
     */
    private static final Set<UUID> DUMMIES = new LinkedHashSet<>();

    /**
     * おおを着た的を目の前に出す。
     *
     * <p>防御は<b>着せるだけ</b>で付く。関所の側に的用の分岐は一切足していないので、
     * 「着たプレイヤーと同じ防御」は写しではなく同じコードパスそのものになる。
     *
     * <p>プレイヤーにしか無い層 ({@code ServerPlayer#die} の関所、
     * {@link RespawnGuard} の復帰、飛行の復元) だけは的では通らない。
     */
    private static int dummy(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("プレイヤーとして実行してください"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Zombie dummy = EntityType.ZOMBIE.create(level);
        if (dummy == null) {
            source.sendFailure(Component.literal("的を作れませんでした"));
            return 0;
        }
        Vec3 look = player.getLookAngle();
        Vec3 spot = player.position().add(look.x * 3, 0, look.z * 3);
        dummy.moveTo(spot.x, player.getY(), spot.z, player.getYRot() + 180f, 0f);
        // 殴り返してこない・逃げない・湧き潰しで消えない。試し撃ちの間ずっとそこに居てほしい
        dummy.setNoAi(true);
        dummy.setPersistenceRequired();
        dummy.setCustomName(Component.literal("おおの的"));
        dummy.setCustomNameVisible(true);
        // ゾンビは素で防具値 2 を持っている。プレイヤーは 0 なので、
        // 揃えておかないと減算層の比較だけ的の方が有利になる
        AttributeInstance armor = dummy.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(0);
        }
        dummy.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.OO.get()));
        // 落とさせない。拾って着られるとこちらの保護が的の外へ広がる
        dummy.setDropChance(EquipmentSlot.CHEST, 0f);
        level.addFreshEntity(dummy);
        // 装備変更イベントは次の tick まで飛ばない。出した瞬間から守られていてほしいので、
        // ここで一度直接合わせる (次の tick に同じ値でもう一度来るだけ)
        AutoGuard.syncEquipProtection(dummy, true);
        DUMMIES.add(dummy.getUUID());
        source.sendSuccess(() -> Component.literal(
                "おおを着た的を出しました (保護対象 " + AutoGuard.protectedCount() + " 体)")
                .withStyle(ChatFormatting.GREEN), true);
        source.sendSuccess(() -> Component.literal(
                "  片付けは /" + Tpsthings.MODID + " damage dummy clear。"
                        + "ゾンビなので日中の屋外では燃えます (記録が焼け死にで埋まります)")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** 出した的を片付ける。保護を外してから消す — 順番を逆にすると関所が削除を拒む。 */
    private static int dummyClear(CommandSourceStack source) {
        int removed = 0;
        for (UUID id : DUMMIES) {
            for (ServerLevel level : source.getServer().getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity == null) {
                    continue;
                }
                AutoGuard.setProtected(entity, false);
                entity.discard();
                removed++;
                break;
            }
        }
        DUMMIES.clear();
        int count = removed;
        source.sendSuccess(() -> Component.literal("的を " + count + " 体片付けました")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int protect(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("プレイヤーとして実行してください"));
            return 0;
        }
        // 装備由来の同期 (setProtected) とは別の手動枠に入れる。同じ枠に入れると、
        // 装備変更やリスポーンのたびに同期が上書きして手動指定が消える
        AutoGuard.setManuallyProtected(player, enabled);
        if (enabled) {
            AutoGuard.setEnabled(true);
        }
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(enabled
                ? "保護対象に追加しました (装備に関係なく、外すまで保護されます)"
                : "手動の保護から外しました")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "watch=" + DamageGuard.isWatching()
                        + " motion=" + DamageGuard.isMotionGuard()
                        + " auto=" + AutoGuard.isEnabled()
                        + " revert=" + AutoGuard.isRevertEnabled()
                        + " stale=" + AutoGuard.isIgnoringStaleChains()
                        + " seal=" + HealthGuard.isSealed()
                        + " canon=" + ReaderGuard.isCanonical()
                        + (StateProbe.count() > 0 ? " 中和=" + StateProbe.count() : "")
                        + " repair=" + RepairGuard.isEnabled()
                        + (RepairGuard.repairCount() > 0
                                ? "(張り直し " + RepairGuard.repairCount() + ")" : "")
                        + " notify=" + GuardNotice.modeName()
                        + " protected=" + AutoGuard.protectedCount()
                        + " agent=" + (MethodDisabler.isReady() ? "attached" : "未アタッチ"))
                .withStyle(ChatFormatting.AQUA), false);
        AutoGuard.autoApplied().forEach(line -> source.sendSuccess(
                () -> Component.literal("  [auto] " + line).withStyle(ChatFormatting.RED), false));
        StateProbe.describe().forEach(line -> source.sendSuccess(
                () -> Component.literal("  [probe] " + line).withStyle(ChatFormatting.YELLOW), false));
        AutoGuard.describeProgress().forEach(line -> source.sendSuccess(
                () -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false));
        AttachGuard.scan().forEach(line -> source.sendSuccess(
                () -> Component.literal("  [agent!] " + line).withStyle(ChatFormatting.RED), false));
        return 1;
    }

    /**
     * 外部の自己アタッチ Java Agent の痕跡を調べる。
     *
     * 見つけても止められはしない ({@link AttachGuard} の説明どおり) が、
     * 「関所に映らない殺し方」の出所を名指しできる。原因が分からないまま死ぬときの
     * 手がかりとして、status とは別に単独でも呼べるようにしておく。
     */
    private static int agentScan(CommandSourceStack source) {
        List<String> findings = new java.util.ArrayList<>(AttachGuard.scan());
        // 明示的な検証なので深掘りも走らせる。自前 agent が未確保なら確保してよい
        // (クラス一覧を得るのに要る。どのみち防御で確保するもの)
        findings.addAll(AttachGuard.scanInstrumentation(true));
        if (findings.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "外部 Java Agent の痕跡は見つかりませんでした")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "外部 Java Agent の疑いがある痕跡を " + findings.size() + " 件検出しました "
                        + "(止められないので、出所の手がかりとして見てください):")
                .withStyle(ChatFormatting.YELLOW), false);
        findings.forEach(line -> source.sendSuccess(
                () -> Component.literal("  ⚠ " + line).withStyle(ChatFormatting.RED), false));
        return findings.size();
    }

    /**
     * 掴んだ敵の変換器を名指しで剥がし、書き換えられたクラスを retransform で戻す。
     *
     * <p><b>剥がす対象は人が指定する。</b> 「読める ≠ 敵」で、正規の agent (軽量化 Mod 等) も
     * 同じように見えるため、名指し以外では剥がさないのが安全弁。名前は agentscan の表示から採る。
     * 既定は retransform をエンティティに絞る (速く安全)。{@code all} を付けると全クラスに広げる
     * (相手が呼び出し箇所まで書き換えている場合に要るが、重い)。
     */
    private static int agentStrip(CommandSourceStack source, String transformer, boolean allClasses) {
        source.sendSuccess(() -> Component.literal(
                "変換器を剥がします: " + transformer + (allClasses ? " (retransform 全クラス)" : ""))
                .withStyle(ChatFormatting.YELLOW), false);
        List<String> report = AttachGuard.stripTransformer(transformer, allClasses);
        report.forEach(line -> source.sendSuccess(
                () -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false));
        return report.size();
    }

    /**
     * 決定実験: 掴めた外部変換器を全部剥がして全クラス retransform する。
     *
     * 「殺しの本線は変換器か、それとも変換器の外か」を白黒つけるための一手。
     * 巻き添え前提 (正規 agent の変換器も外れる) なので常用ではない。
     */
    private static int agentStripAll(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "全外部変換器を剥がします (巻き添えあり・決定実験)")
                .withStyle(ChatFormatting.YELLOW), false);
        List<String> report = AttachGuard.stripAllForeign();
        report.forEach(line -> source.sendSuccess(
                () -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false));
        return report.size();
    }

    private static int showMaxDepth(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "遡る段数の上限: " + AutoGuard.getMaxDepth())
                .withStyle(ChatFormatting.AQUA), false);
        return AutoGuard.getMaxDepth();
    }

    private static int setMaxDepth(CommandSourceStack source, int depth) {
        int applied = AutoGuard.setMaxDepth(depth);
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(
                "遡る段数の上限を " + applied + " にしました"
                        + (applied >= 5 ? " (深くすると相手を行動不能にしやすくなります)" : ""))
                .withStyle(applied >= 5 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
        return applied;
    }

    /** 自動でやったことを全部取り消す。やりすぎた時の戻し口。 */
    private static int reset(CommandSourceStack source) {
        int reverted = AutoGuard.resetAll();
        source.sendSuccess(() -> Component.literal(
                "自動で適用した措置を " + reverted + " 件取り消しました (手動で入れたものは残しています)")
                .withStyle(ChatFormatting.GREEN), true);
        return reverted;
    }

    /**
     * 指定クラスのフィールドを直接書き換えているメソッドを列挙する。
     *
     * setter を通さない書き込みは絞り所では捕まらないので、静的に探すしかない。
     */
    private static int writers(CommandSourceStack source, String owner) {
        String failure = MethodDisabler.ensureReady();
        if (failure != null) {
            source.sendFailure(Component.literal(failure));
            return 0;
        }
        List<FieldWriterScanner.Write> writes = FieldWriterScanner.scan(owner.trim(), 64);
        if (writes.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    owner + " のフィールドを直接書いているコードは見つかりませんでした")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                owner + " のフィールドを直接書いているメソッド (" + writes.size() + " 件)")
                .withStyle(ChatFormatting.AQUA), false);
        writes.forEach(write -> source.sendSuccess(
                () -> Component.literal("  " + write).withStyle(ChatFormatting.WHITE), false));
        return writes.size();
    }

    /** その観測時のスタックを全部出す。梯子のどの段を潰すか選ぶために使う。 */
    private static int trace(CommandSourceStack source, String indexText) {
        String signature = DamageGuard.resolve(indexText);
        if (signature == null) {
            source.sendFailure(Component.literal("解決できません: " + indexText));
            return 0;
        }
        List<DamageGuard.Sighting> sightings = DamageGuard.sightings();
        DamageGuard.Sighting sighting = sightings.stream()
                .filter(s -> s.signature.equals(signature))
                .findFirst()
                .orElse(null);
        if (sighting == null || sighting.lastFrames.isEmpty()) {
            source.sendFailure(Component.literal("記録がありません: " + signature));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(signature + " のスタック (上が呼ばれた側)")
                .withStyle(ChatFormatting.AQUA), false);
        for (String frame : sighting.lastFrames) {
            // 出所が分かっているものは、バニラの名前を着ていても暗く沈めない
            boolean foreign = DamageGuard.isForeign(frame);
            source.sendSuccess(() -> Component.literal("  " + DamageGuard.describe(frame))
                    .withStyle(foreign ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), false);
        }
        return sighting.lastFrames.size();
    }

    private static int setDisabled(CommandSourceStack source, String target, boolean disable) {
        String signature = DamageGuard.resolve(target);
        if (signature == null) {
            source.sendFailure(Component.literal(
                    "解決できません: " + target + " (list の番号か クラス名#メソッド名 を指定)"));
            return 0;
        }
        String failure = disable
                ? MethodDisabler.disable(signature)
                : MethodDisabler.restore(signature);
        if (failure != null) {
            source.sendFailure(Component.literal(failure));
            return 0;
        }
        GuardConfig.save();
        source.sendSuccess(() -> Component.literal(
                (disable ? "メソッドを無効化しました: " : "メソッドを復元しました: ") + signature)
                .withStyle(disable ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        DamageGuard.clearSightings();
        source.sendSuccess(() -> Component.literal("記録を消去しました (ブロックリストは維持)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
