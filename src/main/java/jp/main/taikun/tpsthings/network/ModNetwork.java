package jp.main.taikun.tpsthings.network;

import jp.main.taikun.tpsthings.Tpsthings;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * この Mod 用の通信路。
 *
 * 画面で変えた設定をサーバへ渡すために要る。クライアントで直接いじっても、
 * 実際に動くのはサーバ側 (BlockEntity や即死対策) なので伝えないと何も起きない。
 */
public final class ModNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(Tpsthings.MODID, "main"))
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .networkProtocolVersion(() -> VERSION)
            .simpleChannel();

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, PacketAcceleratorSettings.class,
                PacketAcceleratorSettings::encode,
                PacketAcceleratorSettings::decode,
                PacketAcceleratorSettings::handle);
        // SugoiMenu から即死対策の設定を読み書きする
        CHANNEL.registerMessage(1, PacketGuardRequest.class,
                PacketGuardRequest::encode,
                PacketGuardRequest::decode,
                PacketGuardRequest::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, PacketGuardChange.class,
                PacketGuardChange::encode,
                PacketGuardChange::decode,
                PacketGuardChange::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, PacketGuardState.class,
                PacketGuardState::encode,
                PacketGuardState::decode,
                PacketGuardState::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 貫通攻撃と儀式の演出。どれも見た目だけで、クライアントから送り返すものは無い
        CHANNEL.registerMessage(4, PacketStrikeEffect.class,
                PacketStrikeEffect::encode,
                PacketStrikeEffect::decode,
                PacketStrikeEffect::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, PacketStrikeReport.class,
                PacketStrikeReport::encode,
                PacketStrikeReport::decode,
                PacketStrikeReport::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6, PacketAbyss.class,
                PacketAbyss::encode,
                PacketAbyss::decode,
                PacketAbyss::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
