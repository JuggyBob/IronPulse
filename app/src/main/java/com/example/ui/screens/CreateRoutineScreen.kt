package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.ExerciseMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DistanceFormat
import com.example.data.model.Exercise
import com.example.data.model.EffortDisplay
import com.example.data.model.EffortScale
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseFieldCatalog
import com.example.data.model.ExerciseFieldValues
import com.example.data.model.ExerciseSearch
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineDropSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineMainSetDefault
import com.example.data.model.RoutineDayClamp
import com.example.data.model.RoutineParser
import com.example.data.model.sanitizedFor
import com.example.data.model.SavedRoutine
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.data.model.Weekday
import com.example.ui.components.AiUnavailableDialog
import com.example.ui.components.AnimatedDropdownField
import com.example.ui.components.DayDropdown
import com.example.ui.components.CustomExerciseDialog
import com.example.ui.components.DropSetBranchConnector
import com.example.ui.components.DropSetBranchLeadIn
import com.example.ui.components.DropSetRowVisibility
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.components.WeightInputField
import com.example.ui.theme.CoralOrange
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.OnAccent
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ExerciseMediaStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Training goals offered by the routine builder. The first entry is also the default so the
 * drop down always opens on a selected goal.
 */
private val GOAL_OPTIONS = listOf(
    "Strength & Muscle Growth",
    "Strength & Power",
    "Hypertrophy & Muscle Growth",
    "Calisthenics Mastery",
    "Conditioning & Fat Loss"
)

