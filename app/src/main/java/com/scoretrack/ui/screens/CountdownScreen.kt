package com.scoretrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scoretrack.core.CountdownMath
import com.scoretrack.data.countdown.CountdownExam
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.ConfirmDialog
import com.scoretrack.ui.components.WarningBanner
import com.scoretrack.ui.components.rememberNow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 考试倒计时管理：新增 / 编辑 / 删除 / 显示开关 / 主显示切换。
 *
 * 列表分为「即将到来」与「历史考试」两组，历史考试仍可编辑，只是不再参与倒计时。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownScreen(vm: MainViewModel, onBack: () -> Unit) {
    val exams by vm.countdowns.collectAsState()
    val now = rememberNow()

    var editing by remember { mutableStateOf<CountdownExam?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CountdownExam?>(null) }

    val upcoming = exams.filter { !it.finished(now) }
    val history = exams.filter { it.finished(now) }.sortedByDescending { it.date }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("考试倒计时") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加考试") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "倒计时按设备当前时区、以自然日计算；考试当天显示为「今天」，不会出现负数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (exams.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("还没有添加考试", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "添加考试，为重要的日子做好准备。可添加多场考试，并指定一场作为主页主显示。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (upcoming.isNotEmpty()) {
                item { SectionTitle("即将到来（${upcoming.size}）") }
                items(upcoming, key = { it.id }) { exam ->
                    CountdownManageRow(
                        exam = exam,
                        now = now,
                        onEdit = { editing = exam },
                        onDelete = { deleting = exam },
                        onPrimary = { vm.setCountdownPrimary(exam.id) },
                        onVisible = { vm.setCountdownVisible(exam.id, it) },
                    )
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("历史考试（${history.size}）")
                }
                items(history, key = { it.id }) { exam ->
                    CountdownManageRow(
                        exam = exam,
                        now = now,
                        onEdit = { editing = exam },
                        onDelete = { deleting = exam },
                        onPrimary = { vm.setCountdownPrimary(exam.id) },
                        onVisible = { vm.setCountdownVisible(exam.id, it) },
                    )
                }
            }
        }
    }

    if (showAdd || editing != null) {
        CountdownEditDialog(
            initial = editing,
            onDismiss = {
                showAdd = false
                editing = null
            },
            onConfirm = { name, date, timeMinutes, note, visible, primary ->
                vm.saveCountdown(
                    id = editing?.id,
                    name = name,
                    date = date,
                    timeMinutes = timeMinutes,
                    note = note,
                    visible = visible,
                    primary = primary,
                )
                showAdd = false
                editing = null
            },
        )
    }

    deleting?.let { target ->
        ConfirmDialog(
            title = "删除考试？",
            message = "将删除「${target.name}」及其倒计时设置，不影响任何成绩记录。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                vm.deleteCountdown(target.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownManageRow(
    exam: CountdownExam,
    now: java.time.ZonedDateTime,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrimary: () -> Unit,
    onVisible: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val countdown = exam.countdown(now)
    val finished = exam.finished(now)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Event,
                    contentDescription = null,
                    tint = if (exam.primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    exam.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (exam.primary) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = "主显示",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Switch(checked = exam.visible, onCheckedChange = onVisible)
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (exam.primary) "已设为主显示" else "设为主页主显示") },
                        enabled = !exam.primary && !finished,
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                        onClick = {
                            menu = false
                            onPrimary()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    exam.dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (exam.timeMinutes != null) {
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        exam.timeOrEmpty,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                when {
                    finished -> "已结束 · 距考试 ${countdown.daysPast} 天"
                    countdown.isToday && exam.timeMinutes != null -> "今天 · ${countdown.statusText}"
                    countdown.isToday -> "今天考试"
                    else -> "还剩 ${countdown.days} 天"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )

            if (!exam.visible) {
                Text(
                    "已关闭主页显示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exam.note.isNotBlank()) {
                Text(
                    exam.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 新增 / 编辑考试对话框：名称、日期（系统日期选择器）、时间（可选，系统时间选择器）、备注、
 * 显示开关、主显示开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownEditDialog(
    initial: CountdownExam?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, date: LocalDate, timeMinutes: Int?, note: String, visible: Boolean, primary: Boolean) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var date by remember(initial) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var useTime by remember(initial) { mutableStateOf(initial?.timeMinutes != null) }
    var hour by remember(initial) { mutableStateOf(CountdownMath.hourOf(initial?.timeMinutes ?: (8 * 60))) }
    var minute by remember(initial) { mutableStateOf(CountdownMath.minuteOf(initial?.timeMinutes ?: 0)) }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var visible by remember(initial) { mutableStateOf(initial?.visible ?: true) }
    var primary by remember(initial) { mutableStateOf(initial?.primary ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState, title = { Text("选择考试日期") })
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择考试时间") },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    useTime = true
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加考试" else "编辑考试") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("考试名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = "${date.year}-${"%02d".format(date.monthValue)}-${"%02d".format(date.dayOfMonth)}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("考试日期 *") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "选择日期")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = useTime,
                        onCheckedChange = {
                            useTime = it
                            if (it) showTimePicker = true
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("设置具体时间（可选）", style = MaterialTheme.typography.bodyMedium)
                }
                if (useTime) {
                    OutlinedTextField(
                        value = "%02d:%02d".format(hour, minute),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("考试时间") },
                        trailingIcon = {
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Outlined.Schedule, contentDescription = "选择时间")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = visible, onCheckedChange = { visible = it })
                    Spacer(Modifier.width(8.dp))
                    Text("在主页显示", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = primary, onCheckedChange = { primary = it })
                    Spacer(Modifier.width(8.dp))
                    Text("设为主页主显示考试", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { WarningBanner(text = it, severe = true) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "请填写考试名称"
                    return@TextButton
                }
                onConfirm(
                    name.trim(),
                    date,
                    if (useTime) CountdownMath.toMinutes(hour, minute) else null,
                    note.trim(),
                    visible,
                    primary,
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
