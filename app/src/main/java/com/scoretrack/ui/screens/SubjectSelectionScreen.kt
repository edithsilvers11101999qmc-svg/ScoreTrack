package com.scoretrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import com.scoretrack.core.formatRate
import com.scoretrack.core.formatScore
import com.scoretrack.domain.analytics.AnalyticsEngine
import com.scoretrack.domain.selection.ElectiveScoreRules
import com.scoretrack.domain.selection.ElectiveTotalCalculator
import com.scoretrack.domain.selection.SelectionRules
import com.scoretrack.domain.selection.SubjectSelection
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.chart.ChartPoint
import com.scoretrack.ui.chart.ScoreLineChart
import com.scoretrack.ui.components.EmptyState
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.StatTile
import com.scoretrack.ui.components.WarningBanner

/**
 * 选科管理：3+1+2（首选 2 选 1、再选 4 选 2），按当前学生持久化，并与成绩统计联动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectSelectionScreen(vm: MainViewModel, onBack: () -> Unit) {
    val selection by vm.selection.collectAsState()
    val exams by vm.exams.collectAsState()
    val student by vm.activeStudent.collectAsState()

    var first by remember(selection.studentId) { mutableStateOf(selection.first) }
    var second by remember(selection.studentId) { mutableStateOf(selection.secondChoices) }
    var showClear by remember { mutableStateOf(false) }

    val draft = SubjectSelection(student?.id ?: "", first, second)
    val validation = SelectionRules.validate(first, second)
    val dirty = first != selection.first || second.toSet() != selection.secondChoices.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选科管理") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
            )
        },
    ) { padding ->
        if (student == null) {
            Box(Modifier.padding(padding).fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Outlined.Info,
                    title = "请先创建学生档案",
                    message = "选科结果按学生保存，创建学生后即可设置。",
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    title = "当前学生",
                    subtitle = "${student?.name ?: ""}" + (student?.className?.takeIf { it.isNotBlank() }
                        ?.let { " · $it" } ?: ""),
                ) {
                    Text(
                        "新高考 3+1+2：首选科目 2 选 1，再选科目 4 选 2，共 12 种合法组合。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "首选课程（2选1）", subtitle = "物理 / 历史，必须选择一门") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Subject.firstChoiceOptions().forEach { subject ->
                            ChoiceTile(
                                subject = subject,
                                selected = first == subject,
                                enabled = true,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    first = if (first == subject) null else subject
                                },
                            )
                        }
                    }
                }
            }

            item {
                val full = second.size >= 2
                SectionCard(
                    title = "再选科目（4选2）",
                    subtitle = if (full) "已选满 2 门，先取消已选科目才能更换" else "还需选择 ${2 - second.size} 门",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Subject.secondChoiceOptions().chunked(2).forEach { rowSubjects ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowSubjects.forEach { subject ->
                                    val selected = subject in second
                                    ChoiceTile(
                                        subject = subject,
                                        selected = selected,
                                        enabled = selected || !full,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            second = when {
                                                selected -> second - subject
                                                second.size < 2 -> second + subject
                                                else -> second
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SelectionSummaryCard(draft, validation.ok, exams)
            }

            if (selection.isComplete && !dirty) {
                item {
                    WarningBanner(
                        text = "当前保存的组合：${selection.comboLabel}。修改下方选择后点击「保存选科」生效。",
                        severe = false,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { vm.saveSelection(first, second) },
                        enabled = validation.ok && dirty,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (dirty) "保存选科" else "已保存")
                    }
                    OutlinedButton(
                        onClick = { showClear = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("清空重选") }
                }
            }

            if (!validation.ok) {
                item {
                    WarningBanner(
                        text = validation.message ?: "选科组合尚未完成",
                        severe = false,
                    )
                }
            }

            item { ElectiveStatsSection(draft, exams) }

            item { CombinationReferenceCard() }

            item {
                Text(
                    "提示：切换学生会自动切换到该学生的选科；清空或修改选科都不会删除任何历史成绩，未选科目仍可在「成绩」中查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }

    if (showClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空选科？") },
            text = { Text("清空后可重新选择。历史成绩、考试记录与 AI 报告都不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    first = null
                    second = emptyList()
                    vm.clearSelection()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ChoiceTile(
    subject: Subject,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(subject.cnName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = content)
                Text(
                    "满分 ${formatScore(subject.maxScore)}" + if (subject.isScaled) " · 赋分" else " · 原始分",
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "已选", tint = content, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SelectionSummaryCard(selection: SubjectSelection, valid: Boolean, exams: List<ExamRecord>) {
    val latest = exams.lastOrNull()
    val electiveTotal = latest?.let {
        ElectiveTotalCalculator.total(it.scores, selection, ElectiveScoreRules.default())
    }
    SectionCard(title = "选科结果", subtitle = "实时预览，保存后才会写入数据库") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryRow("首选科目", selection.first?.cnName ?: "未选择")
            SummaryRow("再选科目一", selection.secondChoices.getOrNull(0)?.cnName ?: "未选择")
            SummaryRow("再选科目二", selection.secondChoices.getOrNull(1)?.cnName ?: "未选择")
            SummaryRow("完整组合", selection.comboLabel)
            Spacer(Modifier.height(4.dp))
            if (selection.isComplete && valid) {
                Text(
                    "选科组合已完成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp),
                )
                val totals = exams.map { ElectiveTotalCalculator.total(it.scores, selection) }.filter { it.complete }
                if (totals.isNotEmpty()) {
                    Text(
                        "选考总分（3 科，满分 ${formatScore(totals.last().maxTotal)}）：" +
                            (electiveTotal?.text ?: "暂不计算"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "暂无选考三科齐全的考试记录，选考总分暂不计算（不会按 0 分处理）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "还需选择 ${selection.missingCount} 门" + (if (selection.first == null) "（首选 1 门）" else ""),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                LinearProgressIndicator(
                    progress = { (3 - selection.missingCount) / 3f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 选考科目统计：只用真实数据库成绩，未选科目不计入。 */
