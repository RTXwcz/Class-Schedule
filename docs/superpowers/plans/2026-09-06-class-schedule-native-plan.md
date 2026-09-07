# Class Schedule Native Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 版本升级为 Kotlin + Jetpack Compose 原生课表应用，保留现有 Web/PWA，并逐步实现通知、调休、Widget、OCR、OpenAI 导入和局域网 MCP。

**Architecture:** 原生 Android 使用 Compose UI、ViewModel、Room 和 DataStore；课程生效规则集中在 `EffectiveScheduleResolver`，供界面、通知、Widget 和 MCP 复用。PWA 保持现有实现，通过 JSON 数据契约与原生应用交换数据。

**Tech Stack:** Kotlin 2.4.0、Android Gradle Plugin 8.13.0 + R8 9.1.43、Jetpack Compose、Room 2.7.2、DataStore 1.1.7、Glance 1.1.1、Android AlarmManager、ONNX Runtime Android、PaddleOCR PP-OCRv6 ONNX、官方 MCP Kotlin SDK 0.15.0。

**2026-09-07 状态：** 下方 Task 1-7 的逐步测试/逐任务提交条目保留为原始计划，不作为实际运行日志。原生基础、数据迁移、提醒、小组件、可下载 OCR、OpenAI 导入、MCP 与 Web JSON 交换均已实现；最终验收与限制见 [验证报告](../verification/2026-09-07-native-final-verification.md)。真实手机准确率/省电策略和商店发布不属于已完成验收。

**Spec:** `docs/superpowers/specs/2026-09-06-class-schedule-native-design.md`

## Global Constraints

- Android 原生应用是主应用，Web/PWA 保留。
- 课程、考试和调休使用 Room；设置使用 DataStore。
- 所有日期课程计算必须经过 `EffectiveScheduleResolver`。
- 通知默认提前 10 分钟，只提醒当前日期实际生效课程。
- 调休使用指定日期按目标星期课程替换。
- Widget 显示未来三节课程，并在应用启动和系统刷新时更新。
- 本地 OCR 必须由用户主动选择后下载，识别结果逐项确认后写入。
- MCP 默认关闭，仅允许本机/局域网并使用固定可轮换 Token。
- 每个可测试的领域行为先写失败测试，再写最小实现。
- 当前已安装 JDK 21、Android Studio 和 Android SDK 36；构建与测试通过 ASCII junction `D:\kebiao-build` 运行，结果须以实际日志为准。

---

### Task 1: Native Android foundation and domain module

**Files:**
- Create: `apk/android/gradle/libs.versions.toml`
- Modify: `apk/android/settings.gradle`
- Modify: `apk/android/build.gradle`
- Modify: `apk/android/app/build.gradle`
- Modify: `apk/android/app/src/main/AndroidManifest.xml`
- Replace: `apk/android/app/src/main/java/com/kebiao/app/MainActivity.java` with `MainActivity.kt`
- Replace: `apk/android/app/src/main/res/layout/activity_main.xml` with Compose entry code
- Create: `apk/android/app/src/main/java/com/kebiao/app/domain/model/Course.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/domain/model/WeekRule.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/domain/model/ScheduleOverride.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/domain/model/EffectiveCourse.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/domain/ScheduleResolver.kt`
- Test: `apk/android/app/src/test/java/com/kebiao/app/domain/ScheduleResolverTest.kt`

**Interfaces:**
- `ScheduleResolver.resolve(date: LocalDate, semesterStart: LocalDate?, courses: List<Course>, overrides: List<ScheduleOverride>): List<EffectiveCourse>`
- `WeekRule` values are `ALL`, `ODD`, `EVEN`.
- `Course` validates weekday 1..7 and periods 1..12 at construction boundary.

