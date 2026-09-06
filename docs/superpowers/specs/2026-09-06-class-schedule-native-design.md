# 课表 Android 原生化设计

日期：2026-09-06

状态：待用户审阅

## 目标

将 Android 版本从 Capacitor WebView 外壳升级为 Kotlin + Jetpack Compose 原生应用，同时保留当前 PWA/Web 版本。首版覆盖课程、考试、调休、通知、桌面小组件、本地 OCR 导入、OpenAI 图片导入和局域网 MCP 服务，并保留未来同步所需的数据元信息。

## 已确认的产品行为

- Android 原生应用是主应用，Web 版继续维护。
- 课程提醒默认在上课前 10 分钟发送，用户可调整提醒提前量。
- 只有当前日期实际生效的课程才产生提醒；单双周和整天调休都参与判断。
- 调休规则采用“指定日期按目标星期课程替换”。例如某个周四调休为周一，则当天显示周一课程，不显示原周四课程。
- 课程地点拆为教学楼、教室和可选备注。
- 桌面小组件显示从当前时间开始的未来三节实际课程，应用打开时立即刷新，系统也负责周期刷新。
- 首次进入应用请求通知权限；精确闹钟用于可靠触发提醒，并在重启、时区变化、课程修改后重建。
- 本地 OCR 默认不下载。用户在开始界面选择使用本地 OCR 后，才下载模型到应用私有目录。识别结果必须逐项确认后才能写入课程表。
- OpenAI 图片导入使用用户自己的 API Key，接口地址可配置，默认官方接口。请求前展示图片和字段范围。
- MCP 只监听本机和局域网，使用固定 Token；Token 首次启用时随机生成，用户可轮换。写入操作是否逐次在 App 确认由设置项控制。
- 数据先本地保存，同时保留 `schemaVersion`、数据集 ID、更新时间和来源字段，为未来同步预留。

## 总体架构

```
Android App
  Compose UI
      |
  ViewModel / UseCase
      |
  ScheduleRepository
      |-- Room: courses, exams, overrides, import records
      |-- DataStore: theme, notification settings, OCR choice, MCP settings
      |
  Domain services
      |-- EffectiveScheduleResolver
      |-- ReminderScheduler
      |-- WidgetSnapshotProvider
      |-- OcrImportPipeline
      |-- McpServer

Web/PWA
  继续使用现有课表.html 和 localStorage
  通过 JSON 导入/导出与原生应用交换数据
```

原生应用不再依赖 WebView 来承载主界面。Web 版与原生版共享 JSON 数据契约和字段语义，不共享运行时存储。

## 数据模型

Room 使用稳定 ID、创建时间、更新时间和来源字段。核心实体如下：

```text
Course
  id: String
  name: String
  weekday: Int              // 1..7
  startPeriod: Int          // 1..12
  endPeriod: Int            // 1..12
  weekRule: ALL | ODD | EVEN
  building: String?
  room: String?
  locationNote: String?
  createdAt: Instant
  updatedAt: Instant
  source: MANUAL | OCR_LOCAL | OPENAI | MCP | IMPORT

Exam
  id, subject, date, time, building, room, locationNote, source, timestamps

ScheduleOverride
  date: LocalDate
  replacementWeekday: Int    // 指定日期按哪一个星期的课程执行
  note: String?

AppMetadata
  schemaVersion
  datasetId
  updatedAt
  lastSource
```

原有 Web 数据中的 `location` 在导入时作为兼容字段：优先解析为 `building`/`room`，无法拆分时保留到 `locationNote`。

## 生效课表规则

`EffectiveScheduleResolver` 是通知、Widget、主界面和 MCP 查询共同使用的唯一规则入口：

1. 先确定日期对应的星期。
2. 如果存在整天调休规则，使用 `replacementWeekday` 替代自然星期。
3. 根据学期开始日期计算单双周；没有学期开始日期时只应用每周课程。
4. 过滤星期和单双周不匹配的课程。
5. 按开始节次、结束节次排序，并保留课程冲突信息。

这样可以避免各个功能分别实现单双周和调休逻辑。

## 通知与后台

