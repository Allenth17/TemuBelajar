package com.hiralen.temubelajar.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountLoginDto(
    @SerialName("email_or_username") val emailOrUsername: String,
    val password: String
)