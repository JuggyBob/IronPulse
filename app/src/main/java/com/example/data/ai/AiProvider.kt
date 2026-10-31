package com.example.data.ai

import kotlinx.coroutines.CancellationException

/** One provider protocol adapter. Business logic and tool execution live outside this interface. */
interface AiProvider {
    val id: AiProviderId

    /** Models the app can show before (or without) a live catalog. Never presented as live. */
    fun knownModels(): List<AiModelDescriptor> = emptyList()

    /** Resolves the API root for this provider, including a validated custom endpoint. */
    fun resolveBaseUrl(config: AiProviderConfiguration): Result<String>

    suspend fun listModels(config: AiProviderConfiguration): Result<List<AiModelDescriptor>>

    /** A lightweight real generation request, not merely a catalog probe. */
    suspend fun testConnection(config: AiProviderConfiguration): Result<Unit>

    suspend fun generate(request: AiRequest): Result<AiResponse>
}

/**
 * Runs one adapter operation and normalizes failures.
 *
 * Cancellation is rethrown instead of being converted into a failure: a screen that was closed is
 * not a provider error, and turning it into `Result.failure` would let the conversation engine
 * retry a request the athlete already left. Every other throwable becomes the sanitized
 * [AiException] the UI understands.
 */
internal suspend fun <T> providerCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toAiException())
    }

/** Builds the adapter for a provider id. */
object AiProviderRegistry {
    fun provider(id: AiProviderId): AiProvider = when (id) {
        AiProviderId.GOOGLE -> GoogleProvider
        AiProviderId.ANTHROPIC -> AnthropicProvider
        AiProviderId.OPENAI -> OpenAiCompatibleProvider(
            id = AiProviderId.OPENAI,
            fixedBaseUrl = "https://api.openai.com/v1",
            extraHeaders = emptyMap()
        )
        AiProviderId.OPENROUTER -> OpenAiCompatibleProvider(
            id = AiProviderId.OPENROUTER,
            fixedBaseUrl = "https://openrouter.ai/api/v1",
            extraHeaders = mapOf(
                "HTTP-Referer" to "https://ironpulse.app",
                "X-Title" to "IronPulse"
            )
        )
        AiProviderId.XAI -> OpenAiCompatibleProvider(
            id = AiProviderId.XAI,
            fixedBaseUrl = "https://api.x.ai/v1",
            extraHeaders = emptyMap()
        )
        AiProviderId.CUSTOM_OPENAI -> CustomOpenAiProvider
    }
}

private fun requireKey(config: AiProviderConfiguration): String {
    val key = config.apiKey.trim()
    if (key.isBlank()) {
        throw AiException(
            AiErrorKind.CONFIGURATION,
            "No API key is saved for ${config.provider.displayName}. Add one in Profile & Settings."
        )
    }
    return key
}

private fun requireModel(config: AiProviderConfiguration): String {
    val model = config.model.trim()
    if (model.isBlank()) {
        throw AiException(
            AiErrorKind.CONFIGURATION,
            "No model is selected for ${config.provider.displayName}. Choose or enter one in Profile & Settings."
        )
    }
    return model
}

private fun bearer(key: String) = mapOf("Authorization" to "Bearer $key")

