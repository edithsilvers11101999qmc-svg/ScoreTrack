package com.scoretrack.data.countdown

import com.scoretrack.core.Countdown
import com.scoretrack.core.CountdownMath
import com.scoretrack.data.db.CountdownExamDao
import com.scoretrack.data.db.CountdownExamEntity
import com.scoretrack.data.repo.Ids
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 考试倒计时（domain 模型，UI 不直接依赖 Room 实体）。
 */
data class CountdownExam(
    val id: String,
    val name: String,
    val date: LocalDate,
    val timeMinutes: Int?,
    val note: String,
    val visible: Boolean,
    val primary: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun countdown(now: ZonedDateTime = ZonedDateTime.now()): Countdown =
        CountdownMath.evaluate(date, timeMinutes, now)

    fun finished(now: ZonedDateTime = ZonedDateTime.now()): Boolean =
        CountdownMath.isFinished(date, now)

    val dateText: String
        get() = "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日"

    val timeText: String
        get() = CountdownMath.formatTime(timeMinutes)

    val timeOrEmpty: String
        get() = timeMinutes?.let { CountdownMath.formatTime(it) } ?: ""
}

/**
 * 倒计时数据仓库。
 *
 * 规则：
 * - 同一时刻最多一条 isPrimary，保存 / 设为主显示时由仓库保证；
 * - 排序：按考试日期由近到远（过期考试自然排在最后，UI 再按「历史考试」分组）；
 * - 主页取卡片时优先主显示，其次取最近的「未结束 + 已启用」考试。
 */
class CountdownRepository(private val dao: CountdownExamDao) {

    fun observe(): Flow<List<CountdownExam>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun all(): List<CountdownExam> = dao.all().map { it.toDomain() }

    suspend fun find(id: String): CountdownExam? = dao.find(id)?.toDomain()

    suspend fun count(): Int = dao.count()

    suspend fun save(
        id: String? = null,
        name: String,
        date: LocalDate,
        timeMinutes: Int?,
        note: String = "",
        visible: Boolean = true,
        primary: Boolean = false,
    ): CountdownExam {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.find(it) }
        val resolvedId = existing?.id ?: Ids.newId()
        val entity = CountdownExamEntity(
            id = resolvedId,
            name = name.trim(),
            examDateEpochDay = date.toEpochDay(),
            timeMinutes = timeMinutes,
            note = note.trim(),
            isVisible = visible,
            isPrimary = primary || existing?.isPrimary == true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        if (primary) dao.setPrimary(resolvedId)
        return dao.find(resolvedId)!!.toDomain()
    }

    suspend fun setPrimary(id: String) = dao.setPrimary(id)

    suspend fun setVisible(id: String, visible: Boolean) =
        dao.setVisible(id, visible, System.currentTimeMillis())

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * 主页卡片展示的考试：
     * 1. 主显示（启用且未结束）；
     * 2. 否则最近的启用且未结束的考试；
     * 3. 都没有则返回 null（主页显示空状态）。
     */
    fun homeExam(exams: List<CountdownExam>, now: ZonedDateTime = ZonedDateTime.now()): CountdownExam? =
        pickHomeCountdown(exams, now)
}

/**
 * 主页卡片展示的考试（纯函数，便于复用与测试）：
 * 1. 主显示（启用且未结束）；
 * 2. 否则最近的启用且未结束的考试；
 * 3. 都没有则返回 null（主页显示空状态）。
 */
fun pickHomeCountdown(
    exams: List<CountdownExam>,
    now: ZonedDateTime = ZonedDateTime.now(),
): CountdownExam? {
    val available = exams.filter { it.visible && !it.finished(now) }
    if (available.isEmpty()) return null
    val sorted = available.sortedWith(
        compareBy<CountdownExam> { it.date }
            .thenBy { it.timeMinutes ?: Int.MAX_VALUE }
    )
    return sorted.firstOrNull { it.primary } ?: sorted.first()
}

internal fun CountdownExamEntity.toDomain(): CountdownExam = CountdownExam(
    id = id,
    name = name,
    date = LocalDate.ofEpochDay(examDateEpochDay),
    timeMinutes = timeMinutes,
    note = note,
    visible = isVisible,
    primary = isPrimary,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** 供设置/备份等场景把毫秒时间戳格式化为本地时间文本。 */
internal fun Long.toLocalDateText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()
