package com.joe.mepe.ai

import com.joe.mepe.data.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

/** OpenAI 兼容 /chat/completions 调用（DeepSeek / 通义 / 智谱等） */
object LlmService {

    private val client = OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(120)).build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 返回 AI 回复文本 */
    suspend fun chat(provider: AiProvider, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = buildJsonObject {
                put("model", provider.model)
                put("messages", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", "你是一位专业的健康与目标管理分析师，请用中文回答。")
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
    }
}
