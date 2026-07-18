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

    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(LinearColors.Canvas)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = LinearColors.Primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Banner — surface-3 fill, hairline at top of body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .background(LinearColors.Surface3)
                ) {
                    IconButton(
                        onClick = { component.onBack() },
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).statusBarsPadding()
                    ) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = LinearColors.Ink)
                    }

                    if (!state.isOwnProfile) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = TBSpace.MD, bottom = TBSpace.XS),
                            horizontalArrangement = Arrangement.spacedBy(TBSpace.XS)
                        ) {
                            val youFollow = state.social?.youFollow == true
                            OutlinedButton(
                                onClick = {
                                    component.performAction(
                                        if (youFollow) ProfileAction.Unfollow else ProfileAction.Follow
                                    )
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, LinearColors.Primary),
                                shape = TBShapes.MD,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (youFollow) LinearColors.PrimaryFocus else LinearColors.Surface3,
                                    contentColor = if (youFollow) LinearColors.InverseCanvas else LinearColors.PrimaryHover
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    if (youFollow) TablerIcons.UserCheck else TablerIcons.UserPlus,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (youFollow) "Following" else "Follow", style = TBTypography.Button)
                            }

                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(TablerIcons.DotsVertical, contentDescription = "More", tint = LinearColors.Ink)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    containerColor = LinearColors.Surface2
                                ) {
                                    DropdownMenuItem(
                                        onClick = {
                                            expanded = false
                                            component.performAction(ProfileAction.SendFriendRequest)
                                        },
                                        text = { Text("Add friend", color = LinearColors.Ink) },
                                        leadingIcon = { Icon(TablerIcons.Users, null, Modifier.size(16.dp), tint = LinearColors.Ink) }
                                    )
                                    HorizontalDivider(color = LinearColors.Hairline)
                                    DropdownMenuItem(
                                        onClick = { expanded = false; showBlockConfirm = true },
                                        text = { Text("Blok", color = LinearColors.Error) },
                                        leadingIcon = { Icon(TablerIcons.Ban, contentDescription = null, tint = LinearColors.Error, modifier = Modifier.size(16.dp)) }
                                    )
                                    DropdownMenuItem(
                                        onClick = { expanded = false; showReportDialog = true },
                                        text = { Text("Lapor", color = LinearColors.Error) },
                                        leadingIcon = { Icon(TablerIcons.Flag, contentDescription = null, tint = LinearColors.Error, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Avatar overlapping banner/body
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .offset(x = TBSpace.LG, y = (-40).dp)
                        .clip(CircleShape)
                        .background(LinearColors.Primary)
                        .border(3.dp, LinearColors.Canvas, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.name.take(1).uppercase().ifBlank { state.email.take(1).uppercase() },
                        style = TBTypography.Headline.copy(fontWeight = FontWeight.SemiBold),
                        color = LinearColors.InverseCanvas
                    )
                }

                Spacer(Modifier.height(TBSpace.MD))

                Column(modifier = Modifier.padding(horizontal = TBSpace.LG)) {
                    Text(
                        text = state.name.ifBlank { state.email.substringBefore("@") },
                        style = TBTypography.Headline.copy(fontWeight = FontWeight.SemiBold),
                        color = LinearColors.Ink
                    )
                    if (state.username.isNotBlank()) {
                        Text(
                            "@${state.username}",
                            style = TBTypography.BodySM,
                            color = LinearColors.InkSubtle
                        )
                    }

                    Spacer(Modifier.height(TBSpace.SM))

                    if (state.bio.isNotBlank()) {
                        Text(state.bio, color = LinearColors.InkMuted, style = TBTypography.Body)
                        Spacer(Modifier.height(TBSpace.SM))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(TBSpace.XS)) {
                        if (state.university.isNotBlank()) SocialChip(TablerIcons.School, state.university)
                        if (state.major.isNotBlank()) SocialChip(TablerIcons.Book, state.major)
                    }

                    Spacer(Modifier.height(TBSpace.MD))

                    Row(horizontalArrangement = Arrangement.spacedBy(TBSpace.XL)) {
                        CountColumn(state.social?.followerCount ?: 0, "Followers") { component.onViewFollowers(state.email) }
                        CountColumn(state.social?.followingCount ?: 0, "Following") { component.onViewFollowing(state.email) }
                        CountColumn(state.friends.size, "Friends") { component.onViewFriends(state.email) }
                    }

                    val preview = state.social?.followedByPreview ?: emptyList()
                    if (preview.isNotEmpty()) {
                        Spacer(Modifier.height(TBSpace.SM))
                        val previewText = buildString {
                            append("Followed by ")
                            preview.forEachIndexed { i, email ->
                                append(email.substringBefore("@"))
                                if (i < preview.lastIndex) append(", ")
                            }
                            val remaining = (state.social?.followerCount ?: 0) - preview.size
                            if (remaining > 0) append(" and $remaining more")
                        }
                        Text(previewText, color = LinearColors.InkTertiary, style = TBTypography.BodySM)
                    }

                    Spacer(Modifier.height(TBSpace.LG))

                    if (state.friends.isNotEmpty()) {
                        Text("Friends", style = TBTypography.Subhead.copy(fontWeight = FontWeight.SemiBold), color = LinearColors.Ink)
                        Spacer(Modifier.height(TBSpace.SM))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(TBSpace.XS),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            state.friends.take(10).forEach { friendEmail -> FriendAvatar(email = friendEmail) }
                            if (state.friends.size > 10) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(LinearColors.Surface2)
                                        .border(1.dp, LinearColors.Hairline, CircleShape)
                                        .clickable { component.onViewFriends(state.email) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+${state.friends.size - 10}", color = LinearColors.Ink, style = TBTypography.BodySM)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(TBSpace.XXL))
                }
            }
        }
    }

    if (showReportDialog) {
        ReportDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, detail ->
                component.performAction(ProfileAction.Report(reason, detail))
                showReportDialog = false
            }
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            containerColor = LinearColors.Surface2,
            titleContentColor = LinearColors.Ink,
            title = { Text("Block user?") },
            text = { Text("You won't meet ${state.name.ifBlank { "this user" }} in video chat again.", color = LinearColors.InkMuted) },
            confirmButton = {
                TextButton(onClick = {
                    component.performAction(ProfileAction.Block())
                    showBlockConfirm = false
                }) { Text("Blok", color = LinearColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text("Batal", color = LinearColors.InkSubtle) }
            }
        )
    }
}

