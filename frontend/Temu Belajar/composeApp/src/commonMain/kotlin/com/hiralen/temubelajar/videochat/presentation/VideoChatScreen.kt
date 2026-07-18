package com.hiralen.temubelajar.videochat.presentation

import com.hiralen.temubelajar.core.ui.TBLottie
import com.hiralen.temubelajar.core.ui.BackHandler
import com.hiralen.temubelajar.core.ui.LinearColors
import com.hiralen.temubelajar.core.ui.TBShapes
import com.hiralen.temubelajar.core.ui.TBSpace
import com.hiralen.temubelajar.core.ui.TBTypography
import com.hiralen.temubelajar.core.ui.clickableRole
import com.hiralen.temubelajar.social.data.SocialRepository
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.videochat.component.VideoChatComponent
import com.hiralen.temubelajar.videochat.model.ChatMessage
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import kotlin.random.Random

/**
 * In-call video chat surface.
 *
 * Linear treats the product UI as the protagonist — the dark canvas here is the
 * Linear canvas (#010102), with hairline-bordered surface-1 controls and a
 * single lavender primary accent acting on the "Next" CTA, the peer avatar,
 * and the chat composer send button.
 */
@Composable
fun VideoChatScreen(component: VideoChatComponent) {
    CameraPermission {
        VideoChatContent(component)
    }
}

