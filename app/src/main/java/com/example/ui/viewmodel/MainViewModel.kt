package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ai.AiAccessPolicy
import com.example.data.ai.AiConfigurationRepository
import com.example.data.ai.AiExerciseFactory
import com.example.data.ai.AiErrorKind
import com.example.data.ai.AiException
import com.example.data.ai.AiExerciseName
import com.example.data.ai.AiMessage
import com.example.data.ai.AiModelDescriptor
import com.example.data.ai.AiProviderConfiguration
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiRole
import com.example.data.ai.AiService
import com.example.data.ai.AiToolMode
import com.example.data.ai.NewRoutineProposal
import com.example.data.ai.RepositoryAiDataAccess
import com.example.data.ai.RoutineModificationProposal
import com.example.data.api.ReleaseApi
import com.example.data.backup.BackupArchiveLimits
import com.example.data.backup.BackupException
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupOperation
import com.example.data.backup.BackupPhase
import com.example.data.backup.BackupPreferencesStore
import com.example.data.backup.BackupPreview
import com.example.data.backup.BackupProgress
import com.example.data.backup.DataTransferState
import com.example.data.backup.PreparedImport
import com.example.data.backup.UserDataBackupManager
import com.example.data.backup.copyWithLimit
import com.example.data.db.AppDatabase
import com.example.data.model.ActiveRoutineWorkoutState
import com.example.data.model.ActiveRestTimerState
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.CustomRestTimer
import com.example.data.model.BodyWeightLog
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatMessageKind
import com.example.data.model.ChatMessageStatus
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.ExerciseSearch
import com.example.data.model.GibberishNameDetector
import com.example.data.seed.DatasetSeeder
import com.example.data.seed.LibraryEditTracker
import com.example.util.ProfileMediaStore
import com.example.util.WidgetPhotoStore
import com.example.data.model.ExerciseFilters
import com.example.data.model.ExerciseSchema
import com.example.data.seed.toJsonArray
import com.example.data.model.ExerciseMedia
import com.example.data.model.PredefinedRoutines
import com.example.data.model.ExerciseType
import com.example.data.model.GeminiSummary
import com.example.data.model.HomeDashboard
import com.example.data.model.LocalDateKey
import com.example.data.model.LoggedSet
import com.example.data.model.LoggedSetNumbering
import com.example.data.model.MonthlyExerciseAnalyzer
import com.example.data.model.MonthlyExerciseSummary
import com.example.data.model.ReservedExerciseNames
import com.example.data.model.MuscleCategory
import com.example.data.model.MusclePart
import com.example.data.model.MusclePartResolver
import com.example.data.model.ActivityDay
import com.example.data.model.ActivityHeatmap
import com.example.data.model.AiRoutineSchedule
import com.example.data.model.AboutState
import com.example.data.model.AppVersion
import com.example.data.model.GithubRelease
import com.example.data.model.ReleaseAsset
import com.example.data.model.UpdateState
import com.example.data.model.UpdateVerification
import com.example.data.model.Weekday
import com.example.data.model.ScheduledWorkoutDay
import com.example.data.model.SessionWithSets
import com.example.data.model.StreakSummary
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.RoutineDay
import com.example.data.model.SavedRoutine
import com.example.data.model.UnstructuredParseState
import com.example.data.model.CustomRoutinePromptState
import com.example.data.model.WeightUnit
import com.example.data.model.WeightConversion
import com.example.data.model.UserProfile
import com.example.data.ai.WorkoutSummaryGenerator
import com.example.data.model.WorkoutAiSummary
import com.example.data.model.WorkoutDraft
import com.example.data.model.WorkoutDraftCodec
import com.example.data.model.WorkoutMetrics
import com.example.data.model.WorkoutSession
import com.example.data.model.WorkoutStreak
import com.example.data.preferences.HomeWidgetConfig
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FitnessRepository
import com.example.ui.components.ChartPoint
import com.example.ui.components.HomeWidget
import com.example.ui.theme.ThemeMode
import com.example.util.ExerciseMediaStore
import com.example.util.ApkInstaller
import com.example.util.VibrationHelper
import com.example.util.SoundHelper
import com.example.util.RestTimerScheduler
import com.example.util.RestTimerEventManager
import com.example.util.RestTimerAlertCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ProgressMetric(val displayName: String, val unit: String) {
    MAX_REPS("Max Reps", "reps"),
    ESTIMATED_1RM("Estimated 1RM", "kg"),
    TOTAL_VOLUME("Total Volume", "kg-reps")
}

