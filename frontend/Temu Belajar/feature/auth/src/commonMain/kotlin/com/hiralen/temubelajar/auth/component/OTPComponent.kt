package com.hiralen.temubelajar.auth.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.domain.Result
import com.hiralen.temubelajar.core.domain.errorOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OTPState(
    val otp: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val isResending: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * OTPComponent — handles email OTP verification after registration.
 *
 * Back navigation:
 *   - System back / "Kembali ke Login" button → [navigateBackToLogin] → replaceAll(Login)
 *   - The OTP screen replaces Register in the back-stack (see AuthComponent.navigateToOTP),
 *     so system back already lands on Login. [navigateBackToLogin] is an explicit path that
 *     also clears any lingering OTP state and resets the full auth stack.
 */
class OTPComponent(
    componentContext: ComponentContext,
    private val authComponent: AuthComponent,
    val email: String,
    private val repository: AccountRepository = org.koin.mp.KoinPlatform.getKoin().get()
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val _state = MutableStateFlow(OTPState(email = email))
    val state: StateFlow<OTPState> = _state.asStateFlow()

    fun onOtpChange(v: String) {
        _state.value = _state.value.copy(
            otp = v.filter { it.isDigit() }.take(6),
            error = null
        )
    }

    fun verify() {
        val s = _state.value
        if (s.otp.length != 6) {
            _state.value = s.copy(error = "Masukkan 6 digit OTP")
            return
        }
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            when (val r = repository.verifyOtp(email = s.email, otp = s.otp)) {
                is Result.Success<*> -> authComponent.onVerified()
                else -> {
                    val err = r.errorOrNull() ?: "Verifikasi gagal"
                    _state.value = _state.value.copy(isLoading = false, error = err)
                }
            }
        }
    }

    fun resend() {
        scope.launch {
            _state.value = _state.value.copy(isResending = true, error = null, successMessage = null)
            when (val r = repository.resendOtp(email = email)) {
                is Result.Success<*> -> _state.value = _state.value.copy(
                    isResending = false,
                    successMessage = "OTP baru sudah dikirim ke $email"
                )

                else -> {
                    val err = r.errorOrNull() ?: "Gagal mengirim OTP"
                    _state.value = _state.value.copy(isResending = false, error = err)
                }
            }
        }
    }

    /**
     * Navigates back to the Login screen, resetting the entire auth stack.
     * Called when the user explicitly chooses to go back from the OTP screen
     * (e.g. tapping "Kembali ke Login" or pressing the system back button).
     */
    fun navigateBackToLogin() {
        _state.value = OTPState(email = email) // clear OTP input + errors
        authComponent.navigateToLogin()
    }
}
