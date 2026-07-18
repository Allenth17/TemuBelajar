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

    // Phase 5.18 — while the matchmaking WebSocket is open, intercept the
    // system/gesture back button so a stray back press cancels the search
    // and returns the user to IDLE instead of trying to pop the (root)
    // navigation stack. When the status leaves SEARCHING the BackHandler
    // is disabled and normal back handling applies again. Uses the
    // project's existing cross-platform `core.ui.BackHandler` shim, which
    // delegates to `androidx.activity.compose.BackHandler` on Android and
    // is a no-op on desktop/wasmJs/iOS (no system back gesture there).
    BackHandler(enabled = state.status == MatchingStatus.SEARCHING) {
        component.stopMatching()
    }

    val infiniteTransition = rememberInfiniteTransition()
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing))
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing, delayMillis = 600))
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing, delayMillis = 1200))
    )
    val ring4 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing, delayMillis = 1800))
    )

    val isSearching = state.status == MatchingStatus.SEARCHING

    Box(modifier = Modifier.fillMaxSize().background(LinearColors.Canvas)) {
        // Top bar — Linear wordmark left, queue pill + logout right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = TBSpace.MD, vertical = TBSpace.SM),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "TemuBelajar",
                    style = TBTypography.CardTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = LinearColors.Ink
                )
                if (state.userEmail.isNotEmpty()) {
                    Text(
                        state.userEmail,
                        style = TBTypography.Caption,
                        color = LinearColors.InkTertiary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TBSpace.XS)
            ) {
                if (state.queueSize > 0) {
                    Surface(
                        color = LinearColors.Surface2,
                        shape = TBShapes.Pill,
                        border = androidx.compose.foundation.BorderStroke(1.dp, LinearColors.HairlineStrong)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(Modifier.size(6.dp).background(LinearColors.Success, CircleShape))
                            Text(
                                "${state.queueSize} searching",
                                style = TBTypography.Caption,
                                color = LinearColors.InkMuted
                            )
                        }
                    }
                }

                IconButton(onClick = component::logout) {
                    Icon(
                        TablerIcons.Logout,
                        contentDescription = "Sign out",
                        tint = LinearColors.InkSubtle,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Radar + status + CTA
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TBSpace.XL),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                if (isSearching) {
                    listOf(ring1, ring2, ring3, ring4).forEach { progress ->
                        Box(
                            modifier = Modifier
                                .size((140 + 140 * progress).dp)
                                .alpha(1f - progress)
                                .border(
                                    width = (2f - 1.5f * progress).dp,
                                    color = LinearColors.Primary.copy(alpha = 0.6f - 0.6f * progress),
                                    shape = CircleShape
                                )
                        )
                    }
                } else {
                    Box(Modifier.size(200.dp).border(1.dp, LinearColors.Hairline, CircleShape))
                    Box(Modifier.size(260.dp).border(1.dp, LinearColors.Hairline.copy(alpha = 0.5f), CircleShape))
                }

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(LinearColors.Surface2)
                        .border(1.dp, LinearColors.HairlineStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Phase 1.5 — Home no longer pre-initializes the WebRTC
                    // engine, so there is no local preview to mount here. The
                    // avatar stays a generic icon until the user taps Start
                    // matching and a real match_found arrives.
                    Icon(
                        imageVector = if (isSearching) TablerIcons.Wifi else TablerIcons.Users,
                        contentDescription = null,
                        tint = LinearColors.Primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(TBSpace.LG))

            Text(
                text = when (state.status) {
                    MatchingStatus.IDLE     -> "Ready to meet someone new?"
                    MatchingStatus.SEARCHING -> "Finding your match…"
                    MatchingStatus.FOUND    -> "Match found"
                    MatchingStatus.ERROR    -> "Try again"
                },
                style = TBTypography.Headline,
                color = LinearColors.Ink,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(TBSpace.XS))

            // Phase 5.15 — the error TEXT itself was previously rendered both
            // here in the status sub-line AND again via TBErrorBanner below,
            // duplicating the message on screen. The sub-line now only carries
            // neutral guidance; the actionable error copy lives in the banner.
            Text(
                text = when (state.status) {
                    MatchingStatus.IDLE      -> "Tap the button below to start matching."
                    MatchingStatus.SEARCHING -> "Our algorithm is finding a study partner."
                    MatchingStatus.FOUND     -> "Connecting video call…"
                    MatchingStatus.ERROR     -> "Tap the button below to try again."
                },
                style = TBTypography.Body,
                color = LinearColors.InkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(TBSpace.XL))

            if (isSearching) {
                OutlinedButton(
                    onClick = component::stopMatching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = TBShapes.MD,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LinearColors.HairlineStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LinearColors.Ink,
                        containerColor = LinearColors.Surface1
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = LinearColors.Primary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Hentikan pencarian", style = TBTypography.Button)
                    }
                }
            } else {
                TBPrimaryButton(
                    // Phase 5.37 — match the rest of the app (ID).
                    text = "Mulai mencari",
                    onClick = component::startMatching,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.status != MatchingStatus.FOUND,
                    icon = TablerIcons.ArrowRight
                )
            }

            if (state.error != null && state.status == MatchingStatus.ERROR) {
                Spacer(Modifier.height(TBSpace.SM))
                TBErrorBanner(state.error!!)
            }
        }

        // Phase 5.20 — the "All conversations are end-to-end encrypted 🔒"
        // footer was removed: with cleartext signaling and chat text routed
        // through the gateway, only the media path (DTLS-SRTP) is actually
        // E2E. Advertising otherwise is misleading.
    }
}
