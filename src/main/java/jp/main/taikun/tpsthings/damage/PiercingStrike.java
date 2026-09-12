package jp.main.taikun.tpsthings.damage;

import com.mojang.logging.LogUtils;
import jp.main.taikun.tpsthings.mixin.AccessorEntity;
import jp.main.taikun.tpsthings.mixin.AccessorLivingEntity;
import jp.main.taikun.tpsthings.mixin.AccessorPersistentEntitySectionManager;
import jp.main.taikun.tpsthings.mixin.AccessorServerLevel;
import jp.main.taikun.tpsthings.network.ModNetwork;
import jp.main.taikun.tpsthings.network.PacketStrikeEffect;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 貫通攻撃。L4 から L10 までを浅い順に通し、<b>結果を状態で確かめてから</b>一段下へ降りる。
 *
 * <p>{@link PresenceGuard} の裏返し。守る側が「経路ではなく状態を守る」なら、
 * 殺す側も「経路ではなく状態を確かめる」。{@code hurt} が通ったかどうかは
 * 戻り値でも {@code getHealth()} でもなく同期データの生値で見るし、除去が通ったかは
 * {@code isRemoved()} だけでなく索引に残っているかで見る。相手がどの層で耐えたかを
 * 知らなくても、耐えた層の次から続きを打てる。
 *
 * <p>浅い層で片が付く相手には浅い層しか使わない。バニラの Mob なら {@code hurt} →
 * {@code remove} の正規の道だけで終わり、ドロップも経験値も普段どおり出る。
 * 深い層 (HP の箱への直書き・索引の直接操作) は、正規の道を拒否した相手にだけ使う。
 *
 * <p>判定に {@code getHealth()} / {@code isAlive()} は使わない (L7)。読み出しを
 * 偽装する相手には、偽装された答えを<b>読まない</b>ことで貫通する。
 *
 * <p>特定の Mod を名指しする分岐は持たない。
 */
public final class PiercingStrike {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 打ったあと見張り続ける長さ。
     *
     * 自己修復する相手は次の tick に索引へ戻ってくる。1 回で終わりにすると、
     * 打った瞬間だけ消えて見えるだけになる。ずっと追うと UUID の永久追放になるので区切る。
     */
    private static final int PURSUIT_TICKS = 100;

    /**
     * 正規の道で死んだ相手が倒れ終わるのを待つ長さ。バニラは死んでから 20 tick で自分を除去する。
     * ラグで数 tick 遅れても深い層に落ちないよう、少し余裕を持たせる。
     */
    private static final int DEATH_GRACE_TICKS = 30;

    /** 層ごとの結果に使う印。0 は対象外、{@link #FAILED} は最深でも通らなかった。 */
    public static final int FAILED = 11;

    /** 演出を送る範囲 (ブロック)。 */
    private static final double EFFECT_RANGE = 64.0;

    /** 追撃中の相手。サーバスレッドからしか触らない。 */
    private static final Map<UUID, Pursuit> PURSUITS = new HashMap<>();

    /**
     * プレイヤーにも L9/L10 (除去・索引) まで打つか。既定は死 (L8) まで。
     *
     * <p>世界から剥がされたプレイヤーは通信路だけが残る。死亡処理が通っていれば
     * リスポーンで正規に作り直されるが、死まで拒否した相手は再接続するまで動けなくなる。
     */
    private static volatile boolean playersFullDepth = false;

    private static final class Pursuit {
        /** 索引から外すと世界から引けなくなるので強参照で持つ。期限で必ず手放す。 */
        private final LivingEntity body;
        @Nullable
        private final UUID attacker;
        private int ticksLeft = PURSUIT_TICKS;
        /** 倒れ終わるまでの残り。0 なら待っていない */
        private int graceTicks;
        /** 打ったときの結果。倒れている最中にもう一度打たれたら、打ち直さずにこれを返す */
        private Result result = new Result(0, 0, 0);

        private Pursuit(LivingEntity body, @Nullable UUID attacker) {
            this.body = body;
            this.attacker = attacker;
        }
    }

    /**
     * 1 体分の結果。それぞれ「どの層で通ったか」。
     *
     * @param health  HP が 0 になった層 (5 = hurt / 6 = 同期データへの直書き)
     * @param death   死亡処理が済んだ層 (5 = hurt の中で / 8 = die の直呼び)
     * @param removal 世界から消えた層 (9 = 除去 / 10 = 索引の直接操作)。
     *                プレイヤーは {@link #setPlayersFullDepth} が切れていれば 0
     */
    public record Result(int health, int death, int removal) {