@Composable
private fun ElectiveStatsSection(selection: SubjectSelection, exams: List<ExamRecord>) {
    val subjectList = selection.selectedSubjects
    SectionCard(
        title = "选考成绩概览",
        subtitle = if (subjectList.isEmpty()) "完成选科后显示选考科目统计" else "仅统计当前选科的三门科目",
    ) {
        if (subjectList.isEmpty()) {
            Text(
                "尚未完成选科，暂无选考成绩统计。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        if (exams.isEmpty()) {
            Text(
                "暂无考试记录，录入成绩后即可查看选考科目趋势。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        subjectList.forEach { subject ->
            val stat = AnalyticsEngine.subjectStat(subject, exams)
            val points = exams.mapIndexedNotNull { index, exam ->
                exam.scoreOf(subject)?.let { score ->
                    ChartPoint(
                        label = if (exam.name.length <= 6) exam.name else "第${index + 1}次",
                        value = score,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subject.cnName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (subject.isScaled) {
                        Spacer(Modifier.width(6.dp))
                        Text("赋分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "平均 " + (stat.average?.let { formatScore(kotlin.math.round(it * 10) / 10.0) } ?: "—") +
                            " · 得分率 " + (stat.averageRate?.let { formatRate(it) } ?: "—"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (points.size >= 2) {
                    ScoreLineChart(
                        points = points,
                        maxValue = subject.maxScore,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        showValues = points.size <= 6,
                    )
                } else {
                    Text(
                        "该科目有效成绩少于 2 次，暂不绘制趋势。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        val totals = exams.mapIndexedNotNull { index, exam ->
            val total = ElectiveTotalCalculator.total(exam.scores, selection)
            total.total?.let { value ->
                ChartPoint(
                    label = if (exam.name.length <= 6) exam.name else "第${index + 1}次",
                    value = value,
                )
            }
        }
        if (totals.size >= 1) {
            Text(
                "选考总分（3 科合计，默认原始分累加，满分 300）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (totals.size >= 2) {
                ScoreLineChart(
                    points = totals,
                    maxValue = 300.0,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    showValues = totals.size <= 6,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "最近选考总分",
                    value = totals.lastOrNull()?.let { formatScore(it.value) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "选考总分平均",
                    value = formatScore(kotlin.math.round(totals.map { it.value }.average() * 10) / 10.0),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "说明：该总分是独立统计，不改变原有的 750 分总分体系；不同省份赋分规则可在 domain/selection 中扩展。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CombinationReferenceCard() {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(
        title = "合法组合（12 种）",
        subtitle = if (expanded) "首选 2 种 × 再选 6 种组合" else "点击展开查看全部合法组合",
    ) {
        if (!expanded) {
            TextButton(onClick = { expanded = true }) { Text("查看 12 种组合") }
        } else {
            val combos = SelectionRules.allCombinations()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                combos.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { combo ->
                            Text(
                                combo.label,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
