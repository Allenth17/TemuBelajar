package com.hiralen.temubelajar.auth.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TokenDao {
    @Upsert
    suspend fun upsert(token: Token)

    @Delete
    suspend fun deleteToken(token: Token)

    @Query("SELECT * FROM Token LIMIT 1")
    suspend fun getToken(): Token?
}