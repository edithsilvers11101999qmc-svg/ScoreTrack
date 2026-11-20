# ScoreTrack 升级与数据库迁移说明（V2 → V3 → V4）

本文说明 V3 / V4 如何在**不卸载、不清库**的前提下升级数据库（v1 → v2 → v3）、如何保护旧成绩数据，以及实测结果。V4 选科功能的完整说明见 [SELECTION.md](SELECTION.md)。

## 1. 升级方式

| 场景 | 操作 | 结果 |
| --- | --- | --- |
| 已安装 V2.0.0 | 直接安装 V3/V4 APK（`adb install -r ScoreTrack.apk` 或点击 APK 覆盖安装） | 保留全部学生、考试、成绩、原始成绩单、AI 报告、主题设置，迁移到 v2/v3 |
| 已安装 V3.0.0 | 直接安装 V4 APK（同签名覆盖安装） | 保留全部数据，新增 `subject_selections` 与 3 门选考科目 |
| 全新安装 | 直接安装 APK | 创建数据库 v3，写入默认主题/壁纸/主页模块配置 |
| 降级 | 不保证（数据库已升级到 v2） | 需要时从 `files/upgrade_backups/` 备份恢复 |

两个版本使用**同一签名证书**（SHA-256 `70d42bef6cd06b3e22dc4301f8952b604a9ef48f9fe5a71095200d843add12f7`），因此可以覆盖安装。若签名不一致，Android 会拒绝安装，此时不要卸载（会清空数据），应改用相同签名的安装包。

## 2. 数据库版本变化

- V2：Room `version = 1`，7 张表（students / subjects / exams / scores / source_documents / ai_reports / app_settings）。
- V3：Room `version = 2`，新增 4 张表：

| 表 | 主键 | 用途 |
| --- | --- | --- |
| `theme_settings` | `id = 1` | 主题模式、预设 id、主色 HEX、自定义色、是否使用自定义 |
| `wallpaper_settings` | `id = 1` | 壁纸路径、开关、透明度、缩放、水平/垂直偏移 |
| `goal_cards` | `cardId` | 目标大学、专业、目标总分、可见性、排序、卡片色、透明度、辅助信息开关 |
| `dashboard_cards` | `cardType` | 主页模块开关与排序 |

- V4：Room `version = 3`，新增 1 张表：

| 表 | 主键 | 用途 |
| --- | --- | --- |
| `subject_selections` | `studentId` | 学生选科（首选 1 + 再选 2），外键级联删除 |

同时 `subjects` 表补齐生物、政治、历史 3 门（满分 100），`dashboard_cards` 插入 `subject_selection` 模块。

迁移由 `MIGRATION_1_2` 与 `MIGRATION_2_3` 完成，全部使用 `CREATE TABLE IF NOT EXISTS` + `INSERT OR IGNORE` 默认行：

- 主题模式从旧表 `app_settings.theme_mode` 继承（V2 用户选择的深色/浅色不会丢）。
- 壁纸默认关闭（`enabled = 0`），不改变升级前的视觉。
- 主页模块默认顺序：V3 为大学目标 → 最近考试 → 六科概览，全部启用；V4 变为 大学目标 → 选科概览 → 最近考试 → 六科概览。
- 不修改、不删除任何旧表数据。

## 3. 升级保护流程

`UpgradeManager` 在打开数据库之前和之后各执行一次保护动作：

1. **升级前（`prepareIfNeeded`）**
   - 读取 `PRAGMA user_version`，若小于当前版本则视为需要升级。
   - 把 `databases/scoretrack.db`、`-wal`、`-shm`、`files/wallpapers/` 目录与 `app_settings` 快照复制到
     `files/upgrade_backups/v{旧版本}_to_v{新版本}_{yyyyMMdd_HHmmss}/`，并写入 `manifest.json`（含升级前各表记录数）。
2. **迁移**：Room 执行 `MIGRATION_1_2`。
3. **升级后（`finish`）**
   - 统计 `students / exams / scores / ai_reports / goal_cards` 记录数并与升级前对比。
   - 结果写入 `app_settings` 的 `upgrade.last_result`（JSON）与 `upgrade.pending_notice = 1`。
   - App 启动后读取该标记，弹出「升级完成，数据已保留」对话框，展示 `v1 → v2`、学生/考试/AI 报告数量变化与备份目录；若记录数减少会提示从备份目录恢复。

备份目录结构：

```
/data/user/0/com.scoretrack/files/upgrade_backups/v1_to_v2_20261120_181601/
├── scoretrack.db
├── scoretrack.db-wal
├── scoretrack.db-shm
├── manifest.json          # 升级前记录数、旧版本、时间
└── wallpapers/            # 升级前的壁纸图片（如果有）
```

## 4. 升级实测（真实旧数据库，覆盖安装）

测试环境：Android 15 模拟器（API 35，x86_64，1080×2400），应用 `com.scoretrack`。

步骤：

1. 安装 V2.0.0 正式包，创建学生「LiMing / 3 班 / 3001」，写入 2 次考试共 12 条成绩（高二期中 643 分、11 月月考 661 分），并把主题设置为深色。
2. 记录旧数据库：`PRAGMA user_version = 1`，`students = 1`、`exams = 2`、`scores = 12`。
3. **不卸载**，直接 `adb install -r ScoreTrack.apk` 覆盖安装 V3.0.0（versionCode 2 → 3）。
4. 启动应用并检查弹窗、数据库、界面与功能。

结果：

| 检查项 | 期望 | 实测 |
| --- | --- | --- |
| 覆盖安装 | Success，不卸载 | Success，`versionCode=3 / versionName=3.0.0` |
| 升级弹窗 | 显示版本与记录数变化 | 「升级完成，数据已保留 / 数据库版本：v1 → v2 / 学生：1 → 1 / 考试记录：2 → 2 / AI 报告：0 → 0 / 升级前已自动备份：…v1_to_v2_20261120_181601」 |
| 数据库版本 | `user_version = 2` | 2 |
| 新表 | 4 张表存在 | `theme_settings`、`wallpaper_settings`、`goal_cards`、`dashboard_cards` 均存在 |
| 旧数据 | 学生/考试/成绩数量不变 | 1 / 2 / 12，考试名称与分数完整 |
| 主题继承 | V2 深色 → V3 深色 | `themeMode = DARK` |
| 主页模块 | 默认三项启用 | goals / latest_exam / subject_overview，排序 0/1/2 |
| 升级标记 | 写入结果 JSON | `upgrade.last_result` 含 before/after 与 backupDir |
| 功能回归 | 首页、曲线、AI、备份可用 | 首页显示 661 分与 +18；AI 设置连接测试成功（26 ms）并生成报告；备份/导出可用 |

## 5. 回滚

如升级后需要回到 V2：

1. 从 `files/upgrade_backups/v1_to_v2_*/` 取出 `scoretrack.db`（含 `-wal`/`-shm`）。
2. 关闭应用，把数据库文件放回 `databases/`（需要 root 或 debuggable 包）。
3. 安装 V2 APK（同签名）。

普通用户更推荐使用「设置 → 数据备份与恢复 → 导出完整备份」在升级前自行留档；V3 的备份文件向后兼容，V2 无法读取 V3 新增字段，但核心成绩数据仍可被 V2 的恢复逻辑识别（V3 备份版本号为 3，V2 会因版本超出支持范围而拒绝恢复，因此跨版本恢复请使用 V2 时期导出的备份）。
