package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("IronPulse", appName)
  }

  @Test
  fun `test routine parser JSON round trip`() {
    val originalItems = listOf(
        RoutineExerciseItem(
            exerciseId = 101L,
            exerciseName = "Weighted Pull-up",
            exerciseType = ExerciseType.ADDED_WEIGHT,
            category = "Back",
            equipment = "Pull-up Bar",
            targetSets = 4,
            targetReps = 6,
            targetWeight = 10.0,
            prescription = "4 sets x 6 reps (+10kg)"
        ),
        RoutineExerciseItem(
            exerciseId = 102L,
            exerciseName = "Barbell Bench Press",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            category = "Chest",
            equipment = "Barbell",
            targetSets = 3,
            targetReps = 8,
            targetWeight = 80.0,
            prescription = "3 sets x 8 reps"
        )
    )

    val serialized = RoutineParser.serializeExercises(originalItems)
    val dummyDb = listOf(
        Exercise(id = 101L, name = "Weighted Pull-up", category = "Back", exerciseType = ExerciseType.ADDED_WEIGHT, description = "", equipment = "Pull-up Bar"),
        Exercise(id = 102L, name = "Barbell Bench Press", category = "Chest", exerciseType = ExerciseType.STANDARD_WEIGHT, description = "", equipment = "Barbell")
    )

    val parsed = RoutineParser.parseExercises(serialized, dummyDb)
    assertEquals(2, parsed.size)
    assertEquals(101L, parsed[0].exerciseId)
    assertEquals("Weighted Pull-up", parsed[0].exerciseName)
    assertEquals(4, parsed[0].targetSets)
    assertEquals(6, parsed[0].targetReps)
    assertEquals(102L, parsed[1].exerciseId)
    assertEquals("Barbell Bench Press", parsed[1].exerciseName)
    assertEquals(3, parsed[1].targetSets)
    assertEquals(8, parsed[1].targetReps)
  }

  @Test
  fun `test routine parser structured text from AI`() {
    val rawAiResponse = """
        ### Day 1: Upper Body Power
        EXERCISE: Barbell Bench Press | 4 sets x 8 reps
        EXERCISE: Weighted Dips | 3 sets x 6 reps
        EXERCISE: Cable Tricep Pushdown | 3 sets x 12 reps
    """.trimIndent()

    val dummyDb = listOf(
        Exercise(id = 1L, name = "Barbell Bench Press", category = "Chest", equipment = "Barbell", exerciseType = ExerciseType.STANDARD_WEIGHT, description = ""),
        Exercise(id = 2L, name = "Weighted Dips", category = "Chest", equipment = "Dip Bar", exerciseType = ExerciseType.ADDED_WEIGHT, description = ""),
        Exercise(id = 3L, name = "Cable Tricep Pushdown", category = "Arms", equipment = "Cable", exerciseType = ExerciseType.STANDARD_WEIGHT, description = "")
    )

    val parsed = RoutineParser.parseExercises(rawAiResponse, dummyDb)
    assertEquals(3, parsed.size)
    assertEquals("Barbell Bench Press", parsed[0].exerciseName)
    assertEquals(4, parsed[0].targetSets)
    assertEquals(8, parsed[0].targetReps)
    assertEquals("Weighted Dips", parsed[1].exerciseName)
    assertEquals(3, parsed[1].targetSets)
    assertEquals(6, parsed[1].targetReps)
    assertEquals("Cable Tricep Pushdown", parsed[2].exerciseName)
  }

  @Test
  fun `test extractJson cleans markdown code blocks`() {
    val fencedJson = """
        ```json
        {
          "is_gibberish": false,
          "routine_name": "Chest Blast",
          "exercises": []
        }
        ```
    """.trimIndent()
    val extracted = RoutineParser.extractJson(fencedJson)
    org.junit.Assert.assertTrue(extracted.startsWith("{"))
    org.junit.Assert.assertTrue(extracted.endsWith("}"))

    val json = org.json.JSONObject(extracted)
    assertEquals(false, json.getBoolean("is_gibberish"))
    assertEquals("Chest Blast", json.getString("routine_name"))
  }

  @Test
  fun `test unstructured workout response identifies gibberish correctly`() {
    val gibberishJson = """
        {
          "is_gibberish": true,
          "error_reason": "Input is unreadable and does not describe exercises or sets/reps.",
          "new_exercises_to_create": [],
          "exercises": []
        }
    """.trimIndent()
    val json = org.json.JSONObject(gibberishJson)
    assertEquals(true, json.getBoolean("is_gibberish"))
    org.junit.Assert.assertTrue(json.getString("error_reason").contains("unreadable"))
  }

  @Test
  fun `test unstructured workout response with new exercises to create`() {
    val successJson = """
        {
          "is_gibberish": false,
          "error_reason": "",
          "routine_name": "Nordic & Incline Power",
          "routine_goal": "Strength",
          "new_exercises_to_create": [
            {
              "name": "Nordic Hamstring Curl",
              "category": "Legs",
              "exerciseType": "PURE_BODYWEIGHT",
              "equipment": "Bodyweight",
              "description": "Eccentric knee flexion"
            }
          ],
          "exercises": [
            {
              "name": "Barbell Bench Press",
              "sets": 4,
              "reps": 8,
              "weight": 80.0,
              "holdSeconds": 0,
              "prescription": "4 sets x 8 reps"
            },
            {
              "name": "Nordic Hamstring Curl",
              "sets": 3,
              "reps": 6,
              "weight": 0.0,
              "holdSeconds": 0,
              "prescription": "3 sets x 6 reps"
            }
          ]
        }
    """.trimIndent()

    val json = org.json.JSONObject(successJson)
    assertEquals(false, json.getBoolean("is_gibberish"))
    val newExercises = json.getJSONArray("new_exercises_to_create")
    assertEquals(1, newExercises.length())
    val newEx = newExercises.getJSONObject(0)
    assertEquals("Nordic Hamstring Curl", newEx.getString("name"))
    assertEquals("Legs", newEx.getString("category"))

    val exercises = json.getJSONArray("exercises")
    assertEquals(2, exercises.length())
    assertEquals("Barbell Bench Press", exercises.getJSONObject(0).getString("name"))
    assertEquals(4, exercises.getJSONObject(0).getInt("sets"))
    assertEquals("Nordic Hamstring Curl", exercises.getJSONObject(1).getString("name"))
  }

  @Test
  fun `test exercise pagination logic chunks and bounds`() {
    val totalExercises = (1..833).map {
      Exercise(
        id = it.toLong(),
        name = "Exercise $it",
        category = if (it % 2 == 0) "Chest" else "Back",
        equipment = "Barbell",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        description = ""
      )
    }

    val pageSize = 30
    var displayedCount = pageSize
    val initialPage = totalExercises.take(displayedCount)
    assertEquals(30, initialPage.size)
    assertEquals("Exercise 1", initialPage.first().name)
    assertEquals("Exercise 30", initialPage.last().name)

    // Scroll to end loads next 30
    displayedCount = (displayedCount + pageSize).coerceAtMost(totalExercises.size)
    val secondPage = totalExercises.take(displayedCount)
    assertEquals(60, secondPage.size)

    // Simulate paging up to total
    displayedCount = (displayedCount + 800).coerceAtMost(totalExercises.size)
    val fullPage = totalExercises.take(displayedCount)
    assertEquals(833, fullPage.size)
  }

  @Test
  fun `test completed exercises filter logic`() {
    val completedExercises = listOf("Barbell Bench Press", "Incline Dumbbell Press", "Dumbbell Bicep Curl", "Weighted Pull-up")
    val query = "dumbbell"
    val filtered = completedExercises.filter { it.contains(query, ignoreCase = true) }
    assertEquals(2, filtered.size)
    org.junit.Assert.assertTrue(filtered.contains("Incline Dumbbell Press"))
    org.junit.Assert.assertTrue(filtered.contains("Dumbbell Bicep Curl"))
  }

  @Test
  fun `test isolated weight tag values for standard and added weight exercises`() {
    val dumbbellWeights = listOf(14.0, 16.0, 16.0, 20.0, 14.0).distinct().sorted()
    assertEquals(listOf(14.0, 16.0, 20.0), dumbbellWeights)

    val addedWeights = listOf(0.0, 10.0, 15.0, 10.0).distinct().sorted()
    assertEquals(listOf(0.0, 10.0, 15.0), addedWeights)
  }

  @Test
  fun `test custom routine prompt parsing and exercise registration`() {
    val aiResponseJson = """
      {
        "is_valid_request": true,
        "error_reason": "",
        "routine_name": "Calisthenics Power Split",
        "routine_goal": "Strength & Muscle-up",
        "days_per_week": 2,
        "new_exercises_to_create": [
          {
            "name": "Pelican Curl",
            "category": "Arms",
            "exerciseType": "PURE_BODYWEIGHT",
            "equipment": "Rings",
            "description": "Bicep curl on gymnastic rings"
          }
        ],
        "exercises": [
          {
            "name": "Weighted Pull-up",
            "sets": 4,
            "reps": 6,
            "weight": 15.0,
            "holdSeconds": 0,
            "prescription": "4 sets x 6 reps (+15kg)",
            "day": 1,
            "dayTitle": "Day 1: Upper Pull"
          },
          {
            "name": "Pelican Curl",
            "sets": 3,
            "reps": 8,
            "weight": 0.0,
            "holdSeconds": 0,
            "prescription": "3 sets x 8 reps",
            "day": 1,
            "dayTitle": "Day 1: Upper Pull"
          }
        ]
      }
    """.trimIndent()

    val extracted = RoutineParser.extractJson(aiResponseJson)
    val json = org.json.JSONObject(extracted)
    org.junit.Assert.assertTrue(json.getBoolean("is_valid_request"))
    assertEquals("Calisthenics Power Split", json.getString("routine_name"))

    val newExercises = json.getJSONArray("new_exercises_to_create")
    assertEquals(1, newExercises.length())
    val newEx = newExercises.getJSONObject(0)
    assertEquals("Pelican Curl", newEx.getString("name"))
    assertEquals("Arms", newEx.getString("category"))

    val exercises = json.getJSONArray("exercises")
    assertEquals(2, exercises.length())
  }

  @Test
  fun `test custom routine prompt identifies gibberish or vague prompt`() {
    val vagueResponseJson = """
      {
        "is_valid_request": false,
        "error_reason": "Your request is too vague. Please specify your goals, target muscle groups, or preferred schedule.",
        "routine_name": "",
        "exercises": []
      }
    """.trimIndent()

    val extracted = RoutineParser.extractJson(vagueResponseJson)
    val json = org.json.JSONObject(extracted)
    org.junit.Assert.assertFalse(json.getBoolean("is_valid_request"))
    org.junit.Assert.assertTrue(json.getString("error_reason").contains("too vague"))
  }

  @Test
  fun `test GeminiClient default model and endpoint resolution`() {
    assertEquals("gemini-3.5-flash-lite", com.example.data.api.GeminiClient.DEFAULT_MODEL)
    
    // When model is null or blank, defaults to gemini-3.5-flash-lite
    val defaultEndpoint = com.example.data.api.GeminiClient.getModelEndpoint(null)
    org.junit.Assert.assertTrue(defaultEndpoint.contains("gemini-3.5-flash-lite:generateContent"))

    val blankEndpoint = com.example.data.api.GeminiClient.getModelEndpoint("   ")
    org.junit.Assert.assertTrue(blankEndpoint.contains("gemini-3.5-flash-lite:generateContent"))

    // When custom model is provided, uses custom model
    val customEndpoint = com.example.data.api.GeminiClient.getModelEndpoint("gemini-3.5-flash")
    org.junit.Assert.assertTrue(customEndpoint.contains("gemini-3.5-flash:generateContent"))

    // When model has models/ prefix, strips it correctly
    val prefixedEndpoint = com.example.data.api.GeminiClient.getModelEndpoint("models/gemini-3.1-pro-preview")
    org.junit.Assert.assertTrue(prefixedEndpoint.contains("gemini-3.1-pro-preview:generateContent"))
  }

  @Test
  fun `test GeminiClient validateModel fails gracefully with empty API key`() = kotlinx.coroutines.runBlocking {
    val result = com.example.data.api.GeminiClient.validateModel("gemini-3.5-flash", "")
    org.junit.Assert.assertTrue(result.isFailure)
    org.junit.Assert.assertTrue(result.exceptionOrNull()?.message?.contains("API key") == true)
  }

  @Test
  fun `test GeminiClient curated model list contains Gemini and Gemma models`() {
    val models = com.example.data.api.GeminiClient.getCuratedModelList()
    org.junit.Assert.assertTrue(models.isNotEmpty())
    
    // Default model is first
    assertEquals("gemini-3.5-flash-lite", models.first().apiName)
    
    // Contains both Gemini and Gemma
    val hasGemini = models.any { !it.isGemma && it.apiName.contains("gemini") }
    val hasGemma = models.any { it.isGemma && it.apiName.contains("gemma") }
    org.junit.Assert.assertTrue(hasGemini)
    org.junit.Assert.assertTrue(hasGemma)

    // Check all models have valid API name and Display Name
    models.forEach { model ->
      org.junit.Assert.assertTrue(model.apiName.isNotBlank())
      org.junit.Assert.assertTrue(model.displayName.isNotBlank())
    }
  }

  @Test
  fun `test exercise database deduplication and unique constraint prevents duplicates`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.db.AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = db.exerciseDao()
    
    // Seed initial list
    val seed = listOf(
      com.example.data.model.Exercise(name = "Push-up", category = "Chest", exerciseType = com.example.data.model.ExerciseType.ADDED_WEIGHT, equipment = "Bodyweight"),
      com.example.data.model.Exercise(name = "Pull-up", category = "Back", exerciseType = com.example.data.model.ExerciseType.ADDED_WEIGHT, equipment = "Pull-up Bar"),
      com.example.data.model.Exercise(name = "Barbell Bench Press", category = "Chest", exerciseType = com.example.data.model.ExerciseType.STANDARD_WEIGHT, equipment = "Barbell")
    )
    dao.insertAll(seed)
    val initialCount = dao.getCount()
    assertEquals(3, initialCount)

    // Attempt to re-insert the entire list (should be ignored by unique constraint)
    dao.insertAll(seed)
    val countAfterReinsert = dao.getCount()
    assertEquals(3, countAfterReinsert)

    // Run deduplicateByName query
    dao.deduplicateByName()
    assertEquals(3, dao.getCount())

    db.close()
  }

  @Test
  fun `test cleanPrescription removes equipment but retains sets, reps, and loads`() {
    // Parenthesized equipment removed
    assertEquals("4 sets x 8 reps", RoutineParser.cleanPrescription("4 sets x 8 reps (Barbell)", "Barbell"))
    assertEquals("3 sets x 10 reps", RoutineParser.cleanPrescription("3 sets x 10 reps with dumbbells", "Dumbbell"))
    assertEquals("4x8 @ 80kg", RoutineParser.cleanPrescription("4x8 @ 80kg (Barbell)", "Barbell"))
    assertEquals("3 sets x 12 reps", RoutineParser.cleanPrescription("3 sets x 12 reps - Cable", "Cable"))
    assertEquals("4x6 @ +15kg", RoutineParser.cleanPrescription("4x6 @ +15kg (Dip Belt)", "Bar & Dip Belt"))
    assertEquals("3 sets x 30s", RoutineParser.cleanPrescription("3 sets x 30s", "Bodyweight"))
    // Pure equipment returns empty string to trigger fallback
    assertEquals("", RoutineParser.cleanPrescription("Barbell", "Barbell"))
    assertEquals("", RoutineParser.cleanPrescription("Dumbbells", "Dumbbells"))
  }

  @Test
  fun `test parseDays extracts clean prescriptions without equipment from unstructured text`() {
    val sampleExercises = listOf(
        com.example.data.model.Exercise(id = 1, name = "Barbell Bench Press", category = "Chest", exerciseType = com.example.data.model.ExerciseType.STANDARD_WEIGHT, equipment = "Barbell"),
        com.example.data.model.Exercise(id = 2, name = "Incline Dumbbell Press", category = "Chest", exerciseType = com.example.data.model.ExerciseType.STANDARD_WEIGHT, equipment = "Dumbbells"),
        com.example.data.model.Exercise(id = 3, name = "Weighted Pull-up", category = "Back", exerciseType = com.example.data.model.ExerciseType.ADDED_WEIGHT, equipment = "Bar & Dip Belt")
    )
    val text = """
        1. Bench Press (Barbell) (4x8)
        2. Incline Dumbbell Press - 3 sets x 10 reps
        3. Pull-up (Weighted) = 4 sets x 6 reps @ +10kg
    """.trimIndent()

    val parsedDays = RoutineParser.parseDays(text, sampleExercises)
    assertEquals(1, parsedDays.size)
    val exercises = parsedDays.first().exercises
    assertEquals(3, exercises.size)

    // The prescription is always reduced to "<sets> sets x <reps> reps" and nothing else.
    //
    // A tag that carries the load as well ("4 sets x 6 reps @ +10kg") is too wide for the pill it
    // is shown in and wraps onto its own line, and the load is the athlete's to set, not the
    // program's. The load is kept in the structured fields, not in the text.
    assertEquals("4 sets x 8 reps", exercises[0].prescription)
    org.junit.Assert.assertFalse(exercises[0].prescription.contains("Barbell", ignoreCase = true))

    assertEquals("3 sets x 10 reps", exercises[1].prescription)
    org.junit.Assert.assertFalse(exercises[1].prescription.contains("Dumbbell", ignoreCase = true))

    assertEquals("4 sets x 6 reps", exercises[2].prescription)
    org.junit.Assert.assertFalse(exercises[2].prescription.contains("Weighted", ignoreCase = true))
  }
}
