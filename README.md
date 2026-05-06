# ConnectedTank

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Connectable and upgradable fluid storage tanks for Minecraft.

Place tanks next to each other to connect them into a single, larger storage pool.

[日本語](README.ja.md)

## Features

### Tiered Tank System

7 tiers of tanks with increasing capacity:

| Tier | Capacity Multiplier | Notes |
|------|-------------------|-------|
| Base | 1x | |
| Stone | 2x | |
| Copper | 4x | |
| Iron | 8x | |
| Gold | 16x | |
| Diamond | 32x | |
| Netherite | 64x | Fireproof |

Base capacity is 32 buckets by default (configurable). Each tank's capacity = base × multiplier.

### Connected Storage

- Place tanks adjacent to each other to form a connected group
- Connected tanks combine their capacity into a single fluid pool
- Visual connection indicators show linked sides
- Internal borders between connected tanks become invisible

### Fluid Interaction

- Use buckets or bottles directly on tanks to fill/drain
- Supports any fluid type (water, lava, etc.)
- Breaking a tank drops it with its proportional share of stored fluid
- Upgrading a tank tier via crafting preserves the fluid contents

### Visual

- Transparent block with real-time fluid level rendering
- Light passes through tanks
- Tier-specific frame and side textures

## Requirements

- Minecraft 1.21.8
- Fabric Loader >= 0.17.2
- Fabric API
- Fabric Language Kotlin

## Optional Dependencies

- [Jade](https://modrinth.com/mod/jade) - Fluid info tooltip on tanks
- [ModMenu](https://modrinth.com/mod/modmenu) + [YACL](https://modrinth.com/mod/yacl) - In-game configuration screen

## Configuration

### Server (`config/connectedtank/server.json`)

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

- `tankBucketCapacity` — Base capacity in buckets (1–256)
- `tierMultipliers` — Override capacity multiplier per tier

## Building

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

[MIT](LICENSE)
