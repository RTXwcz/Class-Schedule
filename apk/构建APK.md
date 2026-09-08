# 构建安卓 APK 步骤

`android/` 是 Kotlin + Jetpack Compose 原生应用，使用 Room 保存课表及其学期、单双周和作息规则；DataStore 保存界面、提醒和连接偏好。Android 工程直接由 Gradle 构建。Web/PWA 由仓库根目录独立提供，`www/` 保留发布副本，网页资源不打入 APK。

环境要求：**JDK 21**、**Android SDK Platform 36** 与 **Build Tools 36.0.0**。项目的 Java 与 Kotlin 编译目标均为 21，Gradle Wrapper 固定构建工具版本。

全新克隆可直接运行 Gradle，无需 `npm ci` 或 `cap sync`。Node.js 22+ 只用于 Web 回归与可选图标生成。

## 方式一：Android Studio（推荐，最简单）

1. 安装 [Android Studio](https://developer.android.com/studio)，在 SDK Manager 安装上述 Android SDK，并将 Gradle JDK 配置为 JDK 21。
2. 启动 Android Studio → **Open** → 选择本目录里的 `android` 文件夹。
3. 首次打开会自动 Gradle 同步（联网下载依赖，约几分钟，耐心等右下角进度条结束）。
4. 顶部菜单 **Build → Build App Bundle(s) / APK(s) → Build APK(s)**。
5. 完成后右下角弹出提示 → 点 **locate**，在 `apk/android/app/build/outputs/apk/debug/` 得到 `app-arm64-v8a-debug.apk` 和 `app-armeabi-v7a-debug.apk`。
6. 把这个 `.apk` 传到手机（微信/数据线/网盘均可），点击安装；首次安装需在系统设置里允许「安装未知来源应用」。

## 方式二：命令行

```powershell
# 1. 安装 JDK 21 并设置 JAVA_HOME
# 2. 安装 Android SDK cmdline-tools，并将其 bin 与 platform-tools 加入 PATH：
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses     # 一路输入 y 同意

# 3. 设置 ANDROID_HOME 指向 SDK，或在 apk/android/local.properties 写入 sdk.dir
# 4. 构建（Windows 用 ./gradlew.bat，macOS/Linux 用 ./gradlew）
cd apk/android
./gradlew.bat :app:assembleDebug
```

如果项目路径包含中文字符，Gradle 单元测试在部分 Windows/JDK 组合下可能出现测试类加载失败。可使用 ASCII junction 指向项目目录后运行测试，源码和 APK 输出仍来自同一工作区：

```powershell
New-Item -ItemType Junction -Path D:\kebiao-build -Target D:\课表
cd D:\kebiao-build\apk\android
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

默认调试和正式构建仅产生 ARM64、ARMv7 两个包，不生成通用包。发布文件始终不含 x86/x86_64。

本地 x86_64 模拟器的 UI 验证可使用专用调试选项：

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PlocalUiQa=true
```

该选项只允许明确的 debug/clean 任务，若任务图包含 release 会直接拒绝构建。其 `app-x86_64-debug.apk` 仅用于本地测试，不上传 Release；正式构建不要传这个参数。

## 原生配置

| 项目 | 位置 | 当前值 |
|---|---|---|
| 应用名称 | `android/app/src/main/res/values/strings.xml` | 我的课表 |
| 包名（唯一标识） | `android/app/build.gradle` → `applicationId` | com.kebiao.app |
| 应用图标 / 启动图源文件 | `assets/icon.png`（1024×1024）、`assets/splash.png`（2732×2732） | 生成到 `android/app/src/main/res/` |

- 原生代码位于 `android/app/src/main/java/com/kebiao/app/`，直接修改后运行 Gradle。
- 修改包名需要同时检查 Kotlin namespace、Manifest 组件及测试包名。
- 图标生成工具与 Android 运行时独立。需要更新图标或启动图时，在 `apk/` 执行 `npm ci`，替换源图后运行 `npm run assets:android`，检查生成资源再构建 APK。`capacitor.config.json` 仅保留为该工具的项目配置；不要执行 `cap sync`，它会重新创建已移除的 WebView 桥接配置。

## Web 副本与原生数据交换

1. Web 主文件是根目录 `课表.html`，数据契约是 `schedule-contract.js`；在仓库根目录执行 `./tools/sync-web.ps1`，将两者同步到 `apk/www/index.html`、`apk/www/schedule-contract.js`。
2. Web 与原生通过完整 JSON 导入导出交换课程、考试、日程、调休和数据集元数据。Web 自动视图应用周次及调休，其他筛选模式保留模板查看行为。
3. 网页修改不会改变 Compose 原生界面，不需要重建 Android 工程。

## OCR 与 MCP

- APK 不包含 OCR 权重；用户点击下载后将 PP-OCRv6 tiny（6.3 MB）或 small（31.2 MB）存入应用私有目录，校验后可断网识别。所有导入字段须确认后保存。
- OpenAI 模式需要自填 API Key，可配置兼容的 Chat Completions 图像接口地址和模型。
- MCP 默认关闭。在设置中开启后，Agent 使用页面显示的 IPv4 地址和 `/mcp` 路径，携带 `Authorization: Bearer <Token>`，通过 Streamable HTTP 连接。Token 可复制和轮换；清空数据始终需在应用确认。
- 设置中的学期日期定义第 1 周；之后每 7 天递增。单双周和明确周次共同限制课程，调休仅替换该日期的星期课程。

历史验证与各版本证据见 [文档导航](../docs/README.md)。运行测试时请记录实际 Android 版本、设备类型和测试范围。

## 发布签名

GitHub Releases 提供正式签名 APK。`assembleDebug` 生成开发测试包，二者签名不同，不能互相覆盖安装；先导出 JSON 再切换。

发布构建从四个环境变量读取签名信息：`CLASS_SCHEDULE_KEYSTORE`、`CLASS_SCHEDULE_STORE_PASSWORD`、`CLASS_SCHEDULE_KEY_ALIAS`、`CLASS_SCHEDULE_KEY_PASSWORD`。未设置密钥时 `assembleRelease` 生成 unsigned APK。维护者必须检查签名后再发布。

Windows 维护者可使用 `tools/build-release.ps1`：传入仓库外的 PKCS12 密钥和由 `Export-Clixml` 保存的 PSCredential 文件。脚本仅在构建进程环境中传递密码，结束后清除这些环境变量。凭据受 Windows 当前用户 DPAPI 保护，迁移机器前需要安全备份可恢复的密钥密码。

```powershell
./tools/build-release.ps1 -Keystore D:/Android/Signing/Class-Schedule/release.p12 `
  -CredentialFile D:/Android/Signing/Class-Schedule/release.credentials.xml
```

已签名产物：`apk/android/app/build/outputs/apk/release/app-arm64-v8a-release.apk` 和 `app-armeabi-v7a-release.apk`。不生成通用 APK。不要将签名密钥或密码提交到 Git。Fork 可使用自己的密钥签名安装；不同密钥之间切换时需备份数据并重新安装。
