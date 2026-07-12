#!/usr/bin/env python3
import json
import math
import struct
from pathlib import Path


OUT = Path("app/src/main/assets/models")


class GlbBuilder:
    def __init__(self):
        self.materials = []
        self.material_index = {}
        self.primitives = []

    def material(self, name, color):
        key = (name, tuple(color))
        if key in self.material_index:
            return self.material_index[key]
        alpha = color[3] if len(color) > 3 else 1.0
        material = {
            "name": name,
            "pbrMetallicRoughness": {
                "baseColorFactor": color,
                "metallicFactor": 0.0,
                "roughnessFactor": 0.82,
            },
            "doubleSided": True,
        }
        if alpha < 1.0:
            material["alphaMode"] = "BLEND"
        self.material_index[key] = len(self.materials)
        self.materials.append(material)
        return self.material_index[key]

    def add_primitive(self, positions, normals, indices, material_name, color):
        self.primitives.append(
            {
                "positions": positions,
                "normals": normals,
                "indices": indices,
                "material": self.material(material_name, color),
            }
        )

    def write(self, path):
        buffer = bytearray()
        buffer_views = []
        accessors = []

        def align():
            while len(buffer) % 4:
                buffer.append(0)

        def add_view(data, target):
            align()
            offset = len(buffer)
            buffer.extend(data)
            view = {"buffer": 0, "byteOffset": offset, "byteLength": len(data)}
            if target is not None:
                view["target"] = target
            buffer_views.append(view)
            return len(buffer_views) - 1

        def add_float_accessor(values, accessor_type, target=34962):
            flat = [component for value in values for component in value]
            data = struct.pack("<" + "f" * len(flat), *flat)
            view = add_view(data, target)
            accessor = {
                "bufferView": view,
                "componentType": 5126,
                "count": len(values),
                "type": accessor_type,
            }
            if accessor_type == "VEC3" and values:
                accessor["min"] = [min(v[i] for v in values) for i in range(3)]
                accessor["max"] = [max(v[i] for v in values) for i in range(3)]
            accessors.append(accessor)
            return len(accessors) - 1

        def add_index_accessor(indices):
            data = struct.pack("<" + "I" * len(indices), *indices)
            view = add_view(data, 34963)
            accessors.append(
                {
                    "bufferView": view,
                    "componentType": 5125,
                    "count": len(indices),
                    "type": "SCALAR",
                }
            )
            return len(accessors) - 1

        primitives = []
        for primitive in self.primitives:
            position_accessor = add_float_accessor(primitive["positions"], "VEC3")
            normal_accessor = add_float_accessor(primitive["normals"], "VEC3")
            index_accessor = add_index_accessor(primitive["indices"])
            primitives.append(
                {
                    "attributes": {"POSITION": position_accessor, "NORMAL": normal_accessor},
                    "indices": index_accessor,
                    "material": primitive["material"],
                }
            )

        gltf = {
            "asset": {"version": "2.0", "generator": "caicai-farm-asset-generator"},
            "scene": 0,
            "scenes": [{"nodes": [0]}],
            "nodes": [{"mesh": 0}],
            "meshes": [{"primitives": primitives}],
            "materials": self.materials,
            "buffers": [{"byteLength": len(buffer)}],
            "bufferViews": buffer_views,
            "accessors": accessors,
        }
        json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
        while len(json_bytes) % 4:
            json_bytes += b" "
        align()
        binary = bytes(buffer)
        total_length = 12 + 8 + len(json_bytes) + 8 + len(binary)
        glb = bytearray()
        glb.extend(struct.pack("<III", 0x46546C67, 2, total_length))
        glb.extend(struct.pack("<I4s", len(json_bytes), b"JSON"))
        glb.extend(json_bytes)
        glb.extend(struct.pack("<I4s", len(binary), b"BIN\x00"))
        glb.extend(binary)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(glb)


def transform(point, center=(0, 0, 0), scale=(1, 1, 1)):
    return (
        center[0] + point[0] * scale[0],
        center[1] + point[1] * scale[1],
        center[2] + point[2] * scale[2],
    )


