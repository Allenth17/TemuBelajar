package com.hiralen.temubelajar.auth.data.network

import com.hiralen.temubelajar.auth.data.dto.AccountDto
import com.hiralen.temubelajar.auth.data.dto.AccountLoginDto
import com.hiralen.temubelajar.auth.data.dto.AccountRegisterDto
import com.hiralen.temubelajar.auth.data.dto.LoginResponseDto
import com.hiralen.temubelajar.auth.data.dto.MessageDto
import com.hiralen.temubelajar.auth.data.dto.VerifyOtpDto
import com.hiralen.temubelajar.auth.data.mappers.toAccount
import com.hiralen.temubelajar.auth.data.mappers.toLoginResponse
import com.hiralen.temubelajar.auth.data.mappers.toMessage
import com.hiralen.temubelajar.auth.domain.Account
import com.hiralen.temubelajar.auth.domain.AccountLogin
import com.hiralen.temubelajar.auth.domain.AccountRegister
import com.hiralen.temubelajar.auth.domain.LoginResponse
import com.hiralen.temubelajar.auth.domain.Message
import com.hiralen.temubelajar.core.data.safeCall
import com.hiralen.temubelajar.core.domain.DataError
import com.hiralen.temubelajar.core.domain.Result
import com.hiralen.temubelajar.core.domain.map
import com.hiralen.temubelajar.core.presentation.BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType


class RemoteAccountDataSource(
    private val httpClient: HttpClient
) : AccountDataSource {

    override suspend fun register(
        accountRegister: AccountRegister
    ) : Result<Message, DataError.Remote> {
        val accountRegisterDto = AccountRegisterDto(
            email = accountRegister.email,
            password = accountRegister.password,
            username = accountRegister.username,
            name = accountRegister.name,
            phone = accountRegister.phone,
            university = accountRegister.university
        )

        return safeCall<MessageDto> {
            httpClient.post(urlString = "${BASE_URL}/register") {
                contentType(ContentType.Application.Json)
                setBody(body = accountRegisterDto)
            }
        }.map { it.toMessage() }
    }

    override suspend fun verifyOtp(
        email: String,
        otp: String
    ) : Result<Message, DataError.Remote> {
        val accountVerifyOtpDto = VerifyOtpDto(
            email = email,
            otp = otp
        )

        return safeCall<MessageDto> {
            httpClient.post(urlString = "${BASE_URL}/verify-otp") {
                contentType(type = ContentType.Application.Json)
                setBody(body = accountVerifyOtpDto)
            }
        }.map { it.toMessage() }
    }
    override suspend fun login(
        accountLogin: AccountLogin
    ) : Result<LoginResponse, DataError.Remote> {
        val accountLoginDto = AccountLoginDto(
            emailOrUsername = accountLogin.emailOrUsername,
            password = accountLogin.password
        )

        return safeCall<LoginResponseDto> {
            httpClient.post(urlString = "${BASE_URL}/login") {
                contentType(type = ContentType.Application.Json)
                setBody(body = accountLoginDto)
            }
        }.map { it.toLoginResponse() }
    }

    override suspend fun me(
        token: String
    ) : Result<Account, DataError.Remote> {
        return safeCall<AccountDto> {
            httpClient.get(
                urlString = "${BASE_URL}/me"
            ) { bearerAuth(token) }
        }.map { it.toAccount() }
    }

    override suspend fun logout(
        token: String
    ) : Result<Message, DataError.Remote> {
        return safeCall<MessageDto> {
            httpClient.post(
                urlString = "${BASE_URL}/logout"
            ) {
                bearerAuth(token)
            }
        }.map { it.toMessage() }
    }
}