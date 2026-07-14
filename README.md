# 菜园管家 Android MVP

一个面向家庭菜园/阳台种植的 Android 第一版应用。当前版本使用 Kotlin + Jetpack Compose 实现，数据保存在本机，天气通过 Open-Meteo 免 key 接口获取。

## 已实现功能

- 今日工作台：天气、今日待办、作物状态。
- 菜园管理：默认示例菜园，支持编辑菜园位置、添加地块。
- 自定义农场：在菜园页按真实种植批次摆放植物，并可布置石板路、灌溉、小温室、农具棚、木栅栏、标识牌等元素。
- 种植批次：支持选择作物、地块、播种/移栽日期、数量。
- 作物档案：内置番茄、黄瓜、辣椒、茄子、生菜、小白菜、菠菜、空心菜、香菜、葱、韭菜、萝卜、胡萝卜、豆角、西葫芦。
- 智能提醒：根据作物阶段、天气、上次操作生成浇水、施肥、采摘、天气巡查、照片记录任务。
- 日历：展示未来 7 天任务。
- 操作记录：支持记录浇水、施肥、采摘、拍照、除草、修剪、防虫等操作。

## 构建

```bash
gradle :app:assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装到设备

连接 Android 设备或启动模拟器后：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 版本发布与升级

- 源码和可安装 APK 都发布在公共仓库 `JesseZhao1990/caicai-garden`，APK 位于 GitHub Releases。
- App 每次启动会自动检查最新正式版，“今日”页的“版本升级”卡片也支持手动检查。
- 发现新版本后，App 会下载 APK、校验 GitHub 提供的 SHA-256 摘要，并交给 Android 系统安装器确认升级。
- 如果设备上安装的是此前的 Debug 开发包，需要先卸载再安装一次正式版；之后的 Release 都使用同一发布签名，可以直接覆盖升级。

发布新版本时：

1. 更新 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`。
2. 提交并推送代码。
3. 创建与 `versionName` 一致的标签，例如 `git tag v1.2.0 && git push origin v1.2.0`。
4. GitHub Actions 会构建固定签名的 Release APK，并发布到本仓库的 GitHub Releases。

也可以在源码仓库的 Actions 页面手动运行 `Publish Android release` 工作流，发布当前 `versionName`。

## 当前边界

- 第一版不含账号系统，数据只保存在本机。
- 天气位置通过菜园信息里的经纬度配置。
- 生长阶段和提醒使用可解释规则模型，还未接入图片识别、传感器或后端同步。
