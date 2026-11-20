package com.scoretrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scoretrack.core.Subject
import com.scoretrack.data.upgrade.UpgradeManager

/**
 * 数据库版本与迁移策略：
 * - v1：V2 既有 7 张表；
 * - v2（V3.0.0）：仅新增 theme_settings / wallpaper_settings / goal_cards / dashboard_cards，
 *   既有表结构与数据保持不变，禁止破坏性重建。
 * - v3（V4.0.0）：新增 subject_selections（3+1+2 选科），并向 subjects 表补充生物/政治/历史，
 *   既有学生、考试、成绩、AI 报告与个性化设置全部保留。
 * - 升级前由 [UpgradeManager.prepareIfNeeded] 自动备份数据库与壁纸，迁移后校验数据量。
 */
@Database(
    entities = [
        StudentEntity::class,
        SubjectEntity::class,
        ExamEntity::class,
        ScoreEntity::class,
        SourceDocumentEntity::class,
        AiReportEntity::class,
        AppSettingEntity::class,
        ThemeSettingEntity::class,
        WallpaperSettingEntity::class,
        GoalCardEntity::class,
        DashboardCardEntity::class,
        SubjectSelectionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class ScoreTrackDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun examDao(): ExamDao
    abstract fun subjectDao(): SubjectDao
    abstract fun documentDao(): DocumentDao
    abstract fun aiReportDao(): AiReportDao
    abstract fun settingsDao(): SettingsDao
    abstract fun themeDao(): ThemeDao
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun goalDao(): GoalDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun subjectSelectionDao(): SubjectSelectionDao

    companion object {

        const val DB_NAME = "scoretrack.db"

        @Volatile
        private var instance: ScoreTrackDatabase? = null

        /** v1 → v2：新增 V3 功能表，并延续既有主题设置（app_settings.theme_mode）。 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `theme_settings` (" +
                        "`id` INTEGER NOT NULL, `themeMode` TEXT NOT NULL, `presetId` TEXT NOT NULL, " +
                        "`primaryColorHex` TEXT NOT NULL, `customColorHex` TEXT NOT NULL, " +
                        "`useCustom` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wallpaper_settings` (" +
                        "`id` INTEGER NOT NULL, `wallpaperPath` TEXT, `enabled` INTEGER NOT NULL, " +
                        "`opacity` REAL NOT NULL, `cropScale` REAL NOT NULL, `cropOffsetX` REAL NOT NULL, " +
                        "`cropOffsetY` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_cards` (" +
                        "`cardId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `universityName` TEXT NOT NULL, " +
                        "`majorName` TEXT NOT NULL, `targetScore` REAL NOT NULL, `isVisible` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `accentColorHex` TEXT, `cardOpacity` REAL NOT NULL, " +
                        "`showAuxInfo` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`cardId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_cards_studentId` ON `goal_cards` (`studentId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dashboard_cards` (" +
                        "`cardType` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`cardType`))",
                )

                // 初始化默认行：主题沿用 V2 的用户设置，其余给默认值
                db.execSQL(
                    "INSERT OR REPLACE INTO `theme_settings` " +
                        "(`id`, `themeMode`, `presetId`, `primaryColorHex`, `customColorHex`, `useCustom`, `updatedAt`) " +
                        "VALUES (1, " +
                        "COALESCE((SELECT CASE LOWER(`value`) WHEN 'light' THEN 'LIGHT' WHEN 'dark' THEN 'DARK' ELSE 'SYSTEM' END " +
                        " FROM `app_settings` WHERE `key` = 'theme_mode'), 'SYSTEM'), " +
                        "'default_blue', '#2B5F9E', '#2B5F9E', 0, " + System.currentTimeMillis() + ")",
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `wallpaper_settings` " +
                        "(`id`, `wallpaperPath`, `enabled`, `opacity`, `cropScale`, `cropOffsetX`, `cropOffsetY`, `updatedAt`) " +
                        "VALUES (1, NULL, 0, 1.0, 1.0, 0.0, 0.0, 0)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `dashboard_cards` (`cardType`, `isEnabled`, `sortOrder`) VALUES " +
                        "('goals', 1, 0), ('latest_exam', 1, 1), ('subject_overview', 1, 2)",
                )
            }
        }

        /** v2 → v3：新增选科表，补充生物/政治/历史三科与主页「选科概览」模块。 */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subject_selections` (" +
                        "`studentId` TEXT NOT NULL, `firstSubject` TEXT, `secondSubject1` TEXT, " +
                        "`secondSubject2` TEXT, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`studentId`), " +
                        "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subject_selections_studentId` " +
                        "ON `subject_selections` (`studentId`)",
                )

                // 补充新增科目（生物 / 政治 / 历史），不触碰既有六科数据
                val extra = Subject.allSubjects().filter { it !in Subject.displayOrder() }
                extra.forEachIndexed { index, subject ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO subjects (code, displayName, maxScore, scaled, sortOrder, enabled) " +
                            "VALUES (?, ?, ?, ?, ?, 1)",
                        arrayOf(
                            subject.code,
                            subject.cnName,
                            subject.maxScore,
                            if (subject.isScaled) 1 else 0,
                            Subject.displayOrder().size + index,
                        ),
                    )
                }

                // 主页新增「选科概览」模块，默认排在大学目标之后
                val exists = db.query("SELECT COUNT(*) FROM dashboard_cards WHERE cardType = 'subject_selection'")
                    .use { if (it.moveToFirst()) it.getInt(0) else 0 }
                if (exists == 0) {
                    db.execSQL(
                        "UPDATE dashboard_cards SET sortOrder = sortOrder + 1 " +
                            "WHERE cardType IN ('latest_exam','subject_overview')",
                    )
                    db.execSQL(
                        "INSERT OR IGNORE INTO dashboard_cards (cardType, isEnabled, sortOrder) " +
                            "VALUES ('subject_selection', 1, 1)",
                    )
                }
            }
        }

        /**
         * v3 → v4：目标卡新增「校徽」图片路径列。
         * 只加列，不重建表，不改动任何既有目标数据（旧数据 logoPath 为 NULL，显示默认图标）。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `goal_cards` ADD COLUMN `logoPath` TEXT")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun get(context: Context): ScoreTrackDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): ScoreTrackDatabase {
            // 升级前备份必须在 Room 打开数据库（触发迁移）之前完成
            val pending = UpgradeManager.prepareIfNeeded(context)
            return Room.databaseBuilder(context, ScoreTrackDatabase::class.java, DB_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedSubjects(db)
                        seedV3Defaults(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        seedSubjects(db)
                        seedV3Defaults(db)
                        if (pending != null) {
                            runCatching { UpgradeManager.finish(context, db, pending) }
                        }
                    }
                })
                .build()
        }

        private fun seedSubjects(db: SupportSQLiteDatabase) {
            Subject.allSubjects().forEachIndexed { index, subject ->
                db.execSQL(
                    "INSERT OR IGNORE INTO subjects (code, displayName, maxScore, scaled, sortOrder, enabled) " +
                        "VALUES (?, ?, ?, ?, ?, 1)",
                    arrayOf(subject.code, subject.cnName, subject.maxScore, if (subject.isScaled) 1 else 0, index),
                )
            }
        }

        /** 全新安装时补齐 V3 默认配置（升级安装由迁移脚本负责）。 */
        private fun seedV3Defaults(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO `theme_settings` " +
                    "(`id`, `themeMode`, `presetId`, `primaryColorHex`, `customColorHex`, `useCustom`, `updatedAt`) " +
                    "VALUES (1, 'SYSTEM', 'default_blue', '#2B5F9E', '#2B5F9E', 0, 0)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `wallpaper_settings` " +
                    "(`id`, `wallpaperPath`, `enabled`, `opacity`, `cropScale`, `cropOffsetX`, `cropOffsetY`, `updatedAt`) " +
                    "VALUES (1, NULL, 0, 1.0, 1.0, 0.0, 0.0, 0)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `dashboard_cards` (`cardType`, `isEnabled`, `sortOrder`) VALUES " +
                    "('goals', 1, 0), ('subject_selection', 1, 1), ('latest_exam', 1, 2), ('subject_overview', 1, 3)",
            )
        }
    }
}
