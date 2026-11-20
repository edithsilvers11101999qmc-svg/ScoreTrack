# 选科管理（新高考 3+1+2）· 功能与测试报告

版本：ScoreTrack V4.0.0（versionCode 4）
范围：在 V3.0.0 基础上增量新增选科管理，保留全部既有功能、数据库与学生数据，覆盖安装升级。

---

## 1. 规则

| 类别 | 可选科目 | 数量 |
| --- | --- | --- |
| 首选科目 | 物理、历史 | 2 选 1 |
| 再选科目 | 生物、地理、政治、化学 | 4 选 2 |
| 选考合计 | 首选 1 + 再选 2 | 3 门 |

- 合法组合共 **12 种**（2 × C(4,2)），由 `SelectionRules.allCombinations()` 生成，单元测试逐个校验。
- 校验规则（`SelectionRules.validate(first, second)`）：
  - 首选必须且只能是物理或历史；
  - 再选必须正好 2 门，不能多、不能少；
  - 再选不能重复；
  - 再选只能来自生物/地理/政治/化学，不能混入首选科目；
  - 未完成时返回 `missing`（还差几门），页面显示「还需选择 N 门」。
- 校验失败不写库（`SubjectSelectionRepository.save` 直接返回失败原因）。

## 2. 与成绩体系的联动

- 科目从 6 科扩展到 **9 科**（`Subject.allSubjects()`），新增生物、政治、历史，满分均 100，生物/政治为赋分，历史为原始分。
- **原有 750 分总分规则完全不变**：`Subject.displayOrder()` 仍为语数英物化地六科，`TOTAL_MAX_SCORE = 750`，考试总分只按这六科计算，缺科不按 0 分。
- 录入手动成绩时，9 科都可录入；已选科目在输入框标题显示「· 选考」、说明显示「满分 N · 已选考」并使用主题色描边高亮；生物/政治/历史单独成「选考科目（3+1+2）」区块。
- 选考总分作为**独立统计**：`ElectiveTotalCalculator.total(scores, selection, rule)`，满分 300，缺任意一科返回 `null`（不按 0 分），不参与 750 分总分。
- 赋分规则通过 `ElectiveScoreRule` 接口扩展（默认 `RawScoreRule` 直接累加原始/赋分成绩），不同省份可注册自定义规则，无需改动页面与数据库。
- 修改或清空选科不会删除任何历史成绩（`exam scores` 与 `subject_selections` 完全独立）。
- 未选科目的历史成绩仍可在「成绩」页考试详情与「分析 → 六科」中查看（分析页科目筛选会追加有数据的未选科目并标注「· 未选」）。

## 3. 持久化

表 `subject_selections`（Room v3）：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `studentId` | TEXT PK | 学生 ID，外键 → `students.id`，`ON DELETE CASCADE` |
| `firstSubject` | TEXT? | 首选科目 code（physics / history） |
| `secondSubject1` | TEXT? | 再选科目一 code |
| `secondSubject2` | TEXT? | 再选科目二 code |
| `updatedAt` | INTEGER | 更新时间戳 |

- 每个学生一条记录，切换学生时页面与主页卡片自动切换到该学生的选科。
- 删除学生时选科记录随之级联删除，考试记录同样级联，符合既有数据库关联规则。
- 支持清空（`clear()` 删除该学生记录）后重新选择。

## 4. 主页与设置入口

- 主页「选科概览」卡片：
  - 已完成：显示完整组合（如「物理+生物+化学」）+ 首选 / 再选一 / 再选二三个小标签 + 「修改」，整卡可点击进入选科管理。
  - 未完成：红色提示「尚未完成选科，点击设置」+「去设置」+「还需选择 N 门」。
  - 该卡片纳入主页模块配置（`dashboard_cards.cardType = "subject_selection"`），可开关、可上下排序。
- 设置 →「选科管理」显示当前组合或「尚未完成选科（3+1+2）」。

## 5. 统计与可视化

选科管理页底部「选考成绩概览」：

