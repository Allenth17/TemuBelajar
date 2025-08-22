package com.hiralen.temubelajar.auth.data.repository

import com.hiralen.temubelajar.auth.data.database.Token
import com.hiralen.temubelajar.auth.data.database.TokenDao
import com.hiralen.temubelajar.auth.data.network.AccountDataSource
import com.hiralen.temubelajar.auth.domain.AccountLogin
import com.hiralen.temubelajar.auth.domain.AccountRegister
import com.hiralen.temubelajar.auth.domain.AccountRepository

class DefaultAccountRepo(
    private val remoteDataSource: AccountDataSource,
    private val tokenDao: TokenDao
) : AccountRepository {
    override suspend fun register(
        accountRegister: AccountRegister
    ) = remoteDataSource.register(accountRegister)

    override suspend fun verifyOtp(
        email: String,
        otp: String
    ) = remoteDataSource.verifyOtp(email, otp)

    override suspend fun login(
        accountLogin: AccountLogin
    ) = remoteDataSource.login(accountLogin)

    override suspend fun me(
        token: String
    ) = remoteDataSource.me(token)

    override suspend fun logout(
        token: String
    ) = remoteDataSource.logout(token)

    override suspend fun getToken(): Token? {
        return tokenDao.getToken()
    }

    override suspend fun saveToken(token: String) {
        tokenDao.upsert(Token(token = token))
    }

    override suspend fun clearToken(token: String) {
        tokenDao.deleteToken(Token(token = token))
    }
}