@Composable
fun CreateRoutineScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRoutineSaved: () -> Unit,
    editingRoutine: SavedRoutine? = null,
    modifier: Modifier = Modifier
) {
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val userCreatedExercises by viewModel.userCreatedExercises.collectAsStateWithLifecycle()
    val aiCreationState by viewModel.aiExerciseCreationState.collectAsStateWithLifecycle()
    val aiErrorDialog by viewModel.aiErrorDialog.collectAsStateWithLifecycle()
    val autoplayExerciseDemos by viewModel.autoplayExerciseDemos.collectAsStateWithLifecycle()
    // The read-only exercise sheet opened by tapping an exercise name in the routine list.
    var exerciseShowingDetails by remember { mutableStateOf<Exercise?>(null) }
    val customExerciseResult by viewModel.customExerciseResult.collectAsStateWithLifecycle()
    // Weights live in kilograms; this is the unit the athlete reads and types in.
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val exerciseDefaultRests by viewModel.exerciseDefaultRests.collectAsStateWithLifecycle()
    // Where the athlete's week begins. It decides the order the seven days are shown in, and where
    // a sequence of workouts with no weekday of its own starts.
    val weekStartsOnMonday by viewModel.weekStartFlow.collectAsStateWithLifecycle()

    val isEditing = editingRoutine != null

    var routineName by remember { mutableStateOf(editingRoutine?.name.orEmpty()) }
    var routineDescription by remember { mutableStateOf(editingRoutine?.description.orEmpty()) }
    var selectedGoal by remember { mutableStateOf(editingRoutine?.goal ?: "Strength & Muscle Growth") }

    // Search query with auto-completion
    var searchQuery by remember { mutableStateOf("") }
    // The weekday the athlete is editing, and the one new exercises land on.
    var activeDayForAdding by remember { mutableIntStateOf(Weekday.MONDAY) }
    var goalMenuExpanded by remember { mutableStateOf(false) }

    // "The exercise does not exist yet" flow
    var showCustomExerciseDialog by remember { mutableStateOf(false) }
    var prefillCustomExerciseName by remember { mutableStateOf("") }
    var exerciseBeingEdited by remember { mutableStateOf<Exercise?>(null) }

    // Gallery picker state for the exercise created here. The id only exists once the row is saved,
    // so the picker is deferred to the moment the save result arrives.
    var pendingMediaExerciseId by remember { mutableStateOf<Long?>(null) }
    var pendingMediaAfterCreate by remember { mutableStateOf(false) }
    var mediaImportFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            val targetId = pendingMediaExerciseId
            pendingMediaExerciseId = null
            if (uri == null || targetId == null) return@rememberLauncherForActivityResult
            // The picker's own uri is only readable while the picker is alive, so the bytes are
            // copied into the app's files here and only our own path is stored.
            // The video flag comes from the mime type: a content uri from the picker usually has no
            // extension, and guessing wrong would try to play a photo as a video.
            val mimeType = context.contentResolver.getType(uri)
            val isVideo = mimeType?.startsWith("video/") == true
            val localPath = ExerciseMediaStore.import(context, targetId, uri, mimeType)
            if (localPath != null) {
                viewModel.attachExerciseMedia(targetId, "file://$localPath", isVideo)
            } else {
                mediaImportFailed = true
            }
        }
    )
    var aiCreatedBannerName by remember { mutableStateOf<String?>(null) }
    var aiCreatedBannerToken by remember { mutableIntStateOf(0) }

    // The picker's media could not be copied, so nothing was attached. Said out loud rather than
    // failing quietly, because a blank media box with no explanation looks like the feature is
    // broken in a way the athlete cannot tell apart from a pick that did nothing.
    if (mediaImportFailed) {
        AlertDialog(
            onDismissRequest = { mediaImportFailed = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Could not add that media",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    modifier = Modifier.testTag("routine_media_import_failed_title")
                )
            },
            text = {
                Text(
                    text = "The file could not be read from your gallery, so no media was attached. " +
                        "Try picking it again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { mediaImportFailed = false },
                    modifier = Modifier.testTag("routine_media_import_failed_dismiss")
                ) {
                    Text("OK", color = ElectricCyan)
                }
            }
        )
    }

    // Ordered list of routine exercises
    val selectedRoutineExercises = remember { mutableStateListOf<RoutineExerciseItem>() }

    // Which exercise has its added weight panel open, if any. Tapping the name of an exercise
    // opens it, and only one is open at a time.
    var expandedAddedWeightIndex by remember { mutableStateOf<Int?>(null) }

    // Prefill with the exercises of the routine being edited. This waits for the exercise catalog
    // so every item keeps its real database id (otherwise the routine would only keep names).
    var didSeedEditedRoutine by remember(editingRoutine?.id) { mutableStateOf(false) }
    LaunchedEffect(editingRoutine?.id, allExercises) {
        val routine = editingRoutine ?: return@LaunchedEffect
        if (didSeedEditedRoutine || allExercises.isEmpty()) return@LaunchedEffect
        val existing = routine.exercisesJson.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val parsed = RoutineParser.parseExercises(existing, allExercises, weekStartsOnMonday)
        selectedRoutineExercises.clear()
        selectedRoutineExercises.addAll(parsed)
        didSeedEditedRoutine = true
    }

    // Auto-complete suggestions: the SAME character-aware ranking as the "Log workout" search,
    // over the whole catalog (no arbitrary cap), scrolled inside the drop down.
    val searchResults = remember(searchQuery, allExercises) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            ExerciseSearch.rank(allExercises, searchQuery)
        }
    }

    val canSave = routineName.isNotBlank() && selectedRoutineExercises.isNotEmpty()

    // How many exercises each weekday holds, so the planner shows the shape of the week at a
    // glance and a day with nothing in it reads as a rest day.
    val exercisesPerWeekday = remember(selectedRoutineExercises.toList()) {
        selectedRoutineExercises.groupingBy { it.weekday }.eachCount()
    }

    // The days of the week, in the order the athlete's week runs in.
    val weekdayOrder = remember(weekStartsOnMonday) { Weekday.ordered(weekStartsOnMonday) }

    // An exercise must always sit on a day of the week. Only data written before the seven day
    // model existed can break that, and it is repaired here rather than left invisible.
    LaunchedEffect(selectedRoutineExercises.toList()) {
        val clamped = RoutineDayClamp.clampExercises(selectedRoutineExercises.toList())
        clamped.forEachIndexed { index, item ->
            if (selectedRoutineExercises[index] != item) {
                selectedRoutineExercises[index] = item
            }
        }
        activeDayForAdding = RoutineDayClamp.clampDay(activeDayForAdding)
    }

    // The added exercises are shown one day of the week at a time, chosen in the week planner.
    // Each entry keeps its real index in the whole list, so reordering and deleting still address
    // the right element.
    //
    // Deliberately not `remember`ed off the list itself: a SnapshotStateList is mutable in place,
    // so a remembered key list does not change identity when an exercise is added and the view
    // would not recompute. That was the reason an exercise added to the day being viewed only
    // appeared after switching days away and back.
    val visibleRoutineEntries = selectedRoutineExercises
        .mapIndexed { index, item -> index to item }
        .filter { (_, item) -> item.weekday == activeDayForAdding }

    fun persistRoutine() {
        if (!canSave) return

        // Last line of defence: never persist an exercise on a day that is not part of the week.
        val clamped = RoutineDayClamp.clampExercises(selectedRoutineExercises.toList())
            .map { item ->
                val fields = allExercises.firstOrNull {
                    it.id == item.exerciseId ||
                        it.name.equals(item.exerciseName, ignoreCase = true)
                }?.exerciseFields
                if (fields == null) item else item.sanitizedFor(fields)
            }
        val trainingDays = clamped.map { it.weekday }.distinct().size.coerceAtLeast(1)

        if (isEditing) {
            viewModel.saveEditedRoutine(
                SavedRoutine(
                    id = editingRoutine.id,
                    name = routineName.trim(),
                    description = routineDescription.trim(),
                    daysPerWeek = trainingDays,
                    goal = selectedGoal,
                    exercisesJson = RoutineParser.serializeExercises(clamped),
                    createdAt = editingRoutine.createdAt
                )
            )
        } else {
            viewModel.saveCustomRoutine(
                name = routineName,
                description = routineDescription,
                daysPerWeek = trainingDays,
                goal = selectedGoal,
                exercises = clamped
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        color = DarkBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        // Green confirmation banner: overlays the top, it never pushes the page down.
        AnimatedVisibility(
            visible = aiCreatedBannerName != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, MintGreen.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("create_routine_ai_exercise_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MintGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exercise ready",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MintGreen
                    )
                    Text(
                        text = aiCreatedBannerName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // === TOP APP BAR ===
            Surface(
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_create_routine_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = TextMuted
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isEditing) "Edit Routine" else "Create New Routine",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Text(
                            text = "${selectedRoutineExercises.size} exercises added",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedRoutineExercises.isNotEmpty()) MintGreen else TextMuted
                        )
                    }

                    Button(
                        onClick = {
                            persistRoutine()
                            onRoutineSaved()
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintGreen,
                            contentColor = OnAccent,
                            disabledContainerColor = DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_routine_confirm_button")
                    ) {
                        Text(
                            text = if (isEditing) "Update" else "Save",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (canSave) OnAccent else TextMuted
                        )
                    }
                }
            }

            // === MAIN SCROLLABLE CONTENT ===
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Routine Information
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "ROUTINE DETAILS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextMuted
                            )

                            OutlinedTextField(
                                value = routineName,
                                onValueChange = { routineName = it },
                                label = { Text("Routine Name *") },
                                placeholder = { Text("e.g. Upper Body Strength & Pull") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("routine_name_input")
                            )

                            // Goal selection: same animated drop down as the "create new exercise" equipment / muscle pickers
                            AnimatedDropdownField(
                                label = "Goal Strategy *",
                                value = selectedGoal,
                                icon = Icons.Default.Flag,
                                accent = ElectricCyan,
                                options = GOAL_OPTIONS,
                                expanded = goalMenuExpanded,
                                onExpandedChange = { goalMenuExpanded = it },
                                onOptionSelected = { selectedGoal = it },
                                fieldTestTag = "create_routine_goal_dropdown",
                                optionTestTagPrefix = "create_routine_goal_option"
                            )

                            // The week planner: the seven days of the week, always.
                            //
                            // A routine is a week. Every weekday has a slot, a day with nothing in
                            // it is a rest day, and tapping a day is how the athlete moves between
                            // them. The days are shown in the order the athlete's week runs in.
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "WEEK SCHEDULE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ElectricCyan
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    weekdayOrder.forEach { day ->
                                        val count = exercisesPerWeekday[day] ?: 0
                                        val isSelected = day == activeDayForAdding
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSelected) ElectricCyan else DarkBg,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) ElectricCyan else DarkBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { activeDayForAdding = day }
                                                .padding(vertical = 6.dp)
                                                .testTag("routine_day_chip_$day"),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            Text(
                                                text = Weekday.shortName(day),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) OnAccent else TextWhite,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = if (count > 0) "$count" else "Rest",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) OnAccent else TextMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "A day with no exercise is a rest day. Tap a day to open it, " +
                                        "and new exercises land on the day you tap.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }

                            // Description
                            OutlinedTextField(
                                value = routineDescription,
                                onValueChange = { routineDescription = it },
                                label = { Text("Description / Coaching Notes (Optional)") },
                                placeholder = { Text("e.g. Focus on explosive pull-ups and strict form dips...") },
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("routine_description_input")
                            )
                        }
                    }
                }

                // Section 2: Search & Add Exercises from Database
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Section Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ADD EXERCISES FROM LOCAL DATABASE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                            }

                            // Dedicated Assign to Day Selector
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Assign added exercises to:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextWhite
                                    )

                                    // The day new exercises land on, and the day the list below
                                    // shows. The week planner above does the same job, but the
                                    // dropdown names the day in full.
                                    DayDropdown(
                                        selectedDay = activeDayForAdding,
                                        onSelect = { activeDayForAdding = it },
                                        weekStartsOnMonday = weekStartsOnMonday,
                                        exerciseCount = exercisesPerWeekday
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search 800+ exercises", color = TextMuted) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = ElectricCyan
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = TextMuted
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("exercise_search_input")
                            )

                            // Instant auto-complete suggestions: scrolls INSIDE the dropdown so the page never scrolls
                            AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkBg, RoundedCornerShape(10.dp))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                        .padding(8.dp)
                                        .testTag("create_routine_search_dropdown")
                                ) {
                                    Text(
                                        text = "Search Suggestions (${searchResults.size}):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 230.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        searchResults.forEach { exercise ->
                                            val isAlreadyAdded = selectedRoutineExercises.any { it.exerciseId == exercise.id }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .background(DarkSurface, RoundedCornerShape(8.dp))
                                                    .border(1.dp, if (isAlreadyAdded) MintGreen.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = exercise.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = TextWhite,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${exercise.equipment} • ${exercise.category}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextMuted
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        selectedRoutineExercises.add(
                                                            defaultRoutineItemFor(
                                                                exercise,
                                                                activeDayForAdding,
                                                                exerciseDefaultRests[exercise.id] ?: 0
                                                            )
                                                        )
                                                        searchQuery = "" // Clear after adding
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isAlreadyAdded) DarkSurfaceVariant else ElectricCyan,
                                                        contentColor = if (isAlreadyAdded) MintGreen else OnAccent
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("add_exercise_${exercise.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isAlreadyAdded) Icons.Default.Check else Icons.Default.Add,
                                                        contentDescription = null,
                                                        tint = if (isAlreadyAdded) MintGreen else OnAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isAlreadyAdded) "Add More" else "Add",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isAlreadyAdded) MintGreen else OnAccent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Nothing in the catalog matches: offer to let the AI create it or to
                            // create it manually, right here inside the routine builder.
                            if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
                                NoExerciseFoundCard(
                                    searchedName = searchQuery.trim(),
                                    isAiWorking = aiCreationState is MainViewModel.AiExerciseCreationState.Working,
                                    aiErrorMessage = (aiCreationState as? MainViewModel.AiExerciseCreationState.Failed)?.message,
                                    onAskAi = {
                                        viewModel.resetAiExerciseCreationState()
                                        viewModel.createSingleExerciseWithAI(searchQuery)
                                    },
                                    onCreateIt = {
                                        viewModel.consumeCustomExerciseResult()
                                        prefillCustomExerciseName = searchQuery.trim()
                                        showCustomExerciseDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 3: Selected Exercises in Order
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ROUTINE EXERCISES (${selectedRoutineExercises.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        if (selectedRoutineExercises.isNotEmpty()) {
                            Text(
                                text = "Use arrows to reorder",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                if (selectedRoutineExercises.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No exercises added yet",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Type in the search bar above to find and add exercises from the 800+ database, the AI registered ones and your own custom exercises, in your desired sequence.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // The list shows one training day at a time, picked with the dropdown above:
                    // reordering and deleting then only ever touch the visible day.
                    itemsIndexed(
                        items = visibleRoutineEntries,
                        key = { _, entry -> "${entry.second.exerciseId}_${entry.first}" }
                    ) { visibleIndex, entry ->
                        val index = entry.first
                        val item = entry.second
                        SelectedRoutineExerciseRow(
                            index = visibleIndex,
                            item = item,
                            unit = unit,
                            expanded = expandedAddedWeightIndex == visibleIndex,
                            supportedFields = allExercises.firstOrNull {
                                it.id == item.exerciseId ||
                                    it.name.equals(item.exerciseName, ignoreCase = true)
                            }?.exerciseFields ?: ExerciseFieldCatalog.defaultsForType(item.exerciseType),
                            canMoveUp = visibleIndex > 0,
                            canMoveDown = visibleIndex < visibleRoutineEntries.size - 1,
                            onMoveUp = {
                                if (index > 0) {
                                    val temp = selectedRoutineExercises[index]
                                    selectedRoutineExercises[index] = selectedRoutineExercises[index - 1]
                                    selectedRoutineExercises[index - 1] = temp
                                }
                            },
                            onMoveDown = {
                                if (index < selectedRoutineExercises.size - 1) {
                                    val temp = selectedRoutineExercises[index]
                                    selectedRoutineExercises[index] = selectedRoutineExercises[index + 1]
                                    selectedRoutineExercises[index + 1] = temp
                                }
                            },
                            onToggleExpanded = {
                                // One panel at a time: the athlete is editing one exercise, and
                                // two open panels on a phone screen is one too many.
                                expandedAddedWeightIndex =
                                    if (expandedAddedWeightIndex == visibleIndex) null else visibleIndex
                            },
                            onShowDetails = {
                                allExercises.firstOrNull {
                                    it.id == item.exerciseId ||
                                        it.name.equals(item.exerciseName, ignoreCase = true)
                                }?.let { exerciseShowingDetails = it }
                            },
                            onUpdate = { updated ->
                                selectedRoutineExercises[index] = updated
                            },
                            onDelete = {
                                selectedRoutineExercises.removeAt(index)
                            }
                        )
                    }

                    // A day with nothing on it yet, so the view is never silently blank
                    if (visibleRoutineEntries.isEmpty()) {
                        item {
                            Text(
                                text = "${Weekday.name(activeDayForAdding)} is a rest day in this routine. " +
                                    "Add an exercise above and it lands here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Bottom Save Button
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            persistRoutine()
                            onRoutineSaved()
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintGreen,
                            contentColor = OnAccent,
                            disabledContainerColor = DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_routine_bottom_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (canSave) OnAccent else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditing) "Update Workout Routine" else "Save Workout Routine",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (canSave) OnAccent else TextMuted
                        )
                    }
                }
            }
        }

            // The AI finished (or failed): show the outcome and add the new exercise straight away.
            LaunchedEffect(aiCreationState) {
            when (val state = aiCreationState) {
                is MainViewModel.AiExerciseCreationState.Created -> {
                    aiCreatedBannerName = state.exercise.name
                    aiCreatedBannerToken++
                    searchQuery = ""
                    // Add it to the routine straight away, it is exactly what the user searched for
                    selectedRoutineExercises.add(
                        defaultRoutineItemFor(
                            state.exercise,
                            activeDayForAdding,
                            exerciseDefaultRests[state.exercise.id] ?: 0
                        )
                    )
                    viewModel.resetAiExerciseCreationState()
                }
                is MainViewModel.AiExerciseCreationState.Failed -> {
                    viewModel.dismissAiError()
                    // The message stays on screen: it is the athlete's only sign that Gemini refused
                    // and that nothing was added to the routine.
                }
                else -> Unit
            }
        }

        // A different search means the previous refusal no longer applies
        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotBlank()) {
                viewModel.resetAiExerciseCreationState()
            }
        }

        LaunchedEffect(aiCreatedBannerToken) {
            if (aiCreatedBannerName != null) {
                delay(AI_BANNER_DURATION_MS)
                aiCreatedBannerName = null
            }
        }

        // Manual creation of the missing exercise (same dialog as the "Log Workout" page)
        if (showCustomExerciseDialog) {
            // Hoisted out of the argument list: the callbacks below outlive this recomposition, and a
            // delegated var cannot be smart cast inside them.
            val editing = exerciseBeingEdited
            // The gallery picker, so media can be attached while creating the exercise and not only
            // afterwards. Without it this screen could only ever produce exercises with the built-in
            // animation, which is the gap the athlete reported.
            //
            // A new exercise has no id until it is saved, so the picker is launched once the save
            // completes and the new id is remembered for it.
            val canOwnMedia = editing == null || ExerciseMedia.isUserOwnedMedia(editing.isCustom)
            CustomExerciseDialog(
                existingExercise = exerciseBeingEdited,
                prefillName = prefillCustomExerciseName,
                findSimilar = { viewModel.findSimilarExercise(it) },
                onSave = { name, category, equipment, exerciseType, description, supportedFields ->
                    viewModel.createCustomExercise(
                        name, category, equipment, exerciseType, description, supportedFields
                    )
                },
                onDismiss = { showCustomExerciseDialog = false },
                onAttachMedia = if (canOwnMedia) {
                    {
                        showCustomExerciseDialog = false
                        pendingMediaAfterCreate = true
                    }
                } else {
                    null
                },
                onRemoveMedia = if (editing != null && ExerciseMedia.isUserOwnedMedia(editing.isCustom)) {
                    { viewModel.clearExerciseMedia(editing.id) }
                } else {
                    null
                },
                hasAttachedMedia = editing?.customMediaUri?.isNotBlank() == true
            )
        }

        // The gallery picker for the exercise just created here. Registered at this level rather than
        // inside the dialog so the dialog stays a plain form, and launched once the save lands,
        // because attaching needs an id and a brand new exercise only gets one when it is stored.
        LaunchedEffect(pendingMediaAfterCreate, customExerciseResult) {
            if (!pendingMediaAfterCreate) return@LaunchedEffect
            when (val result = customExerciseResult) {
                is MainViewModel.CustomExerciseResult.Success -> {
                    pendingMediaExerciseId = result.exercise.id
                    pendingMediaAfterCreate = false
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
                // Nothing was stored, so there is nothing to attach to. Leave the dialog's saved
                // wording alone rather than silently reopening it.
                is MainViewModel.CustomExerciseResult.Duplicate,
                is MainViewModel.CustomExerciseResult.Error,
                null -> pendingMediaAfterCreate = false
            }
        }

        // The new exercise is stored: add it to this routine just like the AI generated one
        LaunchedEffect(customExerciseResult) {
            when (val result = customExerciseResult) {
                is MainViewModel.CustomExerciseResult.Success -> {
                    val exercise = result.exercise
                    if (selectedRoutineExercises.none { it.exerciseId == exercise.id }) {
                        selectedRoutineExercises.add(
                            defaultRoutineItemFor(
                                exercise,
                                activeDayForAdding,
                                exerciseDefaultRests[exercise.id] ?: 0
                            )
                        )
                    }
                    aiCreatedBannerName = exercise.name
                    aiCreatedBannerToken++
                    showCustomExerciseDialog = false
                    searchQuery = ""
                    viewModel.consumeCustomExerciseResult()
                }
                is MainViewModel.CustomExerciseResult.Duplicate -> {
                    showCustomExerciseDialog = false
                    searchQuery = result.existing.name
                    viewModel.consumeCustomExerciseResult()
                }
                else -> Unit
            }
        }

        // Read-only exercise detail, opened from a routine exercise name. No pen (fields are not
        // edited from here) and no Add Set (this is a routine, not a running workout).
        exerciseShowingDetails?.let { detailExercise ->
            ExerciseDetailDialog(
                exercise = detailExercise,
                unit = unit.suffix,
                onAddSet = null,
                onDismiss = { exerciseShowingDetails = null },
                autoplayDemos = autoplayExerciseDemos,
                onEdit = null
            )
        }

        aiErrorDialog?.let { errorState ->
                AiUnavailableDialog(
                    title = errorState.title,
                    message = errorState.message,
                    onDismiss = { viewModel.dismissAiError() }
                )
            }
        }
    }
}

/** How long the green "exercise ready" banner stays on screen. */
private const val AI_BANNER_DURATION_MS = 2600L

/**
 * Shown when the search finds nothing in the whole catalog.
 *
 * Both actions are highlighted: "Ask AI" lets Gemini fill in every field of the exercise, "Create
 * it" opens the same dialog the "Log Workout" page uses.
 */
@Composable
private fun NoExerciseFoundCard(
    searchedName: String,
    isAiWorking: Boolean,
    aiErrorMessage: String?,
    onAskAi: () -> Unit,
    onCreateIt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(DarkBg, RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .testTag("no_exercise_found_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = "Exercise does not exist yet.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = TextWhite,
            textAlign = TextAlign.Center
        )
        Text(
            text = "\"$searchedName\" is not in the database yet.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Gemini refused: the athlete is told why instead of the link silently doing nothing
        if (aiErrorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FireRed.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .border(1.dp, FireRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("ask_ai_exercise_error"),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = FireRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = aiErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = FireRed,
                    textAlign = TextAlign.Start
                )
            }
        }

        // Ask AI (blue) ... Or ... Create it (blue)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ask AI",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isAiWorking) TextMuted else ElectricCyan,
                modifier = Modifier
                    .clickable(enabled = !isAiWorking) { onAskAi() }
                    .testTag("ask_ai_for_exercise_link")
            )
            Text(
                text = "  •  ",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Text(
                text = "Create it",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan,
                modifier = Modifier
                    .clickable { onCreateIt() }
                    .testTag("create_missing_exercise_link")
            )
        }

        Text(
            text = if (isAiWorking) {
                "Your AI provider is filling in the exercise fields..."
            } else {
                "Ask AI to generate it, or create it yourself."
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isAiWorking) ElectricCyan else TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A sensible first target for a newly added exercise, using its explicit field configuration.
 *
 * The old fixed "3 x 8" gave a Plank a rep target it cannot record. Hold and timed work start at
 * 30s/10min, and distance work at 1 km, so the routine is editable without a zero staring back.
 */
internal fun defaultRoutineItemFor(
    exercise: Exercise,
    weekday: Int,
    defaultRestSeconds: Int = 0
): RoutineExerciseItem {
    val fields = exercise.exerciseFields
    val hold = when {
        ExerciseField.HOLD_SECONDS in fields -> 30
        ExerciseField.TIME_SECONDS in fields -> 600
        else -> 0
    }
    val item = RoutineExerciseItem(
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        exerciseType = exercise.exerciseType,
        category = exercise.category,
        equipment = exercise.equipment,
        targetSets = 3,
        targetReps = if (ExerciseField.REPS in fields) 8 else 0,
        targetHoldSeconds = hold,
        targetDistanceKm = if (ExerciseField.DISTANCE_KM in fields) 1.0 else 0.0,
        restSeconds = defaultRestSeconds.coerceIn(0, 600),
        weekday = weekday
    )
    return item.copy(prescription = RoutineParser.buildPrescription(item))
}

@Composable
fun SelectedRoutineExerciseRow(
    index: Int,
    item: RoutineExerciseItem,
    unit: WeightUnit,
    expanded: Boolean,
    supportedFields: List<ExerciseField> = emptyList(),
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleExpanded: () -> Unit,
    /** Opens the same read-only exercise sheet the workout uses. */
    onShowDetails: () -> Unit,
    onUpdate: (RoutineExerciseItem) -> Unit,
    onDelete: () -> Unit,
    isSuperset: Boolean = false,
    showSupersetAction: Boolean = true,
    onSuperset: () -> Unit = {},
    effortDisplay: EffortDisplay = EffortDisplay.RIR
) {
    val fields = supportedFields.ifEmpty {
        ExerciseFieldCatalog.defaultsForType(item.exerciseType)
    }
    // The one load value that is always visible. Added weight keeps its + prefix and colour;
    // standard-weight work now shows its working load too, instead of leaving the card silent.
    val loadBadge: Triple<String, Color, String>? = when {
        ExerciseField.ADDED_WEIGHT in fields && item.targetAddedWeightKg > 0.0 ->
            Triple(
                "+${formatRoutineWeight(item.targetAddedWeightKg, unit)} ${unit.suffix}",
                CoralOrange,
                "routine_added_weight_badge_$index"
            )
        ExerciseField.WEIGHT in fields && item.targetWeight > 0.0 ->
            Triple(
                "${formatRoutineWeight(item.targetWeight, unit)} ${unit.suffix}",
                ElectricCyan,
                "routine_weight_badge_$index"
            )
        else -> null
    }
    Card(
        modifier = Modifier
            .testTag("selected_routine_exercise_$index"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, ElectricCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Tapping the name opens the same read-only exercise sheet the workout
                            // shows: instructions, media, fields and targets. The defaults panel
                            // has its own small control, so the name is no longer the only route
                            // to it and a standard-weight exercise no longer has a dead tap.
                            //
                            // The name takes the leftover space rather than what it wants, so the
                            // controls beside it always have room and never wrap.
                            Text(
                                text = item.exerciseName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onShowDetails() }
                                    .testTag("routine_exercise_name_$index")
                            )
                            if (ExerciseField.ADDED_WEIGHT in fields) {
                                IconButton(
                                    onClick = onToggleExpanded,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("routine_exercise_defaults_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = if (expanded) {
                                            "Hide exercise defaults"
                                        } else {
                                            "Exercise defaults"
                                        },
                                        tint = if (expanded) ElectricCyan else TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            // The load the athlete prescribed, visible without opening anything.
                            loadBadge?.let { (text, color, tag) ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = color.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, color.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = color,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                            .testTag(tag)
                                    )
                                }
                            }
                            // The routine's rest default, when there is one. Small badge rather than a
                            // field, so the setting is visible without opening anything.
                            if (item.restSeconds > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MintGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, MintGreen.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = "${item.restSeconds}s rest",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintGreen,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                            .testTag("routine_rest_badge_$index")
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // No per row "Day X" chip: the day is picked once for the whole
                            // view with the dropdown above, which is the only place it can go
                            // wrong. A single day routine has nothing to show here either.
                            Text(
                                text = listOf(
                                    item.equipment,
                                    item.category
                                ).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Reorder and Delete Actions. The exercise-level chevron used to sit here, but it
                // opened a panel at the bottom of the card rather than the main-set fields the
                // athlete expects. The main-set rows below are the single edit path now.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = if (canMoveUp) ElectricCyan else TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = if (canMoveDown) ElectricCyan else TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (showSupersetAction) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSuperset) ElectricCyan.copy(alpha = 0.16f)
                                    else DarkSurfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isSuperset) ElectricCyan.copy(alpha = 0.65f) else DarkBorder,
                                    CircleShape
                                )
                                .clickable(onClick = onSuperset)
                                .semantics {
                                    contentDescription = if (isSuperset) {
                                        "Manage superset"
                                    } else {
                                        "Link superset"
                                    }
                                }
                                .testTag("routine_superset_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = if (isSuperset) ElectricCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Exercise",
                            tint = FireRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compact target row. It shows Sets plus the exercise's first meaningful field.
            // Every other supported field lives in SET DEFAULTS & DROP SETS below, so a Plank is
            // never offered a Reps control it does not support.
            val quickField = if (ExerciseField.REPS in fields) {
                ExerciseField.REPS
            } else {
                fields.firstOrNull()
            }
            fun updateQuickField(field: ExerciseField, newValue: Double) {
                val updated = ExerciseFieldValues.write(item, field, newValue)
                onUpdate(updated.copy(prescription = RoutineParser.buildPrescription(updated)))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sets",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    RoutineCompactStepper(
                        value = item.targetSets.toString(),
                        onMinus = {
                            if (item.targetSets > 1) {
                                val nextSets = item.targetSets - 1
                                val updated = item.copy(
                                    targetSets = nextSets,
                                    dropSets = item.dropSets.filter { it.mainSetIndex <= nextSets },
                                    mainSetDefaults = item.mainSetDefaults.filter {
                                        it.mainSetIndex <= nextSets
                                    }
                                )
                                onUpdate(
                                    updated.copy(
                                        prescription = RoutineParser.buildPrescription(updated)
                                    )
                                )
                            }
                        },
                        onPlus = {
                            if (item.targetSets < 10) {
                                val updated = item.copy(targetSets = item.targetSets + 1)
                                onUpdate(
                                    updated.copy(
                                        prescription = RoutineParser.buildPrescription(updated)
                                    )
                                )
                            }
                        }
                    )
                }
                quickField?.let { field ->
                    val value = ExerciseFieldValues.read(item, field)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ExerciseFieldCatalog.shortLabel(field, unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        RoutineCompactStepper(
                            value = routineFieldValueText(value, field, unit),
                            onMinus = {
                                updateQuickField(
                                    field,
                                    stepRoutineValue(value, field, -1, unit)
                                )
                            },
                            onPlus = {
                                updateQuickField(
                                    field,
                                    stepRoutineValue(value, field, 1, unit)
                                )
                            }
                        )
                    }
                }
            }

            val repBased = ExerciseField.REPS in fields
            // Undo returns to the target that was persisted when this editor row opened. The slot
            // is always measured so the outlined field never shifts when the icon appears.
            val initialEffort = remember(item.exerciseId) { item.targetEffortRpe }
            val effortChanged = item.targetEffortRpe != initialEffort
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 42.dp)
                    .background(DarkBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target effort",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .background(DarkSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.inverseSurface)
                            .clickable {
                                onUpdate(
                                    item.copy(
                                        targetEffortRpe = EffortScale.step(
                                            item.targetEffortRpe,
                                            effortDisplay,
                                            repBased,
                                            -1
                                        )
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                    Text(
                        text = EffortScale.labelForTarget(item.targetEffortRpe, effortDisplay, repBased),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (item.targetEffortRpe == null) TextMuted else ElectricCyan,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 48.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.inverseSurface)
                            .clickable {
                                onUpdate(
                                    item.copy(
                                        targetEffortRpe = EffortScale.step(
                                            item.targetEffortRpe,
                                            effortDisplay,
                                            repBased,
                                            1
                                        )
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (effortChanged) {
                        IconButton(
                            onClick = { onUpdate(item.copy(targetEffortRpe = initialEffort)) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("routine_target_effort_undo")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo target effort change",
                                tint = TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            RoutineDropSetsSection(
                item = item,
                unit = unit,
                fields = fields,
                onUpdate = onUpdate
            )

            // The default added weight, opened by tapping the exercise name.
            //
            // It animates in and out rather than snapping, so the row does not jump: the athlete
            // asked for the panel, they should see where it came from and where it went.
            AnimatedVisibility(
                visible = expanded && ExerciseField.ADDED_WEIGHT in fields,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Default added weight",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Filled in when you start this split. Leave it empty for bodyweight.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WeightInputField(
                        label = "Added weight (${unit.suffix})",
                        kgValue = item.targetAddedWeightKg,
                        unit = unit,
                        onKgChanged = { onUpdate(item.copy(targetAddedWeightKg = it)) },
                        modifier = Modifier.testTag("routine_added_weight_field_$index")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Default rest after each set",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (item.restSeconds > 0) {
                            "Starts automatically when you tick a set in this routine. Manual timer changes never rewrite it."
                        } else {
                            "Off: the rest timer stays manual for this exercise."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(DarkSurface, RoundedCornerShape(6.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    onUpdate(
                                        item.copy(restSeconds = (item.restSeconds - 15).coerceIn(0, 600))
                                    )
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Text("-", color = TextWhite, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (item.restSeconds > 0) "${item.restSeconds}s" else "Off",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (item.restSeconds > 0) MintGreen else TextMuted,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .testTag("routine_rest_seconds_$index")
                            )
                            IconButton(
                                onClick = {
                                    val next = if (item.restSeconds <= 0) 30 else item.restSeconds + 15
                                    onUpdate(item.copy(restSeconds = next.coerceIn(0, 600)))
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Text("+", color = TextWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (item.restSeconds > 0) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = FireRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onUpdate(item.copy(restSeconds = 0)) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("routine_rest_clear_$index")
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Drop-set configuration for one routine exercise.
 *
 * Every main set has its own branch and its own children. The values are copied from the parent
 * when a child is created, never shared, so editing child 2 cannot move child 1 or the parent.
 * Numbers are local to the main set (`mainSetIndex`, `dropIndex`) and are renumbered on removal.
 */
@Composable
private fun RoutineDropSetsSection(
    item: RoutineExerciseItem,
    unit: WeightUnit,
    fields: List<ExerciseField>,
    onUpdate: (RoutineExerciseItem) -> Unit
) {
    val drops = item.dropSets
    val knownKeys = remember(item.exerciseId) {
        drops.map { "${it.mainSetIndex}:${it.dropIndex}" }.toMutableSet()
    }
    val scope = rememberCoroutineScope()
    var removingKeys by remember(item.exerciseId) { mutableStateOf<Set<String>>(emptySet()) }
    // Entering the editor must show the routine, not every exercise's defaults. The section starts
    // closed and the athlete opens only what they want to edit.
    //
    // rememberSaveable matters here: this composable lives inside a LazyColumn item. Scrolling the
    // exercise out of the viewport disposes the item, and a plain remember would forget both the
    // section and the main-set selection. The lazy list's saveable state holder keeps these values
    // keyed by the item's stable exercise key, so scrolling back restores exactly what was open.
    var expanded by rememberSaveable(item.exerciseId) { mutableStateOf(false) }
    var expandedMainSet by rememberSaveable(item.exerciseId) { mutableIntStateOf(0) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "routine_drop_section_arrow_${item.exerciseId}"
    )

    fun removeDropAnimated(target: RoutineDropSet) {
        val key = "${target.mainSetIndex}:${target.dropIndex}"
        removingKeys = removingKeys + key
        scope.launch {
            delay(190)
            knownKeys.remove(key)
            val remaining = drops
                .filterNot {
                    it.mainSetIndex == target.mainSetIndex && it.dropIndex == target.dropIndex
                }
                .map { drop ->
                    if (drop.mainSetIndex == target.mainSetIndex && drop.dropIndex > target.dropIndex) {
                        drop.copy(dropIndex = drop.dropIndex - 1)
                    } else {
                        drop
                    }
                }
            onUpdate(item.copy(dropSets = remaining))
            removingKeys = removingKeys - key
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 3.dp)
                .testTag("routine_drop_section_toggle_${item.exerciseId}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SET DEFAULTS & DROP SETS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (drops.isEmpty()) "Main sets" else "${drops.size} drop set${if (drops.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (drops.isEmpty()) TextMuted else ElectricCyan.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Collapse set defaults" else "Expand set defaults",
                tint = ElectricCyan,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(arrowRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(280), expandFrom = Alignment.Top) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(120))
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))

                for (mainSetIndex in 1..item.targetSets) {
                    val mainDefault = item.defaultForMainSet(mainSetIndex)
                    val children = drops
                        .filter { it.mainSetIndex == mainSetIndex }
                        .sortedBy { it.dropIndex }
                    val mainExpanded = expandedMainSet == mainSetIndex

                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Continue the branch gutter through the main-set fields panel. The line
                        // starts inside the numbered circle (which paints over it) and runs to the
                        // bottom of the expanded panel, so it still connects to the drop set even
                        // when both disclosures are open.
                        if (children.isNotEmpty()) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawLine(
                                    color = Color(0xFF33556A),
                                    start = Offset(14.dp.toPx(), 20.dp.toPx()),
                                    end = Offset(14.dp.toPx(), size.height),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable {
                                        expandedMainSet = if (mainExpanded) 0 else mainSetIndex
                                    }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // The leading column is exactly the branch gutter width. The
                                // circle is centred in it and the connector draws at its centre,
                                // so the branch grows straight out of the number.
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(DarkBg, CircleShape)
                                            .border(1.dp, DarkBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            mainSetIndex.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = TextWhite
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Main set $mainSetIndex",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = TextWhite
                                    )
                                    Text(
                                        routineFieldSummary(mainDefault, fields, unit),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = if (mainExpanded) {
                                        "Collapse main set"
                                    } else {
                                        "Edit main set"
                                    },
                                    tint = if (mainExpanded) ElectricCyan else TextMuted,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(if (mainExpanded) 180f else 0f)
                                )
                            }

                            AnimatedVisibility(
                                visible = mainExpanded,
                                enter = expandVertically(tween(240)) + fadeIn(tween(140)),
                                exit = shrinkVertically(tween(180)) + fadeOut(tween(100))
                            ) {
                                RoutineMainSetDefaultsPanel(
                                    mainSet = mainDefault,
                                    fields = fields,
                                    unit = unit,
                                    onUpdate = { updated -> onUpdate(item.withMainSetDefault(updated)) }
                                )
                            }
                        }
                    }

                    if (children.isNotEmpty()) {
                        DropSetBranchLeadIn(
                            width = 28.dp,
                            gutter = 14.dp,
                            modifier = Modifier.height(5.dp)
                        )
                    }
                    children.forEachIndexed { childIndex, drop ->
                        val key = "${drop.mainSetIndex}:${drop.dropIndex}"
                        val animateIn = knownKeys.add(key)
                        key(key) {
                            // The arm must meet the drop-set number, not the middle of the card.
                            // The card reports the vertical centre of its header row, so opening
                            // the dropdown grows the card downwards while the arm stays on D1.
                            var headerAnchorY by remember(key) { mutableFloatStateOf(0f) }
                            // Box overlay instead of IntrinsicSize: the card contains
                            // AnimatedVisibility, which cannot answer intrinsic measurements.
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val gutterX = 14.dp.toPx()
                                    val armEnd = 28.dp.toPx()
                                    val anchorY = if (headerAnchorY > 0f) {
                                        headerAnchorY
                                    } else {
                                        size.height / 2f
                                    }
                                    drawLine(
                                        color = Color(0xFF33556A),
                                        start = Offset(gutterX, 0f),
                                        end = Offset(
                                            gutterX,
                                            if (childIndex == children.lastIndex) anchorY else size.height
                                        ),
                                        strokeWidth = 1.2.dp.toPx()
                                    )
                                    drawLine(
                                        color = Color(0xFF33556A),
                                        start = Offset(gutterX, anchorY),
                                        end = Offset(armEnd, anchorY),
                                        strokeWidth = 1.2.dp.toPx()
                                    )
                                }
                                DropSetRowVisibility(
                                    visible = key !in removingKeys,
                                    modifier = Modifier.padding(
                                        start = 28.dp,
                                        bottom = if (childIndex == children.lastIndex) 4.dp else 6.dp
                                    )
                                ) {
                                    RoutineDropSetCard(
                                        drop = drop,
                                        unit = unit,
                                        fields = fields,
                                        animateIn = animateIn,
                                        onUpdate = { updated ->
                                            onUpdate(
                                                item.copy(
                                                    dropSets = drops.map {
                                                        if (it.mainSetIndex == drop.mainSetIndex &&
                                                            it.dropIndex == drop.dropIndex
                                                        ) {
                                                            updated
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                )
                                            )
                                        },
                                        onRemove = { removeDropAnimated(drop) },
                                        modifier = Modifier.fillMaxWidth(),
                                        onHeaderAnchorYChanged = { headerAnchorY = it }
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp, bottom = 5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val nextIndex = (children.maxOfOrNull { it.dropIndex } ?: 0) + 1
                                onUpdate(
                                    item.copy(
                                        dropSets = drops + RoutineDropSet(
                                            mainSetIndex = mainSetIndex,
                                            dropIndex = nextIndex,
                                            targetReps = mainDefault.targetReps,
                                            targetHoldSeconds = mainDefault.targetHoldSeconds,
                                            targetWeight = mainDefault.targetWeight,
                                            targetAddedWeightKg = mainDefault.targetAddedWeightKg,
                                            targetDistanceKm = mainDefault.targetDistanceKm,
                                            restSeconds = mainDefault.restSeconds
                                        )
                                    )
                                )
                            }
                            .testTag("routine_add_drop_${item.exerciseId}_$mainSetIndex"),
                        color = DarkBg,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            ElectricCyan.copy(alpha = 0.30f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ElectricCyan.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Add drop set",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElectricCyan.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineMainSetDefaultsPanel(
    mainSet: RoutineMainSetDefault,
    fields: List<ExerciseField>,
    unit: WeightUnit,
    onUpdate: (RoutineMainSetDefault) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Both the main-set and drop-set panels start at the branch gutter (28dp) and reach
            // the card's right edge, so their outer widths match exactly.
            .padding(start = 28.dp, bottom = 5.dp)
            .background(DarkBg, RoundedCornerShape(9.dp))
            .border(1.dp, DarkBorder.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        fields.forEach { field ->
            RoutineFieldStepper(
                field = field,
                value = ExerciseFieldValues.read(mainSet, field),
                unit = unit,
                onMinus = {
                    onUpdate(
                        ExerciseFieldValues.write(
                            mainSet,
                            field,
                            stepRoutineValue(ExerciseFieldValues.read(mainSet, field), field, -1, unit)
                        )
                    )
                },
                onPlus = {
                    onUpdate(
                        ExerciseFieldValues.write(
                            mainSet,
                            field,
                            stepRoutineValue(ExerciseFieldValues.read(mainSet, field), field, 1, unit)
                        )
                    )
                }
            )
        }
        RoutineRestStepper(
            restSeconds = mainSet.restSeconds,
            onMinus = {
                onUpdate(
                    mainSet.copy(
                        restSeconds = if (mainSet.restSeconds <= 0) 0 else {
                            (mainSet.restSeconds - 15).coerceAtLeast(0)
                        }
                    )
                )
            },
            onPlus = {
                onUpdate(
                    mainSet.copy(
                        restSeconds = if (mainSet.restSeconds <= 0) 30 else {
                            (mainSet.restSeconds + 15).coerceAtMost(600)
                        }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RoutineDropSetCard(
    drop: RoutineDropSet,
    unit: WeightUnit,
    fields: List<ExerciseField>,
    animateIn: Boolean,
    onUpdate: (RoutineDropSet) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onHeaderAnchorYChanged: (Float) -> Unit = {}
) {
    // The card is disposed with its parent exercise when the routine list scrolls it away. Saving
    // the expanded flag keeps the athlete's choice: closing a drop set must stay closed.
    var expanded by rememberSaveable(drop.mainSetIndex, drop.dropIndex) { mutableStateOf(false) }
    val state = remember { MutableTransitionState(!animateIn) }
    LaunchedEffect(animateIn) { if (animateIn) state.targetState = true }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "routine_drop_arrow_${drop.mainSetIndex}_${drop.dropIndex}"
    )

    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = expandVertically(tween(280), expandFrom = Alignment.Top) +
            slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(160)),
        exit = shrinkVertically(tween(160)) + fadeOut(tween(100))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("routine_drop_${drop.mainSetIndex}_${drop.dropIndex}"),
            color = DarkBg,
            shape = RoundedCornerShape(9.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.24f))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { onHeaderAnchorYChanged(it.height / 2f) }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(ElectricCyan.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.6f), CircleShape)
                            .clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "D${drop.dropIndex}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            ),
                            color = ElectricCyan
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Drop set ${drop.dropIndex}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            routineFieldSummary(drop, fields, unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("routine_drop_remove_${drop.mainSetIndex}_${drop.dropIndex}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove drop set",
                            tint = FireRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = if (expanded) "Collapse drop set" else "Expand drop set",
                            tint = ElectricCyan,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(arrowRotation)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(220)) + fadeIn(tween(140)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(100))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f))
                        fields.forEach { field ->
                            RoutineFieldStepper(
                                field = field,
                                value = ExerciseFieldValues.read(drop, field),
                                unit = unit,
                                onMinus = {
                                    onUpdate(
                                        ExerciseFieldValues.write(
                                            drop,
                                            field,
                                            stepRoutineValue(
                                                ExerciseFieldValues.read(drop, field),
                                                field,
                                                -1,
                                                unit
                                            )
                                        )
                                    )
                                },
                                onPlus = {
                                    onUpdate(
                                        ExerciseFieldValues.write(
                                            drop,
                                            field,
                                            stepRoutineValue(
                                                ExerciseFieldValues.read(drop, field),
                                                field,
                                                1,
                                                unit
                                            )
                                        )
                                    )
                                }
                            )
                        }
                        RoutineRestStepper(
                            restSeconds = drop.restSeconds,
                            onMinus = {
                                onUpdate(
                                    drop.copy(
                                        restSeconds = if (drop.restSeconds <= 0) 0 else {
                                            (drop.restSeconds - 15).coerceAtLeast(0)
                                        }
                                    )
                                )
                            },
                            onPlus = {
                                onUpdate(
                                    drop.copy(
                                        restSeconds = if (drop.restSeconds <= 0) 30 else {
                                            (drop.restSeconds + 15).coerceAtMost(600)
                                        }
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineFieldStepper(
    field: ExerciseField,
    value: Double,
    unit: WeightUnit,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    RoutineDropStepper(
        label = ExerciseFieldCatalog.shortLabel(field, unit),
        value = routineFieldValueText(value, field, unit),
        onMinus = onMinus,
        onPlus = onPlus,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun routineFieldValueText(value: Double, field: ExerciseField, unit: WeightUnit): String =
    when (field) {
        ExerciseField.REPS -> value.toInt().toString()
        ExerciseField.WEIGHT -> formatRoutineWeight(value, unit)
        ExerciseField.ADDED_WEIGHT -> formatRoutineWeight(value, unit)
        ExerciseField.HOLD_SECONDS -> value.toInt().toString() + " s"
        ExerciseField.TIME_SECONDS -> {
            val minutes = value / 60.0
            if (minutes % 1.0 == 0.0) minutes.toInt().toString() + " min"
            else "%.1f min".format(minutes)
        }
        ExerciseField.DISTANCE_KM -> DistanceFormat.format(value, unit) + " " + DistanceFormat.suffixFor(unit)
    }

private fun routineFieldSummary(
    mainSet: RoutineMainSetDefault,
    fields: List<ExerciseField>,
    unit: WeightUnit
): String = fields.mapNotNull { field ->
    val value = ExerciseFieldValues.read(mainSet, field)
    if (value <= 0.0) null else routineFieldValueText(value, field, unit)
}.joinToString(" · ").ifBlank { "No defaults" }

private fun routineFieldSummary(
    drop: RoutineDropSet,
    fields: List<ExerciseField>,
    unit: WeightUnit
): String = fields.mapNotNull { field ->
    val value = ExerciseFieldValues.read(drop, field)
    if (value <= 0.0) null else routineFieldValueText(value, field, unit)
}.joinToString(" · ").ifBlank { "No defaults" }

private fun stepRoutineValue(
    current: Double,
    field: ExerciseField,
    direction: Int,
    unit: WeightUnit
): Double = when (field) {
    ExerciseField.REPS -> (current + direction).coerceAtLeast(0.0)
    ExerciseField.WEIGHT,
    ExerciseField.ADDED_WEIGHT -> (current + direction * 2.5).coerceAtLeast(0.0)
    ExerciseField.HOLD_SECONDS -> (current + direction * 5.0).coerceAtLeast(0.0)
    ExerciseField.TIME_SECONDS -> (current + direction * 60.0).coerceAtLeast(0.0)
    ExerciseField.DISTANCE_KM -> {
        val step = if (unit == WeightUnit.LB) 0.1 / 0.621371 else 0.1
        (current + direction * step).coerceAtLeast(0.0)
    }
}

@Composable
private fun RoutineCompactStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(DarkSurface, RoundedCornerShape(6.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinus, modifier = Modifier.size(28.dp)) {
            Text("-", color = TextWhite, fontWeight = FontWeight.Bold)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = TextWhite,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 4.dp)
        )
        IconButton(onClick = onPlus, modifier = Modifier.size(28.dp)) {
            Text("+", color = TextWhite, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoutineRestStepper(
    restSeconds: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Rest",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .background(DarkSurface, RoundedCornerShape(6.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinus, modifier = Modifier.size(26.dp)) {
                Text("-", color = TextWhite, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier.width(58.dp),
                contentAlignment = Alignment.Center
            ) {
                if (restSeconds > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            restSeconds.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "s",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                } else {
                    Text(
                        "Off",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                }
            }
            IconButton(onClick = onPlus, modifier = Modifier.size(26.dp)) {
                Text("+", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoutineDropStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .background(DarkSurface, RoundedCornerShape(6.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinus, modifier = Modifier.size(26.dp)) {
                Text("-", color = TextWhite, fontWeight = FontWeight.Bold)
            }
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(62.dp)
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(26.dp)) {
                Text("+", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Formats a weight for the small badge on a routine row.
 *
 * In the unit the athlete reads, like everywhere else, and without a trailing zero so the badge
 * stays narrow enough for the name next to it.
 */
private fun formatRoutineWeight(kg: Double, unit: WeightUnit): String {
    val value = WeightConversion.fromKg(kg, unit)
    return if (value % 1.0 == 0.0) "${value.toInt()}" else "%.1f".format(value)
}
