# 菜园管家项目交接文档

更新时间：2026-07-14

工作目录：`/Users/bytedance/Documents/caicai`

## 1. 给下一会话的直接指令

请先阅读本文件，再检查本地 Git 状态和当前真机/模拟器画面。不要只凭 README 或历史截图判断当前实现。

当前最重要的事实：

- 项目已经创建 GitHub 仓库、提交并推送，不要重复初始化仓库。
- `v1.1.0` 已通过 GitHub Releases 发布，App 内自动/手动升级能力已经实现。
- SceneView/Filament 依赖、GLB 文件和 `FarmScene3DBoard.kt` 已存在，但当前菜园页真正启用的仍是 Compose Canvas 的 `FarmAssetBoard` 2.5D 位图方案。
- 用户最关心的不是游戏系统，而是一个美观、可编辑、能映射真实实体菜园、作物能随时间成长的菜园视图。
- 不要继续用小修小补掩盖渲染路线问题。若继续视觉升级，应先在设备上验证当前画面，再决定替换真实 3D 资产和接入 3D 交互。

推荐新会话开场执行：

```bash
cd /Users/bytedance/Documents/caicai
git status --short --branch
git log --oneline --decorate -5
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

## 2. 产品目标与不可偏离的约束

这是给真实菜园园主使用的 Android App。菜园视图对应用户线下的实体菜园，不是农场经营游戏。

用户明确要求：

1. 核心是菜园本身好看、清楚、方便编辑。
2. 菜园布局必须对应真实地块，可拖拽调整。
3. 作物需要随播种日期和时间推移自然长大，不只是显示一段文字进度。
4. 视觉语言可参考 QQ 农场、梦想城镇、我的花园世界的明亮、可爱、高品质等距农场感，但不要照搬其受版权保护的具体 UI 或素材。
5. 不需要分享、音乐、相机入口、菜单、积分、宠物、商城等游戏化功能。
6. 作物不能装在突兀的盆、箱、器皿或高架容器里。根部要落在土壤上，并位于对应格子的视觉中心。
7. 作物不能悬空，不能出现透明图留白导致根部偏移，也不能用背景图里自带的菜冒充可编辑对象。
8. 小房子、农具棚、温室、鸡笼等环境建筑不应占用种菜方格，应放在菜地外围的独立环境层。
9. 木栅栏、石板路、水管/灌溉曾因旋转和交互不完整被用户要求去掉。不要重新放回主工具栏，除非先把方向、旋转和放置体验完整做好。
10. 8 x 8 地块必须完整显示，不能拉伸变形；在手机上应尽量占满可用屏幕，同时保留缩放和平移。
11. 作物可旋转，但默认角度必须自然；不能为了“可旋转”让作物以诡异角度出现。
12. 交互必须真实可用，不能只做静态效果图。至少要覆盖点选、拖拽换格、缩放、平移、旋转、清空和重置。

## 3. 当前 Git 与远端状态

截至 2026-07-14 的实时核对结果：

- 当前分支：`main`
- 当前 `main` 上有尚未提交的本轮种植方式、逐菜生长视觉、测试和验收文档改动；以文末最新进展和实时 `git status` 为准，不要覆盖。
- 已提交代码与远端一致：`main...origin/main`
- 远端：`git@github.com:JesseZhao1990/caicai-garden.git`
- 仓库：[JesseZhao1990/caicai-garden](https://github.com/JesseZhao1990/caicai-garden)
- 可见性：`PUBLIC`
- 当前提交：`6cb1b32 Document release upgrade path`
- 版本标签：`v1.1.0` 指向提交 `611d07f`

最近提交：

```text
6cb1b32 Document release upgrade path
f3b8e6b Update GitHub Actions runtimes
611d07f (tag: v1.1.0) Add GitHub Releases app updates
8ce30ac Initial Android garden app
```

GitHub CLI 位于：

```text
/Users/bytedance/.local/bin/gh
```

账号和 SSH 已配置为 `JesseZhao1990`。需要操作 GitHub 时可先执行：

```bash
/Users/bytedance/.local/bin/gh auth status
ssh -T git@github.com
```

## 4. 当前版本与发布状态

App 当前版本：

```text
versionCode = 2
versionName = 1.1.0
applicationId = com.caicai.garden
minSdk = 26
targetSdk = 35
```

正式 Release：

- 地址：[菜园管家 v1.1.0](https://github.com/JesseZhao1990/caicai-garden/releases/tag/v1.1.0)
- APK：`app-release.apk`
- 大小：`67,519,259` bytes
- SHA-256：`230488dcea149cfba15a3fc82774baefa454e335dba159a2def117b0a0e960f1`
- 状态：非草稿、非预发布

App 内升级能力已经完成：

- 启动时自动检查 GitHub 最新正式 Release。
- “今日”页有手动检查和升级入口。
- 发现新版本后下载 APK，并展示下载进度。
- 使用 GitHub Release asset 的 SHA-256 digest 校验 APK。
- 通过 `FileProvider` 和 Android 系统安装器安装。
- 未授权“安装未知应用”时会跳转系统设置。

关键文件：

- `app/src/main/java/com/caicai/garden/update/AppUpdateManager.kt`
- `app/src/main/java/com/caicai/garden/ui/AppUpdateUi.kt`
- `app/src/main/java/com/caicai/garden/ui/VisualGardenApp.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/update_file_paths.xml`
- `.github/workflows/release.yml`
- `docs/releases/v1.1.0.md`

发布下一个版本：

1. 提升 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`。
2. 提交并推送代码。
3. 创建与版本名一致的 tag，例如 `v1.2.0`。
4. 推送 tag 后，`Publish Android release` GitHub Actions 会构建固定签名 APK 并创建 Release。

