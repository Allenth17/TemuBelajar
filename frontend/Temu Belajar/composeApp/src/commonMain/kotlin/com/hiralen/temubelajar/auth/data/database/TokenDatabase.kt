package com.hiralen.temubelajar.auth.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Token::class],
    version = 1
)
@ConstructedBy(TokenDatabaseConstructor::class)
abstract class TokenDatabase : RoomDatabase() {
    abstract val dao: TokenDao
    companion object {
        const val DATABASE_NAME = "token.db"
    }
}
