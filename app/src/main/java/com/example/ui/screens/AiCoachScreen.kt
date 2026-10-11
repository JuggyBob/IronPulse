package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.example.ui.components.PredefinedRoutinesSheet
import com.example.ui.components.AppVersionFooter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatSession
import com.example.data.model.SavedRoutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.AiUnavailableDialog
import com.example.ui.components.MarkdownView
import com.example.ui.components.parseInlineMarkdown
import com.example.ui.components.RoutineExerciseListDisplay
import com.example.ui.components.SelectRoutineDayDialog
import com.example.data.model.Exercise
import com.example.data.model.RoutineDay
import com.example.data.model.RoutineParser
import com.example.ui.viewmodel.ChatMessage
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AiCoachScreen(
    viewModel: MainViewModel,
    onNavigateToLogWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("AI Coach Chat", "Routine Generator", "Saved Routines")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Sub-Tab Switcher - Evenly Distributed Layout with equal weight tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .border(
                    width = 1.dp,
                    color = DarkBorder,
                    shape = RoundedCornerShape(0.dp)
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            subTabs.forEachIndexed { index, title ->
                val isSelected = selectedSubTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedSubTab = index }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) ElectricCyan else TextMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(3.dp)
                                .background(
                                    if (isSelected) ElectricCyan else Color.Transparent,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                )
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedSubTab) {
                0 -> AiChatView(viewModel)
                1 -> AiRoutineGeneratorView(
                    viewModel = viewModel,
                    onStartRoutine = {
                        onNavigateToLogWorkout()
                    },
                    onNavigateToSavedRoutines = {
                        selectedSubTab = 2
                    }
                )
                2 -> SavedRoutinesView(
                    viewModel = viewModel,
                    onLaunchRoutine = { _ ->
                        // Dedicated routine workout runs in RoutineWorkoutScreen full-screen
                        onNavigateToLogWorkout()
                    }
                )
            }
        }
    }

    // Shared dialog: shown instead of mocked content when Gemini cannot be used
    val aiErrorDialog by viewModel.aiErrorDialog.collectAsStateWithLifecycle()
    aiErrorDialog?.let { errorState ->
        AiUnavailableDialog(
            title = errorState.title,
            message = errorState.message,
            onDismiss = { viewModel.dismissAiError() }
        )
    }
}

