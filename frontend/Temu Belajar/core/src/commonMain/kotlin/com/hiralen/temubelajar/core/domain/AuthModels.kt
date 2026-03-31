package com.hiralen.temubelajar.core.domain

import kotlinx.serialization.Serializable

@Serializable data class AccountLogin(val email: String, val password: String)

@Serializable data class AccountRegister(
    val email: String,
    val password: String,
    val username: String,
    val name: String,
    val phone: String,
    val university: String
)

@Serializable data class LoginResponse(val token: String)
@Serializable data class MeResponse(val email: String, val name: String, val university: String?)
