# 菜园管家项目交接文档

更新时间：2026-07-15

工作目录：`/Users/bytedance/Documents/caicai`

## 1. 给下一会话的直接指令

请先阅读本文件，再检查本地 Git 状态和当前真机/模拟器画面。不要只凭 README 或历史截图判断当前实现。

当前最重要的事实：

- 项目已经创建 GitHub 仓库、提交并推送，不要重复初始化仓库。
- `v1.3.0` 已通过 GitHub Releases 正式发布，App 内自动/手动升级能力已经实现。
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

截至 2026-07-15 的实时核对结果：

- 当前分支：`main`
- `v1.3.0` 发布代码已提交并推送；最新发布验证记录也已推送到 `main`，继续前仍应以实时 `git status` 为准。
- 已提交代码与远端一致：`main...origin/main`。
- 远端：`git@github.com:JesseZhao1990/caicai-garden.git`
- 仓库：[JesseZhao1990/caicai-garden](https://github.com/JesseZhao1990/caicai-garden)
- 可见性：`PUBLIC`
- `v1.3.0` 发布提交：`4aec9a1 Release garden terrain and crop visual fixes`
- 版本标签：`v1.3.0` 指向提交 `4aec9a172a808cbdb5add11eaa8e896f6d85e5dd`

最近提交：

```text
4aec9a1 (tag: v1.3.0) Release garden terrain and crop visual fixes
38214d6 Document v1.2.0 release verification
20673e2 Fix release notes file lookup
13db698 (tag: v1.2.0) Release garden growth and layout improvements
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
versionCode = 4
versionName = 1.3.0
applicationId = com.caicai.garden
minSdk = 26
targetSdk = 35
```

正式 Release：

- 地址：[菜园管家 v1.3.0](https://github.com/JesseZhao1990/caicai-garden/releases/tag/v1.3.0)
- APK：`app-release.apk`
- 大小：`71,302,263` bytes
- SHA-256：`ce5e08b4d23da3bcc0767596d08762041cd75af46a05df568077f6c60e8a595a`
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
- `docs/releases/v1.3.0.md`

发布下一个版本：

1. 提升 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`。
2. 提交并推送代码。
3. 创建与版本名一致的 tag，例如 `v1.4.0`。
4. 推送 tag 后，`Publish Android release` GitHub Actions 会构建固定签名 APK 并创建 Release。

```bash
git tag -a v1.4.0 -m "菜园管家 v1.4.0"
git push origin v1.4.0
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

## 18. 2026-07-14 v1.2.0 正式发布

本节是当前最新发布状态，覆盖前文关于 `v1.1.0` 和“修改尚未提交”的旧描述。

- App 版本：`versionCode = 3`、`versionName = 1.2.0`。
- 功能提交：`13db698 Release garden growth and layout improvements`。
- 正式 tag：`v1.2.0`，指向 `13db698d09f3915615a05fec3790b67ecf38175a`。
- Release：[菜园管家 v1.2.0](https://github.com/JesseZhao1990/caicai-garden/releases/tag/v1.2.0)。
- GitHub Actions：`Publish Android release` run `29345420104`，执行成功。
- 正式 APK：`app-release.apk`，大小 `68,905,371` bytes。
- SHA-256：`f8c8c4b1ab389a6a2aa4e4b3e87e221d46fb0e51a7c834129db8013aa3a52844`，本地下载计算值与 GitHub Release digest 一致。
- APK 元数据：包名 `com.caicai.garden`、版本 `1.2.0 (3)`、minSdk 26、targetSdk 35。
- APK 使用 v2 签名，证书 SHA-256 为 `5ea7bcef03b70be6ce88049a4e0dc7f0127ec5fddb20d53022e41129687c07a1`，与 v1.1.0 完全一致，可覆盖升级正式版。
- 发布说明已补充为完整的 v1.2.0 功能清单。工作流 notes-file 路径修复提交为 `20673e2`，后续版本会自动读取 `docs/releases/vX.Y.Z.md`。

发布前验证包括 14 个单元测试、Debug/Release 构建、Android Lint、YAML 解析、APK 元数据、APK 签名、Release digest 和线上 Release 状态，全部通过。

## 19. 2026-07-15 菜地与背景自然融合

用户指出 8 x 8 菜地像一整块悬浮草皮，与周围花园背景割裂。代码与素材核对后确认，原实现会为 64 个格子常驻绘制带深色土壤侧壁的 `grass_tile.png`，即使格子没有作物也会组成完整的厚草块边界。

本轮已完成：

- 查看模式不再绘制任何空格草块，空白区域直接透出连续的花园草地背景，彻底移除整块草皮的外轮廓和厚侧壁。
- 根据用户后续反馈补回“整体菜地轮廓”：沿 8 x 8 外围格子的透视顶点生成连续边界，使用低透明度草绿色区域、柔和土色外缘和浅色草缘细线表达范围；不恢复任何单格立体草块。
- 使用内置图片生成模式制作 `flat_soil_patch.png`：平铺的等距土壤、紧凑草缘、透明背景，不含木框、容器、厚侧壁或投影。
- 只有 `RAISED_BED` 格子绘制薄土层；土壤继续使用既有远小近大的 `FarmCellProjection`，与作物保持同一透视尺度。
- 土壤素材按有效内容边界校准为 `1.16x`，并以 `0.96` 透明度轻微吸收背景草色；作物根部接触点从格高 `0.08` 调整为 `0.03`，避免悬浮。
- 8 x 8 菱形格线改为仅在移动、清空、种植或拖拽期间显示，查看模式完全隐藏；辅助线为低透明度绿色描边，不再依赖立体草块表达可编辑区域。
- 保留此前的景深缩放、从远到近遮挡顺序、透视命中、拖拽落点框和占用格交换行为。
- 已保留用户数据覆盖安装到 `emulator-5554`，默认查看和两级放大视图均实测；当前模拟器停留在菜园查看模式，可直接体验。
- 验证截图：`docs/validation/2026-07-15/natural-ground-blend.jpg`、`docs/validation/2026-07-15/natural-ground-edit-grid.jpg`、`docs/validation/2026-07-15/natural-ground-boundary.jpg`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 与 `./gradlew :app:lintDebug --no-daemon --console=plain` 均为 `BUILD SUCCESSFUL`；4 个测试套件共 14 个测试，0 failure、0 error。

本轮新增或修改：

- `app/src/main/assets/farm_v2/sprites/terrain/flat_soil_patch.png`
- `app/src/main/java/com/caicai/garden/ui/FarmAssetBoard.kt`
- `docs/validation/2026-07-15/`
- `docs/NEXT_SESSION_HANDOFF.md`

当前改动尚未提交或发布。继续前先查看实时 `git status`，不要覆盖这些文件。

## 20. 2026-07-15 菜地内部改为整片土地

本节是当前最新视觉状态，覆盖第 19 节中“背景草地延伸到菜地内部、每个种植格单独绘制薄土层”的中间方案。

用户明确要求菜地范围内不要草坪，只保留土地。已完成：

- 8 x 8 透视轮廓内部现在是一整片连续耕作土壤，外围继续显示花园草坪，菜地范围一眼可见。
- 使用内置 imagegen 模式生成 `continuous_soil_texture.png`：俯视、均匀光照、暖棕色耕作土，无草、植物、边框、透视块、阴影和焦点元素。
- 土壤纹理只绘制一次，并通过 `clipPath` 裁进既有透视外围路径；不按 64 个格子重复拼块。
- 移除运行时对 `flat_soil_patch.png` 的引用和素材文件；普通作物与拖拽作物都不再携带独立方形土块，直接落在连续土地上。
- 查看模式隐藏内部网格；移动、清空、种植和拖拽时叠加深浅双层定位线，在棕色土壤上仍清晰可见。
- 保留远小近大投影、透视命中、从远到近遮挡、作物阶段尺寸曲线和拖拽交换逻辑。
- 已保留数据覆盖安装到 `emulator-5554`，默认视图、两级放大和移动模式均完成设备验收；模拟器当前停留在两级放大的查看模式。
- 最终截图：`docs/validation/2026-07-15/continuous-soil-garden.jpg`、`docs/validation/2026-07-15/continuous-soil-edit-grid.jpg`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 与 `./gradlew :app:lintDebug --no-daemon --console=plain` 均为 `BUILD SUCCESSFUL`；4 个测试套件共 14 个测试，0 failure、0 error。

当前新增或修改：

- `app/src/main/assets/farm_v2/sprites/terrain/continuous_soil_texture.png`
- `app/src/main/java/com/caicai/garden/ui/FarmAssetBoard.kt`
- `docs/validation/2026-07-15/`
- `docs/NEXT_SESSION_HANDOFF.md`

当前改动尚未提交或发布。

## 21. 2026-07-15 土壤颗粒比例调整

用户指出连续土壤版本的泥土颗粒偏大，与作物和背景比例不协调。

本轮已完成：

- 保留 `continuous_soil_texture.png` 的颜色和材质，不重新生成或改变菜地结构。
- 将原先“单张纹理裁切铺满整个 8 x 8 菜地”改为 `BitmapShader` 连续重复采样。
- 单次纹理显示宽度设为约 `4.2` 个格子，颗粒视觉尺寸缩小到上一版约 `52%–55%`。
- Shader 使用双向 `REPEAT` 并继续由整体透视路径裁切；默认视图和两级放大视图均未发现明显接缝。
- 菜地仍是一整片土地，不恢复草坪、单格土块或常驻网格；编辑态定位线逻辑不变。
- 已覆盖安装到 `emulator-5554`，当前停留在两级放大的查看模式。
- 验收截图：`docs/validation/2026-07-15/fine-soil-texture.jpg`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 与 `./gradlew :app:lintDebug --no-daemon --console=plain` 均为 `BUILD SUCCESSFUL`；4 个测试套件共 14 个测试，0 failure、0 error。

本轮没有新增图片素材，只修改 `FarmAssetBoard.kt` 的纹理采样比例和绘制方式。当前改动尚未提交或发布。

## 22. 2026-07-15 平面土地方格、泥沟与白菜素材修复

本节是当前最新视觉状态，覆盖第 20、21 节中“查看态不显示单格边界”的中间方案。

用户最终明确希望保留方格，但方格之间的间隙必须是泥土小沟，不能是草，也不能恢复立体草块。本轮已完成：

- 连续土壤仍作为整个 8 x 8 菜地的底层，并额外压暗为泥沟底色；外围继续保留花园草坪和清晰的菜地轮廓。
- 每个格子常驻绘制一个内收的平面等距土地方格，半宽/半高比例为 `0.43`，格子之间自然露出窄而连续的深色泥沟。
- 单格只有轻微的土色明暗边，不绘制厚侧壁、草皮、木框或悬浮阴影；查看态边缘较柔和，移动/编辑态略微增强，定位仍清楚。
- 土壤纹理单次重复宽度由约 `4.2` 格调整为 `3.0` 格，颗粒再次缩小；方格与底层泥沟共用同一纹理，因此不会形成草色拼缝。
- 原 `bok_choy/harvest.png` 顶部存在硬裁切，确实会导致白菜叶片显示不全；已使用内置 imagegen 模式生成完整的单株成熟白菜，并处理为透明背景的 512 x 512 PNG，替换收获期素材。
- 右上角绿色圆圈不是白菜图片内容，而是代码对所有收获期作物叠加的 `mature_ready_badge.png`；现已停止预加载和绘制该徽标，成熟状态仍保留在文字详情中。
- 已保留用户数据覆盖安装到 `emulator-5554`；查看态、移动态和两级放大画面均已验证。模拟器当前停留在两级放大的查看模式，可直接体验。
- 验收截图：`docs/validation/2026-07-15/mud-furrow-grid.jpg`、`docs/validation/2026-07-15/mud-furrow-edit-grid.jpg`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 与 `./gradlew :app:lintDebug --no-daemon --console=plain` 均为 `BUILD SUCCESSFUL`；4 个测试套件共 14 个测试，0 failure、0 error。

本轮新增或修改：

- `app/src/main/assets/farm_v2/sprites/crops_no_soil/bok_choy/harvest.png`
- `app/src/main/assets/farm_v2/sprites/terrain/continuous_soil_texture.png`
- `app/src/main/java/com/caicai/garden/ui/FarmAssetBoard.kt`
- `docs/validation/2026-07-15/`
- `docs/NEXT_SESSION_HANDOFF.md`

以上改动进入 `v1.3.0` 发布提交，正式发布结果见后续记录。

## 23. 2026-07-15 v1.3.0 正式发布

本节是当前最新发布状态，覆盖第 18 节的 `v1.2.0` 发布信息。

- App 版本：`versionCode = 4`、`versionName = 1.3.0`、包名 `com.caicai.garden`。
- 发布代码提交：`4aec9a172a808cbdb5add11eaa8e896f6d85e5dd`，提交说明为 `Release garden terrain and crop visual fixes`。
- 正式 annotated tag：`v1.3.0`，指向上述发布提交。
- Release：[菜园管家 v1.3.0](https://github.com/JesseZhao1990/caicai-garden/releases/tag/v1.3.0)，状态为非草稿、非预发布，并已成为 latest release。
- GitHub Actions：[Publish Android release run 29393453543](https://github.com/JesseZhao1990/caicai-garden/actions/runs/29393453543)，`build-and-publish` 全部步骤成功。
- 正式 APK：`app-release.apk`，大小 `71,302,263` bytes。
- GitHub asset digest 与独立下载后计算的 SHA-256 均为 `ce5e08b4d23da3bcc0767596d08762041cd75af46a05df568077f6c60e8a595a`。
- APK 元数据实测为 `com.caicai.garden`、`1.3.0 (4)`。
- APK 使用 v2 签名；证书 SHA-256 为 `5ea7bcef03b70be6ce88049a4e0dc7f0127ec5fddb20d53022e41129687c07a1`，RSA 3072-bit，与之前正式版本一致。
- 公共 latest-release API 已返回 `v1.3.0`、`app-release.apk` 和相同 digest，App 内升级检查可以直接发现该版本。
- 发布前 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintDebug` 全部成功；4 个测试套件共 14 个测试通过。

本次 Release 包含第 19-22 节记录的自然菜地轮廓、连续细颗粒土壤、平面土地方格与泥沟、白菜完整素材以及成熟圆标移除。发布验证完成后，交接文档单独提交并推送到 `main`；tag 仍固定指向发布代码提交。
