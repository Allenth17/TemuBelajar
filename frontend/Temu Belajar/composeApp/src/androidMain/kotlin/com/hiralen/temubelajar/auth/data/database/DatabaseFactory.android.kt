package com.hiralen.temubelajar.auth.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseFactory(
    private val context: Context
) {
    actual fun create(): RoomDatabase.Builder<TokenDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(TokenDatabase.DATABASE_NAME)
        return Room.databaseBuilder(
            appContext,
            dbFile.path
        )
    }
}