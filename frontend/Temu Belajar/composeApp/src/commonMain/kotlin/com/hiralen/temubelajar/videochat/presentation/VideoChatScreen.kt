package com.hiralen.temubelajar.videochat.presentation

import com.hiralen.temubelajar.core.ui.TBLottie
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.core.ui.TBColors
import com.hiralen.temubelajar.videochat.component.VideoChatComponent
import com.hiralen.temubelajar.videochat.model.ChatMessage
import compose.icons.TablerIcons
import compose.icons.tablericons.*

// ─── STUN/TURN config note: handled by WebRtcManager internally ───────────────

@Composable
fun VideoChatScreen(component: VideoChatComponent) {
    // Request camera + mic before entering the call UI.
    // Each platform handles this differently (see CameraPermission.*.kt actuals).
    CameraPermission {
        VideoChatContent(component)
    }
}

@Composable
private fun VideoChatContent(component: VideoChatComponent) {
    val state by component.state.collectAsState()

    // Draggable PiP (self-view) position
    var pipOffsetX by remember { mutableStateOf(16f) }
    var pipOffsetY by remember { mutableStateOf(80f) }

    // Chat input text
    var chatInput by remember { mutableStateOf("") }

    // Timer format (no String.format — not available in wasmJs)
    val timerText = remember(state.durationSeconds) {
        val h = state.durationSeconds / 3600
        val m = (state.durationSeconds % 3600) / 60
        val s = state.durationSeconds % 60
        if (h > 0)
            "${h.toString().padStart(2,'0')}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
        else
            "${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── Remote Video (full screen) ───────────────────────────────────────
        RemoteVideoView(
            renderer = component.webRtcManager.remoteVideoRenderer,
            modifier = Modifier.fillMaxSize()
        )

        // ─── Connecting overlay ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = !state.isConnected && !state.peerLeft,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TBColors.Primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Menghubungkan video...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ─── Peer left overlay ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.peerLeft,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Orang lain keluar 👋", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { component.nextPerson() }) {
                        Text("Cari orang berikutnya")
                    }
                }
            }
        }

        // ─── Top bar: peer name, timer, social actions ─────────────────────────
        if (state.isConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Peer info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { component.onViewProfile(component.peerEmail) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(TBColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.peerName.take(1).uppercase().ifBlank { "?" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = state.peerName.ifBlank { component.peerEmail.substringBefore("@") },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.peerUniversity.isNotBlank()) {
                            Text(state.peerUniversity, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }

                // Timer + social menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(timerText, color = Color.White, fontSize = 13.sp)

                    // Day/Night Toggle (Lottie)
                    var isDarkMode by remember { mutableStateOf(false) } // Local mockup for now, ideally in a global theme state
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { isDarkMode = !isDarkMode },
                        contentAlignment = Alignment.Center
                    ) {
                        TBLottie(
                            resPath = "files/day_night_cycle.json",
                            modifier = Modifier.size(32.dp),
                            isPlaying = isDarkMode, // Only plays when toggled
                            restartOnPlay = false
                        )
                    }

                    // More actions (follow, block, report)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(TablerIcons.Dots, contentDescription = "Menu", tint = Color.White)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(onClick = { expanded = false }, text = { Text("Follow") },
                                leadingIcon = { Icon(TablerIcons.UserPlus, contentDescription = null, modifier = Modifier.size(16.dp)) })
                            DropdownMenuItem(onClick = { expanded = false }, text = { Text("Tambah teman") },
                                leadingIcon = { Icon(TablerIcons.Users, contentDescription = null, modifier = Modifier.size(16.dp)) })
                            HorizontalDivider()
                            DropdownMenuItem(onClick = { expanded = false }, text = { Text("Blokir", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(TablerIcons.Ban, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) })
                            DropdownMenuItem(onClick = { expanded = false }, text = { Text("Laporkan", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(TablerIcons.Flag, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }
            }
        }

        // ─── PiP self-view — always visible so user can see their own camera ───
        run {
            Box(
                modifier = Modifier
                    .size(width = 90.dp, height = 120.dp)
                    .offset { IntOffset(pipOffsetX.toInt(), pipOffsetY.toInt()) }
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, TBColors.Primary, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX += dragAmount.x
                            pipOffsetY += dragAmount.y
                        }
                    }
            ) {
                LocalVideoView(
                    renderer = component.webRtcManager.localVideoRenderer,
                    isMuted = state.isCameraMuted,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ─── Bottom bar: mic/cam/next/disconnect + chat toggle ────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Chat panel (collapsible) ──────────────────────────────────────
            AnimatedVisibility(
                visible = state.isChatOpen,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                ChatPanel(
                    messages = state.messages,
                    isPeerTyping = state.isPeerTyping,
                    isEmojiPickerOpen = state.isEmojiPickerOpen,
                    inputText = chatInput,
                    onInputChange = { chatInput = it; component.notifyTyping() },
                    onSend = {
                        if (chatInput.isNotBlank()) {
                            component.sendMessage(chatInput)
                            chatInput = ""
                        }
                    },
                    onEmojiToggle = { component.toggleEmojiPicker() },
                    onEmojiPick = { emoji ->
                        component.sendEmoji(emoji)
                        component.closeEmojiPicker()
                    }
                )
            }

            // ── Controls row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic
                ControlButton(
                    icon = if (state.isMicMuted) TablerIcons.MicrophoneOff else TablerIcons.Microphone,
                    label = if (state.isMicMuted) "Unmute" else "Mute",
                    active = !state.isMicMuted,
                    onClick = { component.toggleMic() }
                )

                // Camera
                ControlButton(
                    icon = if (state.isCameraMuted) TablerIcons.VideoOff else TablerIcons.Video,
                    label = if (state.isCameraMuted) "Aktifkan" else "Video",
                    active = !state.isCameraMuted,
                    onClick = { component.toggleCamera() }
                )

                // Next button (prominent, center)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledIconButton(
                        onClick = { component.nextPerson() },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = TBColors.Primary
                        )
                    ) {
                        Icon(TablerIcons.PlayerSkipForward, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Text("Next", color = Color.White, fontSize = 10.sp)
                }

                // Chat toggle with unread badge
                Box {
                    ControlButton(
                        icon = TablerIcons.MessageCircle,
                        label = "Chat",
                        active = state.isChatOpen,
                        onClick = { component.toggleChatPanel() }
                    )
                    if (state.unreadCount > 0 && !state.isChatOpen) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            containerColor = MaterialTheme.colorScheme.error
                        ) { Text("${state.unreadCount}", fontSize = 10.sp) }
                    }
                }

                // Disconnect
                ControlButton(
                    icon = TablerIcons.PhoneOff,
                    label = "Tutup",
                    active = false,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { component.endSession() }
                )
            }
        }
    }
}

