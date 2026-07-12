# 实体菜园 3D/2.5D 可视化技术调研

日期：2026-07-02

## 结论先行

当前项目是原生 Android + Jetpack Compose。上一版用 Compose Canvas 手绘等距格子和植物，适合做“可编辑草图”，但不适合做用户想要的高品质 3D 菜园效果。主要问题不是调几个颜色能解决，而是渲染能力和资产生产方式不匹配：

- Compose Canvas 没有真实 3D 摄像机、透视/正交相机、光照、阴影、深度排序、材质和模型管线。
- 手绘等距块容易变形，尤其是高架床、温室、棚子这些高度不同的对象。
- 作物成长如果继续用 2D 小图或简单圆形叶片，只能表达“状态图标”，难以达到参考图那种精致模型感。

推荐路线：**保持 App 主体为 Jetpack Compose，用 SceneView/Filament 承载菜园 3D 场景；先做一个 8x8 实体菜园的 3D POC，再进入完整设计和实现。**

## 行业内常见技术栈

### 1. 游戏引擎或自研引擎

农场/庄园/城市经营类产品通常不是普通 UI Canvas 画出来的，而是游戏引擎或自研引擎驱动。Playrix 的公开招聘说明里提到其自研引擎类似 Unity/Unreal，并要求 Unity、Unreal、Godot、Cocos2D 等经验。这类路线适合 Township、梦想城镇、QQ 农场这类完整游戏场景。

代表栈：

- Unity：2D Isometric Tilemap、Sprite/Prefab、Animator、URP、Addressables。
- Unreal：高质量 3D，但移动端体量和工程复杂度偏大。
- Godot/Cocos Creator：轻量游戏引擎，适合 2D/轻 3D 和跨平台。
- 自研 C++ 引擎：大厂长期运营类游戏常见，但不适合当前项目阶段。

参考：

- Unity Isometric Tilemap 文档/介绍：支持等距 Tilemap、排序轴、2D 资源构建等距场景。  
  https://unity.com/blog/engine-platform/isometric-2d-environments-with-tilemap
- Unity Manual：可创建 Isometric Tile Palette。  
  https://docs.unity3d.com/6000.4/Documentation/Manual/tilemaps/work-with-tilemaps/isometric-tilemaps/create-isometric-tilemap.html
- Playrix 招聘：自研引擎类似 Unity/Unreal，并提到 Unity、Unreal、Godot、Cocos2D 等经验。  
  https://playrix.com/job/open/c%2B%2B-development/senior-software-engineer-gameplay
- Cocos Creator：轻量跨平台 2D/3D 引擎。  
  https://www.cocos.com/en/creator

### 2. 原生 App 内嵌 3D 渲染器

如果产品不是完整游戏，而是业务 App 里的一块 3D 可视化编辑器，行业常用做法是：主 App 继续使用原生 UI，3D 场景部分用专门渲染器承载。

当前项目更符合这个模式：它是菜园管理 App，不需要商城、宠物、积分、社交和游戏 LiveOps，只需要一个好看、准确、可编辑的实体菜园场景。

代表栈：

- Filament：Android 端高质量 PBR 3D 渲染，可加载 glTF/GLB，适合真实光照、材质、阴影。
- SceneView：基于 Filament，提供 Jetpack Compose 友好的 Scene/Node/ModelNode 封装。
- Unity as a Library：把 Unity 场景作为 Android native app 的一部分嵌入，但包体、构建、通信和维护成本更高。
- Godot Android library：可嵌入现有 Android 应用，但当前 Compose 集成、团队工作流和资产链不如 SceneView 直接。

参考：

- Filament：实时 PBR 渲染引擎，覆盖 Android、iOS、桌面和 WebGL2。  
  https://github.com/google/filament
- Filament 文档：目标是为 Android 开发者提供高质量 2D/3D 渲染能力。  
  https://google.github.io/filament/Filament.md.html
- Android Performance Analyzer：Filament 可把高质量 PBR 3D 渲染引擎集成进 Android App。  
  https://developer.android.com/android-performance-analyzer/case-study/filament
- SceneView：Jetpack Compose 原生 3D/AR SDK，基于 Filament。  
  https://github.com/sceneview/sceneview
- Maven Central：`io.github.sceneview:sceneview`，描述为 Android 3D/AR SDK，Jetpack Compose、Filament、ARCore。  
  https://central.sonatype.com/artifact/io.github.sceneview/sceneview
- Unity as a Library：可把 Unity 2D/3D 实时渲染能力插入原生移动 App。  
  https://unity.com/features/unity-as-a-library
- Unity Android Library 手册：通过 `unityLibrary` 模块集成到 Android Gradle 项目。  
  https://docs.unity3d.com/6000.4/Documentation/Manual/UnityasaLibrary-Android.html
