package jp.main.taikun.tpsthings.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 振ると、見えている Mob が全部こっちに寄ってきてじゃれつく。
 *
 * {@link ItemOo} の視野角キルがまとめて消す道具なら、こちらはまとめて集める道具。
 * 集めてから振る、という順番が成立する。
 *
 * 寄っている間は敵対対象を忘れる。クリーパーもゾンビも、目の前で揺れているものの方が気になる。
 */
public class ItemCatTeaser extends Item {

    /** 届く距離。{@link ItemOo} ほど広げない。おもちゃなので。 */
    private static final double RANGE = 24.0;
    /** 気を引いていられる長さ。 */
    private static final int CHARM_TICKS = 200;
    private static final int COOLDOWN_TICKS = 30;
    /** 寄ってくる速さ。1.0 で通常の移動速度。 */
    private static final double SPEED = 1.35;
    /** これより近づいたら、追うのをやめてその場でじゃれる。 */
    private static final double CLOSE_ENOUGH = 2.5;
    /** じゃれて跳ねる確率の分母。小さいほど落ち着きがない。 */
    private static final int JUMP_CHANCE = 10;
    private static final double JUMP_POWER = 0.42;

    /** 気を引かれている Mob 1 体分。 */
    private static final class Charm {
        private final UUID owner;
        private final ResourceKey<Level> dimension;
        private int ticksLeft;

        private Charm(UUID owner, ResourceKey<Level> dimension, int ticksLeft) {
            this.owner = owner;
            this.dimension = dimension;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Mob の UUID → 気の引かれ具合。振った分しか入らないので、毎 tick 舐めても軽い。 */
    private static final Map<UUID, Charm> CHARMED = new HashMap<>();

    public ItemCatTeaser() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public @NotNull Component getDescription() {
        return Component.translatable("item.tpsthings.cat_teaser.description");
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        List<Mob> mobs = ViewCone.mobsInView(player, RANGE);
        for (Mob mob : mobs) {
            CHARMED.put(mob.getUUID(), new Charm(player.getUUID(), level.dimension(), CHARM_TICKS));
        }

        level.playSound(null, player.blockPosition(), SoundEvents.CAT_PURREOW,
                SoundSource.PLAYERS, 0.7F, 1.0F + (player.getRandom().nextFloat() - 0.5F) * 0.3F);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        player.sendSystemMessage((mobs.isEmpty()
                        ? Component.translatable("message.tpsthings.cat_teaser.none")
                        : Component.translatable("message.tpsthings.cat_teaser.charmed", mobs.size()))
                .withStyle(mobs.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE));
        return InteractionResultHolder.success(stack);
    }

    /**
     * サーバ tick ごとに、気を引かれている Mob を持ち主の方へ寄せ続ける。
     *
     * 1 回寄せただけだと、相手の AI が次の tick で元の用事に戻ってしまう。
     * 気を引き続けるには、こちらも振り続ける必要がある。
     */
    public static void tick(MinecraftServer server) {
        if (CHARMED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Charm>> charms = CHARMED.entrySet().iterator();
        while (charms.hasNext()) {
            Map.Entry<UUID, Charm> entry = charms.next();
            Charm charm = entry.getValue();
            if (--charm.ticksLeft <= 0) {
                charms.remove();
                continue;
            }

            ServerLevel level = server.getLevel(charm.dimension);
            Entity found = level == null ? null : level.getEntity(entry.getKey());
            ServerPlayer owner = server.getPlayerList().getPlayer(charm.owner);
            if (!(found instanceof Mob mob) || !mob.isAlive() || owner == null || owner.level() != level) {
                charms.remove();
                continue;
            }
            follow(mob, owner);
        }
    }

    private static void follow(Mob mob, ServerPlayer owner) {
        mob.getLookControl().setLookAt(owner, 60.0F, 60.0F);
        // 揺れているものの方が気になるので、狙っていた相手のことは忘れる
        mob.setTarget(null);

        if (mob.distanceToSqr(owner) > CLOSE_ENOUGH * CLOSE_ENOUGH) {
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(owner, SPEED);
            }
            return;
        }
        mob.getNavigation().stop();
        if (mob.onGround() && mob.getRandom().nextInt(JUMP_CHANCE) == 0) {
            mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, JUMP_POWER, 0.0));
            mob.hasImpulse = true;
        }
    }

    /** サーバをまたいで持ち越さない。 */
    public static void clear() {
        CHARMED.clear();
    }
}
