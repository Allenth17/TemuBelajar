package com.hiralen.temubelajar.auth.presentation.me

sealed class MeState<out M> {
    data object Loading : MeState<Nothing>()
    data object Idle : MeState<Nothing>()
    data class Success<out M>(
        val data: M
    ) : MeState<M>()
    data class Error(
        val message: String
    ) : MeState<Nothing>()
}