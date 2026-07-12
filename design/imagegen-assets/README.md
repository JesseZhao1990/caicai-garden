# Imagegen 菜园素材图集

本目录用于沉淀后续接入 App 的 AI 生成素材。`raw/` 保留原始品红背景图，`transparent/` 是已去背景透明 PNG。

## 文件

- `asset-atlas-preview.png`：5 张图集的总览预览。
- `raw/plot-tiles-atlas.png`：地块素材原图。
- `transparent/plot-tiles-atlas-transparent.png`：地块素材透明版。
- `transparent/vegetables-leafy-growth-atlas-transparent.png`：叶菜类透明版。
- `transparent/vegetables-fruiting-growth-atlas-transparent.png`：茄果类透明版。
- `transparent/vegetables-vine-growth-atlas-transparent.png`：瓜藤豆类透明版。
- `transparent/vegetables-root-corn-berry-herb-growth-atlas-transparent.png`：根茎/玉米/草莓/香草透明版。
- `sprites/`：已从透明图集裁切出的单个 512 x 512 透明 PNG 素材。

## 单个素材目录

- `sprites/plots/`：12 个地块素材。
- `sprites/<crop>/seedling.png`：幼苗期。
- `sprites/<crop>/young.png`：生长期。
- `sprites/<crop>/flowering.png`：开花/旺长期。
- `sprites/<crop>/harvest.png`：采收期。

目前包含 17 种常见作物：`lettuce`、`cabbage`、`bok_choy`、`spinach`、`tomato`、`chili_pepper`、`eggplant`、`bell_pepper`、`cucumber`、`pumpkin`、`zucchini`、`green_bean`、`carrot`、`daikon`、`corn`、`strawberry`、`basil`。

## 地块图集映射

4 列 x 3 行，按从左到右、从上到下：

1. 草地地块
2. 空土壤地块
3. 木质高架菜畦
4. 幼苗高架菜畦
5. 露地土壤菜畦
6. 覆盖物菜畦
7. 堆肥土菜畦
8. 稻草覆盖菜畦
9. 湿润浇水土壤
10. 花边草地地块
11. 带小灌溉桩地块
12. 选中态金色边框

## 蔬菜图集映射

所有蔬菜图集的行表示阶段：

1. 幼苗期
2. 生长期
3. 开花/旺长期
4. 采收期

### 叶菜类

4 列：生菜、卷心菜、小白菜/上海青、菠菜。

### 茄果类

4 列：番茄、辣椒、茄子、彩椒。

### 瓜藤豆类

4 列：黄瓜、南瓜、西葫芦/笋瓜、豆角。

### 根茎/玉米/草莓/香草

5 列：胡萝卜、白萝卜、玉米、草莓、罗勒/香草。

## 后续接入建议

1. App 内先用 `sprites/` 的 2D billboard 或贴片替换当前占位 GLB 的菜体部分。
2. 如果要减小包体，可把单个 PNG 批量转成 WebP。
3. 后续如果继续走真 3D，可以用这些图集作为建模参考，重新制作 GLB/低模资产。
