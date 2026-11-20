package com.scoretrack.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.scoretrack.core.ColorHex
import com.scoretrack.core.Subject
import com.scoretrack.core.formatScore
import com.scoretrack.data.goal.UniversityGoal
import com.scoretrack.domain.goal.barFraction
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.ConfirmDialog
import com.scoretrack.ui.components.GoalLogoBadge
import com.scoretrack.ui.components.WarningBanner
import com.scoretrack.ui.theme.ThemePresets

/**
 * 大学目标管理：新增 / 编辑 / 删除 / 排序 / 显示开关 / 卡片颜色与透明度。
 * 关闭或删除卡片都不会影响任何成绩数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val goals by vm.goals.collectAsState()
    val exams by vm.exams.collectAsState()
    val latestTotal = exams.lastOrNull()?.total

    var editing by remember { mutableStateOf<UniversityGoal?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<UniversityGoal?>(null) }
    var colorTarget by remember { mutableStateOf<UniversityGoal?>(null) }
    var logoPickerFor by remember { mutableStateOf<UniversityGoal?>(null) }
    var opacityTarget by remember { mutableStateOf<UniversityGoal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大学目标") },
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
                text = { Text("添加目标") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "目标卡显示在主页顶部，进度 = 最近一次考试总分 ÷ 目标总分，可超过 100%。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (goals.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("还没有设置目标大学", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "添加后可以在主页随时看到达成进度与还差多少分。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(goals, key = { it.id }) { goal ->
                GoalManageRow(
                    goal = goal,
                    latestTotal = latestTotal,
                    onEdit = { editing = goal },
                    onPickLogo = { logoPickerFor = goal },
                    onClearLogo = { vm.clearGoalLogo(goal.id) },
                    onToggleVisible = { vm.setGoalVisible(goal.id, !goal.visible) },
                    onToggleAux = { vm.setGoalAuxInfo(goal, !goal.showAuxInfo) },
                    onMoveUp = { vm.moveGoal(goal.id, -1) },
                    onMoveDown = { vm.moveGoal(goal.id, 1) },
                    onColor = { colorTarget = goal },
                    onOpacity = { opacityTarget = goal },
                    onDelete = { deleting = goal },
                )
            }
        }
    }

    val rowLogoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = logoPickerFor
        logoPickerFor = null
        if (uri != null && target != null) {
            vm.importGoalLogo(uri) { path ->
                if (path != null) vm.saveGoal(target.id, target.universityName, target.majorName, target.targetScore, path)
            }
        }
    }

    LaunchedEffect(logoPickerFor) {
        if (logoPickerFor != null) {
            rowLogoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    if (showAdd || editing != null) {
        GoalEditDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            importLogo = { uri, done -> vm.importGoalLogo(uri, done) },
            onConfirm = { university, major, target, logo ->
                vm.saveGoal(editing?.id, university, major, target, logo) { ok ->
                    if (ok) {
                        showAdd = false
                        editing = null
                    }
                }
            },
        )
    }

    deleting?.let { goal ->
        ConfirmDialog(
            title = "删除目标",
            message = "确定删除「${goal.universityName}」吗？删除后主页卡片不再显示，成绩数据不受影响。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                vm.deleteGoal(goal.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    colorTarget?.let { goal ->
        var customHex by remember(goal.id) { mutableStateOf(goal.accentColorHex ?: "") }
        AlertDialog(
            onDismissRequest = { colorTarget = null },
            title = { Text("卡片颜色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("默认跟随主题色", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemePresets.all.forEach { preset ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(ThemePresets.hexToColor(preset.hex), CircleShape)
                                    .clickable {
                                        vm.setGoalAccent(goal, preset.hex)
                                        colorTarget = null
                                    },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customHex,
                        onValueChange = { customHex = it },
                        label = { Text("自定义颜色 #RRGGBB") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = ColorHex.normalize(customHex)
                        vm.setGoalAccent(goal, normalized)
                        colorTarget = null
                    },
                ) { Text("应用") }
            },
            dismissButton = {
                TextButton(onClick = { vm.setGoalAccent(goal, null); colorTarget = null }) { Text("恢复默认") }
            },
        )
    }

    opacityTarget?.let { goal ->
        var value by remember(goal.id) { mutableFloatStateOf(goal.cardOpacity) }
        AlertDialog(
            onDismissRequest = { opacityTarget = null },
            title = { Text("卡片透明度") },
            text = {
                Column {
                    Text("${(value * 100).toInt()}%")
                    Slider(value = value, onValueChange = { value = it }, valueRange = 0.2f..1f)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setGoalOpacity(goal, value); opacityTarget = null }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { opacityTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun GoalManageRow(
    goal: UniversityGoal,
    latestTotal: Double?,
    onEdit: () -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleAux: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onColor: () -> Unit,
    onOpacity: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = goal.progress(latestTotal)
    val accent = goal.accentColorHex?.let { ThemePresets.hexToColor(it) } ?: MaterialTheme.colorScheme.primary
    var menu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = goal.cardOpacity.coerceIn(0.2f, 1f)),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalLogoBadge(path = goal.logoPath, accent = accent, size = 44.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.universityName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val sub = listOfNotNull(
                        goal.majorName.takeIf { it.isNotBlank() },
                        "目标 ${formatScore(goal.targetScore)} 分",
                        if (goal.visible) "主页显示" else "已从主页隐藏",
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = goal.visible, onCheckedChange = { onToggleVisible() })
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("编辑目标") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("更换校徽") }, onClick = { menu = false; onPickLogo() })
                        if (goal.logoPath != null) {
                            DropdownMenuItem(text = { Text("移除校徽") }, onClick = { menu = false; onClearLogo() })
                        }
                        DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMoveUp() })
                        DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMoveDown() })
                        DropdownMenuItem(
                            text = { Text(if (goal.showAuxInfo) "隐藏剩余分数" else "显示剩余分数") },
                            onClick = { menu = false; onToggleAux() },
                        )
                        DropdownMenuItem(text = { Text("卡片颜色") }, onClick = { menu = false; onColor() })
                        DropdownMenuItem(text = { Text("卡片透明度") }, onClick = { menu = false; onOpacity() })
                        DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress.barFraction() },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
            Text(
                if (progress.hasData) {
                    "${progress.percentText} · 最近 ${formatScore(progress.latestTotal ?: 0.0)} 分 · ${progress.remainingText}"
                } else {
                    "还没有完整考试记录，录入后自动计算进度"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalEditDialog(
    initial: UniversityGoal?,
    importLogo: (android.net.Uri, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, String?) -> Unit,
) {
    var university by remember { mutableStateOf(initial?.universityName ?: "") }
    var major by remember { mutableStateOf(initial?.majorName ?: "") }
    var scoreText by remember {
        mutableStateOf(initial?.let { formatScore(it.targetScore) } ?: "650")
    }
    var logoPath by remember { mutableStateOf(initial?.logoPath) }
    var pickingLogo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pickingLogo = true
            importLogo(uri) { path ->
                pickingLogo = false
                if (path != null) logoPath = path
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加目标大学" else "编辑目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = university,
                    onValueChange = { university = it },
                    label = { Text("目标大学 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("目标专业（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalLogoBadge(
                        path = logoPath,
                        accent = MaterialTheme.colorScheme.primary,
                        size = 56.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("校徽（可选）", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (logoPath == null) "显示在目标卡左侧，未设置时使用学校图标" else "已选择校徽，可更换或移除",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            logoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = !pickingLogo,
                    ) {
                        if (pickingLogo) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (logoPath == null) "选择" else "更换")
                        }
                    }
                    if (logoPath != null) {
                        TextButton(onClick = { logoPath = null }) { Text("移除") }
                    }
                }
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it },
                    label = { Text("目标总分 *（满分 ${Subject.TOTAL_MAX_SCORE.toInt()}）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { WarningBanner(it, severe = true) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = scoreText.trim().toDoubleOrNull()
                    when {
                        university.isBlank() -> error = "请填写目标大学"
                        target == null -> error = "目标总分必须是数字"
                        target <= 0 -> error = "目标总分必须大于 0"
                        target > Subject.TOTAL_MAX_SCORE -> error = "目标总分不能超过 ${Subject.TOTAL_MAX_SCORE.toInt()}"
                        else -> onConfirm(university, major, target, logoPath)
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
