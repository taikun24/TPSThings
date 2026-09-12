package jp.main.taikun.tpsthings.damage;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 保護対象本人へ出ていく「死の報せ」の関所。
 *
 * <p>関所は全部サーバの状態を見張っている。どれも前提は同じで、<b>クライアントは
 * サーバの結果を映しているだけ</b>というもの。その前提が崩れる手がある —
 * サーバの状態を一切変えずに、本人の通信路へ「HP 0」「お前は死んだ」とだけ送る。
 * サーバでは生きているので関所は 1 つも作動しないのに、画面には死亡画面が出る。
 * しかも本当は生きているので、リスポーンを押してもバニラが要求を捨てる = 詰む。
 *
 * <p>だから出口にも関所を置く。判断は新しく作らない — <b>死を拒否する方針なら、
 * 死の報せも拒否する</b>。関所が die を拒否し続ける相手に死亡画面だけ届くのは、
 * どう転んでも辻褄が合わない。
 *
 * <p>読み出し ({@code getHealth}) を偽装された結果としてバニラ自身が送ってしまう
 * 嘘も、同じ口で正される。誰の嘘かは問わない。
 */
public final class OutboundGuard {

    private OutboundGuard() {
    }

    /**
     * この相手へこのパケットを送ってよいか。
     *
     * @return そのまま送るなら引数と同じもの、差し替えるなら別のパケット、
     *         送らないなら null
     */
    public static Packet<?> filter(ServerPlayer receiver, Packet<?> packet) {
        // 平常時と保護対象以外は一切触らない。ここは全パケットが通る一番熱い道
        if (packet instanceof ClientboundSetHealthPacket health) {
            return guarding(receiver) ? correctHealth(receiver, health) : packet;
        }
        if (packet instanceof ClientboundPlayerCombatKillPacket kill) {
            // 本人宛ての「お前は死んだ」だけを見る。他人の死は本人の画面を閉じない
            return kill.getPlayerId() == receiver.getId() && guarding(receiver) ? null : packet;
        }
        if (packet instanceof ClientboundSetEntityDataPacket data) {
            return data.id() == receiver.getId() && guarding(receiver)
                    ? correctData(receiver, data) : packet;
        }
        return packet;
    }

    /**
     * いま死を拒否している相手か。
     *
     * <p>自分の操作 (復帰処理など) で出る分は触らない。拒否の方針そのものが
     * 出ていない (書き戻しも封印も切れている) なら、死は正規の結果なので通す。
     */
    private static boolean guarding(ServerPlayer receiver) {
        return receiver != null && !DamageGuard.isSelfAction()
                && AutoGuard.isProtected(receiver) && GuardContext.isSealedOrReverting();
    }

    /** 本当の HP。箱の値が既に 0 に落とされていたら、最後に見た正の値を使う。 */
    private static float truth(ServerPlayer receiver) {
        float raw = HealthGuard.rawHealth(receiver);
        return raw > 0.0F ? raw : AutoGuard.knownGood(receiver.getUUID());
    }

    private static Packet<?> correctHealth(ServerPlayer receiver, ClientboundSetHealthPacket health) {
        if (health.getHealth() > 0.0F) {
            return health;
        }
        float truth = truth(receiver);
        if (truth <= 0.0F) {
            return health; // 戻す先が分からない。嘘だと言い切れないので通す
        }
        return new ClientboundSetHealthPacket(truth, health.getFood(), health.getSaturation());
    }

    /**
     * 同期データの塊の中に混ぜられた「HP 0」を直す。
     *
     * 体力バーの数字は本人宛ての HP パケットで決まるが、生死の判定は同期データの側を
     * 読む相手も居る。片方だけ正してももう片方から死ぬ。
     */
    private static Packet<?> correctData(ServerPlayer receiver, ClientboundSetEntityDataPacket data) {
        EntityDataAccessor<Float> key = HealthGuard.healthKeyOrNull();
        List<SynchedEntityData.DataValue<?>> values = data.packedItems();
        if (key == null || values == null) {
            return data;
        }
        float truth = truth(receiver);
        if (truth <= 0.0F) {
            return data;
        }
        List<SynchedEntityData.DataValue<?>> corrected = null;
        for (int index = 0; index < values.size(); index++) {
            SynchedEntityData.DataValue<?> value = values.get(index);
            if (value.id() != key.getId() || !(value.value() instanceof Float health) || health > 0.0F) {
                continue;
            }
            if (corrected == null) {
                corrected = new ArrayList<>(values);
            }
            corrected.set(index, replace(value, truth));
        }
        return corrected == null ? data : new ClientboundSetEntityDataPacket(data.id(), corrected);
    }

    /** 同じ鍵・同じ書式のまま、値だけ差し替える。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SynchedEntityData.DataValue<?> replace(SynchedEntityData.DataValue<?> value,
                                                          float health) {
        return new SynchedEntityData.DataValue(value.id(), value.serializer(), health);
    }
}
