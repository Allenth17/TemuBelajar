package com.hiralen.temubelajar.app

import androidx.compose.runtime.*
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.*
import com.hiralen.temubelajar.auth.presentation.AuthContent
import com.hiralen.temubelajar.core.ui.TemuBelajarTheme
import com.hiralen.temubelajar.home.presentation.HomeScreen
import com.hiralen.temubelajar.social.presentation.FollowersScreen
import com.hiralen.temubelajar.social.presentation.FriendRequestsScreen
import com.hiralen.temubelajar.social.presentation.FriendsScreen
import com.hiralen.temubelajar.social.presentation.ProfileScreen
import com.hiralen.temubelajar.videochat.presentation.VideoChatScreen

/**
 * RootContent — entry Composable yang di-compose dari MainActivity / Main.kt.
 * Meng-observe Decompose ChildStack dan render screen yang sesuai.
 */
@Composable
fun RootContent(component: RootComponent) {
    TemuBelajarTheme {
        Children(
            stack = component.stack,
            animation = stackAnimation(
                animator = fade() + scale(),
            )
        ) { child ->
            when (val instance = child.instance) {
                is RootComponent.Child.Auth ->
                    AuthContent(component = instance.component)

                is RootComponent.Child.Main ->
                    HomeScreen(component = instance.component)

                is RootComponent.Child.VideoChat ->
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
                        onFollow = { comp.follow(it) } // Only passing follow if needed, but the FollowerScreen allows it
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
                        requests = state.emails, // emails holds the fromEmail list
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
