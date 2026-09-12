package jp.main.taikun.tpsthings.items;

import jp.main.taikun.tpsthings.damage.GuardSettings;
import jp.main.taikun.tpsthings.registries.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * おおの道具としての設定 (範囲破壊・岩盤破壊・ドロップの種類・直接回収)。
 *
 * <p>設定はプレイヤーではなく<b>おおのアイテム自身の NBT</b> に持たせる。
 * 別のおおなら別の設定で、しまって取り出しても設定は残る。
 *
 * <p>メニュー (SugoiMenu) には即死対策の設定と並べて出すが、こちらは自分の道具の設定なので
 * OP 権限は要らない。項目の id は {@link #PREFIX} で始め、受け口で振り分ける。
 */
public final class OoToolSettings {

    public static final String PREFIX = "oo_";
    private static final String TAG = "OoTool";
    private static final int[] WIDTHS = {1, 3, 5, 9};
    private static final int[] DEPTHS = {1, 3, 5, 9};
    private static final int FORTUNE_LEVEL = 5;

    public enum Drop {
        NORMAL("通常"),
        SILK("シルクタッチ"),
        FORTUNE("幸運 V");

        private final String label;

        Drop(String label) {
            this.label = label;
        }
    }

    public record Mode(int width, int depth, boolean bedrock, Drop drop, boolean collect) {

        private static final Mode DEFAULT = new Mode(1, 1, false, Drop.NORMAL, false);

        /** 範囲もドロップも手を加えないなら、破壊はバニラの経路に任せてよい。 */
        public boolean isVanilla() {
            return width == 1 && depth == 1 && drop == Drop.NORMAL && !collect;
        }

        /** 叩いた面に沿って width × width、面から奥へ depth 段。起点を含む。 */
        public List<BlockPos> positions(BlockPos origin, Direction face) {
            List<BlockPos> result = new ArrayList<>(width * width * depth);
            int radius = (width - 1) / 2;
            Direction into = face.getOpposite();
            for (int d = 0; d < depth; d++) {
                BlockPos layer = origin.relative(into, d);
                for (int a = -radius; a <= radius; a++) {
                    for (int b = -radius; b <= radius; b++) {
                        result.add(switch (face.getAxis()) {
                            case X -> layer.offset(0, a, b);
                            case Y -> layer.offset(a, 0, b);
                            case Z -> layer.offset(a, b, 0);
                        });
                    }
                }
            }
            return result;
        }

        /**
         * ドロップの計算に使う道具。おお本体にエンチャントを付けずに、写しにだけ付ける
         * (本体に付けると、設定を戻したときに剥がす手間と、表示の汚れが残る)。
         */
        public ItemStack harvestTool(ItemStack tool) {
            if (drop == Drop.NORMAL) {
                return tool;
            }
            ItemStack copy = tool.copy();
            if (drop == Drop.SILK) {
                copy.enchant(Enchantments.SILK_TOUCH, 1);
            } else {
                copy.enchant(Enchantments.BLOCK_FORTUNE, FORTUNE_LEVEL);
            }
            return copy;
        }

        /** ツールチップに出す 1 行。何も有効でなければ null。 */
        public @Nullable String summary() {
            List<String> parts = new ArrayList<>();
            if (width > 1 || depth > 1) {
                parts.add("範囲 " + width + "×" + width + "×" + depth);
            }
            if (bedrock) {
                parts.add("岩盤破壊");
            }
            if (drop != Drop.NORMAL) {
                parts.add(drop.label);
            }
            if (collect) {
                parts.add("直接回収");
            }
            return parts.isEmpty() ? null : String.join(" · ", parts);
        }
    }

    private OoToolSettings() {
    }

    // ---- NBT ----------------------------------------------------------------------

    public static Mode read(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null) {
            return Mode.DEFAULT;
        }
        Drop[] drops = Drop.values();
        return new Mode(
                pick(WIDTHS, tag.getInt("width")),
                pick(DEPTHS, tag.getInt("depth")),
                tag.getBoolean("bedrock"),
                drops[Math.floorMod(tag.getInt("drop"), drops.length)],
                tag.getBoolean("collect"));
    }

    private static void write(ItemStack stack, Mode mode) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putInt("width", mode.width());
        tag.putInt("depth", mode.depth());
        tag.putBoolean("bedrock", mode.bedrock());
        tag.putInt("drop", mode.drop().ordinal());
        tag.putBoolean("collect", mode.collect());
    }

    /** 選択肢に無い値 (壊れた NBT など) は先頭に倒す。 */
    private static int pick(int[] options, int value) {
        for (int option : options) {
            if (option == value) {
                return option;
            }
        }
        return options[0];
    }

    private static int step(int[] options, int current, int direction) {
        int index = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) {
                index = i;
            }
        }
        return options[Math.floorMod(index + direction, options.length)];
    }

    /** 設定を読み書きする対象のおお。手 → 胴 → インベントリの順に探す。 */
    public static ItemStack find(Player player) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem(),
                player.getItemBySlot(EquipmentSlot.CHEST))) {
            if (stack.is(ModItems.OO.get())) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.OO.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- メニュー -------------------------------------------------------------------

    public static List<GuardSettings.Entry> snapshot(ServerPlayer player) {
        ItemStack oo = find(player);
        if (oo.isEmpty()) {
            return List.of(new GuardSettings.Entry("", "おおの機能", "未所持", GuardSettings.OFF,
                    "おおを持つか着ると、範囲破壊などを選べます"));
        }
        Mode mode = read(oo);
        boolean area = mode.width() > 1 || mode.depth() > 1;
        return List.of(
                new GuardSettings.Entry(PREFIX + "width", "範囲破壊: 幅",
                        mode.width() == 1 ? "OFF" : mode.width() + "×" + mode.width(),
                        mode.width() > 1 ? GuardSettings.ON : GuardSettings.OFF,
                        "叩いた面に沿ってまとめて壊す。右クリックで広く、Shift+右クリックで狭く"),
                new GuardSettings.Entry(PREFIX + "depth", "範囲破壊: 奥行き", mode.depth() + " 段",
                        mode.depth() > 1 ? GuardSettings.ON : GuardSettings.OFF,
                        "叩いた面から奥へ何段まで壊すか。右クリックで深く、Shift+右クリックで浅く"
                                + (area ? "" : " (幅と奥行きが両方 1 なら範囲破壊は OFF)")),
                toggle(PREFIX + "bedrock", "岩盤破壊", mode.bedrock(), GuardSettings.WARN,
                        "岩盤やバリアなど、本来壊せないブロックも叩いた瞬間に壊す (ブロックそのものが落ちる)"),
                new GuardSettings.Entry(PREFIX + "drop", "ドロップ", mode.drop().label,
                        mode.drop() == Drop.NORMAL ? GuardSettings.OFF : GuardSettings.ON,
                        "通常 / シルクタッチ / 幸運 V を切り替える。Shift+右クリックで逆順"),
                toggle(PREFIX + "collect", "直接回収", mode.collect(), GuardSettings.ON,
                        "壊したブロックのドロップと経験値を、その場に落とさずインベントリへ入れる"));
    }

    private static GuardSettings.Entry toggle(String id, String label, boolean value, int onColor, String description) {
        return new GuardSettings.Entry(id, label, value ? "ON" : "OFF", value ? onColor : GuardSettings.OFF, description);
    }

    /**
     * 1 項目を操作する。
     *
     * @return 画面に出す結果の文。知らない id なら null
     */
    public static @Nullable String apply(ServerPlayer player, String id, int direction) {
        ItemStack oo = find(player);
        if (oo.isEmpty()) {
            return "おおを持っていません";
        }
        Mode mode = read(oo);
        Mode next;
        String result;
        switch (id) {
            case PREFIX + "width" -> {
                int width = step(WIDTHS, mode.width(), direction);
                next = new Mode(width, mode.depth(), mode.bedrock(), mode.drop(), mode.collect());
                result = "範囲破壊の幅: " + (width == 1 ? "OFF" : width + "×" + width);
            }
            case PREFIX + "depth" -> {
                int depth = step(DEPTHS, mode.depth(), direction);
                next = new Mode(mode.width(), depth, mode.bedrock(), mode.drop(), mode.collect());
                result = "範囲破壊の奥行き: " + depth + " 段";
            }
            case PREFIX + "bedrock" -> {
                next = new Mode(mode.width(), mode.depth(), !mode.bedrock(), mode.drop(), mode.collect());
                result = "岩盤破壊: " + (next.bedrock() ? "ON" : "OFF");
            }
            case PREFIX + "drop" -> {
                Drop[] drops = Drop.values();
                Drop drop = drops[Math.floorMod(mode.drop().ordinal() + direction, drops.length)];
                next = new Mode(mode.width(), mode.depth(), mode.bedrock(), drop, mode.collect());
                result = "ドロップ: " + drop.label;
            }
            case PREFIX + "collect" -> {
                next = new Mode(mode.width(), mode.depth(), mode.bedrock(), mode.drop(), !mode.collect());
                result = "直接回収: " + (next.collect() ? "ON" : "OFF");
            }
            default -> {
                return null;
            }
        }
        write(oo, next);
        return result;
    }
}
