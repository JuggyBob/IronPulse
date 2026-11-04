/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.BackupArchiveReader
import com.example.data.backup.BackupMapper
import com.example.data.backup.BackupValidator
import com.example.data.backup.BodyWeightLogV1
import com.example.data.backup.ChatMessageV1
import com.example.data.backup.ChatSessionV1
import com.example.data.backup.ExerciseV1
import com.example.data.backup.GeminiSummaryV1
import com.example.data.backup.LoggedSetV1
import com.example.data.backup.RoutinesSectionV1
import com.example.data.backup.SavedRoutineV1
import com.example.data.backup.ScheduledWorkoutDayV1
import com.example.data.backup.WorkoutSessionV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A reasonably large history must round-trip without loading every file twice, without quadratic
 * work and without slowing to a crawl. The time bound is generous so a slow CI machine is not
 * turned into a false failure; it exists to catch an architectural regression, not microseconds.
 */
class BackupPerformanceTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a large history exports and validates in bounded time and size`() {
        val sessionCount = 500
        val setCount = 6000
        val exerciseCount = 80
        val base = BackupTestFixtures.sampleSnapshot(withPhoto = false)

        val exercises = (0 until exerciseCount).map { index ->
            ExerciseV1(
                id = 500_000L + index,
                name = "Large Custom Exercise $index",
                category = "Back",
                bodyPart = "Back",
                target = "Lats",
                secondaryMuscles = "[]",
                exerciseType = "STANDARD_WEIGHT",
                instructions = "Rep $index",
                description = "",
                equipment = "Cable",
                mediaPath = "",
                sourceId = "",
                isCustom = true,
                isAiGenerated = index % 3 == 0
            )
        }
        val sessions = (0 until sessionCount).map { index ->
            WorkoutSessionV1(
                id = 1_000L + index,
                title = "Session $index",
                startTime = 1_700_000_000_000L + index * 3_600_000L,
                endTime = 1_700_000_000_000L + index * 3_600_000L + 2_700_000L,
                notes = if (index % 10 == 0) "Note $index" else "",
                routineId = 2_000L + (index % 50),
                routineWeekday = 1 + (index % 7),
                plannedDate = 1_699_000_000_000L + index * 86_400_000L,
                plannedDateKey = 20260101L + index
            )
        }
        val sets = (0 until setCount).map { index ->
            LoggedSetV1(
                id = 100_000L + index,
                sessionId = 1_000L + (index % sessionCount),
                exerciseId = 500_000L + (index % exerciseCount),
                exerciseName = "Large Custom Exercise ${index % exerciseCount}",
                exerciseType = "STANDARD_WEIGHT",
                setNumber = 1 + (index % 5),
                weight = 20.0 + (index % 200) / 4.0,
                addedWeight = if (index % 7 == 0) 2.5 else 0.0,
                reps = 5 + (index % 10),
                holdDurationSeconds = 0,
                distanceKm = 0.0,
                isCompleted = index % 11 != 0,
                timestamp = 1_700_000_000_000L + index * 1000L,
                bodyWeightKg = 81.25
            )
        }
        val routines = (0 until 100).map { index ->
            SavedRoutineV1(
                id = 2_000L + index,
                name = "Routine $index",
                description = "Generated for the performance test",
                daysPerWeek = 1 + index % 7,
                goal = "Hypertrophy",
                exercisesJson = """[{"exerciseId":${500_000L + index % exerciseCount},"exerciseName":"Large Custom Exercise ${index % exerciseCount}","weekday":1}]""",
                createdAt = 1_690_000_000_000L + index,
                scheduleVersion = 1
            )
        }
        val schedule = (0 until 400).map { index ->
            ScheduledWorkoutDayV1(
                dateStart = 1_690_000_000_000L + index * 86_400_000L,
                dateKey = 20250101L + index,
                routineId = 2_000L + index % 100,
                weekday = 1 + index % 7,
                dayTitle = "Day $index",
                exerciseCount = index % 4,
                capturedAt = 1_690_000_000_000L
            )
        }
        val weights = (0 until 500).map { index ->
            BodyWeightLogV1(300_000L + index, 70.0 + index / 100.0, 1_690_000_000_000L + index * 86_400_000L, "")
        }
        val chats = (0 until 60).map { index ->
            ChatSessionV1("large-chat-$index", "Conversation $index", 1_690_000_000_000L + index, 1_690_000_000_000L + index)
        }
        val messages = (0 until 1200).map { index ->
            ChatMessageV1(
                id = "large-message-$index",
                sessionId = "large-chat-${index % 60}",
                sender = if (index % 2 == 0) "USER" else "AI",
                text = "Message body number $index",
                timestamp = 1_690_000_000_000L + index
            )
        }
        val summaries = (0 until 100).map { index ->
            GeminiSummaryV1(
                400_000L + index,
                "Large Custom Exercise ${index % exerciseCount}",
                "# Summary $index",
                30,
                4,
                12,
                100.0 + index,
                index / 10.0,
                1_690_000_000_000L + index
            )
        }

        val snapshot = base.copy(
            editedLibraryExerciseIds = emptySet(),
            exercises = exercises,
            sessions = sessions,
            sets = sets,
            routines = routines,
            scheduleDays = schedule,
            bodyWeightLogs = weights,
            chatSessions = chats,
            chatMessages = messages,
            geminiSummaries = summaries
        )
        BackupValidator.validate(snapshot)

        val archive = temp.newFile("large.ironpulse")
        val started = System.nanoTime()
        BackupTestFixtures.writeArchive(snapshot, archive)
        val restored = BackupMapper.fromPayload(BackupArchiveReader(archive).read())
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000L

        BackupValidator.validate(restored)
        assertEquals(sessions, restored.sessions)
        assertEquals(sets, restored.sets)
        assertEquals(routines, restored.routines)
        assertEquals(messages, restored.chatMessages)
        assertTrue("archive was unexpectedly large: ${archive.length()}", archive.length() < 64L * 1024 * 1024)
        assertTrue("large backup took ${elapsedMillis}ms", elapsedMillis < 30_000L)
    }
}
