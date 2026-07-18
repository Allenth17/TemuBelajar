package com.hiralen.temubelajar.auth.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.domain.AccountRegister
import com.hiralen.temubelajar.core.domain.Result
import com.hiralen.temubelajar.core.domain.errorOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterState(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val university: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    // Phase 5.14 — explicit success flag (replaces the previously derived
    // `isSuccess = !isLoading && error == null && name.isNotEmpty()` that
    // flipped true as soon as the user typed a name). Set to `true` ONLY
    // after `repository.register()` returns Success; reset by the UI when
    // the success Lottie animation completes or the user taps "Lanjut", and
    // also cleared on any field edit so re-using the screen is clean.
    val isSuccess: Boolean = false
)

class RegisterComponent(
    componentContext: ComponentContext,
    private val authComponent: AuthComponent,
    private val repository: AccountRepository = org.koin.mp.KoinPlatform.getKoin().get()
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun onNameChange(v: String)            { _state.value = _state.value.copy(name = v, error = null, isSuccess = false) }
    fun onUsernameChange(v: String)        { _state.value = _state.value.copy(username = v, error = null, isSuccess = false) }
    fun onEmailChange(v: String)           { _state.value = _state.value.copy(email = v, error = null, isSuccess = false) }
    fun onPhoneChange(v: String)           { _state.value = _state.value.copy(phone = v, error = null, isSuccess = false) }
    fun onUniversityChange(v: String)     { _state.value = _state.value.copy(university = v, error = null, isSuccess = false) }
    fun onPasswordChange(v: String)        { _state.value = _state.value.copy(password = v, error = null, isSuccess = false) }
    fun onConfirmPasswordChange(v: String) { _state.value = _state.value.copy(confirmPassword = v, error = null, isSuccess = false) }

    fun register() {
        val s = _state.value
        when {
            s.name.isBlank()     -> { _state.value = s.copy(error = "Nama tidak boleh kosong"); return }
            s.email.isBlank()    -> { _state.value = s.copy(error = "Email tidak boleh kosong"); return }
            s.password.isBlank() -> { _state.value = s.copy(error = "Password tidak boleh kosong"); return }
            s.password != s.confirmPassword -> { _state.value = s.copy(error = "Konfirmasi password tidak cocok"); return }
        }
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            when (val r = repository.register(
                AccountRegister(
                    email = s.email, password = s.password, username = s.username,
                    name = s.name, phone = s.phone, university = s.university
                )
            )) {
                is Result.Success<*> -> {
                    // Phase 5.14 — keep the registration screen alive briefly
                    // so the success Lottie can play. The user sees the "Lanjut"
                    // button appear; it (and the screen-side animation-completion
                    // callback) calls `proceedToOtp()` to navigate.
                    _state.value = _state.value.copy(isLoading = false, isSuccess = true, error = null)
                }
                else -> {
                    val err = r.errorOrNull() ?: "Registrasi gagal"
                    _state.value = _state.value.copy(isLoading = false, error = err)
                }
            }
        }
    }

    /**
     * Phase 5.14 — invoked either by the success-screen "Lanjut" button or by
     * RegisterScreen's animation-completion handler. Resets the success flag
     * so the screen leaves no stale state behind if the user later navigates
     * back, and triggers the OTP navigation that the legacy `register()` did
     * inline (causing the success Lottie to never be observable).
     */
    fun proceedToOtp() {
        _state.value = _state.value.copy(isSuccess = false)
        authComponent.navigateToOTP(_state.value.email)
    }

    fun goToLogin() = authComponent.navigateToLogin()
}
