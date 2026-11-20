package com.scoretrack.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 一条已确认的考试记录（领域模型，脱离 Room 与 UI 使用） */
data class ExamRecord(
    val id: String,
    val studentId: String,
    val name: String,
    val date: LocalDate,
    val note: String = "",
    val scores: Map<Subject, Double?> = emptyMap(),
    val rawScores: Map<Subject, Double?> = emptyMap(),
    val confidences: Map<Subject, Float> = emptyMap(),
    val sourceDocumentId: String? = null,
    val createdAt: Long = 0L,
) {
    fun scoreOf(subject: Subject): Double? = scores[subject]

    /** 已录入（非缺失）的科目 */
    val presentSubjects: List<Subject>
        get() = Subject.displayOrder().filter { scores[it] != null }

    /** 是否六科齐全 */
    val isComplete: Boolean
        get() = Subject.displayOrder().all { scores[it] != null }

    /** 仅当六科齐全时给出总分，否则为 null（不把缺失当成 0 分） */
    val total: Double?
        get() = if (isComplete) Subject.displayOrder().sumOf { scores[it] ?: 0.0 } else null

    /** 已录入科目的分数合计，仅用于展示“部分合计”，不会当作总分 */
    val partialTotal: Double
        get() = presentSubjects.sumOf { scores[it] ?: 0.0 }

    val missingSubjects: List<Subject>
        get() = Subject.displayOrder().filter { scores[it] == null }

    fun rateOf(subject: Subject): Double? = scores[subject]?.let { subject.rateOf(it) }

    val dateText: String
        get() = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** 用于图表横轴的短标签 */
    fun shortLabel(index: Int): String = if (name.length <= 6) name else "第${index + 1}次"
}

data class Student(
    val id: String,
    val name: String,
    val className: String,
    val studentNumber: String,
    val isActive: Boolean = false,
    val createdAt: Long = 0L,
) {
    val displayLabel: String
        get() = buildString {
            append(name)
            if (className.isNotBlank()) append(" · ").append(className)
            if (studentNumber.isNotBlank()) append(" · ").append(studentNumber)
        }
}

/** OCR / 表格导入得到的候选成绩行（尚未写入数据库） */
data class ImportedScoreRow(
    val studentName: String = "",
    val className: String = "",
    val studentNumber: String = "",
    val scores: Map<Subject, Double?> = emptyMap(),
    val rawScores: Map<Subject, Double?> = emptyMap(),
    val confidences: Map<Subject, Float> = emptyMap(),
    val sourceTexts: Map<Subject, String> = emptyMap(),
    val studentId: String? = null,
    val matchScore: Float = 0f,
)

/** 一次导入过程中，单个字段的识别结果，保留原始文字与来源区域 */
data class FieldEvidence(
    val rawText: String,
    val confidence: Float?,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val pageIndex: Int = 0,
)