def add_box(builder, center, size, color, name="box"):
    sx, sy, sz = size[0] / 2, size[1] / 2, size[2] / 2
    cx, cy, cz = center
    faces = [
        ((0, 1, 0), [(-sx, sy, -sz), (sx, sy, -sz), (sx, sy, sz), (-sx, sy, sz)]),
        ((0, -1, 0), [(-sx, -sy, sz), (sx, -sy, sz), (sx, -sy, -sz), (-sx, -sy, -sz)]),
        ((0, 0, 1), [(-sx, -sy, sz), (-sx, sy, sz), (sx, sy, sz), (sx, -sy, sz)]),
        ((0, 0, -1), [(sx, -sy, -sz), (sx, sy, -sz), (-sx, sy, -sz), (-sx, -sy, -sz)]),
        ((1, 0, 0), [(sx, -sy, sz), (sx, sy, sz), (sx, sy, -sz), (sx, -sy, -sz)]),
        ((-1, 0, 0), [(-sx, -sy, -sz), (-sx, sy, -sz), (-sx, sy, sz), (-sx, -sy, sz)]),
    ]
    positions, normals, indices = [], [], []
    for normal, verts in faces:
        start = len(positions)
        positions.extend([(cx + x, cy + y, cz + z) for x, y, z in verts])
        normals.extend([normal] * 4)
        indices.extend([start, start + 1, start + 2, start, start + 2, start + 3])
    builder.add_primitive(positions, normals, indices, name, color)


def add_cylinder(builder, center, radius, height, color, name="cylinder", segments=14):
    cx, cy, cz = center
    positions, normals, indices = [], [], []
    # side
    for i in range(segments):
        a = 2 * math.pi * i / segments
        x, z = math.cos(a) * radius, math.sin(a) * radius
        positions.append((cx + x, cy - height / 2, cz + z))
        positions.append((cx + x, cy + height / 2, cz + z))
        normals.append((math.cos(a), 0, math.sin(a)))
        normals.append((math.cos(a), 0, math.sin(a)))
    for i in range(segments):
        j = (i + 1) % segments
        indices.extend([2 * i, 2 * j, 2 * i + 1, 2 * i + 1, 2 * j, 2 * j + 1])
    # caps
    top_center = len(positions)
    positions.append((cx, cy + height / 2, cz))
    normals.append((0, 1, 0))
    bottom_center = len(positions)
    positions.append((cx, cy - height / 2, cz))
    normals.append((0, -1, 0))
    top_start = len(positions)
    for i in range(segments):
        a = 2 * math.pi * i / segments
        positions.append((cx + math.cos(a) * radius, cy + height / 2, cz + math.sin(a) * radius))
        normals.append((0, 1, 0))
    bottom_start = len(positions)
    for i in range(segments):
        a = 2 * math.pi * i / segments
        positions.append((cx + math.cos(a) * radius, cy - height / 2, cz + math.sin(a) * radius))
        normals.append((0, -1, 0))
    for i in range(segments):
        j = (i + 1) % segments
        indices.extend([top_center, top_start + i, top_start + j])
        indices.extend([bottom_center, bottom_start + j, bottom_start + i])
    builder.add_primitive(positions, normals, indices, name, color)


def add_sphere(builder, center, radius, color, name="sphere", segments=12, rings=6):
    add_ellipsoid(builder, center, (radius, radius, radius), color, name, segments, rings)


def add_ellipsoid(builder, center, radii, color, name="ellipsoid", segments=12, rings=6):
    cx, cy, cz = center
    rx, ry, rz = radii
    positions, normals, indices = [], [], []
    for r in range(rings + 1):
        v = r / rings
        phi = math.pi * v
        for s in range(segments):
            u = s / segments
            theta = 2 * math.pi * u
            nx = math.sin(phi) * math.cos(theta)
            ny = math.cos(phi)
            nz = math.sin(phi) * math.sin(theta)
            normals.append((nx, ny, nz))
            positions.append((cx + nx * rx, cy + ny * ry, cz + nz * rz))
    for r in range(rings):
        for s in range(segments):
            a = r * segments + s
            b = r * segments + (s + 1) % segments
            c = (r + 1) * segments + s
            d = (r + 1) * segments + (s + 1) % segments
            indices.extend([a, c, b, b, c, d])
    builder.add_primitive(positions, normals, indices, name, color)


def add_roof(builder, center, width, length, base_y, height, color, name="roof"):
    cx, _, cz = center
    w, l = width / 2, length / 2
    verts = [
        (cx - w, base_y, cz - l),
        (cx + w, base_y, cz - l),
        (cx, base_y + height, cz - l),
        (cx - w, base_y, cz + l),
        (cx + w, base_y, cz + l),
        (cx, base_y + height, cz + l),
    ]
    faces = [
        ((0, 0.7, -0.7), [0, 1, 2]),
        ((0, 0.7, 0.7), [3, 5, 4]),
        ((-0.7, 0.7, 0), [0, 2, 5, 3]),
        ((0.7, 0.7, 0), [1, 4, 5, 2]),
        ((0, -1, 0), [0, 3, 4, 1]),
    ]
    positions, normals, indices = [], [], []
    for normal, face in faces:
        start = len(positions)
        for idx in face:
            positions.append(verts[idx])
            normals.append(normal)
        if len(face) == 3:
            indices.extend([start, start + 1, start + 2])
        else:
            indices.extend([start, start + 1, start + 2, start, start + 2, start + 3])
    builder.add_primitive(positions, normals, indices, name, color)


