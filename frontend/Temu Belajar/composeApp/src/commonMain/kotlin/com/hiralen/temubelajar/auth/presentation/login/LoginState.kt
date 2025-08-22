package com.hiralen.temubelajar.auth.presentation.login

sealed class LoginState<out L> {
    data object Loading : LoginState<Nothing>()
    data object Idle : LoginState<Nothing>()
    data class Success<out L>(
        val data: L
    ) : LoginState<L>()
    data class Error(
        val message: String
    ) : LoginState<Nothing>()
}