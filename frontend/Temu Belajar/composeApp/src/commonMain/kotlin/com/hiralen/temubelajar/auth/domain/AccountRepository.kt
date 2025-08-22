package com.hiralen.temubelajar.auth.domain

import com.hiralen.temubelajar.auth.data.database.Token
import com.hiralen.temubelajar.core.domain.DataError
import com.hiralen.temubelajar.core.domain.Result

interface AccountRepository {
    suspend fun register(
        accountRegister: AccountRegister
    ) : Result<Message, DataError.Remote>

    suspend fun verifyOtp(
        email: String,
        otp: String
    ) : Result<Message, DataError.Remote>

    suspend fun login(
        accountLogin: AccountLogin
    ) : Result<LoginResponse, DataError.Remote>

    suspend fun me(
        token: String
    ) : Result<Account, DataError.Remote>

    suspend fun logout(
        token: String
    ) : Result<Message, DataError.Remote>

    suspend fun getToken(): Token?
    suspend fun saveToken(token: String)
    suspend fun clearToken(token: String)
}