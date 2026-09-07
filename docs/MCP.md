# MCP 连接指南

## 1. 准备网络

让手机和 Agent 所在电脑连接同一个可信 Wi-Fi。访客网络、无线隔离或 VPN 可能阻止设备互访。当前 MCP 使用局域网 HTTP，没有 TLS 加密，不应暴露到公网或在不可信网络中使用。

## 2. 开启手机服务

打开“设置 → AI 连接”，开启服务。出现“服务正在监听”后，选择一个局域网地址。不要在电脑端使用 `127.0.0.1`，它指向电脑自身。

保持应用的持续通知，避免在系统中强行停止应用。手机换 Wi-Fi 或 IP 地址变化后，需要更新客户端地址。

## 3. 添加客户端配置

支持 Streamable HTTP 的客户端通常需要填写：

| 配置项 | 内容 |
| --- | --- |
| 服务器名称 | 我的课表，可自定 |
| URL | 应用显示的地址，以 `/mcp` 结尾 |
| 请求头名称 | `Authorization` |
| 请求头值 | `Bearer ` 加一个空格后接配对 Token |

应用可复制完整 JSON 模板。以下是支持 `mcpServers` 格式的客户端示例，地址和 Token 必须换成手机当前显示的值：

```json
{
  "mcpServers": {
    "class-schedule": {
      "url": "http://192.168.1.20:8765/mcp",
      "headers": { "Authorization": "Bearer YOUR_TOKEN" }
    }
  }
}
```

客户端配置格式可能不同；如果不接受该 JSON，请在其图形设置中分别填写 URL 和请求头。连接后可以对 Agent 说：“查询我今天的课表。”

## 权限与工具

默认每次写入都需在应用确认。关闭该选项后，已配对的客户端可直接编辑；`schedule.clear` 始终要求本机确认。轮换 Token 会撤销旧 Token，并取消待确认请求。

| 用途 | 工具 |
| --- | --- |
| 读取完整数据集 | `schedule.list` |
| 查询实际日期 | `schedule.for_date` |
| 查询分类记录 | `schedule.list_courses`、`schedule.list_exams`、`schedule.list_overrides` |
| 保存课程/日期安排/调休 | `schedule.upsert_course`、`schedule.upsert_exam`、`schedule.upsert_override` |
| 删除记录 | `schedule.delete_course`、`schedule.delete_exam`、`schedule.delete_override` |
| 清空记录 | `schedule.clear` |

考试和日程共用 `exams` 集合，以 `type: EXAM` / `EVENT` 区分。课程起止节次必须落在当前作息内。调用 `tools/list` 获取实际参数定义。

## 连接排查

- **连接超时**：确认服务正在监听、手机与电脑可互访、IP 和端口正确。
- **401**：请求头或 Token 不匹配，重新复制；注意 `Bearer` 后面的空格。
- **403 / Host 或 Origin 拒绝**：使用应用显示的数字 IP 地址；服务面向 MCP 客户端，不接受浏览器网页直接跨域访问。
- **写入等待/失败**：查看手机中的确认请求。请求有超时；数据在确认期间变化时，可能需要重新提交。
- **服务已停止**：重新打开应用并启用；检查是否被系统强行停止。

请勿将包含真实 Token 的配置文件提交到公开仓库或发送给无关人员。
