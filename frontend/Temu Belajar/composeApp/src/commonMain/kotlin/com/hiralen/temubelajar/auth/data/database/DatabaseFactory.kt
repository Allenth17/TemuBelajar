package com.hiralen.temubelajar.auth.data.database

import androidx.room.RoomDatabase

expect class DatabaseFactory {
    fun create() : RoomDatabase.Builder<TokenDatabase>
}