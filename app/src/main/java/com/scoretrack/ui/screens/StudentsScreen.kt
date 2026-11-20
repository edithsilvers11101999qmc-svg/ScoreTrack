package com.scoretrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scoretrack.core.Student
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.ConfirmDialog
import com.scoretrack.ui.components.EmptyState
import com.scoretrack.ui.components.WarningBanner

@Composable
fun StudentsScreen(vm: MainViewModel) {
    val students by vm.students.collectAsState()
    val active by vm.activeStudent.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Student?>(null) }
    var deleting by remember { mutableStateOf<Student?>(null) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("学生管理", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "成绩按学生分开保存；同名学生请用班级或学号区分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.FilledTonalButton(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("新建")
                }
            }
        }
        duplicateWarning?.let { item { WarningBanner(it) } }
        if (students.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Groups,
                    title = "还没有学生档案",
                    message = "先创建学生，再录入或导入成绩。",
                    actionLabel = "创建学生",
                    onAction = { showAdd = true },
                )
            }
        }
        items(students, key = { it.id }) { student ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = student.id == active?.id,
                        onClick = { vm.selectStudent(student.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(student.name, style = MaterialTheme.typography.titleMedium)
                        val detail = listOf(
                            student.className.ifBlank { "未填写班级" },
                            student.studentNumber.ifBlank { "未填写学号" },
                        ).joinToString(" · ")
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (student.id == active?.id) {
                            Text("当前学生", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { editing = student }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { deleting = student }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除")
                    }
                }
            }
        }
        item { Spacer(Modifier.size(8.dp)) }
    }

    StudentCreateDialog(
        visible = showAdd,
        onDismiss = { showAdd = false },
        onConfirm = { name, className, number ->
            val sameName = students.filter { it.name == name }
            if (sameName.isNotEmpty() && sameName.none { it.className == className && it.studentNumber == number }) {
                duplicateWarning = "已存在同名学生「$name」。如果不是同一人，请确认班级/学号不同；系统不会合并同名档案。"
            }
            vm.createStudent(name, className, number)
            showAdd = false
        },
    )

    editing?.let { student ->
        var name by remember(student.id) { mutableStateOf(student.name) }
        var className by remember(student.id) { mutableStateOf(student.className) }
        var number by remember(student.id) { mutableStateOf(student.studentNumber) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑学生") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("姓名") },
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
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.updateStudent(student.copy(name = name.trim(), className = className.trim(), studentNumber = number.trim()))
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("取消") }
            },
        )
    }

    deleting?.let { student ->
        ConfirmDialog(
            title = "删除学生档案",
            message = "删除「${student.name}」会同时删除该学生的所有考试记录与成绩，且不可恢复。建议先导出备份。",
            confirmLabel = "确认删除",
            destructive = true,
            onConfirm = { vm.deleteStudent(student.id) },
            onDismiss = { deleting = null },
        )
    }
}
