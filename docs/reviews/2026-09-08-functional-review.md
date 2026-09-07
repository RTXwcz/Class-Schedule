# 产品功能对抗式审查

审查日期：2026-09-08。基线：`f37f389bb27faf1190f65c6a35eadf9ec7c74b9a`。

本报告审查 Android 原生应用的存储、导入、课程管理、提醒、小组件和 MCP 一致性。结论来自基线源代码与已有测试内容的逐项核对；本次没有运行 Gradle、设备测试或新的界面实测。下面的复现步骤是由代码路径确定的触发条件，不能作为已在设备执行的测试记录。后续修复及验证应另行记录，不能把基线问题清单当作修复完成的证据。

## F-01 · P1 · JSON 恢复可能部分提交，却报告导入失败

**触发条件：** 设备已有课表及每日作息。恢复一份同时包含新课程、`periods`、`semesterStartDate`、`parityEnabled` 的 JSON，在 Room 替换成功后发生 DataStore 写入失败，或进程在两类存储写入之间结束。

`AppViewModel.importJson` 先调用 `repository.replaceAll(export)`，再分别执行三次 `settingsStore.update`。Room 的事务只覆盖记录及数据集元数据；设置存储不属于该事务。设置写入抛错时，外层异常处理显示导入失败，已经提交的新课程不会恢复成旧记录。进程中断也可能留下新课表与旧作息、旧学期或旧单双周开关的组合。由此产生的提醒时间和实际显示可能与备份不符。

