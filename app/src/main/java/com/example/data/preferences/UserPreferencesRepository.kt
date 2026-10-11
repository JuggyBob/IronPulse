package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL_NAME = stringPreferencesKey("gemini_model_name")
        val PROFILE_IS_SAVED = booleanPreferencesKey("profile_is_saved")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_HEIGHT_CM = doublePreferencesKey("profile_height_cm")
        val PROFILE_CURRENT_WEIGHT_KG = doublePreferencesKey("profile_current_weight_kg")
        val PROFILE_TARGET_WEIGHT_KG = doublePreferencesKey("profile_target_weight_kg")
        val PROFILE_AGE = intPreferencesKey("profile_age")
        val PROFILE_PREFERRED_UNIT = stringPreferencesKey("profile_preferred_unit")
        val PROFILE_FITNESS_LEVEL = stringPreferencesKey("profile_fitness_level")
        val SAVED_REST_TIMERS_JSON = stringPreferencesKey("saved_rest_timers_json")

        /**
         * Body weight benchmark personal records (max reps).
         *
         * Stored separately from the workouts on purpose: a PR the athlete achieved must stay on
         * their profile even if they later delete the workout that contains it.
         */
        val PR_PUSH_UPS = intPreferencesKey("pr_push_ups")
        val PR_PULL_UPS = intPreferencesKey("pr_pull_ups")
        val PR_BULGARIAN_SPLIT_SQUAT = intPreferencesKey("pr_bulgarian_split_squat")
        // The load each of the three records was done at, in kilograms. 0 means bodyweight. Read
        // together with the matching key above, so the AI is told "X reps with +Y kg" rather than a
        // rep count that could describe two completely different efforts.
        val PR_PUSH_UPS_ADDED = intPreferencesKey("pr_push_ups_added_kg")
        val PR_PULL_UPS_ADDED = intPreferencesKey("pr_pull_ups_added_kg")
        val PR_BULGARIAN_SPLIT_SQUAT_ADDED = intPreferencesKey("pr_bulgarian_split_squat_added_kg")

        /** Rest timer alerts, switchable from the settings page. */
        val REST_TIMER_VIBRATION_ENABLED = booleanPreferencesKey("rest_timer_vibration_enabled")
        val REST_TIMER_SOUND_ENABLED = booleanPreferencesKey("rest_timer_sound_enabled")

        /** Whether the athlete profile may be sent to Gemini with the AI prompts. */
        val SHARE_ATHLETE_DATA_WITH_AI = booleanPreferencesKey("share_athlete_data_with_ai")

        /** Body weight unit used across the app: "kg" or "lb". */
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")

        /**
         * Whether exercise animations play by themselves.
         *
         * Default on, because that is the normal reason someone opens an exercise. Turning it off is
         * for athletes on metered data or a low battery, where a list of looping GIFs is a real cost.
         */
        val AUTOPLAY_EXERCISE_DEMOS = booleanPreferencesKey("autoplay_exercise_demos")

        /**
         * Set once the athlete has finished the first run onboarding, so the landing pages are shown
         * to a new install and never again.
         */
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    // === APP SETTINGS (settings page) ===

    data class AppSettings(
        val unit: String = "kg",
        val restTimerVibration: Boolean = true,
        val restTimerSound: Boolean = true,
        // Off by default, matching what the onboarding and the settings page say. The previous
        // default (true) made the claim plain wrong: nothing was opt-in about data sharing, and an
        // athlete who never opened Settings had their profile sent with every AI request.
        val shareAthleteDataWithAi: Boolean = false,
        val autoplayExerciseDemos: Boolean = true
    )

    val appSettingsFlow: Flow<AppSettings> = context.userDataStore.data.map { prefs ->
        AppSettings(
            unit = prefs[WEIGHT_UNIT] ?: "kg",
            restTimerVibration = prefs[REST_TIMER_VIBRATION_ENABLED] ?: true,
            restTimerSound = prefs[REST_TIMER_SOUND_ENABLED] ?: true,
            shareAthleteDataWithAi = prefs[SHARE_ATHLETE_DATA_WITH_AI] ?: false,
            autoplayExerciseDemos = prefs[AUTOPLAY_EXERCISE_DEMOS] ?: true
        )
    }

    suspend fun setWeightUnit(unit: String) {
        context.userDataStore.edit { it[WEIGHT_UNIT] = unit }
    }

    suspend fun setRestTimerVibration(enabled: Boolean) {
        context.userDataStore.edit { it[REST_TIMER_VIBRATION_ENABLED] = enabled }
    }

    suspend fun setRestTimerSound(enabled: Boolean) {
        context.userDataStore.edit { it[REST_TIMER_SOUND_ENABLED] = enabled }
    }

    /** Whether the athlete profile may be sent to Gemini together with the AI prompts. */
    val shareAthleteDataFlow: Flow<Boolean> = context.userDataStore.data.map { prefs ->
        prefs[SHARE_ATHLETE_DATA_WITH_AI] ?: false
    }

    suspend fun setShareAthleteDataWithAi(enabled: Boolean) {
        context.userDataStore.edit { it[SHARE_ATHLETE_DATA_WITH_AI] = enabled }
    }

    /**
     * Whether exercise animations loop on their own. When false the athlete taps to start them,
     * which is the setting to use on metered data or a low battery.
     */
    val autoplayExerciseDemosFlow: Flow<Boolean> = context.userDataStore.data.map { prefs ->
        prefs[AUTOPLAY_EXERCISE_DEMOS] ?: true
    }

    suspend fun setAutoplayExerciseDemos(enabled: Boolean) {
        context.userDataStore.edit { it[AUTOPLAY_EXERCISE_DEMOS] = enabled }
    }

    /** False until the athlete finishes the first run onboarding. */
    val onboardingCompletedFlow: Flow<Boolean> = context.userDataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted() {
        context.userDataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }

    val defaultRestTimers = listOf(
        com.example.data.model.CustomRestTimer(name = "Quick Rest", totalSeconds = 30),
        com.example.data.model.CustomRestTimer(name = "Standard Rest", totalSeconds = 90),
        com.example.data.model.CustomRestTimer(name = "Heavy Compound", totalSeconds = 180)
    )

    val savedRestTimersFlow: Flow<List<com.example.data.model.CustomRestTimer>> = context.userDataStore.data.map { preferences ->
        val rawJson = preferences[SAVED_REST_TIMERS_JSON]
        if (rawJson.isNullOrBlank()) {
            defaultRestTimers
        } else {
            try {
                val array = org.json.JSONArray(rawJson)
                val list = mutableListOf<com.example.data.model.CustomRestTimer>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        com.example.data.model.CustomRestTimer(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.optString("name", "Rest Timer"),
                            totalSeconds = obj.optInt("totalSeconds", 90)
                        )
                    )
                }
                if (list.isEmpty()) defaultRestTimers else list
            } catch (_: Exception) {
                defaultRestTimers
            }
        }
    }

    suspend fun saveRestTimers(timers: List<com.example.data.model.CustomRestTimer>) {
        context.userDataStore.edit { preferences ->
            val array = org.json.JSONArray()
            for (timer in timers) {
                val obj = org.json.JSONObject().apply {
                    put("id", timer.id)
                    put("name", timer.name)
                    put("totalSeconds", timer.totalSeconds)
                }
                array.put(obj)
            }
            preferences[SAVED_REST_TIMERS_JSON] = array.toString()
        }
    }

    val geminiApiKeyFlow: Flow<String> = context.userDataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }

    val geminiModelFlow: Flow<String> = context.userDataStore.data.map { preferences ->
        preferences[GEMINI_MODEL_NAME] ?: ""
    }

    /**
     * The athlete's chosen weight unit, readable before the profile has ever been saved.
     *
     * This is deliberately not gated on [userProfileFlow]'s "saved" flag. During onboarding the
     * profile does not exist yet, so a flow that only reports a saved profile reads as null and the
     * unit the athlete picked on the "About you" page was never applied: the chips sat there, the
     * DataStore value changed, and nothing in the UI followed.
     *
     * The key order matters. [WEIGHT_UNIT] is the live setting every path writes through
     * (`setWeightUnit`), including the onboarding chips. [PROFILE_PREFERRED_UNIT] is written only by
     * `saveUserProfile`, so it is the fallback for an athlete whose profile was stored before this
     * existed and who has never opened Settings. Reading the profile key alone is what kept the
     * onboarding chips dead: nothing writes it until the form is submitted.
     */
    val preferredUnitFlow: Flow<String> = context.userDataStore.data.map { prefs ->
        prefs[WEIGHT_UNIT] ?: prefs[PROFILE_PREFERRED_UNIT] ?: "kg"
    }

    val userProfileFlow: Flow<UserProfile?> = context.userDataStore.data.map { prefs ->
        if (prefs[PROFILE_IS_SAVED] == true) {
            UserProfile(
                id = 1,
                name = prefs[PROFILE_NAME] ?: "Athlete",
                heightCm = prefs[PROFILE_HEIGHT_CM] ?: 178.0,
                currentWeightKg = prefs[PROFILE_CURRENT_WEIGHT_KG] ?: 75.0,
                targetWeightKg = prefs[PROFILE_TARGET_WEIGHT_KG] ?: 78.0,
                age = prefs[PROFILE_AGE] ?: 26,
                preferredUnit = prefs[PROFILE_PREFERRED_UNIT] ?: "kg",
                fitnessLevel = prefs[PROFILE_FITNESS_LEVEL] ?: "Intermediate",
                customApiKey = prefs[GEMINI_API_KEY] ?: ""
            )
        } else {
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        context.userDataStore.edit { prefs ->
            prefs[PROFILE_IS_SAVED] = true
            prefs[PROFILE_NAME] = profile.name
            prefs[PROFILE_HEIGHT_CM] = profile.heightCm
            prefs[PROFILE_CURRENT_WEIGHT_KG] = profile.currentWeightKg
            prefs[PROFILE_TARGET_WEIGHT_KG] = profile.targetWeightKg
            prefs[PROFILE_AGE] = profile.age
            prefs[PROFILE_PREFERRED_UNIT] = profile.preferredUnit
            prefs[PROFILE_FITNESS_LEVEL] = profile.fitnessLevel
        }
    }

    suspend fun clearUserProfile() {
        context.userDataStore.edit { prefs ->
            prefs.remove(PROFILE_IS_SAVED)
            prefs.remove(PROFILE_NAME)
            prefs.remove(PROFILE_HEIGHT_CM)
            prefs.remove(PROFILE_CURRENT_WEIGHT_KG)
            prefs.remove(PROFILE_TARGET_WEIGHT_KG)
            prefs.remove(PROFILE_AGE)
            prefs.remove(PROFILE_PREFERRED_UNIT)
            prefs.remove(PROFILE_FITNESS_LEVEL)
        }
    }

    /**
     * Body weight benchmark personal records, 0 meaning "not achieved yet".
     *
     * They live outside the workout history on purpose: deleting the workout that set the record
     * must not wipe the record from the athlete profile.
     *
     * Each one carries the load it was done at alongside the rep count. The record used to be a
     * bare rep count taken only from sets logged at bodyweight, which threw away two things the AI
     * needs: a set done with a dumbbell on the bar was not a bodyweight set so it was ignored
     * entirely, and there was no way to tell "20 reps at bodyweight" apart from "20 reps holding
     * +10 kg", which are very different athletes.
     */
    val benchmarkPullUpsFlow: Flow<Int> = context.userDataStore.data.map { it[PR_PULL_UPS] ?: 0 }
    val benchmarkPushUpsFlow: Flow<Int> = context.userDataStore.data.map { it[PR_PUSH_UPS] ?: 0 }
    val benchmarkBulgarianSplitSquatFlow: Flow<Int> =
        context.userDataStore.data.map { it[PR_BULGARIAN_SPLIT_SQUAT] ?: 0 }

    val benchmarkPullUpsAddedWeightFlow: Flow<Int> =
        context.userDataStore.data.map { it[PR_PULL_UPS_ADDED] ?: 0 }
    val benchmarkPushUpsAddedWeightFlow: Flow<Int> =
        context.userDataStore.data.map { it[PR_PUSH_UPS_ADDED] ?: 0 }
    val benchmarkBulgarianSplitSquatAddedWeightFlow: Flow<Int> =
        context.userDataStore.data.map { it[PR_BULGARIAN_SPLIT_SQUAT_ADDED] ?: 0 }

    suspend fun saveBenchmarkPullUps(reps: Int, addedKg: Int = 0) {
        if (reps <= 0) return
        context.userDataStore.edit {
            it[PR_PULL_UPS] = reps
            it[PR_PULL_UPS_ADDED] = if (addedKg > 0) addedKg else 0
        }
    }

    suspend fun saveBenchmarkPushUps(reps: Int, addedKg: Int = 0) {
        if (reps <= 0) return
        context.userDataStore.edit {
            it[PR_PUSH_UPS] = reps
            it[PR_PUSH_UPS_ADDED] = if (addedKg > 0) addedKg else 0
        }
    }

    suspend fun saveBenchmarkBulgarianSplitSquat(reps: Int, addedKg: Int = 0) {
        if (reps <= 0) return
        context.userDataStore.edit {
            it[PR_BULGARIAN_SPLIT_SQUAT] = reps
            it[PR_BULGARIAN_SPLIT_SQUAT_ADDED] = if (addedKg > 0) addedKg else 0
        }
    }

    /** Personal records are part of the athlete profile, so "Delete App Data" removes them. */
    suspend fun clearBenchmarkRecords() {
        context.userDataStore.edit { prefs ->
            prefs.remove(PR_PULL_UPS)
            prefs.remove(PR_PUSH_UPS)
            prefs.remove(PR_BULGARIAN_SPLIT_SQUAT)
            prefs.remove(PR_PULL_UPS_ADDED)
            prefs.remove(PR_PUSH_UPS_ADDED)
            prefs.remove(PR_BULGARIAN_SPLIT_SQUAT_ADDED)
        }
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        context.userDataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun clearGeminiApiKey() {
        context.userDataStore.edit { preferences ->
            preferences.remove(GEMINI_API_KEY)
        }
    }

    suspend fun saveGeminiModel(modelName: String) {
        context.userDataStore.edit { preferences ->
            val clean = modelName.trim().removePrefix("models/")
            if (clean.isBlank()) {
                preferences.remove(GEMINI_MODEL_NAME)
            } else {
                preferences[GEMINI_MODEL_NAME] = clean
            }
        }
    }

    suspend fun clearGeminiModel() {
        context.userDataStore.edit { preferences ->
            preferences.remove(GEMINI_MODEL_NAME)
        }
    }

    /**
     * Removes every entry stored in the preferences DataStore: the Gemini API key, the selected
     * Gemini model, the saved profile (name, height, weights, age, unit, fitness level) and the
     * custom rest timer presets.
     */
    suspend fun clearAllPreferences() {
        context.userDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}