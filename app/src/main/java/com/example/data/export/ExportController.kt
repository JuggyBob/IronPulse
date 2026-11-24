package com.example.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import coil.request.ImageRequest
import com.example.data.model.EffortDisplay
import com.example.data.model.EffortScale
import com.example.data.model.Exercise
import com.example.data.model.ExerciseInstructions
import com.example.data.model.ExerciseMedia
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SupersetRules
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.data.repository.FitnessRepository
import com.example.data.repository.PlanRepository
import com.example.util.IronPulseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportState {
    data object Idle : ExportState
    data class Choosing(val target: ExportTarget) : ExportState
    data class Building(val target: ExportTarget, val format: ExportFormat) : ExportState
    data class Ready(
        val target: ExportTarget,
        val format: ExportFormat,
        val document: ExportDocument,
        val bytes: ByteArray,
        val pdfPages: List<Bitmap> = emptyList(),
        val pdfFile: File? = null,
        val suggestedName: String
    ) : ExportState
    data class Error(val message: String) : ExportState
}

/**
 * Builds one immutable document and projects it into the selected format. The preview and the
 * saved file always come from this same object.
 */
class ExportController(
    private val context: Context,
    private val repository: FitnessRepository,
    private val planRepository: PlanRepository,
    private val scope: CoroutineScope,
    private val weightUnitProvider: () -> WeightUnit,
    private val effortDisplayProvider: () -> EffortDisplay
) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()
    private var buildJob: kotlinx.coroutines.Job? = null
    private var pdfTemp: File? = null

    fun open(target: ExportTarget) {
        buildJob?.cancel()
        buildJob = null
        cleanupTemp()
        _state.value = ExportState.Choosing(target)
    }

    fun close() {
        buildJob?.cancel()
        buildJob = null
        cleanupTemp()
        _state.value = ExportState.Idle
    }

    fun build(target: ExportTarget, format: ExportFormat) {
        buildJob?.cancel()
        cleanupTemp()
        _state.value = ExportState.Building(target, format)
        buildJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val document = when (target) {
                        is ExportTarget.Routine -> routineDocument(target.id)
                        is ExportTarget.Schedule -> scheduleDocument(target.id)
                        is ExportTarget.Workout -> workoutDocument(target.id)
                        is ExportTarget.ExerciseHistory -> exerciseHistoryDocument(target.exerciseName)
                    }
                    currentCoroutineContext().ensureActive()
                    val suggested = suggestedName(target, format)
                    when (format) {
                        ExportFormat.CSV -> {
                            val bytes = CsvWriter.write(document).toByteArray(Charsets.UTF_8)
                            _state.value = ExportState.Ready(target, format, document, bytes, suggestedName = suggested)
                        }
                        ExportFormat.XLSX -> {
                            val bytes = java.io.ByteArrayOutputStream().use { out ->
                                XlsxWriter.write(document, out)
                                out.toByteArray()
                            }
                            _state.value = ExportState.Ready(target, format, document, bytes, suggestedName = suggested)
                        }
                        ExportFormat.PDF -> {
                            val bytes = java.io.ByteArrayOutputStream().use { out ->
                                PdfWriter.write(document, out)
                                out.toByteArray()
                            }
                            val temp = File(context.cacheDir, "ironpulse-export-${System.currentTimeMillis()}.pdf")
                            FileOutputStream(temp).use { it.write(bytes) }
                            pdfTemp = temp
                            currentCoroutineContext().ensureActive()
                            _state.value = ExportState.Ready(
                                target = target,
                                format = format,
                                document = document,
                                bytes = bytes,
                                pdfPages = renderPdf(temp),
                                pdfFile = temp,
                                suggestedName = suggested
                            )
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = ExportState.Error(error.message ?: "The export could not be prepared.")
            }
        }
    }

    fun save(uri: Uri) {
        val ready = _state.value as? ExportState.Ready ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(ready.bytes)
                        out.flush()
                    } ?: error("The document provider did not open the file.")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = ExportState.Error(error.message ?: "The file could not be saved.")
            }
        }
    }

    private suspend fun routineDocument(id: Long): ExportDocument {
        val routine = planRepository.getRoutine(id) ?: error("The routine no longer exists.")
        val exercises = repository.getAllExercisesOnce()
        val items = RoutineParser.parseSingleWorkout(routine.exercisesJson, exercises)
        val byId = exercises.associateBy { it.id }
        val byName = exercises.associateBy { it.name.trim().lowercase(Locale.US) }
        val unit = weightUnitProvider()
        val effortDisplay = effortDisplayProvider()
        val partner = supersetPartners(items)
        val rows = items.map { item ->
            val exercise = byId[item.exerciseId] ?: byName[item.exerciseName.trim().lowercase(Locale.US)]
            listOf(
                item.exerciseName,
                item.targetSets.toString(),
                targetReps(item),
                targetLoad(item, unit),
                item.restSeconds.takeIf { it > 0 }?.let { "${it}s" }.orEmpty(),
                targetEffort(item, effortDisplay, exercise),
                partner[item.supersetGroupId]?.firstOrNull { it != item.exerciseName }.orEmpty()
            )
        }
        val table = ExportTable(
            name = "Routine",
            columns = listOf("Exercise", "Sets", "Reps", "Load", "Rest", "Target effort", "Superset"),
            rows = rows
        )
        val supersetGroups = items
            .filter { !it.supersetGroupId.isNullOrBlank() }
            .groupBy { it.supersetGroupId!! }
        val blocks = items.map { item ->
            val exercise = byId[item.exerciseId]
                ?: byName[item.exerciseName.trim().lowercase(Locale.US)]
            val instructions = exercise?.let {
                ExerciseInstructions.steps(it.instructions.ifBlank { it.description })
            }.orEmpty()
            val group = item.supersetGroupId?.let { supersetGroups[it].orEmpty() }.orEmpty()
            val position = group.indexOfFirst {
                it.exerciseId == item.exerciseId && it.exerciseName == item.exerciseName
            }
            val supersetLabel = if (group.size == 2 && position >= 0) {
                val label = if (position == 0) "A1" else "A2"
                val partnerName = group.firstOrNull {
                    !(it.exerciseId == item.exerciseId && it.exerciseName == item.exerciseName)
                }?.exerciseName
                "Superset $label" + (partnerName?.let { " · paired with $it" } ?: "")
            } else {
                null
            }
            ExportExerciseBlock(
                name = item.exerciseName,
                image = exercise?.let { loadImage(it) },
                instructions = instructions,
                sets = routineSetBullets(item, unit, effortDisplay, exercise),
                supersetLabel = supersetLabel,
                supersetGroupId = item.supersetGroupId?.takeIf { it.isNotBlank() }
            )
        }
        val sections = mutableListOf<ExportSection>()
        sections.add(ExportSection.Paragraph("Goal: ${routine.goal}"))
        if (routine.description.isNotBlank()) sections.add(ExportSection.Paragraph(routine.description))
        sections.add(ExportSection.Heading("Exercises"))
        if (blocks.isNotEmpty()) sections.add(ExportSection.ExerciseBlocks(blocks))
        return ExportDocument(
            title = routine.name,
            subtitle = "Saved routine • ${routine.goal}",
            sections = sections,
            tables = listOf(table)
        )
    }

    private suspend fun scheduleDocument(id: Long): ExportDocument {
        val schedule = planRepository.getSchedule(id) ?: error("The schedule no longer exists.")
        val slots = planRepository.getDaysOnce(id).associateBy { it.weekday }
        val routines = planRepository.getAllRoutinesOnce().associateBy { it.id }
        val exercises = repository.getAllExercisesOnce()
        val byId = exercises.associateBy { it.id }
        val byName = exercises.associateBy { it.name.trim().lowercase(Locale.US) }
        val unit = weightUnitProvider()
        val display = effortDisplayProvider()
        val rows = mutableListOf<List<String?>>()
        val sections = mutableListOf<ExportSection>()
        sections.add(ExportSection.Paragraph("Goal: ${schedule.goal}"))
        if (schedule.description.isNotBlank()) sections.add(ExportSection.Paragraph(schedule.description))
        for (weekday in 1..7) {
            val slot = slots[weekday]
            val routine = slot?.routineId?.let { routines[it] }
            val day = weekdayName(weekday)
            sections.add(ExportSection.Heading(day))
            if (routine == null) {
                sections.add(ExportSection.Paragraph("Rest day"))
                rows.add(listOf(day, "Rest day", "", "", "", "", "", "", ""))
                continue
            }
            sections.add(ExportSection.Paragraph(routine.name))
            val items = RoutineParser.parseSingleWorkout(routine.exercisesJson, exercises)
            val partner = supersetPartners(items)
            items.forEach { item ->
                val exercise = byId[item.exerciseId] ?: byName[item.exerciseName.trim().lowercase(Locale.US)]
                rows.add(
                    listOf(
                        day,
                        routine.name,
                        item.exerciseName,
                        item.targetSets.toString(),
                        targetReps(item),
                        targetLoad(item, unit),
                        item.restSeconds.takeIf { it > 0 }?.let { "${it}s" }.orEmpty(),
                        targetEffort(item, display, exercise),
                        partner[item.supersetGroupId]?.firstOrNull { it != item.exerciseName }.orEmpty()
                    )
                )
            }
            sections.add(ExportSection.Bullets(items.map { it.exerciseName }))
        }
        val table = ExportTable(
            name = "Schedule",
            columns = listOf("Day", "Routine", "Exercise", "Sets", "Reps", "Load", "Rest", "Target effort", "Superset"),
            rows = rows
        )
        sections.add(ExportSection.TableSection(table))
        return ExportDocument(
            title = schedule.name,
            subtitle = "Weekly schedule • ${schedule.goal}",
            sections = sections,
            tables = listOf(table)
        )
    }

    private suspend fun workoutDocument(id: Long): ExportDocument {
        val session = repository.getSessionWithSetsOnce(id) ?: error("The workout no longer exists.")
        val unit = weightUnitProvider()
        val display = effortDisplayProvider()
        val byExercise = session.sets.groupBy { it.exerciseName }
        val rows = session.sets.sortedWith(compareBy({ it.exerciseName }, { it.setNumber }, { it.dropIndex }))
            .map { set -> workoutRow(session.session.startTime, set, unit, display) }
        val table = ExportTable(
            name = "Workout",
            columns = listOf("Date", "Exercise", "Set", "Type", "Reps", "Load", "Rest", "Actual effort", "Superset"),
            rows = rows
        )
        val sections = mutableListOf<ExportSection>()
        sections.add(ExportSection.Paragraph("Date: ${date(session.session.startTime)}"))
        if (session.session.notes.isNotBlank()) {
            sections.add(ExportSection.Paragraph("Notes: ${session.session.notes}"))
        }
        sections.add(ExportSection.Heading("Exercises"))
        byExercise.forEach { (name, sets) ->
            sections.add(ExportSection.Heading(name))
            sections.add(ExportSection.Bullets(sets.sortedBy { it.setNumber }.map { set ->
                buildString {
                    append("Set ${set.setNumber}")
                    if (set.isDropSet) append(" drop ${set.dropIndex}")
                    if (set.isWarmup) append(" (warm-up)")
                    append(": ${WorkoutMetricsLine.load(set, unit)}")
                    set.effortRpe?.let { append(" • ${EffortScale.display(it, display, set.reps > 0)}") }
                }
            }))
        }
        sections.add(ExportSection.TableSection(table))
        return ExportDocument(
            title = session.session.title,
            subtitle = "Completed workout • ${date(session.session.startTime)}",
            sections = sections,
            tables = listOf(table)
        )
    }

    private suspend fun exerciseHistoryDocument(name: String): ExportDocument {
        val sets = repository.getSetsForExerciseList(name)
        val unit = weightUnitProvider()
        val display = effortDisplayProvider()
        val rows = mutableListOf<List<String?>>()
        sets.groupBy { it.sessionId }.toSortedMap(compareByDescending { it }).forEach { (sessionId, sessionSets) ->
            val session = repository.getSessionById(sessionId)
            sessionSets.sortedWith(compareBy({ it.setNumber }, { it.dropIndex })).forEach { set ->
                rows.add(
                    listOf(
                        date(session?.startTime ?: set.timestamp),
                        session?.title.orEmpty(),
                        if (set.isDropSet) "D${set.dropIndex}" else "Set ${set.setNumber}",
                        if (set.isWarmup) "Warm-up" else if (set.isDropSet) "Drop" else "Main",
                        set.reps.toString(),
                        WorkoutMetricsLine.load(set, unit),
                        set.distanceKm.takeIf { it > 0 }?.let { "${"%.2f".format(Locale.US, it)} km" }.orEmpty(),
                        set.effortRpe?.let { EffortScale.display(it, display, set.reps > 0) }.orEmpty(),
                        set.supersetGroupId?.takeIf { it.isNotBlank() }?.let { "A1·A2" }.orEmpty()
                    )
                )
            }
        }
        // XLSX serializes tables, not the document title. Name the sheet after the exercise so the
        // workbook identifies which history it contains; XlsxWriter trims it to Excel's 31-char limit.
        val table = ExportTable(
            name = name.ifBlank { "Exercise history" },
            columns = listOf("Date", "Session", "Set", "Type", "Reps", "Load", "Distance", "Actual effort", "Superset"),
            rows = rows
        )
        return ExportDocument(
            title = name,
            subtitle = "Exercise history",
            sections = listOf(
                ExportSection.Heading(name),
                ExportSection.Paragraph("${sets.size} recorded set(s) across ${sets.map { it.sessionId }.distinct().size} session(s)."),
                ExportSection.TableSection(table)
            ),
            tables = listOf(table)
        )
    }

    private fun workoutRow(
        sessionStart: Long,
        set: LoggedSet,
        unit: WeightUnit,
        display: EffortDisplay
    ): List<String?> = listOf(
        date(sessionStart),
        set.exerciseName,
        if (set.isDropSet) "D${set.dropIndex}" else set.setNumber.toString(),
        if (set.isWarmup) "Warm-up" else if (set.isDropSet) "Drop" else "Main",
        set.reps.toString(),
        WorkoutMetricsLine.load(set, unit),
        set.restSecondsOverride?.let { "${it}s" }.orEmpty(),
        set.effortRpe?.let { EffortScale.display(it, display, set.reps > 0) }.orEmpty(),
        set.supersetGroupId?.takeIf { it.isNotBlank() }?.let { "A1·A2" }.orEmpty()
    )

    private suspend fun loadImage(exercise: Exercise): ExportImage? {
        val model = ExerciseMedia.thumbnailModel(
            localUri = exercise.customMediaUri,
            localIsVideo = exercise.customMediaIsVideo,
            mediaPath = exercise.mediaPath
        ) ?: return null
        val result = withTimeoutOrNull(5_000L) {
            IronPulseApp.stillFrames(context).execute(
                ImageRequest.Builder(context)
                    .data(model)
                    .allowHardware(false)
                    .build()
            )
        } ?: return null
        val bitmap = (result as? coil.request.SuccessResult)?.drawable
            ?.let { it as? BitmapDrawable }
            ?.bitmap
            ?: return null
        return ExportImage(
            bitmap = bitmap,
            attribution = if (exercise.isCustom) null else ExerciseMedia.ATTRIBUTION
        )
    }

    private fun renderPdf(file: File): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                // Preview only a bounded subset at native resolution. The saved PDF still contains
                // every page; retaining 2x bitmaps for a long schedule can consume hundreds of MB.
                val previewPages = minOf(renderer.pageCount, 8)
                for (index in 0 until previewPages) {
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        pages.add(bitmap)
                    }
                }
            }
        }
        return pages
    }

    private fun cleanupTemp() {
        pdfTemp?.delete()
        pdfTemp = null
    }

    private fun suggestedName(target: ExportTarget, format: ExportFormat): String {
        val label = when (target) {
            is ExportTarget.Routine -> "Routine_${target.id}"
            is ExportTarget.Schedule -> "Schedule_${target.id}"
            is ExportTarget.Workout -> "Workout_${target.id}"
            is ExportTarget.ExerciseHistory -> target.exerciseName.replace(Regex("[^A-Za-z0-9_-]+"), "_")
        }
        return "IronPulse_${label}_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.${format.extension}"
    }

    private fun date(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

    private fun weekdayName(day: Int): String = when (day) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        else -> "Sunday"
    }

    private fun targetReps(item: RoutineExerciseItem): String = when {
        item.targetHoldSeconds > 0 -> "${item.targetHoldSeconds}s"
        else -> item.targetReps.toString()
    }

    private fun targetLoad(item: RoutineExerciseItem, unit: WeightUnit): String {
        val weight = when {
            item.targetAddedWeightKg > 0 -> WeightConversion.fromKg(item.targetAddedWeightKg, unit)
            item.targetWeight > 0 -> WeightConversion.fromKg(item.targetWeight, unit)
            else -> return ""
        }
        return "${"%.1f".format(Locale.US, weight)} ${unit.suffix}"
    }

    private fun targetEffort(
        item: RoutineExerciseItem,
        display: EffortDisplay,
        exercise: Exercise?
    ): String {
        val repBased = exercise?.let { EffortScale.supportsRir(it) }
            ?: (item.targetReps > 0)
        return EffortScale.labelForTarget(item.targetEffortRpe, display, repBased)
    }

    private fun routineSetBullets(
        item: RoutineExerciseItem,
        unit: WeightUnit,
        display: EffortDisplay,
        exercise: Exercise?
    ): List<ExportSetBullet> {
        val result = mutableListOf<ExportSetBullet>()
        val effort = targetEffort(item, display, exercise)
            .takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
        for (setNumber in 1..item.targetSets) {
            val measures = buildList {
                if (item.targetReps > 0) add("${item.targetReps} reps")
                if (item.targetHoldSeconds > 0) add("${item.targetHoldSeconds}s")
                if (item.targetDistanceKm > 0) {
                    add("%.2f km".format(Locale.US, item.targetDistanceKm))
                }
                targetLoad(item, unit).takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" · ")
            result += ExportSetBullet(
                text = "Set $setNumber: " + measures.ifBlank { "No target" } +
                    (effort?.let { " · $it" } ?: ""),
                isDrop = false
            )
            item.dropSets
                .filter { it.mainSetIndex == setNumber }
                .sortedBy { it.dropIndex }
                .forEach { drop ->
                    result += ExportSetBullet(
                        text = "Drop ${drop.dropIndex}: " + dropSummary(drop, unit),
                        isDrop = true
                    )
                }
        }
        return result
    }

    private fun dropSummary(drop: com.example.data.model.RoutineDropSet, unit: WeightUnit): String {
        val parts = buildList {
            if (drop.targetReps > 0) add("${drop.targetReps} reps")
            if (drop.targetHoldSeconds > 0) add("${drop.targetHoldSeconds}s")
            if (drop.targetDistanceKm > 0) {
                add("%.2f km".format(Locale.US, drop.targetDistanceKm))
            }
            val loadKg = when {
                drop.targetAddedWeightKg > 0 -> drop.targetAddedWeightKg
                drop.targetWeight > 0 -> drop.targetWeight
                else -> 0.0
            }
            if (loadKg > 0) {
                add("%.1f %s".format(Locale.US, WeightConversion.fromKg(loadKg, unit), unit.suffix))
            }
        }
        return parts.joinToString(" · ").ifBlank { "No target" }
    }

    private fun supersetPartners(items: List<RoutineExerciseItem>): Map<String, List<String>> =
        items.filter { !it.supersetGroupId.isNullOrBlank() }
            .groupBy { it.supersetGroupId!! }
            .mapValues { (_, rows) -> rows.map { it.exerciseName } }

    /** Formatting helper kept local so CSV/PDF/XLSX share exactly the same load text. */
    private object WorkoutMetricsLine {
        fun load(set: LoggedSet, unit: WeightUnit): String {
            val weight = when {
                set.addedWeight > 0 -> set.addedWeight
                set.weight > 0 -> set.weight
                else -> return if (set.reps > 0) "${set.reps} reps" else ""
            }
            return "${"%.1f".format(Locale.US, WeightConversion.fromKg(weight, unit))} ${unit.suffix}"
        }
    }
}
