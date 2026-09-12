package jp.main.taikun.tpsthings.mixin;

import jp.main.taikun.tpsthings.damage.AttachGuard;
import jp.main.taikun.tpsthings.damage.MethodDisabler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 自前 agent のアタッチを Mixin のロードフェーズまで前倒しする。
 *
 * <p>敵対 Mod は Mixin プラグインの最早期に自己アタッチして全クラスを書き換える。
 * こちらが「最初のダメージが来てから」遅延アタッチしていると、<b>後入りは全部
 * 書き換え済みの世界を見る</b>ことになり、retransform の競争に出遅れる。
 * ここで {@code onLoad} に相乗りしておけば、多くのゲームクラスがロードされる前に
 * 自前の変換器を登録でき、競争に最初から参加できる。副産物として
 * {@code ALLOW_ATTACH_SELF} を折る前の状態 (=敵が先に折ったか) も最早期に記録される。
 *
 * <p><b>この段で例外を投げると Mixin のロードごと壊す。</b> だから中の失敗は
 * すべて握って絶対に投げない。ここは「守りを前倒しする」ためのフックであって、
 * ここでゲームを落としては本末転倒。アタッチに失敗しても、従来どおり最初の
 * disable 時に {@link MethodDisabler#ensureReady()} が再挑戦するので損はしない。
 *
 * <p>Mixin の適用そのものには一切干渉しない — 変換の判断系メソッドはすべて既定
 * (全許可・除外なし) を返す。あくまで {@code onLoad} のタイミングを間借りするだけ。
 */
public final class AgentBootstrapPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("tpsthings-agent-bootstrap");

    @Override
    public void onLoad(String mixinPackage) {
        // フル自己アタッチをこの最早期フェーズで試みる。attach は多数のクラスロードを
        // 誘発し、Mixin が変換中の再入になり得るので、何が起きても投げないよう全部握る。
        try {
            String failure = MethodDisabler.ensureReady();
            if (failure == null) {
                LOGGER.info("[tpsthings] 自前 agent を Mixin ロード時に確保しました (変換器を最早期に登録)");
                logEarlyTraces();
            } else {
                LOGGER.warn("[tpsthings] Mixin ロード時の agent 確保に失敗 (後で再挑戦します): {}", failure);
            }
        } catch (Throwable t) {
            // ここで落ちると Mixin ロードごと巻き込む。握って続行する
            LOGGER.warn("[tpsthings] Mixin ロード時の agent 確保で例外 (握って続行): {}", String.valueOf(t));
        }
    }

    /** 確保できたので、この早い段階で見える足跡だけログに残す (深掘りは走らせない)。 */
    private void logEarlyTraces() {
        try {
            List<String> traces = AttachGuard.scan();
            for (String trace : traces) {
                LOGGER.warn("[tpsthings][agent!] {}", trace);
            }
        } catch (Throwable t) {
            // 検知の失敗はゲームに関係ない。黙って続行
        }
    }

    // ---- 以下は Mixin 適用に干渉しないための既定実装 ----------------------------

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
