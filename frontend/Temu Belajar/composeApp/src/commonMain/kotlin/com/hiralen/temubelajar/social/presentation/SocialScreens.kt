package com.hiralen.temubelajar.social.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.material3.ExperimentalMaterial3Api
import com.hiralen.temubelajar.core.ui.*
import com.hiralen.temubelajar.social.component.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

// ─── Followers / Following screen ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersScreen(
    title: String,
    emails: List<String>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onProfileTap: (email: String) -> Unit,
    onFollow: ((email: String) -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = TBTypography.Headline) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = LinearColors.Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LinearColors.Canvas,
                    titleContentColor = LinearColors.Ink,
                    navigationIconContentColor = LinearColors.Ink
                )
            )
        },
        containerColor = LinearColors.Canvas
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LinearColors.Primary)
            }
            emails.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TBSpace.XS)) {
                    Icon(TablerIcons.Users, null, tint = LinearColors.InkTertiary, modifier = Modifier.size(48.dp))
                    Text("No one yet", color = LinearColors.InkSubtle, style = TBTypography.Body)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = TBSpace.XS)
            ) {
                items(emails) { email ->
                    UserListItem(
                        email = email,
                        onTap = { onProfileTap(email) },
                        trailingContent = if (onFollow != null) {
                            {
                                TextButton(onClick = { onFollow(email) }) {
                                    Text("Follow", color = LinearColors.PrimaryHover, style = TBTypography.BodySM)
                                }
                            }
                        } else null
                    )
                }
                if (onLoadMore != null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(TBSpace.MD), contentAlignment = Alignment.Center) {
                            TextButton(onClick = onLoadMore) { Text("Muat lagi", color = LinearColors.InkSubtle) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Friends screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    emails: List<String>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onProfileTap: (email: String) -> Unit,
    onUnfriend: (email: String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends", style = TBTypography.Headline) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = LinearColors.Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LinearColors.Canvas,
                    titleContentColor = LinearColors.Ink,
                    navigationIconContentColor = LinearColors.Ink
                )
            )
        },
        containerColor = LinearColors.Canvas
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LinearColors.Primary)
            }
            emails.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TBSpace.XS)) {
                    Icon(TablerIcons.Heart, null, tint = LinearColors.InkTertiary, modifier = Modifier.size(48.dp))
                    Text("No friends yet", color = LinearColors.InkSubtle, style = TBTypography.Body)
                    Text("Meet new people via video chat!", color = LinearColors.InkTertiary, style = TBTypography.BodySM)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = TBSpace.XS)
            ) {
                items(emails) { email ->
                    var showConfirm by remember { mutableStateOf(false) }

                    UserListItem(
                        email = email,
                        onTap = { onProfileTap(email) },
                        trailingContent = {
                            TextButton(onClick = { showConfirm = true }) {
                                Text("Remove", color = LinearColors.Error.copy(alpha = 0.85f), style = TBTypography.BodySM)
                            }
                        }
                    )

                    if (showConfirm) {
                        AlertDialog(
                            onDismissRequest = { showConfirm = false },
                            containerColor = LinearColors.Surface2,
                            titleContentColor = LinearColors.Ink,
                            title = { Text("Remove friend?") },
                            text = { Text("${email.substringBefore("@")} will be removed from your friends list.", color = LinearColors.InkMuted) },
                            confirmButton = {
                                TextButton(onClick = { onUnfriend(email); showConfirm = false }) {
                                    Text("Remove", color = LinearColors.Error)
                                }
                            },
                            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Batal", color = LinearColors.InkSubtle) } }
                        )
                    }
                }
            }
        }
    }
}

// ─── Pending friend requests screen ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    requests: List<String>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onAccept: (fromEmail: String) -> Unit,
    onReject: (fromEmail: String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friend requests", style = TBTypography.Headline) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = LinearColors.Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LinearColors.Canvas,
                    titleContentColor = LinearColors.Ink,
                    navigationIconContentColor = LinearColors.Ink
                )
            )
        },
        containerColor = LinearColors.Canvas
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = TBSpace.XS)) {
            if (requests.isEmpty() && !isLoading) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No pending requests", color = LinearColors.InkSubtle, style = TBTypography.Body)
                    }
                }
            }
            items(requests) { fromEmail ->
                UserListItem(
                    email = fromEmail,
                    onTap = {},
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { onReject(fromEmail) }) {
                                Text("Decline", color = LinearColors.Error, style = TBTypography.BodySM)
                            }
                            Button(
                                onClick = { onAccept(fromEmail) },
                                shape = TBShapes.MD,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LinearColors.Primary,
                                    contentColor = LinearColors.InverseCanvas
                                )
                            ) {
                                Text("Accept", style = TBTypography.BodySM)
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─── Shared user row ─────────────────────────────────────────────────────────

@Composable
private fun UserListItem(
    email: String,
    onTap: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = TBSpace.MD, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TBSpace.SM),
            modifier = Modifier.weight(1f).clickable { onTap() }
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LinearColors.Surface2)
                    .border(1.dp, LinearColors.Hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = email.take(1).uppercase(),
                    color = LinearColors.Ink,
                    style = TBTypography.Subhead.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Column {
                Text(
                    text = email.substringBefore("@"),
                    color = LinearColors.Ink,
                    style = TBTypography.BodySM.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = email,
                    color = LinearColors.InkTertiary,
                    style = TBTypography.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingContent != null) trailingContent()
    }
    HorizontalDivider(color = LinearColors.Hairline, modifier = Modifier.padding(horizontal = TBSpace.MD))
}
