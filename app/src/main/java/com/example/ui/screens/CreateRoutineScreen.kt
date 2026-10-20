package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Exercise
import com.example.data.model.ExerciseSearch
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineDayClamp
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.ui.components.AiUnavailableDialog
import com.example.ui.components.AnimatedDropdownField
import com.example.ui.components.DayDropdown
import com.example.ui.components.CustomExerciseDialog
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
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ExerciseMediaStore
import kotlinx.coroutines.delay

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
    val customExerciseResult by viewModel.customExerciseResult.collectAsStateWithLifecycle()
    // Weights live in kilograms; this is the unit the athlete reads and types in.
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()

    val isEditing = editingRoutine != null

    var routineName by remember { mutableStateOf(editingRoutine?.name.orEmpty()) }
    var routineDescription by remember { mutableStateOf(editingRoutine?.description.orEmpty()) }
    var selectedGoal by remember { mutableStateOf(editingRoutine?.goal ?: "Strength & Muscle Growth") }
    var daysPerWeek by remember { mutableIntStateOf(editingRoutine?.daysPerWeek ?: 4) }

    // Search query with auto-completion
    var searchQuery by remember { mutableStateOf("") }
    var activeDayForAdding by remember { mutableIntStateOf(1) }
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
        selectedRoutineExercises.clear()
        selectedRoutineExercises.addAll(RoutineParser.parseExercises(existing, allExercises))
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

    // Shrinking the routine must not leave exercises stranded on a day that no longer exists.
    //
    // Without this, an athlete who put "Weighted Dips" on day 5 and then dropped to a 4 day split
    // saved a routine that still contained it: the row stayed in the list, just never visible on any
    // day, and it came back the moment the routine was widened again. Clamping to the last surviving
    // day is the only reading that keeps every exercise both visible and logged.
    LaunchedEffect(daysPerWeek) {
        val clamped = RoutineDayClamp.clampExercises(selectedRoutineExercises.toList(), daysPerWeek)
        clamped.forEachIndexed { index, item ->
            if (selectedRoutineExercises[index] != item) {
                selectedRoutineExercises[index] = item
            }
        }
        activeDayForAdding = RoutineDayClamp.clampSelectedDay(activeDayForAdding, daysPerWeek)
    }

    // The added exercises are shown one training day at a time, chosen with the dropdown in the
    // "Assign added exercises to" box. Each entry keeps its real index in the whole list, so
    // reordering and deleting still address the right element.
    //
    // Deliberately not `remember`ed off the list itself: a SnapshotStateList is mutable in place,
    // so a remembered key list does not change identity when an exercise is added and the view
    // would not recompute. That was the reason an exercise added to the day being viewed only
    // appeared after switching days away and back.
    val visibleRoutineEntries = selectedRoutineExercises
        .mapIndexed { index, item -> index to item }
        .filter { (_, item) -> daysPerWeek <= 1 || item.dayNumber == activeDayForAdding }

    fun persistRoutine() {
        if (!canSave) return

        // Last line of defence for the stranded-day bug: never persist a day number the routine does
        // not have. The LaunchedEffect above already clamps the live list, so this is a guard against
        // a save racing a shrink, not the primary fix.
        val clamped = RoutineDayClamp.clampExercises(selectedRoutineExercises.toList(), daysPerWeek)

        if (isEditing) {
            viewModel.saveEditedRoutine(
                SavedRoutine(
                    id = editingRoutine.id,
                    name = routineName.trim(),
                    description = routineDescription.trim(),
                    daysPerWeek = daysPerWeek,
                    goal = selectedGoal,
                    exercisesJson = RoutineParser.serializeExercises(clamped),
                    createdAt = editingRoutine.createdAt
                )
            )
        } else {
            viewModel.saveCustomRoutine(
                name = routineName,
                description = routineDescription,
                daysPerWeek = daysPerWeek,
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
                            disabledContainerColor = DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_routine_confirm_button")
                    ) {
                        Text(
                            text = if (isEditing) "Update" else "Save",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (canSave) DarkBg else TextMuted
                        )
                    }
                }
            }

            // === MAIN SCROLLABLE CONTENT ===
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
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

                            // Days per week
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Days Per Week: $daysPerWeek",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextWhite
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    (1..7).forEach { d ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .background(
                                                    if (daysPerWeek == d) ElectricCyan else DarkBg,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(1.dp, if (daysPerWeek == d) ElectricCyan else DarkBorder, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    daysPerWeek = d
                                                    activeDayForAdding =
                                                        RoutineDayClamp.clampSelectedDay(activeDayForAdding, d)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$d",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (daysPerWeek == d) DarkBg else TextWhite
                                            )
                                        }
                                    }
                                }
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

                                    // One dropdown instead of a row of "Day X" chips: the number of
                                    // entries always matches the routine, so a 2 day routine never
                                    // offers a 5th day.
                                    DayDropdown(
                                        daysPerWeek = daysPerWeek,
                                        selectedDay = activeDayForAdding,
                                        onSelect = { activeDayForAdding = it }
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
                                                            RoutineExerciseItem(
                                                                exerciseId = exercise.id,
                                                                exerciseName = exercise.name,
                                                                exerciseType = exercise.exerciseType,
                                                                category = exercise.category,
                                                                equipment = exercise.equipment,
                                                                targetSets = 3,
                                                                targetReps = 8,
                                                                prescription = "3 sets x 8 reps",
                                                                dayNumber = if (daysPerWeek > 1) activeDayForAdding else 1,
                                                                dayTitle = "Day ${if (daysPerWeek > 1) activeDayForAdding else 1}"
                                                            )
                                                        )
                                                        searchQuery = "" // Clear after adding
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isAlreadyAdded) DarkSurfaceVariant else ElectricCyan
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("add_exercise_${exercise.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isAlreadyAdded) Icons.Default.Check else Icons.Default.Add,
                                                        contentDescription = null,
                                                        tint = if (isAlreadyAdded) MintGreen else DarkBg,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isAlreadyAdded) "Add More" else "Add",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isAlreadyAdded) MintGreen else DarkBg
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
                            onUpdate = { updated ->
                                selectedRoutineExercises[index] = updated
                            },
                            onDelete = {
                                selectedRoutineExercises.removeAt(index)
                            }
                        )
                    }

                    // A day with nothing on it yet, so the view is never silently blank
                    if (visibleRoutineEntries.isEmpty() && daysPerWeek > 1) {
                        item {
                            Text(
                                text = "Nothing on this day yet. Add an exercise above and it lands here.",
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
                            tint = if (canSave) DarkBg else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditing) "Update Workout Routine" else "Save Workout Routine",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (canSave) DarkBg else TextMuted
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
                        RoutineExerciseItem(
                            exerciseId = state.exercise.id,
                            exerciseName = state.exercise.name,
                            exerciseType = state.exercise.exerciseType,
                            category = state.exercise.category,
                            equipment = state.exercise.equipment,
                            targetSets = 3,
                            targetReps = 8,
                            prescription = "3 sets x 8 reps",
                            dayNumber = if (daysPerWeek > 1) activeDayForAdding else 1,
                            dayTitle = "Day ${if (daysPerWeek > 1) activeDayForAdding else 1}"
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
                onSave = { name, category, equipment, exerciseType, description ->
                    viewModel.createCustomExercise(name, category, equipment, exerciseType, description)
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
                            RoutineExerciseItem(
                                exerciseId = exercise.id,
                                exerciseName = exercise.name,
                                exerciseType = exercise.exerciseType,
                                category = exercise.category,
                                equipment = exercise.equipment,
                                targetSets = 3,
                                targetReps = 8,
                                prescription = "3 sets x 8 reps",
                                dayNumber = if (daysPerWeek > 1) activeDayForAdding else 1,
                                dayTitle = "Day ${if (daysPerWeek > 1) activeDayForAdding else 1}"
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
                "Gemini is filling in the exercise fields..."
            } else {
                "Ask AI to generate it, or create it yourself."
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isAiWorking) ElectricCyan else TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SelectedRoutineExerciseRow(
    index: Int,
    item: RoutineExerciseItem,
    unit: WeightUnit,
    expanded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleExpanded: () -> Unit,
    onUpdate: (RoutineExerciseItem) -> Unit,
    onDelete: () -> Unit
) {
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
                            // Tapping the name opens the settings for this exercise. It is the
                            // only affordance on the row, so a routine with twenty exercises does
                            // not carry twenty visible weight fields.
                            //
                            // The name takes the leftover space rather than what it wants, so the
                            // badge beside it always has room and never wraps onto two lines.
                            Text(
                                text = item.exerciseName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onToggleExpanded() }
                                    .testTag("routine_exercise_name_$index")
                            )
                            // The default added weight, when there is one. A small badge rather
                            // than a field, so the setting is visible without opening anything.
                            if (item.targetAddedWeightKg > 0.0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = CoralOrange.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, CoralOrange.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = "+${formatRoutineWeight(item.targetAddedWeightKg, unit)} ${unit.suffix}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = CoralOrange,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                            .testTag("routine_added_weight_badge_$index")
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

                // Reorder and Delete Actions
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

            // Prescribed Sets & Reps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Target Sets
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .background(DarkSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (item.targetSets > 1) {
                                    onUpdate(item.copy(targetSets = item.targetSets - 1, prescription = "${item.targetSets - 1} sets x ${item.targetReps} reps"))
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("-", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${item.targetSets}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = {
                                if (item.targetSets < 10) {
                                    onUpdate(item.copy(targetSets = item.targetSets + 1, prescription = "${item.targetSets + 1} sets x ${item.targetReps} reps"))
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("+", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Target Reps
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Reps:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .background(DarkSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (item.targetReps > 1) {
                                    onUpdate(item.copy(targetReps = item.targetReps - 1, prescription = "${item.targetSets} sets x ${item.targetReps - 1} reps"))
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("-", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${item.targetReps}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = {
                                if (item.targetReps < 50) {
                                    onUpdate(item.copy(targetReps = item.targetReps + 1, prescription = "${item.targetSets} sets x ${item.targetReps + 1} reps"))
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("+", color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // The default added weight, opened by tapping the exercise name.
            //
            // It animates in and out rather than snapping, so the row does not jump: the athlete
            // asked for the panel, they should see where it came from and where it went.
            AnimatedVisibility(
                visible = expanded,
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
                }
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
