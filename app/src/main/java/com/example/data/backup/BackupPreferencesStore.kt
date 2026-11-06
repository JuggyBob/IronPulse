/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.data.ai.AiConfigurationRepository
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiToolMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.theme.AccentColors
import com.example.ui.theme.ThemeMode
import com.example.data.preferences.userDataStore
import com.example.data.seed.LibraryEditTracker
import com.example.util.RestTimerAlertCoordinator
import com.example.util.WidgetPhotoStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.io.File

/**
 * Reads and writes the exportable half of DataStore and the two small SharedPreferences stores.
 *
 * Every key is named explicitly. There is deliberately no "copy the whole preferences file" path:
 * the same DataStore holds API keys and the profile-picture path, and a generic dump would leak
 * credentials into the archive.
 */
open class BackupPreferencesStore(private val context: Context) {

    data class Snapshot(
        val dataStoreProfile: DataStoreProfileV1?,
        val preferences: PreferencesSectionV1,
        val dashboard: DashboardSectionV1,
        val widgetPhotoPath: String
    )

    open suspend fun readSnapshot(): Snapshot {
        val prefs = context.userDataStore.data.first()
        return Snapshot(
            dataStoreProfile = readProfile(prefs),
            preferences = readPreferences(prefs),
            dashboard = readDashboard(prefs),
            widgetPhotoPath = prefs[UserPreferencesRepository.HOME_WIDGET_PHOTO_PATH].orEmpty()
        )
    }

