package com.hiralen.temubelajar.auth.presentation.forgot_password

sealed class ForgotPasswordState<out F> {
    data object Loading : ForgotPasswordState<Nothing>()
    data object Idle : ForgotPasswordState<Nothing>()
    data class Success<out F>(
        val data: F
    ) : ForgotPasswordState<F>()
    data class Error(
        val message: String
    ) : ForgotPasswordState<Nothing>()
}