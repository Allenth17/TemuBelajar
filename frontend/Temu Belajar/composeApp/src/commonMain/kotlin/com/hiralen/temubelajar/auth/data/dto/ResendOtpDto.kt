package com.hiralen.temubelajar.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResendOtpDto(
    val email: String
)