# 参与开发

欢迎通过 [Issues](https://github.com/RTXwcz/Class-Schedule/issues) 报告问题，或提交 Pull Request。

## 环境与工作流

1. Fork 并克隆仓库，安装 JDK 21、Android SDK 36、Node.js 22+。
2. 在 `apk/` 执行 `npm ci`、`npm run sync`，再打开 `apk/android`。
3. 在独立分支开发。原生 Kotlin 源码位于 `apk/android/app/src/main/java/com/kebiao/app/`。
4. 修改 Web 主页面或数据契约后，执行 `tools/sync-web.ps1` 同步 `apk/www/` 镜像。
5. 运行与改动相关的测试。日期/周次/调休逻辑应覆盖 `ScheduleResolver`；数据交换改动需同时覆盖 Kotlin 和 Web 契约。

```powershell
# 仓库根目录
node --test tests/*.test.cjs
cd apk/android
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

设备测试使用匹配架构的 Android 手机。x86_64 模拟器的本地 UI 验证使用 `-PlocalUiQa=true` 构建 debug 与 AndroidTest 包；该选项会拒绝 release 任务，测试包不可上传发布。OCR 的显式下载测试会下载模型；已有模型时取消测试可能按条件跳过。

## 提交信息

请描述具体问题、修改后的行为和验证结果。报告 OCR 问题时，可以提供已脱敏课表、所选模型和识别结果；不要提交 API Key、MCP Token 或真实个人课表备份。

原生 UI 是主要实现，Web 保留独立入口。JSON 元数据和未知扩展字段需要往返保留。课程、提醒、Widget 和 MCP 应复用相同领域规则。

## 许可

提交本项目自有代码即表示按 GPL-3.0-only 授权该贡献。引入第三方源码时，保留其原有版权和许可证，更新来源记录、`THIRD_PARTY_NOTICES.md` 及 `licenses/`。发布签名密钥和凭据由维护者在仓库外保管；贡献者可用自己的密钥构建。
