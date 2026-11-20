package com.scoretrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import com.scoretrack.domain.selection.SubjectSelection
import com.scoretrack.core.formatDelta
import com.scoretrack.core.formatRate
import com.scoretrack.core.formatScore
import com.scoretrack.domain.analytics.AnalysisResult
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.chart.MiniSparkline
import com.scoretrack.ui.components.DeltaText
import com.scoretrack.ui.components.EmptyState
import com.scoretrack.ui.components.GoalCardView
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.StatTile
import com.scoretrack.ui.components.WallpaperLayer
import com.scoretrack.ui.components.WallpaperScrim

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenImport: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenExam: (String) -> Unit,
    onOpenStudents: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenSelection: () -> Unit,
) {
    val students by vm.students.collectAsState()
    val active by vm.activeStudent.collectAsState()
    val exams by vm.exams.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val goals by vm.goals.collectAsState()
    val wallpaper by vm.wallpaper.collectAsState()
    val dashboard by vm.dashboard.collectAsState()
    val selection by vm.selection.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showSwitch by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer(wallpaper)
        WallpaperScrim(strength = 0.62f)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("当前学生", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = { if (students.isEmpty()) showAddDialog = true else showSwitch = true }) {
                                Text(
                                    active?.name ?: "尚未创建学生",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = showSwitch, onDismissRequest = { showSwitch = false }) {
                                students.forEach { student ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    student.name + if (student.id == active?.id) "（当前）" else "",
                                                    fontWeight = if (student.id == active?.id) FontWeight.SemiBold else FontWeight.Normal,
                                                )
                                                val detail = listOf(student.className, student.studentNumber)
                                                    .filter { it.isNotBlank() }.joinToString(" · ")
                                                if (detail.isNotBlank()) {
                                                    Text(detail, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        },
                                        onClick = {
                                            vm.selectStudent(student.id)
                                            showSwitch = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("管理学生档案") },
                                    onClick = { showSwitch = false; onOpenStudents() },
                                )
                            }
                        }
                    }
                    if (active != null) {
                        val detail = listOf(active!!.className, active!!.studentNumber)
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        if (detail.isNotBlank()) {
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (students.isEmpty()) {
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("创建")
                    }
                }
            }
        }

        if (active == null) {
            item {
                SectionCard {
                    EmptyState(
                        icon = Icons.Outlined.School,
                        title = "先创建一个学生档案",
                        message = "ScoreTrack 支持多个学生，成绩按学生分别管理。\n填写姓名、班级、学号后即可录入成绩。",
                        actionLabel = "创建学生档案",
                        onAction = { showAddDialog = true },
                    )
                }
            }
        } else {

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onOpenImport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("导入成绩")
                    }
                    Button(onClick = onOpenAi, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("AI 成绩分析")
                    }
                }
            }

            val visibleGoals = goals.filter { it.visible }
            dashboard.sortedBy { it.sortOrder }.forEach { section ->
                if (!section.enabled) return@forEach
                when (section.cardType) {
                    "goals" -> {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("大学目标", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = onOpenGoals) {
                                    Text(if (visibleGoals.isEmpty()) "添加目标" else "管理目标")
                                }
                            }
                        }
                        if (visibleGoals.isEmpty()) {
                            item {
                                SectionCard {
                                    val hidden = goals.size
                                    Text(
                                        if (hidden > 0) {
                                            "已隐藏 $hidden 个目标卡（数据仍保留）。可在「管理目标」中重新显示或编辑。"
                                        } else {
                                            "还没有设置目标大学。添加目标后，这里会显示达成进度与还差多少分。"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            items(visibleGoals, key = { "goal_" + it.id }) { goal ->
                                GoalCardView(
                                    goal = goal,
                                    latestTotal = exams.lastOrNull()?.total,
                                    onClose = { vm.setGoalVisible(goal.id, false) },
                                    onClick = onOpenGoals,
                                )
                            }
                        }
                    }

                    "subject_selection" -> item {
                        SubjectSelectionCard(selection = selection, onClick = onOpenSelection)
                    }

                    "latest_exam" -> item { LatestExamCard(exams = exams, onOpenExam = onOpenExam) }

                    "subject_overview" -> item {
                        SectionCard(title = "六科概览", subtitle = "最近一次考试（缺失科目显示为 —，不按 0 分处理）") {
                            val latest = exams.lastOrNull()
                            if (latest == null) {
                                Text(
                                    "还没有成绩记录，导入或手动录入后这里会显示六科成绩。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                SubjectOverviewGrid(latest)
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "成绩概况", subtitle = "基于已确认保存的考试记录") {
                    OverallTiles(analysis)
                }
            }

            if (exams.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("最近考试", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onOpenAnalysis) { Text("查看分析") }
                    }
                }
                items(exams.sortedByDescending { it.date }.take(3), key = { it.id }) { exam ->
                    RecentExamRow(exam) { onOpenExam(exam.id) }
                }
            }
        }
    }
    }

    StudentCreateDialog(
        visible = showAddDialog,
        onDismiss = { showAddDialog = false },
        onConfirm = { name, className, number ->
            vm.createStudent(name, className, number)
            showAddDialog = false
        },
    )
}