- 逐门选考科目：当前成绩、历史最高、历史平均、得分率、最近变化。
- 每门科目独立折线图（满分取该科满分 100），少于 2 次有效成绩时提示「数据不足」，不画假曲线。
- 选考总分曲线（满分 300）+ 最近 / 平均统计；三科未齐时明确提示「暂不计算，不会按 0 分处理」。
- 全部数据来自 Room 中已确认保存的考试记录。

## 6. AI 联动

- 生成报告时在快照与提示词中加入：首选科目、再选科目、选科组合、选考三科、最近选考总分（/300）、选科是否完成。
- `SYSTEM_PROMPT` 新增两条约束：
  - 3.1 必须围绕选考三科展开分析，未选科目只能作历史背景，不能当作当前选考科目；
  - 3.2 不得仅凭选科组合推断大学录取资格，需以具体高校专业招生要求为依据。
- 未完成选科时提示词写「选科：尚未完成」，AI 不得凭空推断组合。
- 快照 JSON 字段向后兼容：旧报告反序列化时 `firstChoice/secondChoices/selectionCombo/electiveSubjects/electiveTotal` 默认为空。

## 7. 备份与恢复

- 备份格式版本升至 `version = 4`，新增 `selections` 数组（`studentId/firstSubject/secondSubject1/secondSubject2/updatedAt`）。
- 恢复时在学生写入之后按 `studentId` upsert 选科，兼容旧备份（`selections` 缺省为空）。
- 仍不导出 API Key。

## 8. 数据库迁移

- 数据库版本 **v2 → v3**（`MIGRATION_2_3`），不使用破坏性迁移：
  - `CREATE TABLE IF NOT EXISTS subject_selections (...)` + 索引 `index_subject_selections_studentId`；
  - `INSERT OR IGNORE INTO subjects` 补入 biology / politics / history；
  - `dashboard_cards` 插入 `subject_selection`（`sortOrder = 1`），并把 latest_exam、subject_overview 的顺序 +1（已存在则跳过）。
- 升级前 `UpgradeManager.prepareIfNeeded` 自动备份数据库、`-wal`、`-shm`、壁纸目录与 `app_settings` 快照到 `files/upgrade_backups/v{old}_to_v{new}_{时间戳}/`。
- 升级后 `UpgradeManager.finish` 校验 students / exams / scores / ai_reports / goal_cards / subject_selections 数量不减少，并写入 `upgrade.last_result`，首页弹窗展示前后数量与备份路径。

## 9. 测试报告

环境：Windows 11 + JDK 17.0.21 + Gradle 8.9 + Android SDK 35；模拟器 AVD `scoretrack`（Android 15 / API 35 / x86_64 / 1080×2400）。

### 9.1 单元测试

`./gradlew :app:testDebugUnitTest` → `BUILD SUCCESSFUL`，**41 个用例全部通过**（V3 的 27 个 + V4 新增 14 个）。

新增 `SubjectSelectionTest`（14 个）：

| 用例 | 结果 |
| --- | --- |
| 首选只有物理与历史 | 通过 |
| 再选只有生物地理政治化学 | 通过 |
| 十二种合法组合全部通过校验且互不重复 | 通过 |
| 全部十二种组合都能生成完整选科对象 | 通过 |
| 缺少首选不合法 | 通过 |
| 首选不能是再选科目 | 通过 |
| 再选数量不足不合法 | 通过 |
| 再选超过两门不合法 | 通过 |
| 再选重复不合法 | 通过 |
| 再选混入首选科目不合法 | 通过 |
| 未完成选科时 selectedSubjects 为空且提示剩余数量 | 通过 |
| 选考总分按三科求和且缺失不按零计算（270/300） | 通过 |
| 未完成选科时选考总分不可用 | 通过 |
| 赋分与原始分标记符合 3+1+2，六科顺序与 750 分不变 | 通过 |

### 9.2 覆盖安装升级（真实旧数据库）

