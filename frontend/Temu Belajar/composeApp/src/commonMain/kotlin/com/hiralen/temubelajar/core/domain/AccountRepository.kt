package com.hiralen.temubelajar.core.domain

interface AccountRepository {
    suspend fun register(account: AccountRegister): Result<Unit>
    suspend fun verifyOtp(email: String, otp: String): Result<Unit>
    suspend fun resendOtp(email: String): Result<Unit>
    suspend fun login(account: AccountLogin): Result<LoginResponse>
    suspend fun logout(token: String): Result<Unit>
    suspend fun me(token: String): Result<MeResponse>

    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
}
