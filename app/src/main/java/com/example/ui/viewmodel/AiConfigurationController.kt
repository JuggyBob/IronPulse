/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.BuildConfig
import com.example.data.ai.AiConfigurationRepository
import com.example.data.ai.AiModelDescriptor
import com.example.data.ai.AiProviderConfiguration
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiService
import com.example.data.ai.AiToolMode
import com.example.data.model.UserProfile
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns AI provider selection, credential/model persistence, model discovery and connection tests.
 *
 * Conversation and generation orchestration stay in [MainViewModel] for now; this component only
 * owns the provider-configuration surface that Settings consumes.
 */
class AiConfigurationController(
    private val aiService: AiService,
    private val configurationRepository: AiConfigurationRepository,
    private val repository: FitnessRepository,
    private val scope: CoroutineScope,
    private val geminiApiKeyProvider: () -> String,
    private val legacyProfileProvider: () -> UserProfile,
    private val aiErrorMessageProvider: (Throwable, String) -> String
) {
    val activeAiProvider: StateFlow<AiProviderId> = aiService.activeProviderFlow
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AiProviderId.GOOGLE
        )

    val aiConfiguration: StateFlow<AiProviderConfiguration> =
        configurationRepository.activeConfigurationFlow
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = AiProviderConfiguration(provider = AiProviderId.GOOGLE)
            )

    private val _availableModels = MutableStateFlow(
        aiService.knownModels(AiProviderId.GOOGLE)
    )
    val availableModels: StateFlow<List<AiModelDescriptor>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _modelDiscoveryError = MutableStateFlow<String?>(null)
    val modelDiscoveryError: StateFlow<String?> = _modelDiscoveryError.asStateFlow()

    private fun modelsFor(configuration: AiProviderConfiguration): List<AiModelDescriptor> {
        val saved = configuration.model.trim().takeIf { it.isNotBlank() }?.let {
            AiModelDescriptor(id = it, displayName = it)
        }
        return (aiService.knownModels(configuration.provider) + listOfNotNull(saved))
            .distinctBy { it.id }
    }

    fun loadAvailableModels() {
        scope.launch(Dispatchers.IO) {
            _isLoadingModels.value = true
            _modelDiscoveryError.value = null
            val provider = aiService.activeProviderFlow.first()
            val stored = aiService.configuration(provider)
            val config = stored.copy(
                apiKey = stored.apiKey.ifBlank { fallbackApiKey(provider) }
            )
            if (config.apiKey.isBlank() && !provider.isCustomEndpoint) {
                _availableModels.value = modelsFor(config)
                _modelDiscoveryError.value =
                    "Add and save an API key for ${provider.displayName} to load its model list."
                _isLoadingModels.value = false
                return@launch
            }

            aiService.discoverModels(provider, config).fold(
                onSuccess = { models ->
                    _availableModels.value = (models + modelsFor(config)).distinctBy { it.id }
                    if (models.isEmpty()) {
                        _modelDiscoveryError.value =
                            "${provider.displayName} returned an empty model list. You can still " +
                                "type a model id manually."
                    }
                },
                onFailure = { error ->
                    _availableModels.value = modelsFor(config)
                    _modelDiscoveryError.value = aiErrorMessageProvider(error, "The model list")
                }
            )
            _isLoadingModels.value = false
        }
    }

    fun selectProvider(provider: AiProviderId) {
        scope.launch(Dispatchers.IO) {
            aiService.setActiveProvider(provider)
            val config = aiService.configuration(provider)
            _availableModels.value = modelsFor(config)
            _modelDiscoveryError.value = null
            loadAvailableModels()
        }
    }

    fun saveApiKey(apiKey: String) {
        val cleanKey = apiKey.trim()
        val provider = activeAiProvider.value
        scope.launch(Dispatchers.IO) {
            aiService.saveApiKey(provider, cleanKey)
            if (provider == AiProviderId.GOOGLE) {
                val current = repository.getUserProfile().firstOrNull() ?: legacyProfileProvider()
                repository.saveUserProfile(current.copy(customApiKey = cleanKey))
            }
        }
    }

    fun saveModel(modelName: String) {
        val cleanModel = modelName.trim().removePrefix("models/")
        val provider = activeAiProvider.value
        scope.launch(Dispatchers.IO) {
            aiService.saveModel(provider, cleanModel)
            if (cleanModel.isNotBlank()) {
                val saved = AiModelDescriptor(id = cleanModel, displayName = cleanModel)
                _availableModels.value = (_availableModels.value + saved).distinctBy { it.id }
            }
        }
    }

    suspend fun validateAndSaveModel(modelName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val provider = activeAiProvider.value
            val cleanModel = modelName.trim().removePrefix("models/")
            if (cleanModel.isBlank()) {
                aiService.saveModel(provider, "")
                return@withContext Result.success(Unit)
            }

            val stored = aiService.configuration(provider)
            val config = stored.copy(
                model = cleanModel,
                apiKey = stored.apiKey.ifBlank { fallbackApiKey(provider) }
            )
            aiService.testConnection(provider, config).fold(
                onSuccess = {
                    aiService.saveModel(provider, cleanModel)
                    val saved = AiModelDescriptor(id = cleanModel, displayName = cleanModel)
                    _availableModels.value = (_availableModels.value + saved).distinctBy { it.id }
                    Result.success(Unit)
                },
                onFailure = { Result.failure(it) }
            )
        }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        val provider = activeAiProvider.value
        val stored = aiService.configuration(provider)
        aiService.testConnection(
            provider,
            stored.copy(apiKey = stored.apiKey.ifBlank { fallbackApiKey(provider) })
        )
    }

    suspend fun saveAndTestConnection(
        apiKey: String,
        baseUrl: String? = null,
        acknowledgeEndpoint: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val provider = activeAiProvider.value
        aiService.saveApiKey(provider, apiKey)
        if (provider.isCustomEndpoint && baseUrl != null) {
            aiService.saveBaseUrl(baseUrl)
            if (acknowledgeEndpoint) {
                aiService.acknowledgeCustomEndpoint(baseUrl)
            }
        }
        val stored = aiService.configuration(provider)
        aiService.testConnection(
            provider,
            stored.copy(apiKey = stored.apiKey.ifBlank { fallbackApiKey(provider) })
        )
    }

    fun saveBaseUrl(baseUrl: String, acknowledge: Boolean) {
        scope.launch(Dispatchers.IO) {
            aiService.saveBaseUrl(baseUrl)
            if (acknowledge) {
                aiService.acknowledgeCustomEndpoint(baseUrl)
            } else {
                aiService.clearCustomEndpointAcknowledgement()
            }
        }
    }

    fun acknowledgeCustomEndpoint(baseUrl: String) {
        scope.launch(Dispatchers.IO) {
            aiService.acknowledgeCustomEndpoint(baseUrl)
        }
    }

    fun saveToolMode(mode: AiToolMode) {
        val provider = activeAiProvider.value
        scope.launch(Dispatchers.IO) {
            aiService.saveToolMode(provider, mode)
        }
    }

    fun resetModelDiscovery() {
        _availableModels.value = aiService.knownModels(activeAiProvider.value)
        _modelDiscoveryError.value = null
    }

    fun fallbackApiKey(provider: AiProviderId): String {
        if (provider != AiProviderId.GOOGLE) return ""
        return geminiApiKeyProvider().trim()
            .ifBlank { legacyProfileProvider().customApiKey.trim() }
            .ifBlank {
                BuildConfig.GEMINI_API_KEY.takeIf {
                    it.isNotBlank() && it != "MY_GEMINI_API_KEY"
                }.orEmpty()
            }
    }
}
