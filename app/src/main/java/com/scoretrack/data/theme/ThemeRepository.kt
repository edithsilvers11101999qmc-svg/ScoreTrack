package com.scoretrack.data.theme

import com.scoretrack.data.db.ThemeDao
import com.scoretrack.data.db.ThemeSettingEntity
import com.scoretrack.data.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ThemeState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val presetId: String = DEFAULT_PRESET,
    val primaryHex: String = DEFAULT_HEX,
    val customHex: String = DEFAULT_HEX,
    val useCustom: Boolean = false,
) {
    val effectiveHex: String get() = if (useCustom) customHex else primaryHex

    companion object {
        const val DEFAULT_PRESET = "default_blue"
        const val DEFAULT_HEX = "#2B5F9E"
    }
}

/**
 * 主题持久化：唯一数据源是 Room 的 theme_settings 表（单行），
 * 同时把主题模式镜像写入旧的 app_settings.theme_mode，保证旧数据/旧逻辑继续可读。
 */
class ThemeRepository(private val dao: ThemeDao) {

    fun observe(): Flow<ThemeState> = dao.observe().map { it?.toState() ?: ThemeState() }

    suspend fun current(): ThemeState = dao.get()?.toState() ?: ThemeState().also { save(it) }

    suspend fun ensureDefault() {
        if (dao.get() == null) save(ThemeState())
    }

    suspend fun setMode(mode: ThemeMode) = update { it.copy(mode = mode) }

    suspend fun setPreset(presetId: String, hex: String) =
        update { it.copy(presetId = presetId, primaryHex = hex, useCustom = false) }

    suspend fun setCustom(hex: String) =
        update { it.copy(presetId = CUSTOM_PRESET, customHex = hex, useCustom = true) }

    suspend fun resetToDefault() = update { ThemeState() }

    private suspend fun update(transform: (ThemeState) -> ThemeState) {
        save(transform(dao.get()?.toState() ?: ThemeState()))
    }

    private suspend fun save(state: ThemeState) {
        dao.upsert(
            ThemeSettingEntity(
                id = 1,
                themeMode = state.mode.name,
                presetId = state.presetId,
                primaryColorHex = state.primaryHex,
                customColorHex = state.customHex,
                useCustom = state.useCustom,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun ThemeSettingEntity.toState(): ThemeState = ThemeState(
        mode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
        presetId = presetId,
        primaryHex = primaryColorHex,
        customHex = customColorHex,
        useCustom = useCustom,
    )

    companion object {
        const val CUSTOM_PRESET = "custom"
    }
}
