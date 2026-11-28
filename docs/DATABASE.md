# ScoreTrack 数据库结构说明

Room 持久化，数据库文件 `scoretrack.db`，当前 `version = 5`（V4.2.0 起；V4.1.0 为 version 4，V4.0.0 为 version 3，V3 为 version 2，V2 为 version 1），`exportSchema = true`（schema 导出到 `app/schemas/`，便于生成与校验迁移）。V3 新增 4 张个性化相关表（见第 4 节），V4 新增 `subject_selections`（见第 5 节），V4.1.0 为 `goal_cards` 增加 `logoPath` 列（`MIGRATION_3_4`），V4.2.0 新增 `countdown_exams`（见第 6 节，`MIGRATION_4_5`）。
定义文件：`app/src/main/java/com/scoretrack/data/db/Entities.kt`、`Daos.kt`、`ScoreTrackDatabase.kt`。

## 1. 实体一览

### students（学生）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | UUID |
| name | TEXT | 姓名 |
| className | TEXT | 班级 |
| studentNumber | TEXT | 学号 |
| createdAt / updatedAt | INTEGER | 毫秒时间戳 |
| isActive | INTEGER | 是否当前学生（同一时刻仅一条为 1，切换时事务内重置） |

同名允许存在（用班级+学号区分），`createOrGet` 按 姓名+班级+学号 精确去重，**不做同名合并**。

### subjects（科目）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| code | TEXT PK | `CHINESE/MATH/ENGLISH/PHYSICS/CHEMISTRY/GEOGRAPHY` |
| displayName | TEXT | 中文名 |
| maxScore | REAL | 满分（150/150/150/100/100/100） |
| scaled | INTEGER | 是否赋分科目（化学、地理为 1） |
| sortOrder | INTEGER | 展示顺序 |
| enabled | INTEGER | 是否启用（预留扩展科目） |

首次创建数据库时通过 `INSERT OR IGNORE` 写入六科种子数据；后续新增科目只需插入新行，不改表结构。

### exams（考试记录）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | UUID，同名考试也各自独立 |
| studentId | TEXT FK → students.id | `ON DELETE CASCADE` |
| name | TEXT | 考试名称（用户完全自定义） |
| examDate | INTEGER | 考试日期（毫秒时间戳，按本地时区解析） |
| note | TEXT | 备注 |
| createdAt / updatedAt | INTEGER | 时间戳 |
| sourceDocumentId | TEXT? | 关联原始成绩单，`ON DELETE SET NULL` |

### scores（成绩，一科一行）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| examId | TEXT FK → exams.id | 复合主键之一，`ON DELETE CASCADE` |
| subjectCode | TEXT | 复合主键之一 |
| score | REAL? | 正式成绩（化学/地理为赋分成绩） |
| rawScore | REAL? | 原始分（仅赋分科目可能非空） |
| maxScore | REAL | 该科满分的快照 |
| confidence | REAL? | OCR 置信度（0~1，人工录入为空） |
| sourceText | TEXT? | OCR 识别原文，便于核对 |

**缺失科目不写行、不写 0**；总分不落库，由六科齐全时实时计算（满分 750）。

### source_documents（原始成绩单）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | UUID |
| studentId | TEXT? | 导入时的学生 |
| fileName | TEXT | 原文件名 |
| mimeType | TEXT | MIME |
| localPath | TEXT | 应用私有目录路径（多文件用 `|` 连接） |
| pageCount | INTEGER | 页数 |
| importedAt | INTEGER | 导入时间 |
| kind | TEXT | `IMAGE` / `PDF` / `SHEET` |
| ocrPayloadJson | TEXT? | 可选保留的 OCR 结果 |

删除原始文件不会删除考试与成绩（`exams.sourceDocumentId` 先置 NULL）。

