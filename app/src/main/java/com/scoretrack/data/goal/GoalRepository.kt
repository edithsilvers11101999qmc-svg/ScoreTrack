package com.scoretrack.data.goal

import com.scoretrack.data.db.DashboardCardEntity
import com.scoretrack.data.db.DashboardDao
import com.scoretrack.data.db.GoalCardEntity
import com.scoretrack.data.db.GoalDao
import com.scoretrack.domain.goal.GoalProgress
import com.scoretrack.domain.goal.computeGoalProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 主页大学目标卡（domain 模型，避免 UI 直接依赖 Room 实体）。 */
data class UniversityGoal(
    val id: String,
    val studentId: String,
    val universityName: String,
    val majorName: String,
    val targetScore: Double,
    val visible: Boolean,
    val sortOrder: Int,
    val accentColorHex: String?,
    val cardOpacity: Float,
    val showAuxInfo: Boolean,
    /** 校徽图片路径（应用私有目录），为空表示使用默认图标。 */
    val logoPath: String? = null,
) {
    fun progress(latestTotal: Double?): GoalProgress = computeGoalProgress(latestTotal, targetScore)
}

class GoalRepository(private val dao: GoalDao) {

    fun observe(studentId: String): Flow<List<UniversityGoal>> =
        dao.observeByStudent(studentId).map { list -> list.map { it.toDomain() } }

    suspend fun list(studentId: String): List<UniversityGoal> =
        dao.listByStudent(studentId).map { it.toDomain() }

    suspend fun all(): List<UniversityGoal> = dao.listAll().map { it.toDomain() }

    suspend fun upsert(goal: UniversityGoal) {
        val existing = dao.listByStudent(goal.studentId)
        val sortOrder = if (existing.any { it.cardId == goal.id }) {
            existing.first { it.cardId == goal.id }.sortOrder
        } else {
            (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        }
        dao.upsert(goal.toEntity(sortOrder))
    }

    suspend fun setVisible(cardId: String, visible: Boolean) {
        val card = dao.listAll().firstOrNull { it.cardId == cardId } ?: return
        dao.upsert(card.copy(isVisible = visible, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(cardId: String) = dao.delete(cardId)

    /** 只更新校徽，不动其它字段（配合设置页里「只换校徽」的场景）。 */
    suspend fun setLogo(cardId: String, logoPath: String?) =
        dao.updateLogo(cardId, logoPath, System.currentTimeMillis())

    suspend fun find(cardId: String): UniversityGoal? =
        dao.listAll().firstOrNull { it.cardId == cardId }?.toDomain()

    /** 该学生全部目标卡的校徽路径（用于清理孤儿文件）。 */
    suspend fun logoPaths(studentId: String): List<String?> =
        dao.listByStudent(studentId).map { it.logoPath }

    suspend fun allLogoPaths(): List<String?> = dao.listAll().map { it.logoPath }

    /** 上移/下移并重排 sortOrder，保证顺序稳定。 */
    suspend fun move(studentId: String, cardId: String, delta: Int) {
        val list = dao.listByStudent(studentId).toMutableList()
        val index = list.indexOfFirst { it.cardId == cardId }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.size - 1)
        if (target == index) return
        val moved = list.removeAt(index)
        list.add(target, moved)
        dao.upsertAll(
            list.mapIndexed { i, card ->
                card.copy(sortOrder = i, updatedAt = System.currentTimeMillis())
            },
        )
    }

    private fun GoalCardEntity.toDomain(): UniversityGoal = UniversityGoal(
        id = cardId,
        studentId = studentId,
        universityName = universityName,
        majorName = majorName,
        targetScore = targetScore,
        visible = isVisible,
        sortOrder = sortOrder,
        accentColorHex = accentColorHex,
        cardOpacity = cardOpacity,
        showAuxInfo = showAuxInfo,
        logoPath = logoPath,
    )

    private fun UniversityGoal.toEntity(sortOrder: Int): GoalCardEntity = GoalCardEntity(
        cardId = id,
        studentId = studentId,
        universityName = universityName,
        majorName = majorName,
        targetScore = targetScore,
        isVisible = visible,
        sortOrder = sortOrder,
        accentColorHex = accentColorHex,
        cardOpacity = cardOpacity,
        showAuxInfo = showAuxInfo,
        logoPath = logoPath,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )
}

/** 主页模块配置（目前只有顺序与开关，后续可扩展倒计时/目标分等模块）。 */
data class DashboardSection(
    val cardType: String,
    val enabled: Boolean,
    val sortOrder: Int,
) {
    companion object {
        const val GOALS = "goals"
        const val LATEST_EXAM = "latest_exam"
        const val SUBJECT_OVERVIEW = "subject_overview"
        val DEFAULTS = listOf(
            DashboardSection(GOALS, true, 0),
            DashboardSection(LATEST_EXAM, true, 1),
            DashboardSection(SUBJECT_OVERVIEW, true, 2),
        )
    }
}

class DashboardRepository(private val dao: DashboardDao) {

    fun observe(): Flow<List<DashboardSection>> = dao.observe().map { list ->
        if (list.isEmpty()) DashboardSection.DEFAULTS else list.map { DashboardSection(it.cardType, it.isEnabled, it.sortOrder) }
    }

    suspend fun ensureDefaults() {
        val existing = dao.list()
        if (existing.isEmpty()) {
            dao.upsertAll(
                DashboardSection.DEFAULTS.map { DashboardCardEntity(it.cardType, it.enabled, it.sortOrder) },
            )
        }
    }

    suspend fun setEnabled(cardType: String, enabled: Boolean) {
        val list = dao.list()
        val card = list.firstOrNull { it.cardType == cardType } ?: return
        dao.upsert(card.copy(isEnabled = enabled))
    }

    suspend fun move(cardType: String, delta: Int) {
        val list = dao.list().sortedBy { it.sortOrder }.toMutableList()
        val index = list.indexOfFirst { it.cardType == cardType }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.size - 1)
        if (target == index) return
        val moved = list.removeAt(index)
        list.add(target, moved)
        dao.upsertAll(list.mapIndexed { i, card -> card.copy(sortOrder = i) })
    }
}
