package jp.main.taikun.tpsthings.profile;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * {@link TickProfiler} の操作面。
 *
 * start で測り始め、しばらく遊んでから top で見る。何が出てくるかは実測次第で、
 * ここでは特定の種類を前提にしない。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = Tpsthings.MODID)
public final class ProfileCommand {

    private static final int DEFAULT_LIMIT = 10;

    private ProfileCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> profile = Commands.literal("profile")
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource())))
                .then(Commands.literal("top")
                        .executes(ctx -> top(ctx.getSource(), null, DEFAULT_LIMIT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> top(ctx.getSource(), null,
                                        IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("blocks")
                        .executes(ctx -> top(ctx.getSource(), TickProfiler.Scope.BLOCK_ENTITY, DEFAULT_LIMIT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> top(ctx.getSource(), TickProfiler.Scope.BLOCK_ENTITY,
                                        IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("entities")
                        .executes(ctx -> top(ctx.getSource(), TickProfiler.Scope.ENTITY, DEFAULT_LIMIT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> top(ctx.getSource(), TickProfiler.Scope.ENTITY,
                                        IntegerArgumentType.getInteger(ctx, "count")))));

        event.getDispatcher().register(
                Commands.literal(Tpsthings.MODID)
                        .requires(source -> source.hasPermission(2))
                        .then(profile));
    }

    private static int start(CommandSourceStack source) {
        TickProfiler.reset();
        TickProfiler.setRunning(true);
        source.sendSuccess(() -> Component.literal(
                "計測を開始しました。しばらく待ってから /" + Tpsthings.MODID + " profile top で見てください")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        TickProfiler.setRunning(false);
        source.sendSuccess(() -> Component.literal(
                "計測を停止しました (結果は残しています)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        TickProfiler.reset();
        source.sendSuccess(() -> Component.literal("計測結果を消去しました")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int top(CommandSourceStack source, TickProfiler.Scope scope, int limit) {
        List<TickProfiler.Entry> entries = TickProfiler.top(scope, limit);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(TickProfiler.isRunning()
                    ? "まだ何も記録されていません"
                    : "記録がありません (/" + Tpsthings.MODID + " profile start で開始)")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        long ticks = Math.max(1L, TickProfiler.elapsedTicks());
        long total = TickProfiler.totalNanos(scope);

        source.sendSuccess(() -> Component.literal(
                (scope == null ? "全体" : scope == TickProfiler.Scope.BLOCK_ENTITY ? "BlockEntity" : "エンティティ")
                        + " / " + ticks + " tick / 合計 " + millis(total) + " ms"
                        + " (" + millisPerTick(total, ticks) + " ms/tick)"
                        + (TickProfiler.isRunning() ? " [計測中]" : ""))
                .withStyle(ChatFormatting.AQUA), false);

        int rank = 1;
        for (TickProfiler.Entry entry : entries) {
            int index = rank++;
            long share = total == 0L ? 0L : entry.nanos() * 100L / total;
            source.sendSuccess(() -> Component.literal(
                    String.format("%2d. %s", index, entry.id))
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(
                            "  " + millisPerTick(entry.nanos(), ticks) + " ms/tick"
                                    + " (" + share + "%)"
                                    + " x" + entry.count())
                            .withStyle(share >= 20 ? ChatFormatting.RED
                                    : share >= 5 ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
                    .append(Component.literal(
                            "  最悪 " + millis(entry.worstNanos()) + " ms @ " + entry.worstWhere())
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return entries.size();
    }

    private static String millis(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private static String millisPerTick(long nanos, long ticks) {
        return String.format("%.3f", nanos / 1_000_000.0 / ticks);
    }
}
