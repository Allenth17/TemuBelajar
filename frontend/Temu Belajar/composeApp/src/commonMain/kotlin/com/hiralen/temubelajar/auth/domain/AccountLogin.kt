package com.hiralen.temubelajar.auth.domain

data class AccountLogin(
    val emailOrUsername: String,
    val password: String
)