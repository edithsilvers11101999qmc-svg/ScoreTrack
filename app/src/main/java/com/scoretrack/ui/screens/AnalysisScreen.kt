package com.scoretrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import com.scoretrack.core.formatDelta
import com.scoretrack.core.formatRate
import com.scoretrack.core.formatScore
import com.scoretrack.domain.analytics.AnalysisResult
import com.scoretrack.domain.analytics.SubjectStat
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.chart.ChartPoint
import com.scoretrack.domain.selection.SubjectSelection
import com.scoretrack.ui.chart.ScoreLineChart
import com.scoretrack.ui.components.EmptyState
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.StatTile
import com.scoretrack.ui.components.WarningBanner

@Composable
fun AnalysisScreen(
    vm: MainViewModel,
    onOpenAi: () -> Unit,
) {
    val exams by vm.exams.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val selection by vm.selection.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("总分", "六科", "综合统计")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("成绩分析", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "共 ${exams.size} 次考试 · 完整 ${analysis.overall.completeExams} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenAi) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("AI 分析")
            }
        }
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        if (exams.isEmpty()) {
            EmptyState(
                title = "暂无分析数据",
                message = "至少录入一次考试后才会生成统计与曲线。数据不足时不会伪造趋势结论。",
            )
            return@Column
        }
        when (tab) {
            0 -> TotalTab(exams, analysis)
            1 -> SubjectTab(analysis, selection)
            else -> StatsTab(analysis)
        }
    }
}

