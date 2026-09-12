# TPSThings

Minecraft 1.20.1 / Forge 47.4.20 向けの Mod。

TPS (Ticks Per Second) を題材にした時間系の機械・道具と、**即死攻撃 (貫通攻撃) に対する多層の防御**を持つ。

## 中身

### 時間系
- **TPS 発電機 / 時間加速器 / 時間流束コレクター** — TPS と時間そのものを資源として扱う機械群
- **プリズム**、**加速の杖**、**蛍光灯** などの道具
- サーバ tick のプロファイラ (`TickProfiler`)

### おお
胸に着ける防具。着ている間は飛行が付き、後述の防御機構の保護対象になる。
左クリックで視野の円錐に入った Mob へ届く攻撃を持つ。

### 防御機構 (DamageGuard)
即死攻撃を「**どの層まで貫通できるか**」で測る枠組みで作られている。

| 層 | 中身 | 関所 |
|---|---|---|
| 表層 | 交戦の不成立 | |
| 減算層 | ダメージ減算 | |
| 刹那層 | 無敵時間 | |
| 不可侵層 | 無敵判定 | |
| 挙動層・合議層 | `hurt()` / Forge イベント | `LivingEntity#hurt` HEAD |
| 生値層 | HP の直接上書き | `SynchedEntityData#set(DATA_HEALTH_ID)` HEAD |
| 虚偽層 | `getHealth()` の偽装 | 同 `get` HEAD (封印中のみ) |
| 終焉層 | 死亡処理 | `LivingEntity#die` / `ServerPlayer#die` HEAD |
| 抹消層 | 除去処理 | `tickDeath` / `Entity#remove` HEAD |
| 索引層 | ワールド登録層 | `EntityLookup#remove` HEAD + 状態側の巡回 |

上から下へ 1 段ずつ降りる。中間素材もこの順に並んでいて、最後は対消滅炉の儀式で
減算層から索引層までを 1 つずつ投げ込むと「おお」になる。

「不死」も「即死」も結果でしかなく、本質は**貫通した層の深さ**という立場を取っている。
特定の Mod を名指しした対策は入れていない — どれも一般の経路に対する関所として書いてある。

操作は `/tpsthings damage ...` から。主なもの:

```
/tpsthings damage watch true        # ダメージの呼び出し元を記録する
/tpsthings damage log list          # 記録を見る
/tpsthings damage stop block <署名> # 名指しで止める
/tpsthings damage protect true      # 自分を装備に関係なく保護する
/tpsthings damage set seal true     # 封印 (HP の減少と殺意のある削除を拒否)
/tpsthings damage dummy             # おおを着た的を出す (試し撃ち用)
/tpsthings damage agentscan         # 外部 Java Agent の痕跡を調べる
```

### Sugoi Menu
キーを押している間だけ開く HUD のメニュー。上の設定をゲーム内から切り替えられる。

## ビルド

```
./gradlew build
```

`build/libs/tpsthings-<version>.jar` が出来る。

## ライセンス

All Rights Reserved.