/** OpenAI, OpenRouter, xAI and the user's compatible endpoint. */
class OpenAiCompatibleProvider(
    override val id: AiProviderId,
    private val fixedBaseUrl: String,
    private val extraHeaders: Map<String, String>
) : AiProvider {

    override fun resolveBaseUrl(config: AiProviderConfiguration): Result<String> = when {
        id.isCustomEndpoint -> AiEndpointValidator.validateCustomBaseUrl(config.baseUrl)
            .map { it.baseUrl }
        fixedBaseUrl.isNotBlank() -> Result.success(fixedBaseUrl)
        else -> Result.failure(AiException(AiErrorKind.CONFIGURATION, "No endpoint is configured."))
    }

    override suspend fun listModels(config: AiProviderConfiguration): Result<List<AiModelDescriptor>> =
        providerCall {
            val key = requireKey(config)
            val base = resolveBaseUrl(config).getOrThrow()
            val json = AiHttp.getJson(
                url = "$base/models",
                headers = bearer(key) + extraHeaders,
                provider = id
            )
            OpenAiProtocol.parseModelList(json, id)
        }

    override suspend fun testConnection(config: AiProviderConfiguration): Result<Unit> = providerCall {
        val model = requireModel(config)
        val key = requireKey(config)
        val base = resolveBaseUrl(config).getOrThrow()
        val request = AiRequest(
            provider = id,
            model = model,
            apiKey = key,
            baseUrl = base,
            messages = listOf(AiMessage(AiRole.USER, "Reply with OK.")),
            generation = AiGenerationConfig(temperature = 0.0, maxOutputTokens = 8)
        )
        generate(request).getOrThrow()
        Unit
    }

    override suspend fun generate(request: AiRequest): Result<AiResponse> = providerCall {
        val key = request.apiKey.trim().ifBlank { throw missingKey() }
        val base = request.baseUrl.trim().trimEnd('/').ifBlank {
            resolveBaseUrl(
                AiProviderConfiguration(
                    provider = id,
                    apiKey = key,
                    model = request.model
                )
            ).getOrThrow()
        }
        val includeTools = request.toolMode != AiToolMode.COMPATIBILITY && request.tools.isNotEmpty()
        val body = OpenAiProtocol.buildChatRequest(request, includeTools)
        val json = AiHttp.postJson(
            url = "$base/chat/completions",
            headers = bearer(key) + extraHeaders,
            body = body,
            provider = id
        )
        OpenAiProtocol.parseChatResponse(json)
    }

    private fun missingKey() = AiException(
        AiErrorKind.CONFIGURATION,
        "No API key is saved for $id. Add one in Profile & Settings."
    )
}

/** The custom endpoint has no fixed host, so it validates on every request. */
object CustomOpenAiProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.CUSTOM_OPENAI

    override fun resolveBaseUrl(config: AiProviderConfiguration): Result<String> =
        AiEndpointValidator.validateCustomBaseUrl(config.baseUrl).map { it.baseUrl }

    /** A local compatible server may be unauthenticated; a key is only sent when one is set. */
    private fun headers(key: String): Map<String, String> =
        if (key.isBlank()) emptyMap() else bearer(key)

    override suspend fun listModels(config: AiProviderConfiguration): Result<List<AiModelDescriptor>> =
        providerCall {
            val key = config.apiKey.trim()
            val endpoint = AiEndpointValidator.validateCustomBaseUrl(config.baseUrl).getOrThrow()
            val json = AiHttp.getJson(
                url = endpoint.modelsUrl,
                headers = headers(key),
                provider = id
            )
            OpenAiProtocol.parseModelList(json, id)
        }

    override suspend fun testConnection(config: AiProviderConfiguration): Result<Unit> = providerCall {
        val model = requireModel(config)
        val key = config.apiKey.trim()
        val endpoint = AiEndpointValidator.validateCustomBaseUrl(config.baseUrl).getOrThrow()
        val request = AiRequest(
            provider = id,
            model = model,
            apiKey = key,
            baseUrl = endpoint.baseUrl,
            messages = listOf(AiMessage(AiRole.USER, "Reply with OK.")),
            generation = AiGenerationConfig(temperature = 0.0, maxOutputTokens = 8)
        )
        generate(request).getOrThrow()
        Unit
    }

    override suspend fun generate(request: AiRequest): Result<AiResponse> = providerCall {
        val key = request.apiKey.trim()
        val endpoint = AiEndpointValidator.validateCustomBaseUrl(request.baseUrl).getOrThrow()
        val includeTools = request.toolMode != AiToolMode.COMPATIBILITY && request.tools.isNotEmpty()
        val body = OpenAiProtocol.buildChatRequest(request, includeTools)
        val json = AiHttp.postJson(
            url = endpoint.chatCompletionsUrl,
            headers = headers(key),
            body = body,
            provider = id
        )
        OpenAiProtocol.parseChatResponse(json)
    }
}

object AnthropicProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.ANTHROPIC
    private const val BASE_URL = "https://api.anthropic.com/v1"

    override fun resolveBaseUrl(config: AiProviderConfiguration): Result<String> =
        Result.success(BASE_URL)

    private fun headers(key: String) = mapOf(
        "x-api-key" to key,
        "anthropic-version" to AnthropicProtocol.API_VERSION,
        "content-type" to "application/json"
    )

    override suspend fun listModels(config: AiProviderConfiguration): Result<List<AiModelDescriptor>> =
        providerCall {
            val key = requireKey(config)
            val json = AiHttp.getJson("$BASE_URL/models", headers(key), id)
            AnthropicProtocol.parseModelList(json)
        }

    override suspend fun testConnection(config: AiProviderConfiguration): Result<Unit> = providerCall {
        val model = requireModel(config)
        val key = requireKey(config)
        val request = AiRequest(
            provider = id,
            model = model,
            apiKey = key,
            baseUrl = BASE_URL,
            messages = listOf(AiMessage(AiRole.USER, "Reply with OK.")),
            generation = AiGenerationConfig(temperature = 0.0, maxOutputTokens = 8)
        )
        generate(request).getOrThrow()
        Unit
    }

    override suspend fun generate(request: AiRequest): Result<AiResponse> = providerCall {
        val key = request.apiKey.trim().ifBlank { throw missingKey() }
        val includeTools = request.toolMode != AiToolMode.COMPATIBILITY && request.tools.isNotEmpty()
        val body = AnthropicProtocol.buildMessagesRequest(request, includeTools)
        val json = AiHttp.postJson("$BASE_URL/messages", headers(key), body, id)
        AnthropicProtocol.parseMessagesResponse(json)
    }

    private fun missingKey() = AiException(
        AiErrorKind.CONFIGURATION,
        "No Anthropic API key is saved. Add one in Profile & Settings."
    )
}

object GoogleProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.GOOGLE

    override fun knownModels(): List<AiModelDescriptor> = GeminiProtocol.curatedModels()

    override fun resolveBaseUrl(config: AiProviderConfiguration): Result<String> =
        Result.success(GeminiProtocol.BASE_URL)

    override suspend fun listModels(config: AiProviderConfiguration): Result<List<AiModelDescriptor>> =
        providerCall {
            val key = requireKey(config)
            val json = AiHttp.getJson(GeminiProtocol.modelsUrl(key), emptyMap(), id)
            GeminiProtocol.parseModelList(json)
        }

    override suspend fun testConnection(config: AiProviderConfiguration): Result<Unit> = providerCall {
        val model = requireModel(config)
        val key = requireKey(config)
        val request = AiRequest(
            provider = id,
            model = model,
            apiKey = key,
            baseUrl = GeminiProtocol.BASE_URL,
            messages = listOf(AiMessage(AiRole.USER, "Reply with OK.")),
            generation = AiGenerationConfig(temperature = 0.0, maxOutputTokens = 8)
        )
        generate(request).getOrThrow()
        Unit
    }

    override suspend fun generate(request: AiRequest): Result<AiResponse> = providerCall {
        val key = request.apiKey.trim().ifBlank {
            throw AiException(
                AiErrorKind.CONFIGURATION,
                "No Google AI API key is saved. Add one in Profile & Settings."
            )
        }
        val model = request.model.trim().removePrefix("models/").ifBlank { GeminiProtocol.DEFAULT_MODEL }
        val includeTools = request.toolMode != AiToolMode.COMPATIBILITY && request.tools.isNotEmpty()
        val body = GeminiProtocol.buildGenerateContentRequest(request.copy(model = model), includeTools)
        val json = AiHttp.postJson(
            url = GeminiProtocol.generateContentUrl(model, key),
            headers = emptyMap(),
            body = body,
            provider = id
        )
        GeminiProtocol.parseGenerateContentResponse(json)
    }
}

/** Converts anything thrown by an adapter into the normalized error the UI understands. */
internal fun Throwable.toAiException(): AiException = when (this) {
    is AiException -> this
    is CancellationException -> AiException(
        AiErrorKind.CANCELLED,
        "The AI request was cancelled."
    )
    else -> AiException(
        AiErrorKind.UNKNOWN,
        "The AI request failed: ${message?.take(240) ?: "unknown error"}"
    )
}