def write_tile(path, color):
    b = GlbBuilder()
    add_box(b, (0, -0.025, 0), (0.92, 0.05, 0.92), color, "tile")
    b.write(OUT / path)


def write_selection():
    b = GlbBuilder()
    glow = (1.0, 0.82, 0.28, 0.72)
    add_box(b, (0, 0.035, -0.45), (0.92, 0.045, 0.055), glow, "selection")
    add_box(b, (0, 0.035, 0.45), (0.92, 0.045, 0.055), glow, "selection")
    add_box(b, (-0.45, 0.035, 0), (0.055, 0.045, 0.92), glow, "selection")
    add_box(b, (0.45, 0.035, 0), (0.055, 0.045, 0.92), glow, "selection")
    b.write(OUT / "tile_selection.glb")


def add_raised_bed(b):
    add_box(b, (0, 0.018, 0), (0.78, 0.036, 0.78), (0.31, 0.20, 0.12, 1), "rich_soil")
    add_box(b, (0, 0.075, -0.43), (0.90, 0.12, 0.075), (0.58, 0.34, 0.17, 1), "wood")
    add_box(b, (0, 0.075, 0.43), (0.90, 0.12, 0.075), (0.64, 0.39, 0.20, 1), "wood")
    add_box(b, (-0.43, 0.075, 0), (0.075, 0.12, 0.90), (0.53, 0.30, 0.15, 1), "wood")
    add_box(b, (0.43, 0.075, 0), (0.075, 0.12, 0.90), (0.68, 0.43, 0.22, 1), "wood")


def add_leaf_cluster(b, x, z, y, scale, leaf=(0.28, 0.65, 0.25, 1), highlight=(0.68, 0.88, 0.36, 1)):
    base_y = 0.09
    add_cylinder(b, (x, base_y + y * 0.45, z), 0.012 * scale, y * 0.9, (0.12, 0.40, 0.18, 1), "stem")
    for dx, dz, dy, r in [(-0.06, 0, 0.0, 1), (0.05, 0.04, 0.02, 1), (0.0, -0.06, 0.035, 0.95), (0.035, -0.03, 0.06, 0.75)]:
        add_ellipsoid(
            b,
            (x + dx * scale, base_y + y + dy * scale, z + dz * scale),
            (0.080 * scale * r, 0.026 * scale * r, 0.055 * scale * r),
            leaf,
            "leaf",
        )
    add_ellipsoid(
        b,
        (x, base_y + y + 0.045 * scale, z),
        (0.050 * scale, 0.020 * scale, 0.038 * scale),
        highlight,
        "leaf_highlight",
    )


def write_crop(path, kind, stage):
    b = GlbBuilder()
    add_raised_bed(b)
    progress = [0.35, 0.65, 1.0][stage]
    count = [3, 5, 7][stage]
    xs = [-0.27, -0.14, 0.0, 0.14, 0.27, -0.07, 0.21][:count]
    zs = [-0.10, 0.08, -0.02, 0.11, -0.09, -0.20, 0.22][:count]
    if kind == "vine":
        for x in [-0.32, 0.32]:
            add_box(b, (x, 0.42, -0.28), (0.035, 0.62, 0.035), (0.60, 0.38, 0.18, 1), "trellis")
            add_box(b, (x, 0.42, 0.28), (0.035, 0.62, 0.035), (0.60, 0.38, 0.18, 1), "trellis")
        for y in [0.34, 0.52, 0.70]:
            add_box(b, (0, y, -0.28), (0.72, 0.025, 0.025), (0.72, 0.48, 0.25, 1), "trellis")
            add_box(b, (0, y, 0.28), (0.72, 0.025, 0.025), (0.72, 0.48, 0.25, 1), "trellis")
    if kind == "tomato":
        for x in [-0.28, 0.0, 0.28]:
            add_cylinder(b, (x, 0.36, -0.26), 0.014, 0.56, (0.63, 0.42, 0.20, 1), "stake")
    for i, (x, z) in enumerate(zip(xs, zs)):
        if kind == "leafy":
            add_leaf_cluster(b, x, z, 0.09 + progress * 0.10, 0.8 + progress * 0.75)
        elif kind == "vine":
            add_cylinder(b, (x, 0.10, z), 0.014, 0.20 + progress * 0.12, (0.08, 0.45, 0.30, 1), "vine")
            add_leaf_cluster(b, x, z, 0.16 + progress * 0.14, 0.8 + progress * 0.6, (0.10, 0.56, 0.38, 1), (0.55, 0.86, 0.48, 1))
            if stage >= 1 and i % 2 == 0:
                add_ellipsoid(b, (x + 0.05, 0.20 + progress * 0.18, z), (0.055, 0.035, 0.080), (0.20, 0.70, 0.36, 1), "cucumber")
        elif kind == "root":
            add_leaf_cluster(b, x, z, 0.10 + progress * 0.10, 0.65 + progress * 0.45)
            if stage > 0:
                add_cylinder(b, (x, 0.075, z + 0.035), 0.022 * progress, 0.12 * progress, (0.92, 0.46, 0.12, 1), "root")
        else:
            add_leaf_cluster(b, x, z, 0.18 + progress * 0.22, 0.68 + progress * 0.45, (0.16, 0.55, 0.22, 1), (0.62, 0.86, 0.38, 1))
            if stage >= 1:
                fruit_color = (0.90, 0.12, 0.08, 1) if kind == "tomato" else (0.78, 0.10, 0.10, 1)
                add_sphere(b, (x + 0.05, 0.24 + progress * 0.22, z + 0.03), 0.045 + 0.012 * stage, fruit_color, "fruit")
    b.write(OUT / path)


