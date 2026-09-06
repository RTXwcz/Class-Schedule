# 构建安卓 APK 步骤

本目录 `apk/` 是一个完整的 Capacitor 8 安卓工程，网页代码已打包进 `www/` 并同步到 `android/`。只需在装有 Android 开发环境的电脑上执行一次构建即可得到 `.apk`。

> 环境要求：**JDK 17 或 21** + **Android SDK（Platform 36）**。最省事的是直接装 **Android Studio（最新版）**，它自带 JDK 和 SDK。

## 方式一：Android Studio（推荐，最简单）

1. 安装 [Android Studio](https://developer.android.com/studio)，一路默认安装。
2. 启动 Android Studio → **Open** → 选择本目录里的 `android` 文件夹。
3. 首次打开会自动 Gradle 同步（联网下载依赖，约几分钟，耐心等右下角进度条结束）。
4. 顶部菜单 **Build → Build App Bundle(s) / APK(s) → Build APK(s)**。
5. 完成后右下角弹出提示 → 点 **locate**，得到 `apk/android/app/build/outputs/apk/debug/app-debug.apk`。
6. 把这个 `.apk` 传到手机（微信/数据线/网盘均可），点击安装；首次安装需在系统设置里允许「安装未知来源应用」。

## 方式二：命令行

```bash
# 1. 安装 JDK 17+ 并设置 JAVA_HOME
# 2. 安装 Android SDK cmdline-tools，然后：
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses     # 一路输入 y 同意

# 3. 构建（Windows 用 gradlew.bat，macOS/Linux 用 ./gradlew）
cd apk/android
gradlew.bat assembleDebug
```

产物同样在 `apk/android/app/build/outputs/apk/debug/app-debug.apk`。

## 需要自定义的三处

| 项目 | 位置 | 当前值 |
|---|---|---|
| 应用名称 | `capacitor.config.json` → `appName` | 我的课表 |
| 包名（唯一标识） | `capacitor.config.json` → `appId` | com.kebiao.app |
| 应用图标 / 启动图 | `assets/icon.png`（1024×1024）、`assets/splash.png`（2732×2732） | 占位图（渐变+表格纹样） |

- 改**应用名**：改 `capacitor.config.json` 后执行 `npx cap sync android`。
- 改**包名**：改 `capacitor.config.json` 后重新执行 `npx cap add android`（会重建 android 目录）。
- 换**正式图标**：把两张源图替换后执行 `npx capacitor-assets generate --android`。

## 修改网页代码后重新打包

1. 编辑根目录的 `课表.html`（主程序）。
2. 同步到工程：`cp "课表.html" "apk/www/index.html"`。
3. `cd apk && npx cap sync android`。
4. 重新 Build APK。

## 关于签名（重要）

- `debug` 版用的是调试签名，**只能自用**，部分手机/安全软件可能提示风险，属正常。
- 若要**长期使用或分发**，需要生成正式签名（release keystore）再打 `release` 包。需要的话我可以给出 release 签名的具体配置步骤。