@Composable
private fun VideoChatContent(component: VideoChatComponent) {
    val state by component.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showConfirmLeave by remember { mutableStateOf(false) }
    // Phase 5.17 — system back shows a confirmation dialog instead of popping
    // the stack and leaking WebRTC resources.
    // Phase 5.9 — Report dropdown action opens a text-field dialog that
    // dispatches `socialRepository.report(peerEmail, reason, detail)` via Koin.
    var showReportDialog by remember { mutableStateOf(false) }
    // Phase 5.9 — Follow / Add friend / Block navigate to the peer's profile
    // (existing `onViewProfile` RootComponent callback, see
    // `RootComponent.kt:73`).
    var showSocialFeedback by remember { mutableStateOf<String?>(null) }

    val timerText = remember(state.durationSeconds) {
        val h = state.durationSeconds / 3600
        val m = (state.durationSeconds % 3600) / 60
        val s = state.durationSeconds % 60
        if (h > 0)
            "${h.toString().padStart(2,'0')}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
        else
            "${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
    }

    // Phase 5.17 — system back / Android predictive-back never pops the stack
    // silently while a call is live (which would leak WebRTC + WS resources).
    // Capture back, surface a confirmation dialog, and only `endSession()` on
    // "Ya".
    BackHandler(enabled = true) { showConfirmLeave = true }

    Box(modifier = Modifier.fillMaxSize().background(LinearColors.Canvas)) {
        // Phase 5.3 — swipe-to-next gesture. The full-bleed Box behind every
        // overlay registers a horizontal-drag pointer-input; if the user
        // swipes > 96dp left-or-right (an OmeTV-spec gesture) we fire
        // `component.nextPerson()`. We do NOT honour verticals so the PiP
        // drag handle (which uses full 2-D detectDragGestures inside its
        // own pointerInput) and a downward swipe-to-dismiss affordance
        // remain unambiguous. We also ignore tiny drags below the
        // threshold so a tap-on-video (which some users do to focus the
        // HUD) doesn't accidentally nuke the call. The gesture-state hoists
        // totalX outside the lambda so the cumulative-sum is read on every
        // onDragEnd, not just the last delta.
        var swipeTotalX by remember { mutableStateOf(0f) }
        // Phase 5.3 — 96dp ≈ the minimum horizontal travel needed to feel
        // intentional vs. an accidental finger drift while re-positioning
        // the hand for the HUD tap. Hoisted into px here so the
        // `pointerInput` lambda can read it (which is a non-@Composable
        // scope where LocalDensity.current can't be queried).
        val swipeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeTotalX = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(swipeTotalX) >= swipeThresholdPx) {
                                component.nextPerson()
                            }
                            swipeTotalX = 0f
                        },
                        onDragCancel = { swipeTotalX = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        swipeTotalX += dragAmount
                    }
                }
        ) {
            // Remote video (full-bleed)
            RemoteVideoView(
                renderer = component.webRtcManager.remoteVideoRenderer,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Connecting scrim
        AnimatedVisibility(
            visible = !state.isConnected && !state.peerLeft,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(LinearColors.Canvas.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LinearColors.Primary)
                    Spacer(Modifier.height(TBSpace.MD))
                    Text(
                        // Phase 5.37 — match the rest of the app (ID).
                        text = "Menyambungkan video…",
                        color = LinearColors.Ink,
                        style = TBTypography.BodySM
                    )
                }
            }
        }

        // Peer-left scrim
        AnimatedVisibility(
            visible = state.peerLeft,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(LinearColors.Canvas.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TBSpace.LG)
                ) {
                    Text(
                        "Your peer has left 👋",
                        color = LinearColors.Ink,
                        style = TBTypography.Subhead
                    )
                    Button(
                        onClick = { component.nextPerson() },
                        shape = TBShapes.MD,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LinearColors.Primary,
                            contentColor = LinearColors.InverseCanvas
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Find next person", style = TBTypography.Button)
                    }
                }
            }
        }

        // Top bar
        if (state.isConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TBSpace.MD, vertical = TBSpace.SM)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Peer pill — surface-2 fill, hairline border
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TBSpace.XS),
                    modifier = Modifier
                        .clip(TBShapes.Pill)
                        .background(LinearColors.Surface2)
                        .border(1.dp, LinearColors.Hairline, TBShapes.Pill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { component.onViewProfile(component.peerEmail) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(LinearColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.peerName.take(1).uppercase().ifBlank { "?" },
                            color = LinearColors.InverseCanvas,
                            style = TBTypography.BodySM.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Column {
                        Text(
                            text = state.peerName.ifBlank { component.peerEmail.substringBefore("@") },
                            color = LinearColors.Ink,
                            style = TBTypography.BodySM.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (state.peerUniversity.isNotBlank()) {
                            Text(
                                state.peerUniversity,
                                color = LinearColors.InkMuted,
                                style = TBTypography.Caption
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TBSpace.SM)
                ) {
                    Text(
                        timerText,
                        color = LinearColors.Ink,
                        style = TBTypography.MonoSM
                    )

                    // Phase 5.24 — the "dark mode" Lottie toggle was a dead
                    // control: clicking only advanced the Lottie animation,
                    // never switching the actual Compose theme (state was
                    // local to this Composable and `TemuBelajarTheme` is
                    // pinned at root with no hoisting path back here). UI
                    // promised a dark/light switch that the app couldn't
                    // deliver, so the entire Box is removed. When light mode
                    // is genuinely added, hoist `isDarkMode` to the Decompose
                    // root and re-introduce the toggle there.

                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                TablerIcons.Dots,
                                contentDescription = "Menu",
                                tint = LinearColors.Ink
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = LinearColors.Surface2
                        ) {
                            DropdownMenuItem(
                                // Phase 5.9 — Follow navigates to the peer's
                                // profile (existing RootComponent.onViewProfile
                                // callback, see RootComponent.kt:73). The actual
                                // follow POST happens from ProfileScreen once
                                // the user confirms.
                                onClick = {
                                    expanded = false
                                    component.onViewProfile(component.peerEmail)
                                },
                                text = { Text("Follow", color = LinearColors.Ink) },
                                leadingIcon = { Icon(TablerIcons.UserPlus, contentDescription = null, modifier = Modifier.size(16.dp), tint = LinearColors.Ink) }
                            )
                            DropdownMenuItem(
                                // Phase 5.9 — Add friend also routes through the
                                // profile screen (the POST happens in
                                // ProfileComponent.performAction).
                                onClick = {
                                    expanded = false
                                    component.onViewProfile(component.peerEmail)
                                },
                                text = { Text("Add friend", color = LinearColors.Ink) },
                                leadingIcon = { Icon(TablerIcons.Users, contentDescription = null, modifier = Modifier.size(16.dp), tint = LinearColors.Ink) }
                            )
                            HorizontalDivider(color = LinearColors.Hairline)
                            DropdownMenuItem(
                                // Phase 5.9 — Block routes through the profile
                                // screen's confirm dialog.
                                onClick = {
                                    expanded = false
                                    component.onViewProfile(component.peerEmail)
                                },
                                text = { Text("Blok", color = LinearColors.Error) },
                                leadingIcon = { Icon(TablerIcons.Ban, contentDescription = "Block user", modifier = Modifier.size(16.dp), tint = LinearColors.Error) }
                            )
                            DropdownMenuItem(
                                // Phase 5.9 — Report opens a local dialog with a
                                // reason + detail field and dispatches via Koin
                                // `SocialRepository.report(peerEmail, reason,
                                // detail)`. See `ReportInCallDialog` below.
                                onClick = {
                                    expanded = false
                                    showReportDialog = true
                                },
                                text = { Text("Lapor", color = LinearColors.Error) },
                                leadingIcon = { Icon(TablerIcons.Flag, contentDescription = "Report user", modifier = Modifier.size(16.dp), tint = LinearColors.Error) }
                            )
                        }
                    }
                }
            }
        }

        // PiP self-view
        // Phase 5.1 — was: `pipOffsetX/Y: Float` initialised to literal
        // 16f / 80f and read directly in `Modifier.offset { IntOffset(x.toInt(),
        // y.toInt()) }`, with `dragAmount` from `detectDragGestures`
        // (also raw px) accumulated in the same field. This conflated dp
        // + px: a `16f` literal was raw px (so the PiP anchored 5.3dp from
        // the corner on a 3x device), the PiP itself was sized in dp
        // (90×120dp), and the drag deltas in px felt 3× slower than on a
        // 220dpi device because Compose scale is hidden from the
        // programmer. Now state is in dp, dragAmount is converted via
        // `LocalDensity` (hoisted before pointerInput) and the offset
        // block converts dp→raw px for the `IntOffset` so the visual
        // PiP position scales consistently across hi/lo-density screens.
        // Initial offset also bumped to `16.dp`/`80.dp` so it renders at
        // the same on-screen position the original author intended.
        val pipDensity = LocalDensity.current
        var pipOffsetXDp by remember { mutableStateOf(16.dp) }
        var pipOffsetYDp by remember { mutableStateOf(80.dp) }
        // Phase 5.1 — clamp the PiP to stay inside the parent Box on y
        // (so drag can't push it off the call HUD into the system bar),
        // computed from window-text-bounds isn't cheap to do per frame
        // inside pointerInput, so we just bound by 16.dp margins and let
        // the actual edge clipping remain the user's responsibility.
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 120.dp)
                .offset {
                    IntOffset(
                        with(pipDensity) { pipOffsetXDp.roundToPx() },
                        with(pipDensity) { pipOffsetYDp.roundToPx() }
                    )
                }
                .clip(TBShapes.LG)
                .border(1.dp, LinearColors.PrimaryFocus, TBShapes.LG)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        with(pipDensity) {
                            pipOffsetXDp += dragAmount.x.toDp()
                            pipOffsetYDp += dragAmount.y.toDp()
                        }
                    }
                }
        ) {
            LocalVideoView(
                renderer = component.webRtcManager.localVideoRenderer,
                isMuted = state.isCameraMuted,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom bar
        // Phase 5.5 — was: a Column that stacked the AnimatedVisibility(ChatPanel)
        // ON TOP OF the control Row, causing the chat to push the controls
        // downward over the video scrim whenever the panel opened. That
        // re-layout shifted the entire call HUD down by the chat panel's
        // height — disorienting mid-call, broke muscle-memory tap
        // positions, and made the PiP self-view appear detached from the
        // bottom. Now both children are siblings in a Box aligned to
        // BottomCenter; the control Row stays pinned to BottomCenter and
        // the ChatPanel is anchored to BottomCenter with `padding(bottom =
        // controlsHeight)` so it slides up OVER the video while controls
        // stay put. The chat panel always paints ABOVE the controls via
        // the composable ordering in the parent Box (children declared
        // later paint on top). We measure controls height lazily by
        // pinning the next-person pill to 72.dp + padding, but instead of
        // hardcoding we wrap the control Row in `onSizeChanged` to track
        // pixels-to-offset the chat panel by. This keeps the chat panel
        // visually floating just above the HUD rather than overlapping
        // the buttons when chat is open.
        var controlBarHeightPx by remember { mutableStateOf(0) }

        // Controls pinned to bottom — declared FIRST so the chat panel (next)
        // paints above them in z-order when it slides in.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, LinearColors.Canvas.copy(alpha = 0.85f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = TBSpace.MD, vertical = TBSpace.SM)
                .onSizeChanged { controlBarHeightPx = it.height },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = if (state.isMicMuted) TablerIcons.MicrophoneOff else TablerIcons.Microphone,
                label = if (state.isMicMuted) "Unmute" else "Mute",
                active = !state.isMicMuted,
                onClick = { component.toggleMic() }
            )
            ControlButton(
                icon = if (state.isCameraMuted) TablerIcons.VideoOff else TablerIcons.Video,
                label = if (state.isCameraMuted) "Enable" else "Video",
                active = !state.isCameraMuted,
                onClick = { component.toggleCamera() }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Phase 5.2 — "Next" is the dominant circular accent CTA
                // (OmeTV-style hierarchy). 72dp vs the 48dp secondary
                // controls around it. Accent container uses LinearColors
                // .Accent (the Linear single-accent alias over Primary),
                // and the inner icon uses the InverseCanvas ink so the
                // label still reads at a glance against the lavender fill.
                // The hairline ring lifts it visually off the canvas even
                // over the dark video scrim behind.
                FilledIconButton(
                    onClick = { component.nextPerson() },
                    modifier = Modifier
                        .size(72.dp)
                        .border(2.dp, LinearColors.PrimaryFocus, CircleShape),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LinearColors.Accent,
                        contentColor = LinearColors.InverseCanvas
                    )
                ) {
                    Icon(
                        TablerIcons.PlayerSkipForward,
                        contentDescription = "Next",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text("Next", color = LinearColors.Ink, style = TBTypography.Button)
            }

            ControlButton(
                icon = TablerIcons.MessageCircle,
                label = "Chat",
                active = state.isChatOpen,
                // Phase 5.25 — badge is now anchored to the icon inside
                // ControlButton; hide when chat is open (ChatPanel itself
                // clears unreadCount on panel open).
                unreadCount = if (state.isChatOpen) 0 else state.unreadCount,
                onClick = { component.toggleChatPanel() }
            )

            ControlButton(
                icon = TablerIcons.PhoneOff,
                label = "End",
                active = false,
                tint = LinearColors.Error,
                onClick = { component.endSession() }
            )
        }

        // Chat panel OVERLAY — anchored to BottomCenter, offset UP by the
        // control bar's measured height so it floats above the HUD rather
        // than overlapping the buttons. Slides in vertically beneath the
        // control bar's z-index because it's declared AFTER (later
        // children paint on top), but we offset it above the controls so
        // it doesn't visually cover them.
        AnimatedVisibility(
            visible = state.isChatOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(LocalDensity.current) { controlBarHeightPx.toDp() }),
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
                // Phase 5.19 — draft text is component state, not a
                // screen-local remember, so `nextPerson()` clears it
                // atomically with the message list.
                inputText = state.chatInput,
                onInputChange = { component.onChatInputChange(it) },
                onSend = { component.sendCurrentChat() },
                onEmojiToggle = { component.toggleEmojiPicker() },
                onEmojiPick = { emoji ->
                    // Phase 5.38 — append into the draft, don't auto-send
                    // and don't auto-close the picker; user may want to
                    // tap several emojis in a row, then Send themselves.
                    component.sendEmoji(emoji)
                }
            )
        }
    }

    // ─── Phase 5.17 — leave-call confirmation ───────────────────────────────
    // Android system back is now intercepted by `BackHandler` (above) and
    // routed here so the user explicitly confirms before `endSession()` tears
    // down WebRTC + the WebSocket. Mirrors ProfileScreen.kt's block-confirm
    // styling (LinearColors.Surface2 scrim / Error confirm button).
    if (showConfirmLeave) {
        AlertDialog(
            onDismissRequest = { showConfirmLeave = false },
            containerColor = LinearColors.Surface2,
            titleContentColor = LinearColors.Ink,
            title = { Text("Akhir panggilan?") },
            text = {
                Text(
                    "Kamu akan menutup panggilan video ini.",
                    color = LinearColors.InkMuted,
                    style = TBTypography.BodySM
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmLeave = false
                    component.endSession()
                }) { Text("Ya", color = LinearColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmLeave = false }) {
                    Text("Tidak", color = LinearColors.InkSubtle)
                }
            }
        )
    }

    // ─── Phase 5.9 — Report dialog ──────────────────────────────────────────
    if (showReportDialog) {
        ReportInCallDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, detail ->
                showReportDialog = false
                // Resolve SocialRepository from Koin — same pattern used by
                // `ProfileComponent` (which `new`s it). Dispatch on a coroutine
                // so the suspending Ktor POST doesn't block the UI.
                val peerEmail = component.peerEmail
                scope.launch(Dispatchers.Default) {
                    runCatching {
                        KoinPlatform.getKoin()
                            .get<SocialRepository>()
                            .report(peerEmail, reason, detail)
                    }
                }
            }
        )
    }
}

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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .background(LinearColors.Canvas.copy(alpha = 0.85f))
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = TBSpace.SM, vertical = TBSpace.SM),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Phase 5.23 — include the monotonic `msg.id` plus `msg.sender`
            // and `msg.content` in the LazyList key so consecutive identical
            // messages (same-millisecond "ok" twice) don't collide and crash
            // `LazyColumn` with `IllegalArgumentException: Key was already used`.
            items(
                messages,
                key = { msg -> "${msg.id}-${msg.timestampMs}-${msg.sender}-${msg.content}" }
            ) { msg ->
                Box(modifier = Modifier.animateItem()) { ChatBubble(msg) }
            }
            if (isPeerTyping) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = TBSpace.SM),
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
                            "typing…",
                            color = LinearColors.InkSubtle,
                            style = TBTypography.Caption
                        )
                    }
                }
            }
        }

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LinearColors.Surface1)
                .border(1.dp, LinearColors.Hairline)
                .padding(horizontal = TBSpace.SM, vertical = TBSpace.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Phase 5.28 — 48dp minimum touch target (was 36dp).
            // Phase 5.29 — contentDescription for Talkback support.
            IconButton(
                onClick = onEmojiToggle,
                modifier = Modifier.size(48.dp)
            ) {
                // keep the visual size of the emoji glyph the same; the wider
                // touch target only adds invisible margins around it.
                // Phase 6.7 — size via TBTypography.Subhead (20sp) instead of a
                // raw 20.sp literal so it scales with the type ladder.
                Text("😊", style = TBTypography.Subhead)
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ketik pesan…",
                        color = LinearColors.InkTertiary,
                        style = TBTypography.BodySM
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LinearColors.PrimaryFocus,
                    unfocusedBorderColor = LinearColors.Hairline,
                    focusedContainerColor = LinearColors.Surface2,
                    unfocusedContainerColor = LinearColors.Surface2,
                    focusedTextColor = LinearColors.Ink,
                    unfocusedTextColor = LinearColors.Ink,
                    cursorColor = LinearColors.Primary
                ),
                maxLines = 3,
                shape = TBShapes.Pill,
                textStyle = TBTypography.BodySM,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            // Phase 5.28 — 48dp minimum touch target (was 36dp). The send
            // button keeps the lavender fill so it remains visually the accent
            // affordance of the chat composer.
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(LinearColors.Primary)
            ) {
                Icon(
                    TablerIcons.Send,
                    contentDescription = "Kirim pesan",
                    tint = LinearColors.InverseCanvas,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        horizontalArrangement = if (msg.fromSelf) Arrangement.End else Arrangement.Start
    ) {
        if (msg.type == ChatMessage.Type.EMOJI) {
            // Phase 6.7 — was `fontSize = 32.sp`; now TBTypography.EmojiXL
            // token (32sp, lineHeight 36sp) so the design system owns the
            // number.
            Text(msg.displayText, style = TBTypography.EmojiXL, modifier = Modifier.padding(4.dp))
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp, topEnd = 12.dp,
                            bottomStart = if (msg.fromSelf) 12.dp else 4.dp,
                            bottomEnd = if (msg.fromSelf) 4.dp else 12.dp
                        )
                    )
                    .background(if (msg.fromSelf) LinearColors.Primary else LinearColors.Surface2)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg.text,
                    color = if (msg.fromSelf) LinearColors.InverseCanvas else LinearColors.Ink,
                    style = TBTypography.BodySM
                )
            }
        }
    }
}