@Composable
private fun SubjectSelectionCard(selection: SubjectSelection, onClick: () -> Unit) {
    SectionCard(
        title = "选科概览",
        subtitle = "新高考 3+1+2 · 点击进入选科管理",
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        if (!selection.isComplete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "尚未完成选科，点击设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.weight(1f))
                Text("去设置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "还需选择 ${selection.missingCount} 门（首选 1 门 + 再选 2 门）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selection.comboLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text("修改", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionChip("首选", selection.first?.cnName ?: "—")
                SelectionChip("再选一", selection.secondChoices.getOrNull(0)?.cnName ?: "—")
                SelectionChip("再选二", selection.secondChoices.getOrNull(1)?.cnName ?: "—")
            }
        }
    }
}

@Composable
private fun SelectionChip(label: String, value: String) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LatestExamCard(exams: List<ExamRecord>, onOpenExam: (String) -> Unit) {
    val latest = exams.lastOrNull()
    val totals = exams.mapNotNull { it.total }
    val delta = if (totals.size >= 2) totals.last() - totals[totals.size - 2] else null
    SectionCard(
        title = "最近考试",
        subtitle = latest?.dateText ?: "暂无考试记录",
    ) {
        if (latest == null) {
            EmptyState(
                icon = Icons.Outlined.DocumentScanner,
                title = "还没有考试记录",
                message = "点击「导入成绩」拍照识别成绩单，或手动录入一次考试。",
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        latest.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            latest.total?.let { formatScore(it) } ?: "—",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            " / 750",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    if (!latest.isComplete) {
                        Text(
                            "缺 " + latest.missingSubjects.joinToString("、") { it.cnName } + "，总分暂不计算",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                DeltaBadge(delta)
            }
            if (totals.size >= 2) {
                Spacer(Modifier.height(8.dp))
                MiniSparkline(values = totals)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { onOpenExam(latest.id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("查看这次考试详情")
            }
        }
    }
}

@Composable
private fun DeltaBadge(delta: Double?) {
    if (delta == null) {
        Text("变化数据不足", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val color = when {
        delta > 0.01 -> Color(0xFF2E7D32)
        delta < -0.01 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("较上次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatDelta(delta),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SubjectOverviewGrid(exam: ExamRecord) {
    val subjects = Subject.displayOrder()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        subjects.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { subject ->
                    val score = exam.scores[subject]
                    Column(
                        Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(subject.cnName, style = MaterialTheme.typography.labelMedium)
                            if (subject.isScaled) {
                                Text(
                                    " 赋分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        Text(
                            score?.let { formatScore(it) } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (score == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            score?.let { formatRate(subject.rateOf(it)) } ?: "未记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OverallTiles(analysis: AnalysisResult) {
    val overall = analysis.overall
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("总分平均", overall.average?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
            StatTile("总分最高", overall.best?.let { formatScore(it) } ?: "数据不足", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "最近变化",
                overall.latestDelta?.let { formatDelta(it) } ?: "数据不足",
                Modifier.weight(1f),
                valueColor = when {
                    (overall.latestDelta ?: 0.0) > 0 -> Color(0xFF2E7D32)
                    (overall.latestDelta ?: 0.0) < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            StatTile("总分趋势", overall.trend.label, Modifier.weight(1f))
        }
        if (analysis.notes.isNotEmpty()) {
            Text(
                analysis.notes.first(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentExamRow(exam: ExamRecord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(exam.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                exam.dateText + if (exam.note.isNotBlank()) " · ${exam.note}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            exam.total?.let { formatScore(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun StudentCreateDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    initialName: String = "",
    initialClass: String = "",
    initialNumber: String = "",
    title: String = "创建学生档案",
) {
    if (!visible) return
    var name by remember { mutableStateOf(initialName) }
    var className by remember { mutableStateOf(initialClass) }
    var number by remember { mutableStateOf(initialNumber) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名 *") },
                    singleLine = true,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("班级") },
                    singleLine = true,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("学号") },
                    singleLine = true,
                )
                Text(
                    "同名学生请填写班级或学号区分，系统不会自动合并同名档案。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), className.trim(), number.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
