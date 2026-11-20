package com.scoretrack

import android.app.Application
import android.content.Context
import com.scoretrack.ai.AiAnalysisService
import com.scoretrack.ai.OpenAiClient
import com.scoretrack.backup.BackupService
import com.scoretrack.data.db.ScoreTrackDatabase
import com.scoretrack.data.repo.AiReportRepository
import com.scoretrack.data.repo.DocumentRepository
import com.scoretrack.data.repo.ExamRepository
import com.scoretrack.data.goal.DashboardRepository
import com.scoretrack.data.goal.GoalLogoStore
import com.scoretrack.data.goal.GoalRepository
import com.scoretrack.data.repo.StudentRepository
import com.scoretrack.data.repo.SubjectSelectionRepository
import com.scoretrack.data.settings.SecretStore
import com.scoretrack.data.theme.ThemeRepository
import com.scoretrack.data.wallpaper.WallpaperRepository
import com.scoretrack.data.settings.SettingsRepository
import com.scoretrack.ocr.ImportPipeline
import com.scoretrack.ocr.OcrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScoreTrackApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 手写依赖容器（ServiceLocator）：模块之间只通过 Repository / Service 接口交互，
 * 不把业务逻辑写进 Activity，后续可平滑替换为 Hilt。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val database: ScoreTrackDatabase = ScoreTrackDatabase.get(appContext)

    val studentRepository = StudentRepository(database.studentDao(), database.examDao())
    val examRepository = ExamRepository(database.examDao())
    val documentRepository = DocumentRepository(database.documentDao())
    val aiReportRepository = AiReportRepository(database.aiReportDao())
    val secretStore = SecretStore()
    val settingsRepository = SettingsRepository(database.settingsDao(), secretStore)

    val openAiClient = OpenAiClient()
    val aiAnalysisService = AiAnalysisService(openAiClient, settingsRepository, aiReportRepository)

    val ocrEngine = OcrEngine()
    val importPipeline = ImportPipeline(appContext, ocrEngine)

    val backupService = BackupService(database, appContext.filesDir)

    // ---- V3：主题 / 壁纸 / 主页目标卡 ----
    val themeRepository = ThemeRepository(database.themeDao())
    val wallpaperRepository = WallpaperRepository(appContext, database.wallpaperDao())
    val goalRepository = GoalRepository(database.goalDao())
    val goalLogoStore = GoalLogoStore(appContext)
    val dashboardRepository = DashboardRepository(database.dashboardDao())

    // ---- V4：3+1+2 选科 ----
    val subjectSelectionRepository = SubjectSelectionRepository(database.subjectSelectionDao())

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appScope.launch {
            runCatching {
                themeRepository.ensureDefault()
                wallpaperRepository.ensureDefault()
                wallpaperRepository.deleteBrokenReference()
                dashboardRepository.ensureDefaults()
            }
        }
    }
}
