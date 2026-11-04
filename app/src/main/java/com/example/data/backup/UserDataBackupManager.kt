/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.db.AppDatabase
import com.example.data.model.BodyWeightLog
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.GeminiSummary
import com.example.data.model.LoggedSet
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduledWorkoutDay
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.seed.DatasetSeeder
import com.example.util.ExerciseMediaStore
import com.example.util.WidgetPhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates export, preflight validation, replacement and rollback.
 *
 * The class deliberately has one entry point per operation and a mutex around both. A second export
 * or import cannot start while one is running, and no caller has to reason about interleaving.
 */
class UserDataBackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val preferences: BackupPreferencesStore = BackupPreferencesStore(context)
) {

    private val operationMutex = Mutex()

    suspend fun exportToFile(
        destination: File,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): ExportSummary = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val now = System.currentTimeMillis()
            onProgress(BackupProgress(BackupPhase.PREPARING, 0, 8))
            val snapshot = readSnapshot()
            coroutineContext.ensureActive()
            onProgress(BackupProgress(BackupPhase.READING_USER_DATA, 2, 8))

            BackupValidator.validate(snapshot)
            onProgress(BackupProgress(BackupPhase.SERIALIZING, 3, 8))
            val sections = BackupMapper.toSections(snapshot)
            val counts = snapshot.sectionCounts
            onProgress(BackupProgress(BackupPhase.COLLECTING_FILES, 4, 8))

            val manifestSections = LinkedHashMap<String, BackupSectionEntry>()
            for ((name, path) in BackupFormat.REQUIRED_SECTIONS) {
                manifestSections[name] = BackupSectionEntry(
                    path = path,
                    required = true,
                    recordCount = counts[name] ?: 0
                )
            }
            val photo = snapshot.widgetPhoto
            val archivePhotoPath = photo?.let {
                "${BackupFormat.FILE_WIDGET_PHOTO}.${it.extension}"
            }
            val fileEntries = if (photo != null && archivePhotoPath != null) {
                listOf(
                    BackupFileEntry(
                        path = archivePhotoPath,
                        role = BackupFormat.ROLE_WIDGET_PHOTO,
                        mediaType = photo.mediaType,
                        size = photo.bytes.size.toLong(),
                        sha256 = BackupArchiveReader.sha256(photo.bytes)
                    )
                )
            } else {
                emptyList()
            }
            val manifest = BackupManifest(
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                exportedAt = isoUtc(now),
                exportedAtEpochMillis = now,
                sections = manifestSections,
                files = fileEntries
            )
            val files = if (photo != null && archivePhotoPath != null) {
                mapOf(archivePhotoPath to photo.bytes)
            } else {
                emptyMap()
            }

            onProgress(BackupProgress(BackupPhase.COMPRESSING, 5, 8))
            BackupArchiveReader.writeToFile(
                file = destination,
                payload = ArchivePayload(manifest, sections, files)
            )
            coroutineContext.ensureActive()
            onProgress(BackupProgress(BackupPhase.VERIFYING, 7, 8))
            // writeToFile already verified the archive it moved into place. Reopen the public file
            // once more so a storage provider that truncated or replaced it cannot be reported as a
            // successful export.
            BackupArchiveReader(destination).verify()
            onProgress(BackupProgress(BackupPhase.COMPLETED, 8, 8))
            ExportSummary(
                fileName = destination.name,
                sizeBytes = destination.length(),
                exportedAtEpochMillis = now,
                sectionCounts = counts
            )
        }
    }

    suspend fun inspectFile(
        archive: File,
        displayName: String?,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): PreparedImport = withContext(Dispatchers.IO) {
        onProgress(BackupProgress(BackupPhase.VALIDATING, 0, 5))
        val payload = BackupArchiveReader(archive).read()
        onProgress(BackupProgress(BackupPhase.VALIDATING, 2, 5))

        val migrated = if (payload.manifest.dataSchemaVersion < BackupFormat.CURRENT_DATA_SCHEMA_VERSION) {
            val trees = LinkedHashMap<String, JsonNode.Obj>()
            for ((name, bytes) in payload.sections) {
                val node = JsonNode.parse(bytes)
                if (node !is JsonNode.Obj) {
                    throw BackupException(
                        "A backup section is not a JSON object and cannot be migrated.",
                        BackupErrorCode.INVALID_DATA
                    )
                }
                trees[name] = node
            }
            BackupSchemaMigrations.migrate(
                sections = trees,
                fromVersion = payload.manifest.dataSchemaVersion,
                toVersion = BackupFormat.CURRENT_DATA_SCHEMA_VERSION
            )
            val migratedSections = trees.mapValues { (_, node) ->
                node.render().toByteArray(Charsets.UTF_8)
            }
            payload.copy(
                manifest = payload.manifest.copy(dataSchemaVersion = BackupFormat.CURRENT_DATA_SCHEMA_VERSION),
                sections = migratedSections
            )
        } else {
            payload
        }

        val snapshot = try {
            BackupMapper.fromPayload(migrated)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(
                "The backup's data is malformed or contains values this app cannot read. Nothing was changed.",
                BackupErrorCode.INVALID_DATA,
                e
            )
        }
        BackupValidator.validate(snapshot)
        onProgress(BackupProgress(BackupPhase.VALIDATING, 4, 5))

        for ((name, section) in migrated.manifest.sections) {
            // The preferences count is advisory: schema v2 adds allowlisted fields, and an older
            // archive must still import with its original, smaller count.
            if (name == BackupFormat.SECTION_PREFERENCES) continue
            val expected = snapshot.sectionCounts[name] ?: 0
            if (section.recordCount != expected) {
                throw BackupException(
                    "The backup's $name section does not match the record count in its manifest.",
                    BackupErrorCode.INVALID_DATA
                )
            }
        }

        val preview = BackupPreview(
            fileName = displayName,
            exportedAt = migrated.manifest.exportedAt,
            exportedAtEpochMillis = migrated.manifest.exportedAtEpochMillis,
            appVersionName = migrated.manifest.appVersionName,
            appVersionCode = migrated.manifest.appVersionCode,
            formatVersion = migrated.manifest.formatVersion,
            dataSchemaVersion = migrated.manifest.dataSchemaVersion,
            archiveSizeBytes = archive.length(),
            sectionCounts = snapshot.sectionCounts
        )
        onProgress(BackupProgress(BackupPhase.VALIDATING, 5, 5))
        PreparedImport(preview = preview, snapshot = snapshot, archiveFile = archive)
    }

    suspend fun applyImport(
        prepared: PreparedImport,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): ImportSummary = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            onProgress(BackupProgress(BackupPhase.STAGING, 0, 9))
            val imported = prepared.snapshot
            val rollback = readSnapshot()
            val destinationProfileKey = rollback.roomCustomApiKey
            val currentExercises = db.exerciseDao().getAllExercisesList()
            val conflictIds = findLibraryNameConflicts(currentExercises, imported)
            validateIdentifierCollisions(currentExercises, imported)

            var trackerChanged = false
            var databaseChanged = false
            var stagedWidgetPhoto: String? = null
            var preferencesChanged = false

            try {
                onProgress(BackupProgress(BackupPhase.APPLYING_DATABASE, 2, 9))
                preferences.writeEditedLibraryExerciseIds(imported.editedLibraryExerciseIds)
                trackerChanged = true

                replaceRoomData(imported, destinationProfileKey, conflictIds)
                databaseChanged = true
                DatasetSeeder.seedFresh(context, db.exerciseDao())
                coroutineContext.ensureActive()

                onProgress(BackupProgress(BackupPhase.RESTORING_FILES, 4, 9))
                stagedWidgetPhoto = imported.widgetPhoto?.let { photo ->
                    preferences.stageWidgetPhoto(photo)
                        ?: throw BackupException(
                            "The Home photo could not be restored.",
                            BackupErrorCode.IO_ERROR
                        )
                }

                onProgress(BackupProgress(BackupPhase.APPLYING_PREFERENCES, 5, 9))
                val dashboard = imported.dashboard.copy(
                    photoPresent = imported.widgetPhoto != null,
                    photoArchivePath = imported.widgetPhoto?.let {
                        "${BackupFormat.FILE_WIDGET_PHOTO}.${it.extension}"
                    },
                    photoMediaType = imported.widgetPhoto?.mediaType
                )
                preferences.apply(
                    profile = imported.dataStoreProfile,
                    preferences = imported.preferences,
                    dashboard = dashboard,
                    widgetPhotoPath = stagedWidgetPhoto
                )
                preferencesChanged = true
                coroutineContext.ensureActive()

                onProgress(BackupProgress(BackupPhase.VERIFYING, 7, 9))
                verifyImport(imported, destinationProfileKey, stagedWidgetPhoto)
                onProgress(BackupProgress(BackupPhase.CLEANING_UP, 8, 9))

                // Only after verification: old widget photos are no longer referenced, and media
                // attached to the replaced user-owned exercises is not part of the new state.
                preferences.cleanWidgetPhotos(stagedWidgetPhoto)
                for (exercise in rollback.exercises) {
                    if (exercise.isCustom || exercise.isAiGenerated) {
                        ExerciseMediaStore.delete(context, exercise.id)
                    }
                }
                onProgress(BackupProgress(BackupPhase.COMPLETED, 9, 9))
                ImportSummary(
                    preview = prepared.preview,
                    restoredCounts = imported.sectionCounts
                )
            } catch (e: CancellationException) {
                rollbackAfterFailure(
                    rollback = rollback,
                    destinationProfileKey = destinationProfileKey,
                    trackerChanged = trackerChanged,
                    databaseChanged = databaseChanged,
                    stagedWidgetPhoto = stagedWidgetPhoto,
                    preferencesChanged = preferencesChanged
                )
                throw e
            } catch (e: Exception) {
                val rolledBack = rollbackAfterFailure(
                    rollback = rollback,
                    destinationProfileKey = destinationProfileKey,
                    trackerChanged = trackerChanged,
                    databaseChanged = databaseChanged,
                    stagedWidgetPhoto = stagedWidgetPhoto,
                    preferencesChanged = preferencesChanged
                )
                if (e is BackupException && e.code == BackupErrorCode.ROLLBACK_FAILED) throw e
                if (!rolledBack) {
                    throw BackupException(
                        "The import failed and the previous data could not be fully restored. Do not continue using the app until you restore a backup.",
                        BackupErrorCode.ROLLBACK_FAILED,
                        e
                    )
                }
                throw BackupException(
                    "The import failed. Your previous data was restored. ${e.message.orEmpty()}".trim(),
                    if (e is BackupException) e.code else BackupErrorCode.IO_ERROR,
                    e,
                    dataPreserved = true
                )
            } finally {
                prepared.cleanup()
            }
        }
    }

    private suspend fun readSnapshot(): UserDataSnapshot {
        val editedIds = preferences.readEditedLibraryExerciseIds()
        val room = db.withTransaction {
            RoomPart(
                profile = db.userProfileDao().getProfileDirect(),
                exercises = db.exerciseDao().getAllExercisesList(),
                sessions = db.workoutDao().getAllSessionsOnce(),
                sets = db.workoutDao().getAllSets(),
                routines = db.savedRoutineDao().getAllRoutinesOnce(),
                scheduleDays = db.scheduleDao().getAllScheduleDaysOnce(),
                bodyWeightLogs = db.bodyWeightDao().getAllWeightLogsOnce(),
                chatSessions = db.chatDao().getAllSessionsOnce(),
                chatMessages = db.chatDao().getAllMessagesOnce(),
                geminiSummaries = db.geminiSummaryDao().getAllSummariesOnce(),
                workoutSummaries = db.workoutAiSummaryDao().getAllSummariesOnce()
            )
        }
        val prefs = preferences.readSnapshot()
        val photo = preferences.readWidgetPhoto(prefs.widgetPhotoPath)
        val dashboard = prefs.dashboard.copy(
            photoPresent = photo != null,
            photoArchivePath = photo?.let { "${BackupFormat.FILE_WIDGET_PHOTO}.${it.extension}" },
            photoMediaType = photo?.mediaType
        )
        return UserDataSnapshot(
            dataStoreProfile = prefs.dataStoreProfile,
            roomProfile = room.profile?.let { profile ->
                RoomProfileV1(
                    id = profile.id,
                    name = profile.name,
                    heightCm = profile.heightCm,
                    currentWeightKg = profile.currentWeightKg,
                    targetWeightKg = profile.targetWeightKg,
                    age = profile.age,
                    preferredUnit = profile.preferredUnit,
                    fitnessLevel = profile.fitnessLevel
                )
            },
            roomCustomApiKey = room.profile?.customApiKey.orEmpty(),
            preferences = prefs.preferences,
            dashboard = dashboard,
            editedLibraryExerciseIds = editedIds,
            exercises = room.exercises
                .asSequence()
                .filter { it.isCustom || it.isAiGenerated || it.id in editedIds }
                .sortedBy { it.id }
                .map(BackupMapper::exerciseToDto)
                .toList(),
            sessions = room.sessions.map(BackupMapper::sessionToDto),
            sets = room.sets.sortedBy { it.id }.map(BackupMapper::setToDto),
            routines = room.routines.map(BackupMapper::routineToDto),
            scheduleDays = room.scheduleDays.map(BackupMapper::scheduleDayToDto),
            bodyWeightLogs = room.bodyWeightLogs.map(BackupMapper::weightLogToDto),
            chatSessions = room.chatSessions.map(BackupMapper::chatSessionToDto),
            chatMessages = room.chatMessages.map(BackupMapper::chatMessageToDto),
            geminiSummaries = room.geminiSummaries.map(BackupMapper::geminiSummaryToDto),
            workoutSummaries = room.workoutSummaries.map(BackupMapper::workoutSummaryToDto),
            widgetPhoto = photo,
            widgetPhotoPath = prefs.widgetPhotoPath.takeIf { it.isNotBlank() }
        )
    }

    private fun findLibraryNameConflicts(
        currentExercises: List<Exercise>,
        imported: UserDataSnapshot
    ): List<Long> {
        val importedCustomNames = imported.exercises
            .filter { it.isCustom || it.isAiGenerated }
            .map { it.name.trim().lowercase() }
            .toSet()
        return currentExercises
            .filter { !it.isCustom && !it.isAiGenerated }
            .filter { it.name.trim().lowercase() in importedCustomNames }
            .filter { row -> imported.exercises.none { it.id == row.id } }
            .map { it.id }
    }

    private fun validateIdentifierCollisions(
        currentExercises: List<Exercise>,
        imported: UserDataSnapshot
    ) {
        val byId = currentExercises.associateBy { it.id }
        for (row in imported.exercises) {
            val existing = byId[row.id] ?: continue
            // A user-owned destination row is deleted before the replacement, so a different
            // custom exercise that happened to receive the same auto-generated id on another device
            // is not a conflict. A shipped library row, however, is not user data and must never be
            // silently overwritten with a different exercise.
            val existingIsUserOwned = existing.isCustom || existing.isAiGenerated
            if (!existingIsUserOwned && !existing.name.equals(row.name, ignoreCase = true)) {
                throw BackupException(
                    "The backup contains an exercise identifier that belongs to a different exercise on this device, so nothing was changed.",
                    BackupErrorCode.RELATIONSHIP_ERROR
                )
            }
        }
    }

    private suspend fun replaceRoomData(
        snapshot: UserDataSnapshot,
        profileApiKey: String,
        conflictIds: List<Long>
    ) {
        val exercises = snapshot.exercises.map(BackupMapper::exerciseFromDto)
        val sessions = snapshot.sessions.map(BackupMapper::sessionFromDto)
        val sets = snapshot.sets.map(BackupMapper::setFromDto)
        val routines = snapshot.routines.map(BackupMapper::routineFromDto)
        val scheduleDays = snapshot.scheduleDays.map(BackupMapper::scheduleDayFromDto)
        val weights = snapshot.bodyWeightLogs.map(BackupMapper::weightLogFromDto)
        val chats = snapshot.chatSessions.map(BackupMapper::chatSessionFromDto)
        val messages = snapshot.chatMessages.map(BackupMapper::chatMessageFromDto)
        val summaries = snapshot.geminiSummaries.map(BackupMapper::geminiSummaryFromDto)
        val workoutSummaries = snapshot.workoutSummaries.map(BackupMapper::workoutSummaryFromDto)
        val profile = snapshot.roomProfile?.let {
            UserProfile(
                id = 1,
                name = it.name,
                heightCm = it.heightCm,
                currentWeightKg = it.currentWeightKg,
                targetWeightKg = it.targetWeightKg,
                age = it.age,
                preferredUnit = it.preferredUnit,
                fitnessLevel = it.fitnessLevel,
                // The destination device keeps its own credential. The backup never carried one.
                customApiKey = profileApiKey
            )
        }

        db.withTransaction {
            db.workoutDao().deleteAllSets()
            db.workoutDao().deleteAllSessions()
            db.savedRoutineDao().deleteAllRoutines()
            db.scheduleDao().deleteAllScheduleDays()
            db.bodyWeightDao().deleteAllWeightLogs()
            db.chatDao().deleteAllMessages()
            db.chatDao().deleteAllSessions()
            db.geminiSummaryDao().deleteAllSummaries()
            db.workoutAiSummaryDao().deleteAll()
            db.userProfileDao().deleteAllProfiles()
            db.exerciseDao().deleteAllUserExercises()
            if (conflictIds.isNotEmpty()) db.exerciseDao().deleteByIds(conflictIds)

            if (profile != null) db.userProfileDao().insertOrUpdate(profile)
            if (routines.isNotEmpty()) db.savedRoutineDao().insertRoutines(routines)
            if (exercises.isNotEmpty()) db.exerciseDao().upsertAll(exercises)
            if (sessions.isNotEmpty()) db.workoutDao().insertSessions(sessions)
            if (sets.isNotEmpty()) db.workoutDao().insertSets(sets)
            if (scheduleDays.isNotEmpty()) db.scheduleDao().insertScheduleDays(scheduleDays)
            if (weights.isNotEmpty()) db.bodyWeightDao().insertWeightLogs(weights)
            if (chats.isNotEmpty()) db.chatDao().insertSessions(chats)
            if (messages.isNotEmpty()) db.chatDao().insertMessages(messages)
            if (summaries.isNotEmpty()) db.geminiSummaryDao().insertSummaries(summaries)
            if (workoutSummaries.isNotEmpty()) db.workoutAiSummaryDao().insertAll(workoutSummaries)
        }
    }

    private suspend fun verifyImport(
        imported: UserDataSnapshot,
        destinationProfileKey: String,
        widgetPhotoPath: String?
    ) {
        val room = db.withTransaction {
            RoomPart(
                profile = db.userProfileDao().getProfileDirect(),
                exercises = db.exerciseDao().getAllExercisesList(),
                sessions = db.workoutDao().getAllSessionsOnce(),
                sets = db.workoutDao().getAllSets(),
                routines = db.savedRoutineDao().getAllRoutinesOnce(),
                scheduleDays = db.scheduleDao().getAllScheduleDaysOnce(),
                bodyWeightLogs = db.bodyWeightDao().getAllWeightLogsOnce(),
                chatSessions = db.chatDao().getAllSessionsOnce(),
                chatMessages = db.chatDao().getAllMessagesOnce(),
                geminiSummaries = db.geminiSummaryDao().getAllSummariesOnce(),
                workoutSummaries = db.workoutAiSummaryDao().getAllSummariesOnce()
            )
        }

        fun mismatch(label: String): Nothing = throw BackupException(
            "The restored $label did not match the backup, so the import was rolled back.",
            BackupErrorCode.INVALID_DATA
        )

        val expectedSessions = imported.sessions.map(BackupMapper::sessionFromDto).sortedBy { it.id }
        if (room.sessions.sortedBy { it.id } != expectedSessions) mismatch("workout history")
        val expectedSets = imported.sets.map(BackupMapper::setFromDto).sortedBy { it.id }
        if (room.sets.sortedBy { it.id } != expectedSets) mismatch("logged sets")
        val expectedRoutines = imported.routines.map(BackupMapper::routineFromDto).sortedBy { it.id }
        if (room.routines.sortedBy { it.id } != expectedRoutines) mismatch("routines")
        val expectedSchedule = imported.scheduleDays.map(BackupMapper::scheduleDayFromDto).sortedBy { it.dateStart }
        if (room.scheduleDays.sortedBy { it.dateStart } != expectedSchedule) mismatch("schedule")
        val expectedWeights = imported.bodyWeightLogs.map(BackupMapper::weightLogFromDto).sortedBy { it.id }
        if (room.bodyWeightLogs.sortedBy { it.id } != expectedWeights) mismatch("weigh-ins")
        val expectedChats = imported.chatSessions.map(BackupMapper::chatSessionFromDto).sortedBy { it.id }
        if (room.chatSessions.sortedBy { it.id } != expectedChats) mismatch("AI conversations")
        val expectedMessages = imported.chatMessages
            .map(BackupMapper::chatMessageFromDto)
            .sortedWith(compareBy({ it.sessionId }, { it.timestamp }, { it.id }))
        if (room.chatMessages != expectedMessages) mismatch("AI messages")
        val expectedSummaries = imported.geminiSummaries.map(BackupMapper::geminiSummaryFromDto).sortedBy { it.id }
        if (room.geminiSummaries.sortedBy { it.id } != expectedSummaries) mismatch("saved AI summaries")
        val expectedWorkoutSummaries = imported.workoutSummaries
            .map(BackupMapper::workoutSummaryFromDto)
            .sortedBy { it.id }
        if (room.workoutSummaries.sortedBy { it.id } != expectedWorkoutSummaries) {
            mismatch("post-workout summaries")
        }

        val expectedProfile = imported.roomProfile?.let {
            UserProfile(
                id = 1,
                name = it.name,
                heightCm = it.heightCm,
                currentWeightKg = it.currentWeightKg,
                targetWeightKg = it.targetWeightKg,
                age = it.age,
                preferredUnit = it.preferredUnit,
                fitnessLevel = it.fitnessLevel,
                customApiKey = destinationProfileKey
            )
        }
        if (room.profile != expectedProfile) mismatch("profile")

        val expectedExercises = imported.exercises.map(BackupMapper::exerciseFromDto).sortedBy { it.id }
        val actualExercises = room.exercises
            .asSequence()
            .filter { it.isCustom || it.isAiGenerated || it.id in imported.editedLibraryExerciseIds }
            .sortedBy { it.id }
            .toList()
        if (actualExercises != expectedExercises) mismatch("custom exercise library")

        val prefs = preferences.readSnapshot()
        if (prefs.dataStoreProfile != imported.dataStoreProfile) mismatch("profile preferences")
        if (prefs.preferences != imported.preferences) mismatch("preferences")
        val expectedPhotoPresent = imported.widgetPhoto != null
        // Hidden widget ids are a set; an older archive may store a different iteration order.
        if (prefs.dashboard.widgetOrder != imported.dashboard.widgetOrder ||
            prefs.dashboard.hiddenWidgetIds?.sorted() != imported.dashboard.hiddenWidgetIds?.sorted() ||
            prefs.dashboard.weeklyGoal != imported.dashboard.weeklyGoal ||
            prefs.dashboard.photoPresent != expectedPhotoPresent
        ) {
            mismatch("Home dashboard configuration")
        }
        if (!widgetPhotoPath.isNullOrBlank() && !WidgetPhotoStore.isUsable(widgetPhotoPath)) {
            mismatch("Home photo")
        }
        if (widgetPhotoPath.isNullOrBlank() && prefs.widgetPhotoPath.isNotBlank()) {
            mismatch("Home photo reference")
        }
    }

    private suspend fun rollbackAfterFailure(
        rollback: UserDataSnapshot,
        destinationProfileKey: String,
        trackerChanged: Boolean,
        databaseChanged: Boolean,
        stagedWidgetPhoto: String?,
        preferencesChanged: Boolean
    ): Boolean {
        var allSucceeded = true
        if (preferencesChanged) {
            try {
                preferences.apply(
                    profile = rollback.dataStoreProfile,
                    preferences = rollback.preferences,
                    dashboard = rollback.dashboard,
                    widgetPhotoPath = rollback.widgetPhotoPathOrNull()
                )
            } catch (rollbackFailure: Exception) {
                allSucceeded = false
            }
        }
        if (stagedWidgetPhoto != null && stagedWidgetPhoto != rollback.widgetPhotoPathOrNull()) {
            runCatching { File(stagedWidgetPhoto).delete() }
        }
        if (trackerChanged) {
            try {
                preferences.writeEditedLibraryExerciseIds(rollback.editedLibraryExerciseIds)
            } catch (rollbackFailure: Exception) {
                allSucceeded = false
            }
        }
        if (databaseChanged) {
            try {
                val conflicts = findLibraryNameConflicts(
                    db.exerciseDao().getAllExercisesList(),
                    rollback
                )
                replaceRoomData(rollback, destinationProfileKey, conflicts)
                DatasetSeeder.seedFresh(context, db.exerciseDao())
            } catch (rollbackFailure: Exception) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private fun UserDataSnapshot.widgetPhotoPathOrNull(): String? = widgetPhotoPath

    private data class RoomPart(
        val profile: UserProfile?,
        val exercises: List<Exercise>,
        val sessions: List<WorkoutSession>,
        val sets: List<LoggedSet>,
        val routines: List<SavedRoutine>,
        val scheduleDays: List<ScheduledWorkoutDay>,
        val bodyWeightLogs: List<BodyWeightLog>,
        val chatSessions: List<ChatSession>,
        val chatMessages: List<ChatMessageEntity>,
        val geminiSummaries: List<GeminiSummary>,
        val workoutSummaries: List<com.example.data.model.WorkoutAiSummary> = emptyList()
    )

    companion object {
        fun isoUtc(epochMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(epochMillis))

        fun suggestedFileName(epochMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }.let { formatter -> "IronPulse_Backup_${formatter.format(Date(epochMillis))}.${BackupFormat.EXTENSION}" }
    }
}
