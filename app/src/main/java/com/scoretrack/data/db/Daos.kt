package com.scoretrack.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun findById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE isActive = 1 LIMIT 1")
    suspend fun activeStudent(): StudentEntity?

    @Query("SELECT * FROM students WHERE name = :name")
    suspend fun findByName(name: String): List<StudentEntity>

    @Query("SELECT * FROM students")
    suspend fun all(): List<StudentEntity>

    @Upsert
    suspend fun upsert(student: StudentEntity)

    @Upsert
    suspend fun upsertAll(students: List<StudentEntity>)

    @Update
    suspend fun update(student: StudentEntity)

    @Delete
    suspend fun delete(student: StudentEntity)

    @Query("UPDATE students SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE students SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Transaction
    suspend fun activate(id: String) {
        clearActive()
        setActive(id)
    }

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM students")
    suspend fun count(): Int
}

@Dao
interface ExamDao {
    @Transaction
    @Query("SELECT * FROM exams WHERE studentId = :studentId ORDER BY examDate ASC, createdAt ASC")
    fun observeByStudent(studentId: String): Flow<List<ExamWithScores>>

    @Transaction
    @Query("SELECT * FROM exams WHERE studentId = :studentId ORDER BY examDate ASC, createdAt ASC")
    suspend fun listByStudent(studentId: String): List<ExamWithScores>

    @Transaction
    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun findById(id: String): ExamWithScores?

    @Transaction
    @Query("SELECT * FROM exams ORDER BY examDate ASC")
    suspend fun listAll(): List<ExamWithScores>

    @Transaction
    @Query("SELECT * FROM exams WHERE studentId = :studentId ORDER BY examDate DESC, createdAt DESC LIMIT 1")
    suspend fun latestForStudent(studentId: String): ExamWithScores?

    @Query("SELECT * FROM exams")
    fun observeAllExams(): Flow<List<ExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScores(scores: List<ScoreEntity>)

    @Query("DELETE FROM scores WHERE examId = :examId")
    suspend fun deleteScores(examId: String)

    @Query("DELETE FROM exams WHERE id = :examId")
    suspend fun deleteExam(examId: String)

    @Update
    suspend fun updateExam(exam: ExamEntity)

    @Transaction
    suspend fun saveExam(exam: ExamEntity, scores: List<ScoreEntity>) {
        insertExam(exam)
        deleteScores(exam.id)
        insertScores(scores)
    }

    @Transaction
    suspend fun saveExamKeepScores(exam: ExamEntity) {
        insertExam(exam)
    }

    @Query("SELECT COUNT(*) FROM exams WHERE studentId = :studentId AND name = :name AND examDate = :date")
    suspend fun countSame(studentId: String, name: String, date: Long): Int

    @Query("SELECT COUNT(*) FROM exams")
    suspend fun count(): Int
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC")
    suspend fun all(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(subjects: List<SubjectEntity>)

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM source_documents ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<SourceDocumentEntity>>

    @Query("SELECT * FROM source_documents WHERE id = :id")
    suspend fun findById(id: String): SourceDocumentEntity?

    @Query("SELECT * FROM source_documents")
    suspend fun all(): List<SourceDocumentEntity>

    @Query("SELECT * FROM source_documents WHERE studentId = :studentId ORDER BY importedAt DESC")
    suspend fun byStudent(studentId: String): List<SourceDocumentEntity>

    @Upsert
    suspend fun upsert(document: SourceDocumentEntity)

    @Upsert
    suspend fun upsertAll(documents: List<SourceDocumentEntity>)

    @Query("DELETE FROM source_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE exams SET sourceDocumentId = NULL WHERE sourceDocumentId = :id")
    suspend fun detachFromExams(id: String)

    @Transaction
    suspend fun deleteSafely(id: String) {
        detachFromExams(id)
        deleteById(id)
    }

    @Query("SELECT COUNT(*) FROM source_documents")
    suspend fun count(): Int
}

@Dao
interface AiReportDao {
    @Query("SELECT * FROM ai_reports WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun observeByStudent(studentId: String): Flow<List<AiReportEntity>>

    @Query("SELECT * FROM ai_reports ORDER BY createdAt DESC")
    suspend fun all(): List<AiReportEntity>

    @Query("SELECT * FROM ai_reports WHERE id = :id")
    suspend fun findById(id: String): AiReportEntity?

    @Upsert
    suspend fun upsert(report: AiReportEntity)

    @Upsert
    suspend fun upsertAll(reports: List<AiReportEntity>)

    @Query("DELETE FROM ai_reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM ai_reports")
    suspend fun count(): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_settings")
    suspend fun all(): List<AppSettingEntity>

    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(settings: List<AppSettingEntity>)

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun remove(key: String)
}

@Dao
interface ThemeDao {
    @Query("SELECT * FROM theme_settings WHERE id = 1")
    suspend fun get(): ThemeSettingEntity?

    @Query("SELECT * FROM theme_settings WHERE id = 1")
    fun observe(): Flow<ThemeSettingEntity?>

    @Upsert
    suspend fun upsert(setting: ThemeSettingEntity)
}

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpaper_settings WHERE id = 1")
    suspend fun get(): WallpaperSettingEntity?

    @Query("SELECT * FROM wallpaper_settings WHERE id = 1")
    fun observe(): Flow<WallpaperSettingEntity?>

    @Upsert
    suspend fun upsert(setting: WallpaperSettingEntity)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal_cards WHERE studentId = :studentId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeByStudent(studentId: String): Flow<List<GoalCardEntity>>

    @Query("SELECT * FROM goal_cards WHERE studentId = :studentId ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun listByStudent(studentId: String): List<GoalCardEntity>

    @Query("SELECT * FROM goal_cards ORDER BY sortOrder ASC")
    suspend fun listAll(): List<GoalCardEntity>

    @Upsert
    suspend fun upsert(card: GoalCardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<GoalCardEntity>)

    @Query("UPDATE goal_cards SET logoPath = :logoPath, updatedAt = :updatedAt WHERE cardId = :cardId")
    suspend fun updateLogo(cardId: String, logoPath: String?, updatedAt: Long)

    @Query("DELETE FROM goal_cards WHERE cardId = :cardId")
    suspend fun delete(cardId: String)

    @Query("SELECT COUNT(*) FROM goal_cards")
    suspend fun count(): Int
}

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_cards ORDER BY sortOrder ASC")
    fun observe(): Flow<List<DashboardCardEntity>>

    @Query("SELECT * FROM dashboard_cards ORDER BY sortOrder ASC")
    suspend fun list(): List<DashboardCardEntity>

    @Upsert
    suspend fun upsertAll(cards: List<DashboardCardEntity>)

    @Upsert
    suspend fun upsert(card: DashboardCardEntity)
}


@Dao
interface SubjectSelectionDao {

    @Query("SELECT * FROM subject_selections WHERE studentId = :studentId LIMIT 1")
    suspend fun find(studentId: String): SubjectSelectionEntity?

    @Query("SELECT * FROM subject_selections WHERE studentId = :studentId LIMIT 1")
    fun observe(studentId: String): Flow<SubjectSelectionEntity?>

    @Query("SELECT * FROM subject_selections")
    suspend fun all(): List<SubjectSelectionEntity>

    @Upsert
    suspend fun upsert(entity: SubjectSelectionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SubjectSelectionEntity>)

    @Query("DELETE FROM subject_selections WHERE studentId = :studentId")
    suspend fun delete(studentId: String)
}
