package com.scoretrack.data.settings

import com.scoretrack.data.db.AppSettingEntity
import com.scoretrack.data.db.SettingsDao
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val storage: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.storage == value } ?: SYSTEM
    }
}

/** 不含密钥明文；hasApiKey 表示 Keystore 中已有可用密文 */
data class AiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val hasApiKey: Boolean = false,
    val keyPreview: String = "",
) {
    val endpoint: String
        get() = buildEndpoint(baseUrl)

    val displayHost: String
        get() {
            val url = baseUrl.trim()
            return url.removePrefix("https://").removePrefix("http://").trimEnd('/').substringBefore('/')
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}

fun buildEndpoint(baseUrl: String): String {
    var url = baseUrl.trim().ifBlank { AiConfig.DEFAULT_BASE_URL }
    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
    url = url.trimEnd('/')
    return when {
        url.endsWith("/chat/completions") -> url
        url.endsWith("/v1") || url.endsWith("/v1/") -> "$url/chat/completions"
        else -> "$url/chat/completions"
    }
}

data class SettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val aiConfig: AiConfig = AiConfig(),
    val onboarded: Boolean = false,
    val aiPrivacyAccepted: Boolean = false,
    val lastBackupAt: Long? = null,
)

class SettingsRepository(
    private val dao: SettingsDao,
    private val secretStore: SecretStore,
) {
    fun observe(): Flow<SettingsSnapshot> = dao.observeAll().map { list ->
        val map = list.associate { it.key to it.value }
        SettingsSnapshot(
            themeMode = ThemeMode.from(map[KEY_THEME]),
            aiConfig = AiConfig(
                baseUrl = map[KEY_AI_BASE_URL] ?: AiConfig.DEFAULT_BASE_URL,
                model = map[KEY_AI_MODEL] ?: AiConfig.DEFAULT_MODEL,
                temperature = map[KEY_AI_TEMPERATURE]?.toDoubleOrNull() ?: 0.7,
                maxTokens = map[KEY_AI_MAX_TOKENS]?.toIntOrNull() ?: 2048,
                hasApiKey = !map[KEY_AI_KEY_CIPHER].isNullOrBlank(),
                keyPreview = keyPreview(map[KEY_AI_KEY_CIPHER]),
            ),
            onboarded = map[KEY_ONBOARDED] == "1",
            aiPrivacyAccepted = map[KEY_AI_PRIVACY] == "1",
            lastBackupAt = map[KEY_LAST_BACKUP]?.toLongOrNull(),
        )
    }

    private fun keyPreview(cipher: String?): String {
        if (cipher.isNullOrBlank()) return ""
        val plain = secretStore.decrypt(cipher) ?: return "已保存（无法解密）"
        return if (plain.length <= 8) {
            "*".repeat(plain.length)
        } else {
            plain.take(4) + "*".repeat((plain.length - 8).coerceAtLeast(4)) + plain.takeLast(4)
        }
    }

    suspend fun snapshot(): SettingsSnapshot {
        val map = dao.all().associate { it.key to it.value }
        return SettingsSnapshot(
            themeMode = ThemeMode.from(map[KEY_THEME]),
            aiConfig = AiConfig(
                baseUrl = map[KEY_AI_BASE_URL] ?: AiConfig.DEFAULT_BASE_URL,
                model = map[KEY_AI_MODEL] ?: AiConfig.DEFAULT_MODEL,
                temperature = map[KEY_AI_TEMPERATURE]?.toDoubleOrNull() ?: 0.7,
                maxTokens = map[KEY_AI_MAX_TOKENS]?.toIntOrNull() ?: 2048,
                hasApiKey = !map[KEY_AI_KEY_CIPHER].isNullOrBlank(),
                keyPreview = keyPreview(map[KEY_AI_KEY_CIPHER]),
            ),
            onboarded = map[KEY_ONBOARDED] == "1",
            aiPrivacyAccepted = map[KEY_AI_PRIVACY] == "1",
            lastBackupAt = map[KEY_LAST_BACKUP]?.toLongOrNull(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = put(KEY_THEME, mode.storage)

    suspend fun setOnboarded() = put(KEY_ONBOARDED, "1")

    suspend fun setAiPrivacyAccepted() = put(KEY_AI_PRIVACY, "1")

    suspend fun setLastBackupAt(millis: Long) = put(KEY_LAST_BACKUP, millis.toString())

    /**
     * 保存 AI 配置。apiKey 为 null 表示保持原有密钥不变；空字符串表示清除密钥。
     */
    suspend fun saveAiConfig(
        baseUrl: String,
        model: String,
        temperature: Double,
        maxTokens: Int,
        apiKey: String?,
    ) {
        put(KEY_AI_BASE_URL, baseUrl.trim().ifBlank { AiConfig.DEFAULT_BASE_URL })
        put(KEY_AI_MODEL, model.trim().ifBlank { AiConfig.DEFAULT_MODEL })
        put(KEY_AI_TEMPERATURE, String.format(Locale.US, "%.2f", temperature.coerceIn(0.0, 2.0)))
        put(KEY_AI_MAX_TOKENS, maxTokens.coerceIn(64, 32768).toString())
        when {
            apiKey == null -> Unit
            apiKey.isBlank() -> clearApiKey()
            else -> put(KEY_AI_KEY_CIPHER, secretStore.encrypt(apiKey.trim()))
        }
    }

    suspend fun clearApiKey() {
        dao.remove(KEY_AI_KEY_CIPHER)
        secretStore.deleteKey()
    }

    suspend fun clearAiConfig() {
        listOf(KEY_AI_BASE_URL, KEY_AI_MODEL, KEY_AI_TEMPERATURE, KEY_AI_MAX_TOKENS, KEY_AI_KEY_CIPHER)
            .forEach { dao.remove(it) }
        secretStore.deleteKey()
    }

    /** 只给 AI 客户端使用，绝不写入日志 */
    suspend fun apiKey(): String? {
        val cipher = dao.get(KEY_AI_KEY_CIPHER) ?: return null
        return secretStore.decrypt(cipher)
    }

    suspend fun allSettings(): List<AppSettingEntity> = dao.all()

    suspend fun putAll(settings: List<AppSettingEntity>) = dao.putAll(settings)

    suspend fun getRaw(key: String): String? = dao.get(key)

    suspend fun putRaw(key: String, value: String) = put(key, value)

    private suspend fun put(key: String, value: String) = dao.put(AppSettingEntity(key, value))

    companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_AI_BASE_URL = "ai.base_url"
        const val KEY_AI_MODEL = "ai.model"
        const val KEY_AI_TEMPERATURE = "ai.temperature"
        const val KEY_AI_MAX_TOKENS = "ai.max_tokens"
        const val KEY_AI_KEY_CIPHER = "ai.api_key.cipher"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_AI_PRIVACY = "ai.privacy_accepted"
        const val KEY_LAST_BACKUP = "last_backup_at"
    }
}