@Composable
fun AiChatView(viewModel: MainViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val sessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentChatSession.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var inputQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxHeight(),
                drawerContainerColor = DarkSurface,
                drawerContentColor = TextWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Chat History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                        }

                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.close() } },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("close_chat_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Menu",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Create New Chat" Button
                    Button(
                        onClick = {
                            viewModel.createNewChatSession()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("side_panel_new_chat_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = DarkBg
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Chat",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREVIOUS CONVERSATIONS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // List of started chats
                    if (sessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = TextMuted.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No saved chats yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sessions, key = { it.id }) { session ->
                                val isSelected = session.id == currentSession?.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectChatSession(session)
                                            coroutineScope.launch { drawerState.close() }
                                        }
                                        .testTag("chat_session_${session.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) DarkSurfaceVariant else DarkSurfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) ElectricCyan else DarkBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = null,
                                            tint = if (isSelected) ElectricCyan else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) TextWhite else TextWhite.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = dateFormat.format(Date(session.lastUpdatedAt)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteChatSession(session) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("delete_chat_${session.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete chat",
                                                tint = FireRed.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                        // The build stamp sits at the foot of the history sidebar.
                        //
                        // It was inside the chat list, where it appeared wherever the athlete stopped
                        // scrolling: a stamp in the middle of a conversation, splitting the thread and
                        // re-rendering every time they scrolled past it. A build number is diagnostic
                        // information about the app, not part of the conversation, so it belongs with
                        // the other app level chrome here, and it stays put.
                        AppVersionFooter(
                            modifier = Modifier
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            compact = true
                        )
                    }

                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Top Bar with Hamburger ("3 line button"), Session Title, and "+ New Chat" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "3 line button" (Hamburger menu)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(DarkSurfaceVariant, CircleShape)
                        .border(1.dp, DarkBorder, CircleShape)
                        .clickable { coroutineScope.launch { drawerState.open() } }
                        .testTag("chat_hamburger_menu_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Chat Menu",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Session Title - safely occupies remaining space without clipping buttons
                Text(
                    text = currentSession?.title ?: "IronPulse Coach",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // "+ New Chat" Quick Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(DarkSurfaceVariant, CircleShape)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape)
                        .clickable { viewModel.createNewChatSession() }
                        .testTag("chat_header_new_chat_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "IronPulse AI is analyzing biomechanics...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                // No app version footer here.
                //
                // It used to be an item at the bottom of the chat, which put a build stamp in the
                // middle of a conversation: it appeared at whatever point the athlete stopped
                // scrolling, so it split the thread rather than marked its end, and it re-rendered as
                // they scrolled past. The version now lives in the history sidebar, where a build
                // stamp belongs, and every other page shows it at the end of its scroll.
            }

            // Input Bar
            Surface(
                color = DarkSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Ask Coach Gemini...", color = TextMuted) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input"),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        singleLine = false,
                        minLines = 1,
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputQuery.isNotBlank()) {
                                viewModel.sendChatMessage(inputQuery)
                                inputQuery = ""
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(44.dp)
                            .background(ElectricCyan, CircleShape)
                            .testTag("ai_chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = DarkBg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One chat message.
 *
 * The text can be selected with a long press so a single line can be copied, and every answer
 * generated by the coach carries a clipboard button that copies the whole message at once.
 */
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == ChatMessage.Sender.USER
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var copiedJob by remember { mutableStateOf<Job?>(null) }

    fun copyWholeMessage() {
        clipboard.setText(AnnotatedString(message.text))
        copied = true
        copiedJob?.cancel()
        copiedJob = scope.launch {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        // The coach's badge hangs from the top of the message, level with its first line.
        //
        // It used to sit against the bottom edge, which put it beside the copy row instead of beside
        // the answer, so the eye met the icon after reading the message rather than before it. Sunk
        // to the bottom it also detached from short messages and floated clear of tall ones.
        verticalAlignment = Alignment.Top
    ) {
        // The coach's badge is a column of its own on the left of the bubble, not a row underneath
        // it. Underneath it read as part of the message: the bubble's tail pointed down at the icon
        // directly beneath it, so the icon looked like it belonged to the text above rather than
        // identifying who sent it. On the left, clear of the bubble, the tail points at the badge,
        // which is what a tail is for.
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 2.dp)
                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, ElectricCyan, CircleShape)
                    .testTag("coach_badge_${message.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "IronPulse coach",
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Card(
                modifier = Modifier.widthIn(max = 330.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) ElectricCyan.copy(alpha = 0.2f) else DarkSurfaceVariant
                ),
                shape = RoundedCornerShape(
                    // The pointed corner is the one nearest the badge, so the tail aims at it. The
                    // badge hangs from the top of the message, so the tail now points up and to the
                    // left rather than down; the bottom stays flat because the copy row lives there.
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isUser) ElectricCyan.copy(alpha = 0.6f) else DarkBorder
                )
            ) {
                // Long press anywhere inside a message selects the text under the finger
                SelectionContainer {
                    if (isUser) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        MarkdownView(
                            markdown = message.text,
                            modifier = Modifier.padding(12.dp),
                            baseTextColor = TextWhite
                        )
                    }
                }

                // The copy action lives on its own line inside the card, at the very end.
                //
                // It used to be a separate chip under the bubble, where it read as a reply to the
                // message rather than part of it: the eye followed the bubble down and stopped at a
                // second, differently shaped panel, so even a one line answer ended up with two
                // visual footers. Inside the card it is simply the last row of the same panel.
                //
                // Icon only, with no "Copy" word: the action is universally understood, the label
                // forced the row wider than the narrowest message it sits under, and the outcome is
                // already carried by the tick that replaces the icon once it is tapped.
                if (!isUser && message.text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (copied) MintGreen.copy(alpha = 0.15f) else DarkBg
                                )
                                .border(
                                    1.dp,
                                    if (copied) MintGreen.copy(alpha = 0.5f) else DarkBorder,
                                    CircleShape
                                )
                                .clickable { copyWholeMessage() }
                                .testTag("copy_message_button_${message.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = if (copied) MintGreen else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How long the "Copied" confirmation stays visible. */
private const val COPIED_FEEDBACK_MS = 1_600L

@Composable
fun AiRoutineGeneratorView(
    viewModel: MainViewModel,
    onStartRoutine: () -> Unit,
    onNavigateToSavedRoutines: () -> Unit
) {
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val goal by viewModel.routineGoal.collectAsStateWithLifecycle()
    val days by viewModel.routineDaysPerWeek.collectAsStateWithLifecycle()
    val equipment by viewModel.routineEquipment.collectAsStateWithLifecycle()
    val generatedText by viewModel.generatedRoutine.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingRoutine.collectAsStateWithLifecycle()
    var showUnstructuredDialog by remember { mutableStateOf(false) }
    var showPromptWorkoutRoutineDialog by remember { mutableStateOf(false) }
    var generatedRoutineForDayPicker by remember { mutableStateOf<SavedRoutine?>(null) }
    val activeQuickWorkout by viewModel.activeSession.collectAsStateWithLifecycle()
    var showExitQuickWorkoutPrompt by remember { mutableStateOf(false) }
    var pendingRoutineLaunch by remember { mutableStateOf<(() -> Unit)?>(null) }

    val safeStartRoutine: (() -> Unit) -> Unit = { action ->
        if (activeQuickWorkout != null) {
            pendingRoutineLaunch = action
            showExitQuickWorkoutPrompt = true
        } else {
            action()
        }
    }

    val goals = listOf(
        "Strength & Power",
        "Hypertrophy & Muscle Mass",
        "Muscle-up & Calisthenics Mastery",
        "Fat Loss & Conditioning"
    )

    val equipmentOptions = listOf(
        "Pull-up Bar, Dip Bars, & Dumbbells",
        "Full Gym & Heavy Free Weights",
        "Pure Bodyweight / No Equipment",
        "Gymnastic Rings & Weight Vest"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GoldenAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Routine Generator",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                viewModel.resetCustomRoutinePromptState()
                                showPromptWorkoutRoutineDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DarkBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("prompt_workout_routine_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Prompt for a workout routine",
                                tint = DarkBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Prompt for Routine",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkBg,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Goal selector
                    Text("FITNESS GOAL", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(goals) { g ->
                            FilterChip(
                                selected = goal == g,
                                onClick = { viewModel.routineGoal.value = g },
                                label = { Text(g, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan,
                                    selectedLabelColor = DarkBg,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextWhite
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Days per week selector
                    Text("TRAINING DAYS PER WEEK", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 4, 5, 6).forEach { d ->
                            FilterChip(
                                selected = days == d,
                                onClick = { viewModel.routineDaysPerWeek.value = d },
                                label = {
                                    Text(
                                        text = "$d Days",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MintGreen,
                                    selectedLabelColor = DarkBg,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextWhite
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("days_chip_$d")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Equipment selector
                    Text("AVAILABLE EQUIPMENT", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(equipmentOptions) { eq ->
                            FilterChip(
                                selected = equipment == eq,
                                onClick = { viewModel.routineEquipment.value = eq },
                                label = { Text(eq, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VioletPurple,
                                    selectedLabelColor = TextWhite,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextWhite
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { viewModel.generateRoutineWithAI() },
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_routine_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DarkBg,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Formulating Program...", color = DarkBg, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = DarkBg,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Structured Routine", color = DarkBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Generated Routine Output Card
        if (generatedText.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generated_routine_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GENERATED PROGRAM",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MintGreen
                            )

                            Button(
                                onClick = {
                                    // The day count comes from the parsed answer, never from the preset
                                    val realDays = RoutineParser.parseDays(generatedText, allExercises)
                                        .size.coerceAtLeast(1)
                                    val title = "AI $goal ($realDays-Day)"
                                    viewModel.saveRoutine(
                                        SavedRoutine(
                                            name = title,
                                            description = "AI Generated for $goal using $equipment",
                                            daysPerWeek = realDays,
                                            goal = goal,
                                            exercisesJson = generatedText
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("save_routine_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = DarkBg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // The generator answers with strict JSON that is parsed into the app's own
                        // routine format, so it is previewed with the structured day-by-day list
                        // instead of showing raw JSON to the user.
                        var isProgramExpanded by remember { mutableStateOf(true) }
                        val generatedDays = remember(generatedText, allExercises) {
                            RoutineParser.parseDays(generatedText, allExercises)
                        }
                        // The real day count comes from the parsed answer, not from the preset
                        val actualDayCount = generatedDays.size.coerceAtLeast(1)
                        val previewRoutine = remember(generatedText, actualDayCount) {
                            SavedRoutine(
                                name = "AI $goal ($actualDayCount-Day)",
                                description = "",
                                daysPerWeek = actualDayCount,
                                goal = goal,
                                exercisesJson = generatedText
                            )
                        }
                        RoutineExerciseListDisplay(
                            routine = previewRoutine,
                            allExercises = allExercises,
                            isExpanded = isProgramExpanded,
                            onToggleExpand = { isProgramExpanded = !isProgramExpanded }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Start Workout
                        Button(
                            onClick = {
                                val parsedDays = RoutineParser.parseDays(generatedText, allExercises)
                                val realDays = parsedDays.size.coerceAtLeast(1)
                                val routine = SavedRoutine(
                                    name = "AI $goal Session",
                                    description = "AI Program Protocol",
                                    daysPerWeek = realDays,
                                    goal = goal,
                                    exercisesJson = generatedText
                                )
                                if (parsedDays.size > 1) {
                                    generatedRoutineForDayPicker = routine
                                } else {
                                    safeStartRoutine {
                                        viewModel.saveRoutine(routine)
                                        viewModel.startRoutineWorkout(routine, targetDayNumber = 1)
                                        onStartRoutine()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_generated_routine_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = DarkBg
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Workout", color = DarkBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        }

        if (generatedRoutineForDayPicker != null) {
            val planDays = remember(generatedRoutineForDayPicker, allExercises) {
                RoutineParser.parseDays(generatedRoutineForDayPicker!!.exercisesJson, allExercises)
            }
            SelectRoutineDayDialog(
                routineName = generatedRoutineForDayPicker!!.name,
                days = planDays,
                onSelectDay = { day ->
                    val r = generatedRoutineForDayPicker!!
                    generatedRoutineForDayPicker = null
                    safeStartRoutine {
                        viewModel.saveRoutine(r)
                        viewModel.startRoutineWorkout(r, targetDayNumber = day.dayNumber)
                        onStartRoutine()
                    }
                },
                onDismiss = { generatedRoutineForDayPicker = null }
            )
        }

        // Prompt user if they attempt to launch a routine while a quick workout is active
        if (showExitQuickWorkoutPrompt) {
            AlertDialog(
                onDismissRequest = {
                    showExitQuickWorkoutPrompt = false
                    pendingRoutineLaunch = null
                },
                containerColor = DarkSurface,
                title = {
                    Text(
                        text = "Workout in Progress",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                },
                text = {
                    Text(
                        text = "You currently have a Quick Workout in progress. Would you like to exit your current workout to start this routine?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitQuickWorkoutPrompt = false
                            viewModel.discardActiveSession()
                            pendingRoutineLaunch?.invoke()
                            pendingRoutineLaunch = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Exit & Start Routine",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showExitQuickWorkoutPrompt = false
                            pendingRoutineLaunch = null
                        }
                    ) {
                        Text("Keep Current Workout", color = ElectricCyan)
                    }
                }
            )
        }

        // Bottom Right Button for Unstructured Workout Parser
        FloatingActionButton(
            onClick = {
                viewModel.resetUnstructuredParseState()
                showUnstructuredDialog = true
            },
            containerColor = ElectricCyan,
            contentColor = DarkBg,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("unstructured_parser_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "AI Parser",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI Parser",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (showUnstructuredDialog) {
            UnstructuredWorkoutDialog(
                viewModel = viewModel,
                onDismiss = { showUnstructuredDialog = false },
                onNavigateToSavedRoutines = {
                    showUnstructuredDialog = false
                    onNavigateToSavedRoutines()
                },
                onStartWorkoutDirectly = {
                    showUnstructuredDialog = false
                    safeStartRoutine {
                        onStartRoutine()
                    }
                }
            )
        }

        if (showPromptWorkoutRoutineDialog) {
            PromptWorkoutRoutineDialog(
                viewModel = viewModel,
                onDismiss = { showPromptWorkoutRoutineDialog = false },
                onNavigateToSavedRoutines = {
                    showPromptWorkoutRoutineDialog = false
                    onNavigateToSavedRoutines()
                },
                onStartWorkoutDirectly = {
                    showPromptWorkoutRoutineDialog = false
                    safeStartRoutine {
                        onStartRoutine()
                    }
                }
            )
        }
    }
}

@Composable
fun SavedRoutinesView(
    viewModel: MainViewModel,
    onLaunchRoutine: (SavedRoutine) -> Unit
) {
    val routines by viewModel.savedRoutines.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    var showCreateRoutineDialog by remember { mutableStateOf(false) }
    var routineBeingEdited by remember { mutableStateOf<SavedRoutine?>(null) }
    var routinePendingDeletion by remember { mutableStateOf<SavedRoutine?>(null) }
    var routineForDayPicker by remember { mutableStateOf<SavedRoutine?>(null) }
    val activeQuickWorkout by viewModel.activeSession.collectAsStateWithLifecycle()
    var showExitQuickWorkoutPrompt by remember { mutableStateOf(false) }
    var pendingRoutineLaunch by remember { mutableStateOf<(() -> Unit)?>(null) }
    // The starter routine browser, opened from the square button beside "Create New Routine".
    var showPredefinedRoutines by remember { mutableStateOf(false) }

    val safeStartRoutine: (() -> Unit) -> Unit = { action ->
        if (activeQuickWorkout != null) {
            pendingRoutineLaunch = action
            showExitQuickWorkoutPrompt = true
        } else {
            action()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Saved Routines (${routines.size})",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Text(
                            text = "Launch workouts directly with your prescribed exercises in exact order",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    // Top-right "+" button to create new routine
                    IconButton(
                        onClick = {
                            routineBeingEdited = null
                            showCreateRoutineDialog = true
                        },
                        modifier = Modifier
                            .background(DarkSurfaceVariant, CircleShape)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape)
                            .size(38.dp)
                            .testTag("add_routine_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Routine",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (routines.isEmpty()) {
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
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No saved routines yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create your own custom routine using the button below or generate one with the AI Coach!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(routines, key = { it.id }) { routine ->
                    SavedRoutineCard(
                        routine = routine,
                        allExercises = allExercises,
                        onStartWorkout = {
                            val days = RoutineParser.parseDays(routine.exercisesJson, allExercises)
                            if (days.size > 1) {
                                routineForDayPicker = routine
                            } else {
                                safeStartRoutine {
                                    viewModel.startRoutineWorkout(routine, targetDayNumber = 1)
                                    onLaunchRoutine(routine)
                                }
                            }
                        },
                        onDelete = { routinePendingDeletion = routine },
                        onDuplicate = { viewModel.duplicateRoutine(routine) },
                        onEdit = {
                            routineBeingEdited = routine
                            showCreateRoutineDialog = true
                        }
                    )
                }
            }

            // The two bottom actions: build one from scratch, or start from a template.
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            routineBeingEdited = null
                            showCreateRoutineDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("create_new_routine_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create New Routine",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }

                    // Square button, styled like the one beside the Gemini monthly summary: the
                    // templates are a shortcut into the same thing the button on its left creates.
                    IconButton(
                        onClick = { showPredefinedRoutines = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .border(
                                1.dp,
                                ElectricCyan.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("open_predefined_routines_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "Browse starter routines",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Starter routine browser. Adopting one saves an ordinary routine the athlete then owns.
        if (showPredefinedRoutines) {
            PredefinedRoutinesSheet(
                allExercises = allExercises,
                onAdopt = { template ->
                    viewModel.adoptPredefinedRoutine(template)
                    showPredefinedRoutines = false
                },
                onDismiss = { showPredefinedRoutines = false }
            )
        }

        // Deleting a saved routine always asks for confirmation first (a mis-tap must not destroy it)
        routinePendingDeletion?.let { routine ->
            DeleteRoutineConfirmDialog(
                routine = routine,
                onConfirm = {
                    routinePendingDeletion = null
                    viewModel.deleteRoutine(routine)
                },
                onDismiss = { routinePendingDeletion = null }
            )
        }

        // Multi-day selector dialog
        if (routineForDayPicker != null) {
            val pickerDays = remember(routineForDayPicker, allExercises) {
                RoutineParser.parseDays(routineForDayPicker!!.exercisesJson, allExercises)
            }
            SelectRoutineDayDialog(
                routineName = routineForDayPicker!!.name,
                days = pickerDays,
                onSelectDay = { day ->
                    val r = routineForDayPicker!!
                    routineForDayPicker = null
                    safeStartRoutine {
                        viewModel.startRoutineWorkout(r, targetDayNumber = day.dayNumber)
                        onLaunchRoutine(r)
                    }
                },
                onDismiss = { routineForDayPicker = null }
            )
        }

        // Prompt user if they attempt to launch a routine while a quick workout is active
        if (showExitQuickWorkoutPrompt) {
            AlertDialog(
                onDismissRequest = {
                    showExitQuickWorkoutPrompt = false
                    pendingRoutineLaunch = null
                },
                containerColor = DarkSurface,
                title = {
                    Text(
                        text = "Workout in Progress",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                },
                text = {
                    Text(
                        text = "You currently have a Quick Workout in progress. Would you like to exit your current workout to start this routine?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitQuickWorkoutPrompt = false
                            viewModel.discardActiveSession()
                            pendingRoutineLaunch?.invoke()
                            pendingRoutineLaunch = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Exit & Start Routine",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showExitQuickWorkoutPrompt = false
                            pendingRoutineLaunch = null
                        }
                    ) {
                        Text("Keep Current Workout", color = ElectricCyan)
                    }
                }
            )
        }

        // Create Routine Screen Overlay (also used to edit an existing routine)
        if (showCreateRoutineDialog) {
            CreateRoutineScreen(
                viewModel = viewModel,
                editingRoutine = routineBeingEdited,
                onDismiss = {
                    showCreateRoutineDialog = false
                    routineBeingEdited = null
                },
                onRoutineSaved = {
                    showCreateRoutineDialog = false
                    routineBeingEdited = null
                }
            )
        }
    }
}

/**
 * Confirmation dialog shown before a saved routine is deleted, so a mis-tap on the trash icon can
 * never destroy a program the user built or generated.
 */
@Composable
private fun DeleteRoutineConfirmDialog(
    routine: SavedRoutine,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("delete_routine_confirm_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = FireRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Routine?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "\"${routine.name}\" will be permanently removed from your saved routines.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = FireRed
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_delete_routine_button")
            ) {
                Text("Delete", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_routine_button")
            ) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun SavedRoutineCard(
    routine: SavedRoutine,
    allExercises: List<Exercise>,
    onStartWorkout: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("saved_routine_card_${routine.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "${routine.daysPerWeek} Days/Week • ${routine.goal}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = FireRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (routine.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = parseInlineMarkdown(routine.description, TextMuted),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Formatted exercise list display with day chips and structured rows (replaces raw JSON)
            RoutineExerciseListDisplay(
                routine = routine,
                allExercises = allExercises,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Start Workout + Copy + Edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartWorkout,
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("start_routine_button_${routine.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = DarkBg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Start Workout",
                        color = DarkBg,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Duplicate this routine (exact copy)
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier
                        .size(46.dp)
                        .background(MintGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, MintGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .testTag("duplicate_routine_button_${routine.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicate routine",
                        tint = MintGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Edit this routine in the Create Routine screen
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(46.dp)
                        .background(ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .testTag("edit_routine_button_${routine.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit routine",
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
