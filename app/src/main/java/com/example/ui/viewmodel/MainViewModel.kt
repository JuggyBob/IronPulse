package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiModelInfo
import com.example.data.db.AppDatabase
import com.example.data.model.ActiveRoutineWorkoutState
import com.example.data.model.ActiveRestTimerState
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.CustomRestTimer
import com.example.data.model.BodyWeightLog
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.ExerciseSearch
import com.example.data.model.GibberishNameDetector
import com.example.data.seed.DatasetSeeder
import com.example.data.seed.LibraryEditTracker
import com.example.util.ProfileMediaStore
import com.example.data.model.ExerciseFilters
import com.example.data.model.ExerciseSchema
import com.example.data.seed.toJsonArray
import com.example.data.model.ExerciseMedia
import com.example.data.model.PredefinedRoutines
import com.example.data.model.ExerciseType
import com.example.data.model.GeminiSummary
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
import com.example.data.model.SessionWithSets
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.UnstructuredParseState
import com.example.data.model.CustomRoutinePromptState
import com.example.data.model.WeightUnit
import com.example.data.model.WeightConversion
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutDraft
import com.example.data.model.WorkoutDraftCodec
import com.example.data.model.WorkoutSession
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FitnessRepository
import com.example.ui.components.ChartPoint
import com.example.util.ExerciseMediaStore
import com.example.util.VibrationHelper
import com.example.util.SoundHelper
import com.example.util.RestTimerScheduler
import com.example.util.RestTimerEventManager
import com.example.util.RestTimerAlertCoordinator
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
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
    val timestamp: Long = System.currentTimeMillis()
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
        bodyWeightDao = db.bodyWeightDao(),
        chatDao = db.chatDao(),
        geminiSummaryDao = db.geminiSummaryDao()
    )

    private val userPreferencesRepo = UserPreferencesRepository(application)

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

    private val _availableModels = MutableStateFlow<List<GeminiModelInfo>>(GeminiClient.getCuratedModelList())
    val availableModels: StateFlow<List<GeminiModelInfo>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    fun loadAvailableModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingModels.value = true
            val activeKey = when {
                geminiApiKey.value.isNotBlank() -> geminiApiKey.value.trim()
                userProfile.value.customApiKey?.isNotBlank() == true -> userProfile.value.customApiKey!!.trim()
                BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
                else -> ""
            }
            val models = GeminiClient.fetchAvailableModels(activeKey)
            _availableModels.value = models
            _isLoadingModels.value = false
        }
    }

    fun saveGeminiApiKey(apiKey: String) {
        val cleanKey = apiKey.trim()
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.saveGeminiApiKey(cleanKey)
            val current = repository.getUserProfile().firstOrNull() ?: userProfile.value
            repository.saveUserProfile(current.copy(customApiKey = cleanKey))
        }
    }

    fun saveGeminiModel(modelName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.saveGeminiModel(modelName)
        }
    }

    suspend fun validateAndSaveGeminiModel(modelName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanModel = modelName.trim().removePrefix("models/")
        if (cleanModel.isBlank()) {
            userPreferencesRepo.saveGeminiModel("")
            return@withContext Result.success(Unit)
        }

        val activeKey = when {
            geminiApiKey.value.isNotBlank() -> geminiApiKey.value.trim()
            userProfile.value.customApiKey?.isNotBlank() == true -> userProfile.value.customApiKey!!.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }

        if (activeKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No Gemini API key available. Please enter and save your API Key in Profile first to test custom models.")
            )
        }

        val testResult = GeminiClient.validateModel(cleanModel, activeKey)
        if (testResult.isSuccess) {
            userPreferencesRepo.saveGeminiModel(cleanModel)
            Result.success(Unit)
        } else {
            Result.failure(testResult.exceptionOrNull() ?: Exception("Failed to reach model '$cleanModel'"))
        }
    }

    // === USER PROFILE ===
    val userProfile: StateFlow<UserProfile> = combine(
        repository.getUserProfile(),
        userPreferencesRepo.userProfileFlow
    ) { roomProfile, prefProfile ->
        prefProfile ?: roomProfile ?: UserProfile(id = 1)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserProfile(id = 1)
        )

    /**
     * The athlete's body weight right now, in kilograms, or 0 when the profile has no usable weight.
     *
     * Taken when a set is created, so the set carries the weight it was done at. A later weigh-in
     * must not rewrite it.
     */
    private fun currentBodyWeightKg(): Double =
        BodyWeightTimeline.currentWeightOrNull(userProfile.value.currentWeightKg) ?: 0.0

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

            val customKey = geminiApiKey.value.ifBlank { userProfile.value.customApiKey }
            val result = GeminiClient.generateContent(
                prompt = prompt,
                userCustomKey = customKey,
                customModel = geminiModelName.value.ifBlank { null }
            )

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
    private fun parseAiExerciseJson(rawResponse: String, fallbackName: String): Exercise? {
        val cleaned = RoutineParser.extractJson(rawResponse)
        val obj = try {
            JSONObject(cleaned)
        } catch (_: Exception) {
            return null
        }

        // The model was told to answer exactly this when the name is not a real exercise
        if (obj.optString("error").isNotBlank()) return null

        val name = obj.optString("name").trim().ifBlank { fallbackName }
        if (RoutineParser.looksLikeProseNotExercise(name)) return null

        // A refused name that came back as real prose, or an echo of the gibberish, is not an exercise
        if (GibberishNameDetector.isGibberish(name)) return null

        val category = obj.optString("category", "").trim()
        val validCategory = if (MuscleCategory.ALL.contains(category)) category else "Calisthenics"
        val rawType = obj.optString("exerciseType", "").trim().uppercase()
        val validType = when (rawType) {
            ExerciseType.ADDED_WEIGHT -> ExerciseType.ADDED_WEIGHT
            ExerciseType.PURE_BODYWEIGHT -> ExerciseType.PURE_BODYWEIGHT
            else -> ExerciseType.STANDARD_WEIGHT
        }
        val equipment = obj.optString("equipment", "").trim().ifBlank { "Bodyweight" }
        val description = obj.optString("description", "").trim().ifBlank {
            "$name performed with $equipment."
        }

        // Dataset fields, so an AI created exercise is indistinguishable from a seeded one to every
        // reader of the row. The AI is asked for the dataset's ten body parts and a target muscle;
        // anything else it invents is dropped rather than stored, because the body map and the
        // TARGETS section both filter on exact values.
        val bodyPart = obj.optString("bodyPart", "").trim().lowercase()
            .takeIf { it in ExerciseSchema.DATASET_BODY_PARTS }
            ?: ExerciseSchema.categoryToBodyPart(validCategory)
        // The prompt asks for a primary target muscle and for secondary muscles, and the model
        // usually supplies them. When it does not, the TARGETS section of the detail dialog is
        // skipped entirely — the row saves fine, looks complete, and simply says nothing about what
        // the movement trains. Falling back to the category keeps the section populated with
        // something true, because the category was already validated against the fixed seven.
        val target = obj.optString("target", "").trim().ifBlank { fallbackTargetForCategory(validCategory) }
        val secondary = obj.optJSONArray("secondaryMuscles")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).trim().takeIf(String::isNotBlank) }
        }.orEmpty()

        return Exercise(
            name = name,
            category = validCategory,
            bodyPart = bodyPart,
            target = target,
            secondaryMuscles = secondary.toJsonArray(),
            exerciseType = validType,
            instructions = obj.optString("instructions", "").trim(),
            equipment = equipment,
            description = description,
            // No media: the dataset has no animation for an invented movement, and the UI shows the
            // placeholder rather than borrowing an unrelated one.
            mediaPath = "",
            sourceId = "",
            isCustom = true,
            isAiGenerated = true
        )
    }

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
     * athlete's own unit — a stored kilogram sent as-is would read as the wrong load to an athlete
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
        val savedAt = System.currentTimeMillis()
        val setsToSave = currentSets
            .filter { it.isCompleted }
            .mapIndexed { index, set -> set.copy(timestamp = savedAt + index) }

        viewModelScope.launch(Dispatchers.IO) {
            if (setsToSave.isNotEmpty()) {
                val finalSession = currentSession.copy(
                    endTime = System.currentTimeMillis(),
                    notes = notes
                )
                val sessionId = repository.insertSession(finalSession)
                val setsWithSessionId = setsToSave.map { it.copy(sessionId = sessionId) }
                repository.insertSets(setsWithSessionId)
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
     * Writes the workout in progress to disk.
     *
     * Does nothing when no workout is running, so the loop can call it unconditionally.
     */
    fun saveActiveWorkoutDraftNow() {
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
                dayNumber = routine.dayNumber ?: 1,
                dayTitle = routine.dayTitle.orEmpty(),
                dayNote = routine.dayNote,
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
                dayNumber = draft.dayNumber,
                dayTitle = draft.dayTitle.ifBlank { "Day 1" },
                dayNote = draft.dayNote
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
        //    today, and two sets done at different body weights are two different tags. A plain
        //    0.0 entry is never added here, it used to appear next to it as a misleading "BW: 0 kg".
        for (set in sets) {
            if (set.weight == 0.0 && set.addedWeight == 0.0) {
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
        fun effectiveWeightOrNull(set: LoggedSet): Double? = when (set.exerciseType) {
            ExerciseType.STANDARD_WEIGHT -> set.weight
            ExerciseType.ADDED_WEIGHT -> bodyWeightOf(set)?.let { it + set.addedWeight }
            else -> bodyWeightOf(set)
        }

        // Filter by isolated weight tag if specified
        val filteredSets = if (addedWeightFilter != null) {
            sets.filter { set ->
                set.weight == addedWeightFilter ||
                set.addedWeight == addedWeightFilter ||
                (set.weight == 0.0 && set.addedWeight == 0.0 && bodyWeightOf(set) == addedWeightFilter)
            }
        } else {
            sets
        }

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
                        if (set.reps <= 1) effectiveWeight
                        else effectiveWeight * (36.0 / (37.0 - set.reps.coerceAtMost(36).toDouble()))
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
                delay(AUTO_SAVE_INTERVAL_MS)
                saveActiveWorkoutDraftNow()
            }
        }

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
        }
    }

    fun deleteWeightLog(log: BodyWeightLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteWeightLog(log)
        }
    }

    fun deleteWeightLogById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteWeightLogById(id)
        }
    }

    fun deleteLoggedSet(set: LoggedSet) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSet(set)
            renumberSetsForExercise(set.exerciseName)
        }
    }

    fun deleteSets(sets: List<LoggedSet>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSets(sets)
            // Every exercise touched by this deletion may now have gaps in its set numbering.
            sets.map { it.exerciseName }.distinct().forEach { renumberSetsForExercise(it) }
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
        _availableModels.value = GeminiClient.getCuratedModelList()

        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllUserData()
            userPreferencesRepo.clearAllPreferences()
            // The profile picture is a file in the app's own storage, not a row in the database,
            // so the database wipe does not touch it. It has to go explicitly, or "delete all app
            // data" would leave a copy of the athlete's photo behind.
            ProfileMediaStore.delete(getApplication())
            // Hand the coach a brand new, empty session so the chat stays usable after the wipe.
            val freshSession = ensureDefaultChatSession()
            withContext(Dispatchers.Main) {
                currentChatSession.value = freshSession
            }
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

            // Each set carries the body weight it was done at. The analyzer only has a timestamp to
            // work with, so the snapshot is looked up by it: sets logged in the same session share
            // a timestamp and therefore a body weight. A set logged before the snapshot existed has
            // none, and falls back to the profile weight, which is the best guess available.
            val bodyWeightByTimestamp = allSets.associate { it.timestamp to it.bodyWeightKg }
            fun bodyWeightAt(timestamp: Long): Double? =
                bodyWeightByTimestamp[timestamp]?.takeIf { it > 0.0 }
                    ?: BodyWeightTimeline.currentWeightOrNull(userProfile.value.currentWeightKg)

            val summary = MonthlyExerciseAnalyzer.build(
                exerciseName = exerciseName,
                exerciseType = exerciseType,
                weightUnit = summaryUnit,
                sets = allSets,
                bodyWeightAt = ::bodyWeightAt
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
            val customKey = geminiApiKey.value.ifBlank { userProfile.value.customApiKey }

            val result = GeminiClient.generateContent(
                prompt = prompt,
                userCustomKey = customKey,
                customModel = geminiModelName.value.ifBlank { null }
            )

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

    private fun monthlySummaryErrorMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("API key", ignoreCase = true) -> MISSING_API_KEY_MESSAGE
            raw.contains("401") || raw.contains("403") ->
                "Gemini rejected your API key. Open the Profile tab and check the key saved there."
            raw.contains("429") ->
                "Gemini is rate limiting your requests right now. Wait about a minute and try again."
            raw.contains("400") ->
                "Gemini could not process the request. Please try again."
            raw.isBlank() -> "The monthly summary could not be generated. Please try again."
            else -> "The monthly summary could not be generated: $raw"
        }
    }

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

    /** True when an API key is available from either the preferences or the profile. */
    fun hasGeminiApiKey(): Boolean =
        geminiApiKey.value.isNotBlank() ||
                userProfile.value.customApiKey?.isNotBlank() == true ||
                (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")

    /**
     * Shows the "missing API key" dialog when the app cannot reach Gemini.
     * @return true when the caller must abort the request.
     */
    private fun ensureApiKeyOrExplain(featureName: String): Boolean {
        if (hasGeminiApiKey()) return false
        showAiError("API Key Required", MISSING_API_KEY_MESSAGE)
        Log.w("MainViewModel", "$featureName requires a Gemini API key")
        return true
    }

    /** Translates a Gemini failure into a message the user can act on. */
    private fun geminiErrorMessage(error: Throwable, featureName: String): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("API key", ignoreCase = true) -> MISSING_API_KEY_MESSAGE
            raw.contains("401") || raw.contains("403") ->
                "Gemini rejected your API key. Open the Profile tab and check the key saved there."
            raw.contains("429") ->
                "Gemini is rate limiting your requests right now. Wait about a minute and try again."
            raw.isBlank() -> "$featureName could not be generated. Please try again."
            else -> "$featureName failed: $raw"
        }
    }

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
            append("# ${summary.exerciseName} — ${MonthlyExerciseAnalyzer.WINDOW_DAYS} Day Summary\n")
            append("## Trend\n")
            append("One short paragraph (2-3 sentences) stating whether strength is trending up, down or holding steady, and the single biggest reason why.\n")
            append("## Key Numbers\n")
            append("4 to 6 bullets covering: training days, total sets, total volume, best estimated 1RM with the set it came from, and the change across the period.\n")
            append("## Session Log\n")
            append("3 to 8 bullets, one per training day, in the format \"- Mar 3 — 4 sets, best set 80kg x 5, est. 1RM 93kg\". Cover the most representative days, not necessarily all of them.\n")
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

    fun setWeightUnit(unit: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesRepo.setWeightUnit(unit)
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
    val activityDays: StateFlow<List<ActivityDay>> = repository.getAllSessionsWithSets()
        .map { sessions -> buildActivityDays(sessions) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildActivityDays(sessions: List<SessionWithSets>): List<ActivityDay> {
        val oneYearAgo = ActivityHeatmap.startOfDay(
            java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -12) }.timeInMillis
        )

        val minutesByDay = HashMap<Long, Int>()
        val volumeByDay = HashMap<Long, Double>()
        val setsByDay = HashMap<Long, Int>()

        sessions.forEach { wrapper ->
            val day = ActivityHeatmap.startOfDay(wrapper.session.startTime)
            if (day < oneYearAgo) return@forEach

            val setTimestamps = wrapper.sets.map { it.timestamp }
            val spanMinutes = if (setTimestamps.size >= 2) {
                ((setTimestamps.max() - setTimestamps.min()) / 60_000L).toInt().coerceAtLeast(0)
            } else {
                0
            }
            val sessionMinutes = if (wrapper.session.endTime > wrapper.session.startTime) {
                ((wrapper.session.endTime - wrapper.session.startTime) / 60_000L).toInt()
            } else {
                0
            }
            // The session duration wins when it is meaningful, the set span is the fallback
            val minutes = maxOf(sessionMinutes, spanMinutes)
            val volume = wrapper.sets.sumOf { (it.weight + it.addedWeight) * it.reps }

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

    fun saveRoutine(routine: SavedRoutine) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRoutine(routine)
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
        }
    }

    fun deleteRoutine(routine: SavedRoutine) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRoutine(routine)
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
                        dayNumber = day.dayNumber,
                        dayTitle = day.title
                    )
                }
            }
            repository.saveRoutine(
                PredefinedRoutines.toSavedRoutine(template).copy(
                    exercisesJson = RoutineParser.serializeExercises(resolved)
                )
            )
        }
    }

    /**
     * Saves the changes made in the "Create New Routine" screen while editing an existing routine.
     * Reusing the same id makes Room replace the row instead of creating a second routine.
     */
    fun saveEditedRoutine(routine: SavedRoutine) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRoutine(routine)
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

    fun startRoutineWorkout(routine: SavedRoutine, targetDayNumber: Int? = null) {
        // Dismiss any lingering quick workout tray so only the dedicated routine workout runs
        _activeSession.value = null
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()

        val allEx = allExercises.value
        val days = RoutineParser.parseDays(routine.exercisesJson, allEx)

        val targetDay = if (targetDayNumber != null && days.isNotEmpty()) {
            days.firstOrNull { it.dayNumber == targetDayNumber } ?: days.first()
        } else if (days.isNotEmpty()) {
            days.first()
        } else {
            null
        }

        val routineItems = targetDay?.exercises ?: RoutineParser.parseExercises(routine.exercisesJson, allEx)
        val dayNumber = targetDay?.dayNumber ?: 1
        val dayTitle = targetDay?.dayTitle ?: "Day 1"
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
            dayNumber = dayNumber,
            dayTitle = dayTitle,
            dayNote = dayNote
        )
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
            val setsToSave = current.sets
                .filter { it.isCompleted }
                .mapIndexed { index, set -> set.copy(timestamp = savedAt + index) }

            // A workout without any logged set is not a workout, so nothing is persisted.
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
                    notes = notes.trim()
                )
                val sessionId = repository.insertSession(session)
                repository.insertSets(setsToSave.map { it.copy(sessionId = sessionId) })
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
                            timestamp = entity.timestamp
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
            val historyPairs = allDbMessages.map { msg ->
                (if (msg.sender == "USER") "user" else "model") to msg.text
            }

            val customKey = geminiApiKey.value.ifBlank { userProfile.value.customApiKey }
            val result = GeminiClient.generateChat(
                conversationHistory = historyPairs,
                userCustomKey = customKey,
                customModel = geminiModelName.value.ifBlank { null },
                athleteContext = athleteContextPromptBlock(
                    includeBenchmarks = true,
                    purpose = "tailored coaching answers"
                )
            )

            result.fold(
                onSuccess = { replyText ->
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            sessionId = session.id,
                            sender = "AI",
                            text = replyText,
                            timestamp = System.currentTimeMillis()
                        )
                    )
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

                append("CRITICAL DATABASE ENFORCEMENT:\n")
                append("You MUST ONLY choose exercises that exist in the complete local database list below. Do not invent any new exercise names.\n")
                append("The list includes the IronPulse library, exercises previously generated by the AI and custom exercises created by the user: if one of them already matches what you need, you MUST use that exact name instead of creating a new exercise.\n")
                append("COMPLETE LOCAL DATABASE CATALOG (ALL EXERCISES IN THE APP):\n$candidates\n\n")

                append("OUTPUT FORMAT: RESPOND WITH ONE SINGLE JSON OBJECT AND NOTHING ELSE.\n")
                append("Do NOT write any greeting, intro, explanation, markdown heading, code fence or closing remark before or after it.\n")
                append("The \"exercises\" array is the ONLY place an exercise name may appear: no description, note or full sentence may ever be placed in it.\n\n")

                append("SCHEMA:\n")
                append("{\n")
                append("  \"routine_name\": \"<short routine title>\",\n")
                append("  \"routine_goal\": \"<short goal>\",\n")
                append("  \"days_per_week\": ${routineDaysPerWeek.value},\n")
                append("  \"day_notes\": [ { \"day\": 1, \"note\": \"<one short motivating sentence>\" } ],\n")
                append("  \"exercises\": [\n")
                append("    {\n")
                append("      \"name\": \"<EXACT exercise name copied from the catalog>\",\n")
                append("      \"sets\": 4,\n")
                append("      \"reps\": 8,\n")
                append("      \"weight\": 0.0,\n")
                append("      \"holdSeconds\": 0,\n")
                append("      \"prescription\": \"4 sets x 8 reps\",\n")
                append("      \"day\": 1,\n")
                append("      \"dayTitle\": \"Day 1: Upper Body Push\"\n")
                append("    }\n")
                append("  ]\n")
                append("}\n\n")

                append("FIELD RULES:\n")
                append("- \"name\" MUST be copied character for character from the catalog. Never write a sentence there.\n")
                append("- \"holdSeconds\": use a value > 0 ONLY for isometric holds (plank, hollow hold). For a rep based movement such as an explosive pull-up it MUST be 0 and \"reps\" MUST carry the rep count.\n")
                append("- \"reps\": use 0 ONLY when \"holdSeconds\" > 0. Never leave both at 0.\n")
                append("- \"prescription\" MUST be exactly '<sets> sets x <reps> reps' and NOTHING else (e.g. '4 sets x 8 reps'). For an isometric hold use '<sets> sets x <seconds>s' (e.g. '3 sets x 30s').\n")
                append("- NEVER put a load in \"prescription\" (no '80kg', no '@ 80kg', no 'bodyweight'). The load is set separately by the athlete.\n")
                append("- \"weight\" is the external load in kg for the movement. Use 0.0 for every bodyweight movement and NEVER the athlete's body weight there. Only put a number for a movement that is genuinely loaded with weight.\n")
                append("- NEVER put equipment ('Barbell', 'Dumbbells', 'with Barbell', ...) in \"prescription\".\n")
                append("- NEVER add any commentary, notes, tempo, rest or technique hints to \"prescription\".\n")
                append("- Order the exercises day by day, exactly as the athlete should perform them.\n")
            }

            val customKey = geminiApiKey.value.ifBlank { userProfile.value.customApiKey }
            val result = GeminiClient.generateContent(
                prompt = prompt,
                userCustomKey = customKey,
                customModel = geminiModelName.value.ifBlank { null }
            )
            result.fold(
                onSuccess = { rawResponse ->
                    // The model may still wrap the object in prose: parse it, never store it raw
                    val routineItems = parseAiRoutineItems(rawResponse, knownExercises)
                    if (routineItems.isEmpty()) {
                        generatedRoutine.value = ""
                        showAiError(
                            "Routine Generation Failed",
                            "The AI did not return any usable exercise. Please generate the routine again."
                        )
                    } else {
                        generatedRoutine.value = RoutineParser.serializeExercises(routineItems)
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
     * Turns an AI routine answer into [RoutineExerciseItem]s.
     *
     * Shared by every AI routine flow: it tolerates prose around the JSON, refuses names that are
     * clearly sentences (the "first exercise is a description" bug) and only keeps real exercises.
     */
    private fun parseAiRoutineItems(
        rawResponse: String,
        knownExercises: List<Exercise>
    ): List<RoutineExerciseItem> {
        val cleaned = RoutineParser.extractJson(rawResponse)
        val root = try {
            if (cleaned.trimStart().startsWith("[")) JSONObject("{\"exercises\": $cleaned}")
            else JSONObject(cleaned)
        } catch (_: Exception) {
            return emptyList()
        }

        val array = root.optJSONArray("exercises") ?: return emptyList()
        val exByName = knownExercises.associateBy { it.name.trim().lowercase() }
        val notesByDay = mutableMapOf<Int, String>()
        root.optJSONArray("day_notes")?.let { notes ->
            for (i in 0 until notes.length()) {
                val noteObj = notes.optJSONObject(i) ?: continue
                val day = noteObj.optInt("day", 0)
                val note = RoutineParser.cleanNoteText(noteObj.optString("note", ""))
                if (day > 0 && note.isNotBlank()) notesByDay[day] = note
            }
        }

        val items = mutableListOf<RoutineExerciseItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawName = obj.optString("name").trim()
            // A greeting / description leaked into the exercises array is dropped, never logged
            if (RoutineParser.looksLikeProseNotExercise(rawName)) continue

            val sets = obj.optInt("sets", 3).coerceIn(1, 10)
            val hold = obj.optInt("holdSeconds", 0).coerceAtLeast(0)
            var reps = obj.optInt("reps", 8).coerceAtLeast(0)
            val weight = obj.optDouble("weight", 0.0).coerceAtLeast(0.0)
            val dayNum = obj.optInt("day", 1).coerceAtLeast(1)
            val dayTitle = obj.optString("dayTitle", "").ifBlank { "Day $dayNum" }
            // A rep based movement must never end up as a hold only set
            if (reps <= 0 && hold <= 0) reps = 8

            val matched = exByName[rawName.lowercase()]
                ?: RoutineParser.findMatchingExercise(rawName, exByName.values)
            val prescription = RoutineParser.shortPrescription(
                obj.optString("prescription", ""),
                matched?.equipment.orEmpty()
            ).ifBlank {
                when {
                    hold > 0 && reps > 0 -> "$sets sets x $reps reps / ${hold}s"
                    hold > 0 -> "$sets sets x ${hold}s"
                    else -> "$sets sets x $reps reps"
                }
            }

            items.add(
                RoutineExerciseItem(
                    exerciseId = matched?.id ?: 0L,
                    exerciseName = matched?.name ?: rawName,
                    exerciseType = matched?.exerciseType ?: ExerciseType.STANDARD_WEIGHT,
                    category = matched?.category.orEmpty(),
                    equipment = matched?.equipment.orEmpty(),
                    targetSets = sets,
                    targetReps = reps,
                    targetWeight = weight,
                    targetHoldSeconds = hold,
                    prescription = prescription,
                    dayNumber = dayNum,
                    dayTitle = dayTitle,
                    dayNote = notesByDay[dayNum].orEmpty()
                )
            )
        }
        return items
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
    private fun fallbackTargetForCategory(category: String): String = when (category) {
        "Chest" -> "Pectorals"
        "Back" -> "Lats"
        "Shoulders" -> "Deltoids"
        "Arms" -> "Biceps"
        "Core" -> "Abs"
        "Legs" -> "Quadriceps"
        else -> "Quadriceps"
    }

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
     * and this would flag the row as AI generated — on a release where the dataset happens to have
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
                     * DO NOT replace "Goblet Squat" with "Band Resisted Squat" or anything else. "Goblet Squat" IS in the catalog under [Legs] — use "Goblet Squat"!
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
            """.trimIndent()

            val customKey = geminiApiKey.value.ifBlank { userProfile.value.customApiKey }
            val geminiResult = GeminiClient.generateContent(
                prompt = prompt,
                userCustomKey = customKey,
                customModel = geminiModelName.value.ifBlank { null }
            )

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

                    // Process routine items
                    val exercisesJson = json.optJSONArray("exercises")
                    val routineItems = mutableListOf<RoutineExerciseItem>()

                    if (exercisesJson != null) {
                        for (i in 0 until exercisesJson.length()) {
                            val obj = exercisesJson.optJSONObject(i) ?: continue
                            val name = obj.optString("name").trim()
                            // A greeting / description that leaked into the exercises array is never logged
                            if (RoutineParser.looksLikeProseNotExercise(name)) continue
                            val sets = obj.optInt("sets", 3)
                            val reps = obj.optInt("reps", 8)
                            val weight = obj.optDouble("weight", 0.0)
                            val hold = obj.optInt("holdSeconds", 0)
                            val rawPrescription = obj.optString("prescription", "")
                            val dayNum = obj.optInt("day", 1)
                            val dayTitle = obj.optString("dayTitle", "Day $dayNum")

                            // Find or create exercise
                            //
                            // The source text is the authority on which movement this is. The AI
                            // normalises aggressively and often drops the modifier: a "Decline Push-Up"
                            // in the pasted program routinely comes back as a plain "Push-Up", and the
                            // exact-name lookup below then succeeds, so the decline is lost for good.
                            // Asking the source first costs one match per line and keeps the movement
                            // the athlete actually wrote.
                            val fromSource = RoutineParser.resolveAgainstSource(
                                aiName = name,
                                sourceText = trimmedInput,
                                candidates = existingByName.values
                            )
                            var matched = fromSource
                                ?: existingByName[name.lowercase()]
                                ?: RoutineParser.findMatchingExercise(name, existingByName.values)
                            if (matched == null) {
                                val fallbackEx = Exercise(
                                    name = name,
                                    category = "Calisthenics",
                                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                                    equipment = "Bodyweight",
                                    description = "Imported custom exercise"
                                )
                                val id = insertCustomExercise(fallbackEx)
                                matched = fallbackEx.copy(id = id)
                                newlyCreatedExercises.add(matched)
                                existingByName[name.lowercase()] = matched
                            }

                            val cleanPrescription = RoutineParser.shortPrescription(rawPrescription, matched.equipment).ifBlank {
                                if (hold > 0) "$sets sets x ${hold}s"
                                else "$sets sets x $reps reps"
                            }

                            routineItems.add(
                                RoutineExerciseItem(
                                    exerciseId = matched.id,
                                    exerciseName = matched.name,
                                    exerciseType = matched.exerciseType,
                                    category = matched.category,
                                    equipment = matched.equipment,
                                    targetSets = sets,
                                    targetReps = reps,
                                    targetWeight = weight,
                                    targetHoldSeconds = hold,
                                    prescription = cleanPrescription,
                                    dayNumber = dayNum,
                                    dayTitle = dayTitle
                                )
                            )
                        }
                    }

                    if (routineItems.isEmpty()) {
                        _unstructuredParseState.value = UnstructuredParseState.Error(
                            "Could not extract any exercises from the provided text. Please verify the exercises and try again."
                        )
                        return@launch
                    }

                    val finalTitle = customName.trim().ifBlank {
                        json.optString("routine_name", "Imported Routine")
                    }
                    val goal = json.optString("routine_goal", "Custom Workout")
                    val daysCount = routineItems.map { it.dayNumber }.distinct().size.coerceAtLeast(1)

                    val savedRoutine = SavedRoutine(
                        name = finalTitle,
                        description = "AI Parsed Program • ${routineItems.size} exercises • $daysCount training day${if (daysCount > 1) "s" else ""}",
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
                   - Organize the routine into clear training days (e.g. Day 1, Day 2...) matching the user's requested split or schedule (default to 3 days if unspecified).
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
                     - "day": Int (training day number starting at 1: 1 for Day 1, 2 for Day 2)
                     - "dayTitle": String (e.g. "Day 1: Upper Body Push" or "Day 1: Full Body A")

                JSON FORMAT REQUIREMENT:
                Respond ONLY with a valid JSON object:
                {
                  "is_valid_request": true,
                  "error_reason": "",
                  "routine_name": "Suggested Routine Name",
                  "routine_goal": "Goal description",
                  "days_per_week": 3,
                  "new_exercises_to_create": [],
                  "exercises": [
                    {
                      "name": "Barbell Bench Press",
                      "sets": 4,
                      "reps": 8,
                      "weight": 0.0,
                      "holdSeconds": 0,
                      "prescription": "4 sets x 8 reps",
                      "day": 1,
                      "dayTitle": "Day 1: Push & Core"
                    }
                  ]
                }
            """.trimIndent()

            val customKey = geminiApiKey.value.ifBlank { userProfile.value.customApiKey }
            val geminiResult = GeminiClient.generateContent(
                prompt = prompt,
                userCustomKey = customKey,
                customModel = geminiModelName.value.ifBlank { null }
            )

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

                    // Process routine items
                    val exercisesJson = json.optJSONArray("exercises")
                    val routineItems = mutableListOf<RoutineExerciseItem>()

                    if (exercisesJson != null) {
                        for (i in 0 until exercisesJson.length()) {
                            val obj = exercisesJson.optJSONObject(i) ?: continue
                            val name = obj.optString("name").trim()
                            // A greeting / description that leaked into the exercises array is never logged
                            if (RoutineParser.looksLikeProseNotExercise(name)) continue
                            val sets = obj.optInt("sets", 3)
                            val reps = obj.optInt("reps", 8)
                            val weight = obj.optDouble("weight", 0.0)
                            val hold = obj.optInt("holdSeconds", 0)
                            val rawPrescription = obj.optString("prescription", "")
                            val dayNum = obj.optInt("day", 1)
                            val dayTitle = obj.optString("dayTitle", "Day $dayNum")

                            // Match or create fallback
                            var matched = existingByName[name.lowercase()] ?: RoutineParser.findMatchingExercise(name, existingByName.values)
                            if (matched == null) {
                                val fallbackEx = Exercise(
                                    name = name,
                                    category = "Calisthenics",
                                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                                    equipment = "Bodyweight",
                                    description = "Imported custom exercise"
                                )
                                val id = insertCustomExercise(fallbackEx)
                                matched = fallbackEx.copy(id = id)
                                newlyCreatedExercises.add(matched)
                                existingByName[name.lowercase()] = matched
                            }

                            val cleanPrescription = RoutineParser.shortPrescription(rawPrescription, matched.equipment).ifBlank {
                                if (hold > 0) "$sets sets x ${hold}s"
                                else "$sets sets x $reps reps"
                            }

                            routineItems.add(
                                RoutineExerciseItem(
                                    exerciseId = matched.id,
                                    exerciseName = matched.name,
                                    exerciseType = matched.exerciseType,
                                    category = matched.category,
                                    equipment = matched.equipment,
                                    targetSets = sets,
                                    targetReps = reps,
                                    targetWeight = weight,
                                    targetHoldSeconds = hold,
                                    prescription = cleanPrescription,
                                    dayNumber = dayNum,
                                    dayTitle = dayTitle
                                )
                            )
                        }
                    }

                    if (routineItems.isEmpty()) {
                        _customRoutinePromptState.value = CustomRoutinePromptState.Error(
                            "Could not generate exercises for the given prompt. Please specify your workout goals more clearly."
                        )
                        return@launch
                    }

                    val finalTitle = customName.trim().ifBlank {
                        json.optString("routine_name", "AI Custom Routine")
                    }
                    val goal = json.optString("routine_goal", "Custom Workout Goal")
                    val daysCount = routineItems.map { it.dayNumber }.distinct().size.coerceAtLeast(1)

                    val generatedRoutine = SavedRoutine(
                        name = finalTitle,
                        description = "AI Prompt Routine • ${routineItems.size} exercises • $daysCount training day${if (daysCount > 1) "s" else ""}",
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
    companion object {
        /** A monthly summary needs more than this many logged sets to read a trend from. */
        const val MIN_SETS_FOR_MONTHLY_SUMMARY = 2

        /** Longest athlete name the profile accepts. */
        const val MAX_NAME_LENGTH = 15

        /** Longest name a workout in progress can be given. */
        const val MAX_WORKOUT_NAME_LENGTH = 60

        /**
         * How often the workout in progress is written to disk.
         *
         * Ten seconds is short enough that a closed app costs at most ten seconds of work, and long
         * enough that a phone in a pocket is not writing to storage the whole time.
         */
        const val AUTO_SAVE_INTERVAL_MS = 10_000L

        /** Timeline note used for a body weight the athlete entered through "Log Today". */
        const val WEIGH_IN_NOTE_MORNING = "Morning weigh-in"

        /**
         * Timeline note used when the body weight changed through the "Edit Athlete Profile" popup.
         * Deliberately different so it is never mistaken for a morning weigh-in.
         */
        const val WEIGH_IN_NOTE_PROFILE_UPDATE = "Profile update"

        private const val MISSING_API_KEY_MESSAGE =
            "No Gemini API key configured. Open the Profile tab, tap the \"?\" next to the AI coach API key " +
                    "to learn how to get a free key, then paste it there and save it."

        private val DATE_FORMAT_DAY = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
        private val DATE_FORMAT_CHART = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("MMM d", Locale.getDefault())
        }
    }
}
