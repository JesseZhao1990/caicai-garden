# V2 菜园素材库

这版素材按新的接入原则整理：

- 菜品素材不带土、不带菜畦、不带地块底座，只保留植物本体。
- 地块、菜田方格、背景、设施、状态组件独立存放。
- 所有可叠加素材都提供透明 PNG，单个 sprite 统一为 512 x 512。

## 目录

- `raw/`：imagegen 原始图集，主要用于追溯和重新裁切。
- `transparent/`：去掉品红背景后的透明图集。
- `sprites/crops_no_soil/`：无土菜品素材，按 `作物/阶段.png` 存放。
- `sprites/terrain/`：地块、菜田方格、路径、放置状态框等。
- `sprites/structures/`：温室、农具棚、栅栏、藤架、工具等。
- `sprites/widgets/`：浇水、施肥、采收、虫害、成熟、旋转等状态小组件。
- `sprites/environment/`：树、灌木、花丛、石头、池塘、树篱等环境装饰。
- `sprites/effects/`：阳光、水滴、雨丝、生长光效、放置光效等叠加效果。
- `backgrounds/`：完整菜园背景图。
- `manifest.json`：全部 sprite 的机器可读清单。

## 预览

- `crops-no-soil-sprites-preview.png`：无土菜品总览。
- `terrain-structures-widgets-preview.png`：地块/设施/小组件/环境/效果总览。
- `all-sprites-preview.png`：全部 sprite 总览。

## 数量

- 无土菜品：120 个，30 种 x 4 阶段。
- 地块/方格：20 个。
- 设施/工具：20 个。
- 状态小组件：20 个。
- 环境装饰：20 个。
- 效果层：20 个。
- 背景：2 张。
- 单个透明 sprite 合计：220 个。

## 菜品

每种菜包含 4 个阶段：

- `seedling`：幼苗期
- `young`：生长期
- `mature`：旺长期/开花期
- `harvest`：采收期

当前覆盖 30 种：

- 叶菜：`lettuce`、`cabbage`、`bok_choy`、`spinach`、`kale`、`celery`
- 茄果：`tomato`、`cherry_tomato`、`chili_pepper`、`bell_pepper`、`eggplant`、`okra`
- 瓜藤豆：`cucumber`、`pumpkin`、`zucchini`、`bitter_melon`、`green_bean`、`snow_pea`
- 根茎葱蒜：`carrot`、`daikon`、`potato`、`sweet_potato`、`onion`、`garlic`
- 香草/其他：`basil`、`mint`、`cilantro`、`scallion`、`strawberry`、`corn`

## 背景

- `backgrounds/garden-empty-background.png`：空菜园背景，中央留白用于叠加编辑元素。
- `backgrounds/garden-8x8-grid-background.png`：带 8 x 8 菜田方格的编辑背景。

## 接入建议

1. App 内菜品只使用 `sprites/crops_no_soil/`。
2. 地块底座使用 `sprites/terrain/`，菜品以 billboard/贴片方式叠加到地块上。
3. 藤架、温室、农具棚等从 `sprites/structures/` 单独摆放。
4. 花草石头、树篱、池塘等从 `sprites/environment/` 作为装饰层。
5. 浇水、施肥、成熟等状态从 `sprites/widgets/` 叠加，不要烘焙到菜品图里。
6. 阳光、水滴、生长光效等从 `sprites/effects/` 做临时动画或状态反馈。
7. 旧版 `design/imagegen-assets/sprites/` 的菜品带土，只作为历史参考，不建议继续接入。
