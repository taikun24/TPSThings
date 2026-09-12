package jp.main.taikun.tpsthings;

import jp.main.taikun.tpsthings.blockentities.BEAbsoluteLifeAnchor;
import jp.main.taikun.tpsthings.damage.AutoGuard;
import jp.main.taikun.tpsthings.damage.GuardConfig;
import jp.main.taikun.tpsthings.damage.GuardTick;
import jp.main.taikun.tpsthings.damage.AttachGuard;
import jp.main.taikun.tpsthings.damage.GuardNotice;
import jp.main.taikun.tpsthings.damage.HealthGuard;
import jp.main.taikun.tpsthings.damage.PiercingStrike;
import jp.main.taikun.tpsthings.damage.PresenceGuard;
import jp.main.taikun.tpsthings.damage.RepairGuard;
import jp.main.taikun.tpsthings.damage.RespawnGuard;
import jp.main.taikun.tpsthings.items.ItemCatTeaser;
import jp.main.taikun.tpsthings.items.ItemOo;
import jp.main.taikun.tpsthings.profile.TickProfiler;
import jp.main.taikun.tpsthings.registries.ModItems;
import jp.main.taikun.tpsthings.time.TickUtil;
import jp.main.taikun.tpsthings.time.TpsMeter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import org.spongepowered.asm.mixin.MixinEnvironment;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = Tpsthings.MODID)
public class ServerEvents {
    @SubscribeEvent
    public static void whenAttack(AttackEntityEvent event) {
        // Player#attack はクライアント側でも走る。クライアントの実体を消しても表示が狂うだけ
        if (event.getEntity().getMainHandItem().is(ModItems.OO.get()) && !event.getEntity().level().isClientSide()) {
            ItemOo.whenAttack(event.getEntity(), event.getTarget());
        }
        // 殴れる相手は生き物とは限らない (ボート・額縁・トロッコ)。生き物でなければ
        // 体力も効果も無いので、普通の殴打に任せる
        if (event.getEntity().getMainHandItem().is(ModItems.FLUORESCENT_LIGHT.get())
                && !event.getEntity().level().isClientSide()
                && event.getTarget() instanceof LivingEntity target) {
            event.cancel();
            float targetEntityHP = target.getHealth() * FLUORESCENT_HP_RATIO;
            target.hurt(event.getEntity().damageSources().playerAttack(event.getEntity()), targetEntityHP);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, FLUORESCENT_EFFECT_TICKS, 1));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FLUORESCENT_EFFECT_TICKS, 1));
            ItemStack stack = event.getEntity().getMainHandItem();
            stack.setDamageValue(stack.getDamageValue() + 1);
        }
    }

    /** 蛍光灯が削る、今の体力の割合。 */
    private static final float FLUORESCENT_HP_RATIO = 4f / 5f;
    /** 目潰しの長さ (10 分)。 */
    private static final int FLUORESCENT_EFFECT_TICKS = 12000;
    /** まぐろ 1 本で並ぶ爆発の数。 */
    private static final int TUNA_BLAST_COUNT = 10;
    /** 爆発 1 発の威力 (バニラの TNT が 4)。 */
    private static final float TUNA_BLAST_POWER = 10f;
    /** 爆発の間隔 (ブロック)。 */
    private static final double TUNA_BLAST_SPACING = 6.0;
    /** 投げてから次を投げられるまで。 */
    private static final int TUNA_COOLDOWN_TICKS = 100;

    @SubscribeEvent
    public static void whenRightClick(PlayerInteractEvent.RightClickItem event){
        if (!event.getSide().equals(LogicalSide.SERVER))return;
        if (event.getEntity().getMainHandItem().is(ModItems.TUNA.get())) {
            Player player = event.getEntity();
            ItemStack stack = player.getMainHandItem();
            // 1 回投げたら無くなる。連打で一度に何本も消えないようクールダウンも置く
            player.getCooldowns().addCooldown(ModItems.TUNA.get(), TUNA_COOLDOWN_TICKS);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            Vec3 orig = player.getEyePosition();
            Vec3 headVec = player.getViewVector(1).normalize().scale(TUNA_BLAST_SPACING);
            Level level = player.level();
            for (int i = 0; i < TUNA_BLAST_COUNT; i++) {
                Vec3 pos = orig.add(headVec.scale(i + 1));
                level.explode(
                        player,
                        pos.x,
                        pos.y,
                        pos.z,
                        TUNA_BLAST_POWER,
                        Level.ExplosionInteraction.TNT
                );
            }
        }
    }
    @SubscribeEvent
    public static void whenRightClickBlock(PlayerInteractEvent.RightClickBlock event){
        if (event.getEntity().getMainHandItem().is(ModItems.ACCELERATION_WAND.get())) {
            TickUtil.tick(
                    event.getLevel(),
                    event.getPos(),
                    10
            );
            TickUtil.randomTick(
                    event.getLevel(),
                    event.getPos(),
                    5
            );
        }
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getHitVec().getBlockPos());
        if (blockEntity instanceof BEAbsoluteLifeAnchor lifeAnchor && event.getSide() ==  LogicalSide.SERVER) {
            lifeAnchor.setAsPlayersAnchor((ServerPlayer) event.getEntity());
        }
    }
    @SubscribeEvent
    public static void whenDamaged(LivingDamageEvent event){
        event.getEntity().getArmorSlots().forEach(e -> {
            if (e.is(ModItems.OO.get())){
                event.getEntity().setHealth(Integer.MAX_VALUE);
                // event.cancel();
            }
        });
    }
    /**
     * 胸に着けたかどうかで保護を合わせる。
     *
     * 着ているのがプレイヤーかどうかは見ない。関所はどれも実体単位なので、
     * 着せた Mob も同じ防御になる (試し撃ちの的に出来る)。飛行だけはプレイヤーの
     * 持ち物なので、そこだけ分ける。
     */
    @SubscribeEvent
    public static void equipmentChange(LivingEquipmentChangeEvent event){
        if (event.getSlot() != EquipmentSlot.CHEST) {
            return;
        }
        boolean wearing = event.getTo().is(ModItems.OO.get());
        if (event.getEntity() instanceof Player player) {
            player.getAbilities().mayfly = wearing;
            player.onUpdateAbilities();
        }
        // 着ている間はダメージ源の自動追跡を回す
        AutoGuard.syncEquipProtection(event.getEntity(), wearing);
    }

    /**
     * ログイン時に装備を見て保護状態を合わせる。
     *
     * 装備変更イベントはログインでは飛ばないので、着たままログインすると
     * 保護が入らないままになる。入り口が装備変更しかないと、ここが穴になる。
     */
    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event){
        AutoGuard.syncEquipProtection(event.getEntity(), ItemOo.isWorn(event.getEntity()));
    }

    /** リスポーンや次元移動でも実体が作り直されるので、そのつど合わせ直す。 */
    @SubscribeEvent
    public static void playerRespawn(PlayerEvent.PlayerRespawnEvent event){
        AutoGuard.syncEquipProtection(event.getEntity(), ItemOo.isWorn(event.getEntity()));
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event){
        AutoGuard.syncEquipProtection(event.getEntity(), ItemOo.isWorn(event.getEntity()));
    }

    /** 居ないプレイヤーを保護対象に残すと、観測状態が溜まったまま消えなくなる。 */
    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event){
        AutoGuard.syncEquipProtection(event.getEntity(), false);
        RespawnGuard.forget(event.getEntity().getUUID());
    }

    /** 前回止めた署名を戻す。コマンドが使えるようになる前に済ませておく。 */
    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event){
        GuardConfig.load();
        // 復帰直後の装備側の後始末 (飛行の復元)。Guard は装備の仕様を知らないので、
        // こちらから渡す
        RespawnGuard.setReviveHook(player -> restoreFlight(player, true));
        // 外部の自己アタッチ agent は起動最早期に仕込まれる。コマンドを待たず、
        // 気づいた足跡はここで一度ログに残しておく (止められはしないが、後で
        // 原因不明の死を追うときの手がかりになる)
        List<String> agentTraces = new java.util.ArrayList<>(AttachGuard.scan());
        // 深掘り (敵 Instrumentation の確保) は自前 agent が要るが、起動時は
        // ゲームを触りたくないので自己アタッチはしない。既に確保済みなら拾う
        agentTraces.addAll(AttachGuard.scanInstrumentation(false));
        if (!agentTraces.isEmpty()) {
            GuardNotice.warn("外部 Java Agent の疑いがある痕跡を " + agentTraces.size()
                    + " 件検出しました (/" + Tpsthings.MODID + " damage agentscan で詳細):");
            agentTraces.forEach(trace -> GuardNotice.warn("  " + trace));
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event){
        GuardConfig.save();
        HealthGuard.reset();
        GuardNotice.reset();
        PresenceGuard.reset();
        RepairGuard.reset();
        RespawnGuard.reset();
        ItemCatTeaser.clear();
        PiercingStrike.reset();
        GuardTick.reset();
        TpsMeter.reset();
    }

    /**
     * ゲームモードを切り替えても飛行を取り上げない。
     *
     * クリエイティブから出ると mayfly と flying が問答無用で落とされる。
     * イベントは切り替えの<b>前</b>に飛ぶので、落とされたあとで戻す。
     */
    @SubscribeEvent
    public static void changeGameMode(PlayerEvent.PlayerChangeGameModeEvent event){
        if (!(event.getEntity() instanceof ServerPlayer player) || !ItemOo.isWorn(player)) {
            return;
        }
        boolean wasFlying = player.getAbilities().flying;
        boolean toSpectator = event.getNewGameMode() == GameType.SPECTATOR;
        player.server.execute(() -> restoreFlight(player, wasFlying && !toSpectator));
    }

    /** OO を着ている間は飛べる状態に戻す。変わっていなければ何もしない。 */
    private static void restoreFlight(ServerPlayer player, boolean resumeFlying) {
        if (!ItemOo.isWorn(player)) {
            return;
        }
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            changed = true;
        }
        if (resumeFlying && !abilities.flying) {
            abilities.flying = true;
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    /**
     * 保護対象は死んでも持ち物を引き継ぐ。
     *
     * 即時リスポーンで OO が外れると追跡が止まってしまうので、
     * keepInventory 相当を装備由来で成立させる。落とす側の抑止は
     * {@code MixinPlayerKeepInventory} が受け持つ。
     */
    @SubscribeEvent
    public static void keepInventory(PlayerEvent.Clone event){
        if (!event.isWasDeath()) return;
        Player original = event.getOriginal();
        if (!ItemOo.isWorn(original)) return;

        original.reviveCaps();
        event.getEntity().getInventory().replaceWith(original.getInventory());
        original.invalidateCaps();
    }
    @SubscribeEvent
    public static void serverTickEvent(TickEvent.ServerTickEvent  event){
        // ServerTickEvent は START と END の 2 回飛ぶ。END だけで回す
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        TpsMeter.onServerTick();
        TickProfiler.onServerTick();
        ItemCatTeaser.tick(event.getServer());
        // 見回りの中身は GuardTick に寄せた。イベントバスごと差し替えて
        // tick のイベントを捨てる相手が居るので、サーバの tick に刺した関所からも
        // 同じものを回す (同じ tick で二度は走らない)
        GuardTick.run(event.getServer());

        event.getServer().getPlayerList().getPlayers().forEach(player -> {
            player.getArmorSlots().forEach(e -> {
                if (e.is(ModItems.OO.get())){
                    player.clearFire();
                    // 飛行を落とす経路はゲームモード切り替えだけではない。
                    // 誰が落としたかを追うより、着ている間は毎 tick 戻す方が確実
                    restoreFlight(player, false);
                    // 満腹度は常時全回復。走り・回復で減る前に毎 tick 埋め直す
                    FoodData food = player.getFoodData();
                    food.setFoodLevel(20);
                    food.setSaturation(20.0F);
                }
            });
        });
    }
    /**
     * 合議層 (Forge イベント) の死の関所。判断は {@link HealthGuard} に寄せる。
     *
     * 以前はここで「おぉを着ているか」を直接見ていたが、保護対象の判定は
     * Guard 側に 1 つだけにする (装備由来の保護は syncEquipProtection が同期済み)。
     */
    @SubscribeEvent
    public static void whenDeath(LivingDeathEvent event){
        if (HealthGuard.shouldCancelDeathEvent(event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().setHealth(event.getEntity().getMaxHealth());
        }
    }
}
