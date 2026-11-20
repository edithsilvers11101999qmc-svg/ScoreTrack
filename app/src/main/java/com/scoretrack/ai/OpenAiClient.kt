package com.scoretrack.ai

import com.scoretrack.data.settings.AiConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class AiErrorType(val title: String) {
    NO_KEY("未配置 API Key"),
    INVALID_URL("API 地址无效"),
    UNAUTHORIZED("密钥错误或无权限"),
    MODEL_NOT_FOUND("模型不可用"),
    BAD_REQUEST("请求参数被拒绝"),
    RATE_LIMIT("请求频率或额度受限"),
    SERVER_ERROR("服务端错误"),
    NETWORK("网络错误"),
    TIMEOUT("请求超时"),
    INVALID_RESPONSE("响应格式不兼容"),
    EMPTY_RESPONSE("模型返回为空"),
    UNKNOWN("未知错误"),
}

class AiException(
    val type: AiErrorType,
    override val message: String,
    val httpCode: Int? = null,
    val detail: String? = null,
) : Exception(message)

data class ChatMessage(val role: String, val content: String)

data class ChatResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val finishReason: String?,
)

data class ConnectionCheck(
    val ok: Boolean,
    val type: AiErrorType?,
    val message: String,
    val latencyMs: Long,
    val endpoint: String,
)

/**
 * OpenAI 兼容的 Chat Completions 客户端。Base URL、模型、Temperature、最大 Token 全部来自用户设置。
 * 不在任何日志中输出 API Key 或请求头。
 */
class OpenAiClient {

