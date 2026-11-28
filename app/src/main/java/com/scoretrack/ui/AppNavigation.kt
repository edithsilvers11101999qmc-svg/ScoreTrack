package com.scoretrack.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scoretrack.ui.imports.ImportViewModel
import com.scoretrack.ui.screens.AboutScreen
import com.scoretrack.ui.screens.AiAnalysisScreen
import com.scoretrack.ui.screens.AiSettingsScreen
import com.scoretrack.ui.screens.AnalysisScreen
import com.scoretrack.ui.screens.BackupScreen
import com.scoretrack.ui.screens.ExamEditScreen
import com.scoretrack.ui.screens.ExamsScreen
import com.scoretrack.ui.screens.GoalsScreen
import com.scoretrack.ui.screens.HomeScreen
import com.scoretrack.ui.screens.ImportScreen
import com.scoretrack.ui.screens.PersonalizationScreen
import com.scoretrack.ui.screens.ReportDetailScreen
import com.scoretrack.ui.screens.ReportsScreen
import com.scoretrack.ui.screens.SettingsScreen
import com.scoretrack.ui.screens.CountdownScreen
import com.scoretrack.ui.screens.SubjectSelectionScreen
import com.scoretrack.ui.screens.StudentsScreen
import com.scoretrack.ui.screens.WallpaperEditorScreen
import com.scoretrack.ui.theme.ScoreTrackTheme
import java.io.File
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val EXAMS = "exams"
    const val ANALYSIS = "analysis"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val EXAM_EDIT = "exam_edit"
    const val AI = "ai"
    const val REPORTS = "reports"
    const val REPORT_DETAIL = "report"
    const val STUDENTS = "students"
    const val AI_SETTINGS = "ai_settings"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val PERSONALIZATION = "personalization"
    const val WALLPAPER_EDITOR = "wallpaper_editor"
    const val GOALS = "goals"
    const val SUBJECT_SELECTION = "subject_selection"
    const val COUNTDOWN = "countdown"

    fun examEdit(examId: String?): String = if (examId == null) "$EXAM_EDIT?examId=" else "$EXAM_EDIT?examId=$examId"
    fun reportDetail(id: String): String = "$REPORT_DETAIL/$id"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun ScoreTrackRoot() {
    val vm: MainViewModel = appViewModel { MainViewModel(it) }
    val theme by vm.themeState.collectAsState()
    ScoreTrackTheme(themeMode = theme.mode, colorHex = theme.effectiveHex) {
        AppNavigation(vm)
    }
}

