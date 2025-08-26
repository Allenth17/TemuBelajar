package com.hiralen.temubelajar.auth.data.network

import com.hiralen.temubelajar.auth.domain.Account
import com.hiralen.temubelajar.auth.domain.AccountLogin
import com.hiralen.temubelajar.auth.domain.AccountRegister
import com.hiralen.temubelajar.auth.domain.LoginResponse
import com.hiralen.temubelajar.auth.domain.Message
import com.hiralen.temubelajar.core.domain.DataError
import com.hiralen.temubelajar.core.domain.Result

interface AccountDataSource {
    suspend fun register(
        accountRegister: AccountRegister
    ) : Result<Message, DataError.Remote>

    suspend fun verifyOtp(
        email: String,
        otp: String
    ) : Result<Message, DataError.Remote>

    suspend fun resendOtp(
        email: String
    ) : Result<Message, DataError.Remote>

    suspend fun login(
        accountLogin: AccountLogin
    ) : Result<LoginResponse, DataError.Remote>

    suspend fun me(
        token: String?
    ) : Result<Account, DataError.Remote>

    suspend fun logout(
        token: String?
    ) : Result<Message, DataError.Remote>
}