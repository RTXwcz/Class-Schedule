# 图片导入阶段验证

日期：2026-09-07。

## 本轮实现

- 原生导入页通过 Android 文件选择器选择 JPG、PNG、WebP；发送前展示原图、目标 API 地址和模型。
- 请求使用结构化 JSON，保留实际图片 MIME；限制图片为 10 MB，限制响应为 2 MB 文本，拒绝重定向及非 HTTPS 地址，关闭连接。
- 响应转换为待审阅草稿。无法识别的星期、节次和周次保留为空，不能自动作为每周课程保存；模型未提供的置信度不再显示为 100%。
- 原图预览和课程名称、星期、起止节次、周次、教学楼、教室、备注均进入审阅页。各字段独立确认，修改后取消确认，可移除误识别课程。
- 审阅通过后由 Room 事务追加课程，保留既有考试、课程和调休；保存失败保留草稿和错误信息。
- API Key 隐藏输入并使用本机加密保存；API 地址、模型改为显式保存。ViewModel 由 ViewModelProvider 持有，支持 Activity 重建时保留本次会话草稿。

## 验证证据

使用既有 Microsoft JDK 21 和 `D:\Android\Sdk`，在 `D:\kebiao-build\apk\android` 执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL in 52s`。31 项 JVM 测试通过，无失败或错误，其中 6 项新测试覆盖图片格式、JSON 转义、空字段、未知周次、截断响应及地址校验。

新增 Room 设备测试验证追加课程后旧课程、考试和调休仍存在。该测试已编译，**尚未在设备上运行**。ADB 当前没有设备，SDK 尚无 emulator/system-images。

APK：`apk/android/app/build/outputs/apk/debug/app-debug.apk`。

官方图片输入格式依据：https://developers.openai.com/api/docs/guides/images-vision （本轮已读取）。

## 未验证与后续工作

- 尚未使用真实 Key 调用 API，也未验证手机上的选图、预览、逐项确认、旋转和落库完整流程；构建通过不等于这些流程已验收。
- 原有其他编辑入口仍以 UI 快照替换整库，应迁移到按记录写入，避免与追加导入及 Agent 写入发生竞争。
- 本地 OCR 当前仍有捆绑式 ML Kit 依赖，尚不满足选择后下载模型的约定；实际模型包与移动端推理仍需完成。
- MCP 标准客户端互操作、通知可靠性与小组件刷新仍需继续实现和运行验证。
- 草稿目前仅跨 Activity 重建保留，进程被系统回收后仍需重新识别。

本轮未新增安装环境。开始时 C/D 盘分别剩余约 40.8/78 GB。