@Composable
private fun AppNavigation(vm: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val container = appContainer()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val importVm: ImportViewModel = appViewModel { ImportViewModel(it) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val upgradeNotice by vm.upgradeNotice.collectAsState()
    upgradeNotice?.let { raw ->
        UpgradeResultDialog(raw = raw, onDismiss = { vm.consumeUpgradeNotice() })
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomItems = listOf(
        BottomItem(Routes.HOME, "首页", Icons.Outlined.Home),
        BottomItem(Routes.EXAMS, "成绩", Icons.Outlined.EditNote),
        BottomItem(Routes.ANALYSIS, "分析", Icons.Outlined.AutoGraph),
        BottomItem(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
    )
    val showBottomBar = bottomItems.any { it.route == currentRoute }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.EXAMS) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.examEdit(null)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("录入成绩") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        vm = vm,
                        onOpenImport = {
                            importVm.reset()
                            navController.navigate(Routes.IMPORT)
                        },
                        onOpenAnalysis = { navController.navigate(Routes.ANALYSIS) },
                        onOpenAi = { navController.navigate(Routes.AI) },
                        onOpenExam = { id -> navController.navigate(Routes.examEdit(id)) },
                        onOpenStudents = { navController.navigate(Routes.STUDENTS) },
                        onOpenGoals = { navController.navigate(Routes.GOALS) },
                        onOpenSelection = { navController.navigate(Routes.SUBJECT_SELECTION) },
                        onOpenCountdown = { navController.navigate(Routes.COUNTDOWN) },
                    )
                }
                composable(Routes.EXAMS) {
                    ExamsScreen(
                        vm = vm,
                        onAddExam = { navController.navigate(Routes.examEdit(null)) },
                        onEditExam = { id -> navController.navigate(Routes.examEdit(id)) },
                        onOpenDocument = { documentId ->
                            scope.launch {
                                val doc = container.documentRepository.find(documentId)
                                if (doc == null) {
                                    vm.showMessage("找不到原始成绩单记录，可能已被删除")
                                } else {
                                    val first = doc.localPath.split("|").firstOrNull().orEmpty()
                                    val file = File(first)
                                    if (!file.exists()) {
                                        vm.showMessage("原始文件已删除，但成绩记录仍然保留")
                                    } else {
                                        runCatching {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file,
                                            )
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, doc.mimeType.ifBlank { "image/*" })
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        }.onFailure { vm.showMessage("无法打开文件：${it.message}") }
                                    }
                                }
                            }
                        },
                    )
                }
                composable(Routes.ANALYSIS) {
                    AnalysisScreen(vm = vm, onOpenAi = { navController.navigate(Routes.AI) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        vm = vm,
                        onOpenStudents = { navController.navigate(Routes.STUDENTS) },
                        onOpenGoals = { navController.navigate(Routes.GOALS) },
                        onOpenPersonalization = { navController.navigate(Routes.PERSONALIZATION) },
                        onOpenSelection = { navController.navigate(Routes.SUBJECT_SELECTION) },
                        onOpenCountdown = { navController.navigate(Routes.COUNTDOWN) },
                        onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                        onOpenReports = { navController.navigate(Routes.REPORTS) },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        onExportCsv = { navController.navigate(Routes.BACKUP) },
                        onExportPdf = { navController.navigate(Routes.BACKUP) },
                    )
                }
                composable(Routes.IMPORT) {
                    ImportScreen(
                        mainVm = vm,
                        importVm = importVm,
                        onBack = { navController.popBackStack() },
                        onSaved = { examId ->
                            vm.showMessage("成绩已保存")
                            navController.navigate(Routes.examEdit(examId)) {
                                popUpTo(Routes.HOME)
                            }
                        },
                    )
                }
                composable(
                    route = "${Routes.EXAM_EDIT}?examId={examId}",
                    arguments = listOf(navArgument("examId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    }),
                ) { entry ->
                    val examId = entry.arguments?.getString("examId")?.takeIf { it.isNotBlank() }
                    ExamEditScreen(vm = vm, examId = examId, onDone = { navController.popBackStack() })
                }
                composable(Routes.AI) {
                    AiAnalysisScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                        onOpenReports = { navController.navigate(Routes.REPORTS) },
                    )
                }
                composable(Routes.REPORTS) {
                    ReportsScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onOpenReport = { id -> navController.navigate(Routes.reportDetail(id)) },
                        onNewReport = { navController.navigate(Routes.AI) },
                    )
                }
                composable(
                    route = "${Routes.REPORT_DETAIL}/{reportId}",
                    arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
                ) { entry ->
                    ReportDetailScreen(
                        reportId = entry.arguments?.getString("reportId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.STUDENTS) {
                    StudentsScreen(vm = vm)
                }
                composable(Routes.AI_SETTINGS) {
                    AiSettingsScreen(vm = vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.BACKUP) {
                    BackupScreen(vm = vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.PERSONALIZATION) {
                    PersonalizationScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onEditWallpaper = { navController.navigate(Routes.WALLPAPER_EDITOR) },
                    )
                }
                composable(Routes.WALLPAPER_EDITOR) {
                    WallpaperEditorScreen(vm = vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.SUBJECT_SELECTION) {
                    SubjectSelectionScreen(vm = vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.COUNTDOWN) {
                    CountdownScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.GOALS) {
                    GoalsScreen(vm = vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Suppress("unused")
private fun unused(nav: NavHostController, uri: Uri) {
    nav.navigate(uri.toString())
}

/** 升级结果提示：迁移完成后展示一次，明确告知旧数据是否完整保留与备份位置。 */
@Composable
private fun UpgradeResultDialog(raw: String, onDismiss: () -> Unit) {
    val info = remember(raw) {
        runCatching {
            val o = org.json.JSONObject(raw)
            val before = o.optJSONObject("before")
            val after = o.optJSONObject("after")
            UpgradeInfo(
                ok = o.optBoolean("ok", false),
                from = o.optInt("fromVersion", 1),
                to = o.optInt("toVersion", 2),
                studentsBefore = before?.optInt("students", 0) ?: 0,
                studentsAfter = after?.optInt("students", 0) ?: 0,
                examsBefore = before?.optInt("exams", 0) ?: 0,
                examsAfter = after?.optInt("exams", 0) ?: 0,
                reportsBefore = before?.optInt("ai_reports", 0) ?: 0,
                reportsAfter = after?.optInt("ai_reports", 0) ?: 0,
                backupDir = o.optString("backupDir", ""),
            )
        }.getOrNull()
    }
    if (info == null) {
        onDismiss()
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (info.ok) "升级完成，数据已保留" else "升级完成，请检查数据") },
        text = {
            Text(
                buildString {
                    appendLine("数据库版本：v${info.from} → v${info.to}")
                    appendLine("学生：${info.studentsBefore} → ${info.studentsAfter}")
                    appendLine("考试记录：${info.examsBefore} → ${info.examsAfter}")
                    appendLine("AI 报告：${info.reportsBefore} → ${info.reportsAfter}")
                    if (info.backupDir.isNotBlank()) {
                        appendLine()
                        appendLine("升级前已自动备份：")
                        appendLine(info.backupDir)
                    }
                    if (!info.ok) {
                        appendLine()
                        appendLine("检测到数据量减少，请从上面的备份目录恢复，或通过「数据备份与恢复」导入备份文件。")
                    }
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("我知道了") } },
    )
}

private data class UpgradeInfo(
    val ok: Boolean,
    val from: Int,
    val to: Int,
    val studentsBefore: Int,
    val studentsAfter: Int,
    val examsBefore: Int,
    val examsAfter: Int,
    val reportsBefore: Int,
    val reportsAfter: Int,
    val backupDir: String,
)
