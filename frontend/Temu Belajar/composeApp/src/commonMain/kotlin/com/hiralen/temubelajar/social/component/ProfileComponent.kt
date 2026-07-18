package com.hiralen.temubelajar.social.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.hiralen.temubelajar.social.data.SocialProfile
import com.hiralen.temubelajar.social.data.SocialRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProfileState(
    val email: String = "",
    val name: String = "",
    val username: String = "",
    val university: String = "",
    val major: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val social: SocialProfile? = null,
    val friends: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isOwnProfile: Boolean = false
)

sealed interface ProfileAction {
    data object Follow : ProfileAction
    data object Unfollow : ProfileAction
    data object SendFriendRequest : ProfileAction
    data object Unfriend : ProfileAction
    data class Block(val reason: String = "") : ProfileAction
    data class Report(val reason: String, val detail: String?) : ProfileAction
}

class ProfileComponent(
    componentContext: ComponentContext,
    private val targetEmail: String,
    private val currentUserEmail: String,
    val onBack: () -> Unit,
    val onViewFollowers: (email: String) -> Unit,
    val onViewFollowing: (email: String) -> Unit,
    val onViewFriends: (email: String) -> Unit
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val socialRepository = SocialRepository()

    private val _state = MutableStateFlow(ProfileState(email = targetEmail))
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        loadProfile()
        // Phase 1.1 — wire destroy to Decompose lifecycle (was dead code).
        lifecycle.doOnDestroy { scope.cancel() }
    }

    private fun loadProfile() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, isOwnProfile = targetEmail == currentUserEmail)

            // Phase 5.35 — ProfileComponent used to populate only the
            // social-graph counts (follower / following / youFollow) and left
            // every identity field in ProfileState blank. The screen thus
            // rendered with the correct number of followers next to an empty
            // "name" + empty university/major/bio for every profile view,
            // including the user's own. Now we fetch the public profile from
            // user_service's `/api/user/:email` (proxied via the gateway) and
            // populate `name` / `username` / `university` / `major` / `bio` /
            // `avatarUrl` from it. The data layer (`SocialRepository.getPublicProfile`)
            // degrades to null on any failure; we guard each field with an
            // elvis so a 404/ network blip still renders the email + social
            // counts rather than crashing the screen.
            val profile = socialRepository.getPublicProfile(targetEmail)
            // Load social data from social_service
            val social = socialRepository.getProfileSocial(targetEmail)
            val friends = socialRepository.getFriends(targetEmail)

            _state.value = _state.value.copy(
                name = profile?.name ?: "",
                username = profile?.username ?: "",
                university = profile?.university ?: "",
                major = profile?.major ?: "",
                bio = profile?.bio ?: "",
                avatarUrl = profile?.avatarUrl,
                social = social,
                friends = friends,
                isLoading = false
            )
        }
    }

    fun performAction(action: ProfileAction) {
        scope.launch {
            when (action) {
                ProfileAction.Follow -> {
                    socialRepository.follow(targetEmail)
                    reloadSocial()
                }
                ProfileAction.Unfollow -> {
                    socialRepository.unfollow(targetEmail)
                    reloadSocial()
                }
                ProfileAction.SendFriendRequest -> {
                    socialRepository.sendFriendRequest(targetEmail)
                }
                ProfileAction.Unfriend -> {
                    socialRepository.unfriend(targetEmail)
                    reloadSocial()
                }
                is ProfileAction.Block -> {
                    socialRepository.block(targetEmail)
                    onBack()
                }
                is ProfileAction.Report -> {
                    socialRepository.report(targetEmail, action.reason, action.detail)
                }
            }
        }
    }

    private suspend fun reloadSocial() {
        val social = socialRepository.getProfileSocial(targetEmail)
        _state.value = _state.value.copy(social = social)
    }
}
