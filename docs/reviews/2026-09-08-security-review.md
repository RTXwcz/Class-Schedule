# 安全审查记录 · 2026-09-08

本次独立审查确认并修复了 **1 项 Web 存储型 DOM XSS（CWE-79）**，同时修复日历中未填写时间时显示 `undefined` / `null` 的问题。其他受审边界未发现具有完整攻击路径的新漏洞；这不代表已经证明整个应用没有安全问题。

审查基线为 `f37f389bb27faf1190f65c6a35eadf9ec7c74b9a`，范围是 Web 导入及 DOM 输出、MCP 入口/授权/写入确认、OpenAI 图片导入、本地 OCR 模型与图片处理、数据库写入及备份配置。采用离线源代码追踪和本地回归复现，没有向外部服务发送课表、调用付费 API、读取签名凭据或修改真实用户数据。

## 已确认并修复

### SEC-01：导入考试/日程 ID 可突破 HTML 属性并注入脚本

**等级：高；证据置信度：高；状态：代码已修复，回归通过。**

攻击者可以提供一个格式合法的课表 JSON，诱导用户确认导入。`ScheduleContract.decode()` 仅要求考试/日程 `id` 是非空字符串，Web 日历随后把 `ex.id` 原样插入 `data-id="…"`。ID 中的双引号和标签会结束该属性并创建带事件处理器的 HTML 元素。

攻击路径：

1. `课表.html:1067` 的 `importData()` 读取用户选择的 JSON（文件上限为 4 MiB）。
2. `schedule-contract.js:20` 的考试解析保留攻击者提供的 ID；ID 本身允许任意文本不是漏洞，问题在输出上下文。
3. 用户确认替换后，`课表.html:1076` 将记录转换为 Web 数据，`save()` 存到 `localStorage`。
4. 基线 `课表.html:795` / `apk/www/index.html:795` 将 ID 拼进属性，`renderCalendar()` 在第 806 行通过 `innerHTML` 写入日历。
5. 导入时会立即渲染日历，即使当前显示课表页；重新打开页面也会加载该记录，因此具有持久性。浏览器解析产生的事件处理器可在该页面同源上下文中运行。

复现输入（只设置无害的内存标记，不读写或发送真实数据）：

```json
{
  "exams": [
    {
      "id": "id\"><img src=x onerror=\"globalThis.__schedule_xss=1\"><div data-id=\"",
      "subject": "数学",
      "date": "2026-09-08",
      "type": "EXAM"
    }
  ]
}
```

该路径也适用于 `type: "EVENT"`。如果通过 Android/MCP 生成同样的文本 ID，再导出到旧 Web 版，仍会抵达相同输出位置；Android 原生 Compose 文本显示本身不执行这段 HTML。

影响是同源课表数据的读取、篡改和页面内容伪装。不能由此推导出能够访问 Android Keystore 或其他应用数据。这里没有网络监听或数据外传复现。

修复在两份实际发布的 HTML 中将 `data-id="${ex.id}"` 改为 `data-id="${esc(ex.id)}"`，复用覆盖 `& < > " '` 的转义函数。保留原始 ID 的存储和 JSON 契约，浏览器读取 `dataset.id` 时仍能正确恢复 ID，因此不需要拒绝已有包含引号的合法 ID。

`tests/web-security.test.cjs` 从两份 HTML 中加载真实 `renderCalendar()` 和 `esc()`，输入经过实际导入契约的攻击记录，捕获写给 `innerHTML` 的文本。基线两个 ID 注入案例都失败，输出确实包含可解析的 `<img … onerror=…>`；修复后均不再产生注入标签。另验证科目、地点仍作为纯文本输出。

证据边界：该回归执行真实应用的 HTML 生成逻辑，不在浏览器中执行攻击脚本；浏览器事件处理行为由源代码中的 `innerHTML` 上下文确认，未声称已进行真实浏览器入侵测试。

### UX-01：全天安排未填时间时显示实现值

**等级：低；性质：显示缺陷；状态：已修复，回归通过。**

合法考试/日程记录可以省略 `time` 或使用 `null`。原日历把它直接传给 `String()`，显示 `undefined` / `null`。同一渲染位置改为对 `ex.time || ''` 转义，回归输入包含省略时间的记录并验证不会显示这两个值。

## 其他受审边界

