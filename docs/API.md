# AI 分析 API 配置说明

ScoreTrack 不内置任何 API Key，也不需要重新编译即可切换服务商。所有配置都在「设置 → AI 分析设置」中完成，密钥保存在本机 Android Keystore。

## 1. 支持范围

- 任何 **OpenAI 兼容的 Chat Completions** 接口（`POST {Base URL}/chat/completions`）
- 默认示例（DeepSeek）：Base URL `https://api.deepseek.com`，Model `deepseek-chat`
- 也可用于：OpenAI（`https://api.openai.com/v1`，`gpt-4o-mini` 等）、通义千问兼容模式、Moonshot、智谱、vLLM / Ollama / LM Studio 自建网关等
- 若 Base URL 已包含 `/chat/completions`，应用不会重复拼接

## 2. 配置项

| 项 | 必填 | 说明 |
| --- | --- | --- |
| API Base URL | 是 | 例如 `https://api.deepseek.com`；自建服务可填 `http://192.168.1.10:11434/v1` |
| API Key | 是 | 只写入 Keystore 加密存储；输入框默认掩码，可点右侧眼睛查看；留空保存表示“不修改” |
| Model Name | 是 | 例如 `deepseek-chat`、`gpt-4o-mini`、`qwen-plus` |
| Temperature | 否 | 0–2，默认 0.7 |
| 最大输出 Token 数 | 否 | 默认 2048，范围 64–32768 |

页面会实时显示“实际请求地址”，例如 `https://api.deepseek.com/chat/completions`。

> 应用已开启 `usesCleartextTraffic`，因此 http 的局域网端点（Ollama / LM Studio）也可用；公网服务建议使用 https。

## 3. 测试连接

点击「测试连接」会带当前配置真实发起一次最小请求（`max_tokens=24`），并区分以下结果：

| 情况 | 提示 |
| --- | --- |
| 2xx 且有内容 | 连接测试成功（显示耗时、endpoint、返回内容摘要） |
| 401 / 403 | 密钥错误或无权限（原样透传服务端 message） |
| 404 | 模型不可用或地址错误 |
| 400 / 422 | 请求被拒绝（参数/模型不兼容，附服务端 message） |
| 429 | 请求过于频繁 / 额度不足 |
| 5xx | 服务端错误 |
| 无网络 / 超时 | 网络错误 |

失败时不会写入任何报告，也不会伪造“成功”。

## 4. 生成报告

- 入口：首页「AI 成绩分析」、分析页右上「AI 分析」、AI 页面「生成 AI 分析报告」。
- 首次生成前弹出隐私说明：明确告知将发送的字段（学生姓名、班级、考试名称与日期、六科成绩、总分、本地统计结果），不会上传原始照片；同意标记写入 `ai.privacy_accepted`。
- 发送内容为本地整理的 JSON（示例结构）：

```json
{
  "version": 1,
  "student": "学生A",
  "className": "高二3班",
  "studentNumber": "3001",
  "generatedAt": "2026-11-15 16:45",
  "subjectMax": { "语文": 150, "数学": 150, "英语": 150, "物理": 100, "化学": 100, "地理": 100 },
  "scaledSubjects": ["化学", "地理"],
  "exams": [
    {
      "name": "11月月考",
      "date": "2026-11-10",
      "scores": { "语文": 125, "数学": 132, "英语": 128, "物理": 85, "化学": 90, "地理": 88 },
      "rates": { "语文": 0.8333, "数学": 0.88, "英语": 0.8533, "物理": 0.85, "化学": 0.9, "地理": 0.88 },
      "rawScores": { "化学": 82, "地理": 79 },
      "total": 648,
      "complete": true,
      "note": ""
    }
  ],
  "statsText": "本地统计（平均/最高/趋势/得分率/稳定性…）",
  "warnings": []
}
```

- 系统提示词要求模型：只使用真实数据、赋分科目按赋分理解、必须用得分率而非原始分比较科目强弱、数据不足要明说、结论必须引用具体数据、输出含固定标题 A–E 的中文 Markdown。
- 报告生成后写入 `ai_reports`，同时保存 `dataSnapshotJson`。之后修改成绩不会改变历史报告内容。

## 5. 安全与隐私

- API Key 使用 Android Keystore（AES-256-GCM，别名 `scoretrack_ai_key_v1`）加密后存 `app_settings.ai.api_key.cipher`，备份 JSON 不导出该键。
- 日志不打印 Key / Authorization 头。
- 可随时「清除 API 配置」；未配置时所有本地功能（录入、OCR、曲线、备份）完全可用。
- API 调用失败只影响本次生成，不修改本地数据库。

## 6. 常见问题

- **401**：Key 复制多了空格或已失效；重新粘贴后点“测试连接”。
- **404 模型不可用**：Model Name 与服务商实际模型不一致（如 `deepseek-chat` 写成 `deepseek`）。
- **连接超时**：Base URL 缺少协议或端口；自建服务确认局域网可达。
- **返回内容为空**：部分兼容实现不返回 `choices[0].message.content`，应用会报“接口返回内容为空”，请换用标准兼容端点。
