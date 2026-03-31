package com.hiralen.temubelajar.social.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.core.ui.*
import com.hiralen.temubelajar.social.component.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@Composable
fun ProfileScreen(component: ProfileComponent) {
    val state by component.state.collectAsState()

    // Report dialog state
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TBColors.DarkBg)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = TBColors.Primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Header / Cover gradient ──────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(TBColors.Primary.copy(alpha = 0.7f), TBColors.DarkBg)
                            )
                        )
                ) {
                    // Back button
                    IconButton(
                        onClick = { component.onBack() },
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).statusBarsPadding()
                    ) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Kembali", tint = Color.White)
                    }

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .align(Alignment.BottomStart)
                            .offset(x = 24.dp, y = 40.dp)
                            .clip(CircleShape)
                            .background(TBColors.Primary)
                            .border(3.dp, TBColors.DarkBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.name.take(1).uppercase().ifBlank { state.email.take(1).uppercase() },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Action button (own profile = edit, other = follow)
                    if (!state.isOwnProfile) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val youFollow = state.social?.youFollow == true
                            OutlinedButton(
                                onClick = {
                                    component.performAction(
                                        if (youFollow) ProfileAction.Unfollow else ProfileAction.Follow
                                    )
                                },
                                border = BorderStroke(1.5.dp, TBColors.Primary),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (youFollow) Color.White else TBColors.Primary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    if (youFollow) TablerIcons.UserCheck else TablerIcons.UserPlus,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (youFollow) "Mengikuti" else "Ikuti", fontSize = 13.sp)
                            }

                            // More actions
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(TablerIcons.DotsVertical, contentDescription = "More", tint = Color.White)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(
                                        onClick = {
                                            expanded = false
                                            component.performAction(ProfileAction.SendFriendRequest)
                                        },
                                        text = { Text("Tambah teman") },
                                        leadingIcon = { Icon(TablerIcons.Users, null, modifier = Modifier.size(16.dp)) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        onClick = { expanded = false; showBlockConfirm = true },
                                        text = { Text("Blokir", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(TablerIcons.Ban, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                                    )
                                    DropdownMenuItem(
                                        onClick = { expanded = false; showReportDialog = true },
                                        text = { Text("Laporkan", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(TablerIcons.Flag, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))

                // ── Name + username ──────────────────────────────────────────
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = state.name.ifBlank { state.email.substringBefore("@") },
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                    if (state.username.isNotBlank()) {
                        Text(
                            "@${state.username}",
                            color = TBColors.Secondary,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Bio
                    if (state.bio.isNotBlank()) {
                        Text(state.bio, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }

                    // University + Major chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.university.isNotBlank()) {
                            SocialChip(TablerIcons.School, state.university)
                        }
                        if (state.major.isNotBlank()) {
                            SocialChip(TablerIcons.Book, state.major)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Follower count row ───────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        CountColumn(
                            count = state.social?.followerCount ?: 0,
                            label = "Pengikut",
                            onClick = { component.onViewFollowers(state.email) }
                        )
                        CountColumn(
                            count = state.social?.followingCount ?: 0,
                            label = "Mengikuti",
                            onClick = { component.onViewFollowing(state.email) }
                        )
                        CountColumn(
                            count = state.friends.size,
                            label = "Teman",
                            onClick = { component.onViewFriends(state.email) }
                        )
                    }

                    // ── "Difollow oleh …" preview ────────────────────────────
                    val preview = state.social?.followedByPreview ?: emptyList()
                    if (preview.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        val previewText = buildString {
                            append("Diikuti oleh ")
                            preview.forEachIndexed { i, email ->
                                append("**${email.substringBefore("@")}**")
                                if (i < preview.lastIndex) append(", ")
                            }
                            val remaining = (state.social?.followerCount ?: 0) - preview.size
                            if (remaining > 0) append(", dan $remaining lainnya")
                        }
                        Text(
                            text = previewText,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Friends preview ───────────────────────────────────────
                    if (state.friends.isNotEmpty()) {
                        Text("Teman", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            state.friends.take(10).forEach { friendEmail ->
                                FriendAvatar(email = friendEmail)
                            }
                            if (state.friends.size > 10) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(TBColors.Primary.copy(alpha = 0.3f))
                                        .clickable { component.onViewFriends(state.email) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+${state.friends.size - 10}", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    // ── Report dialog ─────────────────────────────────────────────────────────
    if (showReportDialog) {
        ReportDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, detail ->
                component.performAction(ProfileAction.Report(reason, detail))
                showReportDialog = false
            }
        )
    }

    // ── Block confirm dialog ──────────────────────────────────────────────────
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Blokir pengguna?") },
            text = { Text("Kamu tidak akan bisa bertemu ${state.name.ifBlank { "pengguna ini" }} lagi di video chat.") },
            confirmButton = {
                TextButton(onClick = {
                    component.performAction(ProfileAction.Block())
                    showBlockConfirm = false
                }) { Text("Blokir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text("Batal") }
            }
        )
    }
}

// ─── Helper composables ───────────────────────────────────────────────────────

@Composable
private fun SocialChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TBColors.Primary.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TBColors.Primary, modifier = Modifier.size(14.dp))
        Text(text, color = TBColors.Primary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CountColumn(count: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = if (count >= 1000) "${count / 1000}k" else count.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White
        )
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun FriendAvatar(email: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(TBColors.Secondary.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                email.take(1).uppercase(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            email.substringBefore("@"),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String?) -> Unit
) {
    val reasons = listOf("spam", "harassment", "inappropriate_content", "impersonation", "other")
    val reasonLabels = listOf("Spam", "Pelecehan", "Konten tidak pantas", "Peniruan identitas", "Lainnya")
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var detail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Laporkan pengguna") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEachIndexed { i, r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedReason = r }
                    ) {
                        RadioButton(selected = selectedReason == r, onClick = { selectedReason = r })
                        Spacer(Modifier.width(8.dp))
                        Text(reasonLabels[i])
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Detail (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(selectedReason, detail.ifBlank { null }) }) {
                Text("Kirim laporan", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
