package com.hiralen.temubelajar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.*
import com.hiralen.temubelajar.auth.presentation.AuthContent
import com.hiralen.temubelajar.core.ui.LinearColors
import com.hiralen.temubelajar.core.ui.TemuBelajarTheme
import com.hiralen.temubelajar.home.presentation.HomeScreen
import com.hiralen.temubelajar.social.presentation.FollowersScreen
import com.hiralen.temubelajar.social.presentation.FriendRequestsScreen
import com.hiralen.temubelajar.social.presentation.FriendsScreen
import com.hiralen.temubelajar.social.presentation.ProfileScreen
import com.hiralen.temubelajar.videochat.presentation.VideoChatScreen

@Composable
fun RootContent(component: RootComponent) {
    TemuBelajarTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LinearColors.Canvas)
        ) {
            Children(
                stack = component.stack,
                // Phase 5.8 — was `fade() + scale()`. The scale portion of
                // the animator wraps every screen in a 0.92→1.0 transform,
                // which the live WebRTC <video> element (WASM) + the
                // per-frame BufferedImage (Desktop) gets caught up in:
                // Compose's GPU layer rebuilds the scaled backing surface
                // every frame during the transition AND the WebRTC video
                // animation continues underneath it, so the user sees a
                // quarter-second of "video framed inside a zoom-from-92%"
                // that reads as a stalling screen rather than a deliberate
                // transition. The fade alone is GPU-cheap (alpha-composite
                // of an opaque layer) + reads as a clean crossfade; the
                // scale was leftover from Linear's marketing-site motion
                // spec (hero-card entrance) and never fit a full-screen
                // VoIP shell. Drop the + scale(); keep fade() only.
                animation = stackAnimation(
                    animator = fade(),
                )
            ) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.Auth ->
                        AuthContent(component = instance.component)

                    is RootComponent.Child.Main ->
                        HomeScreen(component = instance.component)

                    is RootComponent.Child.VideoChat->
                        VideoChatScreen(component = instance.component)

                    is RootComponent.Child.Profile ->
                        ProfileScreen(component = instance.component)

                    is RootComponent.Child.Followers -> {
                        val comp = instance.component
                        val state by comp.state.collectAsState()
                        FollowersScreen(
                            title = comp.title,
                            emails = state.emails,
                            isLoading = state.isLoading,
                            onBack = comp.onBack,
                            onProfileTap = comp.onProfileTap,
                            onFollow = { comp.follow(it) }
                        )
                    }

                    is RootComponent.Child.Friends -> {
                        val comp = instance.component
                        val state by comp.state.collectAsState()
                        FriendsScreen(
                            emails = state.emails,
                            isLoading = state.isLoading,
                            onBack = comp.onBack,
                            onProfileTap = comp.onProfileTap,
                            onUnfriend = { comp.unfriend(it) }
                        )
                    }

                    is RootComponent.Child.FriendRequests -> {
                        val comp = instance.component
                        val state by comp.state.collectAsState()
                        FriendRequestsScreen(
                            requests = state.emails,
                            isLoading = state.isLoading,
                            onBack = comp.onBack,
                            onAccept = { comp.accept(it) },
                            onReject = { comp.reject(it) }
                        )
                    }
                }
            }
        }
    }
}
