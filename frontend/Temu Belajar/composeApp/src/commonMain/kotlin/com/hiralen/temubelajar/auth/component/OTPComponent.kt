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
        _state.value = _state.value.copy(otp = v.filter { it.isDigit() }.take(6), error = null)
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
            _state.value = _state.value.copy(isResending = true, error = null)
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
}
