package com.scoretrack.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val className: String,
    val studentNumber: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean = false,
)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val code: String,
    val displayName: String,
    val maxScore: Double,
    val scaled: Boolean,
    val sortOrder: Int,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "exams",
    indices = [Index("studentId"), Index("sourceDocumentId")],
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class ExamEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val name: String,
    val examDate: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val sourceDocumentId: String? = null,
)

@Entity(
    tableName = "scores",
    primaryKeys = ["examId", "subjectCode"],
    indices = [Index("examId"), Index("subjectCode")],
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class ScoreEntity(
    val examId: String,
    val subjectCode: String,
    /** 正式成绩；化学/地理为赋分后的成绩。null 表示缺失，绝不自动填 0 */
    val score: Double?,
    /** 赋分科目的原始分（如果识别/录入时提供） */
    val rawScore: Double? = null,
    val maxScore: Double,
    val confidence: Float? = null,
    val sourceText: String? = null,
)

@Entity(
    tableName = "source_documents",
    indices = [Index("studentId")],
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
)
data class SourceDocumentEntity(
    @PrimaryKey val id: String,
    val studentId: String?,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val pageCount: Int,
    val importedAt: Long,
    val kind: String,
    val ocrPayloadJson: String? = null,
)

@Entity(
    tableName = "ai_reports",
    indices = [Index("studentId")],
)
data class AiReportEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val studentName: String,
    val title: String,
    val examRange: String,
    val createdAt: Long,
    val model: String,
    val providerHost: String,
    val content: String,
    val dataSnapshotJson: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val status: String = "SUCCESS",
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class ExamWithScores(
    @Embedded val exam: ExamEntity,
    @Relation(parentColumn = "id", entityColumn = "examId")
    val scores: List<ScoreEntity>,
)

// ---------------------------------------------------------------------------
// V3 新增表（数据库版本 2）。以上所有 V2 表结构保持不变，仅新增表与索引。
// ---------------------------------------------------------------------------

/**
 * 主题设置（单行表，id 固定为 1）。
 * [themeMode] SYSTEM / LIGHT / DARK；[presetId] 预设主题标识或 "custom"；
 * [primaryColorHex] 当前生效的品牌色；[customColorHex] 用户自定义色。
 */
@Entity(tableName = "theme_settings")
data class ThemeSettingEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "SYSTEM",
    val presetId: String = "default_blue",
    val primaryColorHex: String = "#2B5F9E",
    val customColorHex: String = "#2B5F9E",
    val useCustom: Boolean = false,
    val updatedAt: Long = 0L,
)

/** 壁纸设置（单行表，id 固定为 1）。offset 为相对视口宽高的比例，跨设备一致。 */
@Entity(tableName = "wallpaper_settings")
data class WallpaperSettingEntity(
    @PrimaryKey val id: Int = 1,
    val wallpaperPath: String? = null,
    val enabled: Boolean = false,
    val opacity: Float = 1f,
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f,
    val updatedAt: Long = 0L,
)

/** 主页大学目标卡（按学生隔离）。 */
@Entity(tableName = "goal_cards", indices = [Index("studentId")])
data class GoalCardEntity(
    @PrimaryKey val cardId: String,
    val studentId: String,
    val universityName: String,
    val majorName: String,
    val targetScore: Double,
    val isVisible: Boolean = true,
    val sortOrder: Int = 0,
    val accentColorHex: String? = null,
    val cardOpacity: Float = 1f,
    val showAuxInfo: Boolean = true,
    /** 目标大学的校徽图片绝对路径（复制到应用私有目录），为空表示未设置。 */
    val logoPath: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** 主页模块开关与顺序。cardType: goals / latest_exam / subject_overview。 */
@Entity(tableName = "dashboard_cards")
data class DashboardCardEntity(
    @PrimaryKey val cardType: String,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
)

/**
 * 新高考 3+1+2 选科（每个学生一行，随学生删除级联）。
 * firstSubject / secondSubject1 / secondSubject2 存科目 code（physics/history/biology/...），
 * 未完成选择时对应列为 NULL。
 */
@Entity(
    tableName = "subject_selections",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("studentId")],
)
data class SubjectSelectionEntity(
    @PrimaryKey val studentId: String,
    val firstSubject: String? = null,
    val secondSubject1: String? = null,
    val secondSubject2: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * 自定义考试倒计时（V4.2 新增，与具体学生无关，属于全局提醒）。
 *
 * - examDateEpochDay：考试日期，存 [java.time.LocalDate.toEpochDay]，与时区无关地表示「哪一天」；
 * - timeMinutes：可选的当天分钟数（0..1439），NULL 表示未设置具体时间；
 * - isPrimary：主页主显示考试；Repository 负责保证同一时刻最多一条为 true。
 */
@Entity(tableName = "countdown_exams", indices = [Index("examDateEpochDay")])
data class CountdownExamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val examDateEpochDay: Long,
    val timeMinutes: Int? = null,
    val note: String = "",
    val isVisible: Boolean = true,
    val isPrimary: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
