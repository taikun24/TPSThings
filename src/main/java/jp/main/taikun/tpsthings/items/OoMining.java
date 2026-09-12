package jp.main.taikun.tpsthings.items;

import jp.main.taikun.tpsthings.Tpsthings;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * おおでのブロック破壊。設定 ({@link OoToolSettings}) に従って、範囲破壊・岩盤破壊・
 * ドロップの種類・直接回収を行う。サーバ側だけで動く。
 *
 * <p>巻き込むブロック 1 つ 1 つについて {@link BlockEvent.BreakEvent} を投げ直してから壊す。
 * 保護系の Mod (土地保護など) が止めた場所は、範囲の中でも壊さない。
 */
@Mod.EventBusSubscriber(modid = Tpsthings.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OoMining {

    /** 権限確認のために自分で投げた BreakEvent に、もう一度反応しないための印。 */
    private static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 破壊の粒子を出すブロック数の上限。9×9×9 を全部出すと通信が詰まる。 */
    private static final int MAX_PARTICLES = 64;

    private OoMining() {
    }

    /** 通常の採掘で壊れる瞬間。最低優先度なので、他の Mod が止めた破壊はここまで届かない。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (CHECKING.get()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || player.isCreative()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ModItems.OO.get())) {
            return;
        }
        OoToolSettings.Mode mode = OoToolSettings.read(tool);
        if (mode.isVanilla()) {
            return;
        }
        // 起点も自前で壊す。ドロップの種類と回収先を、範囲の全ブロックで揃えるため
        event.setCanceled(true);
        harvestArea(player, level, tool, mode, event.getPos(), hitFace(player, event.getPos()), true);
    }

    /**
     * 叩いた瞬間。硬度が負 (岩盤・バリアなど) のブロックは採掘が始まらず BreakEvent も来ないので、
     * 岩盤破壊はここで受け持つ。普通に壊せるブロックは通常の採掘 ({@link #onBreak}) に任せる。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || player.isCreative()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ModItems.OO.get())) {
            return;
        }
        OoToolSettings.Mode mode = OoToolSettings.read(tool);
        BlockPos pos = event.getPos();
        if (!mode.bedrock() || level.getBlockState(pos).getDestroySpeed(level, pos) >= 0.0F) {
            return;
        }
        event.setCanceled(true);
        Direction face = event.getFace() != null ? event.getFace() : hitFace(player, pos);
        harvestArea(player, level, tool, mode, pos, face, false);
    }

    /**
     * @param originApproved 起点が既に BreakEvent を通っているか (通常の採掘から来たとき)
     */
    private static void harvestArea(ServerPlayer player, ServerLevel level, ItemStack tool,
                                    OoToolSettings.Mode mode, BlockPos origin, Direction face,
                                    boolean originApproved) {
        ItemStack harvestTool = mode.harvestTool(tool);
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, harvestTool);
        int silk = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, harvestTool);
        int experience = 0;
        int particles = 0;

        for (BlockPos pos : mode.positions(origin, face)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                continue;
            }
            boolean approved = originApproved && pos.equals(origin);
            if (!approved) {
                if (state.getDestroySpeed(level, pos) < 0.0F && !mode.bedrock()) {
                    continue;
                }
                if (!mayBreak(player, level, pos, state)) {
                    continue;
                }
            }
            int gained = harvest(player, level, pos, state, harvestTool, mode.collect(), fortune, silk);
            if (gained < 0) {
                continue;
            }
            experience += gained;
            // 起点は本人にも粒子を見せる (破壊を自前でやったので、本人の画面では予測されていない)
            if (particles++ < MAX_PARTICLES) {
                level.levelEvent(2001, pos, Block.getId(state));
            }
        }

        if (experience > 0) {
            if (mode.collect()) {
                player.giveExperiencePoints(experience);
            } else {
                ExperienceOrb.award(level, Vec3.atCenterOf(origin), experience);
            }
        }
    }

    /**
     * 1 ブロック壊してドロップを配る。
     *
     * @return 落とすはずだった経験値。壊せなかったら -1
     */
    private static int harvest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
                               ItemStack tool, boolean collect, int fortune, int silk) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // ドロップと経験値はブロックが消える前に決める (中身を持つブロックは消えると読めなくなる)
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, player, tool);
        int experience = state.getExpDrop(level, level.random, pos, fortune, silk);
        boolean unbreakable = state.getDestroySpeed(level, pos) < 0.0F;

        if (!state.onDestroyedByPlayer(level, pos, player, true, level.getFluidState(pos))) {
            return -1;
        }
        state.getBlock().destroy(level, pos, state);
        state.spawnAfterBreak(level, pos, tool, false);
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));

        // 本来壊せないブロックはたいていルートテーブルを持たない。壊したならブロックそのものを渡す
        if (unbreakable && drops.isEmpty() && state.getBlock().asItem() != Items.AIR) {
            drops = List.of(new ItemStack(state.getBlock()));
        }
        for (ItemStack drop : drops) {
            if (!collect) {
                Block.popResource(level, pos, drop);
            } else if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
        }
        return experience;
    }

    /** 範囲に巻き込んだブロックを、この人が壊してよいか。保護系の判断は BreakEvent に委ねる。 */
    private static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
            return false;
        }
        CHECKING.set(Boolean.TRUE);
        try {
            BlockEvent.BreakEvent check = new BlockEvent.BreakEvent(level, pos, state, player);
            MinecraftForge.EVENT_BUS.post(check);
            return !check.isCanceled();
        } finally {
            CHECKING.set(Boolean.FALSE);
        }
    }

    /** 叩いた面。視線の先が同じブロックならその面、取れなければ視線の向きから決める。 */
    private static Direction hitFace(ServerPlayer player, BlockPos pos) {
        HitResult hit = player.pick(player.getBlockReach(), 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
            return blockHit.getDirection();
        }
        Vec3 look = player.getLookAngle();
        return Direction.getNearest(look.x, look.y, look.z).getOpposite();
    }
}
