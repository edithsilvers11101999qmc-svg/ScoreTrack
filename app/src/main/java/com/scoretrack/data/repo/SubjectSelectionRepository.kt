package com.scoretrack.data.repo

import com.scoretrack.core.Subject
import com.scoretrack.data.db.SubjectSelectionDao
import com.scoretrack.data.db.SubjectSelectionEntity
import com.scoretrack.domain.selection.SelectionValidation
import com.scoretrack.domain.selection.SelectionRules
import com.scoretrack.domain.selection.SubjectSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 选科持久化：每个学生一行，按 studentId 关联；删除学生时由外键 CASCADE 自动清理。
 * 所有写入前都会重新校验 3+1+2 规则，非法组合不会落库。
 */
class SubjectSelectionRepository(
    private val dao: SubjectSelectionDao,
) {

    fun observe(studentId: String): Flow<SubjectSelection> =
        dao.observe(studentId).map { it?.toDomain() ?: SubjectSelection(studentId) }

    suspend fun get(studentId: String): SubjectSelection =
        dao.find(studentId)?.toDomain() ?: SubjectSelection(studentId)

    suspend fun all(): List<SubjectSelection> = dao.all().map { it.toDomain() }

    /** 保存选科；返回校验结果，ok=false 时不写库。 */
    suspend fun save(studentId: String, first: Subject?, second: List<Subject>): SelectionValidation {
        val validation = SelectionRules.validate(first, second)
        if (!validation.ok) return validation
        val ordered = Subject.secondChoiceOptions().filter { it in second }
        dao.upsert(
            SubjectSelectionEntity(
                studentId = studentId,
                firstSubject = first?.code,
                secondSubject1 = ordered.getOrNull(0)?.code,
                secondSubject2 = ordered.getOrNull(1)?.code,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return validation
    }

    suspend fun saveAll(selections: List<SubjectSelectionEntity>) = dao.upsertAll(selections)

    /** 清空该学生的选科（历史成绩不受影响）。 */
    suspend fun clear(studentId: String) = dao.delete(studentId)

    suspend fun entities(): List<SubjectSelectionEntity> = dao.all()

    private fun SubjectSelectionEntity.toDomain(): SubjectSelection = SubjectSelection(
        studentId = studentId,
        first = Subject.fromCode(firstSubject),
        second = listOfNotNull(
            Subject.fromCode(secondSubject1),
            Subject.fromCode(secondSubject2),
        ),
    )
}
