package com.hiralen.temubelajar.core.data

import com.hiralen.temubelajar.core.domain.*
import com.hiralen.temubelajar.core.presentation.BASE_URL
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class DefaultAccountRepository(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage
) : AccountRepository {

    override suspend fun register(account: com.hiralen.temubelajar.core.domain.AccountRegister): Result<Unit> = try {
        val resp = client.post("$BASE_URL/api/register") {
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
        // 3.1 — treat any 2xx as success; otherwise surface the typed
        // backend error via Result.Error (3.2).
        if (resp.status.value in 200..299) {
            Result.Success(Unit)
        } else {
            Result.Error(extractError(resp))
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error")
    }

    override suspend fun verifyOtp(email: String, otp: String): Result<Unit> = try {
        val resp = client.post("$BASE_URL/api/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("email", email)
                put("otp", otp)
            })
        }
        if (resp.status.value in 200..299) {
            Result.Success(Unit)
        } else {
            Result.Error(extractError(resp))
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error")
    }

    override suspend fun resendOtp(email: String): Result<Unit> = try {
        val resp = client.post("$BASE_URL/api/resend-otp") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("email", email) })
        }
        if (resp.status.value in 200..299) {
            Result.Success(Unit)
        } else {
            Result.Error(extractError(resp))
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error")
    }

    override suspend fun login(account: com.hiralen.temubelajar.core.domain.AccountLogin): Result<LoginResponse> = try {
        val resp = client.post("$BASE_URL/api/login") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("email_or_username", account.email)
                put("password", account.password)
            })
        }
        if (resp.status.value in 200..299) {
            val body = resp.body<LoginResponse>()
            Result.Success(body)
        } else {
            Result.Error(extractError(resp))
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Login gagal")
    }

    override suspend fun logout(token: String): Result<Unit> = try {
        val resp = client.post("$BASE_URL/api/logout") {
            bearerAuth(token)
        }
        if (resp.status.value in 200..299) {
            Result.Success(Unit)
        } else {
            Result.Error(extractError(resp))
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Logout gagal")
    }

    override suspend fun me(token: String): Result<MeResponse> = try {
        val resp = client.get("$BASE_URL/api/me") { bearerAuth(token) }
        if (resp.status.value in 200..299) {
            Result.Success(resp.body<MeResponse>())
        } else if (resp.status == HttpStatusCode.Unauthorized) {
            // 3.7 — never leak the request URL (BadResponseStatusException's
            // default message embeds it). Surface a localized, plain message.
            Result.Error("Sesi habis, silakan login ulang")
        } else {
            Result.Error(extractError(resp))
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Gagal fetch user")
    }

    override fun saveToken(token: String) = tokenStorage.saveToken(token)
    override fun getToken(): String? = tokenStorage.getToken()
    override fun clearToken() = tokenStorage.clearToken()

    /**
     * Extract the backend error string from the response body.
     *
     * Handles the four backend error envelope shapes observed in
     * `backend_elixir/` (see finding 3.3):
     *   1. `{"error": "message"}`
     *   2. `{"error": {"field": ["msg", ...]}}`
     *   3. `{"error": "Validation failed", "details": {...}}` (details dropped)
     *   4. `{"errors": {"detail": "..."}}` (Phoenix FallbackController shape)
     *
     * Safe to call on empty/non-JSON bodies — falls back to
     * `"Error (Status: <code>)"`.
     */
    private suspend fun extractError(resp: HttpResponse): String {
        return try {
            val errorJson = resp.body<JsonObject>()
            val errorField = errorJson["error"]
            // Shape #1: {"error": "message"}
            if (errorField is JsonPrimitive && errorField != JsonNull) {
                errorField.content
            }
            // Shape #2: {"error": {"field": ["msg"]}}
            else if (errorField is JsonObject) {
                errorField.entries.joinToString("; ") { (field, msgs) ->
                    val messages = (msgs as? JsonArray)?.mapNotNull { it.contentOrNull() } ?: emptyList()
                    "$field: ${messages.joinToString(", ")}"
                }
            }
            // Shape #4: {"errors": {"detail": "..."}} (Phoenix FallbackController)
            else {
                val errorsField = errorJson["errors"]
                if (errorsField is JsonObject) {
                    errorsField["detail"]?.contentOrNull() ?: "Error (Status: ${resp.status.value})"
                } else {
                    "Error (Status: ${resp.status.value})"
                }
            }
        } catch (_: Exception) {
            "Error (Status: ${resp.status.value})"
        }
    }
}

private fun JsonElement?.contentOrNull(): String? =
    (this as? JsonPrimitive)?.let { if (it == JsonNull) null else it.content }
