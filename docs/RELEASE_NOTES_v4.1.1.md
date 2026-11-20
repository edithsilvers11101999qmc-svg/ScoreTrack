# ScoreTrack v4.1.1 — 得分追踪

原生 Android 的成绩管理与分析工具（Kotlin + Jetpack Compose + Room + ML Kit OCR）。

## 本版本内容

- **目标卡校徽**：大学目标卡左侧可显示校徽（相册选图，自动压缩到 512px 并复制到应用私有目录），支持更换与移除；删除目标卡时自动清理图片。
- **关于页署名修正**：策划 → `Clara Windy`。
- 数据库升级到 v4（`goal_cards.logoPath`），备份格式升级到 v5（校徽以 Base64 内联），旧备份仍可恢复。

## 主要能力

- 多学生档案，成绩按学生隔离；同名靠班级/学号区分，不自动合并
- 成绩单导入：JPG / PNG / WEBP / PDF / XLSX / XLS / CSV，OCR 还原表格行列并匹配目标学生行，**确认后才入库**
- 总分曲线（满分 750）、六科曲线、原始分/得分率双视图、综合统计
- AI 学情分析：任意 OpenAI 兼容接口，报告含整体/六科/优势薄弱/变化归因/学习建议，可导出 PDF
- 新高考 3+1+2 选科（12 种组合，按学生隔离）
- 主题色（7 预设 + 自定义 HEX）、主页壁纸、大学目标卡、主页模块排序
- JSON / ZIP / CSV / PDF 备份与导出

## 安装

要求 **Android 13（API 33）及以上**。下载 `ScoreTrack-4.1.1.apk` 后直接安装（需允许「安装未知来源应用」）。

从 v2 / v3 / v4 升级可直接覆盖安装，数据全部保留；首次启动自动执行数据库迁移，并在迁移前自动备份。

## 校验

| 项目 | 值 |
| --- | --- |
| 包名 | `com.scoretrack` |
| versionCode / versionName | 6 / 4.1.1 |
| minSdk / targetSdk / compileSdk | 33 / 35 / 35 |
| APK 大小 | 92,851,013 字节 |
| SHA-256 | `67462fdaa045d6beb8c76b47564ac85c1f596bdf9d86bd036c7de58e2c1b6f24` |
| 签名 | `apksigner verify` 通过；证书 SHA-256 `70d42bef6cd06b3e22dc4301f8952b604a9ef48f9fe5a71095200d843add12f7` |

## 隐私

所有数据保存在本机；OCR 使用离线模型，不上传照片；AI 分析仅在用户主动点击时发送结构化成绩数据，API Key 用 Android Keystore 加密保存，不写入日志与备份。

完整说明见仓库 README 与 `docs/` 目录。