```bash
git tag v1.2.0
git push origin v1.2.0
```

注意：设备若安装的是 Debug 签名版本，第一次切换到正式签名 APK 时需要先卸载 Debug 包。之后使用相同 Release 签名即可覆盖升级。

## 5. 当前 App 架构

技术栈：

- Kotlin
- Jetpack Compose + Material 3
- Android `compileSdk/targetSdk 35`
- 本地 JSON 数据存储
- Open-Meteo 天气接口
- SceneView `2.2.1` / Filament 依赖

主入口：

- `MainActivity.kt` 启动 `VisualGardenApp`。
- `VisualGardenApp.kt` 提供“今日、菜园、日历、记录”四个主 Tab。
- `GardenViewModel.kt` 管理菜园、地块、种植批次、操作记录和布局编辑。
- `GardenRepository.kt` 负责本地持久化和默认数据。
- `GardenAdvisor.kt` 根据种植日期、作物规则、天气和操作记录生成阶段、进度、任务与建议。

主要数据模型：

- `Garden`
- `Plot`
- `PlantingBatch`
- `OperationRecord`
- `FarmLayout(rows = 8, columns = 8)`
- `FarmTile(row, column, type, batchId, rotationDegrees)`

## 6. 菜园渲染的真实现状

### 6.1 当前真正启用的渲染器

`FarmDesignerSection` 当前调用：

```kotlin
FarmAssetBoard(...)
```

也就是说，当前线上画面仍是 Compose Canvas + PNG sprite 的 2.5D 等距方案，不是 SceneView 3D。

`FarmAssetBoard.kt` 当前能力：

- 8 x 8 等距格子。
- 全画布菜园背景。
- 每格草地 sprite。
- 种菜格绘制土壤 tile，再叠加透明背景作物 sprite。
- 作物按底部根点对齐到土壤，并按格子中心绘制。
- 15 种内置作物按各自阶段日历切换 `seedling / young / mature / harvest`，并使用各自的冠幅区间、增长曲线、高度、阴影与风吹参数。
- 自动风吹动画，周期约 9.6 秒；不同格子有错峰，相对根部进行轻微摆动。
- 单指拖拽种植格换位。
- 双指缩放和平移，缩放范围约 `1.0x - 2.4x`。
- 点选、移动、清空、旋转和重置。
- 当前工具栏只暴露“标识牌”结构工具，木栅栏、石板路和灌溉没有暴露。

