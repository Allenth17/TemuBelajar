package com.hiralen.temubelajar.home.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.home.component.HomeComponent
import com.hiralen.temubelajar.home.component.MatchingStatus
import com.hiralen.temubelajar.core.ui.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@Composable
fun HomeScreen(component: HomeComponent) {
    val state by component.state.collectAsState()

    // Infinite radar pulse animations (4 rings, staggered)
    val infiniteTransition = rememberInfiniteTransition()
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing, delayMillis = 500))
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing, delayMillis = 1000))
    )
    val ring4 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing, delayMillis = 1500))
    )

    val isSearching = state.status == MatchingStatus.SEARCHING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TBColors.Background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TemuBelajar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = TBColors.Primary
                )
                if (state.userEmail.isNotEmpty()) {
                    Text(state.userEmail, fontSize = 11.sp, color = TBColors.TextPrimary.copy(alpha = 0.6f))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Online indicator
                if (state.queueSize > 0) {
                    Surface(
                        color = TBColors.Success.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(Modifier.size(6.dp).background(TBColors.Success, CircleShape))
                            Text("${state.queueSize} mencari", fontSize = 11.sp, color = TBColors.Success)
                        }
                    }
                }

                // Logout
                IconButton(onClick = component::logout) {
                    Icon(TablerIcons.Logout, contentDescription = "Keluar", tint = TBColors.TextPrimary.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                }
            }
        }

        // Main content — centered radar + button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ─── Radar ──────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSearching) {
                    // 4 expanding rings
                    listOf(ring1, ring2, ring3, ring4).forEach { progress ->
                        Box(
                            modifier = Modifier
                                .size((140 + 140 * progress).dp)
                                .alpha(1f - progress)
                                .border(
                                    width = (2f - 1.5f * progress).dp,
                                    color = TBColors.Primary.copy(alpha = 0.6f - 0.6f * progress),
                                    shape = CircleShape
                                )
                        )
                    }
                } else {
                    // Idle — static subtle ring
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(1.dp, TBColors.CardBorder, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(1.dp, TBColors.CardBorder.copy(alpha = 0.5f), CircleShape)
                    )
                }

                // Center circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            TBColors.Primary.copy(alpha = if (isSearching) 0.15f else 0.05f)
                        )
                        .border(
                            width = 2.dp,
                            color = TBColors.Primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isCameraReady) {
                        com.hiralen.temubelajar.videochat.presentation.LocalVideoView(
                            renderer = component.webRtcManager.localVideoRenderer,
                            modifier = Modifier.fillMaxSize(),
                            isMuted = false
                        )
                    } else {
                        Icon(
                            imageVector = if (isSearching) TablerIcons.Wifi else TablerIcons.Users,
                            contentDescription = null,
                            tint = TBColors.Primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Status text
            Text(
                text = when (state.status) {
                    MatchingStatus.IDLE     -> "Siap bertemu orang baru?"
                    MatchingStatus.SEARCHING -> "Sedang mencari teman..."
                    MatchingStatus.FOUND    -> "Teman ditemukan! 🎉"
                    MatchingStatus.ERROR    -> "Coba lagi"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TBColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when (state.status) {
                    MatchingStatus.IDLE      -> "Tap tombol di bawah untuk mulai"
                    MatchingStatus.SEARCHING -> "Algoritma kami sedang mencarikan pasangan"
                    MatchingStatus.FOUND     -> "Menyambungkan video call..."
                    MatchingStatus.ERROR     -> state.error ?: "Terjadi kesalahan"
                },
                fontSize = 14.sp,
                color = TBColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // Action button
            if (isSearching) {
                OutlinedButton(
                    onClick = component::stopMatching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TBColors.Primary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TBColors.Primary,
                        containerColor = TBColors.Primary.copy(alpha = 0.05f)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = TBColors.Primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Berhenti Mencari", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            } else {
                TBPrimaryButton(
                    text = "Mulai Cari Teman",
                    onClick = component::startMatching,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.status != MatchingStatus.FOUND && state.isCameraReady
                )
            }

            if (state.error != null && state.status == MatchingStatus.ERROR) {
                Spacer(Modifier.height(12.dp))
                TBErrorBanner(state.error!!)
            }
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "Semua percakapan terenkripsi  🔒",
                fontSize = 12.sp,
                color = TBColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