    suspend fun readWidgetPhoto(path: String): WidgetPhotoData? {
        if (path.isBlank()) return null
        val file = File(path)
        val widgetDirectory = runCatching {
            File(context.filesDir, "home_widget_media").canonicalFile
        }.getOrNull()
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
        if (widgetDirectory == null || canonicalFile == null || canonicalFile.parentFile != widgetDirectory) {
            throw BackupException(
                "The saved Home photo does not point at the app's widget photo storage, so the export was stopped.",
                BackupErrorCode.INVALID_DATA
            )
        }
        if (!file.isFile || file.length() <= 0L) {
            throw BackupException(
                "The saved Home photo is missing from this device, so the export was stopped. Choose the photo again or remove it, then export.",
                BackupErrorCode.IO_ERROR
            )
        }
        if (file.length() > MAX_WIDGET_PHOTO_BYTES) {
            throw BackupException(
                "The saved Home photo is too large to include in a backup.",
                BackupErrorCode.TOO_LARGE
            )
        }
        val mediaType = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> throw BackupException(
                "The saved Home photo has an unsupported format.",
                BackupErrorCode.INVALID_DATA
            )
        }
        return WidgetPhotoData(file.readBytes(), mediaType, file.extension.lowercase().let { if (it == "jpeg") "jpg" else it })
    }

    fun readEditedLibraryExerciseIds(): Set<Long> = LibraryEditTracker.editedIds(context)

    fun writeEditedLibraryExerciseIds(ids: Set<Long>) {
        val value = ids.filter { it > 0L }.sorted().joinToString(",")
        val ok = context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LIBRARY_EDITED_IDS_KEY, value)
            .commit()
        if (!ok) {
            throw BackupException(
                "The exercise library edit record could not be updated.",
                BackupErrorCode.IO_ERROR
            )
        }
    }

    /**
     * Replaces every exportable preference in one DataStore edit, then mirrors the two rest-alert
     * switches to the SharedPreferences copy used by the alarm receiver.
     *
     * Keys that are not in the allowlist (API keys, profile avatar path, cached release data,
     * update-prompt suppression) are never removed or written here.
     */
    open suspend fun apply(
        profile: DataStoreProfileV1?,
        preferences: PreferencesSectionV1,
        dashboard: DashboardSectionV1,
        widgetPhotoPath: String?
    ) {
        context.userDataStore.edit { prefs ->
            for (key in EXPORTABLE_KEYS) prefs.remove(key)
            for (provider in AiProviderId.entries) {
                prefs.remove(AiConfigurationRepository.modelKey(provider))
                prefs.remove(AiConfigurationRepository.toolModeKey(provider))
            }

            if (profile != null) {
                profile.isSaved?.let { prefs[UserPreferencesRepository.PROFILE_IS_SAVED] = it }
                profile.name?.let { prefs[UserPreferencesRepository.PROFILE_NAME] = it }
                profile.heightCm?.let { prefs[UserPreferencesRepository.PROFILE_HEIGHT_CM] = it }
                profile.currentWeightKg?.let { prefs[UserPreferencesRepository.PROFILE_CURRENT_WEIGHT_KG] = it }
                profile.targetWeightKg?.let { prefs[UserPreferencesRepository.PROFILE_TARGET_WEIGHT_KG] = it }
                profile.age?.let { prefs[UserPreferencesRepository.PROFILE_AGE] = it }
                profile.preferredUnit?.let { prefs[UserPreferencesRepository.PROFILE_PREFERRED_UNIT] = it }
                profile.fitnessLevel?.let { prefs[UserPreferencesRepository.PROFILE_FITNESS_LEVEL] = it }
            }

            preferences.unit?.let { prefs[UserPreferencesRepository.WEIGHT_UNIT] = it }
            preferences.restTimerVibrationEnabled?.let { prefs[UserPreferencesRepository.REST_TIMER_VIBRATION_ENABLED] = it }
            preferences.restTimerSoundEnabled?.let { prefs[UserPreferencesRepository.REST_TIMER_SOUND_ENABLED] = it }
            preferences.shareAthleteDataWithAi?.let { prefs[UserPreferencesRepository.SHARE_ATHLETE_DATA_WITH_AI] = it }
            preferences.autoplayExerciseDemos?.let { prefs[UserPreferencesRepository.AUTOPLAY_EXERCISE_DEMOS] = it }
            preferences.liquidGlassEnabled?.let { prefs[UserPreferencesRepository.LIQUID_GLASS_ENABLED] = it }
            preferences.fullscreenEnabled?.let { prefs[UserPreferencesRepository.FULLSCREEN_MODE] = it }
            preferences.themeMode?.let { prefs[UserPreferencesRepository.THEME_MODE] = it }
            preferences.accentColor?.let { raw ->
                AccentColors.normalizeHex(raw)?.let {
                    prefs[UserPreferencesRepository.ACCENT_COLOR] = it
                }
            }
            preferences.onboardingCompleted?.let { prefs[UserPreferencesRepository.ONBOARDING_COMPLETED] = it }
            preferences.activeWorkoutDraft?.let { prefs[UserPreferencesRepository.ACTIVE_WORKOUT_DRAFT] = it }
            preferences.weekStartsOnMonday?.let { prefs[UserPreferencesRepository.WEEK_STARTS_ON_MONDAY] = it }
            preferences.autoSaveIntervalSeconds?.let { prefs[UserPreferencesRepository.AUTO_SAVE_INTERVAL_SECONDS] = it }
            preferences.autoCheckUpdatesOnStart?.let { prefs[UserPreferencesRepository.AUTO_CHECK_UPDATES_ON_START] = it }
            preferences.aiWorkoutDataAccess?.let { prefs[UserPreferencesRepository.AI_WORKOUT_DATA_ACCESS] = it }
            preferences.postWorkoutAiSummaries?.let { prefs[UserPreferencesRepository.POST_WORKOUT_AI_SUMMARIES] = it }
            preferences.activeRoutineId?.let { prefs[UserPreferencesRepository.ACTIVE_ROUTINE_ID] = it }
            preferences.restTimers?.let { timers ->
                prefs[UserPreferencesRepository.SAVED_REST_TIMERS_JSON] = encodeRestTimers(timers)
            }
            preferences.benchmark?.let { benchmark ->
                benchmark.pushUps?.let { prefs[UserPreferencesRepository.PR_PUSH_UPS] = it }
                benchmark.pushUpsAddedKg?.let { prefs[UserPreferencesRepository.PR_PUSH_UPS_ADDED] = it }
                benchmark.pullUps?.let { prefs[UserPreferencesRepository.PR_PULL_UPS] = it }
                benchmark.pullUpsAddedKg?.let { prefs[UserPreferencesRepository.PR_PULL_UPS_ADDED] = it }
                benchmark.bulgarianSplitSquat?.let { prefs[UserPreferencesRepository.PR_BULGARIAN_SPLIT_SQUAT] = it }
                benchmark.bulgarianSplitSquatAddedKg?.let { prefs[UserPreferencesRepository.PR_BULGARIAN_SPLIT_SQUAT_ADDED] = it }
            }
            preferences.widgetThemes?.let { themes ->
                writeWidgetTheme(
                    prefs,
                    UserPreferencesRepository.ANDROID_WIDGET_THEME_STREAK,
                    themes.streak
                )
                writeWidgetTheme(
                    prefs,
                    UserPreferencesRepository.ANDROID_WIDGET_THEME_WEEKLY,
                    themes.weekly
                )
                writeWidgetTheme(
                    prefs,
                    UserPreferencesRepository.ANDROID_WIDGET_THEME_PROFILE,
                    themes.profile
                )
            }
            preferences.ai?.let { ai ->
                ai.activeProvider?.let { prefs[AiConfigurationRepository.ACTIVE_PROVIDER] = it }
                val safeBaseUrl = ai.customBaseUrl?.takeIf { !BackupValidator.containsEmbeddedCredentials(it) }
                safeBaseUrl?.let { prefs[AiConfigurationRepository.CUSTOM_BASE_URL] = it }
                if (safeBaseUrl != null) {
                    ai.customAcknowledgedEndpoint?.let {
                        prefs[AiConfigurationRepository.CUSTOM_ACKNOWLEDGED_ENDPOINT] = it
                    }
                }
                for ((providerKey, model) in ai.modelsByProvider) {
                    val provider = AiProviderId.entries.firstOrNull { it.storageKey == providerKey } ?: continue
                    if (model.isNotBlank()) prefs[AiConfigurationRepository.modelKey(provider)] = model
                }
                for ((providerKey, mode) in ai.toolModesByProvider) {
                    val provider = AiProviderId.entries.firstOrNull { it.storageKey == providerKey } ?: continue
                    if (AiToolMode.entries.any { it.name == mode }) {
                        prefs[AiConfigurationRepository.toolModeKey(provider)] = mode
                    }
                }
                val googleModel = ai.modelsByProvider[AiProviderId.GOOGLE.storageKey]
                if (googleModel.isNullOrBlank()) {
                    prefs.remove(UserPreferencesRepository.GEMINI_MODEL_NAME)
                } else {
                    prefs[UserPreferencesRepository.GEMINI_MODEL_NAME] = googleModel
                }
            }

            dashboard.widgetOrder?.let { prefs[UserPreferencesRepository.HOME_WIDGET_ORDER] = it.joinToString(",") }
            dashboard.hiddenWidgetIds?.let {
                prefs[UserPreferencesRepository.HOME_WIDGET_HIDDEN] = it.sorted().joinToString(",")
            }
            dashboard.weeklyGoal?.let { prefs[UserPreferencesRepository.HOME_WIDGET_WEEKLY_GOAL] = it }
            if (widgetPhotoPath.isNullOrBlank()) {
                prefs.remove(UserPreferencesRepository.HOME_WIDGET_PHOTO_PATH)
            } else {
                prefs[UserPreferencesRepository.HOME_WIDGET_PHOTO_PATH] = widgetPhotoPath
            }
        }

        RestTimerAlertCoordinator.setAlertPreferences(
            context,
            preferences.restTimerVibrationEnabled ?: true,
            preferences.restTimerSoundEnabled ?: true
        )
    }

    suspend fun stageWidgetPhoto(photo: WidgetPhotoData): String? =
        WidgetPhotoStore.writeEncoded(context, photo.bytes, photo.extension)

    fun cleanWidgetPhotos(referencedPath: String?) {
        WidgetPhotoStore.deleteUnreferenced(
            context,
            setOfNotNull(referencedPath?.takeIf { it.isNotBlank() })
        )
    }

    private fun readProfile(prefs: Preferences): DataStoreProfileV1? {
        val isSaved = prefs[UserPreferencesRepository.PROFILE_IS_SAVED]
        val name = prefs[UserPreferencesRepository.PROFILE_NAME]
        val height = prefs[UserPreferencesRepository.PROFILE_HEIGHT_CM]
        val currentWeight = prefs[UserPreferencesRepository.PROFILE_CURRENT_WEIGHT_KG]
        val targetWeight = prefs[UserPreferencesRepository.PROFILE_TARGET_WEIGHT_KG]
        val age = prefs[UserPreferencesRepository.PROFILE_AGE]
        val unit = prefs[UserPreferencesRepository.PROFILE_PREFERRED_UNIT]
        val fitness = prefs[UserPreferencesRepository.PROFILE_FITNESS_LEVEL]
        if (isSaved == null && name == null && height == null && currentWeight == null &&
            targetWeight == null && age == null && unit == null && fitness == null
        ) {
            return null
        }
        return DataStoreProfileV1(
            isSaved = isSaved,
            name = name,
            heightCm = height,
            currentWeightKg = currentWeight,
            targetWeightKg = targetWeight,
            age = age,
            preferredUnit = unit,
            fitnessLevel = fitness
        )
    }

    private fun readPreferences(prefs: Preferences): PreferencesSectionV1 {
        val timers = prefs[UserPreferencesRepository.SAVED_REST_TIMERS_JSON]?.let { raw ->
            if (raw.isBlank()) null else parseRestTimers(raw)
        }
        val benchmark = BenchmarkPreferencesV1(
            pushUps = prefs[UserPreferencesRepository.PR_PUSH_UPS],
            pushUpsAddedKg = prefs[UserPreferencesRepository.PR_PUSH_UPS_ADDED],
            pullUps = prefs[UserPreferencesRepository.PR_PULL_UPS],
            pullUpsAddedKg = prefs[UserPreferencesRepository.PR_PULL_UPS_ADDED],
            bulgarianSplitSquat = prefs[UserPreferencesRepository.PR_BULGARIAN_SPLIT_SQUAT],
            bulgarianSplitSquatAddedKg = prefs[UserPreferencesRepository.PR_BULGARIAN_SPLIT_SQUAT_ADDED]
        ).takeIf {
            it.pushUps != null || it.pushUpsAddedKg != null || it.pullUps != null ||
                it.pullUpsAddedKg != null || it.bulgarianSplitSquat != null ||
                it.bulgarianSplitSquatAddedKg != null
        }

        val models = LinkedHashMap<String, String>()
        val toolModes = LinkedHashMap<String, String>()
        for (provider in AiProviderId.entries) {
            val model = prefs[AiConfigurationRepository.modelKey(provider)] ?: if (provider == AiProviderId.GOOGLE) {
                prefs[UserPreferencesRepository.GEMINI_MODEL_NAME]
            } else {
                null
            }
            if (!model.isNullOrBlank()) models[provider.storageKey] = model
            val mode = prefs[AiConfigurationRepository.toolModeKey(provider)]
            if (!mode.isNullOrBlank()) toolModes[provider.storageKey] = mode
        }
        val activeProviderRaw = prefs[AiConfigurationRepository.ACTIVE_PROVIDER]
        val activeProvider = activeProviderRaw?.takeIf { it.isNotBlank() }?.let { raw ->
            AiProviderId.entries.firstOrNull { it.storageKey == raw }?.storageKey
                ?: throw BackupException(
                    "The saved AI provider setting is not recognized, so the export was stopped.",
                    BackupErrorCode.INVALID_DATA
                )
        }
        val customBaseUrl = prefs[AiConfigurationRepository.CUSTOM_BASE_URL]
            ?.takeIf { it.isNotBlank() && !BackupValidator.containsEmbeddedCredentials(it) }
        val acknowledged = if (customBaseUrl == null) null else {
            prefs[AiConfigurationRepository.CUSTOM_ACKNOWLEDGED_ENDPOINT]?.takeIf { it.isNotBlank() }
        }
        val ai = AiPreferencesV1(
            activeProvider = activeProvider,
            modelsByProvider = models,
            toolModesByProvider = toolModes,
            customBaseUrl = customBaseUrl,
            customAcknowledgedEndpoint = acknowledged
        ).takeIf {
            it.activeProvider != null || it.modelsByProvider.isNotEmpty() ||
                it.toolModesByProvider.isNotEmpty() || it.customBaseUrl != null
        }

        return PreferencesSectionV1(
            unit = prefs[UserPreferencesRepository.WEIGHT_UNIT],
            restTimerVibrationEnabled = prefs[UserPreferencesRepository.REST_TIMER_VIBRATION_ENABLED],
            restTimerSoundEnabled = prefs[UserPreferencesRepository.REST_TIMER_SOUND_ENABLED],
            shareAthleteDataWithAi = prefs[UserPreferencesRepository.SHARE_ATHLETE_DATA_WITH_AI],
            autoplayExerciseDemos = prefs[UserPreferencesRepository.AUTOPLAY_EXERCISE_DEMOS],
            liquidGlassEnabled = prefs[UserPreferencesRepository.LIQUID_GLASS_ENABLED],
            fullscreenEnabled = prefs[UserPreferencesRepository.FULLSCREEN_MODE],
            themeMode = prefs[UserPreferencesRepository.THEME_MODE],
            accentColor = AccentColors.normalizeHex(
                prefs[UserPreferencesRepository.ACCENT_COLOR]
            ),
            onboardingCompleted = prefs[UserPreferencesRepository.ONBOARDING_COMPLETED],
            activeWorkoutDraft = prefs[UserPreferencesRepository.ACTIVE_WORKOUT_DRAFT]?.takeIf { it.isNotBlank() },
            weekStartsOnMonday = prefs[UserPreferencesRepository.WEEK_STARTS_ON_MONDAY],
            autoSaveIntervalSeconds = prefs[UserPreferencesRepository.AUTO_SAVE_INTERVAL_SECONDS],
            autoCheckUpdatesOnStart = prefs[UserPreferencesRepository.AUTO_CHECK_UPDATES_ON_START],
            aiWorkoutDataAccess = prefs[UserPreferencesRepository.AI_WORKOUT_DATA_ACCESS],
            postWorkoutAiSummaries = prefs[UserPreferencesRepository.POST_WORKOUT_AI_SUMMARIES],
            activeRoutineId = prefs[UserPreferencesRepository.ACTIVE_ROUTINE_ID],
            restTimers = timers,
            benchmark = benchmark,
            ai = ai,
            widgetThemes = WidgetThemesV1(
                streak = readWidgetTheme(prefs[UserPreferencesRepository.ANDROID_WIDGET_THEME_STREAK]),
                weekly = readWidgetTheme(prefs[UserPreferencesRepository.ANDROID_WIDGET_THEME_WEEKLY]),
                profile = readWidgetTheme(prefs[UserPreferencesRepository.ANDROID_WIDGET_THEME_PROFILE])
            )
        )
    }

    private fun readWidgetTheme(raw: String?): String {
        val key = raw?.takeIf { value ->
            ThemeMode.values().any { it.storageKey == value }
        }
        return key ?: ThemeMode.IRONPULSE.storageKey
    }

    private fun writeWidgetTheme(
        prefs: MutablePreferences,
        key: Preferences.Key<String>,
        raw: String?
    ) {
        val mode = ThemeMode.values().firstOrNull { it.storageKey == raw } ?: ThemeMode.IRONPULSE
        prefs[key] = mode.storageKey
    }

    private fun readDashboard(prefs: Preferences): DashboardSectionV1 {
        val order = prefs[UserPreferencesRepository.HOME_WIDGET_ORDER]?.let(::parseWidgetIds)
        val hidden = prefs[UserPreferencesRepository.HOME_WIDGET_HIDDEN]?.let(::parseWidgetIds)
        return DashboardSectionV1(
            widgetOrder = order,
            hiddenWidgetIds = hidden,
            weeklyGoal = prefs[UserPreferencesRepository.HOME_WIDGET_WEEKLY_GOAL],
            photoPresent = !prefs[UserPreferencesRepository.HOME_WIDGET_PHOTO_PATH].isNullOrBlank()
        )
    }

    private fun parseWidgetIds(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun parseRestTimers(raw: String): List<RestTimerV1> {
        try {
            val array = JSONArray(raw)
            val result = ArrayList<RestTimerV1>(array.length())
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                result += RestTimerV1(
                    id = obj.optString("id").ifBlank { throw IllegalArgumentException("missing id") },
                    name = obj.optString("name"),
                    totalSeconds = obj.optInt("totalSeconds", 0)
                )
            }
            return result
        } catch (e: Exception) {
            throw BackupException(
                "The saved rest timer presets could not be read, so the export was stopped.",
                BackupErrorCode.INVALID_DATA,
                e
            )
        }
    }

    private fun encodeRestTimers(timers: List<RestTimerV1>): String {
        val array = JSONArray()
        for (timer in timers) {
            array.put(
                org.json.JSONObject().apply {
                    put("id", timer.id)
                    put("name", timer.name)
                    put("totalSeconds", timer.totalSeconds)
                }
            )
        }
        return array.toString()
    }

    companion object {
        private const val LIBRARY_PREFS = "ironpulse_library_edits"
        private const val LIBRARY_EDITED_IDS_KEY = "edited_exercise_ids"
        private const val MAX_WIDGET_PHOTO_BYTES = 20L * 1024 * 1024

        private val EXPORTABLE_KEYS = listOf(
            UserPreferencesRepository.PROFILE_IS_SAVED,
            UserPreferencesRepository.PROFILE_NAME,
            UserPreferencesRepository.PROFILE_HEIGHT_CM,
            UserPreferencesRepository.PROFILE_CURRENT_WEIGHT_KG,
            UserPreferencesRepository.PROFILE_TARGET_WEIGHT_KG,
            UserPreferencesRepository.PROFILE_AGE,
            UserPreferencesRepository.PROFILE_PREFERRED_UNIT,
            UserPreferencesRepository.PROFILE_FITNESS_LEVEL,
            UserPreferencesRepository.SAVED_REST_TIMERS_JSON,
            UserPreferencesRepository.PR_PUSH_UPS,
            UserPreferencesRepository.PR_PUSH_UPS_ADDED,
            UserPreferencesRepository.PR_PULL_UPS,
            UserPreferencesRepository.PR_PULL_UPS_ADDED,
            UserPreferencesRepository.PR_BULGARIAN_SPLIT_SQUAT,
            UserPreferencesRepository.PR_BULGARIAN_SPLIT_SQUAT_ADDED,
            UserPreferencesRepository.REST_TIMER_VIBRATION_ENABLED,
            UserPreferencesRepository.REST_TIMER_SOUND_ENABLED,
            UserPreferencesRepository.SHARE_ATHLETE_DATA_WITH_AI,
            UserPreferencesRepository.WEIGHT_UNIT,
            UserPreferencesRepository.AUTOPLAY_EXERCISE_DEMOS,
            UserPreferencesRepository.LIQUID_GLASS_ENABLED,
            UserPreferencesRepository.FULLSCREEN_MODE,
            UserPreferencesRepository.THEME_MODE,
            UserPreferencesRepository.ACCENT_COLOR,
            UserPreferencesRepository.ONBOARDING_COMPLETED,
            UserPreferencesRepository.ACTIVE_WORKOUT_DRAFT,
            UserPreferencesRepository.WEEK_STARTS_ON_MONDAY,
            UserPreferencesRepository.AUTO_SAVE_INTERVAL_SECONDS,
            UserPreferencesRepository.AUTO_CHECK_UPDATES_ON_START,
            UserPreferencesRepository.AI_WORKOUT_DATA_ACCESS,
            UserPreferencesRepository.POST_WORKOUT_AI_SUMMARIES,
            UserPreferencesRepository.ANDROID_WIDGET_THEME_STREAK,
            UserPreferencesRepository.ANDROID_WIDGET_THEME_WEEKLY,
            UserPreferencesRepository.ANDROID_WIDGET_THEME_PROFILE,
            UserPreferencesRepository.ACTIVE_ROUTINE_ID,
            UserPreferencesRepository.HOME_WIDGET_ORDER,
            UserPreferencesRepository.HOME_WIDGET_HIDDEN,
            UserPreferencesRepository.HOME_WIDGET_PHOTO_PATH,
            UserPreferencesRepository.HOME_WIDGET_WEEKLY_GOAL,
            AiConfigurationRepository.ACTIVE_PROVIDER,
            AiConfigurationRepository.CUSTOM_BASE_URL,
            AiConfigurationRepository.CUSTOM_ACKNOWLEDGED_ENDPOINT,
            UserPreferencesRepository.GEMINI_MODEL_NAME
        )
    }
}