@Composable
private fun SocialChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(TBShapes.Pill)
            .background(LinearColors.Surface1)
            .border(1.dp, LinearColors.Hairline, TBShapes.Pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = LinearColors.InkSubtle, modifier = Modifier.size(14.dp))
        Text(text, color = LinearColors.Ink, style = TBTypography.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CountColumn(count: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickableRole { onClick() }
    ) {
        Text(
            text = if (count >= 1000) "${count / 1000}k" else count.toString(),
            style = TBTypography.Subhead.copy(fontWeight = FontWeight.SemiBold),
            color = LinearColors.Ink
        )
        Text(label, color = LinearColors.InkSubtle, style = TBTypography.Caption)
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
                .background(LinearColors.Surface2)
                .border(1.dp, LinearColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                email.take(1).uppercase(),
                color = LinearColors.Ink,
                style = TBTypography.BodyLG.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            email.substringBefore("@"),
            color = LinearColors.InkSubtle,
            style = TBTypography.Caption,
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
    val reasonLabels = listOf("Spam", "Harassment", "Inappropriate content", "Impersonation", "Other")
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var detail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LinearColors.Surface2,
        titleContentColor = LinearColors.Ink,
        title = { Text("Report user") },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = LinearColors.InkSubtle) } }
    )
}