@Immutable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val kind: String = com.example.data.model.ChatMessageKind.TEXT,
    val payloadJson: String = "",
    val status: String = com.example.data.model.ChatMessageStatus.NONE,
    val sessionId: String = ""
) {
    enum class Sender { USER, AI }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val chatSessionMutex = Mutex()

    private val db = AppDatabase.getInstance(application)
    private val repository = FitnessRepository(
        exerciseDao = db.exerciseDao(),
        workoutDao = db.workoutDao(),
        userProfileDao = db.userProfileDao(),
        savedRoutineDao = db.savedRoutineDao(),
        scheduleDao = db.scheduleDao(),
        bodyWeightDao = db.bodyWeightDao(),
        chatDao = db.chatDao(),
        geminiSummaryDao = db.geminiSummaryDao(),
        workoutAiSummaryDao = db.workoutAiSummaryDao()
    )

    private val userPreferencesRepo = UserPreferencesRepository(application)
    private val routineModificationService = com.example.data.ai.RoutineModificationService(db)

    private val backupPreferencesStore = BackupPreferencesStore(application)
    private val backupManager = UserDataBackupManager(application, db, backupPreferencesStore)

    private val _dataTransferState = MutableStateFlow<DataTransferState>(DataTransferState.Idle)

    /** Export/import progress and results shown by Settings. */
    val dataTransferState: StateFlow<DataTransferState> = _dataTransferState.asStateFlow()

    private var dataTransferJob: Job? = null
    private var preparedImport: PreparedImport? = null

    val geminiApiKey: StateFlow<String> = userPreferencesRepo.geminiApiKeyFlow
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    val geminiModelName: StateFlow<String> = userPreferencesRepo.geminiModelFlow
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    private val aiConfigurationRepository = AiConfigurationRepository(application)
    private val aiService = AiService(
        configurationRepository = aiConfigurationRepository,
        dataAccess = RepositoryAiDataAccess(repository, userPreferencesRepo)
    )
    private val workoutSummaryGenerator = WorkoutSummaryGenerator(repository, aiService)

    val activeAiProvider: StateFlow<AiProviderId> = aiService.activeProviderFlow
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AiProviderId.GOOGLE
        )

    val aiConfiguration: StateFlow<AiProviderConfiguration> =
        aiConfigurationRepository.activeConfigurationFlow
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
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

    /** Models to show for a provider: its known fallback plus the model already saved. */
    private fun modelsFor(configuration: AiProviderConfiguration): List<AiModelDescriptor> {
        val saved = configuration.model.trim().takeIf { it.isNotBlank() }?.let {
            AiModelDescriptor(id = it, displayName = it)
        }
        return (aiService.knownModels(configuration.provider) + listOfNotNull(saved))
            .distinctBy { it.id }
    }

    /**
     * Fetches the active provider's live catalog.
     *
     * A failure never wipes a working selection: the saved model and the provider's known models
     * stay selectable, and the error is shown next to the refresh button. An empty successful
     * catalog is reported as empty, not replaced with invented entries.
     */
    fun loadAvailableModels() {
        viewModelScope.launch(Dispatchers.IO) {
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
                    _modelDiscoveryError.value = aiErrorMessage(error, "The model list")
                }
            )
            _isLoadingModels.value = false
        }
    }

    fun selectAiProvider(provider: AiProviderId) {
        viewModelScope.launch(Dispatchers.IO) {
            aiService.setActiveProvider(provider)
            val config = aiService.configuration(provider)
            _availableModels.value = modelsFor(config)
            _modelDiscoveryError.value = null
            loadAvailableModels()
        }
    }

    fun saveAiApiKey(apiKey: String) {
        val cleanKey = apiKey.trim()
        val provider = activeAiProvider.value
        viewModelScope.launch(Dispatchers.IO) {
            aiService.saveApiKey(provider, cleanKey)
            // The profile copy is the legacy location the old Gemini build read. It is only kept in
            // sync for Google; another provider's key must never overwrite it.
            if (provider == AiProviderId.GOOGLE) {
                val current = repository.getUserProfile().firstOrNull() ?: userProfile.value
                repository.saveUserProfile(current.copy(customApiKey = cleanKey))
            }
        }
    }

    fun saveAiModel(modelName: String) {
        val cleanModel = modelName.trim().removePrefix("models/")
        val provider = activeAiProvider.value
        viewModelScope.launch(Dispatchers.IO) {
            aiService.saveModel(provider, cleanModel)
            if (cleanModel.isNotBlank()) {
                val saved = AiModelDescriptor(id = cleanModel, displayName = cleanModel)
                _availableModels.value = (_availableModels.value + saved).distinctBy { it.id }
            }
        }
    }

    /**
     * Tests the model with a real generation request before saving it.
     *
     * Manual model ids are essential for custom endpoints, so this is a separate action: the
     * settings screen can save an id without a network round trip, and the athlete can ask for a
     * live check whenever the endpoint is reachable.
     */
    suspend fun validateAndSaveAiModel(modelName: String): Result<Unit> = withContext(Dispatchers.IO) {
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

    suspend fun testAiConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        val provider = activeAiProvider.value
        val stored = aiService.configuration(provider)
        aiService.testConnection(
            provider,
            stored.copy(apiKey = stored.apiKey.ifBlank { fallbackApiKey(provider) })
        )
    }

    /**
     * Saves the key (and a custom endpoint, when one was typed) and immediately tests it.
     *
     * The test uses exactly what was just persisted, so the athlete never sees a stale result from
     * an older key or an unacknowledged endpoint.
     */
    suspend fun saveAndTestAiConnection(
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

    fun saveAiBaseUrl(baseUrl: String, acknowledge: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            aiService.saveBaseUrl(baseUrl)
            if (acknowledge) {
                aiService.acknowledgeCustomEndpoint(baseUrl)
            } else {
                aiService.clearCustomEndpointAcknowledgement()
            }
        }
    }

    fun acknowledgeAiCustomEndpoint(baseUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            aiService.acknowledgeCustomEndpoint(baseUrl)
        }
    }

    fun saveAiToolMode(mode: AiToolMode) {
        val provider = activeAiProvider.value
        viewModelScope.launch(Dispatchers.IO) {
            aiService.saveToolMode(provider, mode)
        }
    }

    /** Key available for the active provider, including the legacy Google fallbacks. */
    private fun fallbackApiKey(provider: AiProviderId): String {
        if (provider != AiProviderId.GOOGLE) return ""
        return geminiApiKey.value.trim()
            .ifBlank { userProfile.value.customApiKey.trim() }
            .ifBlank {
                BuildConfig.GEMINI_API_KEY.takeIf {
                    it.isNotBlank() && it != "MY_GEMINI_API_KEY"
                }.orEmpty()
            }
    }

    // Legacy names kept so existing call sites and installations keep working. They now route to
    // the active provider instead of assuming Google.
    fun saveGeminiApiKey(apiKey: String) = saveAiApiKey(apiKey)

    fun saveGeminiModel(modelName: String) = saveAiModel(modelName)

    suspend fun validateAndSaveGeminiModel(modelName: String): Result<Unit> =
        validateAndSaveAiModel(modelName)

    // === USER PROFILE ===
    val userProfile: StateFlow<UserProfile> = combine(
        repository.getUserProfile(),
        userPreferencesRepo.userProfileFlow
    ) { roomProfile, prefProfile ->
        when {
            // The DataStore copy is the live one (it is what onboarding and the profile editor
            // write), but its weight can be 0 when an upgraded install never stored the key. In
            // that case the Room row is the only real source of the athlete's weight, and letting
            // the zero win made every new set stamp 0 kg. The other fields still come from the
            // live profile.
            prefProfile != null -> prefProfile.copy(
                currentWeightKg = prefProfile.currentWeightKg.takeIf { it > 0.0 }
                    ?: roomProfile?.currentWeightKg?.takeIf { it > 0.0 }
                    ?: 0.0
            )
            roomProfile != null -> roomProfile
            else -> UserProfile(id = 1)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserProfile(id = 1)
        )

    /**
     * The newest usable weigh-in, kept eagerly so a body weight can be stamped without blocking a
     * tap.
     *
     * This is only a fallback for when both stored profiles have no usable weight. The profile is
     * still the required source for bodyweight work; a weigh-in is never allowed to rewrite a
     * snapshot that already exists.
     */
    private val latestWeighInKg: StateFlow<Double?> = repository.getAllWeightLogs()
        .map { logs ->
            logs.maxByOrNull { it.timestamp }?.weightKg?.takeIf { it > 0.0 }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The athlete's body weight right now, in kilograms, or 0 when there is no usable value.
     *
     * Taken when a set is created, so the set carries the weight it was done at. A later weigh-in
     * must not rewrite it. The fallback order is the merged profile, then the newest weigh-in.
     */
    private fun currentBodyWeightKg(): Double =
        userProfile.value.currentWeightKg.takeIf { it > 0.0 }
            ?: latestWeighInKg.value
            ?: 0.0

    /**
     * The same value, read from disk when the in-memory flows have not caught up yet.
     *
     * Finishing a workout is the one moment a missing weight is written permanently, so it cannot
     * rely on an eagerly started StateFlow that may still hold its initial value. DataStore is
     * checked first (including the no-default accessor), then Room, then the in-memory profile, and
     * only then the newest weigh-in. The in-memory value comes after the stored ones because its
     * initial value is the `UserProfile` default, not a weight the athlete entered.
     */
    private suspend fun resolveBodyWeightKg(): Double {
        val liveProfileWeight = runCatching { userPreferencesRepo.storedProfileWeightKg() }.getOrNull()
        val roomProfileWeight = repository.getUserProfile()
            .firstOrNull()
            ?.currentWeightKg
            ?.takeIf { it > 0.0 }
        val latestLoggedWeight = latestWeighInKg.value
            ?: repository.getAllWeightLogs()
                .first()
                .maxByOrNull { it.timestamp }
                ?.weightKg
                ?.takeIf { it > 0.0 }

        return liveProfileWeight
            ?: roomProfileWeight
            ?: userProfile.value.currentWeightKg.takeIf { it > 0.0 }
            ?: latestLoggedWeight
            ?: 0.0
    }

    /**
     * The athlete's profile picture, as a path inside the app's own storage.
     *
     * Empty when they have not chosen one, in which case the profile shows the default icon.
     */
    val profileAvatarPath: StateFlow<String> = userPreferencesRepo.profileAvatarPathFlow
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    /**
     * Stores the profile picture and forgets the one it replaces.
     *
     * The old file is deleted rather than left behind: it is a copy of a photo the athlete picked,
     * and copies of photos are not something to accumulate silently.
     */
    fun setProfileAvatar(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val previous = profileAvatarPath.value
            if (previous.isNotBlank() && previous != path) {
                ProfileMediaStore.delete(getApplication())
            }
            userPreferencesRepo.setProfileAvatarPath(path)
        }
    }

    /** Removes the profile picture and goes back to the default icon. */
    fun clearProfileAvatar() {
        viewModelScope.launch(Dispatchers.IO) {
            ProfileMediaStore.delete(getApplication())
            userPreferencesRepo.setProfileAvatarPath("")
        }
    }

    /**
     * Saves the athlete profile.
     *
     * A changed body weight is also appended to the body weight timeline so the history stays
     * continuous, but it is stored with [WEIGH_IN_NOTE_PROFILE_UPDATE] so the log list never shows
     * an "Edit Athlete Profile" change as if it were a morning weigh-in.
     */
    private fun refreshAndroidWidgets() {
        com.example.widget.WorkoutWidgetUpdater.requestUpdate(getApplication())
    }

    fun updateProfile(profile: UserProfile) {
        val cleanName = profile.name.trim().take(MAX_NAME_LENGTH)
        val toSave = profile.copy(name = cleanName)
        viewModelScope.launch(Dispatchers.IO) {
            val previous = repository.getUserProfile().firstOrNull()
            userPreferencesRepo.saveUserProfile(toSave)
            repository.saveUserProfile(toSave)
            // The live unit setting is written here too, not only inside the stored profile.
            //
            // The profile edit dialog has its own unit selector, so it can change the unit without
            // ever going through setWeightUnit. `weightUnit` reads the live key first and only falls
            // back to the one inside the profile, so without this the dialog would appear to do
            // nothing: the profile changed and the app kept rendering the old unit.
            userPreferencesRepo.setWeightUnit(toSave.preferredUnit)

            val weightChanged = previous != null &&
                kotlin.math.abs(previous.currentWeightKg - toSave.currentWeightKg) > 0.0001
            if (weightChanged) {
                repository.insertWeightLog(
                    BodyWeightLog(
                        weightKg = toSave.currentWeightKg,
                        timestamp = System.currentTimeMillis(),
                        note = WEIGH_IN_NOTE_PROFILE_UPDATE
                    )
                )
            }
            refreshAndroidWidgets()
        }
    }

    // === EXERCISES & SEARCH ===

    // === AI GENERATION OF A SINGLE MISSING EXERCISE (Create Routine "Ask AI") ===
    sealed interface AiExerciseCreationState {
        object Idle : AiExerciseCreationState
        data class Working(val exerciseName: String) : AiExerciseCreationState
        data class Created(val exercise: Exercise) : AiExerciseCreationState
        data class Failed(val message: String) : AiExerciseCreationState
    }

    private val _aiExerciseCreationState = MutableStateFlow<AiExerciseCreationState>(AiExerciseCreationState.Idle)
    val aiExerciseCreationState: StateFlow<AiExerciseCreationState> = _aiExerciseCreationState.asStateFlow()

    fun resetAiExerciseCreationState() {
        _aiExerciseCreationState.value = AiExerciseCreationState.Idle
    }

    /**
     * Asks Gemini to fill in every field of one exercise the user searched for but that does not
     * exist yet, then stores it flagged as AI generated so it behaves like any other exercise.
     */
    fun createSingleExerciseWithAI(exerciseName: String) {
        val cleanName = exerciseName.trim()
        if (cleanName.isBlank()) return

        // Keyboard mash must never reach the model: it would get a confident, fictional exercise back
        if (GibberishNameDetector.isGibberish(cleanName)) {
            _aiExerciseCreationState.value = AiExerciseCreationState.Failed(
                GibberishNameDetector.rejectionMessage(cleanName)
            )
            return
        }

        // A search keyword is not an exercise either
        if (ReservedExerciseNames.isReserved(cleanName)) {
            _aiExerciseCreationState.value = AiExerciseCreationState.Failed(
                ReservedExerciseNames.explanation(cleanName)
            )
            return
        }

        if (ensureApiKeyOrExplain("The AI exercise generator")) return

        _aiExerciseCreationState.value = AiExerciseCreationState.Working(cleanName)

        viewModelScope.launch(Dispatchers.IO) {
            val knownExercises = repository.getAllExercises().first()
            val existing = knownExercises.firstOrNull {
                it.name.trim().equals(cleanName, ignoreCase = true)
            }
            if (existing != null) {
                // Already stored (maybe it was added while the AI was answering)
                _aiExerciseCreationState.value = AiExerciseCreationState.Created(existing)
                return@launch
            }

            val prompt = buildString {
                append("You are a strength and calisthenics coach creating ONE new exercise entry " +
                        "for a fitness app database.\n\n")
                append(athleteContextPromptBlock(includeBenchmarks = false, purpose = "realistic exercise fields"))
                append("EXERCISE NAME TO DEFINE: \"$cleanName\"\n\n")
                append("OUTPUT FORMAT: RESPOND WITH ONE SINGLE JSON OBJECT AND NOTHING ELSE.\n")
                append("No greeting, no explanation, no markdown, no code fence.\n\n")
                append("SCHEMA:\n")
                append("{\n")
                append("  \"name\": \"$cleanName\",\n")
                append("  \"category\": \"<one of: Chest, Back, Legs, Shoulders, Arms, Core, Calisthenics>\",\n")
                append("  \"exerciseType\": \"<one of: STANDARD_WEIGHT, ADDED_WEIGHT, PURE_BODYWEIGHT>\",\n")
                append("  \"equipment\": \"<the equipment needed, e.g. Barbell, Dumbbells, Cable, Machine, Bodyweight, Pull-up Bar, Rings, Resistance Band>\",\n")
                append("  \"description\": \"<one or two sentences on how the movement is performed>\"\n")
                append("}\n\n")
                append("RULES:\n")
                append("- \"name\" MUST be exactly \"$cleanName\".\n")
                append("- Choose PURE_BODYWEIGHT only when the movement is done with no external load at all " +
                        "(pull-up, push-up, dip, bodyweight squat). Choose ADDED_WEIGHT when extra weight is " +
                        "hung on the athlete (weighted pull-up, weighted dip). Choose STANDARD_WEIGHT for barbell " +
                        "or dumbbell work.\n")
                append("- For a PURE_BODYWEIGHT movement the app logs BOTH reps and hold seconds, so the athlete " +
                        "can record whichever the movement uses. Never describe it as a timed hold only unless " +
                        "it really is an isometric hold such as a plank.\n")
                append("- Describe ONLY a movement you genuinely know. Never invent one, never rename " +
                        "\"$cleanName\" into something else, and never fill a gap by guessing.\n")
                append("- If \"$cleanName\" is not a real, recognisable exercise (random keyboard mash, " +
                        "gibberish, a typo you cannot resolve into a known movement, an instruction or a " +
                        "question, or a body part on its own such as \"chest\"), you MUST refuse. In that " +
                        "case your entire reply is exactly this, with no other text and no code fence:\n")
                append("{\"error\": \"not_an_exercise\"}\n")
                append("- Refusing is the correct answer far more often than it feels. A made up exercise " +
                        "is worse than no exercise at all.\n")
            }

            val result = runCatching { generateAiText(prompt) }

            result.fold(
                onSuccess = { rawResponse ->
                    val created = parseAiExerciseJson(rawResponse, cleanName)
                    if (created == null) {
                        // Either the model refused outright or it invented something unusable: both
                        // are reported the same way, because both mean "no exercise was created"
                        _aiExerciseCreationState.value = AiExerciseCreationState.Failed(
                            GibberishNameDetector.rejectionMessage(cleanName)
                        )
                    } else {
                        val alreadyThere = repository.getAllExercises().first()
                            .firstOrNull { it.name.trim().equals(created.name, ignoreCase = true) }
                        if (alreadyThere != null) {
                            _aiExerciseCreationState.value = AiExerciseCreationState.Created(alreadyThere)
                        } else {
                            val id = insertCustomExercise(created)
                            _aiExerciseCreationState.value =
                                AiExerciseCreationState.Created(created.copy(id = id))
                        }
                    }
                },
                onFailure = { error ->
                    _aiExerciseCreationState.value = AiExerciseCreationState.Failed(
                        geminiErrorMessage(error, "The AI exercise")
                    )
                }
            )
        }
    }

    /** Reads the single-exercise JSON answer, tolerating prose around the object. */
    private fun parseAiExerciseJson(rawResponse: String, fallbackName: String): Exercise? =
        AiExerciseFactory.parse(rawResponse, fallbackName)

    // === BODY WEIGHT BENCHMARK PERSONAL RECORDS ===
    // Stored outside the workout history: deleting the workout that set the record must not
    // remove it from the athlete profile. They are read-only in the UI.
    val benchmarkPullUps: StateFlow<Int> = userPreferencesRepo.benchmarkPullUpsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val benchmarkPushUps: StateFlow<Int> = userPreferencesRepo.benchmarkPushUpsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val benchmarkBulgarianSplitSquat: StateFlow<Int> =
        userPreferencesRepo.benchmarkBulgarianSplitSquatFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // The load each record was done at, in kilograms. 0 = bodyweight. Kept beside the rep count
    // because a rep count on its own cannot tell the AI which kind of effort it was.
    val benchmarkPullUpsAddedWeight: StateFlow<Int> =
        userPreferencesRepo.benchmarkPullUpsAddedWeightFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val benchmarkPushUpsAddedWeight: StateFlow<Int> =
        userPreferencesRepo.benchmarkPushUpsAddedWeightFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val benchmarkBulgarianSplitSquatAddedWeight: StateFlow<Int> =
        userPreferencesRepo.benchmarkBulgarianSplitSquatAddedWeightFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * One benchmark record: the rep count and the added load it was done with.
     *
     * @param reps most reps logged for the exercise.
     * @param addedKg kilograms of added load on that set, 0 when it was a bodyweight set.
     */
    data class BenchmarkRecord(val reps: Int = 0, val addedKg: Int = 0) {
        val isSet: Boolean get() = reps > 0
    }

    /**
     * Raises a benchmark record when the athlete beat it. Records only ever go up, so deleting a
     * workout can never lower them.
     *
     * Every logged set counts, bodyweight or loaded.
     *
     * This used to keep only sets with no weight and no added weight at all, which threw away the
     * strongest work an athlete does on these movements: the dataset's squat for this benchmark is
     * "Dumbbell Single Leg Split Squat", which is a loaded movement by definition, so a set of it
     * could never be counted and the record stayed at zero no matter what was logged.
     *
     * The ranking puts load first and reps second. A set with fewer reps on more weight is the
     * better performance and the one worth telling the model about, so "+12 kg x 5" beats
     * "bodyweight x 8" rather than being discarded for having fewer reps. Among sets with the same
     * load, the one with more reps wins.
     */
    fun refreshBenchmarkRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val allSets = repository.getAllLoggedSets()
            fun bestEffort(matcher: (String) -> Boolean): BenchmarkRecord {
                var best: BenchmarkRecord = BenchmarkRecord()
                for (set in allSets) {
                    if (set.reps <= 0) continue
                    // Hold sets are measured in seconds: the number in `reps` is not a rep count.
                    if (set.holdDurationSeconds > 0) continue
                    if (!matcher(set.exerciseName.trim().lowercase())) continue
                    val added = set.addedWeight.coerceAtLeast(0.0).toInt()
                    val candidate = BenchmarkRecord(set.reps, added)
                    // More load wins regardless of reps; equal load is decided by reps.
                    val better = when {
                        candidate.addedKg > best.addedKg -> true
                        candidate.addedKg < best.addedKg -> false
                        else -> candidate.reps > best.reps
                    }
                    if (better) best = candidate
                }
                return best
            }

            val pullUpBest = bestEffort { name ->
                name.contains("pull") && !name.contains("chin") && !name.contains("band")
            }
            val pushUpBest = bestEffort { name ->
                name.contains("push") && !name.contains("handstand")
            }
            // "Bulgarian split squat" is not a movement the app ships, and the only Bulgarian entry
            // in the dataset is the weighted one, so matching the word "bulgarian" found a row this
            // filter then rejected for having weight on it. Matched on the movement the athlete can
            // actually pick from the catalog instead.
            val bulgarianBest = bestEffort { name ->
                name.contains("single leg split squat") || name.contains("bulgarian")
            }

            suspend fun raise(
                currentReps: Int,
                currentLoad: Int,
                contender: BenchmarkRecord,
                save: suspend (Int, Int) -> Unit
            ) {
                if (!contender.isSet) return
                val beats = when {
                    contender.addedKg > currentLoad -> true
                    contender.addedKg < currentLoad -> false
                    else -> contender.reps > currentReps
                }
                if (beats) save(contender.reps, contender.addedKg)
            }

            raise(
                benchmarkPullUps.value, benchmarkPullUpsAddedWeight.value, pullUpBest,
                userPreferencesRepo::saveBenchmarkPullUps
            )
            raise(
                benchmarkPushUps.value, benchmarkPushUpsAddedWeight.value, pushUpBest,
                userPreferencesRepo::saveBenchmarkPushUps
            )
            raise(
                benchmarkBulgarianSplitSquat.value,
                benchmarkBulgarianSplitSquatAddedWeight.value,
                bulgarianBest,
                userPreferencesRepo::saveBenchmarkBulgarianSplitSquat
            )
        }
    }

    /**
     * The silent athlete context block appended to the AI prompts.
     *
     * It is never rendered in the UI: the model simply knows who it is advising so the advice can
     * be tailored. The three benchmark records are excluded from the Gemini monthly summary on
     * purpose, since they would bias the summary towards one exercise.
     */
    fun athleteContextPromptBlock(
        includeBenchmarks: Boolean = true,
        purpose: String = "tailored coaching advice"
    ): String {
        // The athlete can switch the sharing off: the prompts then carry no personal data at all.
        if (!shareAthleteDataWithAi.value) return ""
        val profile = userProfile.value
        val unit = weightUnit.value
        val sb = StringBuilder()
        sb.append("ATHLETE CONTEXT (use it silently for $purpose; never repeat or list it back unless asked):\n")
        sb.append("- Name: ").append(profile.name.ifBlank { "Athlete" }).append('\n')
        sb.append("- Age: ").append(profile.age).append('\n')
        sb.append("- Height: ").append(profile.heightCm).append(" cm\n")
        // The stored value is always kilograms: it has to be converted or the AI would read the
        // wrong number for an athlete who trains in pounds.
        sb.append("- Current weight: ")
            .append(WeightConversion.format(profile.currentWeightKg, unit))
            .append('\n')
        sb.append("- Target weight: ")
            .append(WeightConversion.format(profile.targetWeightKg, unit))
            .append('\n')
        sb.append("- Fitness level: ").append(profile.fitnessLevel).append('\n')

        if (includeBenchmarks) {
            sb.append("- Best logged efforts (max reps ever, with the load of that set): ")
                .append("Push-ups ").append(benchmarkPushUps.value)
                .append(" reps at ").append(loadedAt(benchmarkPushUpsAddedWeight.value))
                .append(", Pull-ups ").append(benchmarkPullUps.value)
                .append(" reps at ").append(loadedAt(benchmarkPullUpsAddedWeight.value))
                .append(", single leg split squat ").append(benchmarkBulgarianSplitSquat.value)
                .append(" reps at ").append(loadedAt(benchmarkBulgarianSplitSquatAddedWeight.value))
                .append('\n')
        }
        sb.append("TAILOR YOUR ANSWER to this profile: scale volume and difficulty to the fitness level, ")
            .append("respect any injury or time constraint, and never ignore the benchmark records when choosing progressions.\n\n")
        return sb.toString()
    }

    /**
     * How a benchmark's load reads to the model.
     *
     * "bodyweight" when the set had nothing on the bar, otherwise the kilograms converted to the
     * athlete's own unit ÔÇö a stored kilogram sent as-is would read as the wrong load to an athlete
     * training in pounds.
     */
    private fun loadedAt(addedKg: Int): String =
        if (addedKg <= 0) "bodyweight" else WeightConversion.format(addedKg.toDouble(), weightUnit.value)

    /**
     * The unit every weight is displayed in; the storage stays in kilograms.
     *
     * Read from the preference alone rather than from the profile. Deriving it from the profile made
     * it impossible to pick pounds during onboarding: no profile row exists until the form is
     * submitted, so the profile flow emitted the default and the chips never moved no matter how
     * many times they were tapped.
     */
    val weightUnit: StateFlow<WeightUnit> = userPreferencesRepo.preferredUnitFlow
        .map { WeightUnit.fromLabel(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUnit.KG)

    /** Whether the athlete profile may be sent to Gemini with the AI prompts. */
    val shareAthleteDataWithAi: StateFlow<Boolean> = userPreferencesRepo.shareAthleteDataFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setShareAthleteDataWithAi(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { userPreferencesRepo.setShareAthleteDataWithAi(enabled) }
    }

    /**
     * Whether exercise animations loop on their own.
     *
     * Seeded to true rather than collected lazily: the very first frame an athlete sees decides
     * whether they think the animation works, so this must not start out "not yet known".
     */
    val autoplayExerciseDemos: StateFlow<Boolean> = userPreferencesRepo.autoplayExerciseDemosFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoplayExerciseDemos(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { userPreferencesRepo.setAutoplayExerciseDemos(enabled) }
    }

    /**
     * Whether the first run landing pages still need showing.
     *
     * Two conditions, because an app update is not a new install. The flag alone is not enough: an
     * athlete who installed the app before onboarding existed has no flag, and a preference key that
     * defaults to false cannot tell them apart from someone who genuinely just installed it. So the
     * pages are also considered done whenever the athlete already has a profile or any logged data
     * on disk.
     *
     * Getting this wrong is not a cosmetic problem. Failing the flag alone showed the landing pages
     * to every existing athlete on update and then overwrote the profile they had already filled in
     * on the Profile tab, which is exactly the "it crashed on the first launch setup" report.
     */
    val onboardingCompleted: StateFlow<Boolean> = combine(
        userPreferencesRepo.onboardingCompletedFlow,
        repository.getUserProfile(),
        repository.hasAnyLoggedData()
    ) { completed, profile, hasLoggedData ->
        completed || profile != null || hasLoggedData
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun completeOnboarding() {
        viewModelScope.launch(Dispatchers.IO) { userPreferencesRepo.setOnboardingCompleted() }
    }

    val allExercises: StateFlow<List<Exercise>> = repository.getAllExercises()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>("All")

    /**
     * Magic search keyword: typing this in the "Log workout" search bar lists every exercise
     * the user created themselves.
     */
    val userGeneratedFlag = ReservedExerciseNames.USER_GENERATED

    /**
     * Magic search keyword: typing this in the "Log workout" search bar lists every exercise the
     * AI parser registered on its own.
     */
    val aiGeneratedFlag = ReservedExerciseNames.AI_GENERATED

    val userCreatedExercises: StateFlow<List<Exercise>> = repository.getUserCreatedExercises()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiGeneratedExercises: StateFlow<List<Exercise>> = repository.getAiGeneratedExercises()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === MUSCLE PART FILTER (exercises tab) ===
    private val _selectedMusclePart = MutableStateFlow<MusclePart?>(null)
    val selectedMusclePart: StateFlow<MusclePart?> = _selectedMusclePart.asStateFlow()

    fun setSelectedMusclePart(part: MusclePart?) {
        // The muscle group chips and the body part map both sort the same list; keeping both set
        // intersects them and hides most of the catalog with no explanation. ExerciseFilters owns
        // that rule so the UI and this view model can never disagree about it.
        val next = currentExerciseFilters().selectMusclePart(part)
        _selectedMusclePart.value = next.musclePart
        selectedCategory.value = next.category
    }

    /** Selects a muscle group chip, clearing any body part chosen on the map. */
    fun selectCategory(category: String?) {
        val next = currentExerciseFilters().selectCategory(category)
        selectedCategory.value = next.category
        _selectedMusclePart.value = next.musclePart
    }

    private fun currentExerciseFilters() = ExerciseFilters(
        category = selectedCategory.value,
        musclePart = _selectedMusclePart.value
    )

    @OptIn(FlowPreview::class)
    val filteredExercises: StateFlow<List<Exercise>> = combine(
        allExercises,
        searchQuery.debounce { if (it.isEmpty()) 0L else 120L },
        selectedCategory,
        _selectedMusclePart
    ) { exercises, query, category, musclePart ->
        val trimmedQuery = query.trim()
        val isAllCategory = category == null || category == "All"
        val isUserGeneratedFilter = trimmedQuery.equals(userGeneratedFlag, ignoreCase = true)
        val isAiGeneratedFilter = trimmedQuery.equals(aiGeneratedFlag, ignoreCase = true)

        // The body part chosen on the muscle map narrows the list to the exercises that actually
        // train it, so the "N exercises" counter always matches what is on screen.
        fun byMuscle(list: List<Exercise>): List<Exercise> =
            if (musclePart == null) list else list.filter { MusclePartResolver.resolve(it) == musclePart }

        // "user_generated" is a flag, not a text match: it lists the user's own exercises.
        // AI registered exercises are custom too, so they have to be excluded explicitly.
        if (isUserGeneratedFilter) {
            return@combine byMuscle(
                exercises.filter { exercise ->
                    exercise.isCustom && !exercise.isAiGenerated &&
                        (isAllCategory || exercise.category.equals(category, ignoreCase = true))
                }
            )
        }

        // "ai_generated" is a flag, not a text match: it lists the exercises the AI registered
        if (isAiGeneratedFilter) {
            return@combine byMuscle(
                exercises.filter { exercise ->
                    exercise.isAiGenerated && (isAllCategory || exercise.category.equals(category, ignoreCase = true))
                }
            )
        }

        val categoryMatches = byMuscle(
            if (isAllCategory) {
                exercises
            } else {
                exercises.filter { it.category.equals(category, ignoreCase = true) }
            }
        )

        if (trimmedQuery.isEmpty()) {
            categoryMatches
        } else {
            // Ranked by how close the name is to what was typed, not alphabetically
            ExerciseSearch.rank(categoryMatches, trimmedQuery)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === CUSTOM EXERCISES CREATED BY THE USER ===
    sealed interface CustomExerciseResult {
        data class Success(val exercise: Exercise) : CustomExerciseResult
        data class Duplicate(val existing: Exercise) : CustomExerciseResult
        data class Error(val message: String) : CustomExerciseResult
    }

    private val _customExerciseResult = MutableStateFlow<CustomExerciseResult?>(null)
    val customExerciseResult: StateFlow<CustomExerciseResult?> = _customExerciseResult.asStateFlow()

    fun consumeCustomExerciseResult() {
        _customExerciseResult.value = null
    }

    /**
     * Looks for an exercise that is already (even remotely) the same as [name].
     * Handles variations such as "push up" vs "push-up", "bench" vs "barbell bench press",
     * plurals and modifier words. Returns null when the name is free to use.
     */
    fun findSimilarExercise(name: String, excludeId: Long = 0L): Exercise? {
        if (name.isBlank()) return null
        val candidates = allExercises.value.filter { it.id != excludeId }
        if (candidates.isEmpty()) return null

        // 1. Exact name (case/whitespace insensitive) - the database enforces uniqueness on name
        candidates.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }?.let { return it }

        // 2. Fuzzy match (punctuation, plurals, modifiers, token overlap)
        return RoutineParser.findMatchingExercise(name, candidates)
    }

    /**
     * Creates a new exercise in the local database, owned by the user (so it can be edited or
     * deleted later on). Fails when the name is already taken by a similar existing exercise.
     */
    fun createCustomExercise(
        name: String,
        category: String,
        equipment: String,
        exerciseType: String,
        description: String
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        // "user_generated" / "ai_generated" are search keywords: an exercise may never take one
        if (ReservedExerciseNames.isReserved(cleanName)) {
            _customExerciseResult.value = CustomExerciseResult.Error(
                ReservedExerciseNames.explanation(cleanName)
            )
            return
        }

        findSimilarExercise(cleanName)?.let { duplicate ->
            _customExerciseResult.value = CustomExerciseResult.Duplicate(duplicate)
            return
        }

        val validCategory = category.ifBlank { MuscleCategory.CALISTHENICS }
        val exercise = Exercise(
            name = cleanName,
            category = validCategory,
            // A typed exercise has no dataset record to inherit from, so the body part is derived
            // from the chosen muscle group. Without it the exercise would never light up on the
            // body map, and the detail dialog would show no body part at all.
            bodyPart = ExerciseSchema.categoryToBodyPart(validCategory),
            exerciseType = exerciseType,
            equipment = equipment.trim().ifBlank { "Bodyweight" },
            description = description.trim(),
            // No media: the dataset has no animation for an exercise the athlete just invented.
            mediaPath = "",
            isCustom = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.createUserExercise(exercise)
            if (id == -1L) {
                // Lost a race against another insert with the same name
                val duplicate = repository.getAllExercises().first()
                    .firstOrNull { it.name.trim().equals(cleanName, ignoreCase = true) }
                _customExerciseResult.value = duplicate?.let { CustomExerciseResult.Duplicate(it) }
                    ?: CustomExerciseResult.Error("Could not save the exercise. Please try again.")
            } else {
                _customExerciseResult.value = CustomExerciseResult.Success(exercise.copy(id = id))
            }
        }
    }

    /**
     * Updates an exercise the user owns (one they created themselves or one the AI registered).
     * Built-in IronPulse library exercises are rejected by the database.
     */
    fun updateCustomExercise(
        id: Long,
        name: String,
        category: String,
        equipment: String,
        exerciseType: String,
        description: String
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        // Renaming into a search keyword is rejected for the same reason as creating one
        if (ReservedExerciseNames.isReserved(cleanName)) {
            _customExerciseResult.value = CustomExerciseResult.Error(
                ReservedExerciseNames.explanation(cleanName)
            )
            return
        }

        findSimilarExercise(cleanName, excludeId = id)?.let { duplicate ->
            _customExerciseResult.value = CustomExerciseResult.Duplicate(duplicate)
            return
        }

        val existing = allExercises.value.firstOrNull { it.id == id }
        val validCategory = category.ifBlank { MuscleCategory.CALISTHENICS }

        // Built on top of the stored row rather than from scratch: the edit dialog only offers name,
        // category, equipment, type and description, so constructing a fresh Exercise would silently
        // wipe the dataset fields (target, synergist muscles, instructions, body part) and drop the
        // ownership flags. Editing an AI exercise must not turn it into a user created one either.
        val updated = (existing ?: Exercise(
            name = cleanName,
            category = validCategory,
            exerciseType = exerciseType
        )).copy(
            id = id,
            name = cleanName,
            category = validCategory,
            bodyPart = existing?.bodyPart?.takeIf { it.isNotBlank() }
                ?: ExerciseSchema.categoryToBodyPart(validCategory),
            exerciseType = exerciseType,
            equipment = equipment.trim().ifBlank { "Bodyweight" },
            description = description.trim(),
            isCustom = existing?.isCustom ?: true,
            isAiGenerated = existing?.isAiGenerated ?: false
        )

        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.updateUserExercise(updated)
            _customExerciseResult.value = if (success) {
                CustomExerciseResult.Success(updated)
            } else {
                CustomExerciseResult.Error("This exercise is part of the IronPulse library and cannot be modified.")
            }
        }
    }

    /**
     * Deletes an exercise the user owns (one they created themselves or one the AI registered).
     * Built-in IronPulse library exercises are rejected by the database.
     */
    /**
     * Points one of the athlete's own exercises at a photo or video they picked from the gallery.
     *
     * Rejected for library exercises, and the uri is checked before it is stored. What it is given is
     * a path into this app's own files, copied at the moment the picker handed the media over: the
     * picker's own `content://` uri is only readable while the picker is alive, so persisting that
     * instead would leave a permanently blank slot on the very next read.
     */
    fun attachExerciseMedia(exerciseId: Long, uri: String, isVideo: Boolean) {
        if (!ExerciseMedia.isUsableLocalUri(uri)) return
        val existing = allExercises.value.firstOrNull { it.id == exerciseId } ?: return
        if (!ExerciseMedia.isUserOwnedMedia(existing.isCustom)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.attachExerciseMedia(exerciseId, uri.trim(), isVideo)
        }
    }

    /**
     * Clears the media the athlete attached to one of their own exercises.
     *
     * The file is removed as well as the pointer to it: the media lives in this app's storage, so
     * leaving it behind would keep private photos on disk that the athlete has asked to detach.
     */
    fun clearExerciseMedia(exerciseId: Long) {
        val existing = allExercises.value.firstOrNull { it.id == exerciseId } ?: return
        if (!ExerciseMedia.isUserOwnedMedia(existing.isCustom)) return
        viewModelScope.launch(Dispatchers.IO) {
            // Collected here rather than in the repository so the copy the exercise owns is
            // deleted even when the database write is skipped for a library row.
            ExerciseMediaStore.delete(getApplication(), exerciseId)
            repository.clearExerciseMedia(exerciseId)
        }
    }

    fun deleteCustomExercise(exercise: Exercise) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.deleteUserExercise(exercise.id)
            if (!success) {
                _customExerciseResult.value =
                    CustomExerciseResult.Error("This exercise is part of the IronPulse library and cannot be deleted.")
            }
        }
    }

    // === FULL EXERCISE EDITING (library rows included) ===

    private val _libraryExerciseResult = MutableStateFlow<LibraryExerciseResult?>(null)
    val libraryExerciseResult: StateFlow<LibraryExerciseResult?> = _libraryExerciseResult.asStateFlow()

    /** What the editor screen shows after saving a library exercise. */
    sealed class LibraryExerciseResult {
        /** @param renamedFrom the previous name, when it changed, so the screen can explain itself. */
        data class Saved(val renamedFrom: String?) : LibraryExerciseResult()
        data class Failed(val reason: String) : LibraryExerciseResult()
    }

    fun consumeLibraryExerciseResult() {
        _libraryExerciseResult.value = null
    }

    /**
     * Saves a full edit to any exercise, library row included.
     *
     * The confirmation the athlete already gave is the guard; there is no second check here, because
     * this is the one path that is meant to write a shipped row.
     *
     * A rename deliberately does not rewrite history. Logged sets keep the name they were recorded
     * under, so past workouts stay exactly as they are and only future logging follows the new name.
     *
     * @return the previous name when it changed, so the caller can tell the athlete.
     */
    fun saveExerciseEdit(edited: Exercise) {
        val previousName = allExercises.value.firstOrNull { it.id == edited.id }?.name
        // One entry point that picks the write. The editor always offers the same fields, so an owned
        // exercise must get the same complete write a library row does: an exercise with the wrong
        // target muscle has to be fixable, not just its name and equipment.
        val owned = edited.isCustom || edited.isAiGenerated
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (owned) {
                // Guarded on ownership, and writes every field the editor exposes.
                repository.updateUserExercise(edited)
            } else {
                repository.updateLibraryExercise(edited)
            }

            if (success && !owned) {
                // Remembered so the dataset reseed that runs on open leaves this row alone. Without
                // it, a release that bumps the dataset would silently undo the edit.
                LibraryEditTracker.recordEdit(getApplication(), edited.id)
            }

            _libraryExerciseResult.value = if (success) {
                LibraryExerciseResult.Saved(
                    renamedFrom = previousName?.takeIf { it != edited.name.trim() }
                )
            } else {
                LibraryExerciseResult.Failed("This exercise could not be saved. It may have been removed.")
            }
        }
    }

    private val _revertReport = MutableStateFlow<RevertFeedback?>(null)
    val revertReport: StateFlow<RevertFeedback?> = _revertReport.asStateFlow()

    /** Outcome of "Revert to original database", so the settings row can confirm what happened. */
    data class RevertFeedback(
        val libraryRestored: Int,
        val removedUserCreated: Int,
        val removedAiCreated: Int
    )

    /**
     * Restores the shipped library from the bundled dataset and drops the exercises the athlete chose
     * not to keep.
     *
     * The library rows are updated in place rather than deleted and re-inserted, so their row ids
     * survive and logged sets keep pointing at the same exercise. The rows the athlete is keeping are
     * left untouched in both directions: the seeder never overwrites a custom row, and nothing here
     * touches one either.
     *
     * Workouts are not part of this. A revert repairs the catalog; the sessions, sets, weigh-ins,
     * routines and summaries already stored are the athlete's record and are left alone.
     */
    fun revertCatalogToDatabase(
        keepUserCreated: Boolean,
        keepAiCreated: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // The seeded rows are refreshed from the asset first, so a library row that was edited or
            // renamed goes back to what the dataset says. Matching is by dataset id as well as name,
            // so a renamed row is still found and restored rather than duplicated.
            DatasetSeeder.seedFresh(getApplication(), repository.exerciseDaoForReseed())
            // Dropping the tracker last matters: it is what makes the reseed own these rows again.
            // Clearing it first would be undone by the seed above, so the library would stay edited
            // after an explicit revert.
            LibraryEditTracker.clearAll(getApplication())
            val report = repository.revertCatalogToDataset(keepUserCreated, keepAiCreated)
            _revertReport.value = RevertFeedback(
                libraryRestored = report.libraryRestored,
                removedUserCreated = report.removedUserCreated,
                removedAiCreated = report.removedAiCreated
            )
        }
    }

    fun consumeRevertReport() {
        _revertReport.value = null
    }

    // === REST TIMERS ===
    val savedRestTimers: StateFlow<List<CustomRestTimer>> = userPreferencesRepo.savedRestTimersFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, userPreferencesRepo.defaultRestTimers)

    private val _activeRestTimer = MutableStateFlow(ActiveRestTimerState())
    val activeRestTimer: StateFlow<ActiveRestTimerState> = _activeRestTimer.asStateFlow()

    private var restTimerJob: Job? = null

    /**
     * Plays the finished-timer alert (vibration + "beep beep beep ... beeeeeep").
     * Shared with the exact alarm broadcast so the alert plays exactly once per rest period,
     * no matter whether the app is open, backgrounded or completely closed.
     */
    private fun playRestFinishedAlert(context: android.content.Context, endTimeMillis: Long) {
        val key = if (endTimeMillis > 0L) endTimeMillis.toString() else "inapp_${System.currentTimeMillis()}"
        RestTimerAlertCoordinator.playFinishedAlert(context, key)
    }

    fun startRestTimer(seconds: Int, name: String = "Rest Timer") {
        val total = seconds.coerceAtLeast(1)
        val endTime = System.currentTimeMillis() + (total * 1000L)
        restTimerJob?.cancel()
        _activeRestTimer.value = ActiveRestTimerState(
            isRunning = true,
            isPaused = false,
            totalSeconds = total,
            remainingSeconds = total,
            timerName = name,
            hasFinished = false,
            endTimeMillis = endTime
        )

        // Schedule system exact alarm to fire even if phone is turned off / screen locked / app backgrounded
        RestTimerScheduler.scheduleRestTimer(getApplication(), total, name, endTime)

        restTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                val current = _activeRestTimer.value
                if (!current.isRunning || current.isPaused) continue

                val nextRemaining = ((current.endTimeMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                if (nextRemaining <= 0) {
                    _activeRestTimer.value = current.copy(
                        remainingSeconds = 0,
                        isRunning = false,
                        isPaused = false,
                        hasFinished = true
                    )
                    // Vibration & alert sound. The alert key makes sure the alarm broadcast and this
                    // loop never double up on the same timer.
                    playRestFinishedAlert(getApplication(), current.endTimeMillis)
                    break
                } else {
                    _activeRestTimer.value = current.copy(remainingSeconds = nextRemaining)
                }
            }
        }
    }

    fun pauseRestTimer() {
        val current = _activeRestTimer.value
        if (current.isRunning && !current.isPaused) {
            RestTimerScheduler.cancelRestTimer(getApplication())
            val remaining = ((current.endTimeMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            _activeRestTimer.value = current.copy(isPaused = true, remainingSeconds = remaining)
        }
    }

    fun resumeRestTimer() {
        val current = _activeRestTimer.value
        if (current.isRunning && current.isPaused && current.remainingSeconds > 0) {
            startRestTimer(current.remainingSeconds, current.timerName)
        }
    }

    fun resetRestTimer() {
        val current = _activeRestTimer.value
        if (current.totalSeconds > 0) {
            startRestTimer(current.totalSeconds, current.timerName)
        }
    }

    fun stopRestTimer() {
        restTimerJob?.cancel()
        RestTimerScheduler.cancelRestTimer(getApplication())
        _activeRestTimer.value = ActiveRestTimerState()
    }

    fun addSecondsToRestTimer(secondsToAdd: Int) {
        val current = _activeRestTimer.value
        if (current.isRunning && current.remainingSeconds > 0) {
            val newTotal = (current.totalSeconds + secondsToAdd).coerceAtLeast(1)
            val newEndTime = current.endTimeMillis + (secondsToAdd * 1000L)
            val newRemaining = ((newEndTime - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(1)
            _activeRestTimer.value = current.copy(
                totalSeconds = newTotal,
                remainingSeconds = newRemaining,
                endTimeMillis = newEndTime
            )
            // Re-schedule alarm with updated remaining time
            RestTimerScheduler.cancelRestTimer(getApplication())
            RestTimerScheduler.scheduleRestTimer(getApplication(), newRemaining, current.timerName, newEndTime)
        } else if (current.hasFinished || !current.isRunning) {
            startRestTimer(secondsToAdd.coerceAtLeast(1), if (current.timerName.isNotBlank()) current.timerName else "Rest Timer")
        }
    }

    fun saveCustomRestTimer(name: String, totalSeconds: Int) {
        val cleanName = name.trim().ifBlank { "Rest Timer" }
        val cleanSeconds = totalSeconds.coerceAtLeast(5)
        viewModelScope.launch {
            val currentList = savedRestTimers.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
            val newTimer = CustomRestTimer(name = cleanName, totalSeconds = cleanSeconds)
            if (existingIndex >= 0) {
                currentList[existingIndex] = newTimer
            } else {
                currentList.add(newTimer)
            }
            userPreferencesRepo.saveRestTimers(currentList)
        }
    }

    fun deleteCustomRestTimer(id: String) {
        viewModelScope.launch {
            val currentList = savedRestTimers.value.filter { it.id != id }
            userPreferencesRepo.saveRestTimers(currentList)
        }
    }

    // === WORKOUT SESSIONS & ACTIVE LOGGING ===
    val allSessions: StateFlow<List<WorkoutSession>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getSetsForSession(sessionId: Long): Flow<List<LoggedSet>> = repository.getSetsForSession(sessionId)

    private val _activeSession = MutableStateFlow<WorkoutSession?>(null)
    val activeSession: StateFlow<WorkoutSession?> = _activeSession.asStateFlow()

    private val _activeSets = MutableStateFlow<List<LoggedSet>>(emptyList())
    val activeSets: StateFlow<List<LoggedSet>> = _activeSets.asStateFlow()

    private val _activeSessionExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val activeSessionExercises: StateFlow<List<Exercise>> = _activeSessionExercises.asStateFlow()

    fun startNewSession(title: String = "Quick Workout") {
        // A brand new workout always starts with a clean rest timer.
        stopRestTimer()
        val now = System.currentTimeMillis()
        _activeSession.value = WorkoutSession(
            title = title,
            startTime = now,
            endTime = now
        )
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()
    }

    fun addExerciseToActiveSession(exercise: Exercise) {
        val currentSession = _activeSession.value ?: run {
            startNewSession("Quick Workout")
            _activeSession.value!!
        }

        if (_activeSessionExercises.value.none { it.id == exercise.id }) {
            _activeSessionExercises.value = _activeSessionExercises.value + exercise
        }

        val currentList = _activeSets.value.toMutableList()
        val existingSetsOfThisExercise = currentList.filter { it.exerciseId == exercise.id }
        val nextSetNumber = existingSetsOfThisExercise.size + 1
        val lastSet = existingSetsOfThisExercise.lastOrNull()

        val newSet = LoggedSet(
            sessionId = currentSession.id,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseType = exercise.exerciseType,
            setNumber = nextSetNumber,
            weight = lastSet?.weight ?: if (exercise.exerciseType == ExerciseType.STANDARD_WEIGHT) 60.0 else 0.0,
            addedWeight = lastSet?.addedWeight ?: 0.0,
            reps = lastSet?.reps ?: if (exercise.exerciseType == ExerciseType.PURE_BODYWEIGHT) 10 else 8,
            holdDurationSeconds = lastSet?.holdDurationSeconds ?: 0,
            isCompleted = false,
            timestamp = System.currentTimeMillis(),
            // The body weight that applies to this set, taken now so a later weigh-in does not
            // rewrite it. The set is done at the body weight of the day it was logged.
            bodyWeightKg = currentBodyWeightKg()
        )
        currentList.add(newSet)
        _activeSets.value = currentList
    }

    fun addSetToActiveSession(exercise: Exercise) {
        addExerciseToActiveSession(exercise)
    }

    fun updateActiveSet(index: Int, updated: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = updated
            _activeSets.value = currentList
        }
    }

    fun updateActiveSet(updated: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        val idx = currentList.indexOfFirst {
            (it.id != 0L && it.id == updated.id) || (it.exerciseId == updated.exerciseId && it.setNumber == updated.setNumber)
        }
        if (idx != -1) {
            currentList[idx] = updated
            _activeSets.value = currentList
        }
    }

    fun deleteActiveSet(index: Int) {
        val currentList = _activeSets.value.toMutableList()
        if (index in currentList.indices) {
            val removed = currentList.removeAt(index)
            // Re-index remaining sets for this exercise
            var currentSetNum = 1
            val reindexed = currentList.map { set ->
                if (set.exerciseId == removed.exerciseId) {
                    set.copy(setNumber = currentSetNum++)
                } else {
                    set
                }
            }
            _activeSets.value = reindexed
            if (reindexed.none { it.exerciseId == removed.exerciseId }) {
                _activeSessionExercises.value = _activeSessionExercises.value.filter { it.id != removed.exerciseId }
            }
        }
    }

    fun deleteActiveSet(setToDel: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        val idx = currentList.indexOfFirst {
            (it.id != 0L && it.id == setToDel.id) || (it.exerciseId == setToDel.exerciseId && it.setNumber == setToDel.setNumber)
        }
        if (idx != -1) {
            deleteActiveSet(idx)
        }
    }

    fun finishAndSaveActiveSession(notes: String = "") {
        val currentSession = _activeSession.value ?: return
        val currentSets = _activeSets.value
        // The workout is over: a running rest timer must not survive it, otherwise it would keep
        // counting down (and ring later on) during the next session.
        stopRestTimer()
        // Only sets the user explicitly ticked as "done" are saved. Default rep/weight/hold values
        // are just placeholders, so saving them would pollute history, volume and progress analytics.
        viewModelScope.launch(Dispatchers.IO) {
            val savedAt = System.currentTimeMillis()
            // The body weight is frozen at the moment the workout is finished and copied onto every
            // saved set. Sets made earlier in the same session may have been stamped with an older
            // profile weight if the athlete updated it mid workout; the finished workout must read as
            // one body weight, and that weight must never follow a later profile edit.
            val finalBodyWeightKg = resolveBodyWeightKg()
            val setsToSave = currentSets
                .filter { it.isCompleted }
                .mapIndexed { index, set ->
                    set.copy(
                        timestamp = savedAt + index,
                        bodyWeightKg = finalBodyWeightKg.takeIf { it > 0.0 } ?: set.bodyWeightKg
                    )
                }

            var savedSessionId: Long? = null
            if (setsToSave.isNotEmpty()) {
                val finalSession = currentSession.copy(
                    endTime = System.currentTimeMillis(),
                    notes = notes
                )
                val sessionId = repository.insertSession(finalSession)
                val setsWithSessionId = setsToSave.map { it.copy(sessionId = sessionId) }
                repository.insertSets(setsWithSessionId)
                savedSessionId = sessionId
                // A new bodyweight record may have just been set
                refreshBenchmarkRecords()
            }
            withContext(Dispatchers.Main) {
                _activeSession.value = null
                _activeSets.value = emptyList()
                _activeSessionExercises.value = emptyList()
            }
            // The workout is saved, so there is nothing left to offer back on the next launch.
            clearWorkoutDraft()
            refreshAndroidWidgets()
            savedSessionId?.let { maybeGenerateWorkoutSummary(it) }
        }
    }

    fun discardActiveSession() {
        stopRestTimer()
        _activeSession.value = null
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()
        clearWorkoutDraft()
    }

    // === AUTO SAVE OF THE WORKOUT IN PROGRESS ===
    //
    // A quick workout lived entirely in memory: the session, its exercises and its sets are three
    // separate StateFlows and nothing was ever written down. Closing the app from the recents
    // screen therefore threw the whole session away, down to the last set and the elapsed time.
    //
    // The state is now written to disk every few seconds and offered back on the next launch.

    /**
     * The auto saved workout found at startup, waiting for the athlete to continue or discard it.
     *
     * Null when there is nothing to restore, and again once they have decided.
     */
    private val _pendingWorkoutDraft = MutableStateFlow<WorkoutDraft?>(null)
    val pendingWorkoutDraft: StateFlow<WorkoutDraft?> = _pendingWorkoutDraft.asStateFlow()

    /** When the workout in progress was last written to disk, for the "auto saved" indicator. */
    private val _lastAutoSaveAt = MutableStateFlow<Long?>(null)
    val lastAutoSaveAt: StateFlow<Long?> = _lastAutoSaveAt.asStateFlow()

    /**
     * How often the auto save loop runs, in seconds, as chosen in the settings.
     *
     * Eager rather than while-subscribed because the loop runs for the whole app session and has
     * no screen subscription of its own to keep it alive.
     */
    private val autoSaveIntervalSeconds: StateFlow<Int> =
        userPreferencesRepo.autoSaveIntervalSecondsFlow
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                UserPreferencesRepository.DEFAULT_AUTO_SAVE_INTERVAL_SECONDS
            )

    /**
     * Writes the workout in progress to disk.
     *
     * Does nothing when no workout is running, so the loop can call it unconditionally.
     */
    fun saveActiveWorkoutDraftNow() {
        // A replacement import owns the preference file while it is applying. The autosave loop
        // must not write a stale draft over the draft that was just restored.
        val transfer = _dataTransferState.value
        if (transfer is DataTransferState.Running && transfer.operation == BackupOperation.IMPORT) return
        val routine = _activeRoutineWorkout.value
        val session = _activeSession.value
        if (routine == null && session == null) return

        val draft = if (routine != null) {
            WorkoutDraft(
                kind = WorkoutDraft.KIND_ROUTINE,
                title = routine.routineName,
                startTime = routine.startTime,
                exerciseIds = routine.exercises.map { it.id },
                sets = routine.sets,
                routineId = routine.routineId,
                routineGoal = routine.routineGoal,
                weekday = routine.weekday ?: Weekday.MONDAY,
                dayTitle = routine.dayTitle.orEmpty(),
                dayNote = routine.dayNote,
                plannedDate = routine.plannedDate,
                plannedDateKey = routine.plannedDateKey,
                routineItems = routine.routineItems
            )
        } else {
            WorkoutDraft(
                kind = WorkoutDraft.KIND_QUICK,
                title = session?.title.orEmpty(),
                startTime = session?.startTime ?: System.currentTimeMillis(),
                exerciseIds = _activeSessionExercises.value.map { it.id },
                sets = _activeSets.value
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.saveWorkoutDraft(WorkoutDraftCodec.encode(draft))
            _lastAutoSaveAt.value = System.currentTimeMillis()
        }
    }

    /** Forgets the saved workout. Called when the workout ends, is saved, or is discarded. */
    private fun clearWorkoutDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.clearWorkoutDraft()
            _lastAutoSaveAt.value = null
        }
    }

    /**
     * Puts the saved workout back into the live state, exactly as it was.
     *
     * Every set comes back with the numbers the athlete typed, the exercise list comes back in
     * the order it was built, and the start time is the original one, so the elapsed timer keeps
     * counting from where it stopped rather than restarting.
     */
    fun restorePendingWorkoutDraft() {
        val draft = _pendingWorkoutDraft.value ?: return
        _pendingWorkoutDraft.value = null

        val all = allExercises.value
        val byId = all.associateBy { it.id }
        val byName = all.associateBy { it.name.trim().lowercase() }

        // An exercise that has since been deleted is rebuilt from the sets that mention it, so the
        // athlete keeps the work they actually did even if the catalog row is gone.
        fun resolve(id: Long, name: String, type: String): Exercise =
            byId[id] ?: byName[name.trim().lowercase()] ?: Exercise(
                id = id,
                name = name,
                category = "",
                exerciseType = type,
                equipment = "Bodyweight"
            )

        if (draft.isRoutine) {
            val exercises = draft.routineItems.mapIndexed { index, item ->
                val fromSets = draft.sets.firstOrNull { it.exerciseId == item.exerciseId }
                resolve(item.exerciseId, item.exerciseName, fromSets?.exerciseType ?: item.exerciseType)
            }.ifEmpty {
                draft.exerciseIds.map { id ->
                    val fromSets = draft.sets.firstOrNull { it.exerciseId == id }
                    resolve(id, fromSets?.exerciseName.orEmpty(), fromSets?.exerciseType ?: ExerciseType.STANDARD_WEIGHT)
                }
            }
            _activeRoutineWorkout.value = ActiveRoutineWorkoutState(
                routineId = draft.routineId,
                routineName = draft.title,
                routineGoal = draft.routineGoal,
                startTime = draft.startTime,
                exercises = exercises,
                routineItems = draft.routineItems,
                sets = draft.sets,
                weekday = draft.weekday,
                dayTitle = draft.dayTitle,
                dayNote = draft.dayNote,
                plannedDate = draft.plannedDate,
                plannedDateKey = draft.plannedDateKey
            )
        } else {
            val exercises = draft.exerciseIds.mapTo(mutableListOf()) { id ->
                val fromSets = draft.sets.firstOrNull { it.exerciseId == id }
                resolve(id, fromSets?.exerciseName.orEmpty(), fromSets?.exerciseType ?: ExerciseType.STANDARD_WEIGHT)
            }
            // An exercise with no set yet still belongs to the session: the athlete added it and
            // has not logged the first set.
            if (exercises.isEmpty()) {
                draft.sets.map { it.exerciseId }.distinct().forEach { id ->
                    val fromSets = draft.sets.firstOrNull { it.exerciseId == id }
                    exercises.add(resolve(id, fromSets?.exerciseName.orEmpty(), fromSets?.exerciseType ?: ExerciseType.STANDARD_WEIGHT))
                }
            }
            _activeSession.value = WorkoutSession(
                id = 0L,
                title = draft.title.ifBlank { "Quick Workout" },
                startTime = draft.startTime,
                endTime = draft.startTime
            )
            _activeSessionExercises.value = exercises
            _activeSets.value = draft.sets
        }
    }

    /** Throws the saved workout away. The athlete asked for it, so nothing is restored. */
    fun discardPendingWorkoutDraft() {
        _pendingWorkoutDraft.value = null
        clearWorkoutDraft()
    }

    /**
     * Renames the workout in progress.
     *
     * The quick workout's title is the name of the session that gets saved into the history, so
     * naming it is how a workout is told apart from another one a month later.
     */
    fun renameActiveWorkout(title: String) {
        val clean = title.trim().take(MAX_WORKOUT_NAME_LENGTH)
        if (clean.isBlank()) return

        _activeSession.value?.let { session ->
            _activeSession.value = session.copy(title = clean)
        }
        _activeRoutineWorkout.value?.let { routine ->
            _activeRoutineWorkout.value = routine.copy(routineName = clean)
        }
    }

    fun deleteSession(session: WorkoutSession) {
        viewModelScope.launch(Dispatchers.IO) {
            // Deletes the session AND every logged set attached to it, so the exercises
            // immediately disappear from the Progress page analytics.
            repository.deleteSession(session)
            refreshAndroidWidgets()
        }
    }

    // === PROGRESS & DETAILED ANALYTICS ===
    val distinctLoggedExercises: StateFlow<List<String>> = repository.getDistinctLoggedExerciseNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedExerciseForProgress = MutableStateFlow<String>("")
    val selectedMetric = MutableStateFlow<ProgressMetric>(ProgressMetric.ESTIMATED_1RM)
    val selectedAddedWeightFilter = MutableStateFlow<Double?>(null) // null = All, or specific value like 0.0, 10.0, 15.0

    // Historical sets for selected exercise
    val exerciseHistorySets: StateFlow<List<LoggedSet>> = selectedExerciseForProgress
        .flatMapLatest { exerciseName ->
            if (exerciseName.isBlank()) flowOf(emptyList())
            else repository.getSetsForExercise(exerciseName)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Available weights dynamically populated from the historical sets of the selected exercise.
     *
     * A body weight set is isolated by the body weight it was done at, which is the snapshot the
     * set carries (see [BodyWeightTimeline]). Never by a weigh-in: weigh-ins are optional, and
     * deleting them must not make every body weight exercise lose its weight.
     */
    val availableAddedWeights: StateFlow<List<Double>> = combine(
        exerciseHistorySets,
        userProfile
    ) { sets, profile ->
        if (sets.isEmpty()) return@combine emptyList()

        val weights = sortedSetOf<Double>()

        // 1. Explicit external loads (both weight and added weight), always > 0
        for (set in sets) {
            if (set.addedWeight > 0.0) weights.add(set.addedWeight)
            if (set.weight > 0.0) weights.add(set.weight)
        }

        // 2. Body weight sets: tagged with the body weight that applied when they were logged.
        //    Each set carries its own, so a set from last month keeps its own tag after a weigh-in
        //    today, and two sets done at different body weights are two different tags. Only the
        //    bodyweight types qualify: a zero-load barbell set is an empty bar, and giving it the
        //    athlete's weight used to add a ghost "BW: 80 kg" chip. A plain 0.0 entry is never
        //    added either, it used to appear as a misleading "BW: 0 kg".
        for (set in sets) {
            if (BodyWeightTimeline.isBodyweightTagCandidate(set)) {
                BodyWeightTimeline.bodyWeightForSet(set, profile.currentWeightKg)?.let { weights.add(it) }
            }
        }

        weights.toList()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Computed chart points for progression line graph
    val progressChartPoints: StateFlow<List<ChartPoint>> = combine(
        exerciseHistorySets,
        selectedMetric,
        selectedAddedWeightFilter,
        userProfile
    ) { sets, metric, addedWeightFilter, profile ->
        if (sets.isEmpty()) return@combine emptyList()

        // Group sets by workout session / timestamp date
        val dateFormat = DATE_FORMAT_CHART.get() ?: SimpleDateFormat("MMM d", Locale.getDefault())
        val dayFormat = DATE_FORMAT_DAY.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // The body weight each set was done at, not today's. A set from last month keeps its own
        // weight after a weigh-in today, which is the whole point of the snapshot.
        fun bodyWeightOf(set: LoggedSet): Double? =
            BodyWeightTimeline.bodyWeightForSet(set, profile.currentWeightKg)

        /** Load actually lifted, or null when there is no usable body weight for the set. */
        fun effectiveWeightOrNull(set: LoggedSet): Double? =
            WorkoutMetrics.effectiveWeightKg(set, bodyWeightOf(set))

        // Filter by isolated weight tag if specified.
        //
        // The tag is a plain number inside the block, not a nullable one. A Double compared
        // against a Double? is boxed on both sides, and a boxed Double is compared by reference
        // rather than by value, so the tag is unwrapped before it is compared.
        val filteredSets = addedWeightFilter?.let { tag ->
            sets.filter { set ->
                BodyWeightTimeline.matchesIsolatedWeight(set, tag, bodyWeightOf(set))
            }
        } ?: sets

        if (filteredSets.isEmpty()) return@combine emptyList()

        // Group by day/session
        val groupedByDate = filteredSets.groupBy {
            dayFormat.format(Date(it.timestamp))
        }

        groupedByDate.mapNotNull { (_, daySets) ->
            val sampleSet = daySets.first()
            val timestamp = sampleSet.timestamp
            val label = dateFormat.format(Date(timestamp))

            val value = when (metric) {
                ProgressMetric.MAX_REPS -> {
                    // Reps need no weight at all, so every set counts
                    daySets.maxOfOrNull { it.reps.toDouble() } ?: 0.0
                }
                ProgressMetric.ESTIMATED_1RM -> {
                    // 1RM Calculation: a body weight set without a known body weight cannot be
                    // expressed in kg, so it is left out instead of guessing a number
                    val measurable = daySets.filter { effectiveWeightOrNull(it) != null }
                    measurable.maxOfOrNull { set ->
                        val effectiveWeight = effectiveWeightOrNull(set) ?: 0.0
                        WorkoutMetrics.estimateOneRepMax(effectiveWeight, set.reps)
                    } ?: return@mapNotNull null
                }
                ProgressMetric.TOTAL_VOLUME -> {
                    val measurable = daySets.filter { effectiveWeightOrNull(it) != null }
                    if (measurable.isEmpty()) return@mapNotNull null
                    measurable.sumOf { set -> (effectiveWeightOrNull(set) ?: 0.0) * set.reps }
                }
            }

            val detail = "${daySets.size} sets (${daySets.joinToString { "${it.reps}r" }})"
            ChartPoint(
                timestamp = timestamp,
                value = value,
                label = label,
                detailText = detail
            )
        }.sortedBy { it.timestamp }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auto-select first exercise if none selected
    init {
        // One off: flag the AI generated exercises stored by older app versions.
        // Waits for the library so a fresh install (still seeding) is not skipped.
        viewModelScope.launch {
            allExercises.first { it.isNotEmpty() }
            backfillAiGeneratedExercises()
            // Pick up any bodyweight record logged in a previous session
            refreshBenchmarkRecords()
        }



        // Offer back the workout that was in progress when the app was closed.
        //
        // Only offered when there is a saved draft: a normal launch has nothing to restore. The
        // draft is left alone until the athlete decides, so the copy on disk stays intact in case
        // they pick "Continue".
        viewModelScope.launch {
            val raw = userPreferencesRepo.activeWorkoutDraftFlow.first { true } ?: return@launch
            val draft = WorkoutDraftCodec.decode(raw) ?: run {
                // Unreadable draft: throw it away rather than leaving it to fail again.
                userPreferencesRepo.clearWorkoutDraft()
                return@launch
            }
            _pendingWorkoutDraft.value = draft
        }

        // The auto save loop.
        //
        // A single loop that runs for the life of the app rather than one started and stopped per
        // workout: a workout can be started from the exercises tab, from a routine, and from a
        // restored draft, and a loop that missed one of those entry points would silently stop
        // saving. This one cannot miss any of them.
        viewModelScope.launch {
            while (isActive) {
                // Read the setting on every turn of the loop, so changing it takes effect without
                // restarting the app or waiting a whole old interval.
                delay(autoSaveIntervalSeconds.value.coerceIn(5, 300) * 1_000L)
                saveActiveWorkoutDraftNow()
            }
        }

        // The update check at startup is deliberately quiet: it never reports a network failure,
        // and it only surfaces a dialog when there is actually a newer release to offer.
        viewModelScope.launch { maybeCheckForUpdatesOnStart() }

        viewModelScope.launch {
            RestTimerEventManager.timerFinishedFlow.collect {
                val current = _activeRestTimer.value
                if (current.isRunning) {
                    _activeRestTimer.value = current.copy(
                        remainingSeconds = 0,
                        isRunning = false,
                        isPaused = false,
                        hasFinished = true
                    )
                }
            }
        }

        viewModelScope.launch {
            distinctLoggedExercises.collect { list ->
                val currentSelection = selectedExerciseForProgress.value
                when {
                    // Nothing logged anymore (e.g. all app data was deleted) -> show the empty state
                    list.isEmpty() -> {
                        selectedExerciseForProgress.value = ""
                        selectedAddedWeightFilter.value = null
                    }
                    currentSelection.isBlank() -> {
                        selectedExerciseForProgress.value = list.first()
                    }
                    // The selected exercise no longer has any logged set -> fall back to a valid one
                    list.none { it == currentSelection } -> {
                        selectedExerciseForProgress.value = list.first()
                        selectedAddedWeightFilter.value = null
                    }
                }
            }
        }

        // A weight tag that no longer exists (e.g. after the historical body weight fix) must not
        // silently keep filtering the chart: there would be no chip left to switch it off.
        viewModelScope.launch {
            availableAddedWeights.collect { available ->
                val selected = selectedAddedWeightFilter.value
                if (selected != null && available.isNotEmpty() && available.none { it == selected }) {
                    selectedAddedWeightFilter.value = null
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.getAllChatSessions().collect { sessions ->
                if (sessions.isEmpty()) {
                    val initialSession = ensureDefaultChatSession()
                    withContext(Dispatchers.Main) {
                        if (currentChatSession.value == null) {
                            currentChatSession.value = initialSession
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (currentChatSession.value == null || sessions.none { it.id == currentChatSession.value?.id }) {
                            currentChatSession.value = sessions.first()
                        }
                    }
                }
            }
        }
    }

    /**
     * Guarantees that the AI coach always has a usable session. Returns the existing session when
     * one is present, otherwise creates the default "IronPulse Coach" session with its greeting.
     * Guarded by a mutex so concurrent callers (flow collector + data wipe) can never create
     * duplicate sessions.
     */
    private suspend fun ensureDefaultChatSession(): ChatSession = chatSessionMutex.withLock {
        repository.getAllChatSessions().firstOrNull()?.firstOrNull()?.let { return@withLock it }

        val now = System.currentTimeMillis()
        val initialSession = ChatSession(
            title = "IronPulse Coach",
            createdAt = now,
            lastUpdatedAt = now
        )
        repository.insertChatSession(initialSession)
        repository.insertChatMessage(
            ChatMessageEntity(
                sessionId = initialSession.id,
                sender = "AI",
                text = "Hello! I am IronPulse AI, your calisthenics & strength coach. Ask me anything about muscle-up technique, progressive overload, injury prevention, or routine design."
            )
        )
        initialSession
    }

    // === BODY WEIGHT TRACKING ===
    val weightLogs: StateFlow<List<BodyWeightLog>> = repository.getAllWeightLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyWeightChartPoints: StateFlow<List<ChartPoint>> = weightLogs.combine(flowOf(Unit)) { logs, _ ->
        val dateFormat = DATE_FORMAT_CHART.get() ?: SimpleDateFormat("MMM d", Locale.getDefault())
        logs.map { log ->
            ChartPoint(
                timestamp = log.timestamp,
                value = log.weightKg,
                label = dateFormat.format(Date(log.timestamp)),
                detailText = log.note.ifBlank { "Weigh-in" }
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWeightLog(weightKg: Double, note: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertWeightLog(
                BodyWeightLog(
                    weightKg = weightKg,
                    timestamp = System.currentTimeMillis(),
                    note = note.ifBlank { WEIGH_IN_NOTE_MORNING }
                )
            )
            // Also update current profile weight
            val currentProfile = repository.getUserProfile().firstOrNull() ?: userProfile.value
            val updated = currentProfile.copy(currentWeightKg = weightKg)
            userPreferencesRepo.saveUserProfile(updated)
            repository.saveUserProfile(updated)
            refreshAndroidWidgets()
        }
    }

    fun deleteWeightLog(log: BodyWeightLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteWeightLog(log)
            refreshAndroidWidgets()
        }
    }

    fun deleteWeightLogById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteWeightLogById(id)
            refreshAndroidWidgets()
        }
    }

    fun deleteLoggedSet(set: LoggedSet) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSet(set)
            renumberSetsForExercise(set.exerciseName)
            refreshAndroidWidgets()
        }
    }

    fun deleteSets(sets: List<LoggedSet>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSets(sets)
            // Every exercise touched by this deletion may now have gaps in its set numbering.
            sets.map { it.exerciseName }.distinct().forEach { renumberSetsForExercise(it) }
            refreshAndroidWidgets()
        }
    }

    /**
     * Renumbers the persisted sets of a single exercise so that, for every training day,
     * set numbers are always a continuous 1..N sequence. This keeps the session history in the
     * Progress tab correct after a set (or a whole day) has been deleted.
     */
    private suspend fun renumberSetsForExercise(exerciseName: String) {
        if (exerciseName.isBlank()) return
        val remainingSets = repository.getSetsForExerciseList(exerciseName)
        if (remainingSets.isEmpty()) return

        val positions = LoggedSetNumbering.positionsBySetId(remainingSets)
        for (set in remainingSets) {
            val correctNumber = positions[set.id] ?: continue
            if (set.setNumber != correctNumber) {
                repository.updateSet(set.copy(setNumber = correctNumber))
            }
        }
    }

    /**
     * Deletes every piece of user data stored by the app: workout sessions, logged sets,
     * saved routines, weigh-ins, AI coach chat history, the Gemini API key / model, the saved
     * profile and any rest timer state.
     * The exercise library (including exercises generated by the AI parser) is preserved.
     */
    fun deleteAllAppData() {
        // Immediate, in-memory cleanup so no stale session / timer survives the wipe.
        stopRestTimer()
        discardActiveSession()
        _activeRoutineWorkout.value = null
        _isChatLoading.value = false
        selectedExerciseForProgress.value = ""
        selectedAddedWeightFilter.value = null
        currentChatSession.value = null
        generatedRoutine.value = ""
        _unstructuredParseState.value = UnstructuredParseState.Idle
        _customRoutinePromptState.value = CustomRoutinePromptState.Idle
        _availableModels.value = aiService.knownModels(activeAiProvider.value)
        _modelDiscoveryError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllUserData()
            // Hold the photo lock so a picker result cannot land after the wipe and write a new
            // widget photo path into preferences that were just cleared.
            widgetPhotoMutex.withLock {
                userPreferencesRepo.clearAllPreferences()
                // The profile picture is a file in the app's own storage, not a row in the
                // database, so the database wipe does not touch it. It has to go explicitly, or
                // "delete all app data" would leave a copy of the athlete's photo behind.
                ProfileMediaStore.delete(getApplication())
                // Widget photos are app-private files too, and the preference reset has just
                // removed their only references.
                WidgetPhotoStore.deleteUnreferenced(getApplication(), emptySet())
            }
            refreshAndroidWidgets()
            // Hand the coach a brand new, empty session so the chat stays usable after the wipe.
            val freshSession = ensureDefaultChatSession()
            withContext(Dispatchers.Main) {
                currentChatSession.value = freshSession
            }
        }
    }

    // === USER DATA EXPORT / IMPORT ===

    /**
     * Writes a verified `.ironpulse` archive to the document the athlete chose with the system
     * picker. The archive is built and verified in the app's cache first; only then is it copied to
     * the provider, so a failed provider write can never be reported as a successful backup.
     */
    fun exportUserData(uri: Uri, suggestedName: String? = null) {
        if (_dataTransferState.value is DataTransferState.Running) return
        preparedImport?.cleanup()
        preparedImport = null
        dataTransferJob = viewModelScope.launch(Dispatchers.IO) {
            var temp: File? = null
            var destinationWritten = false
            try {
                _dataTransferState.value = DataTransferState.Running(
                    BackupOperation.EXPORT,
                    BackupProgress(BackupPhase.PREPARING, 0, 8)
                )
                val app = getApplication<Application>()
                temp = File.createTempFile("ironpulse-export-", ".${BackupFormat.EXTENSION}", app.cacheDir)
                val summary = backupManager.exportToFile(temp) { progress ->
                    _dataTransferState.value = DataTransferState.Running(BackupOperation.EXPORT, progress)
                }
                val displayName = queryDocumentName(uri)
                    ?: suggestedName
                    ?: summary.fileName
                val output = app.contentResolver.openOutputStream(uri, "wt")
                    ?: throw BackupException(
                        "The selected destination could not be opened for writing.",
                        com.example.data.backup.BackupErrorCode.IO_ERROR
                    )
                output.use { stream ->
                    temp.inputStream().use { input ->
                        copyWithLimit(input, stream, BackupArchiveLimits().maxArchiveBytes)
                    }
                    stream.flush()
                }
                destinationWritten = true
                val reportedSize = queryDocumentSize(uri)
                if (reportedSize != null && reportedSize != summary.sizeBytes) {
                    destinationWritten = false
                    deleteDestinationDocument(uri)
                    throw BackupException(
                        "The backup file was not fully written to the selected destination.",
                        com.example.data.backup.BackupErrorCode.IO_ERROR
                    )
                }
                _dataTransferState.value = DataTransferState.Success(
                    operation = BackupOperation.EXPORT,
                    title = "Backup saved",
                    detail = "$displayName • ${formatBytes(summary.sizeBytes)}"
                )
            } catch (e: CancellationException) {
                _dataTransferState.value = DataTransferState.Cancelled(
                    "Export cancelled. No completed backup was left in IronPulse."
                )
                throw e
            } catch (e: Exception) {
                if (!destinationWritten) deleteDestinationDocument(uri)
                _dataTransferState.value = DataTransferState.Error(
                    message = friendlyBackupError(e, importOperation = false),
                    destinationPreserved = true
                )
            } finally {
                temp?.delete()
            }
        }
    }

    /**
     * Copies the selected archive into app-private cache and fully validates it, without touching
     * any database, preference or file. Success only moves the UI to the destructive confirmation.
     */
    fun inspectUserDataImport(uri: Uri) {
        if (_dataTransferState.value is DataTransferState.Running ||
            _dataTransferState.value is DataTransferState.ReadyToImport
        ) {
            return
        }
        dataTransferJob = viewModelScope.launch(Dispatchers.IO) {
            var temp: File? = null
            try {
                _dataTransferState.value = DataTransferState.Running(
                    BackupOperation.IMPORT,
                    BackupProgress(BackupPhase.VALIDATING, 0, 5)
                )
                val app = getApplication<Application>()
                temp = File.createTempFile("ironpulse-import-", ".${BackupFormat.EXTENSION}", app.cacheDir)
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw BackupException(
                        "The selected backup could not be opened.",
                        com.example.data.backup.BackupErrorCode.IO_ERROR
                    )
                input.use { stream ->
                    temp.outputStream().use { output ->
                        copyWithLimit(stream, output, BackupArchiveLimits().maxArchiveBytes)
                    }
                }
                val prepared = backupManager.inspectFile(
                    archive = temp,
                    displayName = queryDocumentName(uri)
                ) { progress ->
                    _dataTransferState.value = DataTransferState.Running(BackupOperation.IMPORT, progress)
                }
                preparedImport?.cleanup()
                preparedImport = prepared
                _dataTransferState.value = DataTransferState.ReadyToImport(prepared.preview)
            } catch (e: CancellationException) {
                temp?.delete()
                _dataTransferState.value = DataTransferState.Cancelled(
                    "Import cancelled before anything was changed."
                )
                throw e
            } catch (e: Exception) {
                temp?.delete()
                _dataTransferState.value = DataTransferState.Error(
                    message = friendlyBackupError(e, importOperation = true),
                    destinationPreserved = true
                )
            }
        }
    }

    /** Applies the already-validated archive after the athlete confirms the replacement. */
    fun confirmUserDataImport() {
        val prepared = preparedImport ?: return
        if (_dataTransferState.value is DataTransferState.Running) return
        preparedImport = null
        dataTransferJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main.immediate) {
                    resetTransientStateForReplacement()
                }
                _dataTransferState.value = DataTransferState.Running(
                    BackupOperation.IMPORT,
                    BackupProgress(BackupPhase.STAGING, 0, 9)
                )
                val summary = backupManager.applyImport(prepared) { progress ->
                    _dataTransferState.value = DataTransferState.Running(BackupOperation.IMPORT, progress)
                }
                withContext(Dispatchers.Main.immediate) {
                    resetTransientStateForReplacement()
                    reloadPendingWorkoutDraftLater()
                }
                refreshAndroidWidgets()
                _dataTransferState.value = DataTransferState.Success(
                    operation = BackupOperation.IMPORT,
                    title = "Backup restored",
                    detail = "Restored the backup created by IronPulse ${summary.preview.appVersionName} " +
                        "on ${summary.preview.exportedAt}. API keys and your profile picture were kept on this device."
                )
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main.immediate) {
                    resetTransientStateForReplacement()
                    reloadPendingWorkoutDraftLater()
                }
                _dataTransferState.value = DataTransferState.Cancelled(
                    "Import cancelled. Your previous data was restored."
                )
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    resetTransientStateForReplacement()
                    reloadPendingWorkoutDraftLater()
                }
                _dataTransferState.value = DataTransferState.Error(
                    message = friendlyBackupError(e, importOperation = true),
                    destinationPreserved = (e as? BackupException)?.dataPreserved ?: false
                )
            } finally {
                prepared.cleanup()
            }
        }
    }

    /** Drops a validated archive that has not been applied yet. */
    fun cancelPendingUserDataImport() {
        preparedImport?.cleanup()
        preparedImport = null
        if (_dataTransferState.value is DataTransferState.ReadyToImport) {
            _dataTransferState.value = DataTransferState.Idle
        }
    }

    /** Stops an export (or a pre-apply validation) without touching existing user data. */
    fun cancelDataTransfer() {
        val running = _dataTransferState.value as? DataTransferState.Running ?: return
        val canCancel = running.operation == BackupOperation.EXPORT ||
            running.progress.phase == BackupPhase.PREPARING ||
            running.progress.phase == BackupPhase.VALIDATING
        if (!canCancel) return
        dataTransferJob?.cancel()
    }

    /** Clears a completed/failed message so the Settings card returns to its normal state. */
    fun dismissDataTransferMessage() {
        if (_dataTransferState.value !is DataTransferState.Running &&
            _dataTransferState.value !is DataTransferState.ReadyToImport
        ) {
            _dataTransferState.value = DataTransferState.Idle
        }
    }

    private suspend fun resetTransientStateForReplacement() {
        stopRestTimer()
        _activeSession.value = null
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()
        _activeRoutineWorkout.value = null
        _pendingWorkoutDraft.value = null
        currentChatSession.value = null
        _isChatLoading.value = false
        selectedExerciseForProgress.value = ""
        selectedAddedWeightFilter.value = null
        generatedRoutine.value = ""
        _unstructuredParseState.value = UnstructuredParseState.Idle
        _customRoutinePromptState.value = CustomRoutinePromptState.Idle
        _monthlySummaryState.value = MonthlySummaryState.Idle
        _availableModels.value = aiService.knownModels(activeAiProvider.value)
        _modelDiscoveryError.value = null
    }

    private fun reloadPendingWorkoutDraftLater() {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = userPreferencesRepo.activeWorkoutDraftFlow.first { true } ?: return@launch
            val draft = WorkoutDraftCodec.decode(raw) ?: run {
                userPreferencesRepo.clearWorkoutDraft()
                return@launch
            }
            _pendingWorkoutDraft.value = draft
        }
    }

    private fun queryDocumentName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull()

    private fun queryDocumentSize(uri: Uri): Long? = runCatching {
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0L } else null
        }
    }.getOrNull()

    private fun deleteDestinationDocument(uri: Uri) {
        runCatching {
            val app = getApplication<Application>()
            if (DocumentsContract.isDocumentUri(app, uri)) {
                DocumentsContract.deleteDocument(app.contentResolver, uri)
            }
        }
    }

    private fun friendlyBackupError(error: Throwable, importOperation: Boolean): String {
        if (error is BackupException) return error.message ?: "The backup operation failed."
        return if (importOperation) {
            "The import failed unexpectedly. Your existing data was not replaced."
        } else {
            "The export failed unexpectedly. Your existing IronPulse data was not changed."
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        return String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }

    // === POST-WORKOUT AI SUMMARIES ===
    sealed interface WorkoutSummaryState {
        data object Idle : WorkoutSummaryState
        data class Loading(val workoutId: Long) : WorkoutSummaryState
        data class Success(val summary: WorkoutAiSummary) : WorkoutSummaryState
        data class Error(val workoutId: Long, val message: String) : WorkoutSummaryState
    }

    val workoutSummaries: StateFlow<List<WorkoutAiSummary>> = repository.getAllWorkoutAiSummaries()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _workoutSummaryState = MutableStateFlow<WorkoutSummaryState>(WorkoutSummaryState.Idle)
    val workoutSummaryState: StateFlow<WorkoutSummaryState> = _workoutSummaryState.asStateFlow()

    fun dismissWorkoutSummary() {
        _workoutSummaryState.value = WorkoutSummaryState.Idle
    }

    /** Opens the already persisted summary without contacting any AI provider. */
    fun openWorkoutSummary(workoutId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = repository.getWorkoutAiSummary(workoutId) ?: return@launch
            withContext(Dispatchers.Main) {
                _workoutSummaryState.value = WorkoutSummaryState.Success(summary)
            }
        }
    }

    /** Retries only when no summary exists; an existing immutable summary is never replaced. */
    fun retryWorkoutSummary(workoutId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.getWorkoutAiSummary(workoutId) != null) {
                openWorkoutSummary(workoutId)
                return@launch
            }
            if (!userPreferencesRepo.postWorkoutAiSummariesFlow.first()) {
                withContext(Dispatchers.Main) {
                    _workoutSummaryState.value = WorkoutSummaryState.Error(
                        workoutId,
                        "Post-workout AI summaries are disabled in Settings."
                    )
                }
                return@launch
            }
            generateWorkoutSummary(workoutId)
        }
    }

    private fun maybeGenerateWorkoutSummary(workoutId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!userPreferencesRepo.postWorkoutAiSummariesFlow.first()) return@launch
            if (repository.getWorkoutAiSummary(workoutId) != null) return@launch
            generateWorkoutSummary(workoutId)
        }
    }

    private suspend fun generateWorkoutSummary(workoutId: Long) {
        withContext(Dispatchers.Main) {
            _workoutSummaryState.value = WorkoutSummaryState.Loading(workoutId)
        }
        val result = workoutSummaryGenerator.generate(workoutId)
        withContext(Dispatchers.Main) {
            _workoutSummaryState.value = result.fold(
                onSuccess = { WorkoutSummaryState.Success(it) },
                onFailure = { error ->
                    WorkoutSummaryState.Error(
                        workoutId,
                        geminiErrorMessage(error, "The post-workout summary")
                    )
                }
            )
        }
    }

    // === GEMINI MONTHLY SUMMARY (PROGRESS TAB) ===
    sealed interface MonthlySummaryState {
        object Idle : MonthlySummaryState
        data class Loading(val exerciseName: String) : MonthlySummaryState
        data class Success(val exerciseName: String, val windowLabel: String, val markdown: String) : MonthlySummaryState
        data class Error(val message: String) : MonthlySummaryState
    }

    private val _monthlySummaryState = MutableStateFlow<MonthlySummaryState>(MonthlySummaryState.Idle)
    val monthlySummaryState: StateFlow<MonthlySummaryState> = _monthlySummaryState.asStateFlow()

    /** All previously generated summaries, newest first. */
    val savedSummaries: StateFlow<List<GeminiSummary>> = repository.getAllGeminiSummaries()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSavedSummariesOpen = MutableStateFlow(false)
    val isSavedSummariesOpen: StateFlow<Boolean> = _isSavedSummariesOpen.asStateFlow()

    /** The summary opened from the history list, shown as a read-only detail dialog. */
    private val _openedSavedSummary = MutableStateFlow<GeminiSummary?>(null)
    val openedSavedSummary: StateFlow<GeminiSummary?> = _openedSavedSummary.asStateFlow()

    fun openSavedSummaries() {
        _isSavedSummariesOpen.value = true
    }

    fun closeSavedSummaries() {
        _isSavedSummariesOpen.value = false
        _openedSavedSummary.value = null
    }

    fun openSavedSummary(summary: GeminiSummary) {
        _openedSavedSummary.value = summary
    }

    fun closeOpenedSavedSummary() {
        _openedSavedSummary.value = null
    }

    fun deleteSavedSummary(summary: GeminiSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGeminiSummary(summary)
            if (_openedSavedSummary.value?.id == summary.id) {
                _openedSavedSummary.value = null
            }
        }
    }

    /** Incremented on every request and on every dismissal so stale replies can be dropped. */
    private var monthlySummaryRequestId = 0

    fun dismissMonthlySummary() {
        monthlySummaryRequestId++
        _monthlySummaryState.value = MonthlySummaryState.Idle
    }

    /**
     * Sends the last [MonthlyExerciseAnalyzer.WINDOW_DAYS] days of sets for the exercise currently
     * selected in the Progress tab to Gemini, which replies with a formatted strength trend
     * analysis for that single exercise. The report is stored so it can be re-read later.
     */
    fun requestMonthlySummary() {
        val exerciseName = selectedExerciseForProgress.value.trim()
        if (exerciseName.isBlank()) {
            _monthlySummaryState.value = MonthlySummaryState.Error("Select an exercise first.")
            return
        }
        if (_monthlySummaryState.value is MonthlySummaryState.Loading) return

        // No key -> the same "API key required" dialog used by every other AI feature
        if (!hasGeminiApiKey()) {
            _monthlySummaryState.value = MonthlySummaryState.Error(MISSING_API_KEY_MESSAGE)
            return
        }

        val requestId = ++monthlySummaryRequestId
        _monthlySummaryState.value = MonthlySummaryState.Loading(exerciseName)

        viewModelScope.launch(Dispatchers.IO) {
            val exerciseType = allExercises.value.firstOrNull { it.name == exerciseName }?.exerciseType
                ?: exerciseHistorySets.value.firstOrNull()?.exerciseType
                ?: ExerciseType.STANDARD_WEIGHT
            val summaryUnit = weightUnit.value

            val allSets = repository.getSetsForExerciseList(exerciseName)

            // The body weight each set was done at, taken from the snapshot the set carries.
            //
            // The set is the unit here, not the timestamp: a set carries its own body weight, and
            // two sets of the same session can have been done at different weights. A set logged
            // before the snapshot existed has none, and falls back to the profile weight, which is
            // the best guess available and the same fallback every other screen uses.
            fun bodyWeightForSet(set: LoggedSet): Double? =
                BodyWeightTimeline.bodyWeightForSet(set, userProfile.value.currentWeightKg)

            val summary = MonthlyExerciseAnalyzer.build(
                exerciseName = exerciseName,
                exerciseType = exerciseType,
                weightUnit = summaryUnit,
                sets = allSets,
                bodyWeightForSet = ::bodyWeightForSet
            )

            // The user cancelled, switched exercise or started another request in the meantime
            if (requestId != monthlySummaryRequestId) return@launch

            // A summary is only meaningful once there is enough data to read a trend from
            if (summary.setCount <= MIN_SETS_FOR_MONTHLY_SUMMARY) {
                _monthlySummaryState.value = MonthlySummaryState.Error(
                    "\"$exerciseName\" only has ${summary.setCount} logged set${if (summary.setCount == 1) "" else "s"} " +
                            "in the last ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days. Log more than " +
                            "$MIN_SETS_FOR_MONTHLY_SUMMARY sets of it before generating a summary."
                )
                return@launch
            }

            val prompt = buildMonthlySummaryPrompt(summary)
            val result = runCatching { generateAiText(prompt) }

            if (requestId != monthlySummaryRequestId) return@launch

            result.fold(
                onSuccess = { markdown ->
                    val report = markdown.trim()
                    repository.insertGeminiSummary(
                        GeminiSummary(
                            exerciseName = exerciseName,
                            report = report,
                            windowDays = MonthlyExerciseAnalyzer.WINDOW_DAYS,
                            daysLogged = summary.daysLogged,
                            setCount = summary.setCount,
                            bestOneRm = summary.bestOneRm,
                            trendPercent = summary.trendPercent,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    _monthlySummaryState.value = MonthlySummaryState.Success(
                        exerciseName = exerciseName,
                        windowLabel = "Last ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days",
                        markdown = report
                    )
                },
                onFailure = { error ->
                    _monthlySummaryState.value = MonthlySummaryState.Error(monthlySummaryErrorMessage(error))
                }
            )
        }
    }

    private fun monthlySummaryErrorMessage(error: Throwable): String =
        aiErrorMessage(error, "The monthly summary")

    // === SHARED AI ERROR DIALOG (no mock/offline data anywhere in the app) ===
    data class AiErrorDialogState(val title: String, val message: String)

    private val _aiErrorDialog = MutableStateFlow<AiErrorDialogState?>(null)
    val aiErrorDialog: StateFlow<AiErrorDialogState?> = _aiErrorDialog.asStateFlow()

    fun showAiError(title: String, message: String) {
        _aiErrorDialog.value = AiErrorDialogState(title, message)
    }

    fun dismissAiError() {
        _aiErrorDialog.value = null
    }

    /** Runs a plain-text generation through the shared AI service. */
    private suspend fun generateAiText(prompt: String): String {
        val provider = aiService.activeProviderFlow.first()
        return aiService.completeText(
            prompt = prompt,
            provider = provider,
            apiKeyFallback = fallbackApiKey(provider)
        )
    }

    /** True when the active provider has a usable key, including the legacy Google fallbacks. */
    fun hasGeminiApiKey(): Boolean {
        val config = aiConfiguration.value
        if (config.provider.isCustomEndpoint) return config.baseUrl.isNotBlank()
        if (config.apiKey.isNotBlank()) return true
        return config.provider == AiProviderId.GOOGLE &&
            fallbackApiKey(AiProviderId.GOOGLE).isNotBlank()
    }

    /**
     * Shows the "missing API key" dialog when the active provider cannot be reached.
     * @return true when the caller must abort the request.
     */
    private fun ensureApiKeyOrExplain(featureName: String): Boolean {
        if (hasGeminiApiKey()) return false
        showAiError("API Key Required", MISSING_API_KEY_MESSAGE)
        Log.w("MainViewModel", "$featureName requires an AI API key")
        return true
    }

    /** Translates a normalized provider failure into a message the athlete can act on. */
    private fun aiErrorMessage(error: Throwable, featureName: String): String {
        val ai = error as? AiException
        if (ai == null) {
            val raw = error.message.orEmpty()
            return if (raw.isBlank()) "$featureName failed. Please try again."
            else "$featureName failed: $raw"
        }
        return when (ai.kind) {
            AiErrorKind.CONFIGURATION -> ai.message.ifBlank { MISSING_API_KEY_MESSAGE }
            AiErrorKind.CONSENT_REQUIRED -> ai.message
            AiErrorKind.AUTHENTICATION ->
                "The AI provider rejected your API key. Open Profile & Settings and check the key " +
                    "saved for ${activeAiProvider.value.displayName}."
            AiErrorKind.PERMISSION ->
                "The saved API key is not allowed to use this model. Choose another model or check " +
                    "the key's permissions."
            AiErrorKind.RATE_LIMIT ->
                "The AI provider is rate limiting requests right now. Wait about a minute and try again."
            AiErrorKind.MODEL_NOT_FOUND ->
                "The selected model or endpoint was not found. Refresh the model list in Profile & Settings."
            AiErrorKind.INVALID_REQUEST ->
                "The AI provider could not process the request. ${ai.message}"
            AiErrorKind.UNSUPPORTED_CAPABILITY ->
                "The selected model does not support this feature. ${ai.message}"
            AiErrorKind.NETWORK ->
                "Could not reach the AI provider. Check your connection and the endpoint URL."
            AiErrorKind.TIMEOUT ->
                "The AI provider took too long to answer. Please try again."
            AiErrorKind.CANCELLED -> "The request was cancelled."
            AiErrorKind.SERVER ->
                "The AI provider is temporarily unavailable. Please try again."
            AiErrorKind.PARSE ->
                "The AI provider returned an answer the app could not read. Please try again."
            AiErrorKind.TOOL_EXECUTION -> ai.message
            AiErrorKind.UNKNOWN ->
                if (ai.message.isBlank()) "$featureName failed. Please try again."
                else "$featureName failed: ${ai.message}"
        }
    }

    /** Legacy name kept for the existing call sites; all providers now share one error mapping. */
    private fun geminiErrorMessage(error: Throwable, featureName: String): String =
        aiErrorMessage(error, featureName)

    /**
     * Prompt that pins the response to the markdown subset the in-app renderer supports
     * (headings, bullets, numbered lists, bold, italic) and forbids tables, preamble and sign-off.
     */
    private fun buildMonthlySummaryPrompt(summary: MonthlyExerciseSummary): String {
        val data = summary.toPromptPayload(MonthlyExerciseAnalyzer.WINDOW_DAYS)
        val change = when {
            summary.daysLogged < 2 -> "not enough sessions yet to measure a trend"
            summary.trendPercent > 2.0 -> "improving (+${summary.formatNumber(summary.trendPercent)}%)"
            summary.trendPercent < -2.0 -> "declining (${summary.formatNumber(summary.trendPercent)}%)"
            else -> "stable (${summary.formatNumber(summary.trendPercent)}%)"
        }

        return buildString {
            append("You are IronPulse AI analysing ONE exercise from an athlete's training log.\n\n")
            // The three benchmark exercises are deliberately NOT sent here: they would bias the
            // summary towards exercises the athlete is not asking about.
            append(
                athleteContextPromptBlock(
                    includeBenchmarks = false,
                    purpose = "tailored analysis and coaching takeaways"
                )
            )
            append("LOGGED DATA FOR THE EXERCISE (already computed by the app, treat it as the single source of truth):\n")
            append(data)
            append("\nWRITTEN TREND (computed from the data above): ").append(change).append("\n\n")

            append("YOUR TASK:\n")
            append("Analyse the overall strength trend for \"${summary.exerciseName}\" over the last ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days.\n\n")

            append("OUTPUT FORMAT RULES (strictly follow them):\n")
            append("- Reply with MARKDOWN only. No preamble, no closing remarks, no signature, no \"here is the summary\" sentence.\n")
            append("- Start immediately with the level 1 heading below.\n")
            append("- Use ONLY these markdown elements: \"# \", \"## \", \"- \" bullets, \"1. \" numbered items, \"**bold**\", \"*italic*\".\n")
            append("- NEVER use markdown tables (they are not rendered by this app), horizontal rules, images or code blocks.\n")
            append("- Every bullet must fit on a single line and be short and specific.\n")
            append("- Use the athlete's unit (${summary.weightUnit.suffix}) and their exact numbers from the data block. Never invent numbers.\n")
            append("- Keep the whole answer between 200 and 320 words.\n\n")

            append("REQUIRED STRUCTURE (use exactly these headings, in this order):\n")
            append("# ${summary.exerciseName} ÔÇö ${MonthlyExerciseAnalyzer.WINDOW_DAYS} Day Summary\n")
            append("## Trend\n")
            append("One short paragraph (2-3 sentences) stating whether strength is trending up, down or holding steady, and the single biggest reason why.\n")
            append("## Key Numbers\n")
            append("4 to 6 bullets covering: training days, total sets, total volume, best estimated 1RM with the set it came from, and the change across the period.\n")
            append("## Session Log\n")
            append("3 to 8 bullets, one per training day, in the format \"- Mar 3 ÔÇö 4 sets, best set 80kg x 5, est. 1RM 93kg\". Cover the most representative days, not necessarily all of them.\n")
            append("## What The Numbers Say\n")
            append("2 to 3 bullets interpreting the data: consistency, volume vs intensity, rep range, added-weight progression, or plateaus. Be analytical and honest, including any warning signs.\n")
            append("## Coaching Takeaways\n")
            append("3 numbered items with concrete, actionable advice for the next ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days, referencing sets and reps where useful.\n")
            append("## Next Month Focus\n")
            append("2 to 3 bullets naming the single most important thing to progress.\n")
        }
    }

    // === APP SETTINGS (settings page) ===

    val appSettings: StateFlow<UserPreferencesRepository.AppSettings> = userPreferencesRepo.appSettingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserPreferencesRepository.AppSettings()
        )

    /** Explicit permission for personal workout retrieval and routine proposals. */
    val aiWorkoutDataAccess: StateFlow<Boolean> = userPreferencesRepo.aiWorkoutDataAccessFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Optional automatic post-workout summary generation. */
    val postWorkoutAiSummaries: StateFlow<Boolean> = userPreferencesRepo.postWorkoutAiSummariesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAiWorkoutDataAccess(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setAiWorkoutDataAccess(enabled)
            if (!enabled) {
                db.chatDao().expirePendingRoutineProposals()
            }
        }
    }

    fun setPostWorkoutAiSummaries(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setPostWorkoutAiSummaries(enabled)
        }
    }

    val androidWidgetThemes: StateFlow<com.example.data.model.AndroidWidgetThemes> =
        userPreferencesRepo.androidWidgetThemesFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.example.data.model.AndroidWidgetThemes()
            )

    fun setAndroidWidgetTheme(
        kind: com.example.data.model.AndroidWidgetKind,
        mode: com.example.ui.theme.ThemeMode
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setAndroidWidgetTheme(kind, mode)
            com.example.widget.WorkoutWidgetUpdater.updateAll(getApplication())
        }
    }

    /**
     * The single boolean that drives every Liquid Glass surface.
     *
     * Kept separate from [appSettings] so the root only recomposes when this one preference changes,
     * not when the athlete edits the weight unit or the autosave interval.
     */
    val liquidGlassEnabled: StateFlow<Boolean> = userPreferencesRepo.liquidGlassEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Whether the system bars are hidden.
     *
     * The activity applies this to the window. It is kept separate from [appSettings] for the same
     * reason as Liquid Glass: only the window policy needs to react.
     */
    val fullscreenEnabled: StateFlow<Boolean> = userPreferencesRepo.fullscreenEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * The selected theme.
     *
     * Its own flow, like Liquid Glass, so a change to any other preference never re-applies the
     * theme and a theme change never disturbs an unrelated setting. The initial value is the same
     * IronPulse default the repository falls back to.
     */
    val themeMode: StateFlow<ThemeMode> = userPreferencesRepo.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.IRONPULSE)

    /** Selects a theme and persists it immediately. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setThemeMode(mode)
        }
    }

    /**
     * The athlete's accent override, or blank for the original per-theme IronPulse blue.
     *
     * Its own flow so a theme or Liquid Glass change never re-applies the accent and an accent
     * change never disturbs an unrelated setting.
     */
    val accentColorHex: StateFlow<String> = userPreferencesRepo.accentColorHexFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Persists a preset/custom accent, or clears the override when [hex] is null or blank. */
    fun setAccentColor(hex: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setAccentColorHex(hex)
        }
    }

    /**
     * Whether the athlete's week starts on Monday.
     *
     * Read straight from the settings, so the routine editor, the dashboard, the weekly summaries
     * and the AI import all agree on where the week begins.
     */
    fun weekStartsOnMonday(): Boolean = appSettings.value.weekStartsOnMonday

    /** How often the in-progress workout is written to disk, in seconds. */
    fun setAutoSaveIntervalSeconds(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setAutoSaveIntervalSeconds(seconds)
        }
    }

    /** Whether a newer release is looked up automatically when the app starts. */
    fun setAutoCheckUpdatesOnStart(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setAutoCheckUpdatesOnStart(enabled)
        }
    }

    /** Turns the Liquid Glass treatment on or off and persists the choice. */
    fun setLiquidGlassEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setLiquidGlassEnabled(enabled)
        }
    }

    /** Hides or restores the system bars and persists the choice. */
    fun setFullscreenEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setFullscreenEnabled(enabled)
        }
    }

    /**
     * The athlete's Home widget configuration.
     *
     * The Home screen resolves this with the widgets it knows about, so removing a widget from a
     * release does not leave a hole and adding one does not drop it from the page.
     */
    val homeWidgetConfig: StateFlow<HomeWidgetConfig> = userPreferencesRepo.homeWidgetConfigFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeWidgetConfig()
        )

    /** Kept for the drag gesture, which only changes the visible order. */
    val homeWidgetOrder: StateFlow<List<String>> = homeWidgetConfig
        .map { it.order }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The app-private motivation photo, or blank when none is selected. */
    val homeWidgetPhotoPath: StateFlow<String> = userPreferencesRepo.homeWidgetPhotoPathFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The target number of workouts per week shown by the goal widget. */
    val homeWidgetWeeklyGoal: StateFlow<Int> = userPreferencesRepo.homeWidgetWeeklyGoalFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UserPreferencesRepository.DEFAULT_HOME_WIDGET_WEEKLY_GOAL
        )

    private val _widgetPhotoError = MutableStateFlow<String?>(null)

    /** A human-readable reason the last picked photo was not kept, or null. */
    val widgetPhotoError: StateFlow<String?> = _widgetPhotoError.asStateFlow()

    // Serializes picking, removing and resetting the widget photo. Without this, a picker result
    // could land after a removal and leave a file nothing references.
    private val widgetPhotoMutex = Mutex()

    fun setHomeWidgetOrder(order: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userPreferencesRepo.setHomeWidgetOrder(order)
            } catch (e: Exception) {
                // The local order stays on screen; the next successful write or the next launch
                // simply reads the last durable order.
                Log.w("MainViewModel", "could not save the Home widget order", e)
            }
        }
    }

    /**
     * Writes the visible order and the removed-widget set in one DataStore edit.
     *
     * The Home screen keeps an optimistic local copy for immediate feedback; this is the durable
     * half, and the single edit is what makes an add or a removal survive a process death intact.
     */
    fun setHomeWidgetConfig(order: List<String>, hiddenIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userPreferencesRepo.setHomeWidgetConfig(order, hiddenIds)
            } catch (e: Exception) {
                Log.w("MainViewModel", "could not save the Home widget configuration", e)
            }
        }
    }

    fun setHomeWidgetWeeklyGoal(goal: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userPreferencesRepo.setHomeWidgetWeeklyGoal(goal)
            } catch (e: Exception) {
                Log.w("MainViewModel", "could not save the weekly goal", e)
            }
        }
    }

    /**
     * Copies a picked picture into app storage and makes it the photo widget's image.
     *
     * The previous copy is deleted only after the new path has been written, so a failed import
     * leaves the card exactly as it was. The widget being hidden is treated as a cancelled pick:
     * the new file is discarded instead of becoming an unreferenced orphan.
     */
    fun importWidgetPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            widgetPhotoMutex.withLock {
                _widgetPhotoError.value = null
                val path = WidgetPhotoStore.import(getApplication(), uri)
                if (path == null) {
                    _widgetPhotoError.value =
                        "That image could not be read. Choose a different photo."
                    return@withLock
                }

                val config = userPreferencesRepo.homeWidgetConfigFlow.first()
                val hidden = config.hiddenIds ?: HomeWidget.defaultHiddenIds
                if (HomeWidget.PHOTO.id in hidden) {
                    WidgetPhotoStore.delete(getApplication(), path)
                    return@withLock
                }

                try {
                    userPreferencesRepo.setHomeWidgetPhotoPath(path)
                } catch (e: Exception) {
                    Log.w("MainViewModel", "could not save the widget photo path", e)
                    WidgetPhotoStore.delete(getApplication(), path)
                    _widgetPhotoError.value = "The photo could not be saved. Try again."
                    return@withLock
                }

                // The new path is durable now; every other widget photo is unreferenced.
                WidgetPhotoStore.deleteUnreferenced(getApplication(), setOf(path))
            }
        }
    }

    /** Removes the widget photo and every file that is no longer referenced. */
    fun clearWidgetPhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            widgetPhotoMutex.withLock {
                try {
                    userPreferencesRepo.setHomeWidgetPhotoPath("")
                    WidgetPhotoStore.deleteUnreferenced(getApplication(), emptySet())
                    _widgetPhotoError.value = null
                } catch (e: Exception) {
                    // The card falls back to its empty state if the path was cleared but the
                    // files could not be removed; the next cleanup finishes the job.
                    Log.w("MainViewModel", "could not remove the widget photo", e)
                }
            }
        }
    }

    fun clearWidgetPhotoError() {
        _widgetPhotoError.value = null
    }

    /**
     * Restores the stock Home widgets and removes the widget photos.
     *
     * The preference reset is a single DataStore edit, so the configuration is either the old one
     * or the default one. The files are cleaned afterwards; if that cleanup fails the app is still
     * in a valid state and the next reset or app-data wipe removes the leftovers.
     */
    fun restoreDefaultHomeWidgets(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = widgetPhotoMutex.withLock {
                try {
                    userPreferencesRepo.resetHomeWidgets()
                    WidgetPhotoStore.deleteUnreferenced(getApplication(), emptySet())
                    _widgetPhotoError.value = null
                    true
                } catch (e: Exception) {
                    Log.w("MainViewModel", "could not restore the default Home widgets", e)
                    false
                }
            }
            withContext(Dispatchers.Main) { onResult(restored) }
        }
    }

    /** Where a week begins, as a flow for the screens that redraw when the setting changes. */
    val weekStartFlow: StateFlow<Boolean> = appSettings
        .map { it.weekStartsOnMonday }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Changes the first day of the week. */
    fun setWeekStartsOnMonday(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setWeekStartsOnMonday(enabled)
            refreshAndroidWidgets()
        }
    }

    fun setWeightUnit(unit: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setWeightUnit(unit)
            refreshAndroidWidgets()
            // Keep the profile in sync so every body weight calculation follows the setting.
            //
            // Only an existing profile is touched. Writing one when there is none would invent a
            // row with placeholder values and mark the profile as saved, which is both the mock
            // data this app refuses to seed and enough to make the onboarding gate think the
            // athlete is set up. Before the profile exists (during onboarding) the setting alone is
            // what the unit chips stay on.
            val profile = repository.getUserProfile().firstOrNull() ?: return@launch
            val updated = profile.copy(preferredUnit = unit)
            userPreferencesRepo.saveUserProfile(updated)
            repository.saveUserProfile(updated)
            refreshAndroidWidgets()
        }
    }

    fun setRestTimerVibration(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { userPreferencesRepo.setRestTimerVibration(enabled) }
        RestTimerAlertCoordinator.setAlertPreferences(
            getApplication(),
            enabled,
            appSettings.value.restTimerSound
        )
    }

    fun setRestTimerSound(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { userPreferencesRepo.setRestTimerSound(enabled) }
        RestTimerAlertCoordinator.setAlertPreferences(
            getApplication(),
            appSettings.value.restTimerVibration,
            enabled
        )
    }

    /**
     * Finishes the first run onboarding by writing the profile the athlete just filled in.
     *
     * The very first weigh-in is created from the weight they typed, so the Progress tab opens with
     * a real starting point rather than an empty chart they have to seed themselves. The note says
     * where it came from, so it is never mistaken for a logged measurement.
     */
    fun completeOnboardingWithProfile(
        name: String,
        age: Int,
        heightCm: Double,
        currentWeightKg: Double,
        targetWeightKg: Double,
        fitnessLevel: String,
        unit: WeightUnit
    ) {
        // The typed values are in the athlete's unit; the profile stores kilograms like everything
        // else, so this is the one place the onboarding conversion has to be right.
        val currentKg = WeightConversion.toKg(currentWeightKg, unit)
        val targetKg = WeightConversion.toKg(targetWeightKg, unit)
        val heightCmValue = heightCm
        viewModelScope.launch(Dispatchers.IO) {
            // Start from whatever profile is already on disk rather than assuming there is none, so
            // the only fields that change are the ones the athlete just filled in. If onboarding ever
            // happens to be shown to someone who has a profile, nothing else about it is thrown away.
            val existing = repository.getUserProfile().firstOrNull()
            val profile = UserProfile(
                id = existing?.id ?: 1,
                name = name.trim().ifBlank { existing?.name ?: "Athlete" },
                heightCm = heightCmValue,
                currentWeightKg = currentKg,
                targetWeightKg = targetKg,
                age = age,
                preferredUnit = unit.suffix,
                fitnessLevel = fitnessLevel,
                customApiKey = existing?.customApiKey ?: ""
            )
            userPreferencesRepo.saveUserProfile(profile)
            repository.saveUserProfile(profile)
            userPreferencesRepo.setWeightUnit(unit.suffix)

            // The first weigh-in is only created when there is no history yet: an athlete who has been
            // logging their weight must not get a duplicate entry from finishing a re-run onboarding.
            if (currentKg > 0.0 && (existing == null || repository.weightLogCount() == 0)) {
                repository.insertWeightLog(
                    BodyWeightLog(
                        weightKg = currentKg,
                        timestamp = System.currentTimeMillis(),
                        note = "Starting weight"
                    )
                )
            }

            completeOnboarding()
        }
    }

    // Keeps the rest timer alert switches in sync with what is stored, also on a cold start
    init {
        viewModelScope.launch(Dispatchers.IO) {
            appSettings.collect { settings ->
                RestTimerAlertCoordinator.setAlertPreferences(
                    getApplication(),
                    settings.restTimerVibration,
                    settings.restTimerSound
                )
            }
        }
    }

    // === ACTIVITY HEATMAP (progress page) ===

    /**
     * Training time and lifted volume per day, for the last year.
     *
     * The duration of a session is not reliable on its own: a workout saved a few seconds after it
     * started has an almost zero end-start difference, which used to leave the day of the map
     * completely empty even though the athlete had trained. The span between the first and the last
     * logged set is used as a second source, and a day with logged sets always keeps a visible
     * floor of colour (see [ActivityHeatmap.intensity]).
     */
    val activityDays: StateFlow<List<ActivityDay>> = combine(
        repository.getAllSessionsWithSets(),
        userProfile
    ) { sessions, profile -> buildActivityDays(sessions, profile.currentWeightKg) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildActivityDays(
        sessions: List<SessionWithSets>,
        profileWeightKg: Double
    ): List<ActivityDay> {
        val oneYearAgo = ActivityHeatmap.startOfDay(
            java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -12) }.timeInMillis
        )

        val minutesByDay = HashMap<Long, Int>()
        val volumeByDay = HashMap<Long, Double>()
        val setsByDay = HashMap<Long, Int>()

        sessions.forEach { wrapper ->
            val day = ActivityHeatmap.startOfDay(wrapper.session.startTime)
            if (day < oneYearAgo) return@forEach

            // The same duration and the same volume the Progress chart uses, so the two screens
            // never tell the athlete two different things about the same week.
            val minutes = WorkoutMetrics.minutes(wrapper.session, wrapper.sets)
            val volume = WorkoutMetrics.sessionVolume(wrapper.sets) { set ->
                BodyWeightTimeline.bodyWeightForSet(set, profileWeightKg)
            }

            minutesByDay[day] = (minutesByDay[day] ?: 0) + minutes
            volumeByDay[day] = (volumeByDay[day] ?: 0.0) + volume
            setsByDay[day] = (setsByDay[day] ?: 0) + wrapper.sets.size
        }

        return setsByDay.keys.sorted().map { day ->
            ActivityDay(
                epochDayStart = day,
                minutes = minutesByDay[day] ?: 0,
                volume = volumeByDay[day] ?: 0.0,
                setCount = setsByDay[day] ?: 0
            )
        }
    }

    // === SAVED ROUTINES ===
    val savedRoutines: StateFlow<List<SavedRoutine>> = repository.getAllSavedRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Asks the AI Coach screen to open on its Saved Routines tab.
     *
     * The saved routines are a tab of the coach screen rather than a destination of their own, so
     * a request from elsewhere in the app has to travel through here. The counter is what the
     * coach screen watches: a repeat request is a new number, not the same one again.
     */
    private val _savedRoutinesTabRequest = MutableStateFlow(0)
    val savedRoutinesTabRequest: StateFlow<Int> = _savedRoutinesTabRequest.asStateFlow()

    private val _homeStreakFocusRequest = MutableStateFlow(0)
    val homeStreakFocusRequest: StateFlow<Int> = _homeStreakFocusRequest.asStateFlow()

    fun requestHomeStreakFocus() {
        _homeStreakFocusRequest.value = _homeStreakFocusRequest.value + 1
    }

    /** Starts today's scheduled routine day from the launcher widget. */
    fun startTodayWorkoutFromWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeId = userPreferencesRepo.activeRoutineIdFlow.first()
            val routine = repository.getSavedRoutineById(activeId) ?: return@launch
            val exercises = repository.getAllExercisesOnce()
            val days = com.example.data.model.RoutineParser.parseWeek(
                routine.exercisesJson,
                exercises,
                weekStartsOnMonday = userPreferencesRepo.appSettingsFlow.first().weekStartsOnMonday
            )
            val today = com.example.data.model.LocalDateKey.isoDayOfWeek(
                com.example.data.model.LocalDateKey.keyOf(System.currentTimeMillis())
            )
            val day = days.firstOrNull { it.weekday == today && it.exercises.isNotEmpty() }
                ?: return@launch
            withContext(Dispatchers.Main) {
                startRoutineWorkout(routine, targetWeekday = day.weekday)
            }
        }
    }

    fun requestSavedRoutinesTab() {
        _savedRoutinesTabRequest.value = _savedRoutinesTabRequest.value + 1
    }

    // === HOME DASHBOARD ===

    /**
     * Every finished workout with its sets.
     *
     * One read of the history feeds every number on the Home screen, so the dashboard never
     * shows two different versions of the same week.
     */
    val workoutHistory: StateFlow<List<SessionWithSets>> = repository.getAllSessionsWithSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The body weight that applied to a set, taken from the snapshot the set carries. */
    private fun bodyWeightForSet(profileWeightKg: Double): (LoggedSet) -> Double? = { set ->
        BodyWeightTimeline.bodyWeightForSet(set, profileWeightKg)
    }

    /**
     * The last seven calendar dates, today included, with the volume and the time trained on each.
     */
    val homeHeatmap: StateFlow<List<HomeDashboard.DayCell>> = combine(
        workoutHistory,
        userProfile
    ) { sessions, profile ->
        HomeDashboard.rollingHeatmap(
            sessions = sessions,
            today = System.currentTimeMillis(),
            bodyWeightForSet = bodyWeightForSet(profile.currentWeightKg)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The number of days on which the athlete completed a qualifying workout.
     *
     * It is the number of distinct calendar days, not a streak, so it never resets.
     */
    val lifetimeTrainingDays: StateFlow<Int> = workoutHistory
        .map { sessions -> HomeDashboard.qualifyingDayCount(sessions) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** The totals of the calendar week the athlete is in. */
    val weekTotals: StateFlow<HomeDashboard.WeekTotals> = combine(
        workoutHistory,
        userProfile,
        weekStartFlow
    ) { sessions, profile, weekStartsOnMonday ->
        HomeDashboard.weekTotals(
            sessions = sessions,
            today = System.currentTimeMillis(),
            weekStartsOnMonday = weekStartsOnMonday,
            bodyWeightForSet = bodyWeightForSet(profile.currentWeightKg)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeDashboard.WeekTotals(0, 0, 0.0))

    /** The last workout the athlete finished. */
    val recentWorkout: StateFlow<SessionWithSets?> = workoutHistory
        .map { sessions -> HomeDashboard.mostRecentWorkout(sessions) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The routine the athlete has scheduled their week with. */
    val activeRoutineId: StateFlow<Long> = userPreferencesRepo.activeRoutineIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /**
     * The routine the athlete has scheduled their week with.
     *
     * Null when none has been picked, or when the routine it named has been deleted. The Home
     * screen then asks the athlete to choose one rather than picking one for them.
     */
    val activeRoutine: StateFlow<SavedRoutine?> = combine(
        savedRoutines,
        activeRoutineId
    ) { routines, id ->
        if (id <= 0L) null else routines.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Schedules a routine as the athlete's week, or clears it with 0. */
    fun setActiveRoutine(routineId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setActiveRoutineId(routineId)
            refreshAndroidWidgets()
        }
    }

    /**
     * The seven days of the scheduled routine, one per weekday.
     *
     * A day with no exercises in it is a rest day.
     */
    val activeRoutineDays: StateFlow<List<RoutineDay>> = combine(
        activeRoutine,
        allExercises,
        weekStartFlow
    ) { routine, exercises, weekStartsOnMonday ->
        if (routine == null) {
            emptyList()
        } else {
            RoutineParser.parseWeek(routine.exercisesJson, exercises, weekStartsOnMonday)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The materialized per-date schedule.
     *
     * Past rows are history. Only today and the future are regenerated when the active routine
     * changes, so an edit cannot silently turn last month's completed day into a missed one.
     */
    val scheduleDays: StateFlow<List<ScheduledWorkoutDay>> = repository.getScheduleDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Emits once a minute so a pending today becomes missed at the next local midnight. */
    private val currentDayTicker: Flow<Long> = flow {
        while (true) {
            emit(ActivityHeatmap.startOfDay(System.currentTimeMillis()))
            delay(60_000L)
        }
    }.distinctUntilChanged()

    /**
     * The current/best streak and the twelve-week adherence heat map.
     *
     * Derived from the persisted schedule and the finished sessions, never from a counter that is
     * incremented when the app opens.
     */
    val streakSummary: StateFlow<StreakSummary> = combine(
        scheduleDays,
        workoutHistory,
        weekStartFlow,
        currentDayTicker
    ) { schedule, sessions, weekStartsOnMonday, today ->
        WorkoutStreak.calculate(
            schedule = schedule,
            sessions = sessions,
            today = today,
            weekStartsOnMonday = weekStartsOnMonday
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakSummary())

    init {
        // This block is deliberately after activeRoutine, activeRoutineDays and weekStartFlow:
        // Kotlin initializes properties in declaration order, and collecting them earlier would
        // observe their default values rather than the athlete's active routine.
        viewModelScope.launch(Dispatchers.IO) {
            allExercises.first { it.isNotEmpty() }
            combine(activeRoutine, activeRoutineDays, weekStartFlow) { routine, days, _ ->
                routine to days
            }.collectLatest { (routine, days) ->
                val today = ActivityHeatmap.startOfDay(System.currentTimeMillis())
                val rows = if (routine == null) {
                    emptyList()
                } else {
                    WorkoutStreak.buildScheduleRows(
                        routineId = routine.id,
                        days = days,
                        fromDate = today
                    )
                }
                repository.replaceFutureSchedule(fromDate = today, rows = rows)
                refreshAndroidWidgets()
            }
        }
    }

    fun saveRoutine(routine: SavedRoutine) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRoutine(routine)
            refreshAndroidWidgets()
        }
    }

    fun saveCustomRoutine(
        name: String,
        description: String,
        daysPerWeek: Int,
        goal: String,
        exercises: List<RoutineExerciseItem>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val exercisesJson = RoutineParser.serializeExercises(exercises)
            val routine = SavedRoutine(
                name = name.trim(),
                description = description.trim(),
                daysPerWeek = daysPerWeek,
                goal = goal.trim(),
                exercisesJson = exercisesJson
            )
            repository.saveRoutine(routine)
            refreshAndroidWidgets()
        }
    }

    fun deleteRoutine(routine: SavedRoutine) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRoutine(routine)
            refreshAndroidWidgets()
        }
    }

    /**
     * Copies one of the starter templates into the athlete's own routines.
     *
     * The copy is an ordinary [SavedRoutine], not a reference back to the template, so the athlete
     * can edit or delete it freely afterwards. Template movements are resolved against the real
     * catalog first, so the adopted routine carries real exercise ids and opens in the editor
     * populated rather than as a list of names to retype.
     */
    fun adoptPredefinedRoutine(template: PredefinedRoutines.Template) {
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = allExercises.value
            val resolved = template.days.flatMap { day ->
                day.entries.map { entry ->
                    val name = entry.substringBefore(" (")
                    val prescription = entry.substringAfter(" (", "").removeSuffix(")")
                    val match = RoutineParser.findMatchingExercise(name, catalog)
                    RoutineExerciseItem(
                        exerciseId = match?.id ?: 0L,
                        exerciseName = match?.name ?: name,
                        exerciseType = match?.exerciseType ?: ExerciseType.STANDARD_WEIGHT,
                        category = match?.category.orEmpty(),
                        equipment = match?.equipment.orEmpty(),
                        prescription = prescription,
                        weekday = day.weekday,
                        dayTitle = day.focus
                    )
                }
            }
            repository.saveRoutine(
                PredefinedRoutines.toSavedRoutine(template).copy(
                    exercisesJson = RoutineParser.serializeExercises(resolved)
                )
            )
            refreshAndroidWidgets()
        }
    }

    /**
     * Saves the changes made in the "Create New Routine" screen while editing an existing routine.
     * Reusing the same id makes Room replace the row instead of creating a second routine.
     */
    fun saveEditedRoutine(routine: SavedRoutine) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRoutine(routine)
            refreshAndroidWidgets()
        }
    }

    /**
     * Duplicates a saved routine as an exact copy (same name, description, goal, days and the
     * exact same serialised exercise list) under a brand new id.
     */
    fun duplicateRoutine(routine: SavedRoutine) {
        viewModelScope.launch(Dispatchers.IO) {
            val copy = routine.copy(
                id = 0L,
                createdAt = System.currentTimeMillis()
            )
            repository.saveRoutine(copy)
            refreshAndroidWidgets()
        }
    }

    fun loadRoutineIntoActiveSession(routine: SavedRoutine) {
        startNewSession(routine.name)
        // Parse exercise lines from routine text
        val lines = routine.exercisesJson.lines()
        val allEx = allExercises.value
        val exMap = allEx.associateBy { it.name.lowercase().trim() }
        lines.forEach { line ->
            val cleanName = line.substringBefore("(").trim().lowercase()
            val matchedEx = exMap[cleanName]
            if (matchedEx != null) {
                addExerciseToActiveSession(matchedEx)
            }
        }
    }

    // === DEDICATED ROUTINE WORKOUT SESSION ===
    private val _activeRoutineWorkout = MutableStateFlow<ActiveRoutineWorkoutState?>(null)
    val activeRoutineWorkout: StateFlow<ActiveRoutineWorkoutState?> = _activeRoutineWorkout.asStateFlow()

    /**
     * Applies the routine item's default added weight to the exercise.
     *
     * An added weight is a property of the routine, not of the movement: the same single leg squat
     * can be done at +20 kg in one split and at bodyweight in another. The exercise is therefore
     * turned into an added weight exercise, which is the type that makes the app ask for the added
     * weight and add it to the body weight in the volume maths.
     *
     * The catalog row is left alone. This is a copy, and only for the session.
     */
    private fun withRoutineAddedWeight(exercise: Exercise, item: RoutineExerciseItem): Exercise {
        if (item.targetAddedWeightKg <= 0.0) return exercise
        return exercise.copy(exerciseType = ExerciseType.ADDED_WEIGHT)
    }

    /**
     * Starts a routine workout.
     *
     * @param targetWeekday the weekday to train, 1 = Monday ... 7 = Sunday. When it is null the
     *        first day of the routine is used.
     */
    fun startRoutineWorkout(routine: SavedRoutine, targetWeekday: Int? = null) {
        // Dismiss any lingering quick workout tray so only the dedicated routine workout runs
        _activeSession.value = null
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()

        val allEx = allExercises.value
        val days = RoutineParser.parseDays(routine.exercisesJson, allEx, weekStartsOnMonday())

        // The day the athlete asked for, or the first day of the routine. A rest day is never
        // started: an empty routine must not launch an empty session.
        val targetDay = if (targetWeekday != null && days.isNotEmpty()) {
            days.firstOrNull { it.weekday == targetWeekday && it.exercises.isNotEmpty() }
                ?: days.firstOrNull { it.exercises.isNotEmpty() }
        } else if (days.isNotEmpty()) {
            days.firstOrNull { it.exercises.isNotEmpty() }
        } else {
            null
        }

        val routineItems = targetDay?.exercises
            ?: RoutineParser.parseExercises(routine.exercisesJson, allEx, weekStartsOnMonday())
        val weekday = targetDay?.weekday ?: Weekday.MONDAY
        val dayTitle = targetDay?.dayTitle.orEmpty()
        val dayNote = targetDay?.note.orEmpty()

        val exMap = allEx.associateBy { it.id }
        val exByName = allEx.associateBy { it.name.trim().lowercase() }

        // Resolve exact exercises in the specified order for this day
        val resolvedExercises = mutableListOf<Exercise>()
        for (item in routineItems) {
            // A coach note must never become a workout exercise, even from older saved routines
            if (RoutineParser.isCoachNoteLine(item.exerciseName)) continue
            val byId = exMap[item.exerciseId]
            val byName = exByName[item.exerciseName.trim().lowercase()]
            val found = byId ?: byName ?: RoutineParser.findMatchingExercise(item.exerciseName, exByName.values)
            if (found != null && !resolvedExercises.contains(found)) {
                resolvedExercises.add(withRoutineAddedWeight(found, item))
            } else if (found == null && item.exerciseName.isNotBlank()) {
                val tempEx = Exercise(
                    id = if (item.exerciseId > 0) item.exerciseId else ((System.currentTimeMillis() % 1000000) + resolvedExercises.size),
                    name = item.exerciseName,
                    category = item.category.ifBlank { "Calisthenics" },
                    exerciseType = item.exerciseType,
                    equipment = item.equipment.ifBlank { "Bodyweight" },
                    description = "Prescribed routine exercise"
                )
                resolvedExercises.add(withRoutineAddedWeight(tempEx, item))
            }
        }

        // Fallback for unstructured legacy text if needed
        if (resolvedExercises.isEmpty() && routine.exercisesJson.isNotBlank()) {
            for (line in routine.exercisesJson.lines()) {
                val clean = line.substringBefore("(").replace("*", "").removePrefix("-").trim().lowercase()
                val matched = exByName[clean]
                if (matched != null && !resolvedExercises.contains(matched)) {
                    resolvedExercises.add(matched)
                }
            }
        }

        // Pre-populate initial sets for each exercise so the workout has immediate ready-to-log rows
        val initialSets = mutableListOf<LoggedSet>()
        val now = System.currentTimeMillis()
        resolvedExercises.forEachIndexed { exIndex, ex ->
            val item = routineItems.getOrNull(exIndex)
            val numSets = item?.targetSets?.coerceIn(1, 10) ?: 3
            val reps = item?.targetReps ?: 8
            val hold = item?.targetHoldSeconds ?: 0
            val weight = item?.targetWeight ?: 0.0
            // The default added weight the routine prescribes for this exercise, 0 for none.
            val addedTarget = item?.targetAddedWeightKg ?: 0.0

            for (setNum in 1..numSets) {
                initialSets.add(
                    LoggedSet(
                        sessionId = 0L,
                        exerciseId = ex.id,
                        exerciseName = ex.name,
                        exerciseType = ex.exerciseType,
                        setNumber = setNum,
                        weight = if (ex.exerciseType == ExerciseType.STANDARD_WEIGHT) weight else 0.0,
                        // The added weight the routine prescribes, when it prescribes one. The
                        // exercise has already been turned into an added weight exercise by
                        // withRoutineAddedWeight, so this is the number the athlete lifts on top
                        // of their own body weight.
                        addedWeight = if (ex.exerciseType == ExerciseType.ADDED_WEIGHT) {
                            if (addedTarget > 0.0) addedTarget else weight
                        } else 0.0,
                        // Reps and hold seconds are independent: a prescribed hold never erases the prescribed
                        // reps (an explosive pull-up is 3 reps, a plank is 30 seconds).
                        reps = reps,
                        holdDurationSeconds = hold,
                        isCompleted = false,
                        timestamp = now,
                        // The body weight that applies to this day, taken now so a later weigh-in
                        // does not rewrite what these sets weighed.
                        bodyWeightKg = currentBodyWeightKg()
                    )
                )
            }
        }

        _activeRoutineWorkout.value = ActiveRoutineWorkoutState(
            routineId = routine.id,
            routineName = routine.name,
            routineGoal = routine.goal,
            startTime = now,
            exercises = resolvedExercises,
            routineItems = routineItems,
            sets = initialSets,
            weekday = weekday,
            dayTitle = dayTitle,
            dayNote = dayNote,
            // The day this session fulfils, not necessarily the day it is started. A Monday
            // session done on Wednesday still belongs to Monday's plan.
            plannedDate = WorkoutStreak.dateForWeekdayInWeek(
                weekday = weekday,
                reference = now,
                weekStartsOnMonday = weekStartsOnMonday()
            )
        ).let { state ->
            state.copy(plannedDateKey = LocalDateKey.keyOf(state.plannedDate))
        }
    }

    fun addSetToRoutineWorkout(exercise: Exercise) {
        val current = _activeRoutineWorkout.value ?: return
        val existingForEx = current.sets.filter { it.exerciseId == exercise.id }
        val nextSetNum = existingForEx.size + 1
        val lastSet = existingForEx.lastOrNull()
        val newSet = LoggedSet(
            sessionId = 0L,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseType = exercise.exerciseType,
            setNumber = nextSetNum,
            weight = lastSet?.weight ?: 0.0,
            addedWeight = lastSet?.addedWeight ?: 0.0,
            reps = lastSet?.reps ?: 8,
            holdDurationSeconds = lastSet?.holdDurationSeconds ?: 0,
            isCompleted = false,
            timestamp = System.currentTimeMillis(),
            // The body weight that applies to this set, taken now so a later weigh-in does not
            // rewrite it.
            bodyWeightKg = currentBodyWeightKg()
        )
        _activeRoutineWorkout.value = current.copy(sets = current.sets + newSet)
    }

    fun updateRoutineWorkoutSet(index: Int, updated: LoggedSet) {
        val current = _activeRoutineWorkout.value ?: return
        if (index !in current.sets.indices) return
        val mutableList = current.sets.toMutableList()
        mutableList[index] = updated
        _activeRoutineWorkout.value = current.copy(sets = mutableList)
    }

    fun deleteRoutineWorkoutSet(index: Int) {
        val current = _activeRoutineWorkout.value ?: return
        if (index !in current.sets.indices) return
        val mutableList = current.sets.toMutableList()
        val deletedSet = mutableList.removeAt(index)
        // Re-index remaining sets for this exercise
        var currentSetNum = 1
        val reindexed = mutableList.map { set ->
            if (set.exerciseId == deletedSet.exerciseId) {
                set.copy(setNumber = currentSetNum++)
            } else {
                set
            }
        }
        _activeRoutineWorkout.value = current.copy(sets = reindexed)
    }

    fun stopRoutineWorkout() {
        // Ending a routine workout also ends any rest timer that was running.
        stopRestTimer()
        _activeRoutineWorkout.value = null
        // Ensure no active workout tray remains open on the log workout screen
        _activeSession.value = null
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()
        // The athlete left the workout on purpose, so it must not be offered back on the next launch.
        clearWorkoutDraft()
    }

    fun finishRoutineWorkout(notes: String) {
        val current = _activeRoutineWorkout.value ?: return
        // The workout is over: stop the rest timer so it cannot keep running or ring later.
        stopRestTimer()
        viewModelScope.launch(Dispatchers.IO) {
            // Only the sets the user ticked as "done" are saved. Prescribed target values
            // (reps / weight / hold) are only defaults and must never be counted as logged work.
            val savedAt = System.currentTimeMillis()
            // One body weight for the whole finished workout, taken at the moment it is saved.
            // See finishAndSaveActiveSession: a later profile edit must not rewrite history.
            val finalBodyWeightKg = resolveBodyWeightKg()
            val setsToSave = current.sets
                .filter { it.isCompleted }
                .mapIndexed { index, set ->
                    set.copy(
                        timestamp = savedAt + index,
                        bodyWeightKg = finalBodyWeightKg.takeIf { it > 0.0 } ?: set.bodyWeightKg
                    )
                }

            // A workout without any logged set is not a workout, so nothing is persisted.
            var savedSessionId: Long? = null
            if (setsToSave.isNotEmpty()) {
                val sessionTitle = if (!current.dayTitle.isNullOrBlank() && !current.routineName.contains(current.dayTitle, ignoreCase = true)) {
                    "${current.routineName} (${current.dayTitle})"
                } else {
                    current.routineName
                }
                val session = WorkoutSession(
                    title = sessionTitle,
                    startTime = current.startTime,
                    endTime = System.currentTimeMillis(),
                    notes = notes.trim(),
                    routineId = current.routineId,
                    routineWeekday = current.weekday ?: 0,
                    plannedDate = current.plannedDate,
                    plannedDateKey = current.plannedDateKey.takeIf { it > 0L }
                        ?: LocalDateKey.keyOf(current.plannedDate)
                )
                val sessionId = repository.insertSession(session)
                repository.insertSets(setsToSave.map { it.copy(sessionId = sessionId) })
                savedSessionId = sessionId
            }

            withContext(Dispatchers.Main) {
                _activeRoutineWorkout.value = null
                // Ensure no active workout tray remains open on the log workout screen
                _activeSession.value = null
                _activeSets.value = emptyList()
                _activeSessionExercises.value = emptyList()
            }
            // The workout is saved, so there is nothing left to offer back on the next launch.
            clearWorkoutDraft()
            refreshAndroidWidgets()
            savedSessionId?.let { maybeGenerateWorkoutSummary(it) }
        }
    }

    // === AI FITNESS ASSISTANT (GEMINI) ===
    val chatSessions: StateFlow<List<ChatSession>> = repository.getAllChatSessions()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentChatSession = MutableStateFlow<ChatSession?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<ChatMessage>> = currentChatSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                repository.getMessagesForSession(session.id).map { list ->
                    list.map { entity ->
                        ChatMessage(
                            id = entity.id,
                            sender = if (entity.sender == "USER") ChatMessage.Sender.USER else ChatMessage.Sender.AI,
                            text = entity.text,
                            timestamp = entity.timestamp,
                            kind = entity.kind,
                            payloadJson = entity.payloadJson,
                            status = entity.status,
                            sessionId = entity.sessionId
                        )
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun createNewChatSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val newSession = ChatSession(
                title = "New Chat",
                createdAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
            repository.insertChatSession(newSession)
            val welcomeMsg = ChatMessageEntity(
                sessionId = newSession.id,
                sender = "AI",
                text = "Hello! I am IronPulse AI, your calisthenics & strength coach. Ask me anything about muscle-up technique, progressive overload, injury prevention, or routine design."
            )
            repository.insertChatMessage(welcomeMsg)
            withContext(Dispatchers.Main) {
                currentChatSession.value = newSession
            }
        }
    }

    fun selectChatSession(session: ChatSession) {
        currentChatSession.value = session
    }

    fun deleteChatSession(session: ChatSession) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteChatSession(session.id)
            val remaining = repository.getAllChatSessions().firstOrNull() ?: emptyList()
            val next = remaining.firstOrNull { it.id != session.id }
            withContext(Dispatchers.Main) {
                if (currentChatSession.value?.id == session.id) {
                    if (next != null) {
                        currentChatSession.value = next
                    } else {
                        createNewChatSession()
                    }
                }
            }
        }
    }

    fun sendChatMessage(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank()) return
        val session = currentChatSession.value ?: return

        // No key -> explicit error dialog, never mock advice
        if (ensureApiKeyOrExplain("AI Coach")) return

        _isChatLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val userMsg = ChatMessageEntity(
                sessionId = session.id,
                sender = "USER",
                text = cleanText,
                timestamp = System.currentTimeMillis()
            )
            repository.insertChatMessage(userMsg)

            val updatedTitle = if (session.title == "New Chat" || session.title == "IronPulse Coach") {
                if (cleanText.length > 28) cleanText.take(28).trim() + "..." else cleanText
            } else {
                session.title
            }
            val updatedSession = session.copy(
                title = updatedTitle,
                lastUpdatedAt = System.currentTimeMillis()
            )
            repository.updateChatSession(updatedSession)
            withContext(Dispatchers.Main) {
                currentChatSession.value = updatedSession
            }

            val allDbMessages = repository.getMessagesListForSession(session.id)
            val conversation = allDbMessages
                .filter { it.text.isNotBlank() }
                .map { msg ->
                    AiMessage(
                        role = if (msg.sender == "USER") AiRole.USER else AiRole.ASSISTANT,
                        text = msg.text
                    )
                }

            val dataAccessEnabled = userPreferencesRepo.aiWorkoutDataAccessFlow.first()
            val result = runCatching {
                val provider = aiService.activeProviderFlow.first()
                aiService.complete(
                    systemInstruction = buildCoachSystemInstruction(dataAccessEnabled),
                    messages = conversation,
                    enableTools = true,
                    provider = provider,
                    apiKeyFallback = fallbackApiKey(provider),
                    accessPolicy = AiAccessPolicy(
                        personalData = dataAccessEnabled,
                        routineModification = dataAccessEnabled
                    ),
                    accessPolicyProvider = {
                        val enabledNow = userPreferencesRepo.aiWorkoutDataAccessFlow.first()
                        AiAccessPolicy(
                            personalData = enabledNow,
                            routineModification = enabledNow
                        )
                    }
                )
            }

            result.fold(
                onSuccess = { conversationResult ->
                    when {
                        conversationResult.routineProposal != null -> {
                            val proposal = conversationResult.routineProposal
                            repository.insertChatMessage(
                                ChatMessageEntity(
                                    sessionId = session.id,
                                    sender = "AI",
                                    text = conversationResult.text,
                                    timestamp = System.currentTimeMillis(),
                                    kind = ChatMessageKind.ROUTINE_PROPOSAL,
                                    payloadJson = proposal.toJson().toString(),
                                    status = ChatMessageStatus.PENDING
                                )
                            )
                        }
                        conversationResult.newRoutineProposal != null -> {
                            val proposal = conversationResult.newRoutineProposal
                            repository.insertChatMessage(
                                ChatMessageEntity(
                                    sessionId = session.id,
                                    sender = "AI",
                                    text = conversationResult.text,
                                    timestamp = System.currentTimeMillis(),
                                    kind = ChatMessageKind.NEW_ROUTINE_PROPOSAL,
                                    payloadJson = proposal.toJson().toString(),
                                    status = ChatMessageStatus.PENDING
                                )
                            )
                        }
                        else -> {
                            repository.insertChatMessage(
                                ChatMessageEntity(
                                    sessionId = session.id,
                                    sender = "AI",
                                    text = conversationResult.text,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    // No mock answers: surface the real reason instead
                    showAiError("AI Coach Unavailable", geminiErrorMessage(error, "The AI coach reply"))
                }
            )

            withContext(Dispatchers.Main) {
                _isChatLoading.value = false
            }
        }
    }

    /**
     * Applies exactly one proposal after the athlete taps Grant permission.
     *
     * The proposal is parsed from the card payload, revalidated against the current routine and
     * applied in one transaction. It never authorizes a second edit.
     */
    fun approveRoutineProposal(message: ChatMessage) {
        if (message.status != ChatMessageStatus.PENDING) return
        val proposal = RoutineModificationProposal.fromJson(message.payloadJson)
        if (proposal == null) {
            viewModelScope.launch(Dispatchers.IO) {
                db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.EXPIRED)
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = userPreferencesRepo.aiWorkoutDataAccessFlow.first()
            val activeRoutineId = userPreferencesRepo.activeRoutineIdFlow.first()
            val result = if (!enabled) {
                Result.failure(IllegalStateException("AI workout-data access is disabled."))
            } else if (proposal.routineId != activeRoutineId) {
                Result.failure(IllegalStateException("The active routine changed after this proposal was created."))
            } else {
                routineModificationService.apply(proposal, accessEnabled = true)
            }
            result.fold(
                onSuccess = { change ->
                    db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.APPROVED)
                    refreshAndroidWidgets()
                    val fallback = "Approved and applied: **${change.removedExercise}** was replaced " +
                        "by **${change.addedExercise}** on ${change.weekdayName}. " +
                        "The rest of the routine was not changed."
                    val explanation = continueAfterProposalDecision(
                        sessionId = message.sessionId,
                        instruction = "The athlete approved the proposal and IronPulse applied exactly " +
                            "this change: removed ${change.removedExercise}, added " +
                            "${change.addedExercise} on ${change.weekdayName}. Confirm what changed, " +
                            "explain briefly why it addresses the request, and explicitly state that " +
                            "nothing else in the routine was changed. Do not claim any further edit " +
                            "and do not propose another modification.",
                        fallback = fallback
                    )
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            sessionId = message.sessionId,
                            sender = "AI",
                            text = explanation,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                onFailure = { error ->
                    db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.EXPIRED)
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            sessionId = message.sessionId,
                            sender = "AI",
                            text = "That proposal could not be applied because the routine changed " +
                                "or is no longer valid. No changes were made. ${error.message.orEmpty()}",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            )
        }
    }

    /** Rejects a proposal without touching the routine, then continues the explanation in chat. */
    fun rejectRoutineProposal(message: ChatMessage) {
        if (message.status != ChatMessageStatus.PENDING) return
        val proposal = RoutineModificationProposal.fromJson(message.payloadJson)
        viewModelScope.launch(Dispatchers.IO) {
            db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.REJECTED)
            val fallback = if (proposal != null) {
                "No changes were made. I suggested replacing **${proposal.removeExerciseName}** " +
                    "with **${proposal.replacementExerciseName}** on " +
                    "${com.example.data.model.Weekday.name(proposal.weekday)}. " +
                    "You can ask me to explain the reasoning or propose a different option."
            } else {
                "No changes were made. Ask me for a different routine option when you are ready."
            }
            val explanation = continueAfterProposalDecision(
                sessionId = message.sessionId,
                instruction = "The athlete rejected the routine proposal. Nothing was changed in " +
                    "the database. Explain the recommendation in normal language without applying " +
                    "it, mention the trade-offs and a safe alternative, and do not claim that any " +
                    "edit happened.",
                fallback = fallback
            )
            repository.insertChatMessage(
                ChatMessageEntity(
                    sessionId = message.sessionId,
                    sender = "AI",
                    text = explanation,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Saves one complete AI Coach routine proposal after explicit approval.
     *
     * The proposal itself is inert. This is the only path from a chat proposal to the routine
     * table, and it revalidates every reference in one transaction before writing anything.
     */
    fun approveNewRoutineProposal(message: ChatMessage) {
        if (message.status != ChatMessageStatus.PENDING) return
        val proposal = NewRoutineProposal.fromJson(message.payloadJson)
        viewModelScope.launch(Dispatchers.IO) {
            val current = db.chatDao().getMessagesListForSession(message.sessionId)
                .firstOrNull { it.id == message.id }
            if (current == null || current.status != ChatMessageStatus.PENDING) return@launch
            if (proposal == null) {
                db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.EXPIRED)
                return@launch
            }
            val enabled = userPreferencesRepo.aiWorkoutDataAccessFlow.first()
            val result = routineModificationService.applyNewRoutine(proposal, accessEnabled = enabled)
            result.fold(
                onSuccess = { created ->
                    db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.APPROVED)
                    refreshAndroidWidgets()
                    val generated = created.createdExercises
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString()
                        ?.let { " New exercises created: $it." }
                        .orEmpty()
                    val fallback = "Saved **${created.routineName}** with ${created.trainingDays} " +
                        "training day${if (created.trainingDays == 1) "" else "s"} and " +
                        "${created.exerciseCount} exercises.$generated"
                    val explanation = continueAfterProposalDecision(
                        sessionId = message.sessionId,
                        instruction = "The athlete approved the new routine proposal and IronPulse " +
                            "saved it. Confirm that it was saved, state its name, training days and " +
                            "exercise count, and explain briefly how to start it from Saved Routines. " +
                            "Do not claim that it is already the active schedule and do not propose " +
                            "another change.",
                        fallback = fallback
                    )
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            sessionId = message.sessionId,
                            sender = "AI",
                            text = explanation,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                onFailure = { error ->
                    db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.EXPIRED)
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            sessionId = message.sessionId,
                            sender = "AI",
                            text = "That routine could not be saved because the catalog changed or " +
                                "the proposal is no longer valid. Nothing was created or changed. " +
                                error.message.orEmpty(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            )
        }
    }

    /** Rejects a new-routine proposal without writing an exercise or a routine. */
    fun rejectNewRoutineProposal(message: ChatMessage) {
        if (message.status != ChatMessageStatus.PENDING) return
        val proposal = NewRoutineProposal.fromJson(message.payloadJson)
        viewModelScope.launch(Dispatchers.IO) {
            db.chatDao().updateMessageStatus(message.id, ChatMessageStatus.REJECTED)
            val fallback = if (proposal != null) {
                "No routine was saved. You can ask me to adjust **${proposal.routineName}** or " +
                    "create a different plan."
            } else {
                "No routine was saved. Ask me for a different plan when you are ready."
            }
            val explanation = continueAfterProposalDecision(
                sessionId = message.sessionId,
                instruction = "The athlete rejected the new routine proposal. Nothing was saved " +
                    "and no exercise was created. Acknowledge that briefly, offer to adjust the " +
                    "plan, and do not claim that any change happened.",
                fallback = fallback
            )
            repository.insertChatMessage(
                ChatMessageEntity(
                    sessionId = message.sessionId,
                    sender = "AI",
                    text = explanation,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Lets the coach respond to an approval or rejection as a normal chat turn.
     *
     * The explanation call deliberately has tools disabled: the decision is already final, and a
     * second proposal inside the explanation would be confusing and could invite an unintended
     * edit. A provider failure falls back to a factual local message, so the athlete is never left
     * without an answer.
     */
    private suspend fun continueAfterProposalDecision(
        sessionId: String,
        instruction: String,
        fallback: String
    ): String {
        if (sessionId.isBlank()) return fallback
        val history = repository.getMessagesListForSession(sessionId)
            .filter { it.kind == ChatMessageKind.TEXT && it.text.isNotBlank() }
            .takeLast(20)
            .map { entity ->
                AiMessage(
                    role = if (entity.sender == "USER") AiRole.USER else AiRole.ASSISTANT,
                    text = entity.text
                )
            }
        return try {
            val provider = aiService.activeProviderFlow.first()
            aiService.complete(
                systemInstruction = "${AiService.DEFAULT_COACH_SYSTEM}\n\n$instruction",
                messages = history + AiMessage(AiRole.USER, "I made my decision in the card."),
                enableTools = false,
                provider = provider,
                apiKeyFallback = fallbackApiKey(provider),
                accessPolicy = AiAccessPolicy.disabled
            ).text.trim().takeIf { it.isNotBlank() } ?: fallback
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback
        }
    }

    /**
     * Builds the coach instruction for one request.
     *
     * Personal context is appended only after the explicit permission check. When permission is
     * off, no profile, benchmark or workout data is placed in the prompt; the model is told it must
     * say that it cannot access the database instead of guessing.
     */
    private fun buildCoachSystemInstruction(dataAccessEnabled: Boolean): String = buildString {
        append(AiService.DEFAULT_COACH_SYSTEM)
        append("\n\n")
        if (dataAccessEnabled) {
            append(
                """
                You are also IronPulse's evidence-based workout data assistant. The tools below read
                the athlete's real stored data. Use them only when the answer depends on that data;
                answer general training questions from your own knowledge without a tool.

                TOOLS
                - search_exercises(query, limit): find real catalog names by name, muscle group,
                  body part, target or equipment. Use it before naming an exercise you are not sure
                  exists, and before searching history for an exercise the athlete spelled loosely.
                - get_exercise_details(exerciseId or name): category, target muscles, equipment and
                  instructions for one catalog exercise.
                - list_exercises(category, equipment, limit, offset): browse one page of the catalog.
                - get_current_routine(): the active weekly schedule, including rest days, day titles
                  and every prescribed exercise. Use this for "my routine", "my plan" or "what am I
                  training this week".
                - list_routines(limit): the athlete's saved routines.
                - get_routine(routineId or name): one saved routine with its full prescription.
                - search_recent_workouts(daysBack, exerciseName?, limit, offset): completed sessions
                  in a date range, with dates, duration, order and completed sets. Use it for "what
                  did I train last week" or training-frequency questions.
                - search_exercise_history(exerciseName, daysBack, limit): completed sets for ONE
                  exercise, newest first. Use it for progress, PRs and improvement comparisons.
                - propose_routine_modification(...): propose ONE exercise replacement. It never
                  changes data; the app shows an approval card.
                - propose_new_routine(...): propose a complete new weekly routine. It never saves
                  anything; the app shows the full plan and creates it only after approval.

                HOW TO USE THEM
                - Never answer a question about the athlete's history, schedule, routine, exercise
                  catalog or progress from memory. Call the relevant tool first.
                - For progress questions, resolve the exercise name first when it may be ambiguous,
                  then call search_exercise_history with a real catalog name. Pass the athlete's
                  wording when it is already clear; the app normalizes case, punctuation, spacing
                  and simple plurals ("push ups" matches "Push-Up") but does not merge different
                  movements such as "Push-Up" and "Pike Push Up".
                - Dates and windows are local to the athlete. "Last week" means the previous seven
                  days unless the athlete names a calendar week; state the window you used.
                - Every session result carries both "date" (yyyy-MM-dd) and "dateLabel". Copy one of
                  those fields exactly for each session. Never shift a workout to another day and
                  never merge two sessions into one date.
                - You may call several independent tools in one turn (for example history plus the
                  current routine), and you may make another round when the results raise a new
                  question. Stop and answer once you have enough evidence.
                - Empty result: a successful result with count 0 means the database has no matching
                  data. Say that plainly. If exerciseInCatalog is false, closestMatches are
                  suggestions only; never present one as the athlete's exercise and never fabricate
                  sets, weights, dates or percentages.
                - Tool error: a result with "ok": false is an error, not data. Read "message" and
                  "correctArgumentsSchema", correct the arguments and retry once. If it still fails,
                  explain the limitation to the athlete instead of guessing.
                - Compare only the same exercise and compatible units. Keep dates and units explicit,
                  and do not dump raw database output.

                ROUTINE CHANGES AND NEW ROUTINES
                Use propose_routine_modification for a change to the current weekly routine. When
                the athlete asks for a NEW routine from scratch, first make sure you have enough
                detail: goal, experience level, available equipment, training days per week and any
                injury constraints. Ask a short clarifying question only when a missing detail would
                materially change the plan; otherwise use sensible defaults and state them. Then
                call propose_new_routine once with the complete week. A routine request is not
                complete until propose_new_routine has been called: do not answer a create-a-routine
                request with a prose plan only, because the app cannot save prose as a routine.
                Prefer catalog exercises and call search_exercises before using a name you are unsure
                about. Include newExercise metadata only for a genuinely missing movement. The app
                validates the plan, shows every day and prescription, and saves it only after
                explicit approval.

                Make at most one proposal per message. A proposal does not modify anything; never
                claim that a routine or exercise was created before approval. Do not diagnose
                injuries or guarantee that an exercise is medically safe; advise professional
                guidance when appropriate.
                """.trimIndent()
            )
            append("\n\n")
            append(
                athleteContextPromptBlock(
                    includeBenchmarks = true,
                    purpose = "tailored, data-grounded answers"
                )
            )
        } else {
            append(
                "The athlete has not enabled AI workout-data access. You have no access to their " +
                    "workout history, current routine, exercise history, profile or benchmark data. " +
                    "Do not imply that you can see them and do not invent values. You may still give " +
                    "general coaching advice and explain how to enable the permission in Settings.\n"
            )
        }
    }

    // AI Routine Generator Form State
    val routineGoal = MutableStateFlow("Strength & Muscle Growth")
    val routineDaysPerWeek = MutableStateFlow(4)
    val routineEquipment = MutableStateFlow("Pull-up Bar, Dip Bars, & Dumbbells")
    val generatedRoutine = MutableStateFlow<String>("")
    val isGeneratingRoutine = MutableStateFlow(false)

    /**
     * Complete catalog of every exercise stored locally, grouped by muscle category.
     *
     * It contains the built-in dataset library, the exercises the AI registered through the parser
     * AND the exercises the user created themselves, so Gemini can always reuse an exercise that
     * already exists instead of inventing (or duplicating) a new one.
     *
     * With 1,300+ rows this is roughly 8k tokens, which is large but affordable on the models this
     * app talks to. The one thing that would blow the budget is the user's own history on top of it,
     * so custom exercises are always included (they exist nowhere else and must be reusable) while
     * the budget itself is enforced by [catalogNameBudget]: when a prompt is already huge, the
     * library names are truncated from the least likely to be relevant group (see
     * [compactCatalogForPrompt]) rather than silently dropping the user's own exercises.
     */
    private suspend fun buildFullExerciseCatalog(): String {
        val all = repository.getAllExercises().first()
        if (all.isEmpty()) {
            return "[Calisthenics]: Weighted Pull-up, Weighted Dips, Barbell Bench Press, Barbell Full Squat"
        }
        return all
            .groupBy { it.category.ifBlank { MuscleCategory.CALISTHENICS } }
            .toSortedMap()
            .map { (category, list) -> "[$category]: " + list.joinToString(", ") { it.name } }
            .joinToString("\n")
    }

    /**
     * The catalog with the library trimmed down to [budgetChars] worth of names, always keeping
     * every exercise the athlete owns.
     *
     * This exists because the catalog is now dataset sized. Custom exercises are few and are the
     * ones Gemini most needs to reuse verbatim, so they are kept first and the shipped library is
     * what gets truncated. Categories are dropped from the largest ones down until the budget fits,
     * which keeps the result deterministic rather than dependent on query order.
     */
    private val catalogNameBudget = 28_000

    private fun compactCatalog(
        all: List<Exercise>,
        budgetChars: Int
    ): String {
        val byCategory = all.groupBy { it.category.ifBlank { MuscleCategory.CALISTHENICS } }
        // Custom exercises are the contract with the user, so they are never truncated.
        val custom = all.filter { it.isCustom }.groupBy { it.category.ifBlank { MuscleCategory.CALISTHENICS } }

        val kept = LinkedHashMap<String, MutableList<String>>()
        for ((category, list) in custom) {
            kept.getOrPut(category) { mutableListOf() }.addAll(list.map { it.name })
        }

        var used = kept.entries.sumOf { (category, names) ->
            category.length + 4 + names.sumOf { name -> name.length + 2 }
        }
        val libraryByCategory = byCategory
            .filterKeys { it !in custom }
            .mapValues { (_, list) -> list.map { it.name } }
            // Largest groups first: dropping them buys the most characters per removed entry.
            .toList()
            .sortedByDescending { (_, names) -> names.sumOf { name -> name.length } }

        for ((category, names) in libraryByCategory) {
            if (used >= budgetChars) break
            val entryCost = category.length + 4 + names.sumOf { name -> name.length + 2 }
            if (used + entryCost <= budgetChars) {
                kept.getOrPut(category) { mutableListOf() }.addAll(names)
                used += entryCost
            }
        }

        return kept.entries.joinToString("\n") { (category, names) ->
            "[$category]: " + names.joinToString(", ")
        }
    }

    /** Prompt-specific view of the catalog that respects the token budget of that call site. */
    private suspend fun catalogForPrompt(budgetChars: Int): String {
        val all = repository.getAllExercises().first()
        if (all.isEmpty()) return buildFullExerciseCatalog()
        return compactCatalog(all, budgetChars)
    }

    fun generateRoutineWithAI() {
        // No key -> explicit error dialog, never a mocked routine
        if (ensureApiKeyOrExplain("The AI routine generator")) return
        isGeneratingRoutine.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = catalogForPrompt(catalogNameBudget)
            val knownExercises = repository.getAllExercises().first()
            val prompt = buildString {
                append("You are an expert strength and calisthenics coach.\n")
                append(
                    "Generate a structured ${routineDaysPerWeek.value}-day weekly workout routine " +
                            "for an athlete with the goal: '${routineGoal.value}', " +
                            "using equipment: '${routineEquipment.value}'.\n\n"
                )
                append(athleteContextPromptBlock())

                append("EXERCISE RULES:\n")
                append("1. ALWAYS prefer an exercise that already exists in the complete local database list below, and use its exact name.\n")
                append("2. If the routine genuinely needs a movement that is NOT in the list, do NOT force-map it to an unrelated exercise. Put it in \"new_exercises_to_create\" and use the same exact name in the day's \"exercises\" array. The app creates it locally.\n")
                append("3. Never substitute a biomechanically different movement just because it shares a word.\n")
                append("4. The list includes the IronPulse library, exercises previously generated by the AI and custom exercises created by the user: if one already matches what you need, reuse that exact name.\n")
                append("COMPLETE LOCAL DATABASE CATALOG (ALL EXERCISES IN THE APP):\n$candidates\n\n")

                append("OUTPUT FORMAT: RESPOND WITH ONE SINGLE JSON OBJECT AND NOTHING ELSE.\n")
                append("Do NOT write any greeting, intro, explanation, markdown heading, code fence or closing remark before or after it.\n")
                append("The \"exercises\" array is the ONLY place an exercise name may appear: no description, note or full sentence may ever be placed in it.\n\n")

                append("SCHEMA:\n")
                append("{\n")
                append("  \"routine_name\": \"<short routine title>\",\n")
                append("  \"routine_goal\": \"<short goal>\",\n")
                append("  \"new_exercises_to_create\": [],\n")
                append("  \"days\": [\n")
                append("    {\n")
                append("      \"weekday\": \"Monday\",\n")
                append("      \"focus\": \"<short focus of the day, e.g. Upper Body Push>\",\n")
                append("      \"note\": \"<one short motivating sentence, or empty>\",\n")
                append("      \"exercises\": [\n")
                append("        {\n")
                append("          \"name\": \"<EXACT exercise name copied from the catalog>\",\n")
                append("          \"sets\": 4,\n")
                append("          \"reps\": 8,\n")
                append("          \"weight\": 0.0,\n")
                append("          \"holdSeconds\": 0,\n")
                append("          \"prescription\": \"4 sets x 8 reps\"\n")
                append("        }\n")
                append("      ]\n")
                append("    }\n")
                append("  ]\n")
                append("}\n\n")

                append("FIELD RULES:\n")
                append("- \"name\" MUST be copied character for character from the catalog, or match a name in \"new_exercises_to_create\". Never write a sentence there.\n")
                append("- \"new_exercises_to_create\" MUST be an array, empty when every exercise exists. Each entry needs: \"name\", \"category\" (Chest, Back, Legs, Shoulders, Arms, Core, Calisthenics), \"bodyPart\" (chest, back, shoulders, upper arms, lower arms, waist, upper legs, lower legs, cardio, neck), \"target\", \"secondaryMuscles\" (array), \"equipment\", \"exerciseType\" (STANDARD_WEIGHT, ADDED_WEIGHT, PURE_BODYWEIGHT) and \"instructions\" (2-4 coaching sentences separated by ' | '). Every created name MUST also appear verbatim in the routine days.\n")
                append("- \"holdSeconds\": use a value > 0 ONLY for isometric holds (plank, hollow hold). For a rep based movement such as an explosive pull-up it MUST be 0 and \"reps\" MUST carry the rep count.\n")
                append("- \"reps\": use 0 ONLY when \"holdSeconds\" > 0. Never leave both at 0.\n")
                append("- \"prescription\" MUST be exactly '<sets> sets x <reps> reps' and NOTHING else (e.g. '4 sets x 8 reps'). For an isometric hold use '<sets> sets x <seconds>s' (e.g. '3 sets x 30s').\n")
                append("- NEVER put a load in \"prescription\" (no '80kg', no '@ 80kg', no 'bodyweight'). The load is set separately by the athlete.\n")
                append("- \"weight\" is the external load in kg for the movement. Use 0.0 for every bodyweight movement and NEVER the athlete's body weight there. Only put a number for a movement that is genuinely loaded with weight.\n")
                append("- NEVER put equipment ('Barbell', 'Dumbbells', 'with Barbell', ...) in \"prescription\".\n")
                append("- NEVER add any commentary, notes, tempo, rest or technique hints to \"prescription\".\n")
                append("- Order the exercises day by day, exactly as the athlete should perform them.\n")
                append(weekdayPromptRules())
            }

            val result = runCatching { generateAiText(prompt) }
            result.fold(
                onSuccess = { rawResponse ->
                    // The model may still wrap the object in prose: parse it, never store it raw
                    var parsed = parseAiRoutine(rawResponse, knownExercises)
                    if (parsed.isValid && parsed.items.isNotEmpty()) {
                        // A movement the catalog does not know is created through the same
                        // validated factory and only then re-parsed, so the routine carries real
                        // exercise ids instead of dangling names.
                        val created = createMissingRoutineExercises(rawResponse, parsed, knownExercises)
                        if (created.isNotEmpty()) {
                            parsed = parseAiRoutine(rawResponse, knownExercises + created)
                        }
                    }
                    if (!parsed.isValid || parsed.items.isEmpty()) {
                        generatedRoutine.value = ""
                        showAiError(
                            "Routine Generation Failed",
                            parsed.errorReason.ifBlank {
                                "The AI did not return any usable exercise. Please generate the routine again."
                            }
                        )
                    } else {
                        generatedRoutine.value = RoutineParser.serializeExercises(parsed.items)
                    }
                },
                onFailure = { error ->
                    generatedRoutine.value = ""
                    showAiError("Routine Generation Failed", geminiErrorMessage(error, "Your AI routine"))
                }
            )
            isGeneratingRoutine.value = false
        }
    }

    /**
     * Creates the exercises a generated routine declared as missing.
     *
     * This is the Routine Generator's own original mechanism: it never uses the AI Coach tools and
     * never receives an AI Coach tool definition. The model supplies metadata in
     * `new_exercises_to_create`; [AiExerciseFactory] validates each record, then the routine is
     * re-parsed so its items carry the new database ids.
     */
    private suspend fun createMissingRoutineExercises(
        rawResponse: String,
        parsed: AiRoutineSchedule.Parsed,
        knownExercises: List<Exercise>
    ): List<Exercise> {
        val unresolved = parsed.items.filter {
            it.exerciseId <= 0L && it.exerciseName.isNotBlank()
        }
        if (unresolved.isEmpty()) return emptyList()

        val declaredByName = mutableMapOf<String, Exercise>()
        runCatching {
            val root = JSONObject(RoutineParser.extractJson(rawResponse))
            val array = root.optJSONArray("new_exercises_to_create") ?: return@runCatching
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue
                AiExerciseFactory.fromJsonObject(obj, name)?.let {
                    declaredByName[AiExerciseName.canonical(name)] = it
                }
            }
        }

        val existingKeys = knownExercises.mapTo(mutableSetOf()) {
            AiExerciseName.canonical(it.name)
        }
        val created = mutableListOf<Exercise>()
        for (item in unresolved) {
            val key = AiExerciseName.canonical(item.exerciseName)
            if (key.isBlank() || key in existingKeys) continue
            val prototype = declaredByName[key] ?: AiExerciseFactory.fromJsonObject(
                JSONObject().apply {
                    put("name", item.exerciseName)
                    put("category", item.category)
                    put("bodyPart", "")
                    put("target", "")
                    put("equipment", item.equipment)
                    put("exerciseType", item.exerciseType)
                    put("instructions", "")
                },
                fallbackName = item.exerciseName
            ) ?: continue
            val id = insertCustomExercise(prototype.copy(id = 0))
            if (id <= 0L) continue
            created.add(prototype.copy(id = id))
            existingKeys.add(key)
        }
        return created
    }

    /**
     * The scheduling rules every routine prompt carries.
     *
     * The app applies the rules itself, so the prompt only has to ask for the right shape: the model
     * names the weekdays it was given, and [AiRoutineSchedule] works out the rest. The week start is
     * passed in because the model has to know where an unspecified sequence of workouts begins.
     */
    private fun weekdayPromptRules(usesDaysArray: Boolean = true): String {
        val firstDay = if (weekStartsOnMonday()) "Monday" else "Sunday"
        return buildString {
            append("\nSCHEDULE RULES:\n")
            if (usesDaysArray) {
                append("- The routine is a week of seven days. Use the \"days\" array, with one entry per training day.\n")
                append("- \"weekday\" MUST be the full name of the day of the week: Monday, Tuesday, Wednesday, Thursday, Friday, Saturday or Sunday. Never write a number and never write \"Day 1\".\n")
                append("- \"focus\" is a short label for the day, e.g. \"Upper Body Push\". Never put the weekday name in it.\n")
                append("- \"note\" is one short motivating sentence for the day, or an empty string.\n")
            } else {
                append("- \"weekday\" MUST be the full name of the day of the week: Monday, Tuesday, Wednesday, Thursday, Friday, Saturday or Sunday. Never write a number and never write \"Day 1\".\n")
                append("- Use \"weekday\" ONLY when the athlete's own text names the day of the week. When it does not, leave it out and use \"day\" instead.\n")
            }
            append("- Only include the days that hold a workout. Every day you leave out becomes a rest day.\n")
            append("- The athlete's week starts on $firstDay. Spread the training days over the week so that the same day is never used twice, and keep at least one rest day between two hard sessions.\n")
        }
    }

    /**
     * Turns an AI routine answer into the seven weekday slots of a routine.
     *
     * The scheduling rules are applied by [AiRoutineSchedule], not by the model: a weekday the
     * answer named is kept, a workout with no weekday named is laid out from the start of the
     * athlete's week, and every day nothing was assigned to is a rest day.
     */
    private suspend fun parseAiRoutine(
        rawResponse: String,
        knownExercises: List<Exercise>
    ): AiRoutineSchedule.Parsed {
        val exByName = knownExercises.associateBy { it.name.trim().lowercase() }
        return AiRoutineSchedule.parse(
            rawResponse = rawResponse,
            resolveExercise = { name ->
                exByName[name.trim().lowercase()]
                    ?: RoutineParser.findMatchingExercise(name, exByName.values)
            },
            weekStartsOnMonday = weekStartsOnMonday()
        )
    }

    // === UNSTRUCTURED WORKOUT PROGRAM PARSER (GEMINI AI) ===
    private val _unstructuredParseState = MutableStateFlow<UnstructuredParseState>(UnstructuredParseState.Idle)
    val unstructuredParseState: StateFlow<UnstructuredParseState> = _unstructuredParseState.asStateFlow()

    fun resetUnstructuredParseState() {
        _unstructuredParseState.value = UnstructuredParseState.Idle
    }

    /**
     * Registers an exercise straight into the local database (used by the AI flows).
     * AI registered exercises are never flagged as user created (the user did not type them),
     * but they are flagged as AI generated so the "ai_generated" search finds them and the user
     * can still edit or delete them.
     */
    /**
     * The primary target muscle to assume when the AI does not name one.
     *
     * The dataset's own vocabulary, matched to the exercise's category, so the TARGETS section of
     * the detail dialog says something true rather than nothing at all.
     */
    suspend fun insertCustomExercise(exercise: Exercise): Long {
        return repository.createAiGeneratedExercise(exercise)
    }

    /**
     * Flags the exercises that older app versions already stored as AI generated.
     *
     * The AI parser is the only writer besides the user and the built-in library, so any exercise
     * that is not part of the shipped library and that the user did not create must have come from
     * an AI parse. Runs once per app start and is a no-op on a fresh install.
     *
     * It deliberately keys off the ownership flags rather than the dataset source id: both library
     * and AI rows are non custom, so isAiGenerated is the only thing that tells them apart. Using
     * isCustom alone would flag every one of the 1300+ library exercises as AI generated.
     *
     * A library row the athlete edited is exempt, and that is not an optimisation. Renaming a library
     * exercise takes its name out of the dataset, so the "not in the library" test below matches it
     * and this would flag the row as AI generated ÔÇö on a release where the dataset happens to have
     * moved, and silently, in the background, with nothing the athlete did to cause it. Those edits
     * are exactly the ones already remembered by LibraryEditTracker, so the exemption is free.
     */
    fun backfillAiGeneratedExercises() {
        viewModelScope.launch(Dispatchers.IO) {
            val edited = LibraryEditTracker.editedIds(getApplication())
            val candidates = allExercises.value.filter { !it.isCustom && !it.isAiGenerated && it.id !in edited }
            if (candidates.isEmpty()) return@launch
            // Compare against the dataset asset, not against the table: a row the asset knows is
            // library, and an unknown non custom name can only have come from an AI parse.
            val libraryNames = try {
                DatasetSeeder.libraryNames(getApplication())
            } catch (e: Exception) {
                return@launch
            }
            candidates
                .filter { it.name.trim().lowercase() !in libraryNames }
                .forEach { repository.markExerciseAsAiGenerated(it.id) }
        }
    }

    fun parseAndImportUnstructuredWorkout(rawText: String, customName: String = "") {
        val trimmedInput = rawText.trim()
        if (trimmedInput.isBlank()) {
            _unstructuredParseState.value = UnstructuredParseState.Error("Please enter or paste your workout program.")
            return
        }

        // Quick check: if text contains no alphabetical letters at all (e.g. only numbers or punctuation)
        if (trimmedInput.none { it.isLetter() }) {
            _unstructuredParseState.value = UnstructuredParseState.Error(
                "The text does not contain any readable words or exercise names. Please enter an actual workout (e.g. 'Pushups = 15 reps x 3 sets')."
            )
            return
        }

        // No key -> explicit error dialog, never a mocked parse
        if (ensureApiKeyOrExplain("The AI parser")) return

        _unstructuredParseState.value = UnstructuredParseState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val currentExercises = repository.getAllExercises().first()
            // Includes built-in, AI generated and user created exercises
            val candidateSummary = catalogForPrompt(catalogNameBudget)

            val prompt = """
                You are a fitness parser and biomechanics expert.
                A user has entered an unstructured workout program:
                \"\"\"
                $trimmedInput
                \"\"\"

                ${athleteContextPromptBlock(includeBenchmarks = false, purpose = "accurate exercise matching")}

                COMPLETE LOCAL DATABASE CATALOG (ALL EXERCISES IN LOCAL APP, INCLUDING THE ONES CREATED BY THE USER):
                $candidateSummary

                CRITICAL PARSING RULES:
                1. GIBBERISH / UNREADABLE CHECK:
                   - If the text is pure gibberish (e.g. keyboard mashing like "asdfghjkl", random unreadable characters, meaningless noise, or text that has nothing to do with physical exercises, movements, or training), you MUST set "is_gibberish": true and explain why in "error_reason".
                   - If the text represents an actual workout in ANY custom format (such as shorthand "bench 3x10", "goblet squat 4x10", bullet points, notes with typos, or unlisted exercises), set "is_gibberish": false. ONLY throw an error if genuinely unintelligible.

                2. EXACT EXERCISE MATCHING (NO MISMATCHED SUBSTITUTIONS):
                   - If an exercise in the user's workout exists in the catalog above (for example: "Goblet Squat", "Knee Raises" -> "Hanging Knee Raise" or "Captain's Chair Knee Raise", "Bench Press" -> "Barbell Bench Press" or "Flat Dumbbell Bench Press", "Squat" -> "Barbell Back Squat"), you MUST use the EXACT exercise title from the catalog!
                   - NEVER substitute an exercise with a biomechanically different movement simply because it shares a word:
                     * DO NOT replace "Goblet Squat" with "Band Resisted Squat" or anything else. "Goblet Squat" IS in the catalog under [Legs] ÔÇö use "Goblet Squat"!
                     * DO NOT replace "Knee Raises" with "Ab Wheel Rollout from Knees" or "Clapping Push-up on Knees". Use "Hanging Knee Raise" (under [Core])!
                     * DO NOT replace squats with lunges, pull-ups with pulldowns, or bench press with flyes.

                3. DYNAMIC EXERCISE CREATION (new_exercises_to_create):
                   - If an exercise in the user's input is genuinely NOT present in the catalog above (e.g. "Banded Front Squat", "Pelican Curls", "Jefferson Deadlift", "Reverse Nordic"), do NOT force-map it to an incorrect existing exercise!
                   - Instead, output it under "new_exercises_to_create" so the app can automatically insert it into SQLite.
                   - Each new exercise MUST have:
                     - "name": String (exact proper title, e.g. "Banded Front Squat", "Pelican Curl")
                     - "category": String (must strictly be one of: "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Calisthenics")
                      - "bodyPart": String (must strictly be one of the dataset body part names used by the app: "chest", "back", "shoulders", "upper arms", "lower arms", "waist", "upper legs", "lower legs", "cardio", "neck")
                      - "target": String (primary muscle worked, e.g. "Pectorals", "Lats", "Abs", "Quadriceps")
                      - "secondaryMuscles": array of strings (synergist muscles, e.g. ["Triceps", "Shoulders"])
                      - "instructions": String (REQUIRED: 2 to 4 short sentences, one per step, written as coaching cues. Say how to set up, how to move through the rep, and what not to do. Separate the steps with " | ", e.g. "Set the bar on your upper back and brace your core. | Squat to parallel while keeping your heels down. | Drive up without letting your knees cave in.". NEVER leave this empty, blank or null.)
                     - "exerciseType": String (must strictly be one of: "STANDARD_WEIGHT", "ADDED_WEIGHT", "PURE_BODYWEIGHT")
                     - "equipment": String (e.g. "Barbell", "Dumbbell", "Cable", "Bodyweight", "Resistance Band", "Pull-up Bar", "Rings")
                     - "description": String (concise description)
                   - In the "exercises" list, use that exact same name for the newly created exercise.

                4. EXERCISE EXTRACTION:
                   - Extract each exercise in the exact sequence intended by the user:
                     - "name": String (exact matching catalog title or newly created name)
                     - "sets": Int (target sets, default 3)
                     - "reps": Int (target reps, default 8)
                     - "weight": Double (load in kg if specified, otherwise 0.0)
                     - "holdSeconds": Int (isometric hold in seconds, otherwise 0)
                     - "prescription": String (e.g. "4 sets x 8 reps" or "3 sets x 30s")
                       CRITICAL PRESCRIPTION RULES:
                       * "prescription" MUST be exactly "<sets> sets x <reps> reps" and NOTHING else (e.g. "4 sets x 8 reps"). For an isometric hold use "<sets> sets x <seconds>s" (e.g. "3 sets x 30s").
                       * NEVER put a load inside "prescription" (no "80kg", no "@ 80kg", no "bodyweight"). The load is set separately by the athlete.
                       * NEVER include equipment (e.g. do NOT write "Barbell", "Dumbbell", "Cable", "Machine", "with Barbell", "using Dumbbells", "(Barbell)") inside "prescription"! Equipment belongs strictly in the "equipment" field, NOT in "prescription".
                       * NEVER add commentary, notes, tempo, rest or technique hints to "prescription".
                     - "day": Int (training day number starting at 1, e.g. 1 for Day 1, 2 for Day 2)
                     - "dayTitle": String (title of the training day, e.g. "Day 1: Lower Body" or "Day 1")
                     - "weekday": String (the full name of the day of the week, e.g. "Monday"). Use it ONLY when the athlete's own text names the day of the week; leave it out when it does not.

                JSON FORMAT REQUIREMENT:
                Respond ONLY with a valid JSON object:
                {
                  "is_gibberish": false,
                  "error_reason": "",
                  "routine_name": "Custom Routine",
                  "routine_goal": "Strength & Hypertrophy",
                  "days_per_week": 1,
                  "new_exercises_to_create": [],
                  "exercises": [
                    {
                      "name": "Goblet Squat",
                      "sets": 3,
                      "reps": 10,
                      "weight": 0.0,
                      "holdSeconds": 0,
                      "prescription": "3 sets x 10 reps",
                      "day": 1,
                      "dayTitle": "Day 1"
                    }
                  ]
                }
            """.trimIndent() + "\n" + weekdayPromptRules(usesDaysArray = false)
            val geminiResult = runCatching { generateAiText(prompt) }

            if (geminiResult.isSuccess) {
                val rawResponse = geminiResult.getOrThrow()
                try {
                    val cleanedJson = RoutineParser.extractJson(rawResponse)
                    val json = JSONObject(cleanedJson)
                    val isGibberish = json.optBoolean("is_gibberish", false)
                    val errorReason = json.optString("error_reason", "")

                    if (isGibberish) {
                        val reasonText = if (errorReason.isNotBlank()) errorReason else "Unrecognized workout text. The input appears to be unreadable or does not specify recognizable exercises."
                        _unstructuredParseState.value = UnstructuredParseState.Error(reasonText)
                        return@launch
                    }

                    // Process new exercises to create in local database
                    val newExercisesJson = json.optJSONArray("new_exercises_to_create")
                    val newlyCreatedExercises = mutableListOf<Exercise>()
                    val existingByName = currentExercises.associateBy { it.name.lowercase().trim() }.toMutableMap()

                    if (newExercisesJson != null) {
                        for (i in 0 until newExercisesJson.length()) {
                            val obj = newExercisesJson.optJSONObject(i) ?: continue
                            val name = obj.optString("name").trim()
                            if (name.isBlank() || existingByName.containsKey(name.lowercase())) continue

                            val category = obj.optString("category", "Calisthenics")
                            val validCategory = if (MuscleCategory.ALL.contains(category)) category else "Calisthenics"
                            val type = obj.optString("exerciseType", ExerciseType.STANDARD_WEIGHT)
                            val validType = when (type) {
                                ExerciseType.ADDED_WEIGHT -> ExerciseType.ADDED_WEIGHT
                                ExerciseType.PURE_BODYWEIGHT -> ExerciseType.PURE_BODYWEIGHT
                                else -> ExerciseType.STANDARD_WEIGHT
                            }
                            val equipment = obj.optString("equipment", "Bodyweight")
                            val desc = obj.optString("description", "")

                            val newEx = Exercise(
                                name = name,
                                category = validCategory,
                                exerciseType = validType,
                                equipment = equipment,
                                description = desc
                            )
                            val insertedId = insertCustomExercise(newEx)
                            val created = newEx.copy(id = insertedId)
                            newlyCreatedExercises.add(created)
                            existingByName[name.lowercase()] = created
                        }
                    }

                    // The routine itself: the seven weekday slots the answer describes.
                    //
                    // The source text is the authority on which movement this is. The AI
                    // normalises aggressively and often drops the modifier: a "Decline Push-Up"
                    // in the pasted program routinely comes back as a plain "Push-Up", and the
                    // exact-name lookup would then succeed, so the decline is lost for good.
                    // Asking the source first costs one match per line and keeps the movement
                    // the athlete actually wrote.
                    val parsed = AiRoutineSchedule.parse(
                        rawResponse = rawResponse,
                        resolveExercise = { name ->
                            val fromSource = RoutineParser.resolveAgainstSource(
                                aiName = name,
                                sourceText = trimmedInput,
                                candidates = existingByName.values
                            )
                            val found = fromSource
                                ?: existingByName[name.trim().lowercase()]
                                ?: RoutineParser.findMatchingExercise(name, existingByName.values)
                            if (found != null) {
                                found
                            } else {
                                val fallbackEx = Exercise(
                                    name = name,
                                    category = "Calisthenics",
                                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                                    equipment = "Bodyweight",
                                    description = "Imported custom exercise"
                                )
                                val id = insertCustomExercise(fallbackEx)
                                val created = fallbackEx.copy(id = id)
                                newlyCreatedExercises.add(created)
                                existingByName[name.trim().lowercase()] = created
                                created
                            }
                        },
                        weekStartsOnMonday = weekStartsOnMonday()
                    )

                    if (!parsed.isValid || parsed.items.isEmpty()) {
                        _unstructuredParseState.value = UnstructuredParseState.Error(
                            parsed.errorReason.ifBlank {
                                "Could not extract any exercises from the provided text. Please verify the exercises and try again."
                            }
                        )
                        return@launch
                    }

                    val routineItems = parsed.items
                    val finalTitle = customName.trim().ifBlank {
                        parsed.routineName.ifBlank { "Imported Routine" }
                    }
                    val goal = parsed.routineGoal.ifBlank { "Custom Workout" }
                    val daysCount = parsed.trainingDays.coerceAtLeast(1)

                    val savedRoutine = SavedRoutine(
                        name = finalTitle,
                        description = "AI Parsed Program ÔÇó ${routineItems.size} exercises ÔÇó $daysCount training day${if (daysCount > 1) "s" else ""}",
                        daysPerWeek = daysCount,
                        goal = goal,
                        exercisesJson = RoutineParser.serializeExercises(routineItems)
                    )
                    val routineId = repository.saveRoutine(savedRoutine)
                    _unstructuredParseState.value = UnstructuredParseState.Success(
                        routine = savedRoutine.copy(id = routineId),
                        newExercisesCreated = newlyCreatedExercises,
                        exerciseCount = routineItems.size
                    )
                    return@launch
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to parse the Gemini JSON response of the parser", e)
                    _unstructuredParseState.value = UnstructuredParseState.Error(
                        "The AI answer could not be read as a workout program. Please try again."
                    )
                    return@launch
                }
            }

            // No mock parser fallback: report the real reason to the user
            geminiResult.exceptionOrNull()?.let { error ->
                _unstructuredParseState.value =
                    UnstructuredParseState.Error(geminiErrorMessage(error, "The workout parser"))
                showAiError("AI Parser Unavailable", geminiErrorMessage(error, "The workout parser"))
            }
        }
    }
    // === PROMPT FOR A WORKOUT ROUTINE (GEMINI 3.5 FLASH LITE) ===
    private val _customRoutinePromptState = MutableStateFlow<CustomRoutinePromptState>(CustomRoutinePromptState.Idle)
    val customRoutinePromptState: StateFlow<CustomRoutinePromptState> = _customRoutinePromptState.asStateFlow()

    fun resetCustomRoutinePromptState() {
        _customRoutinePromptState.value = CustomRoutinePromptState.Idle
    }

    fun saveCustomPromptRoutine(routine: SavedRoutine, onSaved: ((Long) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.saveRoutine(routine)
            withContext(Dispatchers.Main) {
                val current = _customRoutinePromptState.value
                if (current is CustomRoutinePromptState.Success) {
                    _customRoutinePromptState.value = current.copy(
                        routine = routine.copy(id = id),
                        isSaved = true
                    )
                }
                onSaved?.invoke(id)
            }
        }
    }

    fun generateRoutineFromCustomPrompt(userPrompt: String, customName: String = "") {
        val trimmedInput = userPrompt.trim()
        if (trimmedInput.isBlank()) {
            _customRoutinePromptState.value = CustomRoutinePromptState.Error(
                "Please enter a prompt describing your desired workout routine."
            )
            return
        }

        if (trimmedInput.none { it.isLetter() }) {
            _customRoutinePromptState.value = CustomRoutinePromptState.Error(
                "The prompt contains no readable words. Please describe what kind of workout routine you would like to generate (e.g. '4-day push/pull/legs split')."
            )
            return
        }

        val lettersOnly = trimmedInput.filter { it.isLetter() }
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y', 'A', 'E', 'I', 'O', 'U', 'Y')
        val hasVowel = lettersOnly.any { it in vowels }
        if (lettersOnly.length > 5 && !hasVowel) {
            _customRoutinePromptState.value = CustomRoutinePromptState.Error(
                "The input appears to be random letters or keyboard mashing. Please enter an actual workout routine request."
            )
            return
        }

        // No key -> explicit error dialog, never a mocked routine
        if (ensureApiKeyOrExplain("The AI routine prompt")) return

        _customRoutinePromptState.value = CustomRoutinePromptState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val currentExercises = repository.getAllExercises().first()
            // Includes built-in, AI generated and user created exercises
            val candidateSummary = catalogForPrompt(catalogNameBudget)

            val prompt = """
                You are an expert strength and calisthenics coach, personal trainer, and biomechanics authority.
                A user wants you to generate a structured workout routine based on their specific request/needs:
                \"\"\"
                $trimmedInput
                \"\"\"

                ${athleteContextPromptBlock(includeBenchmarks = true, purpose = "tailored exercise selection and progression")}

                COMPLETE LOCAL DATABASE CATALOG (ALL EXERCISES IN LOCAL APP, INCLUDING THE ONES CREATED BY THE USER):
                $candidateSummary

                CRITICAL ROUTINE GENERATION & FORMATTING RULES:
                1. GIBBERISH / VAGUE REQUEST VALIDATION:
                   - If the user's prompt is pure gibberish (e.g. keyboard mashing like "asdfghjkl", random unreadable characters, meaningless noise, or text that has nothing to do with physical exercises, movements, fitness, or workout routines), you MUST set "is_valid_request": false and explain why in "error_reason".
                   - If the user's prompt is excessively vague or does NOT actually ask for or describe a workout (e.g. single words like "hi", "test", "gym", "workout", "help" without any details on goals, muscles, split, days, or equipment), you MUST set "is_valid_request": false and set "error_reason" to "Your request is too vague. Please specify your goals, target muscle groups, preferred schedule (e.g. 3-day split), or available equipment."
                   - ONLY set "is_valid_request": true if the user actually wrote and requested a workout routine based on their needs or preferences!

                2. DATABASE EXERCISE MATCHING (NO MISMATCHED SUBSTITUTIONS):
                   - Whenever possible, choose exercises that ALREADY EXIST in the catalog above! Use their EXACT titles from the catalog.
                   - NEVER substitute an exercise with a biomechanically different movement simply because it shares a word.

                3. DYNAMIC EXERCISE CREATION (new_exercises_to_create):
                   - If the routine calls for a specialized or unique exercise that is genuinely NOT present in the catalog above (e.g. "Pelican Curl", "Sissy Squat", "Nordic Hamstring Curl", "Spanish Squat", "Powell Raise", "Deficit Push-up"), do NOT force-map it to an unrelated exercise!
                   - Put it in "new_exercises_to_create" so the app can automatically insert and register it into SQLite.
                   - Each new exercise MUST have:
                     - "name": String (exact proper title)
                     - "category": String (must strictly be one of: "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Calisthenics")
                      - "bodyPart": String (must strictly be one of the dataset body part names used by the app: "chest", "back", "shoulders", "upper arms", "lower arms", "waist", "upper legs", "lower legs", "cardio", "neck")
                      - "target": String (primary muscle worked, e.g. "Pectorals", "Lats", "Abs", "Quadriceps")
                      - "secondaryMuscles": array of strings (synergist muscles, e.g. ["Triceps", "Shoulders"])
                      - "instructions": String (REQUIRED: 2 to 4 short sentences, one per step, written as coaching cues. Say how to set up, how to move through the rep, and what not to do. Separate the steps with " | ", e.g. "Set the bar on your upper back and brace your core. | Squat to parallel while keeping your heels down. | Drive up without letting your knees cave in.". NEVER leave this empty, blank or null.)
                     - "exerciseType": String (must strictly be one of: "STANDARD_WEIGHT", "ADDED_WEIGHT", "PURE_BODYWEIGHT")
                     - "equipment": String (e.g. "Barbell", "Dumbbell", "Cable", "Bodyweight", "Resistance Band", "Pull-up Bar", "Rings")
                     - "description": String (concise biomechanical coaching cue or description)
                   - In the "exercises" list, use that exact same name for the newly created exercise.

                4. STRUCTURED WORKOUT FORMAT:
                   - Organize the routine into the training days of a week, using the "days" array, matching the user's requested split or schedule (default to 3 days if unspecified).
                   - Each day of the "days" array must have:
                     - "weekday": String (the full name of the day of the week, e.g. "Monday")
                     - "focus": String (a short label for the day, e.g. "Upper Body Push")
                     - "note": String (one short motivating sentence, or an empty string)
                     - "exercises": an array of the exercises of that day
                   - Each exercise in the routine must have:
                     - "name": String (exact matching catalog title or newly created name)
                     - "sets": Int (target sets, e.g. 3 or 4)
                     - "reps": Int (target reps, e.g. 8 or 10 or 12, or 0 if holdSeconds > 0)
                     - "weight": Double (load in kg if specified, otherwise 0.0)
                     - "holdSeconds": Int (isometric hold in seconds, otherwise 0)
                     - "prescription": String (e.g. "4 sets x 8 reps" or "3 sets x 30s")
                       CRITICAL PRESCRIPTION RULES:
                       * "prescription" MUST be exactly "<sets> sets x <reps> reps" and NOTHING else (e.g. "4 sets x 8 reps"). For an isometric hold use "<sets> sets x <seconds>s" (e.g. "3 sets x 30s").
                       * NEVER put a load inside "prescription" (no "80kg", no "@ 80kg", no "bodyweight"). The load is set separately by the athlete.
                       * NEVER include equipment (e.g. do NOT write "Barbell", "Dumbbell", "Cable", "Machine", "with Barbell", "using Dumbbells", "(Barbell)") inside "prescription"! Equipment belongs strictly in the "equipment" field, NOT in "prescription".
                       * NEVER add commentary, notes, tempo, rest or technique hints to "prescription".

                JSON FORMAT REQUIREMENT:
                Respond ONLY with a valid JSON object:
                {
                  "is_valid_request": true,
                  "error_reason": "",
                  "routine_name": "Suggested Routine Name",
                  "routine_goal": "Goal description",
                  "new_exercises_to_create": [],
                  "days": [
                    {
                      "weekday": "Monday",
                      "focus": "Push & Core",
                      "note": "",
                      "exercises": [
                        {
                          "name": "Barbell Bench Press",
                          "sets": 4,
                          "reps": 8,
                          "weight": 0.0,
                          "holdSeconds": 0,
                          "prescription": "4 sets x 8 reps"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent() + "\n" + weekdayPromptRules()

            val geminiResult = runCatching { generateAiText(prompt) }

            if (geminiResult.isSuccess) {
                val rawResponse = geminiResult.getOrThrow()
                try {
                    val cleanedJson = RoutineParser.extractJson(rawResponse)
                    val json = JSONObject(cleanedJson)
                    val isValidRequest = json.optBoolean("is_valid_request", !json.optBoolean("is_gibberish", false))
                    val errorReason = json.optString("error_reason", "")

                    if (!isValidRequest) {
                        val reasonText = if (errorReason.isNotBlank()) errorReason else "Your prompt does not specify a recognizable workout routine request. Please provide more details on your fitness goals, target muscle groups, or equipment."
                        _customRoutinePromptState.value = CustomRoutinePromptState.Error(reasonText)
                        return@launch
                    }

                    // Process new exercises to create in local database
                    val newExercisesJson = json.optJSONArray("new_exercises_to_create")
                    val newlyCreatedExercises = mutableListOf<Exercise>()
                    val existingByName = currentExercises.associateBy { it.name.lowercase().trim() }.toMutableMap()

                    if (newExercisesJson != null) {
                        for (i in 0 until newExercisesJson.length()) {
                            val obj = newExercisesJson.optJSONObject(i) ?: continue
                            val name = obj.optString("name").trim()
                            if (name.isBlank() || existingByName.containsKey(name.lowercase())) continue

                            val category = obj.optString("category", "Calisthenics")
                            val validCategory = if (MuscleCategory.ALL.contains(category)) category else "Calisthenics"
                            val type = obj.optString("exerciseType", ExerciseType.STANDARD_WEIGHT)
                            val validType = when (type) {
                                ExerciseType.ADDED_WEIGHT -> ExerciseType.ADDED_WEIGHT
                                ExerciseType.PURE_BODYWEIGHT -> ExerciseType.PURE_BODYWEIGHT
                                else -> ExerciseType.STANDARD_WEIGHT
                            }
                            val equipment = obj.optString("equipment", "Bodyweight")
                            val desc = obj.optString("description", "AI registered exercise")

                            val newEx = Exercise(
                                name = name,
                                category = validCategory,
                                exerciseType = validType,
                                equipment = equipment,
                                description = desc
                            )
                            val insertedId = insertCustomExercise(newEx)
                            val created = newEx.copy(id = insertedId)
                            newlyCreatedExercises.add(created)
                            existingByName[name.lowercase()] = created
                        }
                    }

                    // The routine itself: the seven weekday slots the answer describes.
                    val parsed = AiRoutineSchedule.parse(
                        rawResponse = rawResponse,
                        resolveExercise = { name ->
                            val found = existingByName[name.trim().lowercase()]
                                ?: RoutineParser.findMatchingExercise(name, existingByName.values)
                            if (found != null) {
                                found
                            } else {
                                val fallbackEx = Exercise(
                                    name = name,
                                    category = "Calisthenics",
                                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                                    equipment = "Bodyweight",
                                    description = "Imported custom exercise"
                                )
                                val id = insertCustomExercise(fallbackEx)
                                val created = fallbackEx.copy(id = id)
                                newlyCreatedExercises.add(created)
                                existingByName[name.trim().lowercase()] = created
                                created
                            }
                        },
                        weekStartsOnMonday = weekStartsOnMonday()
                    )

                    if (!parsed.isValid || parsed.items.isEmpty()) {
                        _customRoutinePromptState.value = CustomRoutinePromptState.Error(
                            parsed.errorReason.ifBlank {
                                "Could not generate exercises for the given prompt. Please specify your workout goals more clearly."
                            }
                        )
                        return@launch
                    }

                    val routineItems = parsed.items
                    val finalTitle = customName.trim().ifBlank {
                        parsed.routineName.ifBlank { "AI Custom Routine" }
                    }
                    val goal = parsed.routineGoal.ifBlank { "Custom Workout Goal" }
                    val daysCount = parsed.trainingDays.coerceAtLeast(1)

                    val generatedRoutine = SavedRoutine(
                        name = finalTitle,
                        description = "AI Prompt Routine ÔÇó ${routineItems.size} exercises ÔÇó $daysCount training day${if (daysCount > 1) "s" else ""}",
                        daysPerWeek = daysCount,
                        goal = goal,
                        exercisesJson = RoutineParser.serializeExercises(routineItems)
                    )

                    // Emitted as Success (unpersisted until user chooses to save)
                    _customRoutinePromptState.value = CustomRoutinePromptState.Success(
                        routine = generatedRoutine,
                        newExercisesCreated = newlyCreatedExercises,
                        exerciseCount = routineItems.size,
                        isSaved = false
                    )
                    return@launch
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to parse the Gemini JSON prompt routine", e)
                    _customRoutinePromptState.value = CustomRoutinePromptState.Error(
                        "The AI answer could not be read as a routine. Please try again."
                    )
                    return@launch
                }
            }

            // No mock routine fallback: report the real reason to the user
            geminiResult.exceptionOrNull()?.let { error ->
                _customRoutinePromptState.value =
                    CustomRoutinePromptState.Error(geminiErrorMessage(error, "Your AI routine"))
                showAiError("AI Routine Unavailable", geminiErrorMessage(error, "Your AI routine"))
            }
        }
    }
    // === APP UPDATES AND ABOUT ===

    /**
     * The service that reads the official releases of this repository.
     *
     * It is created once and reused, so the connections are pooled and the release metadata is
     * read from the same place every time.
     */
    private val releaseApi = ReleaseApi()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /**
     * The release the startup check found, while its prompt is on screen.
     *
     * Kept apart from [updateState] so the prompt only ever appears for an automatic check, never
     * when the athlete pressed "Check for updates" in the settings themselves.
     */
    private val _startupUpdate = MutableStateFlow<GithubRelease?>(null)
    val startupUpdate: StateFlow<GithubRelease?> = _startupUpdate.asStateFlow()

    /**
     * Looks for a newer release when the app starts, unless the athlete turned that off or already
     * asked not to be reminded for this installed version.
     *
     * A failure is swallowed on purpose: a workout app must not open on an error about GitHub.
     */
    private suspend fun maybeCheckForUpdatesOnStart() {
        if (!userPreferencesRepo.autoCheckUpdatesOnStartFlow.first()) return
        val dismissedFor = userPreferencesRepo.dismissedUpdatePromptVersionFlow.first()
        if (dismissedFor == BuildConfig.VERSION_NAME) return

        releaseApi.fetchLatestRelease()
            .onSuccess { release ->
                cacheRelease(release)
                val state = evaluateRelease(release)
                // Only a real, newer release opens the prompt. Up to date and unreadable versions
                // stay silent at startup.
                if (state is UpdateState.Available) {
                    if (_updateState.value is UpdateState.Idle) {
                        _updateState.value = state
                    }
                    _startupUpdate.value = release
                }
            }
    }

    /**
     * Closes the startup update prompt.
     *
     * Closing without [dontShowAgain] is not remembered: the prompt is meant to come back on the
     * next start until the athlete either updates or asks it to stop. The "do not show again"
     * suppression is stored against the running version, so it lapses by itself after an update.
     */
    fun closeStartupUpdatePrompt(dontShowAgain: Boolean = false) {
        if (dontShowAgain) {
            viewModelScope.launch(Dispatchers.IO) {
                userPreferencesRepo.dismissUpdatePromptForVersion(BuildConfig.VERSION_NAME)
            }
        }
        _startupUpdate.value = null
    }

    /**
     * The release information the About screen shows.
     *
     * It starts from the cached copy, so the screen has something to say before the network has
     * answered, and it is refreshed whenever the screen is opened.
     */
    private val _aboutState = MutableStateFlow(
        AboutState(installedVersion = BuildConfig.VERSION_NAME)
    )
    val aboutState: StateFlow<AboutState> = _aboutState.asStateFlow()

    /** The running check or download, so it can be cancelled. */
    private var updateJob: Job? = null

    /**
     * Looks the newest published release up and works out whether it is newer than the build that
     * is running.
     */
    fun checkForUpdates() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            releaseApi.fetchLatestRelease()
                .onSuccess { release ->
                    cacheRelease(release)
                    _updateState.value = evaluateRelease(release)
                }
                .onFailure { error ->
                    _updateState.value = UpdateState.Failed(
                        error.message ?: "The update could not be checked."
                    )
                }
        }
    }

    /** Stops a running check or download and puts the interface back where it started. */
    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        _updateState.value = UpdateState.Cancelled
    }

    /** Puts the update interface back to its starting state. */
    fun dismissUpdate() {
        updateJob?.cancel()
        updateJob = null
        _updateState.value = UpdateState.Idle
    }

    /**
     * Downloads the release APK the update dialog is offering.
     *
     * The download is only started when the athlete asked for it, and it is written into the app's
     * own storage, where nothing else on the device can read it.
     */
    fun downloadUpdate() {
        // A retry from the startup prompt can happen after a failed download, when the shared
        // state is no longer Available. The release the prompt is showing is still the target.
        val available = _updateState.value as? UpdateState.Available
            ?: _startupUpdate.value?.let { release ->
                release.installableApk()?.let { UpdateState.Available(release, it) }
            }
            ?: return
        val asset = available.asset ?: return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(available.release, asset, 0L, asset.size)
            val target = ApkInstaller.updateFile(getApplication(), asset.name)
            val lastPercent = intArrayOf(-1)
            releaseApi.downloadAsset(asset, target) { read, total ->
                // Only tell the interface when the percentage moved, so a fast download does not
                // flood it with updates.
                val percent = if (total > 0L) ((read * 100L) / total).toInt() else -1
                if (percent != lastPercent[0]) {
                    lastPercent[0] = percent
                    _updateState.value = UpdateState.Downloading(available.release, asset, read, total)
                }
            }.onSuccess { file ->
                // The check returns null when the file is good, and the reason it is not when it
                // is not. A good file is ready to install; a bad one is deleted and the athlete
                // is told what was wrong with it.
                val problem = verifyDownload(asset, file)
                if (problem == null) {
                    _updateState.value = UpdateState.ReadyToInstall(available.release, file)
                } else {
                    file.delete()
                    _updateState.value = UpdateState.Failed(problem)
                }
            }.onFailure { error ->                _updateState.value = UpdateState.Failed(
                    error.message ?: "The download did not finish."
                )
            }
        }
    }

    /**
     * Checks a downloaded file before it is handed to Android.
     *
     * The digest GitHub publishes is checked when there is one. The package is always checked, so
     * a file that is not an update of this app is never handed to the installer, and so is the key
     * it is signed with.
     *
     * @return null when the file is good, or the reason it is not.
     */
    private fun verifyDownload(asset: ReleaseAsset, file: java.io.File): String? {
        val packageProblem = ApkInstaller.verify(getApplication(), file)
            .exceptionOrNull()
            ?.message
        return UpdateVerification.problem(
            expectedDigest = asset.sha256,
            actualDigest = sha256Of(file),
            packageProblem = packageProblem
        )
    }

    /**
     * The SHA-256 of a file, as a hex string.
     *
     * @return the digest, or null when the file could not be read.
     */
    private fun sha256Of(file: java.io.File): String? = try {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    } catch (_: Exception) {
        null
    }

    /**
     * Works out what the release means for the build that is running.
     *
     * A release that is not newer is never offered: an installed build that is ahead of the
     * published one is up to date as far as the athlete is concerned, and offering it a "newer"
     * release would be offering a downgrade.
     */
    private fun evaluateRelease(release: GithubRelease): UpdateState {
        val latest = release.version
        if (latest == null) {
            return UpdateState.Failed(
                "The release ${release.tagName} does not carry a version the app can read.",
                canRetry = false
            )
        }
        val installed = AppVersion.parse(BuildConfig.VERSION_NAME)
        if (installed == null) {
            return UpdateState.Failed(
                "The installed version could not be read, so no update was offered.",
                canRetry = false
            )
        }
        if (!latest.isNewerThan(installed)) {
            return UpdateState.UpToDate(release)
        }
        return UpdateState.Available(release, release.installableApk())
    }

    /**
     * Reads the release information for the About screen.
     *
     * The cached copy is shown first, so the screen is never empty, and the network answer replaces
     * it when it arrives.
     */
    fun loadAboutInfo() {
        viewModelScope.launch {
            userPreferencesRepo.cachedReleaseFlow.first()?.let { cached ->
                val release = ReleaseApi.parseRelease(cached)
                if (release != null) {
                    _aboutState.value = _aboutState.value.copy(
                        latestRelease = release,
                        isFromCache = true
                    )
                }
            }
            _aboutState.value = _aboutState.value.copy(isLoading = true, error = null)
            releaseApi.fetchLatestRelease()
                .onSuccess { release ->
                    cacheRelease(release)
                    _aboutState.value = _aboutState.value.copy(
                        latestRelease = release,
                        isLoading = false,
                        error = null,
                        isFromCache = false
                    )
                }
                .onFailure { error ->
                    _aboutState.value = _aboutState.value.copy(
                        isLoading = false,
                        error = error.message ?: "The release information could not be read."
                    )
                }
        }
    }

    /** Keeps a copy of the release information, so the About screen works without a network. */
    private fun cacheRelease(release: GithubRelease) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.saveCachedRelease(release)
        }
    }

    /** The page the official releases are on. */
    fun releasesPageUrl(): String = releaseApi.releasesPageUrl()

    companion object {
        /** A monthly summary needs more than this many logged sets to read a trend from. */
        const val MIN_SETS_FOR_MONTHLY_SUMMARY = 2

        /** Longest athlete name the profile accepts. */
        const val MAX_NAME_LENGTH = 15

        /** Longest name a workout in progress can be given. */
        const val MAX_WORKOUT_NAME_LENGTH = 60

        /** Timeline note used for a body weight the athlete entered through "Log Today". */
        const val WEIGH_IN_NOTE_MORNING = "Morning weigh-in"

        /**
         * Timeline note used when the body weight changed through the "Edit Athlete Profile" popup.
         * Deliberately different so it is never mistaken for a morning weigh-in.
         */
        const val WEIGH_IN_NOTE_PROFILE_UPDATE = "Profile update"

        private const val MISSING_API_KEY_MESSAGE =
            "No AI API key configured. Open Profile & Settings, choose your AI provider, and save " +
                "its API key."

        private val DATE_FORMAT_DAY = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
        private val DATE_FORMAT_CHART = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("MMM d", Locale.getDefault())
        }
    }
}