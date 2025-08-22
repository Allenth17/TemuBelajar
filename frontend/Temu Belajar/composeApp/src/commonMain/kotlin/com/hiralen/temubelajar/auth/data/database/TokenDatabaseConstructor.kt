package com.hiralen.temubelajar.auth.data.database

import androidx.room.RoomDatabaseConstructor

expect object TokenDatabaseConstructor: RoomDatabaseConstructor<TokenDatabase> {
    override fun initialize(): TokenDatabase
}