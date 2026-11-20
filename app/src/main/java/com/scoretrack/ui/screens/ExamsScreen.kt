package com.scoretrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import com.scoretrack.core.formatScore
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.ConfirmDialog
import com.scoretrack.ui.components.EmptyState

@Composable
fun ExamsScreen(
    vm: MainViewModel,
    onAddExam: () -> Unit,
    onEditExam: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    val exams by vm.exams.collectAsState()
    val active by vm.activeStudent.collectAsState()
    var pendingDelete by remember { mutableStateOf<ExamRecord?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("历次考试", style = MaterialTheme.typography.headlineSmall)
                Text(
                    active?.let { "${it.name} · 共 ${exams.size} 次考试记录" } ?: "尚未选择学生",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (exams.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = "还没有考试记录",
                    message = "可以拍照/导入成绩单自动识别，也可以手动录入。同名考试允许存在多条记录。",
                    actionLabel = "手动录入一次考试",
                    onAction = onAddExam,
                )
            }
        }
        items(exams.sortedWith(compareByDescending<ExamRecord> { it.date }.thenByDescending { it.id }), key = { it.id }) { exam ->
            ExamCard(
                exam = exam,
                onEdit = { onEditExam(exam.id) },
                onDelete = { pendingDelete = exam },
                onOpenDocument = { exam.sourceDocumentId?.let(onOpenDocument) },
            )
        }
    }

    pendingDelete?.let { exam ->
        ConfirmDialog(
            title = "删除考试记录",
            message = "确定删除「${exam.name}」（${exam.dateText}）吗？该考试的六科成绩会一并删除，原始成绩单文件不会被删除。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = { vm.deleteExam(exam.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ExamCard(
    exam: ExamRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(exam.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        exam.dateText + if (exam.note.isNotBlank()) " · ${exam.note}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        exam.total?.let { formatScore(it) } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("总分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                        if (exam.sourceDocumentId != null) {
                            DropdownMenuItem(
                                text = { Text("查看原始成绩单") },
                                onClick = { menu = false; onOpenDocument() },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Subject.displayOrder().forEach { subject ->
                    val value = exam.scores[subject]
                    Column(
                        Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(subject.cnName, style = MaterialTheme.typography.labelSmall)
                        Text(
                            value?.let { formatScore(it) } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (!exam.isComplete) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "缺失：" + exam.missingSubjects.joinToString("、") { it.cnName } + "（缺失成绩不计 0 分，总分暂不计算）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