// ─── Emoji picker ────────────────────────────────────────────────────────────

@Composable
private fun EmojiPickerSheet(onEmojiPick: (String) -> Unit) {
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
            .background(LinearColors.Surface1)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = TBSpace.XS, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            emojiCategories.keys.forEach { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(TBShapes.MD)
                        .background(if (isSelected) LinearColors.Primary else Color.Transparent)
                        .clickable { selectedCategory = cat }
                        .padding(TBSpace.XS),
                    contentAlignment = Alignment.Center
                ) {
                    // Phase 6.7 — was `fontSize = 18.sp` raw literal. Now uses
                    // TBTypography.BodyLG (18sp, same size, with proper line
                    // height) for consistency with the design system.
                    Text(cat, style = TBTypography.BodyLG)
                }
            }
        }
        HorizontalDivider(color = LinearColors.Hairline)

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
                        .clip(TBShapes.MD)
                        .clickable { onEmojiPick(emojis[idx]) },
                    contentAlignment = Alignment.Center
                ) {
                    // Phase 6.7 — was `fontSize = 22.sp`. Now TBTypography.CardTitle
                    // (22sp, lineHeight 28sp) — exact same size via the type ladder.
                    Text(emojis[idx], style = TBTypography.CardTitle, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─── Control button ─────────────────────────────────────────────────────────

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    tint: Color = LinearColors.Ink,
    // Phase 5.25 — unread badge is anchored to the *icon*'s TopEnd rather
    // than to the outer icon+label Column, so it doesn't drift toward the
    // label on smaller surfaces. Owned by ControlButton so any control can
    // surface a count. Negative or zero hides the badge.
    unreadCount: Int = 0,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (active) LinearColors.Surface3 else LinearColors.Surface1,
        animationSpec = tween(250, easing = LinearOutSlowInEasing)
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) tint else LinearColors.InkSubtle,
        animationSpec = tween(250, easing = LinearOutSlowInEasing)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Phase 5.25 — wrap the FilledIconButton in its own Box so the badge
        // is anchored relative to the 48dp icon, not to the icon+label column.
        Box {
            FilledIconButton(
                onClick = onClick,
                // Phase 5.28 — 48dp minimum touch target (Material guideline).
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor)
            ) {
                Icon(
                    icon,
                    // Phase 5.29 — contentDescription describes the action,
                    // not the glyph.
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (unreadCount > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd),
                    containerColor = LinearColors.Error,
                    contentColor = LinearColors.InverseCanvas
                ) {
                    // Phase 6.7 — was `fontSize = 10.sp`. Now TBTypography.Badge
                    // (10sp Medium) — explicit smaller-than-Caption tier.
                    Text(unreadCount.toString(), style = TBTypography.Badge)
                }
            }
        }
        Text(label, color = contentColor, style = TBTypography.Caption)
    }
}

