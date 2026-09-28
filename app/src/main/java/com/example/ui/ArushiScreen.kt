package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TaskEntity
import com.example.engine.ActionResult
import com.example.engine.ContactMatch
import com.example.engine.UniversalTaskManager
import com.example.engine.VoiceManager
import com.example.ui.components.ArushiOrb
import com.example.ui.components.AssistantVisualState
import com.example.ui.components.ContactDisambiguationDialog
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.components.TaskCenterBottomSheet
import com.example.ui.components.TaskExecutionCard
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.Flow

data class ConversationMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "ARUSHI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ArushiScreen(
    taskManager: UniversalTaskManager,
    voiceManager: VoiceManager,
    allTasksFlow: Flow<List<TaskEntity>>,
    isBackgroundServiceActive: Boolean,
    onToggleBackgroundService: (Boolean) -> Unit,
    savedApiKey: String,
    onSaveApiKey: (String) -> Unit
) {
    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val isProcessing by taskManager.isProcessing.collectAsStateWithLifecycle()
    val currentTask by taskManager.currentTask.collectAsStateWithLifecycle()
    val activeSteps by taskManager.activeSteps.collectAsStateWithLifecycle()
    val ambiguityMatches by taskManager.ambiguityMatches.collectAsStateWithLifecycle()
    val allTasks by allTasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var isTextMode by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showTaskCenter by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ConversationMessage(
                sender = "ARUSHI",
                text = "Hello! Main hoon Arushi, aapki personal voice assistant aur AI agent. Kuch bhi bolo, kaam ho jayega ❤️ Aur koi kaam hai?"
            )
        )
    }

    val listState = rememberLazyListState()

    // Determine visual state
    val visualState = when {
        isSpeaking -> AssistantVisualState.SPEAKING
        isListening -> AssistantVisualState.LISTENING
        isProcessing -> AssistantVisualState.EXECUTING
        else -> AssistantVisualState.IDLE
    }

    // Connect voiceManager recognition callback
    LaunchedEffect(Unit) {
        voiceManager.onSpeechRecognized = { text ->
            if (text.isNotBlank()) {
                messages.add(ConversationMessage(sender = "USER", text = text))
                taskManager.processCommand(text, customApiKey = savedApiKey) { reply ->
                    messages.add(ConversationMessage(sender = "ARUSHI", text = reply))
                }
            }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Bar
            TopBar(
                visualState = visualState,
                isBackgroundActive = isBackgroundServiceActive,
                activeTaskCount = allTasks.count { it.status == "RUNNING" || it.status == "QUEUED" },
                onOpenTaskCenter = { showTaskCenter = true },
                onOpenSettings = { showSettings = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector Pill (Voice / Text)
            ModeSelectorPill(
                isTextMode = isTextMode,
                onModeChanged = { isTextMode = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Central Area
            if (!isTextMode) {
                // VOICE MODE VIEW
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    ArushiOrb(
                        state = visualState,
                        onClick = {
                            if (isListening) {
                                voiceManager.stopListening()
                            } else if (isSpeaking) {
                                voiceManager.stopSpeaking()
                            } else {
                                voiceManager.startListening()
                            }
                        }
                    )
                }

                // Quick Action Chips Row
                QuickActionChips(
                    onActionSelected = { command ->
                        messages.add(ConversationMessage(sender = "USER", text = command))
                        taskManager.processCommand(command, customApiKey = savedApiKey) { reply ->
                            messages.add(ConversationMessage(sender = "ARUSHI", text = reply))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Active Task Execution Banner (if a task is currently executing)
            AnimatedVisibility(
                visible = currentTask != null && (currentTask?.status == "RUNNING" || currentTask?.status == "QUEUED"),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                currentTask?.let { task ->
                    TaskExecutionCard(
                        task = task,
                        steps = activeSteps,
                        onCancel = { taskManager.cancelCurrentExecution() },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            // Conversation Stream
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Input Bar
            BottomInputBar(
                inputText = textInput,
                onInputChanged = { textInput = it },
                isListening = isListening,
                onMicClick = {
                    if (isListening) {
                        voiceManager.stopListening()
                    } else {
                        voiceManager.startListening()
                    }
                },
                onSendClick = {
                    if (textInput.isNotBlank()) {
                        val cmd = textInput.trim()
                        textInput = ""
                        messages.add(ConversationMessage(sender = "USER", text = cmd))
                        taskManager.processCommand(cmd, customApiKey = savedApiKey) { reply ->
                            messages.add(ConversationMessage(sender = "ARUSHI", text = reply))
                        }
                    }
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            )
        }

        // Contact Disambiguation Dialog
        if (!ambiguityMatches.isNullOrEmpty()) {
            ContactDisambiguationDialog(
                contacts = ambiguityMatches!!,
                onSelect = { contact ->
                    taskManager.selectAmbiguousContact(contact, currentTask)
                },
                onDismiss = {
                    taskManager.cancelCurrentExecution()
                }
            )
        }

        // Task Center Bottom Sheet
        if (showTaskCenter) {
            TaskCenterBottomSheet(
                tasks = allTasks,
                onClearFinished = {
                    // Handled via database DAO
                },
                onDismiss = { showTaskCenter = false }
            )
        }

        // Settings Bottom Sheet
        if (showSettings) {
            SettingsBottomSheet(
                currentApiKey = savedApiKey,
                onSaveApiKey = { key ->
                    onSaveApiKey(key)
                    showSettings = false
                },
                isBackgroundModeEnabled = isBackgroundServiceActive,
                onToggleBackgroundMode = onToggleBackgroundService,
                onTestSpeaker = { onComplete ->
                    voiceManager.testSpeakerHardware(onComplete)
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
fun TopBar(
    visualState: AssistantVisualState,
    isBackgroundActive: Boolean,
    activeTaskCount: Int,
    onOpenTaskCenter: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // App Title & Status Pill
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Arushi",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " AI",
                color = NeonCyan,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(10.dp))

            // State Badge
            val (badgeBg, badgeText, badgeColor) = when (visualState) {
                AssistantVisualState.LISTENING -> Triple(NeonCyan.copy(alpha = 0.2f), "LISTENING", NeonCyan)
                AssistantVisualState.SPEAKING -> Triple(NeonMagenta.copy(alpha = 0.2f), "SPEAKING", NeonMagenta)
                AssistantVisualState.EXECUTING -> Triple(NeonAmber.copy(alpha = 0.2f), "ACTIVE AGENT", NeonAmber)
                AssistantVisualState.IDLE -> Triple(SurfaceVariantDark, if (isBackgroundActive) "BG ACTIVE" else "READY", TextSecondary)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Action Icons: Task Center & Settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenTaskCenter, modifier = Modifier.testTag("task_center_button")) {
                BadgedBox(
                    badge = {
                        if (activeTaskCount > 0) {
                            Badge(containerColor = NeonCyan) {
                                Text("$activeTaskCount", color = BackgroundDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = "Task Center",
                        tint = if (activeTaskCount > 0) NeonCyan else TextSecondary
                    )
                }
            }

            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings_button")) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
fun ModeSelectorPill(
    isTextMode: Boolean,
    onModeChanged: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceVariantDark, RoundedCornerShape(20.dp))
                .padding(3.dp)
        ) {
            Row {
                // Voice Mode Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (!isTextMode) NeonViolet else Color.Transparent)
                        .clickable { onModeChanged(false) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (!isTextMode) TextPrimary else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Voice Mode",
                            color = if (!isTextMode) TextPrimary else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Text Mode Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isTextMode) NeonViolet else Color.Transparent)
                        .clickable { onModeChanged(true) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = if (isTextMode) TextPrimary else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Text Mode",
                            color = if (isTextMode) TextPrimary else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionChips(onActionSelected: (String) -> Unit) {
    val scrollState = rememberScrollState()
    val chips = listOf(
        "💬 WhatsApp Yogesh" to "WhatsApp kholo aur Yogesh ko message karo ki main ghar aa gaya hoon",
        "▶️ YouTube Song" to "YouTube kholo aur Code With Yogesh search karke video chalao",
        "📞 Call Mom" to "Mom ko call karo",
        "⚡ Carpenter App" to "Arushi mere liye carpenter app bana do aur APK generate karo",
        "✉️ Check Gmail" to "Gmail check karo aur unread email batao",
        "🔊 Test Speaker" to "Test Speaker"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (label, command) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceVariantDark, RoundedCornerShape(12.dp))
                    .clickable { onActionSelected(command) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ConversationMessage) {
    val isUser = message.sender == "USER"

    Row(
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Brush.horizontalGradient(listOf(NeonViolet, Color(0xFF7C3AED)))
                    else Brush.horizontalGradient(listOf(SurfaceCard, SurfaceVariantDark))
                )
                .border(
                    1.dp,
                    if (isUser) NeonViolet.copy(alpha = 0.5f) else SurfaceVariantDark,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun BottomInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChanged,
            placeholder = { Text("Speak or type task in Hinglish...", color = TextMuted, fontSize = 13.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = SurfaceVariantDark,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("text_input_field")
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Mic or Send Button
        if (inputText.isNotBlank()) {
            IconButton(
                onClick = onSendClick,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Command",
                    tint = BackgroundDark,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isListening) NeonMagenta else NeonViolet)
                    .testTag("bottom_mic_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Microphone",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
