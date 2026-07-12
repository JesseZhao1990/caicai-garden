#!/usr/bin/env python3
import json
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path("design/imagegen-assets/v2")
TRANSPARENT = ROOT / "transparent"
SPRITES = ROOT / "sprites"
SPRITE_SIZE = 512
SPRITE_CONTENT = 448
STAGES = ["seedling", "young", "mature", "harvest"]


SPECS = [
    {
        "path": TRANSPARENT / "crops-no-soil-leafy-atlas-transparent.png",
        "kind": "crop",
        "group": "leafy",
        "rows": 4,
        "cols": 6,
        "names": ["lettuce", "cabbage", "bok_choy", "spinach", "kale", "celery"],
    },
    {
        "path": TRANSPARENT / "crops-no-soil-fruiting-atlas-transparent.png",
        "kind": "crop",
        "group": "fruiting",
        "rows": 4,
        "cols": 6,
        "names": ["tomato", "cherry_tomato", "chili_pepper", "bell_pepper", "eggplant", "okra"],
    },
    {
        "path": TRANSPARENT / "crops-no-soil-vine-legume-atlas-transparent.png",
        "kind": "crop",
        "group": "vine_legume",
        "rows": 4,
        "cols": 6,
        "names": ["cucumber", "pumpkin", "zucchini", "bitter_melon", "green_bean", "snow_pea"],
    },
    {
        "path": TRANSPARENT / "crops-no-soil-root-bulb-atlas-transparent.png",
        "kind": "crop",
        "group": "root_bulb",
        "rows": 4,
        "cols": 6,
        "names": ["carrot", "daikon", "potato", "sweet_potato", "onion", "garlic"],
    },
    {
        "path": TRANSPARENT / "crops-no-soil-herb-extra-atlas-transparent.png",
        "kind": "crop",
        "group": "herb_extra",
        "rows": 4,
        "cols": 6,
        "names": ["basil", "mint", "cilantro", "scallion", "strawberry", "corn"],
    },
    {
        "path": TRANSPARENT / "terrain-grid-atlas-transparent.png",
        "kind": "terrain",
        "group": "terrain",
        "rows": 4,
        "cols": 5,
        "names": [
            "grass_tile",
            "empty_soil_tile",
            "raised_bed_empty",
            "raised_bed_soil",
            "selected_frame",
            "watered_soil_tile",
            "mulched_bed_tile",
            "straw_bed_tile",
            "compost_soil_tile",
            "disabled_grass_tile",
            "stone_path_tile",
            "gravel_path_tile",
            "wood_plank_path_tile",
            "flower_border_tile",
            "grass_edge_corner_tile",
            "mini_8x8_grid",
            "field_grid_overlay",
            "target_cell_frame",
            "valid_placement_frame",
            "invalid_placement_frame",
        ],
    },
    {
        "path": TRANSPARENT / "structures-facilities-atlas-transparent.png",
        "kind": "structure",
        "group": "structures",
        "rows": 4,
        "cols": 5,
        "names": [
            "greenhouse",
            "tool_shed",
            "wood_fence_straight",
            "wood_fence_corner",
            "garden_gate",
            "bamboo_trellis",
            "arch_trellis",
            "tomato_stakes",
            "irrigation_sprinkler",
            "rain_barrel",
            "compost_bin",
            "blank_wooden_sign",
            "blank_label_stake",
            "watering_can",
            "hose_coil",
            "seed_tray",
            "harvest_basket",
            "pruning_shears",
            "shovel_rake_set",
            "garden_lamp",
        ],
    },
    {
        "path": TRANSPARENT / "status-widgets-atlas-transparent.png",
        "kind": "widget",
        "group": "widgets",
        "rows": 4,
        "cols": 5,
        "names": [
            "water_badge",
            "fertilizer_badge",
            "harvest_badge",
            "growth_badge",
            "heat_badge",
            "rain_badge",
            "pest_warning_badge",
            "disease_warning_badge",
            "pruning_badge",
            "photo_badge",
            "note_record_badge",
            "calendar_badge",
            "mature_ready_badge",
            "low_water_badge",
            "temperature_badge",
            "selection_ring",
            "drag_handle_arrows",
            "rotate_arrow",
            "scale_widget",
            "progress_gauge",
        ],
    },
    {
        "path": TRANSPARENT / "environment-decor-atlas-transparent.png",
        "kind": "environment",
        "group": "environment",
        "rows": 4,
        "cols": 5,
        "names": [
            "small_fruit_tree",
            "leafy_shrub",
            "pink_flower_bush",
            "yellow_flower_bush",
            "lavender_clump",
            "round_stone",
            "rock_cluster",
            "stepping_stones",
            "small_pond",
            "wooden_border_log",
            "hedge_straight",
            "hedge_corner",
            "grass_tuft_cluster",
            "wildflower_patch",
            "wheelbarrow",
            "seed_packet_blank",
            "burlap_sack",
            "empty_clay_pot",
            "wooden_crate",
            "birdhouse_no_bird",
        ],
    },
    {
        "path": TRANSPARENT / "effects-overlays-atlas-transparent.png",
        "kind": "effect",
        "group": "effects",
        "rows": 4,
        "cols": 5,
        "names": [
            "sunlight_glow",
            "soft_shadow_blob",
            "water_splash_droplets",
            "dew_sparkles",
            "growth_sparkles",
            "rain_streak_patch",
            "mist_puff",
            "heat_wave_shimmer",
            "frost_sparkles",
            "wind_swirl",
            "moisture_blue_aura",
            "harvest_glow_ring",
            "valid_placement_glow",
            "invalid_placement_glow",
            "selected_golden_glow",
            "leaf_fall_particles",
            "flower_petal_particles",
            "fertilizer_sparkles",
            "pest_warning_pulse",
            "disease_warning_pulse",
        ],
    },
]