def write_greenhouse():
    b = GlbBuilder()
    add_box(b, (0, 0.02, 0), (0.95, 0.04, 0.70), (0.44, 0.58, 0.48, 1), "base")
    add_box(b, (0, 0.30, 0), (0.88, 0.50, 0.62), (0.68, 0.93, 1.0, 0.36), "glass")
    add_roof(b, (0, 0, 0), 0.94, 0.70, 0.55, 0.35, (0.72, 0.95, 1.0, 0.48), "glass_roof")
    for x in [-0.45, 0.0, 0.45]:
        add_box(b, (x, 0.31, -0.36), (0.025, 0.56, 0.025), (0.46, 0.52, 0.50, 1), "frame")
        add_box(b, (x, 0.31, 0.36), (0.025, 0.56, 0.025), (0.46, 0.52, 0.50, 1), "frame")
    for z in [-0.36, 0.36]:
        add_box(b, (-0.45, 0.31, z), (0.025, 0.56, 0.025), (0.46, 0.52, 0.50, 1), "frame")
        add_box(b, (0.45, 0.31, z), (0.025, 0.56, 0.025), (0.46, 0.52, 0.50, 1), "frame")
    add_leaf_cluster(b, -0.18, 0.08, 0.22, 0.75)
    add_sphere(b, (0.22, 0.25, -0.10), 0.055, (0.95, 0.60, 0.18, 1), "greenhouse_fruit")
    b.write(OUT / "greenhouse.glb")


def write_shed():
    b = GlbBuilder()
    add_box(b, (0, 0.30, 0), (0.72, 0.60, 0.58), (0.66, 0.38, 0.18, 1), "wall")
    add_box(b, (0, 0.28, -0.31), (0.22, 0.42, 0.04), (0.44, 0.25, 0.13, 1), "door")
    add_roof(b, (0, 0, 0), 0.86, 0.68, 0.58, 0.24, (0.36, 0.52, 0.41, 1), "roof")
    for x in [-0.26, 0, 0.26]:
        add_box(b, (x, 0.31, -0.33), (0.015, 0.44, 0.03), (0.78, 0.52, 0.30, 1), "plank")
    b.write(OUT / "tool_shed.glb")


def write_sign():
    b = GlbBuilder()
    add_cylinder(b, (0, 0.24, 0), 0.025, 0.48, (0.55, 0.32, 0.16, 1), "pole")
    add_box(b, (0, 0.62, -0.02), (0.46, 0.28, 0.045), (1.0, 0.82, 0.42, 1), "board")
    add_sphere(b, (-0.06, 0.63, -0.055), 0.060, (0.90, 0.12, 0.08, 1), "tomato")
    add_sphere(b, (0.04, 0.70, -0.055), 0.035, (0.22, 0.58, 0.25, 1), "leaf")
    b.write(OUT / "sign.glb")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    write_tile("tile_grass.glb", (0.42, 0.78, 0.28, 1))
    write_tile("tile_soil.glb", (0.42, 0.25, 0.13, 1))
    write_selection()
    for kind in ["tomato", "leafy", "vine", "root"]:
        for stage_name, stage in [("early", 0), ("mid", 1), ("late", 2)]:
            write_crop(f"crop_{kind}_{stage_name}.glb", kind, stage)
    write_greenhouse()
    write_shed()
    write_sign()


if __name__ == "__main__":
    main()
