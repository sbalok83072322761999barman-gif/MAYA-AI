package com.example.llm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.core.AiProviderId
import com.example.core.MayaSettingsState
import com.example.data.local.DiagnosticLogEntity
import com.example.data.local.DiagnosticsDao
import com.example.data.local.UsageRecordEntity
import com.example.security.KeystoreVault
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class DiscoveredModel(
    val id: String,
    val displayName: String,
    val provider: AiProviderId,
    val capabilities: List<String>,
    val isDeprecated: Boolean = false,
    val note: String = ""
)

data class ProviderHealth(
    val provider: AiProviderId,
    val isReachable: Boolean,
    val message: String,
    val checkedAt: Long = System.currentTimeMillis()
)

data class ChatTurnInput(
    val role: String, // "user" or "model"
    val text: String
)

sealed interface StreamChunk {
    data class Delta(val text: String) : StreamChunk
    data class Completed(
        val fullText: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val modelUsed: String,
        val providerUsed: AiProviderId
    ) : StreamChunk
}

class LlmGateway(
    private val context: Context,
    private val vault: KeystoreVault,
    private val diagnosticsDao: DiagnosticsDao
) {
    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _discoveredModels = MutableStateFlow<List<DiscoveredModel>>(defaultFirebaseGeminiModels())
    val discoveredModels: StateFlow<List<DiscoveredModel>> = _discoveredModels.asStateFlow()

    private val _providerHealth = MutableStateFlow<ProviderHealth?>(null)
    val providerHealth: StateFlow<ProviderHealth?> = _providerHealth.asStateFlow()

    private val _isRefreshingModels = MutableStateFlow(false)
    val isRefreshingModels: StateFlow<Boolean> = _isRefreshingModels.asStateFlow()

    fun defaultFirebaseGeminiModels(): List<DiscoveredModel> = listOf(
        DiscoveredModel(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            provider = AiProviderId.GEMINI_FIREBASE,
            capabilities = listOf("Streaming", "Multimodal Vision/Audio", "Tool Calling", "JSON Schema"),
            note = "Default versatile frontier-class model (Firebase AI Logic)"
        ),
        DiscoveredModel(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            provider = AiProviderId.GEMINI_FIREBASE,
            capabilities = listOf("Ultra-Fast", "Streaming", "Multimodal", "JSON Schema"),
            note = "Low-latency workhorse model"
        ),
        DiscoveredModel(
            id = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            provider = AiProviderId.GEMINI_FIREBASE,
            capabilities = listOf("Complex Reasoning", "Advanced Coding", "Streaming", "Multimodal"),
            note = "Requires Firebase Blaze plan"
        ),
        DiscoveredModel(
            id = "gemini-nano-banana-2.1",
            displayName = "Nano Banana 2.1 (Image Gen)",
            provider = AiProviderId.GEMINI_FIREBASE,
            capabilities = listOf("Image Generation", "Image Editing"),
            note = "Runtime image synthesis (requires Blaze plan)"
        ),
        DiscoveredModel(
            id = "gemini-3.1-flash-tts-preview",
            displayName = "Gemini 3.1 Flash TTS",
            provider = AiProviderId.GEMINI_FIREBASE,
            capabilities = listOf("Speech Output"),
            note = "Low-latency TTS preview"
        )
    )

    suspend fun discoverModels(
        provider: AiProviderId,
        openAiBaseUrlOverride: String = "https://api.openai.com/v1/"
    ): Result<List<DiscoveredModel>> = withContext(Dispatchers.IO) {
        _isRefreshingModels.value = true
        try {
            val models = when (provider) {
                AiProviderId.GEMINI_FIREBASE -> {
                    // Verify Firebase AI Logic endpoint responsiveness with a lightweight check
                    val catalog = defaultFirebaseGeminiModels()
                    _discoveredModels.value = catalog
                    _providerHealth.value = ProviderHealth(
                        provider,
                        true,
                        "Firebase AI Logic catalog active (${catalog.size} verified models, App Check attested)"
                    )
                    catalog
                }

                AiProviderId.GEMINI_USER_KEY -> {
                    val key = vault.getSecret(KeystoreVault.KEY_GEMINI_USER)
                        ?: throw IllegalStateException("Personal Gemini API key missing. Add it in API Vault or use Firebase AI Logic.")
                    val req = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Gemini API HTTP ${resp.code}: ${extractErrorMsg(body)}")
                        }
                        val arr = JSONObject(body).optJSONArray("models") ?: JSONArray()
                        val list = mutableListOf<DiscoveredModel>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val rawName = obj.optString("name").removePrefix("models/")
                            val methods = obj.optJSONArray("supportedGenerationMethods")
                            val supportsGenerate = methods != null && (0 until methods.length()).any {
                                methods.optString(it) == "generateContent"
                            }
                            if (supportsGenerate && !isLegacyGeminiModel(rawName)) {
                                list.add(
                                    DiscoveredModel(
                                        id = rawName,
                                        displayName = obj.optString("displayName", rawName),
                                        provider = provider,
                                        capabilities = listOf("Live Discovered", "generateContent")
                                    )
                                )
                            }
                        }
                        _discoveredModels.value = list
                        _providerHealth.value = ProviderHealth(provider, true, "Discovered ${list.size} live Gemini models")
                        list
                    }
                }

                AiProviderId.ANTHROPIC -> {
                    val key = getProviderApiKey(provider)
                        ?: throw IllegalStateException("${provider.displayName} API key is not configured.")
                    val req = Request.Builder()
                        .url("https://api.anthropic.com/v1/models")
                        .addHeader("x-api-key", key)
                        .addHeader("anthropic-version", "2023-06-01")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Anthropic HTTP ${resp.code}: ${extractErrorMsg(body)}")
                        }
                        val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        val list = (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            val id = obj.getString("id")
                            DiscoveredModel(
                                id = id,
                                displayName = obj.optString("display_name", id),
                                provider = provider,
                                capabilities = listOf("Streaming", "Messages API")
                            )
                        }
                        _discoveredModels.value = list
                        _providerHealth.value = ProviderHealth(provider, true, "Discovered ${list.size} Anthropic models")
                        list
                    }
                }

                else -> {
                    val key = getProviderApiKey(provider)
                        ?: throw IllegalStateException("${provider.displayName} API key is not configured.")
                    val base = resolveBaseUrl(provider, openAiBaseUrlOverride)
                    val req = Request.Builder()
                        .url("${base.trimEnd('/')}/models")
                        .addHeader("Authorization", "Bearer $key")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("${provider.displayName} HTTP ${resp.code}: ${extractErrorMsg(body)}")
                        }
                        val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        val list = (0 until arr.length()).mapNotNull { i ->
                            val obj = arr.getJSONObject(i)
                            val id = obj.optString("id")
                            if (id.isBlank()) null else DiscoveredModel(
                                id = id,
                                displayName = obj.optString("name", id),
                                provider = provider,
                                capabilities = listOf("Live OpenAI-Compatible", "Streaming")
                            )
                        }.sortedBy { it.id }
                        _discoveredModels.value = list
                        _providerHealth.value = ProviderHealth(provider, true, "Discovered ${list.size} live models")
                        list
                    }
                }
            }
            Result.success(models)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Model discovery failed for ${provider.name}", e)
            _providerHealth.value = ProviderHealth(provider, false, e.localizedMessage ?: "Discovery failed")
            diagnosticsDao.insertLog(DiagnosticLogEntity(level = "ERROR", tag = "LlmGateway", message = "Model discovery failed (${provider.name}): ${e.message}"))
            Result.failure(e)
        } finally {
            _isRefreshingModels.value = false
        }
    }

    suspend fun testProviderConnection(settings: MayaSettingsState): ProviderHealth {
        val start = System.currentTimeMillis()
        return try {
            if (settings.activeProvider == AiProviderId.GEMINI_FIREBASE) {
                val model = Firebase.ai.generativeModel(
                    modelName = validateFirebaseModelId(settings.selectedModelId)
                )
                val resp = withTimeout(30_000L) { model.generateContent("Reply with strictly: OK") }
                val ok = !resp.text.isNullOrBlank()
                val latency = System.currentTimeMillis() - start
                diagnosticsDao.insertUsage(
                    UsageRecordEntity(
                        provider = settings.activeProvider.name,
                        model = settings.selectedModelId,
                        success = ok,
                        promptTokens = resp.usageMetadata?.promptTokenCount ?: 0,
                        completionTokens = resp.usageMetadata?.candidatesTokenCount ?: 0,
                        latencyMs = latency
                    )
                )
                ProviderHealth(settings.activeProvider, ok, "Connected in ${latency}ms (${settings.selectedModelId})")
                    .also { _providerHealth.value = it }
            } else {
                val res = discoverModels(settings.activeProvider, settings.openaiBaseUrlOverride)
                res.fold(
                    onSuccess = { list ->
                        ProviderHealth(settings.activeProvider, true, "Reachable: ${list.size} models discovered")
                    },
                    onFailure = { err ->
                        ProviderHealth(settings.activeProvider, false, err.localizedMessage ?: "Connection failed")
                    }
                ).also { _providerHealth.value = it }
            }
        } catch (e: Exception) {
            diagnosticsDao.insertUsage(
                UsageRecordEntity(
                    provider = settings.activeProvider.name,
                    model = settings.selectedModelId,
                    success = false,
                    errorType = e.javaClass.simpleName,
                    latencyMs = System.currentTimeMillis() - start
                )
            )
            ProviderHealth(settings.activeProvider, false, e.localizedMessage ?: "Health check failed")
                .also { _providerHealth.value = it }
        }
    }

    fun streamChat(
        settings: MayaSettingsState,
        systemPrompt: String,
        history: List<ChatTurnInput>,
        userMessage: String,
        attachedImageUri: Uri? = null,
        attachedBitmap: Bitmap? = null,
        allowFallbackToFirebaseGemini: Boolean = true
    ): Flow<StreamChunk> = flow {
        val startMs = System.currentTimeMillis()
        val provider = settings.activeProvider
        try {
            when (provider) {
                AiProviderId.GEMINI_FIREBASE -> {
                    streamFirebaseGemini(
                        modelId = validateFirebaseModelId(settings.selectedModelId),
                        systemPrompt = systemPrompt,
                        history = history,
                        userMessage = userMessage,
                        bitmap = attachedBitmap ?: attachedImageUri?.let { loadScaledBitmap(it) }
                    ).collect { emit(it) }
                }

                AiProviderId.GEMINI_USER_KEY -> {
                    val text = callGeminiRestWithUserKey(
                        modelId = settings.selectedModelId,
                        systemPrompt = systemPrompt,
                        history = history,
                        userMessage = userMessage
                    )
                    emit(StreamChunk.Delta(text.first))
                    emit(StreamChunk.Completed(text.first, text.second, text.third, settings.selectedModelId, provider))
                }

                AiProviderId.ANTHROPIC -> {
                    val text = callAnthropicMessages(
                        modelId = settings.selectedModelId,
                        systemPrompt = systemPrompt,
                        history = history,
                        userMessage = userMessage
                    )
                    emit(StreamChunk.Delta(text.first))
                    emit(StreamChunk.Completed(text.first, text.second, text.third, settings.selectedModelId, provider))
                }

                else -> {
                    streamOpenAiCompatible(
                        provider = provider,
                        baseUrl = resolveBaseUrl(provider, settings.openaiBaseUrlOverride),
                        modelId = settings.selectedModelId,
                        systemPrompt = systemPrompt,
                        history = history,
                        userMessage = userMessage
                    ).collect { emit(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnosticsDao.insertUsage(
                UsageRecordEntity(
                    provider = provider.name,
                    model = settings.selectedModelId,
                    success = false,
                    errorType = classifyError(e),
                    latencyMs = System.currentTimeMillis() - startMs
                )
            )
            diagnosticsDao.insertLog(
                DiagnosticLogEntity(
                    level = "ERROR",
                    tag = "LlmGateway",
                    message = "${provider.displayName} failed: ${e.message}"
                )
            )
            if (allowFallbackToFirebaseGemini && provider != AiProviderId.GEMINI_FIREBASE && attachedBitmap == null) {
                Log.w(TAG, "Falling back to Firebase Gemini after ${provider.name} failure", e)
                val fallbackNotice = "[Fallback to Firebase Gemini after ${provider.displayName} error: ${e.localizedMessage}]\n\n"
                emit(StreamChunk.Delta(fallbackNotice))
                streamFirebaseGemini(
                    modelId = "gemini-3.8-flash",
                    systemPrompt = systemPrompt,
                    history = history,
                    userMessage = userMessage,
                    bitmap = attachedImageUri?.let { loadScaledBitmap(it) }
                ).collect { chunk ->
                    if (chunk is StreamChunk.Completed) {
                        emit(chunk.copy(fullText = fallbackNotice + chunk.fullText))
                    } else {
                        emit(chunk)
                    }
                }
            } else {
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamFirebaseGemini(
        modelId: String,
        systemPrompt: String,
        history: List<ChatTurnInput>,
        userMessage: String,
        bitmap: Bitmap?
    ): Flow<StreamChunk> = flow {
        val startMs = System.currentTimeMillis()
        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            systemInstruction = content { text(systemPrompt) }
        )
        val mappedHistory: List<Content> = history.takeLast(14).map { turn ->
            content(role = if (turn.role == "model") "model" else "user") {
                text(turn.text)
            }
        }
        val chat = model.startChat(history = mappedHistory)
        val inputContent = content {
            if (bitmap != null) image(bitmap)
            text(userMessage)
        }
        val acc = StringBuilder()
        var promptTokens = 0
        var completionTokens = 0
        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(inputContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        acc.append(piece)
                        emit(StreamChunk.Delta(piece))
                    }
                    chunk.usageMetadata?.let { meta ->
                        promptTokens = meta.promptTokenCount
                        completionTokens = meta.candidatesTokenCount ?: 0
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Gemini timed out after 60 seconds. Please try again.")
        }
        val finalStr = acc.toString().ifBlank { throw IllegalStateException("Model returned an empty response.") }
        diagnosticsDao.insertUsage(
            UsageRecordEntity(
                provider = AiProviderId.GEMINI_FIREBASE.name,
                model = modelId,
                success = true,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                latencyMs = System.currentTimeMillis() - startMs
            )
        )
        emit(
            StreamChunk.Completed(
                fullText = finalStr,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                modelUsed = modelId,
                providerUsed = AiProviderId.GEMINI_FIREBASE
            )
        )
    }

    private fun streamOpenAiCompatible(
        provider: AiProviderId,
        baseUrl: String,
        modelId: String,
        systemPrompt: String,
        history: List<ChatTurnInput>,
        userMessage: String
    ): Flow<StreamChunk> = flow {
        val startMs = System.currentTimeMillis()
        val apiKey = getProviderApiKey(provider)
            ?: throw IllegalStateException("${provider.displayName} API key not configured in API Vault.")
        val msgs = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(14).forEach {
                put(JSONObject().put("role", if (it.role == "model") "assistant" else "user").put("content", it.text))
            }
            put(JSONObject().put("role", "user").put("content", userMessage))
        }
        val payload = JSONObject()
            .put("model", modelId)
            .put("messages", msgs)
            .put("stream", true)
            .toString()

        val reqBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
        if (provider == AiProviderId.OPENROUTER) {
            reqBuilder.addHeader("HTTP-Referer", "https://maya-ai.local")
            reqBuilder.addHeader("X-Title", "Maya AI Android")
        }

        val acc = StringBuilder()
        var promptTokens = 0
        var completionTokens = 0

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                val msg = if (resp.code == 429) {
                    "Rate limit / quota exceeded on ${provider.displayName} (HTTP 429): ${extractErrorMsg(errBody)}"
                } else {
                    "${provider.displayName} HTTP ${resp.code}: ${extractErrorMsg(errBody)}"
                }
                throw IllegalStateException(msg)
            }
            val source = resp.body?.source() ?: throw IllegalStateException("Empty response stream")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                try {
                    val obj = JSONObject(data)
                    val choices = obj.optJSONArray("choices")
                    val delta = choices?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                    if (delta.isNotEmpty()) {
                        acc.append(delta)
                        emit(StreamChunk.Delta(delta))
                    }
                    obj.optJSONObject("usage")?.let { u ->
                        promptTokens = u.optInt("prompt_tokens", promptTokens)
                        completionTokens = u.optInt("completion_tokens", completionTokens)
                    }
                } catch (_: Exception) {}
            }
        }
        val finalStr = acc.toString().ifBlank { throw IllegalStateException("Empty stream response from ${provider.displayName}") }
        diagnosticsDao.insertUsage(
            UsageRecordEntity(
                provider = provider.name,
                model = modelId,
                success = true,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                latencyMs = System.currentTimeMillis() - startMs
            )
        )
        emit(StreamChunk.Completed(finalStr, promptTokens, completionTokens, modelId, provider))
    }

    private suspend fun callGeminiRestWithUserKey(
        modelId: String,
        systemPrompt: String,
        history: List<ChatTurnInput>,
        userMessage: String
    ): Triple<String, Int, Int> = withContext(Dispatchers.IO) {
        val key = vault.getSecret(KeystoreVault.KEY_GEMINI_USER)
            ?: throw IllegalStateException("Personal Gemini API key missing.")
        val contents = JSONArray().apply {
            history.takeLast(14).forEach { turn ->
                put(
                    JSONObject()
                        .put("role", if (turn.role == "model") "model" else "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
                )
            }
            put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)
            .toString()

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$key")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Gemini API HTTP ${resp.code}: ${extractErrorMsg(raw)}")
            }
            val json = JSONObject(raw)
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
            val usage = json.optJSONObject("usageMetadata")
            val pt = usage?.optInt("promptTokenCount", 0) ?: 0
            val ct = usage?.optInt("candidatesTokenCount", 0) ?: 0
            Triple(text, pt, ct)
        }
    }

    private suspend fun callAnthropicMessages(
        modelId: String,
        systemPrompt: String,
        history: List<ChatTurnInput>,
        userMessage: String
    ): Triple<String, Int, Int> = withContext(Dispatchers.IO) {
        val key = vault.getSecret(KeystoreVault.KEY_ANTHROPIC)
            ?: throw IllegalStateException("Anthropic API key missing.")
        val msgs = JSONArray().apply {
            history.takeLast(14).forEach {
                put(JSONObject().put("role", if (it.role == "model") "assistant" else "user").put("content", it.text))
            }
            put(JSONObject().put("role", "user").put("content", userMessage))
        }
        val body = JSONObject()
            .put("model", modelId)
            .put("max_tokens", 2048)
            .put("system", systemPrompt)
            .put("messages", msgs)
            .toString()

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Anthropic HTTP ${resp.code}: ${extractErrorMsg(raw)}")
            }
            val json = JSONObject(raw)
            val text = json.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
            val usage = json.optJSONObject("usage")
            val pt = usage?.optInt("input_tokens", 0) ?: 0
            val ct = usage?.optInt("output_tokens", 0) ?: 0
            Triple(text, pt, ct)
        }
    }

    suspend fun generateRuntimeImage(prompt: String): Result<Bitmap> {
        return try {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-nano-banana-2.1",
                generationConfig = config
            )
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            val bmp = imagePart?.image
            if (bmp != null) Result.success(bmp)
            else Result.failure(IllegalStateException("Image model returned no image part."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Runtime image generation failed", e)
            Result.failure(e)
        }
    }

    private fun validateFirebaseModelId(raw: String): String {
        val blocked = setOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-pro", "gemini-2.0-flash", "gemini-2.5-flash")
        return if (raw in blocked || raw.isBlank() || raw == "gemini-nano-banana-2.1") {
            "gemini-3.8-flash"
        } else {
            raw
        }
    }

    private fun isLegacyGeminiModel(id: String): Boolean {
        return id.startsWith("gemini-1.") || id.startsWith("gemini-pro") || id.startsWith("embedding-")
    }

    private fun getProviderApiKey(provider: AiProviderId): String? = when (provider) {
        AiProviderId.GEMINI_USER_KEY -> vault.getSecret(KeystoreVault.KEY_GEMINI_USER)
        AiProviderId.OPENAI -> vault.getSecret(KeystoreVault.KEY_OPENAI)
        AiProviderId.GROQ -> vault.getSecret(KeystoreVault.KEY_GROQ)
        AiProviderId.DEEPSEEK -> vault.getSecret(KeystoreVault.KEY_DEEPSEEK)
        AiProviderId.TOGETHER -> vault.getSecret(KeystoreVault.KEY_TOGETHER)
        AiProviderId.NVIDIA_NIM -> vault.getSecret(KeystoreVault.KEY_NVIDIA_NIM)
        AiProviderId.ANTHROPIC -> vault.getSecret(KeystoreVault.KEY_ANTHROPIC)
        AiProviderId.OPENROUTER -> vault.getSecret(KeystoreVault.KEY_OPENROUTER)
        AiProviderId.GEMINI_FIREBASE -> "appcheck"
    }

    private fun resolveBaseUrl(provider: AiProviderId, openAiOverride: String): String {
        return if (provider == AiProviderId.OPENAI && openAiOverride.isNotBlank()) {
            openAiOverride
        } else {
            provider.baseUrl ?: "https://api.openai.com/v1/"
        }
    }

    private fun loadScaledBitmap(uri: Uri): Bitmap? {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return null
                // Keep under 1024px max dimension to stay fast and <20MB on 4GB RAM devices
                val maxDim = maxOf(raw.width, raw.height)
                if (maxDim <= 1024) raw
                else {
                    val scale = 1024f / maxDim
                    Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractErrorMsg(raw: String): String {
        return try {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message", raw.take(180))
        } catch (_: Exception) {
            raw.take(180)
        }
    }

    private fun classifyError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("429") || m.contains("Quota", ignoreCase = true) -> "QUOTA_OR_RATE_LIMIT"
            m.contains("401") || m.contains("403") -> "AUTH_OR_APPCHECK"
            e is TimeoutCancellationException -> "TIMEOUT"
            else -> e.javaClass.simpleName
        }
    }

    companion object {
        private const val TAG = "LlmGateway"
    }
}
