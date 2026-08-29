package com.joe.mepe.ai

import com.joe.mepe.data.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

/**
 * AI 调用（与桌面端 LlmService 一致）：
 * apiFormat 0=OpenAI 兼容 /chat/completions（DeepSeek / 通义 / 智谱等）
 * apiFormat 1=Anthropic /v1/messages（Claude 系列）
 */
object LlmService {

    private val client = OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(120)).build()

    private val json = Json { ignoreUnknownKeys = true }

    private const val SYSTEM_PROMPT = "你是一位专业的健康与目标管理分析师，请用中文回答。"

    /** 返回 AI 回复文本 */
    suspend fun chat(provider: AiProvider, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (provider.apiFormat == 1) chatAnthropic(provider, prompt) else chatOpenAI(provider, prompt)
    }

    /** OpenAI 兼容：POST {base}/v1/chat/completions */
    private fun chatOpenAI(provider: AiProvider, prompt: String): Result<String> = runCatching {
        val bodyJson = buildJsonObject {
            put("model", provider.model)
            put("messages", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val url = provider.baseUrl.trimEnd('/') + "/v1/chat/completions"
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${provider.encryptedApiKey ?: ""}")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string()?.take(300)}")
            val text = resp.body?.string() ?: error("空响应")
            val root = json.parseToJsonElement(text).jsonObject
            root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: error("响应中没有回复内容")
        }
    }

    /** Anthropic：POST {base}/v1/messages，key 走 x-api-key 头 */
    private fun chatAnthropic(provider: AiProvider, prompt: String): Result<String> = runCatching {
        val bodyJson = buildJsonObject {
            put("model", provider.model)
            put("max_tokens", 4096)
            put("system", SYSTEM_PROMPT)
            put("messages", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val base = provider.baseUrl.trimEnd('/').removeSuffix("/v1")
        val url = base + "/v1/messages"
        val req = Request.Builder().url(url)
            .header("x-api-key", provider.encryptedApiKey ?: "")
            .header("anthropic-version", "2023-06-01")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string()?.take(300)}")
            val text = resp.body?.string() ?: error("空响应")
            val root = json.parseToJsonElement(text).jsonObject
            // content 为块数组：[{type:"text", text:"..."}]，取全部 text 拼接
            root["content"]?.jsonArray
                ?.mapNotNull { b -> b.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }
                ?: error("响应中没有回复内容")
        }
    }
}
