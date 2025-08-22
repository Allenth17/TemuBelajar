package com.hiralen.temubelajar.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val email: String,
    val username: String,
    val name: String,
    val phone: String,
    val university: String
)