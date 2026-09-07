# Native Android Verification

日期：2026-09-06

后续图片导入变更与验证范围见 [2026-09-07 图片导入验证](2026-09-07-image-import-verification.md)。本文件记录早期构建结果，不代表完整功能验收。

## 环境

- JDK：Microsoft OpenJDK 21.0.12
- Gradle：8.14.3 wrapper
- Android SDK：`D:\Android\Sdk`
- Platform：`android-36`
- Build Tools：`36.0.0`
- 构建入口：`apk/android/gradlew.bat`

## 已运行命令

在 ASCII junction `D:\kebiao-build` 下执行，避免当前中文工作路径触发 Gradle 测试类加载异常：

```text
:app:testDebugUnitTest --no-daemon --console=plain
:app:assembleDebug --no-daemon --console=plain
```

最近一次结果：`BUILD SUCCESSFUL`。Debug APK 位于：
`apk/android/app/build/outputs/apk/debug/app-debug.apk`

测试覆盖：领域单双周/调休、JSON 导入兼容、ViewModel CRUD、提醒规划、OCR 字段解析与确认门槛、MCP Token 和工具授权。

## 已验证的代码边界

- Android 主入口使用 Compose，不再使用 WebView 作为主界面。
- Room/DataStore 是原生数据层，Web/PWA 继续通过 JSON 契约交换数据。
- `ScheduleResolver` 是课程、提醒和 Widget 共同使用的规则入口。
- 通知权限、精确闹钟、重启恢复和 Glance Widget 已接线。
- OCR 模型管理代码存在；真实模型包、按用户选择下载及推理流程尚未验收。草稿逐字段确认入口已在后续变更中接入。
- MCP 默认关闭，启用后通过固定 Token 和局域网地址过滤。

## 尚未完成的外部验证

- 尚未在真实 Android 设备上验证通知、Widget、精确闹钟和厂商后台限制。
- PaddleOCR ONNX 推理引擎仍需接入真实模型包；当前已完成模型管理和坐标/字段解析接口，并提供 ML Kit 中文离线回退引擎。
- OpenAI 请求与响应解析已有实现；API Key 以 Keystore 管理的密钥加密后写入应用私有偏好设置。真实 Key/图片端到端验证尚未完成。
- MCP 已连接应用设置并提供局域网 HTTP JSON-RPC 分发；仍需用真实 Agent 客户端做一次互操作测试。
