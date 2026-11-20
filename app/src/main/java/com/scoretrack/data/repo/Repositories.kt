package com.scoretrack.data.repo

import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Student
import com.scoretrack.core.Subject
import com.scoretrack.data.db.AiReportDao
import com.scoretrack.data.db.AiReportEntity
import com.scoretrack.data.db.DocumentDao
import com.scoretrack.data.db.ExamDao
import com.scoretrack.data.db.ExamEntity
import com.scoretrack.data.db.ExamWithScores
import com.scoretrack.data.db.ScoreEntity
import com.scoretrack.data.db.SourceDocumentEntity
import com.scoretrack.data.db.StudentDao
import com.scoretrack.data.db.StudentEntity
import com.scoretrack.data.db.SubjectDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun StudentEntity.toDomain(): Student = Student(
    id = id,
    name = name,
    className = className,
    studentNumber = studentNumber,
    isActive = isActive,
    createdAt = createdAt,
)

fun ExamWithScores.toDomain(): ExamRecord {
    val scoreMap = scores.associate { it.subjectCode to it.score }
    val rawMap = scores.associate { it.subjectCode to it.rawScore }
    val confidenceMap = scores.mapNotNull { entity -> entity.confidence?.let { entity.subjectCode to it } }.toMap()
    val bySubject = Subject.displayOrder().associateWith { subject ->
        Subject.fromCode(subject.code)?.let { scoreMap[it.code] } ?: scoreMap[subject.code]
    }
    val rawBySubject = Subject.displayOrder().associateWith { subject -> rawMap[subject.code] }
    val confBySubject = Subject.displayOrder().mapNotNull { subject ->
        confidenceMap[subject.code]?.let { subject to it }
    }.toMap()
    return ExamRecord(
        id = exam.id,
        studentId = exam.studentId,
        name = exam.name,
        date = exam.examDate.toLocalDate(),
        note = exam.note,
        scores = bySubject,
        rawScores = rawBySubject,
        confidences = confBySubject,
        sourceDocumentId = exam.sourceDocumentId,
        createdAt = exam.createdAt,
    )
}

class StudentRepository(
    private val studentDao: StudentDao,
    private val examDao: ExamDao,
) {
    fun observeStudents(): Flow<List<Student>> = studentDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveStudent(): Flow<Student?> = studentDao.observeAll().map { list ->
        list.firstOrNull { it.isActive }?.toDomain() ?: list.firstOrNull()?.toDomain()
    }

    suspend fun activeStudent(): Student? = (studentDao.activeStudent() ?: studentDao.all().firstOrNull())?.toDomain()

    suspend fun all(): List<Student> = studentDao.all().map { it.toDomain() }

    suspend fun findByName(name: String): List<Student> = studentDao.findByName(name.trim()).map { it.toDomain() }

    /**
     * 同名学生的安全处理：如果已存在同名且班级/学号也相同的档案，返回既有档案 id；
     * 否则新建。绝不把不同学生合并。
     */
    suspend fun createOrGet(name: String, className: String, studentNumber: String): Student {
        val existing = studentDao.findByName(name.trim())
        val exact = existing.firstOrNull {
            it.className.trim() == className.trim() && it.studentNumber.trim() == studentNumber.trim()
        }
        if (exact != null) return exact.toDomain()

        val now = System.currentTimeMillis()
        val entity = StudentEntity(
            id = Ids.newId(),
            name = name.trim(),
            className = className.trim(),
            studentNumber = studentNumber.trim(),
            createdAt = now,
            updatedAt = now,
            isActive = existing.isEmpty() && studentDao.count() == 0,
        )
        studentDao.upsert(entity)
        if (studentDao.activeStudent() == null) studentDao.activate(entity.id)
        return entity.toDomain()
    }

    suspend fun update(student: Student) {
        val existing = studentDao.findById(student.id) ?: return
        studentDao.update(
            existing.copy(
                name = student.name.trim(),
                className = student.className.trim(),
                studentNumber = student.studentNumber.trim(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(studentId: String) {
        val wasActive = studentDao.findById(studentId)?.isActive == true
        studentDao.deleteById(studentId)
        if (wasActive) {
            studentDao.all().firstOrNull()?.let { studentDao.activate(it.id) }
        }
    }

    suspend fun activate(studentId: String) = studentDao.activate(studentId)

    suspend fun ensureActive(): Student? {
        val active = activeStudent()
        if (active != null) {
            if (studentDao.activeStudent() == null) studentDao.activate(active.id)
            return active
        }
        return null
    }
}

class ExamRepository(
    private val examDao: ExamDao,
) {
    fun observeExams(studentId: String): Flow<List<ExamRecord>> =
        examDao.observeByStudent(studentId).map { list -> list.map { it.toDomain() } }

    suspend fun listExams(studentId: String): List<ExamRecord> =
        examDao.listByStudent(studentId).map { it.toDomain() }

    suspend fun getExam(examId: String): ExamRecord? = examDao.findById(examId)?.toDomain()

    suspend fun latestExam(studentId: String): ExamRecord? = examDao.latestForStudent(studentId)?.toDomain()

    suspend fun allExams(): List<ExamRecord> = examDao.listAll().map { it.toDomain() }

    suspend fun save(
        id: String?,
        studentId: String,
        name: String,
        date: LocalDate,
        note: String,
        scores: Map<Subject, Double?>,
        rawScores: Map<Subject, Double?> = emptyMap(),
        confidences: Map<Subject, Float> = emptyMap(),
        sourceDocumentId: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val examId = id ?: Ids.newId()
        val existing = if (id != null) examDao.findById(id)?.exam else null
        val entity = ExamEntity(
            id = examId,
            studentId = studentId,
            name = name.trim().ifBlank { "未命名考试" },
            examDate = date.toEpochMillis(),
            note = note.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            sourceDocumentId = sourceDocumentId ?: existing?.sourceDocumentId,
        )
        val scoreEntities = Subject.displayOrder().mapNotNull { subject ->
            val value = scores[subject]
            val raw = rawScores[subject]
            if (value == null && raw == null) return@mapNotNull null
            ScoreEntity(
                examId = examId,
                subjectCode = subject.code,
                score = value,
                rawScore = raw,
                maxScore = subject.maxScore,
                confidence = confidences[subject],
            )
        }
        examDao.saveExam(entity, scoreEntities)
        return examId
    }

    suspend fun delete(examId: String) = examDao.deleteExam(examId)

    suspend fun count(): Int = examDao.count()
}

class DocumentRepository(
    private val documentDao: DocumentDao,
) {
    fun observeAll(): Flow<List<SourceDocumentEntity>> = documentDao.observeAll()

    suspend fun all(): List<SourceDocumentEntity> = documentDao.all()

    suspend fun byStudent(studentId: String): List<SourceDocumentEntity> = documentDao.byStudent(studentId)

    suspend fun find(id: String): SourceDocumentEntity? = documentDao.findById(id)

    suspend fun save(document: SourceDocumentEntity) = documentDao.upsert(document)

    suspend fun deleteSafely(id: String) = documentDao.deleteSafely(id)
}

class AiReportRepository(
    private val dao: AiReportDao,
) {
    fun observeReports(studentId: String) = dao.observeByStudent(studentId)

    suspend fun all(): List<AiReportEntity> = dao.all()

    suspend fun find(id: String): AiReportEntity? = dao.findById(id)

    suspend fun save(report: AiReportEntity) = dao.upsert(report)

    suspend fun saveAll(reports: List<AiReportEntity>) = dao.upsertAll(reports)

    suspend fun delete(id: String) = dao.deleteById(id)
}
