package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import com.example.util.ExerciseMediaStore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.example.data.model.DistanceFormat
import com.example.data.model.ExerciseMedia
import androidx.compose.material.icons.filled.ExpandLess
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import com.example.ui.components.AppVersionFooter
import com.example.ui.components.ExerciseGifThumbnail
import com.example.ui.components.ExerciseGif
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WeightUnit
import com.example.data.model.MuscleCategory
import com.example.ui.components.DayDropdown
import com.example.ui.components.MuscleSelectorDialog
import com.example.ui.components.AutoSaveIndicator
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.components.ExerciseEditorDialog
import com.example.ui.components.CustomExerciseDialog
import com.example.ui.components.DeleteCustomExerciseDialog
import com.example.ui.components.WeightInputField
import com.example.ui.components.RestTimerDialog
import com.example.ui.components.RestTimerDockedButton
import com.example.ui.theme.AiBlue
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
import com.example.ui.theme.VioletPurple
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class QuickWorkoutTrayState {
    MINIMIZED,
    PARTIAL,
    EXPANDED
}

private const val EXERCISE_PAGE_SIZE = 30

/** How long the "Added to workout" banner stays visible before it slides back out. */
private const val ADDED_BANNER_DURATION_MS = 1500L

private val ExerciseCardShape = RoundedCornerShape(14.dp)
private val ExerciseCardBorder = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
private val ExerciseBadgeShape = RoundedCornerShape(8.dp)
private val ExerciseButtonShape = RoundedCornerShape(10.dp)
private val ExerciseButtonBorder = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan)
private val ExerciseButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

