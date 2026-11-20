package com.scoretrack.ai

import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Student
import com.scoretrack.data.db.AiReportEntity
import com.scoretrack.data.repo.AiReportRepository
import com.scoretrack.data.repo.Ids
import com.scoretrack.data.settings.SettingsRepository
import com.scoretrack.core.Subject
import com.scoretrack.domain.analytics.AnalysisResult
import com.scoretrack.domain.selection.ElectiveTotalCalculator
import com.scoretrack.domain.selection.SubjectSelection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.encodeToString

class AiAnalysisService(
    private val client: OpenAiClient,
    private val settingsRepository: SettingsRepository,
    private val reportRepository: AiReportRepository,
) {

    fun checkConfiguration(hasApiKey: Boolean, baseUrl: String): String? = when {
        !hasApiKey -> "尚未配置 API Key，请前往「设置 → AI 分析设置」填写 API 信息。"
        baseUrl.isBlank() -> "API 地址为空，请前往 AI 分析设置填写。"
        else -> null
    }

    suspend fun generate(
        student: Student,
        analysis: AnalysisResult,
        selection: SubjectSelection = SubjectSelection.empty(student.id),
        previousReportId: String? = null,
    ): AiReportEntity {
        val settings = settingsRepository.snapshot()
        val config = settings.aiConfig
        if (!config.hasApiKey) throw AiException(AiErrorType.NO_KEY, "尚未配置 API Key")
        val apiKey = settingsRepository.apiKey()
            ?: throw AiException(AiErrorType.NO_KEY, "无法读取本地保存的 API Key，请重新填写")

        val elective = ElectiveTotalCalculator.total(
            scores = latestScoreMap(analysis),
            selection = selection,
        )
        val snapshot = SnapshotBuilder.build(student, analysis, selection, elective)
        val messages = listOf(
            ChatMessage("system", PromptBuilder.SYSTEM_PROMPT),
            ChatMessage("user", PromptBuilder.userPrompt(snapshot, analysis)),
        )
        val response = client.chat(config = config, apiKey = apiKey, messages = messages)
        if (previousReportId != null) reportRepository.delete(previousReportId)

        val now = System.currentTimeMillis()
        val range = examRange(analysis.exams)
        val report = AiReportEntity(
            id = Ids.newId(),
            studentId = student.id,
            studentName = student.name,
            title = "AI 成绩分析报告",
            examRange = range,
            createdAt = now,
            model = config.model,
            providerHost = config.displayHost,
            content = response.content,
            dataSnapshotJson = AiJson.json.encodeToString(snapshot),
            promptTokens = response.promptTokens,
            completionTokens = response.completionTokens,
            status = "SUCCESS",
        )
        reportRepository.save(report)
        return report
    }

    private fun examRange(exams: List<ExamRecord>): String {
        if (exams.isEmpty()) return "暂无考试数据"
        val first = exams.minOf { it.date }
        val last = exams.maxOf { it.date }
        val fmt = DateTimeFormatter.ofPattern("yyyy年M月d日")
        return "${first.format(fmt)} — ${last.format(fmt)} · 共${exams.size}次考试"
    }

    fun buildSnapshot(
        student: Student,
        analysis: AnalysisResult,
        selection: SubjectSelection = SubjectSelection.empty(student.id),
    ): AiSnapshot = SnapshotBuilder.build(student, analysis, selection)

    private fun latestScoreMap(analysis: AnalysisResult): Map<Subject, Double?> =
        analysis.exams.lastOrNull()?.scores ?: emptyMap()

    fun snapshotJson(snapshot: AiSnapshot): String = AiJson.json.encodeToString(snapshot)

    fun nowText(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
