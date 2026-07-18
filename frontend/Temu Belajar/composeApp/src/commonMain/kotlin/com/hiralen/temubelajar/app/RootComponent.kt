package com.hiralen.temubelajar.app

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.hiralen.temubelajar.auth.component.AuthComponent
import com.hiralen.temubelajar.home.component.HomeComponent
import com.hiralen.temubelajar.social.component.FollowersComponent
import com.hiralen.temubelajar.social.component.FriendRequestsComponent
import com.hiralen.temubelajar.social.component.FriendsComponent
import com.hiralen.temubelajar.social.component.ProfileComponent
import com.hiralen.temubelajar.videochat.component.VideoChatComponent
import kotlinx.serialization.Serializable

/**
 * RootComponent — top-level Decompose component.
 *
 * Drives navigation between the Auth flow and the Main flow, then the leaf
 * screens (video chat, profile, followers, friends, friend requests).
 *
 * `@OptIn(DelicateDecomposeApi::class)` is class-level because every
 * `navigation.push(Config.*)` call touches the `StackNavigation` API surface
 * that Decompose marks as delicate (we intentionally rely on push across
 * the navigation coroutine for in-call routing).
 */
@OptIn(com.arkivanov.decompose.DelicateDecomposeApi::class)
class RootComponent(
    componentContext: ComponentContext,
    private val currentUserEmail: String = "", // Placeholder if needed globally, but we can pass it
    private val onAuthSuccess: suspend () -> Boolean = { false }
) : ComponentContext by componentContext {

    private val tokenStorage = com.hiralen.temubelajar.core.data.TokenStorage()
    private val navigation = StackNavigation<Config>()

    // Phase 5.6 — keep a weak/strong reference to the live HomeComponent so
    // `VideoChatComponent.onNext` can not just `navigation.pop()` back to
    // Home (which leaves the user staring at the "Mulai mencari" CTA, having
    // to tap it after every Next) but ALSO immediately kick off a fresh
    // matching run on the existing HomeComponent instance. We bypass
    // re-creating Home (which would re-fetch `/api/me`, re-acquire the WS,
    // and reset the loaded user info) by holding the reference here.
    // Set every time childFactory creates the Main child; null when Main
    // is replaced with Auth (navigateToAuth → replaceAll → Main destroyed).
    private var homeComponentRef: HomeComponent? = null

    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = if (tokenStorage.getToken() != null) Config.Main else Config.Auth,
        handleBackButton = true,
        childFactory = ::createChild
    )

    private fun createChild(config: Config, ctx: ComponentContext): Child {
        return when (config) {
            Config.Auth -> Child.Auth(
                AuthComponent(
                    componentContext = ctx,
                    onLoginSuccess = { navigateToMain() }
                )
            )
            Config.Main -> Child.Main(
                HomeComponent(
                    componentContext = ctx,
                    onMatchFound = { pairId, role, peerEmail, peerUni ->
                        navigateToVideoChat(pairId, role, peerEmail, peerUni)
                    },
                    onLogout = { navigateToAuth() }
                ).also { homeComponentRef = it }
            )
            is Config.VideoChat -> Child.VideoChat(
                VideoChatComponent(
                    componentContext = ctx,
                    pairId = config.pairId,
                    role = config.role,
                    peerEmail = config.peerEmail,
                    peerUniversity = config.peerUniversity,
                    onBack = { navigation.pop() },
                    onNext = {
                        // Phase 5.6 — was: bare `navigation.pop()` left the
                        // user on Home staring at the "Mulai mencari" CTA.
                        // Now: pop back to Home AND immediately fire
                        // `startMatching()` on the same HomeComponent so
                        // the user slides directly into the next queue
                        // instead of being prompted to tap again. OmeTV-spec
                        // UX matches the swipe-to-next affordance users
                        // expect from random-video-chat apps. Pop happens
                        // first so the VideoChat's WS scopes cancel before
                        // Home re-opens the matchmaking channel.
                        navigation.pop()
                        homeComponentRef?.startMatching()
                    },
                    onViewProfile = { email -> navigateToProfile(email) }
                )
            )
            is Config.Profile -> Child.Profile(
                ProfileComponent(
                    componentContext = ctx,
                    targetEmail = config.email,
                    currentUserEmail = "", // Will be verified dynamically by API using X-Caller-Email inside the service
                    onBack = { navigation.pop() },
                    onViewFollowers = { email -> navigateToFollowers(email, FollowersComponent.Type.FOLLOWERS) },
                    onViewFollowing = { email -> navigateToFollowers(email, FollowersComponent.Type.FOLLOWING) },
                    onViewFriends = { email -> navigateToFriends(email) }
                )
            )
            is Config.Followers -> Child.Followers(
                FollowersComponent(
                    componentContext = ctx,
                    email = config.email,
                    type = config.type,
                    onBack = { navigation.pop() },
                    onProfileTap = { email -> navigateToProfile(email) }
                )
            )
            is Config.Friends -> Child.Friends(
                FriendsComponent(
                    componentContext = ctx,
                    email = config.email,
                    onBack = { navigation.pop() },
                    onProfileTap = { email -> navigateToProfile(email) }
                )
            )
            Config.FriendRequests -> Child.FriendRequests(
                FriendRequestsComponent(
                    componentContext = ctx,
                    onBack = { navigation.pop() },
                    onProfileTap = { email -> navigateToProfile(email) }
                )
            )
        }
    }

    fun navigateToMain() {
        navigation.replaceAll(Config.Main)
    }

    fun navigateToAuth() {
        homeComponentRef = null
        navigation.replaceAll(Config.Auth)
    }

    fun navigateToVideoChat(pairId: String, role: String, peerEmail: String, peerUniversity: String) {
        navigation.push(Config.VideoChat(pairId, role, peerEmail, peerUniversity))
    }

    fun navigateToProfile(email: String) {
        navigation.push(Config.Profile(email))
    }

    fun navigateToFollowers(email: String, type: FollowersComponent.Type) {
        navigation.push(Config.Followers(email, type))
    }

    fun navigateToFriends(email: String) {
        navigation.push(Config.Friends(email))
    }

    fun navigateToFriendRequests() {
        navigation.push(Config.FriendRequests)
    }

    // ─── Config ──────────────────────────────────────────────────────────────

    @Serializable
    sealed interface Config {
        @Serializable data object Auth : Config
        @Serializable data object Main : Config
        @Serializable data class VideoChat(
            val pairId: String,
            val role: String,
            val peerEmail: String,
            val peerUniversity: String = ""
        ) : Config
        @Serializable data class Profile(val email: String) : Config
        @Serializable data class Followers(val email: String, val type: FollowersComponent.Type) : Config
        @Serializable data class Friends(val email: String) : Config
        @Serializable data object FriendRequests : Config
    }

    // ─── Child ───────────────────────────────────────────────────────────────

    sealed interface Child {
        data class Auth(val component: AuthComponent) : Child
        data class Main(val component: HomeComponent) : Child
        data class VideoChat(val component: VideoChatComponent) : Child
        data class Profile(val component: ProfileComponent) : Child
        data class Followers(val component: FollowersComponent) : Child
        data class Friends(val component: FriendsComponent) : Child
        data class FriendRequests(val component: FriendRequestsComponent) : Child
    }
}