- 使用 `AlarmManager.setExactAndAllowWhileIdle()` 调度下一次课程提醒。
- 使用 `BroadcastReceiver` 接收提醒并显示通知。
- 使用 `BOOT_COMPLETED`、时区变化和日期变化广播重建提醒。
- 每次课程、设置或调休变化后取消旧提醒并重新计算未来窗口。
- Android 13+ 首次启动请求 `POST_NOTIFICATIONS`；精确闹钟权限被拒绝时显示明确状态和系统设置入口。
- 通知正文显示课程名、开始时间、教学楼和教室；点击通知进入课程详情。

## Widget

使用 Jetpack Glance。Widget 只读取 `WidgetSnapshotProvider` 生成的未来三节快照，不直接访问 Room 规则细节。数据变化和应用启动时立即更新，系统刷新用于恢复和周期性更新。没有课程时显示下一次上课日期或空状态。

## OCR 与图片导入

首选 PaddleOCR 官方 Android 路径：`PP-OCRv6 tiny/small ONNX + ONNX Runtime Android`。模型下载、SHA-256 校验、版本切换和删除都由 `OcrModelManager` 管理。识别流水线为：图片预处理 -> 检测/识别 -> 坐标聚类 -> 表头和节次解析 -> `CourseDraft` -> 字段校验 -> 逐项确认 -> Room 写入。

ML Kit 中文识别作为低存储设备或快速回退方案；通用 OCR 输出不得未经确认直接写入正式课程。

OpenAI 导入与本地 OCR 共用 `CourseDraft` 和确认界面，差异只在识别提供方。Key 使用 Android Keystore 加密保存，请求前明确展示将发送的图片。

## MCP

第一版提供本机 HTTP JSON-RPC 服务，局域网可访问。能力包括：

- 查询今天、指定日期和未来课程
- 查询考试和调休
- 添加、编辑、删除课程与考试
- 添加和删除调休规则
- 清空操作作为高风险命令单独暴露

服务启动默认关闭。启用时生成固定 Token，设置页展示二维码/复制入口并允许轮换。写入确认模式支持“自动写入”和“每次在 App 确认”；无效 Token、非局域网来源和危险操作返回结构化错误。

## Web 兼容与迁移

- Web 版继续使用现有 PWA 代码，不在本阶段重写其 UI。
- 增加与原生相同字段语义的 JSON 导入/导出格式。
- 原生应用首次使用提供“导入 Web 版导出的 JSON”入口。
- 旧 Web 数据仍可通过现有导出功能迁移；不直接依赖 WebView 的 localStorage。

## 测试与验收

- Domain 单元测试覆盖单双周、整天调休、跨午夜边界、课程冲突和无学期开始日期。
- Room 迁移测试覆盖 schemaVersion 变化和旧 `location` 字段导入。
- ReminderScheduler 测试覆盖重启恢复、权限缺失、课程修改和重复提醒。
- OCR 测试使用匿名课表样本，记录字段准确率、低置信度字段和导入耗时。
- MCP 测试覆盖鉴权、局域网限制、读取、写入确认和清空保护。
- Android 真机验证通知、Widget、模型下载、离线 OCR 和应用重启后的状态恢复。
- Web 版现有功能继续通过手工回归验证，数据契约增加跨端导入导出样例。

## 实施顺序

1. 建立原生 Android 模块、Compose 入口、Room/DataStore 和数据契约。
2. 实现课程/考试/调休领域规则和原生课表界面。
3. 实现通知调度、权限处理和重启恢复。
4. 实现 Glance Widget。
5. 实现 JSON 导入导出和 Web 数据迁移。
6. 接入本地 OCR 模型管理、解析流水线和确认界面。
7. 接入 OpenAI 图片导入。
8. 实现局域网 MCP 服务和配对设置。
9. 真机验证、APK 构建和版本发布文档。

## 主要风险

- 当前机器缺少 JDK/Android SDK，无法立即完成 Gradle 真机构建；需要先补齐 Android 构建环境。
- 精确闹钟和 Android 厂商后台限制会影响提醒可靠性，必须提供权限状态和手动重建入口。
- OCR 的核心难点是课表坐标到字段的解析，不是单纯文本识别；必须保留逐项确认。
- MCP 监听局域网会扩大本地攻击面，默认关闭、固定 Token 轮换和确认模式是必要边界。
