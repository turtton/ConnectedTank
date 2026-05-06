# ConnectedTank

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

接続可能でアップグレード可能な液体タンクを追加する Minecraft Fabric MOD です。

タンクを隣接して設置すると接続され、容量を合算した 1 つのストレージとして機能します。

[English](README.md)

## 特徴

### ティア制タンクシステム

容量の異なる 7 段階のタンク:

| ティア | 容量倍率 | 備考 |
|--------|---------|------|
| Base | 1x | |
| Stone | 2x | |
| Copper | 4x | |
| Iron | 8x | |
| Gold | 16x | |
| Diamond | 32x | |
| Netherite | 64x | 耐火 |

基本容量はデフォルトで 32 バケツ (設定で変更可能)。各タンクの容量 = 基本容量 × 倍率。

### 接続型ストレージ

- タンクを隣接して設置するとグループを形成し、容量を合算した 1 つの液体プールとして動作
- 接続状態を視覚的に表示 (接続面の境界が透明化)

### 液体操作

- バケツやボトルでタンクに直接液体を出し入れ可能
- 全種類の液体に対応 (水、溶岩など)
- タンク破壊時は保持する液体の按分量をドロップ
- クラフトによるティアアップグレード時に液体を保持

### ビジュアル

- 透明ブロックでリアルタイムの液体量レンダリング
- タンクを通して光が透過
- ティアごとに異なるフレーム・側面テクスチャ

## 動作要件

- Minecraft 1.21.8
- Fabric Loader >= 0.17.2
- Fabric API
- Fabric Language Kotlin

## オプション依存

- [Jade](https://modrinth.com/mod/jade) — タンクの液体情報をツールチップに表示
- [ModMenu](https://modrinth.com/mod/modmenu) + [YACL](https://modrinth.com/mod/yacl) — ゲーム内設定画面

## 設定

### サーバー設定 (`config/connectedtank/server.json`)

```json
{
  "tankBucketCapacity": 32,
  "tierMultipliers": {
    "BASE": 1,
    "STONE": 2,
    "COPPER": 4,
    "IRON": 8,
    "GOLD": 16,
    "DIAMOND": 32,
    "NETHERITE": 64
  }
}
```

- `tankBucketCapacity` — 基本容量 (バケツ単位、1〜256)
- `tierMultipliers` — ティアごとの容量倍率を上書き

## ビルド

```bash
./gradlew build
```

ビルド成果物は `build/libs/` に出力されます。

## ライセンス

[MIT](LICENSE)
