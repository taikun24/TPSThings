package jp.main.taikun.tpsthings.damage;

import com.mojang.logging.LogUtils;
import jp.main.taikun.tpsthings.mixin.AccessorEntity;
import jp.main.taikun.tpsthings.mixin.AccessorEntityLookup;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 貫通攻撃。挙動層から索引層までを浅い順に通し、<b>結果を状態で確かめてから</b>一段下へ降りる。
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
 * <p>判定に {@code getHealth()} / {@code isAlive()} は使わない (虚偽層)。読み出しを
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
     * 戻ってくるたびに見張りを延ばす上限。
     *
     * 守る側の見張りは相手が居る限り続く。こちらが一定時間で諦めると、それより後に
     * 載せ直す相手には必ず負ける。延ばしはするが、UUID の永久追放にはしない。
     */
    private static final int MAX_PURSUIT_TICKS = 1200;

    /**
     * 正規の道で死んだ相手が倒れ終わるのを待つ長さ。バニラは死んでから 20 tick で自分を除去する。
     * ラグで数 tick 遅れても深い層に落ちないよう、少し余裕を持たせる。
     */
    private static final int DEATH_GRACE_TICKS = 30;

    /** 層ごとの結果に使う印。0 は対象外、{@link #FAILED} は最深でも通らなかった。 */
    public static final int FAILED = 11;

    /** 演出を送る範囲 (ブロック)。 */
    private static final double EFFECT_RANGE = 64.0;

    /**
     * 装備外しを打ち直す間隔。
     *
     * どうやっても倒せない相手だと、拒否が続く限り毎 tick 装備を外して戻すことになり、
     * ログも処理も無駄に膨らむ (実測で 1 回の検証に 649 回)。倒せる相手なら一度で片が付くので、
     * 間隔を空けても取り逃がさない。倒せない相手には空回りの回数だけ減らす。
     */
    private static final int DISARM_INTERVAL = 20;

    /** 追撃中の相手。サーバスレッドからしか触らない。 */
    private static final Map<UUID, Pursuit> PURSUITS = new HashMap<>();

    /**
     * 世界から消したあと、正規のリスポーンで作り直すのを待っているプレイヤー。
     *
     * <p>打った<b>その場</b>では作り直せない。攻撃はプレイヤーの tick の最中に走っていて、
     * そこで {@code PlayerList#respawn} を呼ぶと反復中に世界から出し入れすることになり、
     * 同じ UUID が二重に並ぶ。サーバ tick の終わりまで持ち越す。
     */
    private static final Set<UUID> AWAITING_RESPAWN = new LinkedHashSet<>();

    /**
     * プレイヤーにも抹消層・索引層 (除去・索引) まで打つか。既定は終焉層 (死) まで。
     *
     * <p>世界から剥がされたプレイヤーは通信路だけが残る。死亡処理が通っていれば
     * リスポーンで正規に作り直されるが、死まで拒否した相手は再接続するまで動けなくなる。
     */
    private static volatile boolean playersFullDepth = false;

    /**
     * 消しきれなかったときに、剥がした索引を戻すか。既定は戻さない。
     *
     * <p>層は浅い順に降りるので、除去まで通らなかった相手には<b>途中まで効いた跡</b>が残る。
     * tick 一覧と UUID 索引からは外れたのに区画には残る、という状態になると、相手は
     * 動けて画面も開けるのに、世界の tick が担う仕事 (採掘の進み・入れ物の同期) だけが止まる。
     * これを「無力化できた」と見るか「中途半端に壊した」と見るかは使う側の判断なので、切り替えにする。
     */
    private static volatile boolean restoreOnFailure = false;

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
        /** 延ばしも含めた見張りの総量の残り */
        private int budget = MAX_PURSUIT_TICKS;
        /** 相手を守っていた名簿から外したもの。追撃が終わるときに、相手が生き残っていれば戻す */
        private final List<StrikeRosters.Taken> taken = new ArrayList<>();
        /** 実体に載っていた不死のスイッチのうち倒したもの。生き残っていれば戻す */
        private final List<StrikeState.Change> stateChanges = new ArrayList<>();
        /** 装備外しを次に打てるまでの残り tick。空回りを間引く */
        private int disarmCooldown = 0;
        /** 抵抗の材料 (名簿・殻) を診断に出したか。追撃 1 回につき一度だけ */
        private boolean diagnosed = false;
        /** 不死のスイッチを倒したことを報せたか。追撃 1 回につき一度だけ */
        private boolean stateNoted = false;
        /** 相手の門を開けて通したことを報せたか。追撃 1 回につき一度だけ */
        private boolean gateNoted = false;
        /** 手こずった末に倒しきれたことを報せたか。追撃 1 回につき一度だけ */
        private boolean wonNoted = false;

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
            return pierce(attacker, target, null, false);
        }
        Pursuit current = PURSUITS.get(target.getUUID());
        if (current != null && current.body == target && current.graceTicks > 0) {
            // 倒れている最中の相手は打ち直さない。振るたびに粒と報告が出直すだけで、待ち時間も巻き戻る
            return current.result;
        }
        Pursuit pursuit = new Pursuit(target, attacker == null ? null : attacker.getUUID());
        if (current != null) {
            // 追い直しでも、外した名簿の記録は引き継ぐ。捨てると戻す機会が無くなる
            pursuit.taken.addAll(current.taken);
        }
        pursuit.result = pierce(attacker, target, pursuit, true);
        PURSUITS.put(target.getUUID(), pursuit);
        note(target, pursuit.result);
        // 消しきれたプレイヤーは、通信路だけが残って画面が壊れる。tick の終わりに作り直す
        if (target instanceof ServerPlayer victim && !refused(pursuit.result)) {
            AWAITING_RESPAWN.add(victim.getUUID());
        }
        return pursuit.result;
    }

    /**
     * 全層を通し、正規の道を拒否されたら<b>守りの材料を抜いてからもう一度</b>通す。
     *
     * @param pursuit    外した名簿を持っておく追撃。無ければ名簿には触らない (戻せないので)
     * @param allowGrace 相手が正規の道で死んだなら、除去せずに倒れ終わるのを待たせるか。
     *                   追撃で打ち直すときは false (もう待たない)
     */
    private static Result pierce(@Nullable Player attacker, LivingEntity target,
                                 @Nullable Pursuit pursuit, boolean allowGrace) {
        if (!(target.level() instanceof ServerLevel level)) {
            return new Result(0, 0, 0);
        }
        EntityDataAccessor<Float> healthKey = AccessorLivingEntity.tpsthings$healthId();
        // 剥がれ落ちる数字として見せる、打つ前の体力
        float before = raw(target, healthKey);
        Pursuit grace = allowGrace ? pursuit : null;
        Result result = layers(attacker, target, level, healthKey, grace);

        // 正規の道を拒否された。関所の注入も読み出しの書き換えも、判断の材料は「相手を名指しした
        // 名簿」であることが多い。注入と取り合うより、材料を抜いて正規の道を開け直す
        if (pursuit != null && refused(result)) {
            // 1. 実体の外の名簿 (UUID を名指しした static な集合) から外す
            List<StrikeRosters.Taken> taken = StrikeRosters.strip(target, pursuit.taken);
            if (!taken.isEmpty()) {
                pursuit.taken.addAll(taken);
                GuardNotice.info("貫通攻撃: " + target.getName().getString()
                        + " を名指しで守っていた名簿から外しました (追撃の間だけ): "
                        + String.join(", ", taken.stream().map(StrikeRosters.Taken::label).distinct().toList()));
                result = layers(attacker, target, level, healthKey, grace);
            }
            // 2. 実体に混ぜ込まれた不死のスイッチ (外来の同期真偽値・注入された真偽値) を倒す
            if (refused(result)) {
                List<StrikeState.Change> changes = StrikeState.neutralize(target);
                if (!changes.isEmpty()) {
                    pursuit.stateChanges.addAll(changes);
                    // 毎 tick 立て直してくる相手だとここも毎 tick 通る (実測で 1 回の検証に 940 行)。
                    // 倒したこと自体は追撃 1 回につき一度伝われば足りる
                    if (!pursuit.stateNoted) {
                        pursuit.stateNoted = true;
                        GuardNotice.info("貫通攻撃: " + target.getName().getString()
                                + " の実体に載っていた不死のスイッチを " + changes.size() + " 個倒しました (追撃の間だけ)");
                    }
                    result = layers(attacker, target, level, healthKey, grace);
                }
            }
            // 3. 相手が<b>自分の関所を自分で通り抜けるために</b>置いている門を、その場だけ開けて打つ。
            // 力ずくで入れ物を抜くのと違い、相手の後始末も相手自身の手で正しく走るので、
            // 中途半端に壊れた状態が残らない。開けた門は通った通らないに関わらず必ず閉じる
            if (refused(result)) {
                List<StrikeSwitches.Raised> raised = StrikeSwitches.raise();
                try {
                    if (!raised.isEmpty()) {
                        result = layers(attacker, target, level, healthKey, grace);
                    }
                } finally {
                    StrikeSwitches.lower(raised);
                }
                // 効いたかどうかに関わらず一度は出す。成功時だけ出すと「門が無かった」のか
                // 「開けたが別の理由で耐えた」のかが外から区別できない
                if (!pursuit.gateNoted) {
                    pursuit.gateNoted = true;
                    GuardNotice.info("貫通攻撃: " + target.getName().getString()
                            + " の門: 候補 " + StrikeSwitches.candidateCount() + " 個 / 開けた " + raised.size() + " 個"
                            + (raised.isEmpty() ? "" : " (" + String.join(", ", raised.stream()
                                    .map(StrikeSwitches.Raised::label).limit(6).toList()) + ")")
                            + " → " + (refused(result) ? "まだ耐えています" : "通りました"));
                }
            }
            // まだ耐えているなら、材料 (名指しの名簿・同期データの殻) を一度だけ診断に出す。
            // 「見えているのに外せない名簿」があるのか、そもそも材料が見えていないのかを分ける
            if (refused(result) && !pursuit.diagnosed) {
                pursuit.diagnosed = true;
                diagnoseResistance(level, target);
            }
            // 3. 装備由来の不死は、その装備を外している間だけ消える。層を通す間だけ外して必ず戻す。
            // 倒せない相手だと拒否が続く限り毎 tick 空回りするので、間隔を空ける
            if (refused(result) && hasEquipment(target) && pursuit.disarmCooldown <= 0) {
                pursuit.disarmCooldown = DISARM_INTERVAL;
                result = layersDisarmed(attacker, target, level, healthKey, grace);
            }
            // 手こずった末に通ったなら、通ったと言う。
            //
            // ここまでの報告は<b>耐えられたとき</b>にしか出ない作りだった。つまり倒しきれた
            // 追撃はログに何も残さず、「一度も打っていない」場合と区別がつかない
            // (実測で何度も、0 行を見て成否を判断できなかった)。抵抗した相手を倒した事実は、
            // 耐えられた事実と同じだけ知りたい。正規の道で素直に死ぬ相手は今までどおり黙る
            if (!refused(result) && !pursuit.wonNoted) {
                pursuit.wonNoted = true;
                GuardNotice.info("貫通攻撃: " + target.getName().getString()
                        + " を倒しきりました (" + result + ")");
            }
        }

        return announce(level, target, before, result);
    }

    /** 装備を外した状態で層を通す。装備由来の判定 (防具一式など) はここで無効になる。必ず戻す。 */
    private static Result layersDisarmed(@Nullable Player attacker, LivingEntity target, ServerLevel level,
                                         EntityDataAccessor<Float> healthKey, @Nullable Pursuit grace) {
        Map<EquipmentSlot, ItemStack> saved = new java.util.EnumMap<>(EquipmentSlot.class);
        try {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack worn = target.getItemBySlot(slot);
                if (!worn.isEmpty()) {
                    saved.put(slot, worn);
                    DamageGuard.runAsSelf(() -> target.setItemSlot(slot, ItemStack.EMPTY));
                }
            }
            if (!saved.isEmpty()) {
                GuardNotice.info("貫通攻撃: " + target.getName().getString()
                        + " の装備を外して打ちました (装備一式で無敵になる相手向け・すぐ戻します)");
            }
            return layers(attacker, target, level, healthKey, grace);
        } finally {
            // 死なせられていても戻す。リスポーンは古い実体の持ち物を引き継ぐので、装備は本人に残る
            saved.forEach((slot, worn) ->
                    DamageGuard.runAsSelf(() -> target.setItemSlot(slot, worn)));
        }
    }

    private static boolean hasEquipment(LivingEntity target) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!target.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * まだ耐えている相手について、抵抗の<b>材料</b>を診断に出す。
     *
     * <p>{@link StrikeRosters#strip} は外せた名簿しか報せない。名指ししているのに外せない名簿が
     * あれば「見えているのに効いていない」、名簿が空なら「材料が実体の外の別の形にある」と分かる。
     * 同期データの殻の有無も添える。追撃 1 回につき一度だけ。
     */
    private static void diagnoseResistance(ServerLevel level, LivingEntity target) {
        List<String> naming = StrikeRosters.naming(target);
        boolean wrapped = StateProbe.rawData(target.getEntityData()) != target.getEntityData();
        GuardNotice.info("貫通攻撃: " + target.getName().getString()
                + " がまだ耐えています。名指ししている名簿=" + (naming.isEmpty() ? "なし" : String.join(", ", naming))
                + " / 同期データの殻=" + (wrapped ? "あり (剥がして読みます)" : "なし")
                + " / 除去の印=" + HealthGuard.rawRemoved(target)
                + " / 索引に残っている場所: " + presenceDetail(level, target));
    }

    /**
     * どの索引がまだこの実体を数えているか。
     *
     * <p>{@link #present} は 3 つの索引の論理和なので、「消えない」とだけ分かっても
     * <b>どこに残っているか</b>が分からない。残っている場所ごとに外し方が違う
     * (UUID 索引・tick 一覧・区画) ので、切り分けられないと次の手が決まらない。
     */
    private static String presenceDetail(ServerLevel level, LivingEntity target) {
        String byUuid;
        String byTick;
        String bySection;
        try {
            byUuid = String.valueOf(level.getEntity(target.getUUID()) == target);
        } catch (Throwable unreadable) {
            byUuid = "?";
        }
        try {
            byTick = String.valueOf(((AccessorServerLevel) level).tpsthings$entityTickList().contains(target));
        } catch (Throwable unreadable) {
            byTick = "?";
        }
        String sectionDetail = "";
        try {
            EntitySection<EntityAccess> section =
                    sections(level).getSection(SectionPos.asLong(target.blockPosition()));
            boolean holds = section != null && section.getEntities().anyMatch(e -> e == target);
            bySection = String.valueOf(holds);
            if (holds) {
                // 外し漏れた入れ物があるのか、入れ物には居ないのに読み出しだけが
                // 混ぜて返しているのか。ここが分かれ目なので名前で出す
                List<String> where = StrikeIndex.locate(section, target);
                sectionDetail = " / 区画の内訳=" + (where.isEmpty()
                        ? "入れ物には居ません (読み出しだけが数えています。区画の型="
                          + section.getClass().getSimpleName() + ")"
                        : String.join(", ", where));
            }
        } catch (Throwable unreadable) {
            bySection = "?";
        }
        return "UUID索引=" + byUuid + " tick一覧=" + byTick + " 区画=" + bySection + sectionDetail;
    }

    /**
     * 打った結果を 1 行だけ残す。
     *
     * <p>報告はこれまで<b>耐えられたときにしか出なかった</b>。倒しきれた打撃は何も残さないので、
     * ログが 0 行のときに「一度も打っていない」のか「全部通った」のかが区別できず、
     * 実測で何度も判断に詰まった。勝敗のどちらでも 1 行は残す。
     *
     * <p>ただしバニラの Mob が正規の道で素直に死ぬ分まで出すと、本物の 1 行が埋もれる。
     * 深い層を要した打撃と、プレイヤー相手 (検証で必ず知りたい) だけに絞り、
     * 同じ相手への連打は間引く。
     */
    private static void note(LivingEntity target, Result result) {
        boolean deep = result.health() == 6 || result.death() == 8
                || result.removal() == 10 || result.removal() == FAILED;
        if (!deep && !(target instanceof Player)) {
            return;
        }
        GuardNotice.infoThrottled("strike-" + target.getUUID(),
                "貫通攻撃: " + target.getName().getString() + " → " + result
                        + (refused(result) ? " (まだ耐えています)" : " (倒しきりました)"));
    }

    /**
     * まだ倒しきれていないか。倒しきれていないなら守りの材料を抜いて打ち直す。
     *
     * <p>目標は<b>世界から消すこと</b>。だから成否は「どの層で通ったか」ではなく<b>消えたか</b>で見る:
     * <ul>
     *   <li>抹消層・索引層まで通って消えた (removal 9/10) → 倒しきった。
     *   <li>正規の道で素直に死んで、除去はリスポーンに任せた (health5・death5・removal0) → 倒しきった。
     *   <li>それ以外 (索引に残った FAILED / 死ねても消えてもいない) → まだ。材料を抜いて打ち直す。
     * </ul>
     */
    private static boolean refused(Result result) {
        if (result.removal() == 9 || result.removal() == 10) {
            return false; // 世界から消えた
        }
        if (result.removal() == 0 && result.health() == 5 && result.death() == 5) {
            return false; // 素直に死んだ (除去はリスポーンに任せた)
        }
        return true;
    }

    /**
     * 浅い層から順に 1 回通す。
     *
     * @param grace 渡されていて、相手が正規の道で死んだなら、除去せずに倒れ終わるのを待たせる
     */
    private static Result layers(@Nullable Player attacker, LivingEntity target, ServerLevel level,
                                 EntityDataAccessor<Float> healthKey, @Nullable Pursuit grace) {

        // ---- 表層〜不可侵層: 無敵の類を外す。ここは拒否されても次の層で結果が出るので確かめない
        attempt(() -> {
            target.setInvulnerable(false);
            target.invulnerableTime = 0;
            if (attacker != null) {
                // ドロップと経験値を持ち主に付ける
                target.setLastHurtByPlayer(attacker);
            }
        });

        // ---- 挙動層・合議層: 正規の道。無敵貫通のダメージ種で最大値
        attempt(() -> target.hurt(level.damageSources().genericKill(), Float.MAX_VALUE));
        int health = raw(target, healthKey) <= 0.0F ? 5 : 0;
        int death = dead(target) ? 5 : 0;

        // ---- 生値層: HP の入れ物へ。入口 (set) → 箱 (DataItem) の順
        if (health == 0) {
            attempt(() -> target.getEntityData().set(healthKey, 0.0F, true));
            if (raw(target, healthKey) > 0.0F) {
                attempt(() -> writeItem(target, healthKey, 0.0F));
            }
            health = raw(target, healthKey) <= 0.0F ? 6 : FAILED;
        }

        // ---- 終焉層: 死亡処理を直接。HP を見て死を判断しない相手にも死を通す
        if (death == 0) {
            DamageSource source = attacker != null
                    ? level.damageSources().playerAttack(attacker)
                    : level.damageSources().genericKill();
            attempt(() -> target.die(source));
            death = dead(target) ? 8 : FAILED;
        }

        // ---- 正規の道で死んだ相手は、倒れ終わるまで待つ。
        // ここで除去すると倒れる姿も煙も出ずに消え、深い層で消した相手と見分けがつかなくなる。
        // 報告の「消」は、倒れ終わりの remove (抹消層) として出す。消えなければ追撃が抹消層から続きを打つ
        if (grace != null && health == 5 && death == 5 && !(target instanceof Player)) {
            grace.graceTicks = DEATH_GRACE_TICKS;
            return new Result(health, death, 9);
        }

        // プレイヤーを世界から剥がすと通信路だけが残る。<b>正規の道で素直に死んだ</b>プレイヤーは
        // リスポーンに任せ、そこで止める (普段の即死はこれ。死亡画面が出て、蘇って戻ってくる)。
        // だが<b>死を拒んだ</b>プレイヤー — 相手 Mod が死亡処理を潰して素直に死なない — は、
        // 死なせられた見た目だけ残して世界に残り続ける。そういう相手は索引層まで通して消滅させる。
        // (強制removeの既定 playersFullDepth が入っていれば、素直に死んだ相手も剥がす)
        boolean diedCleanly = health == 5 && death == 5;
        if (target instanceof Player && !playersFullDepth && diedCleanly) {
            return new Result(health, death, 0);
        }

        // ---- 抹消層: 除去。remove の上書きを飛ばすために setRemoved も直接叩く。
        // isRemoved() は書き換えられて嘘をつくので、印は必ずフィールドを直読みして確かめる
        int removal = 0;
        attempt(() -> target.remove(Entity.RemovalReason.KILLED));
        if (!HealthGuard.rawRemoved(target)) {
            attempt(() -> target.setRemoved(Entity.RemovalReason.KILLED));
        }
        if (!HealthGuard.rawRemoved(target)) {
            // 入口ごと塞がれていても、印そのものは立てられる。索引は下の層で外す
            attempt(() -> ((AccessorEntity) target).tpsthings$setRemovalReason(Entity.RemovalReason.KILLED));
        }
        if (HealthGuard.rawRemoved(target) && !present(level, target)) {
            removal = 9;
        }

        // ---- 索引層: 除去の印が立っても、索引に残っていれば世界はまだ数えている
        if (removal == 0) {
            attempt(() -> erase(level, target));
            removal = present(level, target) ? FAILED : 10;
        }

        return new Result(health, death, removal);
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
        // 消したプレイヤーの後始末。打った場所では呼べないので、ここまで持ち越してある
        if (!AWAITING_RESPAWN.isEmpty()) {
            for (UUID id : List.copyOf(AWAITING_RESPAWN)) {
                AWAITING_RESPAWN.remove(id);
                ServerPlayer victim = server.getPlayerList().getPlayer(id);
                if (victim == null) {
                    continue; // もう繋がっていない。作り直す先が無い
                }
                if (RespawnGuard.respawnAfterRemoval(victim) != null) {
                    GuardNotice.info("貫通攻撃: " + victim.getName().getString()
                            + " を世界から消したあと、正規のリスポーンで作り直しました"
                            + " (通信路だけが残って画面が壊れるのを防ぎます)");
                }
            }
        }
        if (PURSUITS.isEmpty()) {
            return;
        }
        Iterator<Pursuit> pursuits = PURSUITS.values().iterator();
        while (pursuits.hasNext()) {
            Pursuit pursuit = pursuits.next();
            if (--pursuit.ticksLeft <= 0 || --pursuit.budget <= 0
                    || !(pursuit.body.level() instanceof ServerLevel level)) {
                release(pursuit, server);
                pursuits.remove();
                continue;
            }
            if (pursuit.disarmCooldown > 0) {
                pursuit.disarmCooldown--;
            }
            Player attacker = pursuit.attacker == null ? null : server.getPlayerList().getPlayer(pursuit.attacker);
            LivingEntity body = pursuit.body;
            if (pursuit.graceTicks > 0) {
                boolean fallen = raw(body, AccessorLivingEntity.tpsthings$healthId()) <= 0.0F && dead(body);
                if (HealthGuard.rawRemoved(body) && !present(level, body)) {
                    // 倒れ終わって正規に消えた。ここからは戻ってこないかだけを見る
                    pursuit.graceTicks = 0;
                } else if (fallen && --pursuit.graceTicks > 0) {
                    continue;
                } else {
                    // 倒れている途中で起き上がったか、待っても消えなかった。もう待たずに下の層まで打つ
                    pursuit.graceTicks = 0;
                    pierce(attacker, body, pursuit, false);
                    continue;
                }
            }
            if (revived(level, body)) {
                pierce(attacker, body, pursuit, false);
                // 戻ってくる限りは見張りを延ばす (総量は budget で区切る)
                pursuit.ticksLeft = Math.max(pursuit.ticksLeft, PURSUIT_TICKS);
            }
            // 同じ UUID で作り直してくる相手。プレイヤーのリスポーンは正規なので追わない
            if (!(body instanceof Player)
                    && level.getEntity(body.getUUID()) instanceof LivingEntity twin && twin != body) {
                pierce(attacker, twin, pursuit, false);
                pursuit.ticksLeft = Math.max(pursuit.ticksLeft, PURSUIT_TICKS);
            }
        }
    }

    /** サーバをまたいで持ち越さない。強参照なので、残すと世界ごと GC できなくなる。 */
    public static void reset() {
        PURSUITS.values().forEach(pursuit -> release(pursuit, null));
        PURSUITS.clear();
        AWAITING_RESPAWN.clear();
    }

    /**
     * 追撃の終わり。外した名簿は、相手が生き残っているときだけ戻す。
     *
     * <p>倒し切れなかったのに外したままにすると、関係の無い仕組みまで壊したことになる
     * (保存時に名簿を見て印を書く作りなら、無敵が永久に失われる)。倒し切った相手の記載は
     * 宙に浮いた古いものなので戻さない。プレイヤーはリスポーン後の実体で見る。
     */
    private static void release(Pursuit pursuit, @Nullable MinecraftServer server) {
        // 索引を戻す判断は名簿より先に見る。層は名簿に触らなくても索引まで降りるので、
        // 「外した名簿が無い = 何もしていない」ではない
        if (restoreOnFailure) {
            restorePresence(pursuit.body);
        }
        if (pursuit.taken.isEmpty() && pursuit.stateChanges.isEmpty()) {
            return;
        }
        LivingEntity standing = pursuit.body;
        if (server != null && pursuit.body instanceof Player) {
            standing = server.getPlayerList().getPlayer(pursuit.body.getUUID());
        } else if (pursuit.body.level() instanceof ServerLevel level
                && level.getEntity(pursuit.body.getUUID()) instanceof LivingEntity current) {
            standing = current;
        }
        boolean survived = standing != null && !HealthGuard.rawRemoved(standing)
                && !dead(standing) && raw(standing, AccessorLivingEntity.tpsthings$healthId()) > 0.0F;
        if (survived) {
            StrikeRosters.restore(pursuit.taken);
            StrikeState.restore(pursuit.stateChanges);
        }
        pursuit.taken.clear();
        pursuit.stateChanges.clear();
    }

    /**
     * 消しきれなかった相手を世界へ戻す。
     *
     * <p>戻すのは<b>まだ世界に居るのに、こちらが除去の印だけ立ててしまった相手</b>だけ。
     * 本当に消えた相手は戻さないし、印が無い相手はそもそもここで剥がしていない。
     *
     * <p>継ぎ足しではなく、残骸を片付けてからバニラの登録を 1 回通す。区画には残ったまま
     * 登録し直すと、同じ者が世界に二重に並んで後始末の側が壊れる
     * ({@link PresenceGuard#rebuild} が同じ理由で同じ順にしている)。
     */
    private static void restorePresence(LivingEntity body) {
        if (!(body.level() instanceof ServerLevel level) || !HealthGuard.rawRemoved(body)) {
            return; // 印が無い = ここで剥がした相手ではない
        }
        if (body instanceof ServerPlayer player && PresenceGuard.stale(player)) {
            return; // リスポーンで作り直された後の古い実体。戻す先が無い
        }
        if (!present(level, body)) {
            return; // 本当に world から消えている。戻すのは「消しきれなかった相手」だけ
        }
        AccessorServerLevel accessor = (AccessorServerLevel) level;
        DamageGuard.runAsSelf(() -> {
            attempt(() -> ((AccessorEntity) body).tpsthings$setRemovalReason(null));
            attempt(() -> StrikeIndex.purge(accessor.tpsthings$entityManager(), body));
            attempt(() -> StrikeIndex.purge(accessor.tpsthings$entityTickList(), body));
            attempt(() -> sections(level).getExistingSectionsInChunk(ChunkPos.asLong(body.blockPosition()))
                    .forEach(section -> StrikeIndex.purge(section, body)));
            attempt(() -> accessor.tpsthings$entityManager().addNewEntityWithoutEvent(body));
            attempt(() -> {
                if (!body.isAddedToWorld()) {
                    body.onAddedToWorld();
                }
            });
        });
        GuardNotice.info("貫通攻撃: " + body.getName().getString()
                + " を消しきれなかったので、世界の索引へ戻しました"
                + " (動けるのにブロックが壊せない中途半端な状態を残さない)");
    }

    /** 偽装に影響されない「死んでいるか」。同期データの生値・{@code dead} フィールド・除去の印だけで見る。 */
    private static boolean rawDead(LivingEntity target) {
        return raw(target, AccessorLivingEntity.tpsthings$healthId()) <= 0.0F
                || dead(target) || HealthGuard.rawRemoved(target);
    }

    /**
     * 並べた相手に順に打つ (掃討)。
     *
     * @return 倒しきれた数。成否は {@link #refused} で見る — 死や除去の層の数字だけで数えると、
     *         正規の道で素直に倒れた相手 (除去はリスポーン任せ) を取りこぼす
     */
    public static int strikeEach(@Nullable Player attacker, List<? extends LivingEntity> targets) {
        int down = 0;
        for (LivingEntity target : targets) {
            if (!refused(strike(attacker, target))) {
                down++;
            }
        }
        return down;
    }

    public static boolean isPlayersFullDepth() {
        return playersFullDepth;
    }

    public static void setPlayersFullDepth(boolean value) {
        playersFullDepth = value;
    }

    public static boolean isRestoreOnFailure() {
        return restoreOnFailure;
    }

    public static void setRestoreOnFailure(boolean value) {
        restoreOnFailure = value;
    }

    // ---- 状態の確認 -------------------------------------------------------------

    private static boolean revived(ServerLevel level, LivingEntity body) {
        if (body instanceof ServerPlayer player) {
            // リスポーン後の古い実体は死んだままでよい
            if (PresenceGuard.stale(player)) {
                return false;
            }
            if (playersFullDepth) {
                return !HealthGuard.rawRemoved(player) || present(level, player);
            }
            return !HealthGuard.rawRemoved(player) && raw(player, AccessorLivingEntity.tpsthings$healthId()) > 0.0F;
        }
        return !HealthGuard.rawRemoved(body) || present(level, body);
    }

    /**
     * 同期データの生値。{@code getHealth()} は偽装されうるので読まない。
     *
     * 入れ物ごと殻で包んで {@code get} 自体に嘘をつかせる相手が居るので、殻を剥がした
     * 素の入れ物から読む ({@link StateProbe#rawData})。包まれていなければそのまま。
     */
    private static float raw(LivingEntity target, EntityDataAccessor<Float> key) {
        return StateProbe.rawData(target.getEntityData()).get(key);
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

    /** 入口 ({@code set}) を通らず、値が載っている箱に直接書く。殻で包まれていれば素の箱へ。 */
    @SuppressWarnings("unchecked")
    private static void writeItem(LivingEntity target, EntityDataAccessor<Float> key, float value) {
        Map<?, ?> items = StateProbe.rawItemsOf(target.getEntityData());
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
        // 索引を書き換える手は、どれも「削除されていないのに索引から外す」形をしている。
        // 守る側の関所はまさにその形に反応するので、自分の矛を自分の関所に止めさせないよう
        // 自分の操作として通す ({@link PresenceGuard#purge} の片付けと同じ扱い)
        DamageGuard.runAsSelf(() -> eraseNow(level, target));
    }

    private static void eraseNow(ServerLevel level, LivingEntity target) {
        AccessorServerLevel accessor = (AccessorServerLevel) level;
        PersistentEntitySectionManager<Entity> manager = accessor.tpsthings$entityManager();
        AccessorPersistentEntitySectionManager managerAccessor = (AccessorPersistentEntitySectionManager) (Object) manager;

        accessor.tpsthings$entityTickList().remove(target);
        EntityLookup<EntityAccess> lookup = managerAccessor.tpsthings$visibleEntityStorage();
        lookup.remove(target);
        forgetIndexed(lookup, target);

        EntitySectionStorage<EntityAccess> sections = managerAccessor.tpsthings$sectionStorage();
        EntitySection<EntityAccess> section = sections.getSection(SectionPos.asLong(target.blockPosition()));
        if (section == null || !section.remove(target)) {
            // 位置の通知を飛ばして動かされていると、載っている区画と座標がずれる。同じチャンクを舐める
            sections.getExistingSectionsInChunk(ChunkPos.asLong(target.blockPosition()))
                    .forEach(candidate -> candidate.remove(target));
        }
        managerAccessor.tpsthings$knownUuids().remove(target.getUUID());

        // ここまでは「バニラが持っている入れ物」への手。索引の持ち主に入れ物を生やされると、
        // 相手はそちらに隠れたまま残る (区画の並びの入れ物・UUID 台帳の写しなど)。
        // 索引を担う物それぞれについて、抱えている入れ物を名指しせずに全部舐めて外す
        StrikeIndex.purge(accessor.tpsthings$entityTickList(), target);
        StrikeIndex.purge(manager, target);
        sections.getExistingSectionsInChunk(ChunkPos.asLong(target.blockPosition()))
                .forEach(candidate -> StrikeIndex.purge(candidate, target));
        // 座標の区画は名指しでも外す。区画の一覧 (getExistingSectionsInChunk) は別の台帳を
        // 引いているので、そちらから外されていると一覧に出ず、素通りしてしまう
        StrikeIndex.purge(sections.getSection(SectionPos.asLong(target.blockPosition())), target);

        // ここで一度測る。入れ物を外した直後に消えているなら、後の手順で誰かが入れ直している。
        // 直後から消えていないなら、外し漏れている入れ物がある。外からは同じ「まだ居る」にしか
        // 見えないので、erase の中で挟まないと切り分けられない
        boolean clearedByPurge = !present(level, target);

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

        if (!clearedByPurge) {
            GuardNotice.warnThrottled("strike-not-cleared", "貫通攻撃: " + target.getName().getString()
                    + " は索引の入れ物を外した直後から、まだ世界に数えられています (外し漏れている入れ物があります)");
        } else if (present(level, target)) {
            GuardNotice.warnThrottled("strike-re-added", "貫通攻撃: " + target.getName().getString()
                    + " は索引から消えた直後に入れ直されました (除去の後始末の途中で戻されています)");
        }
    }

    /**
     * UUID 索引から確実に外す。
     *
     * <p>{@code EntityLookup#remove} はただのメソッドで、頭で握り潰せば何事も無かったように
     * 戻ってくる — 例外も戻り値も無いので、呼んだ側からは成功と見分けがつかない。
     * 実測でも、tick 一覧と区画は外せているのに UUID 索引にだけ残り続けていた
     * (除去の印=true / UUID索引=true / tick一覧=false / 区画=false)。
     *
     * <p>だから外せたかどうかはメソッドの結果ではなく<b>索引の状態</b>で確かめ、
     * 残っていれば値が実際に載っている入れ物へ直接書く。層を降りる作りと同じ考え方で、
     * 誰が握り潰したのかは知らなくてよい。
     */
    private static void forgetIndexed(EntityLookup<EntityAccess> lookup, LivingEntity target) {
        if (!indexed(lookup, target)) {
            return; // 正規の道で外れた
        }
        // まず素の索引 (バニラが持っている 2 つ) へ直接書く
        AccessorEntityLookup accessor = (AccessorEntityLookup) lookup;
        attempt(() -> accessor.tpsthings$byUuid().remove(target.getUUID()));
        attempt(() -> accessor.tpsthings$byId().remove(target.getId()));
        if (!indexed(lookup, target)) {
            GuardNotice.warnThrottled("strike-index-refused", "貫通攻撃: " + target.getName().getString()
                    + " の索引からの除去が握り潰されました (呼んでも外れない)。索引の中身へ直接書きました");
            return;
        }
        // まだ引ける = 素の索引には元から載っていない。索引の持ち主が<b>別の入れ物</b>を
        // 生やして、そちらに隠したうえで読み出しだけ素の索引に混ぜて返している
        int purged = StrikeIndex.purge(lookup, target);
        GuardNotice.warnThrottled("strike-index-shadow", "貫通攻撃: " + target.getName().getString()
                + " は素の索引に載っていませんでした (別の入れ物に隠されています)。"
                + "索引が持っている入れ物 " + purged + " 個から外しました");
    }

    /**
     * 索引から本人が引けるか。
     *
     * <p>読み出しごと差し替えられていても、<b>引けるなら世界はまだ数えている</b>。
     * どの入れ物に載っているかを知らなくても、引けるかどうかは必ず本当のことを言う。
     */
    private static boolean indexed(EntityLookup<EntityAccess> lookup, LivingEntity target) {
        return lookup.getEntity(target.getUUID()) == target || lookup.getEntity(target.getId()) == target;
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