        /** 抵抗の強さ。表示する 1 体を選ぶのに使う。 */
        public int resistance() {
            return health + death + removal;
        }

        @Override
        public String toString() {
            String text = "HP " + label(health) + " · 死 " + label(death);
            return removal == 0 ? text : text + " · 消 " + label(removal);
        }

        public static String label(int layer) {
            if (layer == 0) return "-";
            if (layer == FAILED) return "×";
            return "L" + layer;
        }
    }

    private PiercingStrike() {
    }

    /** 打って、しばらく追う。 */
    public static Result strike(@Nullable Player attacker, LivingEntity target) {
        if (target.level().isClientSide()) {
            return pierce(attacker, target, null);
        }
        Pursuit current = PURSUITS.get(target.getUUID());
        if (current != null && current.body == target && current.graceTicks > 0) {
            // 倒れている最中の相手は打ち直さない。振るたびに粒と報告が出直すだけで、待ち時間も巻き戻る
            return current.result;
        }
        Pursuit pursuit = new Pursuit(target, attacker == null ? null : attacker.getUUID());
        pursuit.result = pierce(attacker, target, pursuit);
        PURSUITS.put(target.getUUID(), pursuit);
        return pursuit.result;
    }

    /**
     * @param grace 渡されていて、相手が正規の道で死んだなら、除去せずに倒れ終わるのを待たせる。
     *              追撃で打ち直すときは null (もう待たない)
     */
    private static Result pierce(@Nullable Player attacker, LivingEntity target, @Nullable Pursuit grace) {
        if (!(target.level() instanceof ServerLevel level)) {
            return new Result(0, 0, 0);
        }
        EntityDataAccessor<Float> healthKey = AccessorLivingEntity.tpsthings$healthId();
        // 剥がれ落ちる数字として見せる、打つ前の体力
        float before = raw(target, healthKey);

        // ---- L0〜L3: 無敵の類を外す。ここは拒否されても次の層で結果が出るので確かめない
        attempt(() -> {
            target.setInvulnerable(false);
            target.invulnerableTime = 0;
            if (attacker != null) {
                // ドロップと経験値を持ち主に付ける
                target.setLastHurtByPlayer(attacker);
            }
        });

        // ---- L4/L5: 正規の道。無敵貫通のダメージ種で最大値
        attempt(() -> target.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
        int health = raw(target, healthKey) <= 0.0F ? 5 : 0;
        int death = dead(target) ? 5 : 0;

        // ---- L6: HP の入れ物へ。入口 (set) → 箱 (DataItem) の順
        if (health == 0) {
            attempt(() -> target.getEntityData().set(healthKey, 0.0F, true));
            if (raw(target, healthKey) > 0.0F) {
                attempt(() -> writeItem(target, healthKey, 0.0F));
            }
            health = raw(target, healthKey) <= 0.0F ? 6 : FAILED;
        }

        // ---- L8: 死亡処理を直接。HP を見て死を判断しない相手にも死を通す
        if (death == 0) {
            DamageSource source = attacker != null
                    ? level.damageSources().playerAttack(attacker)
                    : level.damageSources().genericKill();
            attempt(() -> target.die(source));
            death = dead(target) ? 8 : FAILED;
        }

        // ---- 正規の道で死んだ相手は、倒れ終わるまで待つ。
        // ここで除去すると倒れる姿も煙も出ずに消え、深い層で消した相手と見分けがつかなくなる。
        // 報告の「消」は、倒れ終わりの remove (L9) として出す。消えなければ追撃が L9 から続きを打つ
        if (grace != null && health == 5 && death == 5 && !(target instanceof Player)) {
            grace.graceTicks = DEATH_GRACE_TICKS;
            return announce(level, target, before, new Result(health, death, 9));
        }

        // プレイヤーを世界から剥がすと通信路だけが残る。既定では死までで止める
        if (target instanceof Player && !playersFullDepth) {
            return announce(level, target, before, new Result(health, death, 0));
        }

        // ---- L9: 除去。remove の上書きを飛ばすために setRemoved も直接叩く
        int removal = 0;
        attempt(() -> target.remove(Entity.RemovalReason.KILLED));
        if (!target.isRemoved()) {
            attempt(() -> target.setRemoved(Entity.RemovalReason.KILLED));
        }
        if (!target.isRemoved()) {
            // 入口ごと塞がれていても、印そのものは立てられる。索引は下の層で外す
            attempt(() -> ((AccessorEntity) target).tpsthings$setRemovalReason(Entity.RemovalReason.KILLED));
        }
        if (target.isRemoved() && !present(level, target)) {
            removal = 9;
        }

        // ---- L10: 索引。除去の印が立っても、索引に残っていれば世界はまだ数えている
        if (removal == 0) {
            attempt(() -> erase(level, target));
            removal = present(level, target) ? FAILED : 10;
        }

        return announce(level, target, before, new Result(health, death, removal));
    }

    /**
     * 周りに、どの層で通ったかを見せる。演出の中身はクライアントの StrikeEffects が結果の深さだけで決める。
     * 相手は索引から外れて手元でも消えているので、位置と大きさを添えて送る。
     */
    private static Result announce(ServerLevel level, LivingEntity target, float before, Result result) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(0.5), target.getZ(),
                24, target.getBbWidth() * 0.5, target.getBbHeight() * 0.5, target.getBbWidth() * 0.5, 0.05);
        ModNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(
                        target.getX(), target.getY(), target.getZ(), EFFECT_RANGE, level.dimension())),
                new PacketStrikeEffect(target.getX(), target.getY(), target.getZ(),
                        target.getBbWidth(), target.getBbHeight(), before,
                        result.health(), result.death(), result.removal()));
        return result;
    }

    /**
     * サーバ tick の終わりに、打った相手が戻ってきていないかを見る。
     *
     * 戻ってくる相手の tick は索引から外れた時点で止まっているので、こちらから回す。
     */
    public static void tick(MinecraftServer server) {
        if (PURSUITS.isEmpty()) {
            return;
        }
        Iterator<Pursuit> pursuits = PURSUITS.values().iterator();
        while (pursuits.hasNext()) {
            Pursuit pursuit = pursuits.next();
            if (--pursuit.ticksLeft <= 0 || !(pursuit.body.level() instanceof ServerLevel level)) {
                pursuits.remove();
                continue;
            }
            Player attacker = pursuit.attacker == null ? null : server.getPlayerList().getPlayer(pursuit.attacker);
            LivingEntity body = pursuit.body;
            if (pursuit.graceTicks > 0) {
                boolean fallen = raw(body, AccessorLivingEntity.tpsthings$healthId()) <= 0.0F && dead(body);
                if (body.isRemoved() && !present(level, body)) {
                    // 倒れ終わって正規に消えた。ここからは戻ってこないかだけを見る
                    pursuit.graceTicks = 0;
                } else if (fallen && --pursuit.graceTicks > 0) {
                    continue;
                } else {
                    // 倒れている途中で起き上がったか、待っても消えなかった。もう待たずに下の層まで打つ
                    pursuit.graceTicks = 0;
                    pierce(attacker, body, null);
                    continue;
                }
            }
            if (revived(level, body)) {
                pierce(attacker, body, null);
            }
            // 同じ UUID で作り直してくる相手。プレイヤーのリスポーンは正規なので追わない
            if (!(body instanceof Player)
                    && level.getEntity(body.getUUID()) instanceof LivingEntity twin && twin != body) {
                pierce(attacker, twin, null);
            }
        }
    }

    /** サーバをまたいで持ち越さない。強参照なので、残すと世界ごと GC できなくなる。 */
    public static void reset() {
        PURSUITS.clear();
    }

    public static boolean isPlayersFullDepth() {
        return playersFullDepth;
    }

    public static void setPlayersFullDepth(boolean value) {
        playersFullDepth = value;
    }

    // ---- 状態の確認 -------------------------------------------------------------

    private static boolean revived(ServerLevel level, LivingEntity body) {
        if (body instanceof ServerPlayer player) {
            // リスポーン後の古い実体は死んだままでよい
            if (PresenceGuard.stale(player)) {
                return false;
            }
            if (playersFullDepth) {
                return !player.isRemoved() || present(level, player);
            }
            return !player.isRemoved() && raw(player, AccessorLivingEntity.tpsthings$healthId()) > 0.0F;
        }
        return !body.isRemoved() || present(level, body);
    }

    /** 同期データの生値。{@code getHealth()} は偽装されうるので読まない。 */
    private static float raw(LivingEntity target, EntityDataAccessor<Float> key) {
        return target.getEntityData().get(key);
    }

    private static boolean dead(LivingEntity target) {
        return ((AccessorLivingEntity) target).tpsthings$dead();
    }

    /** 世界がまだこの実体を数えているか。UUID 索引・tick 一覧・区画のどれか 1 つでも残っていれば居る。 */
    private static boolean present(ServerLevel level, LivingEntity target) {
        if (level.getEntity(target.getUUID()) == target) {
            return true;
        }
        if (((AccessorServerLevel) level).tpsthings$entityTickList().contains(target)) {
            return true;
        }
        EntitySection<EntityAccess> section = sections(level).getSection(SectionPos.asLong(target.blockPosition()));
        return section != null && section.getEntities().anyMatch(entity -> entity == target);
    }

    // ---- 深い層の道具 -----------------------------------------------------------

    /** 入口 ({@code set}) を通らず、値が載っている箱に直接書く。 */
    @SuppressWarnings("unchecked")
    private static void writeItem(LivingEntity target, EntityDataAccessor<Float> key, float value) {
        Map<?, ?> items = StateProbe.itemsOf(target.getEntityData());
        if (items != null && items.get(key.getId()) instanceof SynchedEntityData.DataItem<?> item) {
            ((SynchedEntityData.DataItem<Float>) item).setValue(value);
            item.setDirty(true);
        }
    }

    /**
     * 世界の索引から直接外す。{@link PresenceGuard#restore} が載せ直す場所を、同じだけ外す。
     *
     * 正規の除去 ({@code levelCallback.onRemove}) が通らなかった相手にだけ使う。
     */
    private static void erase(ServerLevel level, LivingEntity target) {
        AccessorServerLevel accessor = (AccessorServerLevel) level;
        PersistentEntitySectionManager<Entity> manager = accessor.tpsthings$entityManager();
        AccessorPersistentEntitySectionManager managerAccessor = (AccessorPersistentEntitySectionManager) (Object) manager;

        accessor.tpsthings$entityTickList().remove(target);
        managerAccessor.tpsthings$visibleEntityStorage().remove(target);

        EntitySectionStorage<EntityAccess> sections = managerAccessor.tpsthings$sectionStorage();
        EntitySection<EntityAccess> section = sections.getSection(SectionPos.asLong(target.blockPosition()));
        if (section == null || !section.remove(target)) {
            // 位置の通知を飛ばして動かされていると、載っている区画と座標がずれる。同じチャンクを舐める
            sections.getExistingSectionsInChunk(ChunkPos.asLong(target.blockPosition()))
                    .forEach(candidate -> candidate.remove(target));
        }
        managerAccessor.tpsthings$knownUuids().remove(target.getUUID());
        if (target instanceof Mob mob) {
            accessor.tpsthings$navigatingMobs().remove(mob);
        }

        // 追跡を切る。追跡側が既に外されていても、見ている全員に消去を送り直す
        level.getChunkSource().removeEntity(target);
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(target.getId());
        for (ServerPlayer viewer : level.players()) {
            // 本人に自分の消去を送ると、クライアントが自分の実体を失って固まる。本人には送らない
            if (viewer != target) {
                viewer.connection.send(packet);
            }
        }
        if (target instanceof ServerPlayer player) {
            // players() は索引とは別に持っている一覧。範囲検索や睡眠判定がここを見る
            level.players().remove(player);
            level.updateSleepingPlayerList();
        }

        // 後から自分で索引に触りに来ても、もう繋がっていない
        target.setLevelCallback(EntityInLevelCallback.NULL);
        target.onRemovedFromWorld();
    }

    private static EntitySectionStorage<EntityAccess> sections(ServerLevel level) {
        PersistentEntitySectionManager<Entity> manager = ((AccessorServerLevel) level).tpsthings$entityManager();
        return ((AccessorPersistentEntitySectionManager) (Object) manager).tpsthings$sectionStorage();
    }

    /**
     * 1 つの層を試す。相手の Mod がその層で例外を投げてきても、次の層は打つ。
     * 投げること自体が拒否のやり方の 1 つなので。
     */
    private static void attempt(Runnable layer) {
        try {
            layer.run();
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("貫通攻撃の層が例外で拒否されました", e);
        }
    }
}
