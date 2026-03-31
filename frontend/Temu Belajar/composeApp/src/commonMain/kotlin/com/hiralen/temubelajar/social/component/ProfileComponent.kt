package com.hiralen.temubelajar.social.component

import com.arkivanov.decompose.ComponentContext
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
    }

    private fun loadProfile() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, isOwnProfile = targetEmail == currentUserEmail)

            // Load social data from social_service
            val social = socialRepository.getProfileSocial(targetEmail)
            val friends = socialRepository.getFriends(targetEmail)

            _state.value = _state.value.copy(
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

    fun onDestroy() { scope.cancel() }
}
