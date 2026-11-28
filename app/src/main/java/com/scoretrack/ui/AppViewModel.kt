package com.scoretrack.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scoretrack.AppContainer
import com.scoretrack.ScoreTrackApp
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Student
import com.scoretrack.core.Subject
import com.scoretrack.domain.selection.SelectionRules
import com.scoretrack.domain.selection.SubjectSelection
import com.scoretrack.data.db.AiReportEntity
import android.net.Uri
import com.scoretrack.data.countdown.CountdownExam
import com.scoretrack.data.goal.DashboardSection
import com.scoretrack.data.goal.UniversityGoal
import com.scoretrack.data.settings.SettingsSnapshot
import com.scoretrack.data.theme.ThemeState
import com.scoretrack.data.wallpaper.WallpaperState
import com.scoretrack.data.upgrade.UpgradeManager
import com.scoretrack.data.repo.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import com.scoretrack.domain.analytics.AnalysisResult
import com.scoretrack.domain.analytics.AnalyticsEngine
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as ScoreTrackApp).container }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val studentRepo = container.studentRepository
    private val examRepo = container.examRepository

    val settings: StateFlow<SettingsSnapshot> = container.settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    val students: StateFlow<List<Student>> = studentRepo.observeStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeStudent: StateFlow<Student?> = studentRepo.observeActiveStudent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val exams: StateFlow<List<ExamRecord>> = activeStudent
        .flatMapLatest { student ->
            if (student == null) flowOf(emptyList()) else examRepo.observeExams(student.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analysis: StateFlow<AnalysisResult> = exams
        .map { AnalyticsEngine.analyze(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsEngine.analyze(emptyList()))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val reports: StateFlow<List<AiReportEntity>> = activeStudent
        .flatMapLatest { student ->
            if (student == null) flowOf(emptyList()) else container.aiReportRepository.observeReports(student.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun showMessage(text: String) { _message.value = text }

    fun selectStudent(id: String) = viewModelScope.launch { studentRepo.activate(id) }

    fun createStudent(name: String, className: String, studentNumber: String, onDone: (Student?) -> Unit = {}) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _message.value = "请填写学生姓名"
                onDone(null)
                return@launch
            }
            val student = studentRepo.createOrGet(name, className, studentNumber)
            studentRepo.activate(student.id)
            onDone(student)
        }
    }

    fun updateStudent(student: Student) = viewModelScope.launch {
        if (student.name.isBlank()) {
            _message.value = "学生姓名不能为空"
            return@launch
        }
        studentRepo.update(student)
        _message.value = "学生信息已更新"
    }

    fun deleteStudent(id: String) = viewModelScope.launch {
        studentRepo.delete(id)
        _message.value = "学生档案已删除"
    }

    fun saveExam(
        id: String?,
        studentId: String,
        name: String,
        date: LocalDate,
        note: String,
        scores: Map<Subject, Double?>,
        rawScores: Map<Subject, Double?>,
        confidences: Map<Subject, Float>,
        sourceDocumentId: String?,
        onDone: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            val examId = examRepo.save(
                id = id,
                studentId = studentId,
                name = name,
                date = date,
                note = note,
                scores = scores,
                rawScores = rawScores,
                confidences = confidences,
                sourceDocumentId = sourceDocumentId,
            )
            _message.value = "成绩已保存"
            onDone(examId)
        }
    }

    fun deleteExam(id: String) = viewModelScope.launch {
        examRepo.delete(id)
        _message.value = "考试记录已删除"
    }

    // ---------------------------------------------------------------
    // V3：主题 / 壁纸 / 大学目标 / 主页模块
    // ---------------------------------------------------------------

    val themeState: StateFlow<ThemeState> = container.themeRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeState())

    val wallpaper: StateFlow<WallpaperState> = container.wallpaperRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WallpaperState())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val goals: StateFlow<List<UniversityGoal>> = activeStudent
        .flatMapLatest { student ->
            if (student == null) flowOf(emptyList()) else container.goalRepository.observe(student.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dashboard: StateFlow<List<DashboardSection>> = container.dashboardRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardSection.DEFAULTS)

    /** 自定义考试倒计时（全局，不区分学生）。 */
    val countdowns: StateFlow<List<CountdownExam>> = container.countdownRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前学生的 3+1+2 选科（切换学生自动切换）。 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selection: StateFlow<SubjectSelection> = activeStudent
        .flatMapLatest { student ->
            if (student == null) flowOf(SubjectSelection("")) else container.subjectSelectionRepository.observe(student.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectSelection(""))

    /** 升级提示：仅在新版本首次启动后提示一次，内容来自 UpgradeManager 写入的校验结果。 */
    private val _upgradeNotice = MutableStateFlow<String?>(null)
    val upgradeNotice: StateFlow<String?> = _upgradeNotice.asStateFlow()

    init {
        viewModelScope.launch {
            val pending = container.settingsRepository.getRaw(UpgradeManager.SETTING_PENDING_NOTICE)
            if (pending == "1") {
                _upgradeNotice.value = container.settingsRepository.getRaw(UpgradeManager.SETTING_LAST_RESULT)
                container.settingsRepository.putRaw(UpgradeManager.SETTING_PENDING_NOTICE, "0")
            }
        }
    }

    fun consumeUpgradeNotice() { _upgradeNotice.value = null }

    fun setThemeMode(mode: com.scoretrack.data.settings.ThemeMode) = viewModelScope.launch {
        container.themeRepository.setMode(mode)
        // 同步写入旧键，保证 V2 数据与备份恢复逻辑继续可读
        container.settingsRepository.setThemeMode(mode)
    }

    fun setThemePreset(presetId: String, hex: String) = viewModelScope.launch {
        container.themeRepository.setPreset(presetId, hex)
    }

    fun setCustomTheme(hex: String) = viewModelScope.launch {
        container.themeRepository.setCustom(hex)
        _message.value = "自定义主题已应用"
    }

    fun resetTheme() = viewModelScope.launch {
        container.themeRepository.resetToDefault()
        _message.value = "已恢复默认主题"
    }

    fun importWallpaper(uri: Uri, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val result = container.wallpaperRepository.importImage(uri)
        if (result.isSuccess) {
            _message.value = "壁纸已导入，可调整裁剪与透明度"
            onDone(true)
        } else {
            _message.value = "导入壁纸失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            onDone(false)
        }
    }

    fun clearWallpaper() = viewModelScope.launch {
        container.wallpaperRepository.clear()
        _message.value = "已恢复默认背景"
    }

    fun setWallpaperEnabled(enabled: Boolean) = viewModelScope.launch {
        container.wallpaperRepository.setEnabled(enabled)
    }

    fun saveWallpaperTransform(scale: Float, offsetX: Float, offsetY: Float, opacity: Float) =
        viewModelScope.launch {
            container.wallpaperRepository.saveTransform(scale, offsetX, offsetY, opacity)
            _message.value = "壁纸设置已保存"
        }

    fun saveGoal(
        id: String?,
        universityName: String,
        majorName: String,
        targetScore: Double,
        logoPath: String? = null,
        onDone: (Boolean) -> Unit = {},
    ) = viewModelScope.launch {
        val student = activeStudent.value
        if (student == null) {
            _message.value = "请先创建并选择学生"
            onDone(false)
            return@launch
        }
        if (universityName.isBlank()) {
            _message.value = "请填写大学名称"
            onDone(false)
            return@launch
        }
        if (targetScore <= 0) {
            _message.value = "目标总分必须大于 0"
            onDone(false)
            return@launch
        }
        val existing = goals.value.firstOrNull { it.id == id }
        // 校徽：传 null 表示保持原样；传空串表示清除
        val resolvedLogo = when {
            logoPath == null -> existing?.logoPath
            else -> logoPath.ifBlank { null }
        }
        if (existing?.logoPath != null && existing.logoPath != resolvedLogo) {
            container.goalLogoStore.delete(existing.logoPath)
        }
        container.goalRepository.upsert(
            UniversityGoal(
                id = id ?: Ids.newId(),
                studentId = student.id,
                universityName = universityName.trim(),
                majorName = majorName.trim(),
                targetScore = targetScore,
                visible = existing?.visible ?: true,
                sortOrder = existing?.sortOrder ?: 0,
                accentColorHex = existing?.accentColorHex,
                cardOpacity = existing?.cardOpacity ?: 1f,
                showAuxInfo = existing?.showAuxInfo ?: true,
                logoPath = resolvedLogo,
            ),
        )
        _message.value = "目标已保存"
        onDone(true)
    }

    fun deleteGoal(cardId: String) = viewModelScope.launch {
        val logo = container.goalRepository.find(cardId)?.logoPath
        container.goalRepository.delete(cardId)
        container.goalLogoStore.delete(logo)
        _message.value = "目标已删除"
    }

    /** 从相册选好图片后立即复制到应用私有目录，返回可保存的路径。 */
    fun importGoalLogo(uri: android.net.Uri, onDone: (String?) -> Unit) = viewModelScope.launch {
        val path = container.goalLogoStore.import(uri)
        if (path == null) _message.value = "校徽图片读取失败，请换一张试试" else onDone(path)
    }

    /** 单独清除某张卡片的校徽（管理页「移除校徽」）。 */
    fun clearGoalLogo(cardId: String) = viewModelScope.launch {
        val logo = container.goalRepository.find(cardId)?.logoPath
        container.goalRepository.setLogo(cardId, null)
        container.goalLogoStore.delete(logo)
        _message.value = "已移除校徽"
    }

    fun setGoalVisible(cardId: String, visible: Boolean) = viewModelScope.launch {
        container.goalRepository.setVisible(cardId, visible)
    }

    fun moveGoal(cardId: String, delta: Int) = viewModelScope.launch {
        val student = activeStudent.value ?: return@launch
        container.goalRepository.move(student.id, cardId, delta)
    }

    fun setGoalAuxInfo(goal: UniversityGoal, show: Boolean) = viewModelScope.launch {
        container.goalRepository.upsert(goal.copy(showAuxInfo = show))
    }

    fun setGoalAccent(goal: UniversityGoal, hex: String?) = viewModelScope.launch {
        container.goalRepository.upsert(goal.copy(accentColorHex = hex))
    }

    fun setGoalOpacity(goal: UniversityGoal, opacity: Float) = viewModelScope.launch {
        container.goalRepository.upsert(goal.copy(cardOpacity = opacity))
    }

    fun setDashboardEnabled(cardType: String, enabled: Boolean) = viewModelScope.launch {
        container.dashboardRepository.setEnabled(cardType, enabled)
    }

    fun moveDashboard(cardType: String, delta: Int) = viewModelScope.launch {
        container.dashboardRepository.move(cardType, delta)
    }

    /** 保存选科；非法组合不落库并提示具体原因。 */
    fun saveSelection(first: Subject?, second: List<Subject>, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            val student = activeStudent.value
            if (student == null) {
                showMessage("请先创建并选择学生档案")
                onDone(false)
                return@launch
            }
            val result = container.subjectSelectionRepository.save(student.id, first, second)
            if (result.ok) {
                val combo = (listOfNotNull(first) + Subject.secondChoiceOptions().filter { it in second })
                    .joinToString("+") { it.cnName }
                showMessage("选科已保存：$combo")
                onDone(true)
            } else {
                showMessage(result.message ?: "选科组合不合法")
                onDone(false)
            }
        }

    /** 清空选科（不影响任何历史成绩）。 */
    fun clearSelection() = viewModelScope.launch {
        val student = activeStudent.value ?: return@launch
        container.subjectSelectionRepository.clear(student.id)
        showMessage("已清空选科，可重新选择")
    }

    fun deleteReport(id: String) = viewModelScope.launch {
        container.aiReportRepository.delete(id)
        _message.value = "AI 报告已删除"
    }

    // ---------------- 考试倒计时 ----------------

    fun saveCountdown(
        id: String? = null,
        name: String,
        date: java.time.LocalDate,
        timeMinutes: Int?,
        note: String,
        visible: Boolean,
        primary: Boolean,
        onDone: (Boolean) -> Unit = {},
    ) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showMessage("请填写考试名称")
            onDone(false)
            return@launch
        }
        runCatching {
            container.countdownRepository.save(
                id = id,
                name = trimmed,
                date = date,
                timeMinutes = timeMinutes,
                note = note,
                visible = visible,
                primary = primary,
            )
        }.onSuccess {
            showMessage(if (id == null) "考试已添加" else "考试已更新")
            onDone(true)
        }.onFailure {
            showMessage("保存失败：${it.message ?: "未知错误"}")
            onDone(false)
        }
    }

    fun deleteCountdown(id: String) = viewModelScope.launch {
        container.countdownRepository.delete(id)
        showMessage("考试已删除")
    }

    fun setCountdownPrimary(id: String) = viewModelScope.launch {
        container.countdownRepository.setPrimary(id)
        showMessage("已设为主页主显示考试")
    }

    fun setCountdownVisible(id: String, visible: Boolean) = viewModelScope.launch {
        container.countdownRepository.setVisible(id, visible)
    }
}

fun Context.container(): AppContainer = (applicationContext as ScoreTrackApp).container
