package com.hiralen.temubelajar.social.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.social.data.SocialRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Base state for user list
data class UserListState(
    val emails: List<String> = emptyList(),
    val isLoading: Boolean = true
)

// 1. Followers / Following Component
class FollowersComponent(
    componentContext: ComponentContext,
    private val email: String,
    val type: Type, // Followers or Following
    val onBack: () -> Unit,
    val onProfileTap: (String) -> Unit
) : ComponentContext by componentContext {
    
    enum class Type { FOLLOWERS, FOLLOWING }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val repository = SocialRepository()

    private val _state = MutableStateFlow(UserListState())
    val state: StateFlow<UserListState> = _state.asStateFlow()

    val title = if (type == Type.FOLLOWERS) "Pengikut" else "Mengikuti"

    init { load() }

    private fun load() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val users = if (type == Type.FOLLOWERS) repository.getFollowers(email) else repository.getFollowing(email)
            _state.value = _state.value.copy(emails = users, isLoading = false)
        }
    }

    fun follow(targetEmail: String) {
        scope.launch { repository.follow(targetEmail) }
    }

    fun onDestroy() { scope.cancel() }
}

// 2. Friends Component
class FriendsComponent(
    componentContext: ComponentContext,
    private val email: String,
    val onBack: () -> Unit,
    val onProfileTap: (String) -> Unit
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val repository = SocialRepository()

    private val _state = MutableStateFlow(UserListState())
    val state: StateFlow<UserListState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val users = repository.getFriends(email)
            _state.value = _state.value.copy(emails = users, isLoading = false)
        }
    }

    fun unfriend(targetEmail: String) {
        scope.launch {
            repository.unfriend(targetEmail)
            load()
        }
    }

    fun onDestroy() { scope.cancel() }
}

// 3. Friend Requests Component
class FriendRequestsComponent(
    componentContext: ComponentContext,
    val onBack: () -> Unit,
    val onProfileTap: (String) -> Unit
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val repository = SocialRepository()

    private val _state = MutableStateFlow(UserListState())
    val state: StateFlow<UserListState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val pending = repository.getPendingRequests()
            _state.value = _state.value.copy(emails = pending.map { it.fromEmail }, isLoading = false)
        }
    }

    fun accept(fromEmail: String) {
        scope.launch {
            repository.respondFriendRequest(fromEmail, accept = true)
            load()
        }
    }

    fun reject(fromEmail: String) {
        scope.launch {
            repository.respondFriendRequest(fromEmail, accept = false)
            load()
        }
    }

    fun onDestroy() { scope.cancel() }
}
