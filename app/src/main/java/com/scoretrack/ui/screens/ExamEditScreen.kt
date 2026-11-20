package com.scoretrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.appContainer
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.WarningBanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditScreen(
    vm: MainViewModel,
    examId: String?,
    onDone: () -> Unit,
) {
    val container = appContainer()
    val students by vm.students.collectAsState()
    val active by vm.activeStudent.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(examId == null) }
    var exam by remember { mutableStateOf<ExamRecord?>(null) }

    var studentId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var values by remember { mutableStateOf<Map<Subject, String>>(emptyMap()) }
    var raws by remember { mutableStateOf<Map<Subject, String>>(emptyMap()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStudentMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(examId, active?.id) {
        if (examId != null) {
            val record = container.examRepository.getExam(examId)
            exam = record
            if (record != null) {
                studentId = record.studentId
                name = record.name
                date = record.date
                note = record.note
                values = record.scores.mapValues { (_, v) ->
                    v?.let { if (it == Math.floor(it)) it.toInt().toString() else it.toString() } ?: ""
                }
                raws = record.rawScores.mapValues { (_, v) ->
                    v?.let { if (it == Math.floor(it)) it.toInt().toString() else it.toString() } ?: ""
                }
            }
            loaded = true
        } else if (studentId.isBlank()) {
            studentId = active?.id ?: ""
        }
    }

    if (!loaded) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text("正在读取考试记录…") }
        return
    }

    val chosenStudent = students.firstOrNull { it.id == studentId }
    val selection by vm.selection.collectAsState()
    val selectedNow = selection.selectedSubjects.toSet()
    val baseSubjects = Subject.displayOrder()
    val extraSubjects = Subject.allSubjects().filter { it !in baseSubjects }
    val allEntrySubjects = baseSubjects + extraSubjects

    val parsedScores: Map<Subject, Double?> = allEntrySubjects.associateWith { subject ->
        values[subject]?.trim()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    }
    val parsedRaws: Map<Subject, Double?> = allEntrySubjects.associateWith { subject ->
        raws[subject]?.trim()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    }
    val invalid = allEntrySubjects.filter { subject ->
        val v = parsedScores[subject]
        v != null && (v < 0 || v > subject.maxScore)
    }
    // 原有 750 分体系：仍只统计语文/数学/英语/物理/化学/地理六科，选科总分另行统计
    val baseParsed = baseSubjects.associateWith { parsedScores[it] }
    val total = if (baseParsed.values.all { it != null }) baseParsed.values.sumOf { it ?: 0.0 } else null
    val completeCount = baseParsed.values.count { it != null }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = if (examId == null) "手动录入考试" else "编辑考试记录") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showStudentMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            chosenStudent?.let { "${it.name}${if (it.className.isNotBlank()) " · ${it.className}" else ""}" }
                                ?: "选择学生（必选）"
                        )
                    }
                    DropdownMenu(expanded = showStudentMenu, onDismissRequest = { showStudentMenu = false }) {
                        students.forEach { student ->
                            DropdownMenuItem(
                                text = { Text(student.name + if (student.className.isNotBlank()) " · ${student.className}" else "") },
                                onClick = { studentId = student.id; showStudentMenu = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("考试名称 *") },
                    placeholder = { Text("例如：高二上学期期中考试") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("高二上学期期中考试", "2026年11月月考", "成都七校联考").forEach { suggestion ->
                        TextButton(onClick = { name = suggestion }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                            Text(suggestion, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("考试日期：${date.format(DATE_FORMAT)}")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionCard(
            title = "六科成绩",
            subtitle = "满分：语文/数学/英语 150，物理/化学/地理 100；化学、地理填写赋分成绩",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Subject.displayOrder().chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { subject ->
                            val v = values[subject].orEmpty()
                            val parsed = v.toDoubleOrNull()
                            val isBad = invalid.contains(subject)
                            val isSelected = subject in selectedNow
                            OutlinedTextField(
                                value = v,
                                onValueChange = { raw ->
                                    values = values + (subject to raw.filter { it.isDigit() || it == '.' }.take(6))
                                },
                                label = {
                                    Text(subject.cnName + (if (subject.isScaled) "（赋分）" else "") + (if (isSelected) " · 选考" else ""))
                                },
                                singleLine = true,
                                isError = isBad,
                                supportingText = {
                                    Text(
                                        if (isBad) "超出满分 ${subject.maxScore.toInt()}"
                                        else "满分 ${subject.maxScore.toInt()}" + (if (isSelected) " · 已选考" else ""),
                                    )
                                },
                                colors = if (isSelected) {
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                                    )
                                } else OutlinedTextFieldDefaults.colors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Subject.displayOrder().filter { it.isScaled }.forEach { subject ->
                    OutlinedTextField(
                        value = raws[subject].orEmpty(),
                        onValueChange = { raw -> raws = raws + (subject to raw.filter { it.isDigit() || it == '.' }.take(6)) },
                        label = { Text("${subject.cnName}原始分（可选，仅记录）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "总分：" + (total?.let { if (it == Math.floor(it)) it.toInt().toString() else String.format("%.1f", it) } ?: "六科未填完，暂不计算") +
                        "（已填 $completeCount/6 科，缺失科目不会按 0 分计算）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SectionCard(
            title = "选考科目（3+1+2）",
            subtitle = if (selection.isComplete) {
                "当前选科：${selection.comboLabel}；只有选考科目计入选科统计，未选科目留空即可"
            } else {
                "尚未完成选科。这里可录入生物/政治/历史成绩，未选科目留空即可"
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                extraSubjects.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { subject ->
                            val v = values[subject].orEmpty()
                            val isBad = invalid.contains(subject)
                            val isSelected = subject in selectedNow
                            OutlinedTextField(
                                value = v,
                                onValueChange = { raw ->
                                    values = values + (subject to raw.filter { it.isDigit() || it == '.' }.take(6))
                                },
                                label = {
                                    Text(subject.cnName + (if (subject.isScaled) "（赋分）" else "") + (if (isSelected) " · 选考" else ""))
                                },
                                singleLine = true,
                                isError = isBad,
                                supportingText = {
                                    Text(
                                        if (isBad) "超出满分 ${subject.maxScore.toInt()}"
                                        else "满分 ${subject.maxScore.toInt()}" + (if (isSelected) " · 已选考" else ""),
                                    )
                                },
                                colors = if (isSelected) {
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                                    )
                                } else OutlinedTextFieldDefaults.colors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text(
                    "说明：总分仍按原有六科（语数英物化地，满分 750）计算；选考总分在「选科管理」中独立统计。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (invalid.isNotEmpty()) {
            WarningBanner("以下科目超出满分范围：" + invalid.joinToString("、") { it.cnName } + "，请修改后再保存。", severe = true)
        }
        if (examId == null && (name.isBlank() || studentId.isBlank())) {
            WarningBanner("考试名称与学生为必填项。")
        }
        error?.let { WarningBanner(it, severe = true) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = {
                    if (studentId.isBlank()) {
                        error = "请选择学生"
                        return@Button
                    }
                    if (name.isBlank()) {
                        error = "请填写考试名称"
                        return@Button
                    }
                    if (invalid.isNotEmpty()) {
                        error = "有科目成绩超出满分范围，请修正"
                        return@Button
                    }
                    scope.launch {
                        vm.saveExam(
                            id = examId,
                            studentId = studentId,
                            name = name,
                            date = date,
                            note = note,
                            scores = parsedScores,
                            rawScores = parsedRaws.filterKeys { it.isScaled },
                            confidences = emptyMap(),
                            sourceDocumentId = exam?.sourceDocumentId,
                        ) { onDone() }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    }
}