环境层当前会固定绘制花丛、灌木、石头、独轮车和温室。温室不占种菜格。

### 6.2 已存在但未接入的 3D 路线

以下代码和资产已经存在：

- `app/src/main/java/com/caicai/garden/ui/FarmScene3DBoard.kt`
- `app/src/main/assets/models/*.glb`
- `app/build.gradle.kts` 中的 `io.github.sceneview:sceneview:2.2.1`

目前有 18 个 GLB，包括草地、选择框、温室、农具棚、标识牌，以及 leafy/root/tomato/vine 四类作物的 early/mid/late 模型。

但需要特别注意：

- `FarmScene3DBoard` 没有在 `FarmDesignerSection` 中启用。
- 当前 3D POC 只实现点击命中和相机操控，没有完整复刻 2D 版的拖拽换格、编辑工具、视图边界和风吹动画。
- 现有 GLB 是早期 POC 资产，不能默认认为已经达到用户要求的精品农场品质。
- 不要只把调用从 `FarmAssetBoard` 换成 `FarmScene3DBoard` 就交付，这会造成视觉或交互倒退。

### 6.3 当前 PNG 素材

素材目录：

```text
app/src/main/assets/farm_v2/
```

其中：

- `backgrounds/`：菜园空背景与网格背景。
- `sprites/crops_no_soil/`：30 类常见作物，每类 4 个生长阶段，共 120 张 PNG。
- `sprites/terrain/`：草地、土壤、选择框等。
- `sprites/environment/`：花丛、灌木、石头等外围装饰。
- `sprites/structures/`：温室、标识牌、工具棚等结构。
- `sprites/effects/`：阴影、选中、天气和状态效果。
- `sprites/widgets/`：操作控件和状态徽标。

这些作物使用 `crops_no_soil` 透明素材。对齐逻辑位于 `drawRaisedBedTile` 和 `drawBitmapBottomAligned`，不要再用含土盆、木框或大块透明留白的素材替换。

## 7. 交互与布局现状

布局数据保存在 `FarmLayout`，默认是 8 x 8。

默认布局：

- 8 个预设种植位置，根据活跃种植批次循环绑定。
- 1 个标识牌。
- 不再默认铺设石板路、水管或木栅栏。

当前编辑操作由 `GardenViewModel` 提供：

- `placeFarmTile`
- `moveFarmTile`
- `clearFarmTile`
- `rotateFarmTile`
- `resetFarmTileRotation`
- `resetFarmLayout`

仍存在的技术债：

- `FarmTileType` 枚举和部分旧绘制代码仍保留 `PATH / FENCE / IRRIGATION`，虽然当前工具栏没有暴露它们。
- `FarmDesigner.kt` 里还保留一套旧的 `FarmBoard` Canvas 实现，目前不是主渲染路径。
- 3D 和 2D 各自有一套坐标、命中和渲染逻辑，尚未抽成共享的编辑控制层。
- 当前有 4 个测试套件、14 个单元测试，覆盖更新版本、布局交换、种植方式预测和逐菜生长视觉；仍没有 Compose 手势/UI 自动化测试。

## 8. 下一阶段建议顺序

如果用户继续要求提升菜园视觉和交互，建议按以下顺序推进：

