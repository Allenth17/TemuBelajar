package com.hiralen.temubelajar.auth.presentation.otp

sealed class OTPState <out O> {
    data object Loading : OTPState<Nothing>()
    data object Idle : OTPState<Nothing>()
    data class Success<out O>(
        val data: O
    ) : OTPState<O>()
    data class Error(
        val message: String
    ) : OTPState<Nothing>()
}