### ai_reports（AI 分析报告）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT PK | UUID |
| studentId | TEXT | 学生 |
| studentName | TEXT | 生成时姓名快照 |
| title | TEXT | 报告标题 |
| examRange | TEXT | 分析范围文本 |
| createdAt | INTEGER | 生成时间 |
| model | TEXT | 使用的模型名 |
| providerHost | TEXT | API 主机（不含 Key） |
| content | TEXT | Markdown 报告正文 |
| dataSnapshotJson | TEXT | **生成时的数据快照 JSON**（成绩改动后旧报告仍可追溯） |
| promptTokens / completionTokens | INTEGER? | token 用量 |
| status | TEXT | 状态（SUCCESS 等） |

### app_settings（键值设置）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| key | TEXT PK | 设置键 |
| value | TEXT | 值 |

主要键：`theme_mode`、`ai.base_url`、`ai.model`、`ai.temperature`、`ai.max_tokens`、`ai.api_key.cipher`（Keystore 加密后的密文，**不存明文**）、`ai.privacy_accepted`、`onboarded`、`last_backup_at`。

## 2. 关系

```
students 1 ── n exams 1 ── n scores
students 1 ── n source_documents
students 1 ── n ai_reports
exams.sourceDocumentId ── 1 source_documents（可为 NULL）
```

## 3. 索引与约束
- `scores` 复合主键 `(examId, subjectCode)`，天然避免同一考试同一科目重复
- 外键开启 CASCADE / SET NULL，删除学生即删除其考试与成绩
- 所有写入通过 DAO 的 `@Transaction` 方法（`saveExam`）保证考试+成绩原子性

## 4. V3 新增表（version 2）

### theme_settings（主题设置，单行 id = 1）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | INTEGER PK | 固定 1 |
| themeMode | TEXT | `SYSTEM` / `LIGHT` / `DARK` |
| presetId | TEXT | 预设 id（`default_blue` / `sky` / `mint` / `sakura` / `lavender` / `sunset` / `deep_sea` / `custom`） |
| primaryColorHex | TEXT | 当前生效主色 |
| customColorHex | TEXT | 用户自定义 HEX |
| useCustom | INTEGER | 是否使用自定义色 |
| updatedAt | INTEGER | 时间戳 |

### wallpaper_settings（壁纸设置，单行 id = 1）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | INTEGER PK | 固定 1 |
| wallpaperPath | TEXT? | 私有目录图片路径（`files/wallpapers/wallpaper_*.jpg`） |
| enabled | INTEGER | 是否启用（0 时不显示，不影响数据） |
| opacity | REAL | 0.0–1.0 |
| cropScale | REAL | 缩放 1.0–4.0 |
| cropOffsetX / cropOffsetY | REAL | 归一化偏移 −1.0–1.0 |
| updatedAt | INTEGER | 时间戳 |

### goal_cards（大学目标卡）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| cardId | TEXT PK | UUID |
| studentId | TEXT | 归属学生（建索引 `index_goal_cards_studentId`），按学生隔离 |
| universityName | TEXT | 目标大学 |
| majorName | TEXT | 目标专业（可为空） |
| targetScore | REAL | 目标总分（1–750） |
| isVisible | INTEGER | 是否在主页显示（关闭仅隐藏，不删除） |
| sortOrder | INTEGER | 排序 |
| accentColorHex | TEXT? | 卡片颜色，空表示跟随主题 |
| cardOpacity | REAL | 卡片透明度 |
| showAuxInfo | INTEGER | 是否显示辅助信息（剩余分数等） |
| logoPath | TEXT? | 校徽图片绝对路径（应用私有目录 `files/goal_logos/`），空表示未设置；V4.1.0 新增 |
| createdAt / updatedAt | INTEGER | 时间戳 |

进度不落库：运行时用「该学生最近一次考试总分 ÷ targetScore × 100」实时计算，不封顶；无完整成绩时显示提示。

### dashboard_cards（主页模块配置）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| cardType | TEXT PK | `goals` / `latest_exam` / `subject_overview` |
| isEnabled | INTEGER | 是否显示 |
| sortOrder | INTEGER | 排序 |

