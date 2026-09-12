package jp.main.taikun.tpsthings.items;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import jp.main.taikun.tpsthings.damage.PiercingStrike;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ItemOo extends ArmorItem implements WavyNameItem {
    /**
     * 属性マップ。リーチなど Forge の属性 (RegistryObject) はアイテム登録時点では
     * まだレジストリに居ないので、コンストラクタでは組まずに初回参照時に遅延構築する。
     */
    private final com.google.common.base.Supplier<Multimap<Attribute, AttributeModifier>> handModifiers;
    private final com.google.common.base.Supplier<Multimap<Attribute, AttributeModifier>> wornModifiers;

    public ItemOo() {
        super(new ArmorOo(), Type.CHESTPLATE, new Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(1));
        this.handModifiers = com.google.common.base.Suppliers.memoize(() -> {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", Double.NaN, AttributeModifier.Operation.ADDITION));
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", 1000000, AttributeModifier.Operation.ADDITION));
            builder.put(Attributes.ARMOR, new AttributeModifier(UUID.randomUUID(), "Weapon modifier", Integer.MAX_VALUE, AttributeModifier.Operation.ADDITION));
            // リーチ。視野円錐の射程 (VIEW_RANGE) と揃えて、直接クリックでも同じ距離まで届くように
            builder.put(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), new AttributeModifier(UUID.randomUUID(), "Weapon modifier", VIEW_RANGE, AttributeModifier.Operation.ADDITION));
            builder.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), new AttributeModifier(UUID.randomUUID(), "Weapon modifier", VIEW_RANGE, AttributeModifier.Operation.ADDITION));
            // 通常ヒットで相手を吹き飛ばす + 幸運
            builder.put(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(UUID.randomUUID(), "Weapon modifier", 10, AttributeModifier.Operation.ADDITION));
            builder.put(Attributes.LUCK, new AttributeModifier(UUID.randomUUID(), "Weapon modifier", 1024, AttributeModifier.Operation.ADDITION));
            return builder.build();
        });
        this.wornModifiers = com.google.common.base.Suppliers.memoize(() -> {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            // 元の防具ステータス (防具値・防具強度・ノックバック耐性) はそのまま引き継ぐ
            builder.putAll(super.getDefaultAttributeModifiers(EquipmentSlot.CHEST));
            builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(UUID.randomUUID(), "oo bonus", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
            builder.put(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), new AttributeModifier(UUID.randomUUID(), "oo bonus", 1.0, AttributeModifier.Operation.ADDITION));
            return builder.build();
        });
    }


    @Override
    public @NotNull Component getDescription() {
        return Component.translatable("item.tpsthings.oo.description");
    }
    

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return ItemPrism.decorateName(this, stack);
    }

    /**
     * ツールチップの説明行。末尾の「おお」と台詞は属性の行より後ろに置きたいので、
     * ここではなく ItemPoemTooltip が足す。
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @org.jetbrains.annotations.Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        String toolMode = OoToolSettings.read(stack).summary();
        if (toolMode != null) {
            tooltip.add(Component.literal(toolMode).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return java.util.Optional.of(new OoTooltip());
    }

    /**
     * おお専用のツールチップシェーダー (tpsthings:tooltip_oo) のマーカー。
     * 表層〜索引層の地層を降りきった最下段だけが静かな白に戻る。描画は ClientShaderTooltip が担当する。
     */
    public record OoTooltip() implements net.minecraft.world.inventory.tooltip.TooltipComponent {
    }

    /**
     * アイテム形態のカスタム描画 (回転する八面体、プリズムと共用)。
     * 装備時の防具モデルには影響しない。
     */
    @Override
    public void initializeClient(@NotNull java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return jp.main.taikun.tpsthings.gui.PrismItemRenderer.INSTANCE.get();
            }
        });
    }

    public boolean canAttackBlock(BlockState p_43291_, Level p_43292_, BlockPos p_43293_, Player p_43294_) {
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack p_41425_, BlockState p_41426_) {
        return Float.MAX_VALUE;
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState p_41450_) {
        return true;
    }

    /**
     * 耐久を一切減らさない。
     *
     * ダメージを与える側 (hurtAndBreak 等) は必ずこの 2 つを見てから減らすので、
     * ここを折っておけば経路を問わず減らない。耐久値を大きくするだけだと
     * 表示上のバーが出たり、直接 setDamageValue される余地が残る。
     */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot p_43274_) {
        if (p_43274_ == EquipmentSlot.MAINHAND) return this.handModifiers.get();
        if (p_43274_ == EquipmentSlot.CHEST) return this.wornModifiers.get();
        return super.getDefaultAttributeModifiers(p_43274_);
    }
    public static class ArmorOo implements ArmorMaterial {

        @Override
        public int getDurabilityForType(@NotNull Type type) {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getDefenseForType(@NotNull Type p_267168_) {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public @NotNull SoundEvent getEquipSound() {
            return ArmorMaterials.NETHERITE.getEquipSound();
        }

        @Override
        public @NotNull Ingredient getRepairIngredient() {
            return Ingredient.of(Items.NETHERITE_INGOT);
        }

        @Override
        public @NotNull String getName() {
            return "oo";
        }

        @Override
        public float getToughness() {
            return Integer.MAX_VALUE;
        }

        @Override
        public float getKnockbackResistance() {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack p_41453_) {
        return true;
    }
    /** OO を胴に着ているか。装備由来の挙動の判定を 1 箇所にまとめる。 */
    public static boolean isWorn(LivingEntity entity) {
        return entity != null
                && entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.OO.get());
    }

    /** 円錐の長さ。 */
    private static final double VIEW_RANGE = 64.0;

    /**
     * 左クリック (スイング) で、視野円錐に入っている Mob を全て {@link #whenAttack} に通す。
     *
     * 当たり判定を経由しないので、相手がどれだけ速く動いていても関係なく通る。
     */
    @Override
    public boolean onEntitySwing(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        if (entity.level().isClientSide() || !(entity instanceof Player player)) {
            return false;
        }
        // isAlive() は偽装されうる。「死んで見える生者」を取り逃がさないよう、除去の印だけで数える
        List<PiercingStrike.Result> results = new ArrayList<>();
        for (Mob mob : ViewCone.mobsInView(player, VIEW_RANGE, mob -> !mob.isRemoved())) {
            results.add(PiercingStrike.strike(player, mob));
        }
        report(player, results);
        return false; // 通常のスイング挙動はそのまま残す
    }

    /** 直接殴った相手を貫通攻撃に通す。サーバ側からだけ呼ぶ。 */
    public static void whenAttack(Player attacker, Entity target) {
        // エンダードラゴンのような多部位の相手は、殴れるのが部位でも本体を狙う
        Entity body = target instanceof PartEntity<?> part ? part.getParent() : target;
        if (!(body instanceof LivingEntity living) || living.isRemoved()) {
            return;
        }
        report(attacker, List.of(PiercingStrike.strike(attacker, living)));
    }

    /**
     * 一番しぶとかった 1 体が、どの層まで貫通を要したかを本人に知らせる。
     * 何体いても、知りたいのは「どこまで降りる必要があったか」なので。
     * 層を 1 つずつ区切ってアクションバーに出すのはクライアントの StrikeEffects。
     */
    private static void report(Player player, List<PiercingStrike.Result> results) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        results.stream()
                .max(Comparator.comparingInt(PiercingStrike.Result::resistance))
                .ifPresent(worst -> jp.main.taikun.tpsthings.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new jp.main.taikun.tpsthings.network.PacketStrikeReport(
                                results.size(), worst.health(), worst.death(), worst.removal())));
    }
}
