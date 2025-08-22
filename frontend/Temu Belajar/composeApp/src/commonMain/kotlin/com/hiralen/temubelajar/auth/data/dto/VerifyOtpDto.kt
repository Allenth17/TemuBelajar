package com.hiralen.temubelajar.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyOtpDto(
    val email: String,
    val otp: String
)