1. 模拟器安装 V3.0.0（versionCode 3），数据库 `user_version = 2`，含 1 名学生（LiMing / 3 / 3001）、2 次考试（高二期中 643、11月月考 661）、12 条成绩、1 张目标卡、1 份 AI 报告、樱花粉主题与 65% 壁纸。
2. `adb install -r app-release.apk`（V4，versionCode 4），**不卸载**。
3. 启动后弹窗：

```
升级完成，数据已保留
数据库版本：v2 → v3
学生：1 → 1
考试记录：2 → 2
AI 报告：2 → 2
升级前已自动备份：/data/user/0/com.scoretrack/files/upgrade_backups/v2_to_v3_...
```

4. 迁移后数据库核对：`user_version = 3`；`subjects = 9`（新增 biology / politics / history）；`subject_selections` 表存在；`dashboard_cards = goals/0、subject_selection/1、latest_exam/2、subject_overview/3`；`students = 1`、`exams = 2`、`scores = 12`、`ai_reports = 2`、`goal_cards = 1` 全部保留。

### 9.3 功能测试

| 测试项 | 步骤 | 结果 |
| --- | --- | --- |
| 物理/历史二选一 | 选物理后再点历史 | 物理自动取消、历史选中，组合实时更新 |
| 生物/地理/政治/化学四选二 | 连续点 3 门 | 选满 2 门后其余置灰不可点，提示「已选满 2 门，先取消已选科目才能更换」 |
| 12 种合法组合 | 单元测试穷举 + 页面「查看 12 种组合」 | 全部合法，无重复 key |
| 非法组合拒绝 | 缺首选 / 只选 1 门再选 | 保存按钮置灰，红色提示「还需要选择 N 门…」，不写库 |
| 保存与持久化 | 保存「物理+生物+化学」→ 强制停止 → 重启 | 主页卡片、设置页、选科页均保持该组合 |
| 切换学生隔离 | 新建 WangDi（4 · 4001），设置「历史+政治+化学」后切回 LiMing | WangDi 显示历史组合，LiMing 仍为物理组合，互不影响 |
| 修改选科不删历史成绩 | 清空 WangDi 选科 | `subject_selections` 仅删除该学生行；`exams = 2`、`scores = 12` 不变 |
| 主页显示 | 完成选科后返回主页 | 卡片显示「物理+生物+化学」+ 首选/再选一/再选二 +「修改」；未完成时显示「尚未完成选科，点击设置」 |
| 成绩录入高亮 | 手动录入页 | 物理/化学显示「· 选考 / 已选考」并描边高亮，地理（未选）不高亮，新增「选考科目（3+1+2）」区块录入生物/政治/历史 |
| 成绩统计识别选考 | 分析 → 六科 | 科目筛选显示「物理 · 选考」「化学 · 选考」，未选但有数据的科目标「· 未选」 |
| AI 读取选科 | 配置本地 Mock（`http://10.0.2.2:8766`）后生成报告，抓取请求体 | 提示词含「选科（3+1+2）：物理+生物+化学（首选：物理；再选：生物、化学）」「选考三科…」；快照 JSON 含 `firstChoice/secondChoices/selectionCombo/electiveSubjects`；系统提示含 3.1/3.2 约束 |
| 选考总分不伪造 | 选考三科未齐（缺生物成绩） | 页面显示「暂无选考三科齐全的考试记录，选考总分暂不计算（不会按 0 分处理）」，`electiveTotal = null` |
| 备份导出 | 导出 JSON 备份 | `version = 4`，含 `selections` 两条记录；不含 `api_key` |
| 既有功能回归 | OCR 导入 / 曲线 / 目标卡 / 主题 / 壁纸 / AI | 均正常，无崩溃 |

## 10. 已知边界

- 选考总分仅按 3 门选考科目求和（满分 300），与 750 分总分体系并列，不代表任何省份的具体投档规则。
- 赋分规则接口默认实现为「直接使用录入分数」，如需按省份换算，请实现 `ElectiveScoreRule` 并通过 `ElectiveScoreRules.register` 注册。
- `input text` 无法输入中文，设备自动化测试中的名称使用 ASCII；应用本身完整支持中文输入与显示。