- [ ] Write failing tests for natural weekday selection, odd/even weeks, missing semester start, full-day replacement, and sorted output.
- [ ] Run `apk\android\gradlew.bat test` and capture the expected environment failure if JDK is unavailable.
- [ ] Add Kotlin/Compose/Room/DataStore/Glance dependency pins and convert `MainActivity` to `ComponentActivity` with a minimal Compose root.
- [ ] Implement immutable domain models and `ScheduleResolver` without Android dependencies.
- [ ] Run the focused unit test command again; record pass/fail output and distinguish code failures from missing JDK.
- [ ] Commit with `feat: add native Android foundation and schedule resolver`.

### Task 2: Room, DataStore, and JSON contract

**Files:**
- Create: `apk/android/app/src/main/java/com/kebiao/app/data/local/AppDatabase.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/data/local/CourseEntity.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/data/local/ExamEntity.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/data/local/ScheduleOverrideEntity.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/data/local/ScheduleDao.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/data/settings/AppSettingsStore.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/data/JsonScheduleCodec.kt`
- Test: `apk/android/app/src/test/java/com/kebiao/app/data/JsonScheduleCodecTest.kt`
- Test: `apk/android/app/src/androidTest/java/com/kebiao/app/data/AppDatabaseTest.kt`

**Interfaces:**
- `JsonScheduleCodec.decode(json: String): ScheduleExport`
- `JsonScheduleCodec.encode(export: ScheduleExport): String`
- `ScheduleRepository.observeCourses(): Flow<List<Course>>`
- `ScheduleRepository.replaceAll(export: ScheduleExport)`

- [ ] Write failing codec tests for current Web JSON, split building/room fields, schema metadata, and malformed records.
- [ ] Implement entities, DAOs, Room database version 1, and DataStore settings keys.
- [ ] Implement a codec that accepts legacy `location`, preserves unknown fields, and emits the shared schema.
- [ ] Run JVM and Android database tests; record exact output.
- [ ] Commit with `feat: add local schedule storage and JSON contract`.

### Task 3: Compose timetable, exams, overrides, and settings

**Files:**
- Create: `apk/android/app/src/main/java/com/kebiao/app/ui/App.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ui/timetable/TimetableScreen.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ui/calendar/ExamCalendarScreen.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ui/settings/SettingsScreen.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ui/importexport/ImportExportScreen.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ui/AppViewModel.kt`
- Create: `apk/android/app/src/main/res/values/strings.xml` additions
- Test: `apk/android/app/src/test/java/com/kebiao/app/ui/AppViewModelTest.kt`

- [ ] Write ViewModel tests for adding/editing/deleting courses, exams, and full-day overrides.
- [ ] Implement Compose navigation and a usable timetable with weekday columns and period rows.
- [ ] Add separate building, room, and location note fields to course and exam forms.
- [ ] Add plain-language odd/even week explanation beside the semester setting.
- [ ] Add JSON import/export using `JsonScheduleCodec`.
- [ ] Run tests and perform a static resource check for all referenced strings and routes.
- [ ] Commit with `feat: add native schedule and settings UI`.

### Task 4: Notifications and Glance Widget

**Files:**
- Create: `apk/android/app/src/main/java/com/kebiao/app/notifications/ReminderScheduler.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/notifications/ReminderReceiver.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/notifications/BootReceiver.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/widget/ScheduleWidget.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/widget/WidgetSnapshotProvider.kt`
- Modify: `apk/android/app/src/main/AndroidManifest.xml`
- Test: `apk/android/app/src/test/java/com/kebiao/app/notifications/ReminderPlannerTest.kt`

- [ ] Write failing planner tests for ten-minute offset, expired courses, overrides, odd/even filtering, and duplicate prevention.
- [ ] Implement `ReminderScheduler` with exact alarms, notification channels, permission state, and rescheduling hooks.
- [ ] Register boot/timezone/date receivers and rebuild future alarms after restart.
- [ ] Implement Glance widget showing the next three `EffectiveCourse` entries and an empty state.
- [ ] Add notification permission and exact-alarm status UI.
- [ ] Run unit tests; document that emulator/device verification requires Android SDK and JDK.
- [ ] Commit with `feat: add course reminders and home screen widget`.

