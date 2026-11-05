package com.example.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The single AI entry point used by the app.
 *
 * View models ask for an answer; they never know which provider protocol is in use, how models are
 * discovered, or how tools are executed. Keeping this boundary means adding a provider cannot
 * accidentally add a second coach implementation, and every request goes through the same consent,
 * validation, retry and tool-limit rules.
 */
class AiService(
    private val configurationRepository: AiConfigurationRepository,
    dataAccess: AiDataAccess,
    private val engine: AiConversationEngine = AiConversationEngine(
        dispatcher = AiToolDispatcher(AiToolRegistry(dataAccess))
    ),
    private val providerResolver: (AiProviderId) -> AiProvider = { AiProviderRegistry.provider(it) }
) {

    companion object {
        /** The coaching instruction the app has always sent with generation requests. */
        const val DEFAULT_COACH_SYSTEM =
            "You are IronPulse AI, an elite calisthenics, gym, and strength coach. " +
                "Provide clear, biomechanically sound advice, technique breakdowns, progressive " +
                "overload tips, and structured routines. When asked for a workout routine, format " +
                "each exercise clearly with suggested Sets and Reps or Hold Durations on individual " +
                "lines (e.g. 'Exercise Name (4x8 @ +10kg)'). Keep advice concise, motivating, and " +
                "actionable."
    }

    val activeProviderFlow: Flow<AiProviderId> = configurationRepository.activeProviderFlow

    fun configurationFlow(provider: AiProviderId): Flow<AiProviderConfiguration> =
        configurationRepository.configurationFlow(provider)

    suspend fun activeConfiguration(): AiProviderConfiguration {
        val stored = configurationRepository.activeConfiguration()
        // An empty model means "use the provider default", which is exactly what the Settings
        // screen shows for Google. Resolve it here so features such as the post-workout summary do
        // not mistake that visible default for a missing selection.
        return stored.withDefaults(stored.provider)
    }

    suspend fun configuration(provider: AiProviderId): AiProviderConfiguration =
        configurationRepository.configuration(provider)

    /** Models the selector can show before any network call. Never labelled as live. */
    fun knownModels(provider: AiProviderId): List<AiModelDescriptor> =
        providerResolver(provider).knownModels()

    /**
     * Fetches the provider's model catalog.
     *
     * An empty successful result is returned as-is so the UI can show a real empty state instead of
     * pretending a hardcoded list came from the provider.
     */
    suspend fun discoverModels(
        provider: AiProviderId,
        configuration: AiProviderConfiguration? = null
    ): Result<List<AiModelDescriptor>> {
        val config = (configuration ?: configurationRepository.configuration(provider))
            .withDefaults(provider)
        return providerCall {
            ensureEndpointAllowed(config)
            providerResolver(provider).listModels(config).getOrThrow()
        }
    }

    /** A real generation request, not a catalog probe. */
    suspend fun testConnection(
        provider: AiProviderId,
        configuration: AiProviderConfiguration? = null
    ): Result<Unit> {
        val config = (configuration ?: configurationRepository.configuration(provider))
            .withDefaults(provider)
        return providerCall {
            ensureEndpointAllowed(config)
            providerResolver(provider).testConnection(config).getOrThrow()
        }
    }

    suspend fun complete(
        systemInstruction: String,
        messages: List<AiMessage>,
        enableTools: Boolean = true,
        generation: AiGenerationConfig = AiGenerationConfig(),
        provider: AiProviderId? = null,
        apiKeyFallback: String = "",
        accessPolicy: AiAccessPolicy = AiAccessPolicy.disabled,
        accessPolicyProvider: (suspend () -> AiAccessPolicy)? = null
    ): AiConversationResult {
        val config = resolveConfiguration(provider, apiKeyFallback)
        ensureEndpointAllowed(config)
        return engine.run(
            configuration = config,
            systemInstruction = systemInstruction,
            messages = messages,
            enableTools = enableTools,
            generation = generation,
            accessPolicy = if (enableTools) accessPolicy else AiAccessPolicy.disabled,
            accessPolicyProvider = if (enableTools) accessPolicyProvider else null
        )
    }

    /** Convenience for the structured-generation call sites that need plain text. */
    suspend fun completeText(
        prompt: String,
        systemInstruction: String = DEFAULT_COACH_SYSTEM,
        provider: AiProviderId? = null,
        apiKeyFallback: String = ""
    ): String = complete(
        systemInstruction = systemInstruction,
        messages = listOf(AiMessage(AiRole.USER, prompt)),
        enableTools = false,
        provider = provider,
        apiKeyFallback = apiKeyFallback
    ).text

    suspend fun setActiveProvider(provider: AiProviderId) =
        configurationRepository.setActiveProvider(provider)

    suspend fun saveApiKey(provider: AiProviderId, apiKey: String) =
        configurationRepository.saveApiKey(provider, apiKey)

    suspend fun saveModel(provider: AiProviderId, model: String) =
        configurationRepository.saveModel(provider, model)

    suspend fun saveBaseUrl(baseUrl: String) = configurationRepository.saveBaseUrl(baseUrl)

    suspend fun saveToolMode(provider: AiProviderId, mode: AiToolMode) =
        configurationRepository.saveToolMode(provider, mode)

    suspend fun acknowledgeCustomEndpoint(baseUrl: String) =
        configurationRepository.acknowledgeCustomEndpoint(baseUrl)

    suspend fun clearCustomEndpointAcknowledgement() =
        configurationRepository.clearCustomEndpointAcknowledgement()

    suspend fun clearAll() = configurationRepository.clearAll()

    private suspend fun resolveConfiguration(
        provider: AiProviderId?,
        apiKeyFallback: String
    ): AiProviderConfiguration {
        val selected = provider ?: configurationRepository.activeProviderFlow.first()
        val stored = configurationRepository.configuration(selected)
        return stored.copy(
            apiKey = stored.apiKey.ifBlank { apiKeyFallback.trim() }
        ).withDefaults(selected)
    }

    private fun AiProviderConfiguration.withDefaults(provider: AiProviderId): AiProviderConfiguration =
        copy(
            provider = provider,
            model = model.ifBlank {
                if (provider == AiProviderId.GOOGLE) GeminiProtocol.DEFAULT_MODEL else ""
            }
        )

    /**
     * A custom endpoint is a credential destination. Nothing is sent until the athlete has
     * acknowledged the exact normalized endpoint, and the URL is re-validated on every call so a
     * later edit cannot reuse an old consent.
     */
    private fun ensureEndpointAllowed(configuration: AiProviderConfiguration) {
        if (!configuration.provider.isCustomEndpoint) return
        val validated = AiEndpointValidator.validateCustomBaseUrl(configuration.baseUrl)
            .getOrElse { throw it.toAiException() }
        if (validated.baseUrl != configuration.acknowledgedEndpoint) {
            throw AiException(
                AiErrorKind.CONSENT_REQUIRED,
                "This custom endpoint has not been confirmed. Review its privacy warning in " +
                    "Profile & Settings before sending any data."
            )
        }
    }
}