private val StandardWtBadgeBg = ElectricCyan.copy(alpha = 0.18f)
private val AddedWtBadgeBg = CoralOrange.copy(alpha = 0.18f)
private val BodyweightBadgeBg = MintGreen.copy(alpha = 0.18f)
private val CardioBadgeBg = GoldenAmber.copy(alpha = 0.18f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedMusclePart by viewModel.selectedMusclePart.collectAsStateWithLifecycle()
    val filteredExercises by viewModel.filteredExercises.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeSets by viewModel.activeSets.collectAsStateWithLifecycle()
    val activeSessionExercises by viewModel.activeSessionExercises.collectAsStateWithLifecycle()
    val activeRoutineWorkout by viewModel.activeRoutineWorkout.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val customExerciseResult by viewModel.customExerciseResult.collectAsStateWithLifecycle()
    // When the workout in progress was last written to disk, for the "auto saved" indicator.
    val lastAutoSaveAt by viewModel.lastAutoSaveAt.collectAsStateWithLifecycle()

    var showCustomExerciseDialog by remember { mutableStateOf(false) }
    var exerciseBeingEdited by remember { mutableStateOf<Exercise?>(null) }
    var exercisePendingDeletion by remember { mutableStateOf<Exercise?>(null) }
    var prefillCustomExerciseName by remember { mutableStateOf("") }

    // Tapping an exercise card opens its full information popup
    var exerciseShowingDetails by remember { mutableStateOf<Exercise?>(null) }
    // The square button floating over the category chips opens the body part picker
    var showMuscleSelector by remember { mutableStateOf(false) }

    // Whether exercise animations play by themselves (settings page toggle).
    val autoplayDemos by viewModel.autoplayExerciseDemos.collectAsStateWithLifecycle()
    // The exercise whose media slot is being filled from the gallery picker.
    var pendingMediaExerciseId by remember { mutableStateOf<Long?>(null) }
    // The exercise open in the low level editor, reached from the pen on the detail dialog.
    var exerciseBeingEditedFull by remember { mutableStateOf<Exercise?>(null) }
    // Set when the athlete asked for media while creating a new exercise, which has no id yet.
    var pendingMediaAfterCreate by remember { mutableStateOf(false) }
    var mediaImportFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // PickVisualMedia is the modern picker: it needs no storage permission at all.
    //
    // What it hands back is a content uri owned by the system photo picker, and that grant expires
    // the moment the picker is gone: reading it later fails with "does not have permission to
    // access picker uri", which is exactly what used to happen and why attached media showed as
    // "Could not load animation". The bytes are therefore copied into this app's own files right
    // here, while the result is still warm, and only the path we own is persisted.
    //
    // The video flag is read from the mime type rather than the extension, since the uri usually
    // has none.
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            val targetId = pendingMediaExerciseId
            pendingMediaExerciseId = null
            if (uri == null || targetId == null) return@rememberLauncherForActivityResult
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

    // "user_generated" is a flag that lists only the exercises created by the user
    val isUserGeneratedSearch = remember(searchQuery) {
        searchQuery.trim().equals(viewModel.userGeneratedFlag, ignoreCase = true)
    }

    // "ai_generated" is a flag that lists only the exercises the AI registered
    val isAiGeneratedSearch = remember(searchQuery) {
        searchQuery.trim().equals(viewModel.aiGeneratedFlag, ignoreCase = true)
    }

    var trayState by remember { mutableStateOf(QuickWorkoutTrayState.PARTIAL) }
    var showExitWorkoutDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showRenameWorkoutDialog by remember { mutableStateOf(false) }
    var showRestTimerDialog by rememberSaveable { mutableStateOf(false) }
    var finishNotes by remember { mutableStateOf("") }
    var showExitRoutinePrompt by remember { mutableStateOf(false) }
    var pendingQuickWorkoutAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var autoCollapseJob by remember { mutableStateOf<Job?>(null) }
    var targetScrollExerciseId by remember { mutableLongStateOf(-1L) }
    var targetScrollTrigger by remember { mutableIntStateOf(0) }

    // "Exercise added" slide-in banner. It owns a dedicated job so the tray's own
    // autoCollapseJob?.cancel() calls can never leave the banner stuck on screen.
    var addedExerciseName by remember { mutableStateOf<String?>(null) }
    var addedBannerJob by remember { mutableStateOf<Job?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val workoutListState = rememberLazyListState()

    val safeStartQuickWorkout: () -> Unit = {
        autoCollapseJob?.cancel()
        if (activeRoutineWorkout != null) {
            pendingQuickWorkoutAction = {
                viewModel.startNewSession("Quick Workout")
                trayState = QuickWorkoutTrayState.PARTIAL
            }
            showExitRoutinePrompt = true
        } else {
            viewModel.startNewSession("Quick Workout")
            trayState = QuickWorkoutTrayState.PARTIAL
        }
    }

    val safeAddExercise: (Exercise) -> Unit = { exercise ->
        // Central handler so both entry points (normal add & "exit routine first") behave the same
        fun performAdd() {
            viewModel.addExerciseToActiveSession(exercise)
            targetScrollExerciseId = exercise.id
            targetScrollTrigger++

            // The old pop-up (tray sliding up and back down on its own) is gone: adding an exercise
            // no longer moves the bottom tray at all, only the green banner animates in and out.
            addedExerciseName = exercise.name
            addedBannerJob?.cancel()
            addedBannerJob = coroutineScope.launch {
                delay(ADDED_BANNER_DURATION_MS)
                addedExerciseName = null
            }
        }

        if (activeRoutineWorkout != null) {
            pendingQuickWorkoutAction = { performAdd() }
            showExitRoutinePrompt = true
        } else {
            performAdd()
        }
    }

    BackHandler(enabled = activeSession != null && trayState == QuickWorkoutTrayState.EXPANDED) {
        autoCollapseJob?.cancel()
        trayState = QuickWorkoutTrayState.PARTIAL
    }

    val categories = remember { listOf("All") + MuscleCategory.ALL }

    // Progressive loading / Pagination state
    val listState = rememberLazyListState()
    var displayedCount by remember { mutableIntStateOf(EXERCISE_PAGE_SIZE) }

    // Reset pagination window and scroll to top when search query or category filter changes
    LaunchedEffect(searchQuery, selectedCategory, selectedMusclePart) {
        displayedCount = EXERCISE_PAGE_SIZE
        if (listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }
    }

    val displayedExercises = remember(filteredExercises, displayedCount) {
        if (filteredExercises.size <= displayedCount) {
            filteredExercises
        } else {
            filteredExercises.take(displayedCount)
        }
    }

    // High performance derived state for end-of-list pagination detection
    val shouldLoadMore by remember(displayedExercises.size, filteredExercises.size) {
        derivedStateOf {
            if (displayedExercises.size >= filteredExercises.size) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val totalItemsCount = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && displayedCount < filteredExercises.size) {
            displayedCount = (displayedCount + EXERCISE_PAGE_SIZE).coerceAtMost(filteredExercises.size)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(DarkBg)) {
        val fullHeight = maxHeight
        val partialHeight = (maxHeight * 0.36f).coerceIn(260.dp, 340.dp)
        val minimizedHeight = 56.dp

        val targetTrayHeight = when (trayState) {
            QuickWorkoutTrayState.EXPANDED -> fullHeight
            QuickWorkoutTrayState.PARTIAL -> partialHeight
            QuickWorkoutTrayState.MINIMIZED -> minimizedHeight
        }

        val animatedTrayHeight by animateDpAsState(
            targetValue = targetTrayHeight,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "quick_workout_tray_height"
        )

        val listBottomPadding = when {
            activeSession == null -> 24.dp
            trayState == QuickWorkoutTrayState.MINIMIZED -> 72.dp
            trayState == QuickWorkoutTrayState.PARTIAL -> partialHeight + 16.dp
            else -> 24.dp
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The title column shrinks first so the action row on the right keeps its natural width
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Exercises",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${filteredExercises.size} exercises",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isUserGeneratedSearch -> MintGreen
                                isAiGeneratedSearch -> AiBlue
                                selectedMusclePart != null -> ElectricCyan
                                else -> TextMuted
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        // The body part picker sits right next to the "+", on the same baseline and
                        // the same size: as a floating square over the chips it read as a stray
                        // sticker rather than a control.
                        IconButton(
                            onClick = { showMuscleSelector = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("open_muscle_selector_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = "Choose the body part to train",
                                tint = if (selectedMusclePart != null) ElectricCyan else ElectricCyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.consumeCustomExerciseResult()
                                exerciseBeingEdited = null
                                prefillCustomExerciseName = ""
                                showCustomExerciseDialog = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("add_custom_exercise_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Custom Exercise",
                                tint = MintGreen
                            )
                        }

                        if (activeSession == null) {
                            Button(
                                onClick = safeStartQuickWorkout,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("start_workout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = DarkBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Start",
                                    fontWeight = FontWeight.Bold,
                                    color = DarkBg,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Autocomplete Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search exercise", color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = ElectricCyan
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMuted
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exercise_search_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )

                // "Your exercise does not exist, let's create it!" shortcut
                if (searchQuery.isNotBlank() && !isUserGeneratedSearch && filteredExercises.isEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("exercise_not_found_hint"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Your exercise does not exist, let's ",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                        Text(
                            text = "create it!",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan,
                            modifier = Modifier
                                .clickable {
                                    viewModel.consumeCustomExerciseResult()
                                    exerciseBeingEdited = null
                                    prefillCustomExerciseName = searchQuery.trim()
                                    showCustomExerciseDialog = true
                                }
                                .testTag("create_missing_exercise_link")
                        )
                    }
                }

                // "user_generated" flag banner
                if (isUserGeneratedSearch) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MintGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, MintGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("user_generated_filter_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MintGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (filteredExercises.isEmpty()) {
                                    "You have not created any exercise yet. Tap + to add one."
                                } else {
                                    "Showing your ${filteredExercises.size} custom exercise${if (filteredExercises.size == 1) "" else "s"} - you can edit or delete them"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MintGreen
                            )
                        }
                        IconButton(
                            onClick = { viewModel.searchQuery.value = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear user_generated filter",
                                tint = MintGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // "ai_generated" flag banner: same layout as the user one, light blue instead of green
                if (isAiGeneratedSearch) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AiBlue.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, AiBlue.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("ai_generated_filter_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AiBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (filteredExercises.isEmpty()) {
                                    "No AI generated exercise yet. They appear here when Gemini adds a new one to a routine."
                                } else {
                                    "Showing ${filteredExercises.size} AI generated exercise${if (filteredExercises.size == 1) "" else "s"} - not made by you, but you can edit or delete them"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = AiBlue
                            )
                        }
                        IconButton(
                            onClick = { viewModel.searchQuery.value = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear ai_generated filter",
                                tint = AiBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Muscle Category Chips
                LazyRow(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(categories, key = { it }, contentType = { "category_chip" }) { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(cat) },
                        label = {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan,
                            selectedLabelColor = DarkBg,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextWhite
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = DarkBorder,
                            selectedBorderColor = ElectricCyan
                        )
                    )
                    }
                }

                // Active body part filter, only visible when one is picked
                if (selectedMusclePart != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ElectricCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                            .testTag("active_muscle_filter_banner"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Training ${selectedMusclePart?.label}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.setSelectedMusclePart(null) },
                            modifier = Modifier.testTag("clear_muscle_filter_button")
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }

            // Exercise List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = displayedExercises,
                    key = { it.id },
                    contentType = { "exercise_card" }
                ) { exercise ->
                    // Both the user's own exercises and the AI generated ones can be edited/deleted
                    val isEditable = exercise.isCustom || exercise.isAiGenerated
                    ExerciseCard(
                        exercise = exercise,
                        unit = weightUnit.suffix,
                        onClick = { exerciseShowingDetails = exercise },
                        onAddToWorkout = {
                            safeAddExercise(exercise)
                        },
                        onEdit = if (isEditable) {
                            {
                                viewModel.consumeCustomExerciseResult()
                                exerciseBeingEdited = exercise
                                showCustomExerciseDialog = true
                            }
                        } else {
                            null
                        },
                        onDelete = if (isEditable) {
                            { exercisePendingDeletion = exercise }
                        } else {
                            null
                        }
                    )
                }

                if (displayedExercises.size < filteredExercises.size) {
                    item(key = "load_more_exercises_indicator", contentType = "loading_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = ElectricCyan,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Loading more exercises...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // The build stamp, once the athlete has scrolled to the very end of the catalog.
                item(key = "app_version_footer", contentType = "footer") {
                    AppVersionFooter()
                }
            }
        }

        // Quick Workout Resizable Tray (mimicking Routine Workout UI)
        if (activeSession != null) {
            val workout = activeSession!!

            var elapsedTimeSeconds by remember(workout.startTime) {
                mutableLongStateOf((System.currentTimeMillis() - workout.startTime).coerceAtLeast(0) / 1000)
            }
            LaunchedEffect(workout.startTime) {
                while (true) {
                    elapsedTimeSeconds = (System.currentTimeMillis() - workout.startTime).coerceAtLeast(0) / 1000
                    delay(1000)
                }
            }
            val minutes = elapsedTimeSeconds / 60
            val seconds = elapsedTimeSeconds % 60
            val timerFormatted = "%02d:%02d".format(minutes, seconds)

            val exercisesInSession = remember(activeSessionExercises, activeSets, allExercises) {
                if (activeSessionExercises.isNotEmpty()) {
                    activeSessionExercises
                } else {
                    activeSets.mapNotNull { set -> allExercises.find { it.id == set.exerciseId } }.distinctBy { it.id }
                }
            }
            val totalSetsCount = activeSets.size
            val completedSetsCount = activeSets.count { it.isCompleted }
            val progressFraction = if (totalSetsCount > 0) completedSetsCount.toFloat() / totalSetsCount.toFloat() else 0f

            LaunchedEffect(exercisesInSession, targetScrollTrigger) {
                if (targetScrollExerciseId != -1L && exercisesInSession.isNotEmpty()) {
                    delay(80)
                    val exerciseIdx = exercisesInSession.indexOfFirst { it.id == targetScrollExerciseId }
                    val targetIndex = if (exerciseIdx != -1) exerciseIdx + 1 else exercisesInSession.size
                    if (targetIndex in 0..exercisesInSession.size) {
                        workoutListState.animateScrollToItem(targetIndex, scrollOffset = 450)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(animatedTrayHeight)
                    .testTag("quick_workout_tray"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = if (trayState == QuickWorkoutTrayState.EXPANDED) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.8f))
            ) {
                if (trayState == QuickWorkoutTrayState.MINIMIZED) {
                    // ONE SINGLE BOX FOR HIDDEN/MINIMIZED MODE:
                    // Only shows a timer and the sets done of the workout. No multiple boxes, no broken second line!
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                autoCollapseJob?.cancel()
                                trayState = QuickWorkoutTrayState.PARTIAL
                            }
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Live Timer with pulsing green dot
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MintGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timerFormatted,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MintGreen,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Center: Sets done
                        Text(
                            text = "$completedSetsCount / $totalSetsCount Sets Done",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextWhite,
                            maxLines = 1,
                            softWrap = false
                        )

                        // Right: Expand arrow up
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand Workout",
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // PARTIAL or EXPANDED STATE
                    // Lifts with the keyboard, so a set row that is far down the list is not covered
                    // by it. The tray is where the weight and rep fields live, so this is the screen
                    // where the keyboard covers the field being typed into.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        // 1. Resizing & Control Header Bar (Unified into a single 48dp bar - NO CIRCLES)
                        Surface(
                            color = DarkSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { delta ->
                                        if (delta < -16) {
                                            autoCollapseJob?.cancel()
                                            if (trayState == QuickWorkoutTrayState.MINIMIZED) {
                                                trayState = QuickWorkoutTrayState.PARTIAL
                                            } else if (trayState == QuickWorkoutTrayState.PARTIAL) {
                                                trayState = QuickWorkoutTrayState.EXPANDED
                                            }
                                        } else if (delta > 16) {
                                            autoCollapseJob?.cancel()
                                            if (trayState == QuickWorkoutTrayState.EXPANDED) {
                                                trayState = QuickWorkoutTrayState.PARTIAL
                                            } else if (trayState == QuickWorkoutTrayState.PARTIAL) {
                                                trayState = QuickWorkoutTrayState.MINIMIZED
                                            }
                                        }
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp)
                            ) {
                                // Left: Exit Button + "Quick Workout" Title
                                Row(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { showExitWorkoutDialog = true },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("exit_quick_workout_top_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Exit Workout",
                                            tint = FireRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // The title is the name of the session that ends up in the
                                    // history, so tapping it renames the workout.
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { showRenameWorkoutDialog = true }
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                            .testTag("quick_workout_title")
                                    ) {
                                        Text(
                                            text = activeSession?.title?.ifBlank { "Quick Workout" } ?: "Quick Workout",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = TextWhite,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        AutoSaveIndicator(
                                            lastAutoSaveAt = lastAutoSaveAt,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                // Center: Perfectly Centered Drag Handle Bar + Centered Timer directly below it
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .clickable {
                                            autoCollapseJob?.cancel()
                                            trayState = if (trayState == QuickWorkoutTrayState.PARTIAL) {
                                                QuickWorkoutTrayState.EXPANDED
                                            } else {
                                                QuickWorkoutTrayState.PARTIAL
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Centered Drag Handle Pill
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .background(TextMuted.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    // Centered Timer under the centered bar
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(MintGreen, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = timerFormatted,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MintGreen,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                // Right: 3 Mode Buttons (NO CIRCLES!)
                                Row(
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Hide Button
                                    IconButton(
                                        onClick = {
                                            autoCollapseJob?.cancel()
                                            trayState = QuickWorkoutTrayState.MINIMIZED
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("tray_mode_minimize")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Hide Workout",
                                            tint = if (trayState == QuickWorkoutTrayState.MINIMIZED) ElectricCyan else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Half screen (1/3rd) Button
                                    IconButton(
                                        onClick = {
                                            autoCollapseJob?.cancel()
                                            trayState = QuickWorkoutTrayState.PARTIAL
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("tray_mode_partial")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Splitscreen,
                                            contentDescription = "Half Screen",
                                            tint = if (trayState == QuickWorkoutTrayState.PARTIAL) ElectricCyan else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Full screen Button
                                    IconButton(
                                        onClick = {
                                            autoCollapseJob?.cancel()
                                            trayState = QuickWorkoutTrayState.EXPANDED
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("tray_mode_expand")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = "Full Screen",
                                            tint = if (trayState == QuickWorkoutTrayState.EXPANDED) ElectricCyan else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Linear Progress Indicator
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = MintGreen,
                            trackColor = DarkSurfaceVariant
                        )

                        // Exercises Execution List
                        LazyColumn(
                            state = workoutListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "EXERCISES IN WORKOUT (${exercisesInSession.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextMuted
                                    )
                                    if (trayState == QuickWorkoutTrayState.PARTIAL) {
                                        Text(
                                            text = "Add more from library above ↑",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                            }

                            if (exercisesInSession.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FitnessCenter,
                                                contentDescription = null,
                                                tint = TextMuted,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "No exercises added yet",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = TextWhite
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Tap '+ Log' on any exercise in the database above to add it to this workout session.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = exercisesInSession,
                                    key = { _, exercise -> exercise.id }
                                ) { index, exercise ->
                                    val exerciseSets = activeSets.filter { it.exerciseId == exercise.id }
                                    RoutineExerciseExecutionCard(
                                        orderIndex = index + 1,
                                        exercise = exercise,
                                        prescription = "", // No yellow target bar
                                        sets = exerciseSets,
                                        unit = weightUnit,
                                        onAddSet = {
                                            autoCollapseJob?.cancel()
                                            targetScrollExerciseId = exercise.id
                                            targetScrollTrigger++
                                            viewModel.addSetToActiveSession(exercise)
                                        },
                                        onUpdateSet = { setIndexInWorkout, updatedSet ->
                                            autoCollapseJob?.cancel()
                                            viewModel.updateActiveSet(setIndexInWorkout, updatedSet)
                                        },
                                        onDeleteSet = { setIndexInWorkout ->
                                            autoCollapseJob?.cancel()
                                            viewModel.deleteActiveSet(setIndexInWorkout)
                                        },
                                        allWorkoutSets = activeSets,
                                        // Tapping the name opens the exercise's details.
                                        //
                                        // The quick workout used the same card as the routine workout
                                        // without passing this, so the name was dead text in one and
                                        // live in the other. Same component, same behaviour.
                                        onShowDetails = {
                                            autoCollapseJob?.cancel()
                                            exerciseShowingDetails = exercise
                                        }
                                    )
                                }
                            }
                        }

                        // Small button on the lower left (right on top of the number of sets completed)
                        RestTimerDockedButton(
                            viewModel = viewModel,
                            onOpenDialog = { showRestTimerDialog = true },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Bottom Floating Action Bar
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$completedSetsCount / $totalSetsCount Sets Completed",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (completedSetsCount == totalSetsCount && totalSetsCount > 0) MintGreen else TextWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Elapsed: $timerFormatted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MintGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { showExitWorkoutDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FireRed),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, FireRed.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("exit_quick_workout_btn")
                                    ) {
                                        Text("Exit", color = FireRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { showFinishDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("finish_quick_workout_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = DarkBg,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Finish", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // "Exercise added" banner: anchored right above the workout tray, so it can never cover
        // the tray itself, the docked rest timer button or the "x / y sets completed" counter.
        AnimatedVisibility(
            visible = addedExerciseName != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = animatedTrayHeight + 10.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, MintGreen.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("exercise_added_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(MintGreen.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MintGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Added to workout",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MintGreen
                    )
                    Text(
                        text = addedExerciseName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Rename Workout Dialog
    if (showRenameWorkoutDialog) {
        var renameValue by remember { mutableStateOf(activeSession?.title ?: "Quick Workout") }
        AlertDialog(
            onDismissRequest = { showRenameWorkoutDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Name this workout",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(60) },
                    singleLine = true,
                    label = { Text("Workout name", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("rename_workout_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameWorkoutDialog = false
                        viewModel.renameActiveWorkout(renameValue)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_rename_workout_button")
                ) {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = DarkBg
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameWorkoutDialog = false },
                    modifier = Modifier.testTag("cancel_rename_workout_button")
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Exit Workout Dialog (same layout as the saved routine workout exit dialog)
    if (showExitWorkoutDialog) {
        AlertDialog(
            onDismissRequest = { showExitWorkoutDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Exit Workout?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop this workout? Any in-progress sets from this session will be discarded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitWorkoutDialog = false
                        viewModel.discardActiveSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_exit_workout_button")
                ) {
                    Text(
                        text = "Exit Workout",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitWorkoutDialog = false },
                    modifier = Modifier.testTag("cancel_exit_workout_button")
                ) {
                    Text("Continue Workout", color = ElectricCyan)
                }
            }
        )
    }

    // Exit Routine Conflict Dialog
    if (showExitRoutinePrompt) {
        AlertDialog(
            onDismissRequest = {
                showExitRoutinePrompt = false
                pendingQuickWorkoutAction = null
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Routine in Progress",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                Text(
                    text = "You currently have an active routine workout in progress. Would you like to exit it to start a Quick Workout?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitRoutinePrompt = false
                        viewModel.stopRoutineWorkout()
                        pendingQuickWorkoutAction?.invoke()
                        pendingQuickWorkoutAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Exit & Start Quick Workout",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitRoutinePrompt = false
                        pendingQuickWorkoutAction = null
                    }
                ) {
                    Text("Keep Routine Workout", color = ElectricCyan)
                }
            }
        )
    }

    // Finish Workout Dialog
    if (showFinishDialog) {
        val completedCount = activeSets.count { it.isCompleted }
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            containerColor = DarkSurface,
            title = { Text("Complete Workout", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "You ticked $completedCount of ${activeSets.size} sets as done. Only the ticked sets are saved to your history and the Progress page.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (completedCount == 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No sets are ticked, so finishing now will not save a workout.",
                            color = FireRed,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = finishNotes,
                        onValueChange = { finishNotes = it },
                        placeholder = { Text("Felt strong, hit PR on pull-ups...", color = TextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("workout_finish_notes_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishAndSaveActiveSession(finishNotes)
                        showFinishDialog = false
                        finishNotes = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("save_workout_to_history_button")
                ) {
                    Text("Save to History", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Rest Timer Pop-up Dialog
    if (showRestTimerDialog) {
        RestTimerDialog(
            viewModel = viewModel,
            onDismiss = { showRestTimerDialog = false }
        )
    }

    // Full exercise information: fields to log, tags, the animation and the body parts it trains
    exerciseShowingDetails?.let { detailExercise ->
        // Read the row back from the live list rather than holding the snapshot that opened the
        // dialog.
        //
        // Media used to be attached, the database updated, and the dialog kept rendering the object
        // it was opened with, so the new photo or video only appeared after the athlete closed the
        // details and reopened them. Looking the id up in the observable list means every write the
        // dialog triggers is visible immediately, because the next emission carries the new row.
        val liveDetail = allExercises.firstOrNull { it.id == detailExercise.id } ?: detailExercise
        ExerciseDetailDialog(
            exercise = liveDetail,
            unit = weightUnit.suffix,
            onAddSet = {
                exerciseShowingDetails = null
                safeAddExercise(detailExercise)
            },
            onDismiss = { exerciseShowingDetails = null },
            autoplayDemos = autoplayDemos,
            onAttachMedia = if (ExerciseMedia.isUserOwnedMedia(liveDetail.isCustom)) {
                {
                    pendingMediaExerciseId = liveDetail.id
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            } else {
                null
            },
            onRemoveMedia = if (ExerciseMedia.isUserOwnedMedia(liveDetail.isCustom)) {
                { viewModel.clearExerciseMedia(liveDetail.id) }
            } else {
                null
            },
            // The pen opens the low level editor. Offered on every exercise, library rows included:
            // the shipped catalog is not always right and the editor asks before writing anything.
            //
            // Not offered while a workout is running. The athlete is mid set, the logging screen is
            // showing them the very fields this edit would change, and editing an exercise's type or
            // muscles underneath an in-progress session is exactly the kind of thing that should wait
            // until they have finished lifting.
            //
            // The detail dialog also stays open behind the editor. It used to be dismissed, so after
            // saving an edit the athlete was left on the list and had to open the details again to
            // see what they had just changed. Leaving it open, and reading the live row, means the
            // edit is confirmed the moment the editor closes.
            onEdit = if (activeSession == null) {
                {
                    exerciseBeingEditedFull = liveDetail
                }
            } else {
                null
            }
        )
    }

    // The low level editor, reached from the pen above. It covers every field that decides what the
    // logging screen asks for, so a wrong exercise type or muscle group can be fixed completely
    // rather than half way.
    exerciseBeingEditedFull?.let { edited ->
        val canOwnMedia = ExerciseMedia.isUserOwnedMedia(edited.isCustom)
        ExerciseEditorDialog(
            exercise = edited,
            // One save path for every exercise. The view model picks the guarded or unguarded write
            // depending on who owns the row, and both write every field the editor exposes.
            onSave = { updated -> viewModel.saveExerciseEdit(updated) },
            onDismiss = { exerciseBeingEditedFull = null },
            onAttachMedia = if (canOwnMedia) {
                {
                    pendingMediaExerciseId = edited.id
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            } else {
                null
            },
            onRemoveMedia = if (canOwnMedia) {
                { viewModel.clearExerciseMedia(edited.id) }
            } else {
                null
            }
        )
    }

    // Searchable body part picker, opened from the button floating over the category chips
    if (showMuscleSelector) {
        MuscleSelectorDialog(
            selected = selectedMusclePart,
            onSelect = { part ->
                viewModel.setSelectedMusclePart(part)
                showMuscleSelector = false
            },
            onDismiss = { showMuscleSelector = false }
        )
    }

    // Create / Edit Custom Exercise Dialog
    if (showCustomExerciseDialog) {
        val editing = exerciseBeingEdited
        CustomExerciseDialog(
            existingExercise = editing,
            prefillName = if (editing == null) prefillCustomExerciseName else "",
            findSimilar = { candidateName -> viewModel.findSimilarExercise(candidateName, editing?.id ?: 0L) },
            onSave = { name, category, equipment, type, description ->
                if (editing == null) {
                    viewModel.createCustomExercise(name, category, equipment, type, description)
                } else {
                    viewModel.updateCustomExercise(editing.id, name, category, equipment, type, description)
                }
            },
            onDismiss = {
                showCustomExerciseDialog = false
                exerciseBeingEdited = null
                prefillCustomExerciseName = ""
            },
            // Media is attached here, where the picker already exists for the detail dialog, so the
            // creation and edit flows share one picker instead of racing each other for a result.
            // Null for anything the athlete does not own: the library rows are not theirs to change.
            //
            // A new exercise has no id until it is saved, so its picker waits for the save result
            // rather than opening now and dropping the chosen uri.
            onAttachMedia = when {
                !(editing == null || ExerciseMedia.isUserOwnedMedia(editing.isCustom)) -> null
                editing == null -> {
                    {
                        showCustomExerciseDialog = false
                        pendingMediaAfterCreate = true
                    }
                }
                else -> {
                    {
                        pendingMediaExerciseId = editing.id
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                }
            },
            onRemoveMedia = if (editing != null && ExerciseMedia.isUserOwnedMedia(editing.isCustom)) {
                { viewModel.clearExerciseMedia(editing.id) }
            } else {
                null
            },
            hasAttachedMedia = editing != null && editing.customMediaUri.isNotBlank()
        )
    }

    // One effect for both concerns, in the order they have to happen.
    //
    // This used to be two effects reading the same customExerciseResult. The close effect consumed
    // the result as soon as it was set, so by the time the media effect ran its when saw null and it
    // reset its own flag without ever launching the picker. The athlete pressed "Add", the dialog
    // closed, and nothing else happened, which is exactly the reported behaviour.
    //
    // Closing first, then launching the picker for a brand new exercise, whose id only exists now.
    LaunchedEffect(customExerciseResult) {
        when (val result = customExerciseResult) {
            is MainViewModel.CustomExerciseResult.Success -> {
                showCustomExerciseDialog = false
                exerciseBeingEdited = null
                prefillCustomExerciseName = ""
                // A media request made while creating has no exercise to attach to until this
                // moment, so the picker goes now and remembers the new id.
                if (pendingMediaAfterCreate) {
                    pendingMediaAfterCreate = false
                    pendingMediaExerciseId = result.exercise.id
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
                // Consumed last: the launch above still needs this result's id.
                viewModel.consumeCustomExerciseResult()
            }
            is MainViewModel.CustomExerciseResult.Duplicate -> {
                // Nothing was stored, so there is nothing to attach to.
                pendingMediaAfterCreate = false
            }
            is MainViewModel.CustomExerciseResult.Error -> {
                pendingMediaAfterCreate = false
            }
            null -> Unit
        }
    }

    // Delete confirmation for one of the user's own exercises
    exercisePendingDeletion?.let { exerciseToDelete ->
        DeleteCustomExerciseDialog(
            exercise = exerciseToDelete,
            onConfirm = {
                viewModel.deleteCustomExercise(exerciseToDelete)
                exercisePendingDeletion = null
            },
            onDismiss = { exercisePendingDeletion = null }
        )
    }

    // The picker handed back something that could not be copied into the app's storage, so nothing
    // was attached. Said out loud rather than failing quietly: a blank media box with no
    // explanation is indistinguishable from the media silently disappearing, which is the bug this
    // whole flow exists to fix.
    if (mediaImportFailed) {
        AlertDialog(
            onDismissRequest = { mediaImportFailed = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Could not add that media",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    modifier = Modifier.testTag("media_import_failed_title")
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
                    modifier = Modifier.testTag("media_import_failed_dismiss")
                ) {
                    Text("OK", color = ElectricCyan)
                }
            }
        )
    }
}

@Composable
fun ExerciseCard(
    exercise: Exercise,
    unit: String,
    onClick: (() -> Unit)? = null,
    onAddToWorkout: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val (badgeBg, badgeText, badgeColor) = remember(exercise.exerciseType) {
        when (exercise.exerciseType) {
            ExerciseType.ADDED_WEIGHT -> Triple(AddedWtBadgeBg, "Added Wt / Cali", CoralOrange)
            ExerciseType.PURE_BODYWEIGHT -> Triple(BodyweightBadgeBg, "Bodyweight", MintGreen)
            ExerciseType.CARDIO -> Triple(CardioBadgeBg, "Cardio", GoldenAmber)
            else -> Triple(StandardWtBadgeBg, "Standard Wt", ElectricCyan)
        }
    }

    val fieldsSummary = remember(exercise.exerciseType, unit) {
        when (exercise.exerciseType) {
            ExerciseType.STANDARD_WEIGHT -> "Fields: Sets • Reps • Weight ($unit)"
            ExerciseType.ADDED_WEIGHT -> "Fields: Reps • Added Weight (+$unit)"
            ExerciseType.PURE_BODYWEIGHT -> "Fields: Reps / Hold (seconds)"
            ExerciseType.CARDIO -> "Fields: Time • Distance (km)"
            else -> "Fields: Sets • Reps"
        }
    }

    // A still frame of the movement, streamed on demand. There is deliberately no "Play" row under
    // it: the whole card opens the detail dialog, which is where the animation belongs. A label
    // between them only pushed the rest of the card down for something the athlete already knows.
    val hasMedia =
        ExerciseMedia.resolve(exercise.customMediaUri, exercise.customMediaIsVideo, exercise.mediaPath) !=
            ExerciseMedia.Source.NONE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("exercise_card_${exercise.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = ExerciseCardShape,
        border = ExerciseCardBorder
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Only ever drawn for exercises that actually have media, so rows never get a
                // placeholder hole where an animation could be.
                if (hasMedia) {
                    ExerciseGifThumbnail(
                        localUri = exercise.customMediaUri,
                        localIsVideo = exercise.customMediaIsVideo,
                        mediaPath = exercise.mediaPath
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Tapping the name opens the same dialog the Exercises tab uses, so the detail is
                    // one tap away from inside a workout rather than only from the catalog list.
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (onClick != null) {
                                    Modifier.clickable(onClick = onClick)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 2.dp)
                            .testTag("exercise_card_name_${exercise.id}")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${exercise.equipment} • ${exercise.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .background(badgeBg, ExerciseBadgeShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
            }

            // Ownership marker: only the exercises the user created or the AI registered can be
            // edited / deleted. User created = green, AI generated = light blue.
            //
            // An AI registered exercise is custom too, so isCustom alone would label it as the
            // user's own. The AI flag has to win: it is the more specific fact about who made it.
            val isUserOwned = exercise.isUserCreated
            val isAiOwned = exercise.isAiCreated
            if (isUserOwned || isAiOwned) {
                val markerColor = if (isUserOwned) MintGreen else AiBlue
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(markerColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .testTag(
                            if (isUserOwned) {
                                "user_created_marker_${exercise.id}"
                            } else {
                                "ai_generated_marker_${exercise.id}"
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isUserOwned) Icons.Default.Person else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = markerColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUserOwned) "Created by you" else "AI generated",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = markerColor
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        onEdit?.let { editAction ->
                            IconButton(
                                onClick = editAction,
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("edit_custom_exercise_${exercise.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit your exercise",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        onDelete?.let { deleteAction ->
                            IconButton(
                                onClick = deleteAction,
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("delete_custom_exercise_${exercise.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete your exercise",
                                    tint = FireRed.copy(alpha = 0.85f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            val inlineGuidance = exercise.instructions.ifBlank { exercise.description }
            if (inlineGuidance.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = inlineGuidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fieldsSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                Button(
                    onClick = onAddToWorkout,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    border = ExerciseButtonBorder,
                    shape = ExerciseButtonShape,
                    contentPadding = ExerciseButtonPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Set",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveSetRow(
    setIndex: Int,
    set: LoggedSet,
    unit: String,
    onUpdateSet: (LoggedSet) -> Unit,
    onDeleteSet: () -> Unit
) {
    // The label arrives as a string; the fields below need the real unit to convert with
    val weightUnit = WeightUnit.fromLabel(unit)
    val fieldColors = activeFieldColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_set_row_$setIndex"),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Set ${set.setNumber}: ${set.exerciseName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                }

                IconButton(
                    onClick = onDeleteSet,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Set",
                        tint = FireRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Fields according to exerciseType
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (set.exerciseType) {
                    ExerciseType.STANDARD_WEIGHT -> {
                        // Standard Weight: the field speaks the athlete's unit, the set stores kg
                        WeightInputField(
                            label = "Weight ($unit)",
                            kgValue = set.weight,
                            unit = weightUnit,
                            onKgChanged = { kg -> onUpdateSet(set.copy(weight = kg)) },
                            modifier = Modifier.weight(1f),
                            testTag = "set_weight_input_${set.id}"
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reps", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            OutlinedTextField(
                                value = if (set.reps == 0) "" else set.reps.toString(),
                                onValueChange = { newVal ->
                                    val parsed = newVal.toIntOrNull() ?: 0
                                    onUpdateSet(set.copy(reps = parsed))
                                },
                                placeholder = { Text("0", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors
                            )
                        }
                    }

                    ExerciseType.ADDED_WEIGHT -> {
                        // Added Weight / Calisthenics: the field speaks the athlete's unit, the set
                        // stores kilograms
                        WeightInputField(
                            label = "Added Wt (+$unit)",
                            kgValue = set.addedWeight,
                            unit = weightUnit,
                            onKgChanged = { kg -> onUpdateSet(set.copy(addedWeight = kg)) },
                            labelColor = CoralOrange,
                            placeholder = "+0",
                            modifier = Modifier.weight(1f),
                            testTag = "set_added_weight_input_${set.id}"
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reps", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            OutlinedTextField(
                                value = if (set.reps == 0) "" else set.reps.toString(),
                                onValueChange = { newVal ->
                                    val parsed = newVal.toIntOrNull() ?: 0
                                    onUpdateSet(set.copy(reps = parsed))
                                },
                                placeholder = { Text("0", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors
                            )
                        }
                    }

                    ExerciseType.CARDIO -> {
                        // Distance work: a duration and a distance. There is no weight and no rep
                        // count here, because a 5 km run has neither, and asking for them produced
                        // the "Set 1: Run, 80 kg x 8" nonsense the athlete reported.
                        val distanceLabel = "Distance (${DistanceFormat.suffixFor(weightUnit)})"

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Time (min)", style = MaterialTheme.typography.labelSmall, color = GoldenAmber)
                            OutlinedTextField(
                                value = if (set.holdDurationSeconds == 0) {
                                    ""
                                } else {
                                    // Stored as seconds; a run is recorded in minutes.
                                    (set.holdDurationSeconds / 60).toString()
                                },
                                onValueChange = { newVal ->
                                    val minutes = newVal.toIntOrNull() ?: 0
                                    onUpdateSet(set.copy(holdDurationSeconds = minutes * 60))
                                },
                                placeholder = { Text("0", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                distanceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            OutlinedTextField(
                                value = DistanceFormat.format(set.distanceKm, weightUnit),
                                onValueChange = { newVal ->
                                    val parsed = DistanceFormat.toKm(newVal, weightUnit)
                                    onUpdateSet(set.copy(distanceKm = parsed))
                                },
                                placeholder = { Text("0.0", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors
                            )
                        }
                    }

                    ExerciseType.PURE_BODYWEIGHT -> {
                        // Pure Bodyweight: Reps or Hold Duration (seconds)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reps", style = MaterialTheme.typography.labelSmall, color = MintGreen)
                            OutlinedTextField(
                                value = if (set.reps == 0) "" else set.reps.toString(),
                                onValueChange = { newVal ->
                                    val parsed = newVal.toIntOrNull() ?: 0
                                    onUpdateSet(set.copy(reps = parsed))
                                },
                                placeholder = { Text("0", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hold (seconds)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            OutlinedTextField(
                                value = if (set.holdDurationSeconds == 0) "" else set.holdDurationSeconds.toString(),
                                onValueChange = { newVal ->
                                    val parsed = newVal.toIntOrNull() ?: 0
                                    onUpdateSet(set.copy(holdDurationSeconds = parsed))
                                },
                                placeholder = { Text("0s", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors
                            )
                        }
                    }
                }

                // Completed Checkbox toggle
                IconButton(
                    onClick = { onUpdateSet(set.copy(isCompleted = !set.isCompleted)) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (set.isCompleted) MintGreen.copy(alpha = 0.2f) else DarkBg,
                            CircleShape
                        )
                        .border(1.dp, if (set.isCompleted) MintGreen else DarkBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = if (set.isCompleted) MintGreen else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun activeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkBg,
    unfocusedContainerColor = DarkBg,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite
)