代码证据：[`AppViewModel.kt:207–217`](https://github.com/RTXwcz/Class-Schedule/blob/f37f389bb27faf1190f65c6a35eadf9ec7c74b9a/apk/android/app/src/main/java/com/kebiao/app/ui/AppViewModel.kt#L207)、`ScheduleRepository.replaceAll` 的 Room 事务。

**建议修复：** 将影响课表解析的作息、学期及单双周设置与数据集记录放在同一个权威事务中。若仍保留两套存储，则需要可恢复的提交协议；仅在 `catch` 中写回旧快照不足以覆盖进程中断和并发修改。导出、MCP、提醒及小组件应共同读取提交后的权威快照。

**必须补充的验证：** 新课程与新规则一起成功提交并在磁盘重开后保持；注入规则写入失败时旧数据完整保留；在提交边界中断后重启只出现完整旧版本或完整新版本。已有 `DatasetPersistenceTest.failedMutationRollsBackMetadataWithCourseRecords` 仅覆盖 Room 内部失败，不能证明跨存储恢复原子性。

## F-02 · P2 · 提前量跨过午夜时课程提醒被丢弃

**触发条件：** 每日第 1 节设为 `00:05–00:45`，提前量设为 10 分钟，周二有第 1 节课程。计划器正确生成周一 `23:55` 的提醒，但广播到达时不会找到对应课程。

`ReminderPlanner.plan` 按课程日期减去提前量，允许触发时间处于前一日。`currentReminder` 却只解析 `now.toLocalDate()` 的课程；周一广播携带周二课程的 key，因此校验结果为 `null`。这与“单节课程不跨午夜”的现有限制不同：例子中的课程本身完全在周二，跨日的是提醒时间。

代码证据：[`ReminderPlanner.kt:31–37`](https://github.com/RTXwcz/Class-Schedule/blob/f37f389bb27faf1190f65c6a35eadf9ec7c74b9a/apk/android/app/src/main/java/com/kebiao/app/notifications/ReminderPlanner.kt#L31)，以及同文件 `plan` 的 `startsAt.minusMinutes(leadMinutes)`。

**建议修复：** 使用计划对应的课程日期重新解析并验证课程、规则及原触发时间，或在由当前时刻和提前量确定的有限日期范围内查找。继续保留取消、已送达去重和旧广播失效检查。

**必须补充的验证：** 周二 `00:05` 课程在周一 `23:55` 得到有效计划；周二调休及单双周规则正确应用；课程删除、改时、规则变化后原广播失效；午夜前已提醒的次日课程在刷新时不会重复通知。现有提醒测试未覆盖此边界。

## F-03 · P2 · 已保存但尚未生效的课程缺少可发现的管理入口

**触发条件：** 新安装尚未设置学期开始日期，在手动课程表单填写周次 `1-16` 后保存；或开启单双周但不设置学期日期，再添加单周课程。

`CourseEditorDialog` 允许保存这些记录。`ScheduleResolver` 在学期未知时过滤明确周次及已启用的单双周课程；`TimetableScreen` 只为 `effectiveCourses(date)` 中的记录创建可编辑卡片。基线没有展示全部已保存课程的管理入口，录入页只能创建新记录。因此该课程虽已持久化，却无法在任意日期的课表卡片中找到、编辑或删除；用户只能猜测去设置学期，或借助 JSON/MCP。

代码证据：[`TimetableScreen.kt:125–143`](https://github.com/RTXwcz/Class-Schedule/blob/f37f389bb27faf1190f65c6a35eadf9ec7c74b9a/apk/android/app/src/main/java/com/kebiao/app/ui/timetable/TimetableScreen.kt#L125)、同文件课程编辑器的周次字段与保存校验，以及 `ScheduleResolver.resolve` 对 `weeks`、`weekParity` 的过滤。

**建议修复：** 提供“全部课程”管理入口，包含暂未生效和已结束的课程，并显示未生效原因。设置学期的提示及导航可以减少困惑，但不应代替对所有已保存记录的管理能力。新课程保存后应给出可定位的成功反馈。

**必须补充的验证：** 未设置学期时添加限定周次课程，能在全部课程中找到并编辑、删除；单周课程在双周也能管理；已结束学期的课程仍可管理；课表实际生效规则不因管理入口而放宽。

## 已核对到的相邻一致性风险

在读取基线 MCP 代码时，还发现 `schedule.list` 直接编码 `store.snapshot()`，而当前 `periods`、学期和单双周开关保存在 DataStore；只有 `schedule.for_date` 单独读取当前设置。导入曾携带的这些字段被保存在 Room 的 `extraFields` 后，用户再从设置页修改它们，`schedule.list` 可能继续返回旧值；全新数据集也可能完全缺少这些字段。这应随 F-01 的统一权威快照一并解决，并验证“设置页修改 → MCP 全量查询 → JSON 导出”结果一致。

证据：[`McpToolRegistry.kt:110–131`](https://github.com/RTXwcz/Class-Schedule/blob/f37f389bb27faf1190f65c6a35eadf9ec7c74b9a/apk/android/app/src/main/java/com/kebiao/app/mcp/McpToolRegistry.kt#L110)；`AppViewModel.exportCurrentJson` 为原生导出另外合并当前设置，而 `schedule.list` 没有同等步骤。

## 已有保障与验证边界

- 单条课程、考试、日程、调休增删改在仓库中执行，未看到早期“拿尚未加载完整的 UI 快照覆盖整库”的路径。Room 全量替换先验证记录、重复 ID 与重复调休日期，再进入事务。
- 考试与日程类型、地点和普通备注贯穿 Room、JSON、MCP；已有迁移、磁盘重开与类型修改测试覆盖。提醒和小组件目前只面向课程，README 与 Release 应明确这一产品边界。
- 单双周、明确周次、调休由共享解析器计算；提醒和小组件接收自定义作息。已有测试覆盖多节课程连续卡片、课间对齐、同时间课程并列以及第 13 节的显示，不能据此推断所有窄屏、字体缩放、读屏和复杂冲突布局都已验收。
- 普通设置更新在 DataStore 成功后由 Flow 更新界面，并捕获保存异常。课程、考试和作息编辑弹窗则在发起异步保存时立即关闭；后续视觉/交互验收应检查错误出现时是否足以恢复用户输入。此项本报告不扩展为新的修复任务。
- 日期与时间的格式文本输入是用户已明确要求替换的交互，不重复列为缺陷。新滚轮需要覆盖闰年、月末、全天开关、取消不保存、已有值回显、时间先后校验及无键盘格式输入的实际设备流程。

本报告的三项优先修复问题均已发送实现负责人。没有改动应用源代码，没有提交 Git，也没有将已有截图描述为本次新实测。
