package com.hiralen.temubelajar.auth.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Token(
    @PrimaryKey
    val token: String
)