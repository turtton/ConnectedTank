# /// script
# requires-python = ">=3.12"
# dependencies = [
#     "pillow>=11.0.0",
# ]
# ///
"""Generate tank block textures for each tier.

Tier IDs must match TankTier.kt enum values.
When adding/changing tiers, update both this file and TankTier.kt.
"""

import os
from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = REPO_ROOT / "src" / "main" / "resources" / "assets" / "connectedtank" / "textures" / "block"

# Tier definitions: (id, frame_color_rgb, gauge_color_rgb)
# Colors based on the Minecraft material used for each tier
TIERS = [
    ("connected_tank", (139, 105, 20), (160, 130, 50)),  # Oak wood brown
    ("stone_connected_tank", (127, 127, 127), (150, 150, 150)),  # Stone gray
    ("copper_connected_tank", (184, 115, 51), (210, 140, 70)),  # Copper orange
    ("iron_connected_tank", (216, 216, 216), (230, 230, 230)),  # Iron silver
    ("gold_connected_tank", (255, 215, 0), (255, 235, 80)),  # Gold yellow
    ("diamond_connected_tank", (90, 234, 234), (130, 245, 245)),  # Diamond cyan
    ("netherite_connected_tank", (61, 49, 41), (90, 72, 60)),  # Netherite dark
]


def create_side_texture(gauge_color):
    """Create a 16x16 side texture with gauge marks only (no border)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    ga = gauge_color + (255,)

    # Gauge marks on left edge (x=0)
    # Major marks at y=4, y=8, y=12 (3px wide)
    for tick_y in [4, 8, 12]:
        for dx in range(3):
            img.putpixel((dx, tick_y), ga)

    # Minor marks at y=2, y=6, y=10, y=14 (2px wide)
    for tick_y in [2, 6, 10, 14]:
        for dx in range(2):
            img.putpixel((dx, tick_y), ga)

    return img


def create_frame_texture(frame_color):
    """Create a 16x16 solid color texture for border overlays."""
    img = Image.new("RGBA", (16, 16), frame_color + (255,))
    return img


def create_item_texture(frame_color, gauge_color):
    """Create a 16x16 item icon texture with borders and gauge marks."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    fr = frame_color + (255,)
    ga = gauge_color + (255,)

    # Frame border (1px on all sides)
    for i in range(16):
        img.putpixel((i, 0), fr)
        img.putpixel((i, 15), fr)
        img.putpixel((0, i), fr)
        img.putpixel((15, i), fr)

    # Gauge marks on left inner edge (x=1)
    for tick_y in [4, 8, 12]:
        for dx in range(3):
            img.putpixel((1 + dx, tick_y), ga)

    for tick_y in [2, 6, 10, 14]:
        for dx in range(2):
            img.putpixel((1 + dx, tick_y), ga)

    return img


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    for tier_id, frame_color, gauge_color in TIERS:
        side = create_side_texture(gauge_color)
        frame = create_frame_texture(frame_color)
        item = create_item_texture(frame_color, gauge_color)

        side.save(OUTPUT_DIR / f"{tier_id}_side.png")
        frame.save(OUTPUT_DIR / f"{tier_id}_frame.png")
        item.save(OUTPUT_DIR / f"{tier_id}_item.png")
        print(f"Generated: {tier_id}")

    print("Done!")


if __name__ == "__main__":
    main()