1. **先做现状验收**：在用户同尺寸设备或模拟器中录屏/截图，实际测试拖拽、缩放、平移、旋转、重置和作物成长阶段。不要以编译通过代替 UI 验收。
2. **确定主渲染路线**：用户此前明确要求 SceneView/Filament + GLB。应把它作为最终路线，但先补齐高品质、统一风格、根点正确的 GLB 资产。
3. **建立 3D 坐标和交互底座**：把 8 x 8 网格、相机、屏幕射线命中、地面坐标换格、拖拽预览、缩放边界和选中状态做稳定。
4. **先完成三类验收资产**：土壤地块、叶菜、番茄，覆盖幼苗/成长/成熟/采收四阶段，并验证根部落地、中心对齐、统一光照和比例。
5. **迁移编辑能力**：3D 路径必须覆盖当前 2D 已有的点选、拖拽、平移、缩放、旋转、清空和重置，再替换主画面。
6. **环境建筑独立层**：温室、农具棚、小房子等放在地块外围，不能占种菜格；先不加鸡笼、商城等无关元素。
7. **性能与视觉验收**：在至少一个手机纵向视口检查完整地块、不变形、不遮挡、无悬空、拖拽命中可靠，再交给用户。

不要做的事情：

- 不要继续在一张带菜的背景图上伪造可编辑对象。
- 不要给每棵菜加盆、木框、高架床或突兀器皿。
- 不要把建筑塞进种菜格。
- 不要重新加入用户已要求移除的水管、石板路和木栅栏。
- 不要只改截图或静态设计稿，不同步实现真实交互。
- 不要只跑 Gradle 构建就宣称视觉和手势已验收。

## 9. 验收清单

视觉：

- [ ] 8 x 8 地块完整进入手机视口，等距比例正确，无拉伸。
- [ ] 土壤、作物和环境统一光照、视角和材质风格。
- [ ] 每株作物根点落在土面，并处于目标格视觉中心。
- [ ] 没有容器感、悬空、透明留白偏移和畸形缩放。
- [ ] 建筑位于菜地外围，不占种菜格。
- [ ] 四个生长阶段能直接从视觉上区分。
- [ ] 风吹动画轻微、错峰、以根部为支点，不像整张贴图漂移。

交互：

- [ ] 点中作物后能稳定选中正确格子。
- [ ] 单指拖拽能换格，落点预览明确。
- [ ] 拖到已占用格时行为明确，不丢数据。
- [ ] 双指缩放和平移不误触拖拽。
- [ ] 缩放后地块仍可回到完整视图。
- [ ] 旋转、清空和重置均能持久化。
- [ ] App 重启后布局与角度保持。

工程：

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` 通过。
- [ ] 真机或模拟器完成主要手势自测，并保存截图或录屏证据。
- [ ] 发布版使用固定签名，版本号和 tag 一致。
- [ ] 发布后通过 GitHub latest Release API 和 App 内升级入口做端到端验证。

## 10. 最近验证结果

2026-07-14 执行：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL`，44 个 task，1 个执行、43 个 up-to-date。

Debug APK：

```text
/Users/bytedance/Documents/caicai/app/build/outputs/apk/debug/app-debug.apk
```

当前大小约 68 MB。

已知构建提示：项目使用了 Gradle 10 将不再兼容的废弃特性。当前不影响 Gradle 9.3 构建，但后续升级 Gradle 前需运行 `--warning-mode all` 定位。

## 11. 关键文档

- `README.md`：项目功能、构建、安装和发布说明。
- `docs/phase2-visual-garden-prd.md`：二期视觉菜园 PRD。
- `docs/real-garden-editor-design.md`：实体菜园编辑器设计。
- `docs/real-garden-3d-tech-research.md`：SceneView/Filament 技术路线调研。
- `docs/phase2-visual-garden-3d-render.png`：早期视觉目标参考。
- `docs/releases/v1.1.0.md`：1.1.0 发布说明。

## 12. 建议发给新会话的短提示词

