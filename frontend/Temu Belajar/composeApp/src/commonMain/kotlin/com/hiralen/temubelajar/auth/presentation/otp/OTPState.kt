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

sealed class ROTPState <out RO> {
    data object Loading : ROTPState<Nothing>()
    data object Idle : ROTPState<Nothing>()
    data class Success<out RO>(
        val data: RO
    ) : ROTPState<RO>()
    data class Error(
        val message: String
    ) : ROTPState<Nothing>()
}