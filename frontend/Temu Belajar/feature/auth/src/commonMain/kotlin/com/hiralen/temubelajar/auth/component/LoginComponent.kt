package com.hiralen.temubelajar.auth.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.domain.AccountLogin
import com.hiralen.temubelajar.core.domain.Result
import com.hiralen.temubelajar.core.domain.errorOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class LoginComponent(
    componentContext: ComponentContext,
    private val authComponent: AuthComponent,
    private val repository: AccountRepository = org.koin.mp.KoinPlatform.getKoin().get()
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmailChange(v: String) {
        _state.value = _state.value.copy(email = v, error = null)
    }

    fun onPasswordChange(v: String) {
        _state.value = _state.value.copy(password = v, error = null)
    }

    fun login() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "Email/username dan password tidak boleh kosong")
            return
        }
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            when (val r = repository.login(AccountLogin(s.email, s.password))) {
                is Result.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val resp = r as? Result.Success<com.hiralen.temubelajar.core.domain.LoginResponse>
                    val token = resp?.data?.token ?: ""
                    repository.saveToken(token)
                    authComponent.onVerified()
                }

                else -> {
                    val err = r.errorOrNull() ?: "Login gagal"
                    _state.value = _state.value.copy(isLoading = false, error = err)
                }
            }
        }
    }

    fun goToRegister() = authComponent.navigateToRegister()
}
