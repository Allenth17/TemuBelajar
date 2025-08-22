package com.hiralen.temubelajar.auth.domain

data class Account(
    val email: String,
    val username: String,
    val name: String,
    val phone: String,
    val university: String
)