// ─── Chat Panel ──────────────────────────────────────────────────────────────

@Composable
private fun ChatPanel(
    messages: List<ChatMessage>,
    isPeerTyping: Boolean,
    isEmojiPickerOpen: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onEmojiToggle: () -> Unit,
    onEmojiPick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .background(Color.Black.copy(alpha = 0.75f))
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.timestampMs.toString() + it.text.take(5) }) { msg ->
                Box(modifier = Modifier.animateItem()) {
                    ChatBubble(msg)
                }
            }
            if (isPeerTyping) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TBLottie(
                            resPath = "files/chat_loading.json",
                            modifier = Modifier.size(40.dp),
                            iterations = Int.MAX_VALUE
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "sedang mengetik...",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Emoji picker grid
        AnimatedVisibility(
            visible = isEmojiPickerOpen,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            EmojiPickerSheet(onEmojiPick = onEmojiPick)
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Emoji toggle button
            IconButton(onClick = onEmojiToggle, modifier = Modifier.size(36.dp)) {
                Text("😊", fontSize = 20.sp)
            }

            // Text field
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ketik pesan...", fontSize = 13.sp, color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TBColors.Primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = TBColors.Primary
                ),
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            // Send button
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TBColors.Primary)
            ) {
                Icon(TablerIcons.Send, contentDescription = "Kirim", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Chat Bubble ─────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        horizontalArrangement = if (msg.fromSelf) Arrangement.End else Arrangement.Start
    ) {
        if (msg.type == ChatMessage.Type.EMOJI) {
            // Large emoji display
            Text(msg.displayText, fontSize = 32.sp, modifier = Modifier.padding(4.dp))
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (msg.fromSelf) 16.dp else 4.dp,
                            bottomEnd = if (msg.fromSelf) 4.dp else 16.dp
                        )
                    )
                    .background(if (msg.fromSelf) TBColors.Primary else Color(0xFF2A2A3A))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─── Emoji Picker Sheet ───────────────────────────────────────────────────────

@Composable
private fun EmojiPickerSheet(onEmojiPick: (String) -> Unit) {
    // Curated common emojis grouped by category
    // Using hardcoded emoji strings for KMP compatibility across all targets
    val emojiCategories = remember {
        mapOf(
            "😊" to listOf("😀","😂","🥹","😊","😇","🥰","😍","🤩","😘","😗","😚","😙","🙂","😄","😁","😆","😅","🤣"),
            "👍" to listOf("👍","👎","👏","🙌","🤝","🫶","❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","❣️","💯","🔥"),
            "🤔" to listOf("🤔","🤨","😐","😑","🙄","😒","😓","😔","😕","🙁","😣","😖","😞","😟","😤","😢","😭","😩"),
            "🎉" to listOf("🎉","🎊","🎈","🎁","🎀","🥳","🎂","🍰","🍕","🍔","🍟","🌮","🍜","🍣","🍺","🥂","☕","🧋"),
            "🌟" to listOf("⭐","🌟","✨","💫","🌈","☀️","🌙","⚡","🔥","❄️","🌊","🌺","🌸","🌼","🌻","🍀","🌲","🦋")
        )
    }

    var selectedCategory by remember { mutableStateOf(emojiCategories.keys.first()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color(0xFF1A1A2E))
    ) {
        // Category tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            emojiCategories.keys.forEach { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) TBColors.Primary else Color.Transparent)
                        .clickable { selectedCategory = cat }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(cat, fontSize = 18.sp)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Emoji grid
        val emojis = emojiCategories[selectedCategory] ?: emptyList()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(40.dp),
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(emojis.size) { idx ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEmojiPick(emojis[idx]) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emojis[idx], fontSize = 22.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─── Control Button ───────────────────────────────────────────────────────────

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (active) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(250, easing = LinearOutSlowInEasing)
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) tint else Color.White.copy(alpha = 0.6f),
        animationSpec = tween(250, easing = LinearOutSlowInEasing)
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor)
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Text(label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// Delegating VideoViews to feature:videochat module