def remove_small_components(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    width, height = image.size
    alpha = image.getchannel("A")
    data = list(alpha.getdata())
    seen = bytearray(width * height)
    components = []
    directions = ((1, 0), (-1, 0), (0, 1), (0, -1))

    for index, value in enumerate(data):
        if value <= 12 or seen[index]:
            continue
        queue = deque([index])
        seen[index] = 1
        pixels = []
        while queue:
            current = queue.popleft()
            pixels.append(current)
            x = current % width
            y = current // width
            for dx, dy in directions:
                nx = x + dx
                ny = y + dy
                if nx < 0 or nx >= width or ny < 0 or ny >= height:
                    continue
                next_index = ny * width + nx
                if seen[next_index] or data[next_index] <= 12:
                    continue
                seen[next_index] = 1
                queue.append(next_index)
        components.append(pixels)

    if not components:
        return image

    max_area = max(len(component) for component in components)
    min_area = max(40, int(max_area * 0.0025))
    keep = bytearray(width * height)
    for component in components:
        if len(component) >= min_area:
            for index in component:
                keep[index] = 1

    pixels = image.load()
    for index, flag in enumerate(keep):
        if flag:
            continue
        x = index % width
        y = index // width
        red, green, blue, alpha_value = pixels[x, y]
        if alpha_value:
            pixels[x, y] = (red, green, blue, 0)
    return image


def crop_cell(atlas: Image.Image, left: int, top: int, right: int, bottom: int) -> Image.Image | None:
    cell = remove_small_components(atlas.crop((left, top, right, bottom)))
    bbox = cell.getchannel("A").getbbox()
    if not bbox:
        return None

    padding = 20
    l, t, r, b = bbox
    l = max(0, l - padding)
    t = max(0, t - padding)
    r = min(cell.width, r + padding)
    b = min(cell.height, b + padding)
    cropped = remove_small_components(cell.crop((l, t, r, b)))

    canvas = Image.new("RGBA", (SPRITE_SIZE, SPRITE_SIZE), (0, 0, 0, 0))
    scale = min(SPRITE_CONTENT / cropped.width, SPRITE_CONTENT / cropped.height, 1.0)
    if scale < 1.0:
        cropped = cropped.resize(
            (max(1, int(cropped.width * scale)), max(1, int(cropped.height * scale))),
            Image.Resampling.LANCZOS,
        )
    canvas.alpha_composite(cropped, ((SPRITE_SIZE - cropped.width) // 2, (SPRITE_SIZE - cropped.height) // 2))
    return remove_small_components(canvas)


def write_sprite_preview(files: list[Path], out_path: Path) -> None:
    thumb = 128
    label_height = 24
    cols = 10
    rows = (len(files) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * thumb, rows * (thumb + label_height)), (250, 252, 244, 255))
    draw = ImageDraw.Draw(sheet)

    for index, path in enumerate(files):
        image = Image.open(path).convert("RGBA")
        tile = Image.new("RGBA", (thumb, thumb), (250, 252, 244, 255))
        image.thumbnail((116, 116), Image.Resampling.LANCZOS)
        tile.alpha_composite(image, ((thumb - image.width) // 2, (thumb - image.height) // 2))
        x = (index % cols) * thumb
        y = (index // cols) * (thumb + label_height)
        sheet.alpha_composite(tile, (x, y))
        label = f"{path.parent.name[:8]}/{path.stem[:5]}"
        draw.text((x + 4, y + thumb + 4), label, fill=(34, 55, 40, 255))

    sheet.save(out_path)


def draw_rain_streak_patch(out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (SPRITE_SIZE, SPRITE_SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    streaks = [
        (90, 72, 154, 190),
        (180, 34, 238, 156),
        (282, 82, 344, 210),
        (390, 42, 454, 174),
        (132, 240, 190, 356),
        (260, 224, 318, 342),
        (388, 260, 442, 370),
    ]
    for index, (x1, y1, x2, y2) in enumerate(streaks):
        alpha = 120 if index % 2 == 0 else 88
        draw.line((x1, y1, x2, y2), fill=(115, 190, 255, alpha), width=8)
        draw.line((x1 + 5, y1, x2 + 5, y2), fill=(235, 250, 255, alpha // 2), width=3)
    for x, y, radius in [(126, 390, 11), (222, 402, 8), (334, 386, 10), (420, 420, 7)]:
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(90, 178, 255, 110))
    image.save(out_path)


def main() -> None:
    manifest = {"sprite_size": SPRITE_SIZE, "items": []}
    written = []

    for spec in SPECS:
        atlas = Image.open(spec["path"]).convert("RGBA")
        cell_width = atlas.width / spec["cols"]
        cell_height = atlas.height / spec["rows"]

        if spec["kind"] == "crop":
            for row, stage in enumerate(STAGES):
                for col, name in enumerate(spec["names"]):
                    sprite = crop_cell(
                        atlas,
                        round(col * cell_width),
                        round(row * cell_height),
                        round((col + 1) * cell_width),
                        round((row + 1) * cell_height),
                    )
                    if sprite is None:
                        continue
                    out_dir = SPRITES / "crops_no_soil" / name
                    out_dir.mkdir(parents=True, exist_ok=True)
                    out_path = out_dir / f"{stage}.png"
                    sprite.save(out_path)
                    written.append(out_path)
                    manifest["items"].append(
                        {
                            "kind": "crop_no_soil",
                            "group": spec["group"],
                            "name": name,
                            "stage": stage,
                            "path": str(out_path),
                        }
                    )
        else:
            out_dir = SPRITES / spec["group"]
            out_dir.mkdir(parents=True, exist_ok=True)
            for index, name in enumerate(spec["names"]):
                row, col = divmod(index, spec["cols"])
                sprite = crop_cell(
                    atlas,
                    round(col * cell_width),
                    round(row * cell_height),
                    round((col + 1) * cell_width),
                    round((row + 1) * cell_height),
                )
                if sprite is None:
                    continue
                out_path = out_dir / f"{name}.png"
                sprite.save(out_path)
                written.append(out_path)
                manifest["items"].append(
                    {
                        "kind": spec["kind"],
                        "group": spec["group"],
                        "name": name,
                        "path": str(out_path),
                    }
                )

    rain_path = SPRITES / "effects" / "rain_streak_patch.png"
    has_rain_patch = any(item.get("group") == "effects" and item.get("name") == "rain_streak_patch" for item in manifest["items"])
    if not rain_path.exists():
        draw_rain_streak_patch(rain_path)
        written.append(rain_path)
    if not has_rain_patch:
        manifest["items"].append(
            {
                "kind": "effect",
                "group": "effects",
                "name": "rain_streak_patch",
                "path": str(rain_path),
            }
        )

    (ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    crop_files = sorted((SPRITES / "crops_no_soil").glob("*/*.png"))
    scene_files = sorted(path for path in written if "/crops_no_soil/" not in str(path))
    write_sprite_preview(crop_files, ROOT / "crops-no-soil-sprites-preview.png")
    write_sprite_preview(scene_files, ROOT / "terrain-structures-widgets-preview.png")
    write_sprite_preview(sorted(written), ROOT / "all-sprites-preview.png")
    print(f"Wrote {len(written)} sprites")


if __name__ == "__main__":
    main()
