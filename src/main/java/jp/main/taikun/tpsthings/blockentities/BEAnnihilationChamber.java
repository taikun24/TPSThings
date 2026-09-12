package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.network.ModNetwork;
import jp.main.taikun.tpsthings.network.PacketAbyss;
import jp.main.taikun.tpsthings.registries.ModBlockEntityTypes;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;

/**
 * 対消滅炉。
 *
 * GUI を持たない。上面に落ちているアイテムを毎 tick 見て、材料が揃っていたら消費する。
 * 出力は<b>重み付きの抽選</b>で、外れると何も出ない — 未定義動作を素材として扱う都合上、
 * 結果が定まらないこと自体が仕様。
 *
 * <p>おおの最終工程 (儀式) もここで行う。L1 から L10 の素材を層の順に 1 つずつ投げ込むと、
 * 1 段ごとに周りの世界が暗く静かになり、Java Agent で全てが途切れたあと、おおが浮かび上がる。
 */
public class BEAnnihilationChamber extends BlockEntity {

    /** 1 回反応したら少し黙る。連続投入で 1 tick に何度も走らせない。 */
    private static final int COOLDOWN_TICKS = 20;

    private int cooldown;

    public BEAnnihilationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ANNIHILATION_CHAMBER.get(), pos, state);
    }

    /** 必要な材料 1 種。 */
    public record Need(Supplier<Item> item, int count) {}

    /** 抽選結果 1 つ。{@code item} が null なら「何も出ない」。 */
    public record Outcome(int weight, @Nullable Supplier<Item> item, int count) {}

    public record Reaction(List<Need> inputs, List<Outcome> outcomes) {}

    /** 反応の一覧。JEI の表示もここを読むので、レシピの定義はこの 1 箇所だけになる。 */
    public static List<Reaction> reactions() {
        return REACTIONS;
    }

    /**
     * ModItems の RegistryObject をそのまま掴むとレジストリ登録前に触ることになるので、
     * メソッド参照 (Supplier) にして参照を反応時まで遅らせる。
     */
    private static final List<Reaction> REACTIONS = List.of(
            // HOPE と反 HOPE の対消滅。ふつうは両方消えるだけで、稀に境界の穴が残る
            new Reaction(
                    List.of(new Need(ModItems.HOPE_SHEET::get, 1),
                            new Need(ModItems.ANTI_HOPE_SHEET::get, 1)),
                    List.of(new Outcome(1, ModItems.UNDEFINED_SHARD::get, 1),
                            new Outcome(4, null, 0))
            ),
            // 欠片を無敵フラグで縛って未定義動作へ。失敗すると「おおじゃないが」が出る
            new Reaction(
                    List.of(new Need(ModItems.UNDEFINED_SHARD::get, 2),
                            new Need(ModItems.INVULNERABLE_FLAG::get, 2),
                            new Need(ModItems.TIME_FLUX_CRYSTAL::get, 1)),
                    List.of(new Outcome(6, ModItems.UNDEFINED_BEHAVIOR::get, 1),
                            new Outcome(3, ModItems.NOT_OO::get, 1),
                            new Outcome(1, null, 0))
            ),
            // 握り潰した死を凍った時間で包むと、消されること自体を拒めるようになる
            new Reaction(
                    List.of(new Need(ModItems.DEATH_HOOK::get, 1),
                            new Need(ModItems.FROZEN_TICK::get, 4)),
                    List.of(new Outcome(3, ModItems.REMOVAL_VETO::get, 1),
                            new Outcome(1, ModItems.NOT_OO::get, 1))
            )
    );

    // ---- 儀式 ------------------------------------------------------------------

    /** 儀式で投げ込む順番。L1 から L10 まで、層を 1 枚ずつ降りる。JEI の説明もここを読む。 */
    private static final List<Supplier<Item>> RITUAL = List.of(
            ModItems.ANTI_HOPE_SHEET::get,    // L1
            ModItems.UNDEFINED_SHARD::get,    // L2
            ModItems.INVULNERABLE_FLAG::get,  // L3
            ModItems.UNDEFINED_BEHAVIOR::get, // L4
            ModItems.CANCELLED_EVENT::get,    // L5
            ModItems.RAW_HEALTH::get,         // L6
            ModItems.MIXIN::get,              // L7
            ModItems.DEATH_HOOK::get,         // L8
            ModItems.REMOVAL_VETO::get,       // L9
            ModItems.JAVA_AGENT::get          // L10
    );

    public static List<Supplier<Item>> ritual() {
        return RITUAL;
    }

    /** これだけ何も投げ込まれなければ、儀式は途切れる (1 分) */
    private static final int RITUAL_TIMEOUT = 1200;
    /** Java Agent が入ってから、おおが浮かび上がるまで。L10 のフラッシュと、その後の完全な無音 */
    private static final int FINALE_TICKS = 60;
    private static final int FLASH_TICKS = 20;
    /** 暗さと静けさを届ける範囲 (ブロック) */
    private static final double ABYSS_RANGE = 32.0;
    /** 無音が明けて白に溶かす長さ。短く、薄く */
    private static final int FINALE_WHITEOUT_TICKS = 20;
    /** 浮かび上がったおおを手に取れるまで。白が引いて、浮かんでいくのを見届ける時間 */
    private static final int OO_PICKUP_DELAY = 100;

    /** 済んだ段の数 (0 = 儀式の外)。 */
    private int ritualStage;
    /** 最後に段が進んでからの tick */
    private int ritualIdle;
    /** 最後の無音の経過 tick。-1 なら最後の段に居ない */
    private int finale = -1;

    public void tick() {
        if (!(this.level instanceof ServerLevel server)) return;
        if (this.finale >= 0) {
            tickFinale(server);
            return;
        }
        if (this.ritualStage > 0) {
            if (++this.ritualIdle > RITUAL_TIMEOUT) {
                fizzle(server);
                return;
            }
            // 周りの暗さと静けさは、クライアント側で切れる前に送り直す
            if (this.ritualIdle % 20 == 0) {
                holdAbyss(server, 0);
            }
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }
        List<ItemEntity> items = server.getEntitiesOfClass(ItemEntity.class,
                new AABB(this.worldPosition.above()).inflate(0.25, 0.5, 0.25),
                entity -> entity.isAlive() && !entity.getItem().isEmpty());
        if (items.isEmpty()) return;

        if (this.ritualStage > 0) {
            // 儀式の途中は普通の反応を止める。材料の取り合いにしない
            stepRitual(server, items);
            return;
        }
        for (Reaction reaction : REACTIONS) {
            if (react(server, reaction, items)) {
                this.cooldown = COOLDOWN_TICKS;
                return;
            }
        }
        tryBeginRitual(server, items);
    }

    private boolean react(ServerLevel server, Reaction reaction, List<ItemEntity> items) {
        for (Need need : reaction.inputs()) {
            if (count(items, need.item().get()) < need.count()) return false;
        }
        for (Need need : reaction.inputs()) {
            consume(items, need.item().get(), need.count());
        }
        emit(server, pick(server, reaction.outcomes()));
        return true;
    }

    /**
     * 始まりの 1 枚 (反 HOPE シート) が、他に何も無い上面に 1 枚だけ置かれたら儀式を始める。
     * HOPE との対消滅と取り合わないよう、単独の 1 枚に限る。
     */
    private void tryBeginRitual(ServerLevel server, List<ItemEntity> items) {
        if (items.size() != 1) return;
        ItemStack stack = items.get(0).getItem();
        Item first = RITUAL.get(0).get();
        if (stack.getCount() != 1 || !stack.is(first)) return;
        consume(items, first, 1);
        advanceRitual(server);
    }

    private void stepRitual(ServerLevel server, List<ItemEntity> items) {
        // 先の層の素材が混ざったら、順番を飛ばしたとみなす。済んだ層の余りと、儀式に関係ない物は見ない
        for (int later = this.ritualStage + 1; later < RITUAL.size(); later++) {
            Item skipped = RITUAL.get(later).get();
            if (count(items, skipped) > 0) {
                consume(items, skipped, 1);
                failRitual(server);
                return;
            }
        }
        Item expected = RITUAL.get(this.ritualStage).get();
        if (count(items, expected) > 0) {
            consume(items, expected, 1);
            advanceRitual(server);
        }
    }

    /** 1 段降りる。降りるほど輪は狭まり、色は赤紫に寄り、音は低くなる。 */
    private void advanceRitual(ServerLevel server) {
        this.ritualStage++;
        this.ritualIdle = 0;
        this.cooldown = COOLDOWN_TICKS;
        setChanged();

        float depth = this.ritualStage / 10.0F;
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 1.1;
        double z = this.worldPosition.getZ() + 0.5;

        DustParticleOptions dust = new DustParticleOptions(new Vector3f(
                Mth.lerp(depth, 0.10F, 0.90F), Mth.lerp(depth, 0.80F, 0.08F), Mth.lerp(depth, 0.90F, 0.40F)), 1.5F);
        int points = 24 + this.ritualStage * 4;
        double radius = 1.6 - depth;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            server.sendParticles(dust, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 10 + this.ritualStage * 4, 0.2, 0.4, 0.2, 0.05);
        server.playSound(null, this.worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS,
                1.0F, 2.0F - depth * 1.5F);

        if (this.ritualStage >= RITUAL.size()) {
            this.finale = 0;
        } else {
            // 深い段ほどインパクトフレームを長く打つ
            holdAbyss(server, 2 + this.ritualStage / 3);
        }
    }

    /**
     * Java Agent が入った瞬間、周りの画面を L10 で塗り、炉から鳴る音の他は全部止める。
     * 無音が明けたら、光と音を一度に戻して、おおを静かに浮かび上がらせる。
     */
    private void tickFinale(ServerLevel server) {
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 1.1;
        double z = this.worldPosition.getZ() + 0.5;

        if (this.finale == 0) {
            // 世界ごと崩しながら L10 で塗り、インパクトフレームを長く叩き込む
            sendAbyss(server, new PacketAbyss(FLASH_TICKS, 1.0F, 0.75F, 1.0F, FLASH_TICKS, 10, 0));
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 200, 0.6, 0.6, 0.6, 0.4);
            server.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.5F);
        } else if (this.finale == FLASH_TICKS) {
            // ノイズが止み、ほぼ真っ暗で何も聞こえない静止
            sendAbyss(server, new PacketAbyss(FINALE_TICKS - FLASH_TICKS + 10, 1.0F, 0.92F, 0.0F, 0, 0, 0));
        }
        if (++this.finale < FINALE_TICKS) {
            setChanged();
            return;
        }

        // 音を戻すのが先。後から鳴らす音まで消されないように。暗闇は薄い白に溶かしてから戻す
        sendAbyss(server, new PacketAbyss(0, 0.0F, 0.0F, 0.0F, 0, 0, FINALE_WHITEOUT_TICKS));

        ItemEntity oo = new ItemEntity(server, x, y, z, new ItemStack(ModItems.OO.get()));
        oo.setNoGravity(true);
        oo.setDeltaMovement(0.0, 0.015, 0.0);
        oo.setPickUpDelay(OO_PICKUP_DELAY);
        oo.setGlowingTag(true);
        server.addFreshEntity(oo);

        // 完成品だけは静かに。光の粒を少しと、澄んだ音を 1 つだけ
        server.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, 0.1, 0.1, 0.1, 0.01);
        server.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.8F);
        // 誰の名前も付けずに
        server.getServer().getPlayerList().broadcastSystemMessage(Component.literal("おお"), false);

        resetRitual();
    }

    /**
     * 順番を飛ばした。深層から押し戻され、「おおじゃないが」が外へ弾き出される。
     * 反応の成功 (emit) と同じ高い音と光にしない。
     */
    private void failRitual(ServerLevel server) {
        resetRitual();
        this.cooldown = COOLDOWN_TICKS;
        // 押し戻される瞬間だけ、インパクトフレームで崩す
        sendAbyss(server, new PacketAbyss(0, 0.0F, 0.0F, 0.0F, 0, 6, 0));

        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 1.1;
        double z = this.worldPosition.getZ() + 0.5;
        ItemEntity rejected = new ItemEntity(server, x, y, z, new ItemStack(ModItems.NOT_OO.get()));
        // 上面に落ち直さないよう、横へ跳ね飛ばす
        double angle = server.random.nextDouble() * Math.PI * 2.0;
        rejected.setDeltaMovement(Math.cos(angle) * 0.25, 0.35, Math.sin(angle) * 0.25);
        rejected.setPickUpDelay(20);
        server.addFreshEntity(rejected);

        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 60, 0.1, 0.1, 0.1, 0.35);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.2, 0.1, 0.2, 0.02);
        server.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.5F);
        server.playSound(null, this.worldPosition, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.5F);
    }

    /** 投げ込みが途絶えた。何も残らない。 */
    private void fizzle(ServerLevel server) {
        resetRitual();
        sendAbyss(server, PacketAbyss.RESET);
        server.sendParticles(ParticleTypes.SMOKE, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.1,
                this.worldPosition.getZ() + 0.5, 20, 0.2, 0.1, 0.2, 0.01);
        server.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    private void resetRitual() {
        this.ritualStage = 0;
        this.ritualIdle = 0;
        this.finale = -1;
        setChanged();
    }

    /** 今の段の深さに応じて、周りを暗く静かに、崩れた状態に保つ。impact は段が進んだ瞬間だけ渡す。 */
    private void holdAbyss(ServerLevel server, int impact) {
        float depth = this.ritualStage / 10.0F;
        sendAbyss(server, new PacketAbyss(40, depth * 0.8F, depth * 0.45F, depth * 0.6F, 0, impact, 0));
    }

    private void sendAbyss(ServerLevel server, PacketAbyss packet) {
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 1.0;
        double z = this.worldPosition.getZ() + 0.5;
        // 炉の位置を添える。ここで鳴る儀式の音は、儀式が作った静けさに消されない
        // (段が進む音も最後の一撃も、前の段の静けさに確率で、あるいは必ず消されていた)
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(
                x, y, z, ABYSS_RANGE, server.dimension())), packet.from(x, y, z));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("RitualStage", this.ritualStage);
        tag.putInt("RitualIdle", this.ritualIdle);
        tag.putInt("Finale", this.finale);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.ritualStage = Mth.clamp(tag.getInt("RitualStage"), 0, RITUAL.size());
        this.ritualIdle = tag.getInt("RitualIdle");
        this.finale = tag.contains("Finale") ? tag.getInt("Finale") : -1;
    }

    // ---- 共通 ------------------------------------------------------------------

    private static int count(List<ItemEntity> items, Item item) {
        int total = 0;
        for (ItemEntity entity : items) {
            if (entity.getItem().is(item)) total += entity.getItem().getCount();
        }
        return total;
    }

    private static void consume(List<ItemEntity> items, Item item, int amount) {
        int remaining = amount;
        for (ItemEntity entity : items) {
            if (remaining <= 0) break;
            ItemStack stack = entity.getItem();
            if (!stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            // ItemEntity は同期データ越しに中身を持つので、減らしたら必ず入れ直す
            entity.setItem(stack);
            if (stack.isEmpty()) entity.discard();
        }
    }

    private static Outcome pick(ServerLevel server, List<Outcome> outcomes) {
        int total = 0;
        for (Outcome outcome : outcomes) total += outcome.weight();
        int roll = server.random.nextInt(total);
        for (Outcome outcome : outcomes) {
            roll -= outcome.weight();
            if (roll < 0) return outcome;
        }
        return outcomes.get(outcomes.size() - 1);
    }

    private void emit(ServerLevel server, Outcome outcome) {
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 1.1;
        double z = this.worldPosition.getZ() + 0.5;

        boolean produced = outcome.item() != null && outcome.count() > 0;
        if (produced) {
            ItemEntity result = new ItemEntity(server, x, y, z, new ItemStack(outcome.item().get(), outcome.count()));
            result.setDeltaMovement(0.0, 0.15, 0.0);
            result.setPickUpDelay(10);
            server.addFreshEntity(result);
        }

        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 40, 0.3, 0.3, 0.3, 0.08);
        if (produced) {
            server.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.15, 0.2, 0.15, 0.03);
        }
        server.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                1.0F, produced ? 1.6F : 0.6F);
    }
}