- Godot Android Library：Godot 可嵌入既有 Android 应用或库。  
  https://docs.godotengine.org/en/stable/tutorials/platform/android/android_library.html

### 3. WebGL/WebView 方案

Three.js/Babylon.js 常用于 Web 3D。如果产品需要同一套 3D 菜园在 Web、H5、小程序和 App 里复用，可以考虑 WebView + WebGL。

但对当前 Android 原生项目来说，WebView 会带来手势冲突、生命周期、离线资源、性能监控和原生数据同步问题，不是首选。

参考：

- Three.js：JavaScript 3D library。  
  https://threejs.org/
- Babylon.js：Web 3D 渲染引擎。  
  https://www.babylonjs.com/

## 当前项目适配评估

现状：

- Android 原生 App。
- Jetpack Compose + Material3。
- 数据模型已经有：`FarmLayout`、`FarmTile`、`PlantingBatch`、`PlantingInsight.progressPercent`。
- 已经实现拖拽、摆放、旋转、清空等编辑逻辑。
- 缺的是高质量视觉层和可靠 3D 坐标/命中系统。

不推荐继续纯 Compose Canvas 作为最终方案：

- 可以保留为低成本 fallback 或编辑草图。
- 不能作为“精致 3D 菜园展示”的主方案。
- 继续手绘会不断遇到变形、深度遮挡、光照虚假、作物不像、温室/棚子贴图不统一的问题。

## 推荐技术路线

### 首选：Compose + SceneView/Filament + GLB 资产

用 SceneView 承载一个正交相机下的 3D 菜园小场景：

- 地面：固定 8x8 网格，对应真实菜园。
- 菜畦：低多边形/卡通写实高架床 GLB。
- 作物：番茄、黄瓜、叶菜等按成长阶段切换模型或缩放骨骼/节点。
- 温室/棚子/标识牌：独立 GLB 模型。
- 相机：固定 isometric orthographic camera，避免手绘透视变形。
- 交互：通过 SceneView hit test 或映射地面坐标实现点选、拖拽、放置、缩放视图。
- 数据：继续复用现有 `FarmLayout` 和 `PlantingInsight.progressPercent`。

优点：

- 和现有 Compose 项目集成路径最短。
- 可实现真实光照、阴影、材质和模型透视。
- 不需要引入完整 Unity 工程和复杂构建链。
- 能自然支持作物随时间长大。

风险：

- 需要制作或引入一套一致风格的 GLB 低模资产。
- 初期需要验证 SceneView 在当前 Gradle/SDK 下的兼容性和包体。
- 拖拽命中需要做地面坐标转换，不能沿用当前 2D offset 逻辑。

### 次选：Unity as a Library

适合目标变成“完整农场游戏级场景”，例如大量动画、粒子、昼夜天气、复杂地形、批量美术管线。

不作为当前首选的原因：

- 这是菜园管理 App，不是游戏 App。
- 包体和构建复杂度高。
- Android 原生页面与 Unity 场景通信成本高。
- 当前需求集中在一个菜园编辑场景，用 SceneView 更轻。

### 不推荐：继续纯 Compose Canvas

仅适合：

- 快速草图。
- 管理后台式平面布局。
- 无高质感要求的 2D 卡片。

不适合：

- 用户要求的精致 3D/等距农场规划模型。
- 植物自然成长。
- 温室、棚子、菜畦在同一真实空间下统一光照和遮挡。

## 下一步设计前置约束

进入设计前，必须先把视觉目标定成可实现的工程约束：

1. 画面不是 QQ 农场菜单页，也不是纯游戏大厅。
2. 画面是“真实菜园主人的 3D 菜园规划视图”。
3. 主视觉只服务实体菜园：菜畦、棚子、温室、标识牌、作物成长、编辑操作。
4. 菜畦必须不变形：用固定正交相机 + 统一 3D 网格。
5. 作物成长不靠文字解释：不同进度必须直接改变模型高度、叶片密度、果实数量。
6. 一期先做 8x8 场景 POC，验证渲染、命中、拖拽和包体，再替换完整菜园页。

## 建议 POC 验收标准

进入完整实现前，先做一个小 POC。验收标准如下：

- Android App 内能渲染 SceneView/Filament 场景。
- 固定等距正交视角，8x8 地块完整显示，不拉伸、不变形。
- 至少 3 个 GLB/程序模型：菜畦、温室、作物。
- 作物支持 3 个阶段：幼苗、成长、结果。
- 点击地块能选中，拖拽菜畦能换格。
- 性能：模拟器和真机上滑动/拖拽无明显卡顿。
- 失败 fallback：保留当前 Compose 布局作为临时降级，不作为最终视觉。