@Composable
private fun TotalTab(exams: List<ExamRecord>, analysis: AnalysisResult) {
    val complete = exams.filter { it.total != null }
    val points = complete.mapIndexed { index, exam -> ChartPoint(exam.shortLabel(index), exam.total ?: 0.0, exam.dateText) }
    var selected by remember { mutableIntStateOf(-1) }
    var detail by remember { mutableStateOf<ExamRecord?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "总分曲线", subtitle = "横轴为考试，纵轴总分（满分 750）；可双指缩放、拖动，点击节点查看详情") {
            if (points.isEmpty()) {
                WarningBanner("六科成绩完整的考试记录不足，暂不绘制总分曲线。")
            } else {
                ScoreLineChart(
                    points = points,
                    maxValue = 750.0,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    selectedIndex = selected,
                    onPointSelected = { index ->
                        selected = index
                        detail = complete.getOrNull(index)
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("总分平均", analysis.overall.average?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
            StatTile("总分最高", analysis.overall.best?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("最近总分", analysis.overall.latest?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
            StatTile(
                "最近变化",
                analysis.overall.latestDelta?.let { formatDelta(it) } ?: "数据不足",
                Modifier.weight(1f),
            )
        }
        SectionCard(title = "历次考试") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                exams.sortedByDescending { it.date }.forEach { exam ->
                    Row(
                        Modifier.fillMaxWidth().clickable { detail = exam }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(exam.name, style = MaterialTheme.typography.bodyMedium)
                            Text(exam.dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            exam.total?.let { formatScore(it) } ?: "不完整",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (exam.total == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        if (analysis.notes.isNotEmpty()) {
            SectionCard(title = "数据说明") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    analysis.notes.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    detail?.let { exam ->
        ExamDetailDialog(exam) { detail = null }
    }
}

@Composable
private fun SubjectTab(analysis: AnalysisResult, selection: SubjectSelection) {
    var subject by remember { mutableStateOf(Subject.CHINESE) }
    var rateMode by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }

    val baseSubjects = Subject.displayOrder()
    val extraSubjects = Subject.allSubjects().filter { it !in baseSubjects && (it in selection.selectedSubjects || (analysis.subjectStat(it)?.count ?: 0) > 0) }
    val subjectList = baseSubjects + extraSubjects
    val stat = analysis.subjectStat(subject)
    val points = analysis.exams.mapIndexedNotNull { index, exam ->
        val value = exam.scores[subject] ?: return@mapIndexedNotNull null
        if (rateMode) {
            ChartPoint(exam.shortLabel(index), subject.rateOf(value) * 100.0, exam.dateText)
        } else {
            ChartPoint(exam.shortLabel(index), value, exam.dateText)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            subjectList.forEach { item ->
                FilterChip(
                    selected = subject == item,
                    onClick = { subject = item; selected = -1 },
                    label = {
                        Text(
                            item.cnName + if (item in selection.selectedSubjects) " · 选考" else if (item !in baseSubjects) " · 未选" else "",
                        )
                    },
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !rateMode,
                onClick = { rateMode = false; selected = -1 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("原始分") }
            SegmentedButton(
                selected = rateMode,
                onClick = { rateMode = true; selected = -1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("得分率") }
        }
        SectionCard(
            title = "${subject.cnName}成绩曲线",
            subtitle = if (rateMode) "得分率 = 成绩 ÷ ${subject.maxScore.toInt()} × 100%" else "满分 ${subject.maxScore.toInt()} 分" +
                if (subject.isScaled) "（赋分成绩）" else "",
        ) {
            if (points.size < 2) {
                WarningBanner("该科目有效成绩少于 2 次，暂时无法绘制趋势曲线。")
            } else {
                ScoreLineChart(
                    points = points,
                    maxValue = if (rateMode) 100.0 else subject.maxScore,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    valueSuffix = if (rateMode) "%" else "",
                    selectedIndex = selected,
                    onPointSelected = { selected = it },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "当前",
                stat?.current?.let { if (rateMode) formatRate(subject.rateOf(it)) else formatScore(it) } ?: "—",
                Modifier.weight(1f),
            )
            StatTile(
                "历史最高",
                stat?.best?.let { if (rateMode) formatRate(subject.rateOf(it)) else formatScore(it) } ?: "—",
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "历史平均",
                stat?.average?.let {
                    val avg = kotlin.math.round(it * 10) / 10.0
                    if (rateMode) formatRate(subject.rateOf(avg)) else formatScore(avg)
                } ?: "—",
                Modifier.weight(1f),
            )
            StatTile(
                "最近变化",
                if (rateMode) stat?.latestRateDelta?.let { String.format("%+.1f%%", it * 100.0) } ?: "数据不足"
                else stat?.latestDelta?.let { formatDelta(it) } ?: "数据不足",
                Modifier.weight(1f),
            )
        }
        SectionCard(title = "六科得分率对比", subtitle = "标准化后可直接横向比较不同满分的科目") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                analysis.subjects.forEach { item ->
                    val rate = item.averageRate
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.subject.cnName, Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
                        Column(Modifier.weight(1f)) {
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)),
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier
                                        .fillMaxWidth((rate ?: 0.0).toFloat().coerceIn(0f, 1f))
                                        .height(10.dp)
                                        .background(
                                            if (item.subject.isScaled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(5.dp),
                                        ),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rate?.let { formatRate(it) } ?: "—",
                            Modifier.width(56.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (stat != null) {
            SectionCard(title = "趋势判断") {
                Text(
                    "当前趋势：" + stat.trend.label +
                        if (stat.count >= 2) "（基于 ${stat.count} 次成绩）" else "（有效成绩 ${stat.count} 次，数据不足）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StatsTab(analysis: AnalysisResult) {
    val overall = analysis.overall
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "总分统计") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("平均分", overall.average?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
                    StatTile("最高分", overall.best?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
                    StatTile("最近一次", overall.latest?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("最近变化", overall.latestDelta?.let { formatDelta(it) } ?: "数据不足", Modifier.weight(1f))
                    StatTile("标准差", overall.stability?.let { formatScore(kotlin.math.round(it * 10) / 10.0) } ?: "数据不足", Modifier.weight(1f))
                    StatTile("趋势", overall.trend.label, Modifier.weight(1f))
                }
            }
        }
        SectionCard(title = "六科明细", subtitle = "得分率用于横向比较不同满分科目") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                analysis.subjects.forEach { item -> SubjectStatRow(item) }
            }
        }
        if (analysis.strengths.isNotEmpty() || analysis.weaknesses.isNotEmpty()) {
            SectionCard(title = "优势与薄弱", subtitle = "按平均得分率判断，不使用绝对分数比较") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (analysis.strengths.isNotEmpty()) {
                        Text(
                            "相对优势：" + analysis.strengths.joinToString("、") { "${it.subject.cnName}（${it.rateText}）" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (analysis.weaknesses.isNotEmpty()) {
                        Text(
                            "相对薄弱：" + analysis.weaknesses.joinToString("、") { "${it.subject.cnName}（${it.rateText}）" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (analysis.notes.isNotEmpty()) {
            SectionCard(title = "数据说明") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    analysis.notes.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SubjectStatRow(stat: SubjectStat) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.2f)) {
            Text(stat.subject.cnName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "得分率 " + (stat.averageRate?.let { formatRate(it) } ?: "—") + " · " + stat.trend.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1.6f), horizontalAlignment = Alignment.End) {
            Text(
                "当前 " + (stat.current?.let { formatScore(it) } ?: "—") +
                    "  最高 " + (stat.best?.let { formatScore(it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "平均 " + (stat.average?.let { formatScore(kotlin.math.round(it * 10) / 10.0) } ?: "—") +
                    "  变化 " + (stat.latestDelta?.let { formatDelta(it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ExamDetailDialog(exam: ExamRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exam.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(exam.dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Subject.displayOrder().forEach { subject ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            subject.cnName + if (subject.isScaled) "（赋分）" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            exam.scores[subject]?.let { formatScore(it) } ?: "未记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (exam.scores[subject] == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("总分", style = MaterialTheme.typography.titleMedium)
                    Text(
                        exam.total?.let { formatScore(it) } ?: "不完整",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (exam.note.isNotBlank()) {
                    Text("备注：" + exam.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
