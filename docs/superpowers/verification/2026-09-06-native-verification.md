# Native Android Verification

日期：2026-09-06

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
- OCR 模型下载支持私有目录、临时文件和 SHA-256 校验；草稿必须逐字段确认。
- MCP 默认关闭，启用后通过固定 Token 和局域网地址过滤。

## 尚未完成的外部验证

- 尚未在真实 Android 设备上验证通知、Widget、精确闹钟和厂商后台限制。
- PaddleOCR ONNX 推理引擎仍需接入真实模型包；当前已完成模型管理和坐标/字段解析接口，并提供 ML Kit 中文离线回退引擎。
- OpenAI 图片请求、JSON 响应解析和 API Key Keystore 存储已完成；仍需真实 Key/图片做端到端验证。
- MCP 已连接应用设置并提供局域网 HTTP JSON-RPC 分发；仍需用真实 Agent 客户端做一次互操作测试。
