package com.hiralen.temubelajar.core.data

import com.hiralen.temubelajar.core.domain.*
import com.hiralen.temubelajar.core.presentation.BASE_URL
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class DefaultAccountRepository(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage
) : AccountRepository {

    override suspend fun register(account: com.hiralen.temubelajar.core.domain.AccountRegister): Result<Unit> =
        runCatching {
            client.post("$BASE_URL/api/register") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("email", account.email)
                    put("password", account.password)
                    put("username", account.username)
                    put("name", account.name)
                    put("phone", account.phone)
                    put("university", account.university)
                })
            }
        }.fold({ Result.Success(Unit) }, { Result.Error(it.message ?: "Unknown error") })

    override suspend fun verifyOtp(email: String, otp: String): Result<Unit> = runCatching {
        client.post("$BASE_URL/api/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("email", email)
                put("otp", otp)
            })
        }
    }.fold({ Result.Success(Unit) }, { Result.Error(it.message ?: "Unknown error") })

    override suspend fun resendOtp(email: String): Result<Unit> = runCatching {
        client.post("$BASE_URL/api/resend-otp") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("email", email) })
        }
    }.fold({ Result.Success(Unit) }, { Result.Error(it.message ?: "Unknown error") })

    override suspend fun login(account: com.hiralen.temubelajar.core.domain.AccountLogin): Result<LoginResponse> = try {
        val resp = client.post("$BASE_URL/api/login") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                // API Gateway accepts email OR username in this field
                put("email_or_username", account.email)
                put("password", account.password)
            })
        }
        if (resp.status == HttpStatusCode.OK) {
            val body = resp.body<LoginResponse>()
            Result.Success(body)
        } else {
            val errorJson = resp.body<JsonObject>()
            val errorMessage = errorJson["error"]?.jsonPrimitive?.content ?: "Login gagal (Status: ${resp.status.value})"
            Result.Error(errorMessage)
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Login gagal")
    }

    override suspend fun logout(token: String): Result<Unit> = runCatching {
        client.post("$BASE_URL/api/logout") {
            bearerAuth(token)
        }
    }.fold({ Result.Success(Unit) }, { Result.Error(it.message ?: "Logout gagal") })

    override suspend fun me(token: String): Result<MeResponse> = try {
        val resp = client.get("$BASE_URL/api/me") { bearerAuth(token) }
        Result.Success(resp.body<MeResponse>())
    } catch (e: Exception) {
        Result.Error(e.message ?: "Gagal fetch user")
    }

    override fun saveToken(token: String) = tokenStorage.saveToken(token)
    override fun getToken(): String? = tokenStorage.getToken()
    override fun clearToken() = tokenStorage.clearToken()
}