| 边界 | 本次检查与结论 | 主要证据 |
| --- | --- | --- |
| Web 动态 HTML | 追踪全部 `innerHTML` 使用点。课程名称、地点、批量导入错误已转义；作息时间来自格式和区间验证；主题和日历结构来自固定值。修复遗漏的记录 ID 属性。 | `课表.html` 中的 `esc`、`render`、`renderCalendar`、`runBatch`；`schedule-contract.js` |
| JSON 导入 | Web 文件读取上限 4 MiB，替换前确认，契约验证数组、日期、节次、重复记录和作息。Android Room 写入前验证领域字段，并以事务替换数据。跨 Room/DataStore 原子性属于独立功能审查范围，由主流程处理。 | `importData`；`ImportExportScreen` 的文件读取；`JsonScheduleCodec`；`ScheduleRepository.replaceAll`；[功能审查](2026-09-08-functional-review.md) |
| MCP 网络入口 | 每个请求检查实际 socket 对端的本机/私网地址；未启用转发头覆盖。Bearer Token 必须匹配。SDK 接收明确的 Host 允许列表和空 Origin 允许列表。服务默认关闭，开启后有前台通知和停止入口。 | `McpServer.kt:19`、`:22`、`:28`、`:34`；`McpService.kt` |
| MCP 写入确认 | 仅应用内可调用队列 `resolve`，没有同名 MCP 工具。清空始终确认；普通写入按用户模式确认。最多 8 项待确认，2 分钟超时，取消采用代数标记。真正写入时在 Room 事务内复核原记录、Token 和代数，防止旧确认覆盖已改变的数据。未发现客户端传 `confirmed` 能绕过的路径。 | `McpApprovalQueue.kt:16`、`:25`、`:44`；`McpToolRegistry.kt:201`、`:206`、`:215` |
| MCP 数据参数 | 工具运行时拒绝未知字段、错误类型、空文本、超长文本、不支持的枚举、错误日期/周次/节次。输出采用 JSON 序列化；字段内容没有进入 SQL 字符串拼接或命令执行。 | `McpToolRegistry.validate`、`date`；Room DAO |
| Token 与 API Key | Token 使用 32 字节 `SecureRandom` 生成和常量时间比较；Token、OpenAI Key 均使用 Android Keystore 中的 AES-GCM 密钥加密保存。两份备份配置均排除对应 SharedPreferences。未读取真实密钥值。 | `McpAuthStore.kt:18`、`:46`、`:50`；`OpenAiImageImporter`；`backup_rules.xml`、`data_extraction_rules.xml` |
| OpenAI 请求 | API 地址只接受 HTTPS，拒绝 URL 用户信息和 fragment；禁用自动重定向，避免把 Bearer Key 随重定向发送至其他主机。输入限制 10 MiB，响应限制约 2 Mi 字符，设置连接/读取超时。错误消息不包含 API 响应正文或 Key。 | `OpenAiImageContract.kt:10`；`OpenAiImageImporter.kt:50`、`:57`、`:65`、`:72` |
| 模型响应写入 | OpenAI 输出只解析为待复核字段，最多 200 条。未知单双周或缺失必要信息不能直接通过校验；保存还要求所有字段已确认。未发现模型输出能直接触发 MCP 工具或执行脚本的路径。 | `OpenAiImageContract.parseResponse`；`ImportValidation.canPersist` |
| OCR 下载与激活 | 使用固定 `hf-mirror.com` revision、精确字节数和 SHA-256；仅跟随 HTTPS 重定向，最多 6 次；下载分块限定长度。私有 `noBackupFilesDir` 暂存，完整验证后激活。模型推理/删除共享互斥锁，推理前再次校验摘要。镜像替换内容无法通过摘要校验。 | `OcrModelManager.kt:34`、`:48`、`:91`、`:123`、`:151`、`:188` |
| OCR 图片解码 | 接受本地 `content` / `file` URI；先读取图片尺寸并设置采样，最长边限制 4096，采样目标上限约 1200 万像素，旋转校正后释放 Bitmap。 | `PaddleOcrEngine.kt:66`、`:69`、`:73`、`:116` |
| Android 组件与文件共享 | 提醒接收器和 MCP 服务不导出；FileProvider 不导出。仍保留 Capacitor 模板的较宽 external/cache 路径，但当前应用代码没有调用 `getUriForFile` 或可由外部输入驱动的 URI 授权路径，未建立越权读取攻击链。 | `AndroidManifest.xml`；`res/xml/file_paths.xml`；当前 `src` 下 FileProvider 引用搜索 |

## 应准确公开的设计限制

- **MCP 为局域网 HTTP，并未加密传输。** `McpServer` 绑定 `0.0.0.0` 的普通 CIO HTTP 端口，`McpService` 公布 `http://…/mcp`。Token 的本机加密保存不能保护传输中的 Token 和课表；能观察这段网络流量的参与者可能获得并重放 Token。来源私网检查和 Host 验证也不能替代 TLS。当前适用边界是用户信任的局域网；产品说明应明确这一点，不能使用“端到端加密”“任何局域网均安全”等表述。本次没有抓包或修改已选定的 MCP 配对/传输方案。
- **OpenAI 配置的提供方接收图片和 Key。** HTTPS 与禁止重定向保护传输路径，无法保证用户主动配置的第三方 API 服务可信。识别前的应用确认页已经展示目标地址。离线 OCR 不需要把课表图片发送给该服务。
- **正常课表数据可以参与 Android 系统备份，导出的 JSON 为明文。** 排除的是 OpenAI Key 和 MCP Token；这与“所有课表绝不离开设备”不同。模型放在 `noBackupFilesDir`。
- **未验证图片解码器和依赖内部不存在漏洞。** 本次没有对 Bitmap/EXIF、ONNX、OpenCV、Ktor 或 MCP SDK 原生/内部代码进行模糊测试或完整依赖漏洞扫描。现有输入上限降低资源风险，不能作为依赖安全证明。

## 验证与发布交接

本次实际运行：

```powershell
node --test tests/web-security.test.cjs
node --test tests/*.test.cjs
git diff --check
```

第一次在未修复的 HTML 上运行新回归为 **2 通过 / 2 失败**；修复后完整 Web 集合为 **11 通过 / 0 失败 / 0 跳过**。`git diff --check` 没有空白错误。该审查没有运行 Gradle，也没有把既往 Android 设备测试报告当作本次已重跑的证据。

提交包含两份 HTML 的修复、新回归和本报告。最终集成还需在发布流程中让 CI 执行 `node --test tests/*.test.cjs`，并更新 `sw.js` 的缓存版本，使已安装 PWA 能拉取修复页面；这些构建/发布文件由主流程统一处理。上述原生边界若在其他功能修复中改变，应对改变的边界补充相应测试后再发布。