## 5. V4 新增表（version 3）

### subject_selections（学生选科，3+1+2）
| 列 | 类型 | 说明 |
| --- | --- | --- |
| studentId | TEXT PK | 学生 ID，外键 → `students.id`，`ON DELETE CASCADE` |
| firstSubject | TEXT? | 首选科目 code（`physics` / `history`） |
| secondSubject1 | TEXT? | 再选科目一 code（`biology` / `geography` / `politics` / `chemistry`） |
| secondSubject2 | TEXT? | 再选科目二 code |
| updatedAt | INTEGER | 更新时间戳（毫秒） |

- 一名学生一行，切换学生自动切换选科；删除学生级联删除。
- 选科与成绩解耦：清空/修改选科不会删除 `exams` 与 `scores`。
- V4 同时把 `subjects` 从 6 行扩展为 9 行（新增 `biology` 生物、`politics` 政治、`history` 历史，满分 100，生物/政治为赋分科目）。
- 原有 750 分总分体系不变：`Subject.displayOrder()` 仍是语数英物化地六科，`TOTAL_MAX_SCORE = 750`；选考总分（满分 300）是独立统计。

## 6. 迁移策略
- V2 → V3：`version 1 → 2`，`MIGRATION_1_2` 用 `CREATE TABLE IF NOT EXISTS` + `INSERT OR IGNORE` 建表并写默认行；`themeMode` 从旧 `app_settings.theme_mode` 继承，壁纸默认关闭，主页模块默认全部启用（顺序 0/1/2）。不修改、不删除任何旧表数据。
- V3 → V4：`version 2 → 3`，`MIGRATION_2_3` 建 `subject_selections` 表与索引、`INSERT OR IGNORE` 补齐 3 门新科目、`dashboard_cards` 插入 `subject_selection` 并顺延既有模块顺序。不修改、不删除既有数据。
- 升级前 `UpgradeManager` 自动备份 db/-wal/-shm、壁纸目录与 `app_settings` 快照到 `files/upgrade_backups/v1_to_v2_*/`；升级后校验各表记录数不减少，并写入 `upgrade.last_result` 供弹窗展示。详见 [UPGRADE.md](UPGRADE.md)。
- 禁止 `fallbackToDestructiveMigration()`；后续版本提升 version 并注册新迁移。
- 新增科目走数据（`subjects` 表），不改表结构。
- 备份文件 `format=scoretrack-backup`、`version=5`（兼容读取 v1–v4；`selections`、`logoBase64` 缺省为空），恢复前校验格式与版本。

## 6. V4.2 新增表（version 5）

### countdown_exams（自定义考试倒计时）

全局表，不绑定学生；主页卡片与「考试倒计时」管理页使用。

| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | TEXT | 主键，UUID，创建后不变 |
| name | TEXT | 考试名称（必填） |
| examDateEpochDay | INTEGER | 考试日期，`LocalDate.toEpochDay()`，与时区无关地表示「哪一天」（建索引 `index_countdown_exams_examDateEpochDay`） |
| timeMinutes | INTEGER? | 可选的当天分钟数（0..1439），NULL 表示未设置具体时间 |
| note | TEXT | 备注，可为空串 |
| isVisible | INTEGER | 是否在主页显示 |
| isPrimary | INTEGER | 是否为主页主显示考试；Repository 保证同一时刻最多一条为 1 |
| createdAt | INTEGER | 创建时间（毫秒） |
| updatedAt | INTEGER | 最后修改时间（毫秒） |

迁移：`MIGRATION_4_5` 仅执行 `CREATE TABLE countdown_exams` 与 `CREATE INDEX index_countdown_exams_examDateEpochDay`，不改动既有表，禁止破坏性迁移。

备份：`BackupFile.VERSION = 6`，新增 `countdowns: List<CountdownDto>`；旧备份无该字段时按空列表处理。
