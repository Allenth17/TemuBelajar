package com.hiralen.temubelajar.social.presentation

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.*
import androidx.compose.material3.ExperimentalMaterial3Api
import com.hiralen.temubelajar.core.ui.*
import com.hiralen.temubelajar.social.component.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

// ─── Followers / Following screen ─────────────────────────────────────────────

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
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TBColors.DarkBg,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = TBColors.DarkBg
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TBColors.Primary)
            }
        } else if (emails.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(TablerIcons.Users, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    Text("Belum ada pengguna", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(emails) { email ->
                    UserListItem(
                        email = email,
                        onTap = { onProfileTap(email) },
                        trailingContent = if (onFollow != null) {
                            {
                                TextButton(onClick = { onFollow(email) }) {
                                    Text("Ikuti", color = TBColors.Primary, fontSize = 13.sp)
                                }
                            }
                        } else null
                    )
                }
                if (onLoadMore != null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = onLoadMore) { Text("Muat lebih banyak", color = TBColors.Secondary) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Friends screen ───────────────────────────────────────────────────────────

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
                title = { Text("Teman", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TBColors.DarkBg,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = TBColors.DarkBg
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TBColors.Primary)
            }
        } else if (emails.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(TablerIcons.Heart, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    Text("Belum ada teman", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    Text("Temui orang baru via video chat!", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(emails) { email ->
                    var showConfirm by remember { mutableStateOf(false) }

                    UserListItem(
                        email = email,
                        onTap = { onProfileTap(email) },
                        trailingContent = {
                            TextButton(onClick = { showConfirm = true }) {
                                Text("Hapus", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), fontSize = 13.sp)
                            }
                        }
                    )

                    if (showConfirm) {
                        AlertDialog(
                            onDismissRequest = { showConfirm = false },
                            title = { Text("Hapus teman?") },
                            text = { Text("${email.substringBefore("@")} akan dihapus dari daftar temanmu.") },
                            confirmButton = {
                                TextButton(onClick = { onUnfriend(email); showConfirm = false }) {
                                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Batal") } }
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
    requests: List<String>,  // fromEmail list
    isLoading: Boolean,
    onBack: () -> Unit,
    onAccept: (fromEmail: String) -> Unit,
    onReject: (fromEmail: String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permintaan pertemanan", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TBColors.DarkBg,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = TBColors.DarkBg
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (requests.isEmpty() && !isLoading) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tidak ada permintaan", color = Color.White.copy(alpha = 0.5f))
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
                                Text("Tolak", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { onAccept(fromEmail) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TBColors.Primary)
                            ) {
                                Text("Terima", fontSize = 13.sp)
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─── Shared UserListItem ──────────────────────────────────────────────────────

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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .clickable { onTap() }
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TBColors.Primary.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = email.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = email.substringBefore("@"),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = email,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── End of file ─────────────────────────────────────────────────────────────