    suspend fun chat(
        config: AiConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        temperature: Double = config.temperature,
        maxTokens: Int = config.maxTokens,
        timeoutMs: Int = 120_000,
    ): ChatResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", config.model.ifBlank { AiConfig.DEFAULT_MODEL })
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
            })
            put("temperature", temperature.coerceIn(0.0, 2.0))
            put("max_tokens", maxTokens.coerceIn(16, 32768))
            put("stream", false)
        }.toString()

        val response = execute(config.endpoint, apiKey, body, timeoutMs)
        parseChatResponse(response.body)
    }

    suspend fun testConnection(
        config: AiConfig,
        apiKey: String,
        timeoutMs: Int = 30_000,
    ): ConnectionCheck = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val response = chat(
                config = config,
                apiKey = apiKey,
                messages = listOf(
                    ChatMessage("system", "你是连接测试助手。"),
                    ChatMessage("user", "请只回复 OK 两个字符。"),
                ),
                temperature = 0.0,
                maxTokens = 24,
                timeoutMs = timeoutMs,
            )
            ConnectionCheck(
                ok = true,
                type = null,
                message = "连接成功，模型返回：${response.content.take(40).ifBlank { "(空)" }}",
                latencyMs = System.currentTimeMillis() - started,
                endpoint = config.endpoint,
            )
        } catch (e: AiException) {
            ConnectionCheck(
                ok = false,
                type = e.type,
                message = buildString {
                    append(e.type.title)
                    append("：")
                    append(e.message)
                    e.httpCode?.let { append("（HTTP ").append(it).append("）") }
                },
                latencyMs = System.currentTimeMillis() - started,
                endpoint = config.endpoint,
            )
        }
    }

    private data class RawResponse(val code: Int, val body: String)

    private fun execute(url: String, apiKey: String, body: String, timeoutMs: Int): RawResponse {
        val parsedUrl = try {
            URL(url)
        } catch (e: Exception) {
            throw AiException(AiErrorType.INVALID_URL, "无法解析 API 地址：$url")
        }
        if (parsedUrl.protocol != "http" && parsedUrl.protocol != "https") {
            throw AiException(AiErrorType.INVALID_URL, "API 地址必须以 http:// 或 https:// 开头")
        }

        val connection = try {
            (parsedUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("User-Agent", "ScoreTrack/2.0 (Android)")
            }
        } catch (e: Exception) {
            throw AiException(AiErrorType.NETWORK, "无法创建网络连接：${e.message}")
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: ""
            if (code !in 200..299) throw mapHttpError(code, text)
            RawResponse(code, text)
        } catch (e: AiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiException(AiErrorType.TIMEOUT, "请求超时（${timeoutMs / 1000} 秒），请检查网络或稍后重试")
        } catch (e: UnknownHostException) {
            throw AiException(AiErrorType.NETWORK, "无法解析域名：${parsedUrl.host}")
        } catch (e: javax.net.ssl.SSLException) {
            throw AiException(AiErrorType.NETWORK, "TLS 握手失败：${e.message}")
        } catch (e: Exception) {
            throw AiException(AiErrorType.NETWORK, "网络请求失败：${e.javaClass.simpleName} ${e.message ?: ""}")
        } finally {
            connection.disconnect()
        }
    }

    private fun mapHttpError(code: Int, body: String): AiException {
        val detail = extractErrorMessage(body)
        val type = when (code) {
            401, 403 -> AiErrorType.UNAUTHORIZED
            404 -> AiErrorType.MODEL_NOT_FOUND
            400, 422 -> AiErrorType.BAD_REQUEST
            429 -> AiErrorType.RATE_LIMIT
            in 500..599 -> AiErrorType.SERVER_ERROR
            else -> AiErrorType.UNKNOWN
        }
        val friendly = when (type) {
            AiErrorType.UNAUTHORIZED -> "API Key 无效或没有访问该模型的权限"
            AiErrorType.MODEL_NOT_FOUND -> "接口不存在或模型名称不可用，请检查 Base URL 与 Model"
            AiErrorType.BAD_REQUEST -> "请求被服务端拒绝"
            AiErrorType.RATE_LIMIT -> "请求过于频繁或额度不足"
            AiErrorType.SERVER_ERROR -> "服务端返回错误"
            else -> "请求失败"
        }
        return AiException(type, "$friendly${detail?.let { "；$it" } ?: ""}", code, body.take(800))
    }

    private fun extractErrorMessage(body: String): String? = runCatching {
        val json = JSONObject(body)
        when {
            json.has("error") -> {
                val error = json.get("error")
                when (error) {
                    is JSONObject -> error.optString("message").takeIf { it.isNotBlank() }
                    is String -> error
                    else -> null
                }
            }
            json.has("message") -> json.optString("message").takeIf { it.isNotBlank() }
            else -> null
        }
    }.getOrNull()

    private fun parseChatResponse(body: String): ChatResponse {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw AiException(
                AiErrorType.INVALID_RESPONSE,
                "返回内容不是 JSON，可能该地址不是 OpenAI 兼容接口",
                detail = body.take(400),
            )
        }
        if (json.has("error")) {
            throw AiException(AiErrorType.INVALID_RESPONSE, extractErrorMessage(body) ?: "接口返回错误")
        }
        val choices = json.optJSONArray("choices")
            ?: throw AiException(AiErrorType.INVALID_RESPONSE, "响应缺少 choices 字段，接口可能不兼容")
        if (choices.length() == 0) throw AiException(AiErrorType.EMPTY_RESPONSE, "服务端未返回任何候选结果")
        val first = choices.optJSONObject(0)
        val message = first?.optJSONObject("message")
        val content = message?.optString("content").orEmpty()
        if (content.isBlank()) {
            throw AiException(AiErrorType.EMPTY_RESPONSE, "模型返回内容为空，请调整模型或最大 Token 数后重试")
        }
        val usage = json.optJSONObject("usage")
        return ChatResponse(
            content = content.trim(),
            promptTokens = usage?.optInt("prompt_tokens")?.takeIf { it > 0 },
            completionTokens = usage?.optInt("completion_tokens")?.takeIf { it > 0 },
            finishReason = first?.optString("finish_reason")?.takeIf { it.isNotBlank() },
        )
    }
}