### Task 5: OCR and OpenAI import pipeline

**Files:**
- Create: `apk/android/app/src/main/java/com/kebiao/app/ocr/OcrModelManager.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ocr/PaddleOcrEngine.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ocr/CourseTableParser.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/ocr/CourseDraft.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/imports/OpenAiImageImporter.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/imports/ImportReviewScreen.kt`
- Test: `apk/android/app/src/test/java/com/kebiao/app/ocr/CourseTableParserTest.kt`
- Test: `apk/android/app/src/test/java/com/kebiao/app/imports/ImportValidationTest.kt`

- [ ] Write parser tests for Chinese weekdays, period ranges, odd/even labels, building/room extraction, and low-confidence fields.
- [ ] Implement model choice, download progress, SHA-256 verification, private storage, and atomic model activation.
- [ ] Implement OCR output normalization into `CourseDraft` with source boxes and confidence.
- [ ] Implement a review screen that requires confirmation for every draft field before Room write.
- [ ] Implement Keystore-backed OpenAI API settings and an image request that reuses the same draft/review contract.
- [ ] Run parser and validation tests without model assets; report model/device tests separately.
- [ ] Commit with `feat: add reviewed OCR and OpenAI imports`.

### Task 6: LAN MCP service

**Files:**
- Create: `apk/android/app/src/main/java/com/kebiao/app/mcp/McpServer.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/mcp/McpAuthStore.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/mcp/McpToolRegistry.kt`
- Create: `apk/android/app/src/main/java/com/kebiao/app/mcp/McpSettingsScreen.kt`
- Test: `apk/android/app/src/test/java/com/kebiao/app/mcp/McpAuthTest.kt`
- Test: `apk/android/app/src/test/java/com/kebiao/app/mcp/McpToolRegistryTest.kt`

- [ ] Write failing tests for token auth, localhost/LAN allowlist, read tools, write confirmation mode, and clear protection.
- [ ] Implement a loopback/LAN HTTP JSON-RPC server with explicit start/stop lifecycle and bounded request sizes.
- [ ] Generate and rotate a fixed token using Android Keystore-backed storage.
- [ ] Implement query and mutation tools through `ScheduleRepository`, never direct database access.
- [ ] Add settings for server enabled, port, token rotation, and automatic versus per-write confirmation.
- [ ] Run protocol and auth tests; perform a local socket smoke test when JDK is available.
- [ ] Commit with `feat: add authenticated LAN MCP service`.

### Task 7: Integration, migration, and release verification

**Files:**
- Modify: `打包说明.md`
- Modify: `apk/构建APK.md`
- Create: `apk/android/app/src/androidTest/java/com/kebiao/app/migration/WebJsonMigrationTest.kt`
- Create: `docs/superpowers/verification/2026-09-06-native-verification.md`
- Modify: `.gitignore` if generated native artifacts appear

- [ ] Add migration fixtures exported by the current Web version and verify import into Room.
- [ ] Add a build preflight documenting JDK 17/21, Android SDK 36, Gradle, and model download prerequisites.
- [ ] Run all available JVM tests, Android tests, static checks, and `assembleDebug` when the environment is installed.
- [ ] Perform device checks for notifications, Widget updates, offline OCR, app restart, and MCP auth.
- [ ] Compare PWA and native JSON export fields and record residual gaps.
- [ ] Commit with `docs: record native verification and build requirements`.

## Completion Checklist

- [ ] All tasks have a commit and passing available tests.
- [x] Native app launches without WebView as the primary UI. Verified on Android 15 emulator; see `2026-09-07-persistence-device-verification.md`.
- [x] Domain rules drive UI, notifications, Widget, and MCP consistently.
- [x] OCR and OpenAI results require review before persistence.
- [x] MCP is disabled by default and authenticated when enabled.
- [x] Web version remains available and can exchange JSON with native app.
- [x] APK build and device verification results are recorded honestly.