```text
继续处理 /Users/bytedance/Documents/caicai。先完整阅读 docs/NEXT_SESSION_HANDOFF.md，并以当前本地 Git、源码和设备画面为准。

项目已公开发布到 JesseZhao1990/caicai-garden，当前 main 为 6cb1b32，v1.1.0 Release 和 App 内自动/手动升级已经完成。

注意：SceneView/Filament、GLB 和 FarmScene3DBoard 已存在，但主菜园仍启用 FarmAssetBoard 2.5D 位图方案。用户核心诉求是实体菜园映射、美观、可编辑、作物随时间成长；作物必须根部落地并居中，不能放进器皿，建筑必须在种菜格外，木栅栏/石板路/水管不要重新加入。

先在模拟器或真机核验现状，再继续实现；不能只以构建通过作为视觉和交互验收。
```

## 13. 2026-07-14 本次续接进展

本节晚于前文的 Git 工作区描述，后续会话应以本节和实时 `git status` 为准。

已完成：

- 使用 `Medium_Phone` AVD（Android 17、1080 x 2400、420 dpi）安装并验收当前 Debug APK。
- 实测点选、拖到空格、旋转持久化、按钮缩放、放大后单指平移、缩回完整视图和重置。
- 发现并修复拖到已占用格时删除目标对象的数据丢失问题。现在两个对象会交换位置，界面显示“目标格已有内容，已交换位置”。
- 新增纯数据层 `FarmLayout.moveTile` 和 `FarmLayoutTest`，覆盖空格移动、占用格交换和无效移动。
- 用临时诊断构建在同一模拟器启用 `FarmScene3DBoard`。Filament 正常渲染，但现有低模 GLB 带高架木框/棚架且缺少完整编辑能力，因此已恢复 `FarmAssetBoard` 主调用，没有把 3D POC 直接交付。
- 完整执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`，结果为 `BUILD SUCCESSFUL`。
- 验收报告和截图保存在 `docs/validation/2026-07-14/`。

本次工作区新增或修改：

- `app/src/main/java/com/caicai/garden/data/GardenModels.kt`
- `app/src/main/java/com/caicai/garden/ui/GardenViewModel.kt`
- `app/src/test/java/com/caicai/garden/data/FarmLayoutTest.kt`
- `docs/validation/2026-07-14/`
- `docs/NEXT_SESSION_HANDOFF.md`

仍需继续：

- 双指捏合无法通过本轮 ADB 稳定注入，仍需真实双指人工补测。
- 3D 路线应先完成无容器、根部落地、统一材质的土壤/叶菜/番茄四阶段验收资产，再补齐射线命中、拖拽预览、占用格交换、相机边界和视图复位。
- 现有 2.5D 画面虽然交互可用，但菜园格和作物在大背景中仍显偏小，背景、地块和作物的尺度/材质统一性仍需提升。

## 14. 2026-07-14 种植方式与逐日生长续接进展

用户本轮新增的明确要求：

1. 种菜时必须说明种下的是苗还是种子，并记录种植日期，以便预测后续长势。
2. 菜园中的作物必须根据各自生长周期随时间持续变大。

已完成：

- 菜园页新增“种菜”入口，原先没有入口的新增种植对话框已接回主流程。
- 新增种植不再默认选择方式；用户必须明确选择种子、种苗或插条，否则“开始种植”不可用。
- 日期字段会随方式显示为播种日期、移栽日期或扦插日期，并拒绝未来日期。
- 对话框会在保存前显示完整生长周期、方式对应的起始生长偏移和预计采收起点。
- `CropProfile` 增加种苗、插条的生长起始偏移；15 种内置作物均有各自配置。
- `GardenAdvisor` 已将种植方式纳入阶段、进度、任务和采收窗口计算。原始“已过天数”仍保留，预测使用“已过天数 + 方式偏移”。
- 主渲染器 `FarmAssetBoard` 读取 `CropVisualLibrary`：四张图片按每种菜自己的阶段天数切换，冠幅在阶段内按该作物的曲线逐日增长，不再使用全作物共用的生命周期缩放公式。
- 工具栏和选中详情会显示种植材料、日期、已过天数、估算生长日、阶段和进度。
- 设备实测番茄种苗在 2026-07-14 移栽时使用 +14 天起始生长，预计 2026-09-13 起可采收。
- 设备实测新增“番茄 · 种子”，持久化数据为 `method=SEED`、`startDate=2026-07-14`；放入菜畦后显示“估算生长第 0 天 · 缓苗/幼苗期 · 0%”，嫩芽明显小于较早种下的番茄。
- 新增 `GardenAdvisorTest` 和 `GrowthVisualsTest`。当前 4 个测试套件共 14 个测试，全部通过。
- 新增设备截图 `planting-method-date.jpg`、`daily-growth-size.jpg`，并更新 `docs/validation/2026-07-14/REPORT.md`。

本轮新增或修改的主要文件：

- `app/src/main/java/com/caicai/garden/data/GardenModels.kt`
- `app/src/main/java/com/caicai/garden/domain/GardenAdvisor.kt`
- `app/src/main/java/com/caicai/garden/domain/GrowthVisuals.kt`
- `app/src/main/java/com/caicai/garden/ui/FarmAssetBoard.kt`
- `app/src/main/java/com/caicai/garden/ui/FarmDesigner.kt`
- `app/src/main/java/com/caicai/garden/ui/GardenViewModel.kt`
- `app/src/main/java/com/caicai/garden/ui/VisualGardenApp.kt`
- `app/src/test/java/com/caicai/garden/domain/GardenAdvisorTest.kt`
- `app/src/test/java/com/caicai/garden/domain/GrowthVisualsTest.kt`
- `docs/validation/2026-07-14/`

需要注意：

- 种苗起始生长日是基于每种作物常见苗龄的估算，不是用户实际苗龄。UI 已明确标注这一点；若后续要求更精确，应增加“苗龄”输入，而不是继续提高固定偏移。
- 生长尺寸当前是确定性的周期进度函数，不会根据真实照片或株高反推。天气会影响预测进度，但没有替代用户的实际观察记录。
- 本轮修改尚未提交或推送；继续前先查看实时 `git status`，不要覆盖现有改动。

## 15. 2026-07-14 逐菜阶段图片与尺寸曲线实现

本节是当前最新状态，覆盖前文关于“全作物连续生命周期比例”的旧描述。

用户明确要求：

1. 图片必须按每种菜自己的阶段天数切换。
2. 成熟茄子、番茄、黄瓜冠幅约为 1.0-1.25 个格子，幼苗保持较小。
3. 不允许所有作物共用同一条尺寸曲线。

已完成：

- `GrowthVisuals.kt` 建立 15 份显式 `CropVisualProfile`，每份包含素材目录、四阶段冠幅区间、高度比例、阴影比例、风吹系数和增长曲线。
- 提供 `SMOOTH / FAST_EARLY / ROSETTE / LATE_SWELL / UPRIGHT` 五类阶段内增长曲线，不同作物按株型选择，且每种菜还有独立尺寸区间。
- 图片阶段直接与 `CropProfile.stages` 的日期边界对齐；`GardenAdvisor` 的文字阶段与 `FarmAssetBoard` 的图片阶段共用同一个 `CropVisualState`，避免文案和画面错位。
- 番茄采收阶段冠幅 `1.05 -> 1.20` 格，黄瓜 `1.05 -> 1.20` 格，茄子 `1.08 -> 1.25` 格；三者幼苗起始冠幅分别为 `0.30 / 0.28 / 0.30` 格。
- 叶菜、直立葱蒜、根菜、藤蔓和茄果类均有不同配置，不再调用全局 `growthVisualWidthFactor`。
- 空心菜和韭菜原先没有独立素材，现已新增各自的 `seedling / young / mature / harvest` 透明 PNG，并接入独立素材目录。
- 运行时使用的 15 种作物 x 4 阶段共 60 张图片已逐项检查存在。
- 设备保留原有数据覆盖安装 Debug APK；现有 2026-04-01 播种的第 104 天茄子显示为采收期、挂有成熟茄果，计算冠幅约 `1.17` 格，截图为 `docs/validation/2026-07-14/eggplant-day-104.jpg`。
- `GrowthVisualsTest` 现在覆盖完整配置、全作物逐日严格增长、周期后钳制、茄子/番茄/黄瓜关键阶段边界、空心菜/韭菜独立素材映射。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 与 `./gradlew :app:lintDebug --no-daemon --console=plain` 均为 `BUILD SUCCESSFUL`；4 个套件共 14 个测试，0 failure、0 error。

新增资源目录：

- `app/src/main/assets/farm_v2/sprites/crops_no_soil/water_spinach/`
- `app/src/main/assets/farm_v2/sprites/crops_no_soil/chive/`

当前 Debug APK 已安装并启动在 `emulator-5554`，用户可以直接体验。不要清除 App 数据，否则会丢失当前用于验收的第 104 天茄子记录。

## 16. 2026-07-14 田块间距与点选反馈调整

用户根据实际画面指出田块之间距离太大，且点选后出现的木质框含义不清楚，要求去除。

已完成：

- `FarmAssetBoard` 中草地和土壤田块的绘制比例由 `1.38` 提高到 `1.72`，田块可见尺寸约增加 25%，中心坐标、命中逻辑和作物尺寸不变。
- 普通点选已彻底停止加载和绘制 `selected_frame.png`，点选后仍正常更新下方作物详情和编辑操作。
- 拖拽时的 `target_cell_frame.png` 仍保留，仅在正在拖动并预览落点时出现，避免失去移动反馈。
- Debug APK 已保留数据覆盖安装到 `emulator-5554`；设备截图为 `docs/validation/2026-07-14/tight-tiles-no-selection-frame.jpg`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 验证通过。

## 17. 2026-07-14 透视投影与地形堆叠修复

用户放大查看后指出田块仍像方块堆在一起，并明确质疑当前是否缺少基于视角的远小近大效果。核对截图和代码后确认问题真实存在：上一版只是把田块贴图从 `1.38` 放大到 `1.72`，但渲染顺序仍是“先画全部草地，再画全部土块”，导致种植格同时存在草地底块和土壤上层，放大后尤其像堆箱子；原投影也是所有深度同尺寸的正交等距投影。

本轮已完成：

- 新增透视深度投影。以 `row + column` 作为景深，远端单格缩放为 `0.88x`，近端连续增长到 `1.08x`。
- 横向收敛、纵向间距、田块尺寸、作物冠幅、结构尺寸、成熟徽标和拖拽目标框统一使用同一 `FarmCellProjection`。
- `cellAt` 改为逐格使用透视菱形命中，不再用只适用于正交网格的反算取整；设备已实测可点中远端第 104 天茄子。
- 地形拆成独立层，每个格子只选择绘制草地或土块，不再在同一格叠两层。
- 地形与作物分别按 `row + column` 从远到近绘制；前景作物自然遮挡后景作物。
- 拖拽源格会恢复草地，拖起的土块和作物作为一个整体绘制，落点仍使用透视后的格子中心。
- 土壤田块不再跟随作物旋转，旋转只作用于作物或结构，避免等距地块偏离网格。
- 已在默认视图和两级放大视图验收，放大截图为 `docs/validation/2026-07-14/perspective-depth-grid.jpg`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 通过，Debug APK 已保留数据覆盖安装到 `emulator-5554`。

当前仍是高质量 2.5D 透视渲染，不是真正的 SceneView/Filament 3D 网格；但本轮已补齐用户指出的景深缩放、透视命中和正确遮挡关系。若后续切真 3D，仍需先补齐满足产品约束的 GLB 资产和完整编辑交互，不能直接启用现有低模 POC。
