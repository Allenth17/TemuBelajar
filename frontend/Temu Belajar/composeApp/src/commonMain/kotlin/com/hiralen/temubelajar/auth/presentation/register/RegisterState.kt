package com.hiralen.temubelajar.auth.presentation.register

sealed class RegisterState<out R> {
    data object Loading : RegisterState<Nothing>()
    data object Idle : RegisterState<Nothing>()
    data class Success<out R>(
        val data: R
    ) : RegisterState<R>()
    data class Error(
        val message: String
    ) : RegisterState<Nothing>()
}