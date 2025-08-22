package com.hiralen.temubelajar.auth.domain

data class AccountRegister(
    val email: String,
    val password: String,
    val username: String,
    val name: String,
    val phone: String,
    val university: String
)