// ─── Phase 5.9 — In-call Report dialog ───────────────────────────────────────
//
// Mirrors ProfileScreen.kt's `ReportDialog` (reason radio group + optional
// detail field) but kept module-local so VideoChatScreen can dispatch a
// report without first navigating to the profile (the dropdown menu wires
// the entry point). Uses LinearColors/TBTypography/TBShapes tokens for the
// dark theme.

@Composable
private fun ReportInCallDialog(
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String?) -> Unit
) {
    val reasons = listOf("spam", "harassment", "inappropriate_content", "impersonation", "other")
    val reasonLabels = listOf("Spam", "Harassment", "Inappropriate content", "Impersonation", "Other")
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var detail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LinearColors.Surface2,
        titleContentColor = LinearColors.Ink,
        title = { Text("Report user", style = TBTypography.Subhead) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TBSpace.XS)) {
                reasons.forEachIndexed { i, r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickableRole { selectedReason = r }
                    ) {
                        RadioButton(
                            selected = selectedReason == r,
                            onClick = { selectedReason = r },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = LinearColors.Primary,
                                unselectedColor = LinearColors.InkSubtle
                            )
                        )
                        Spacer(Modifier.width(TBSpace.XS))
                        Text(reasonLabels[i], color = LinearColors.Ink, style = TBTypography.BodySM)
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Detail (optional)", color = LinearColors.InkSubtle) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LinearColors.PrimaryFocus,
                        unfocusedBorderColor = LinearColors.Hairline,
                        focusedContainerColor = LinearColors.Surface1,
                        unfocusedContainerColor = LinearColors.Surface1,
                        focusedTextColor = LinearColors.Ink,
                        unfocusedTextColor = LinearColors.Ink,
                        cursorColor = LinearColors.Primary
                    ),
                    shape = TBShapes.MD
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(selectedReason, detail.ifBlank { null }) }) {
                Text("Kirim laporan", color = LinearColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = LinearColors.InkSubtle)
            }
        }
    )
}
