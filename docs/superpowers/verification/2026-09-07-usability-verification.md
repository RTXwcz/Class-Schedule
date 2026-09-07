# 易用性与自定义作息验证

日期：2026-09-07。设备：Android 15 / API 35 模拟器。最新目标包含界面优化、HF 镜像、可选单双周、手动录入、考试/日程分类、自定义节数/时间、连堂连续显示。

## 逐项验收

| 需求 | 实现与证据 |
| --- | --- |
| 原生界面更清晰 | 课表一屏整周与展开模式；今日强调、次数统计；录入独立分组、安排类型筛选。实际截图见下方。 |
| hf-mirror.com 下载 | 两套模型全部文件 URL 指向镜像；独立临时私有目录实际下载 tiny/small，SHA-256 校验与中文推理均通过，测试后清理。主机单独下载 tiny 字典也与固定摘要一致。 |
| 单双周可选 | 新安装默认关闭、已有设置保留旧行为；原规则不删除。领域测试覆盖关闭后的每周课程、显式周集合与调休，以及提醒/Widget/MCP 的一致性。 |
| 手动录入与导入并存 | “录入”有手动填写、图片识别、数据备份。设备测试在未配置模型/API 时手动保存课程与日程，转到安排页验证显示。 |
| 考试和日程类型 | EXAM/EVENT、独立普通备注、筛选/编辑/删除；旧 JSON 默认 EXAM，Room v1/v2→v3 迁移及磁盘重开通过；MCP 读写与 JSON 往返通过。 |
| 自定义节数与时间 | 设置支持 1–48 节及 HH:mm 起止，拒绝重叠/倒序/跨午夜；设备测试改成 2 节、第一节 07:45，UI 显示保存结果。13 节 JSON 往返及不能缩减掉已有课程通过。 |
| 各处使用自定义作息 | 提醒与 Widget 使用传入 LessonPeriod；领域测试将三节连堂从 08:30 开始，仅产生 08:20 一次提醒，并验证 Widget 时间。MCP 返回 periods，课程写入检查当前节数。 |
| 连堂连续显示 | 设备断言第 3–5 节是一个节点，顶端对齐第 3 节、底端对齐第 5 节，卡片高度约为单节 3 倍；同一时段课程仍分列。 |

## 命令与结果

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`：成功。JVM **78 项通过**，Lint **0 错误、35 条提示**。
- 最新设备集成测试排除已验证且代码未变的 OCR 推理类，**25 项通过、0 跳过、0 失败**，涵盖数据、MCP、提醒与所有新增 UI 流程。
- 前一轮含 OCR 的整合套件为 27 项：26 通过、1 条件跳过（保留已安装 small 的取消测试）。其中独立临时目录的镜像 tiny/small 全新下载推理测试通过，确保使用了网络而不是模型缓存。
- `node --test tests/schedule-contract.test.cjs`：**7 项通过**，包括类型、可选单双周、自定义作息与第 13 节跨端往返。
- Room 导出结构保存在 `apk/android/app/schemas/com.kebiao.app.data.local.AppDatabase/3.json`。

本机详细日志（Git 忽略）：`apk/android/build/goal-final-build.log`、`goal-device-tests.log`、`feature-device-tests.log`。Release 构建、签名和下载文件信息见 [`v1.3.0` 发布记录](../../releases/v1.3.0.md)。

## 截图

- [最终整周与时间刻度](assets/usability/final-whole-week.png)
- [手动录入入口](assets/usability/entry-options.png)
- [考试与日程](assets/usability/dated-items.png)
- [修改后的作息与单双周开关](assets/usability/custom-periods.png)

截图使用测试数据。测试中的课程/日程展示使用内存 ViewModel；数据库字段另由迁移、磁盘重开与仓库集成测试验证。

## 范围限制

没有实体手机验收，厂商后台限制与长期提醒准点率仍待验证。OCR 已验证镜像下载和生成的中文样本推理，未对真实课表样本集做准确率基准。Web 完成契约及脚本检查，本轮没有新的浏览器视觉验收。作息暂不支持单节跨午夜。
