package com.example.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.userDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** One provider's persisted configuration. Credentials stay associated with their provider. */
data class AiProviderConfiguration(
    val provider: AiProviderId,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val toolMode: AiToolMode = AiToolMode.AUTO,
    val acknowledgedEndpoint: String = ""
) {
    val isConfigured: Boolean
        get() = model.isNotBlank() && when (provider) {
            // A local OpenAI-compatible server commonly has no key. A key, when present, is still
            // sent only to this endpoint.
            AiProviderId.CUSTOM_OPENAI -> baseUrl.isNotBlank()
            else -> apiKey.isNotBlank()
        }

    /** True when this custom endpoint has been explicitly acknowledged by the athlete. */
    fun isEndpointAcknowledged(): Boolean {
        if (!provider.isCustomEndpoint) return true
        val identity = AiEndpointValidator.endpointIdentity(baseUrl)
        return identity.isNotBlank() && identity == acknowledgedEndpoint
    }
}

/**
 * The only place AI provider settings live.
 *
 * It uses the same DataStore as the rest of the app, but its keys are provider scoped, so switching
 * from OpenAI to Anthropic can never move an OpenAI key to Anthropic's endpoint. Google keeps
 * reading the legacy Gemini keys, which is what makes the existing installation upgrade in place.
 */
class AiConfigurationRepository(private val context: Context) {

    companion object {
        val ACTIVE_PROVIDER = stringPreferencesKey("ai_provider")
        val CUSTOM_BASE_URL = stringPreferencesKey("ai_custom_base_url")
        val CUSTOM_ACKNOWLEDGED_ENDPOINT = stringPreferencesKey("ai_custom_acknowledged_endpoint")

        fun apiKeyKey(provider: AiProviderId) =
            stringPreferencesKey("ai_api_key_${provider.storageKey}")

        fun modelKey(provider: AiProviderId) =
            stringPreferencesKey("ai_model_${provider.storageKey}")

        fun toolModeKey(provider: AiProviderId) =
            stringPreferencesKey("ai_tool_mode_${provider.storageKey}")
    }

    val activeProviderFlow: Flow<AiProviderId> = context.userDataStore.data
        .map { AiProviderId.fromStorage(it[ACTIVE_PROVIDER]) }
        .distinctUntilChanged()

    fun configurationFlow(provider: AiProviderId): Flow<AiProviderConfiguration> =
        context.userDataStore.data.map { prefs ->
            val newKey = prefs[apiKeyKey(provider)].orEmpty()
            val legacyKey = if (provider == AiProviderId.GOOGLE) {
                prefs[UserPreferencesRepository.GEMINI_API_KEY].orEmpty()
            } else {
                ""
            }
            val newModel = prefs[modelKey(provider)].orEmpty()
            val legacyModel = if (provider == AiProviderId.GOOGLE) {
                prefs[UserPreferencesRepository.GEMINI_MODEL_NAME].orEmpty()
            } else {
                ""
            }
            AiProviderConfiguration(
                provider = provider,
                apiKey = newKey.ifBlank { legacyKey },
                model = newModel.ifBlank { legacyModel },
                baseUrl = prefs[CUSTOM_BASE_URL].orEmpty(),
                toolMode = AiToolMode.fromStorage(prefs[toolModeKey(provider)]),
                acknowledgedEndpoint = prefs[CUSTOM_ACKNOWLEDGED_ENDPOINT].orEmpty()
            )
        }.distinctUntilChanged()

    val activeConfigurationFlow: Flow<AiProviderConfiguration> =
        activeProviderFlow.flatMapLatest { configurationFlow(it) }

    suspend fun activeConfiguration(): AiProviderConfiguration = activeConfigurationFlow.first()

    suspend fun configuration(provider: AiProviderId): AiProviderConfiguration =
        configurationFlow(provider).first()

    suspend fun setActiveProvider(provider: AiProviderId) {
        context.userDataStore.edit { it[ACTIVE_PROVIDER] = provider.storageKey }
    }

    suspend fun saveApiKey(provider: AiProviderId, apiKey: String) {
        val clean = apiKey.trim()
        context.userDataStore.edit { prefs ->
            prefs[apiKeyKey(provider)] = clean
            if (provider == AiProviderId.GOOGLE) {
                prefs[UserPreferencesRepository.GEMINI_API_KEY] = clean
            }
        }
    }

    suspend fun saveModel(provider: AiProviderId, model: String) {
        val clean = model.trim().removePrefix("models/")
        context.userDataStore.edit { prefs ->
            prefs[modelKey(provider)] = clean
            if (provider == AiProviderId.GOOGLE) {
                prefs[UserPreferencesRepository.GEMINI_MODEL_NAME] = clean
            }
        }
    }

    suspend fun saveBaseUrl(baseUrl: String) {
        context.userDataStore.edit { it[CUSTOM_BASE_URL] = baseUrl.trim() }
    }

    suspend fun saveToolMode(provider: AiProviderId, mode: AiToolMode) {
        context.userDataStore.edit { it[toolModeKey(provider)] = mode.name }
    }

    /**
     * Records consent for one exact endpoint identity. Changing the host or the path invalidates it,
     * so a materially different endpoint triggers the warning again.
     */
    suspend fun acknowledgeCustomEndpoint(baseUrl: String) {
        val identity = AiEndpointValidator.endpointIdentity(baseUrl)
        context.userDataStore.edit { it[CUSTOM_ACKNOWLEDGED_ENDPOINT] = identity }
    }

    suspend fun clearCustomEndpointAcknowledgement() {
        context.userDataStore.edit { it.remove(CUSTOM_ACKNOWLEDGED_ENDPOINT) }
    }

    /** Clears every AI credential and selection; used by "Delete app data". */
    suspend fun clearAll() {
        context.userDataStore.edit { prefs ->
            AiProviderId.entries.forEach { provider ->
                prefs.remove(apiKeyKey(provider))
                prefs.remove(modelKey(provider))
                prefs.remove(toolModeKey(provider))
            }
            prefs.remove(ACTIVE_PROVIDER)
            prefs.remove(CUSTOM_BASE_URL)
            prefs.remove(CUSTOM_ACKNOWLEDGED_ENDPOINT)
            prefs.remove(UserPreferencesRepository.GEMINI_API_KEY)
            prefs.remove(UserPreferencesRepository.GEMINI_MODEL_NAME)
        }
    }
}
