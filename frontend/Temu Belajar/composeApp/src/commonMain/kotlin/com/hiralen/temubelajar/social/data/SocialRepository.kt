package com.hiralen.temubelajar.social.data

import com.hiralen.temubelajar.core.data.HttpClientFactory
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.presentation.BASE_URL
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.mp.KoinPlatform

/**
 * Repository for all social-graph operations — proxied via api_gateway.
 * Uses x-caller-email header (set after login) for server-side auth.
 */
class SocialRepository {
    private val httpClient: HttpClient = KoinPlatform.getKoin().get()
    private val accountRepository: AccountRepository = KoinPlatform.getKoin().get()

    private suspend fun token() = accountRepository.getToken()
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private suspend fun callerEmail(): String {
        val t = token() ?: return "unknown@temubelajar.id"
        try {
            val parts = t.split(".")
            if (parts.size >= 2) {
                // Decode base64 URL safe. Kotlin's Base64 adds padding if needed?
                // Actually the built in Base64 might require correct padding. 
                // Let's manually pad it or just use a simple regex if decoded part is valid.
                var payloadStr = parts[1]
                while (payloadStr.length % 4 != 0) {
                    payloadStr += "="
                }
                val payloadBytes = kotlin.io.encoding.Base64.UrlSafe.decode(payloadStr)
                val payload = payloadBytes.decodeToString()
                val match = Regex("\"email\"\\s*:\\s*\"([^\"]+)\"").find(payload)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        } catch (_: Exception) {}
        return "unknown@temubelajar.id"
    }

    // ── Follow ────────────────────────────────────────────────────────────────

    suspend fun follow(targetEmail: String): Result<Unit> = runCatching {
        httpClient.post("$BASE_URL/api/social/follow") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
            contentType(ContentType.Application.Json)
            setBody("""{"target":"$targetEmail"}""")
        }
    }

    suspend fun unfollow(targetEmail: String): Result<Unit> = runCatching {
        httpClient.delete("$BASE_URL/api/social/follow/$targetEmail") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
        }
    }

    suspend fun getFollowers(email: String, limit: Int = 50, offset: Int = 0): List<String> {
        return try {
            val resp = httpClient.get("$BASE_URL/api/social/followers/$email") {
                bearerAuth(token() ?: "")
                parameter("limit", limit)
                parameter("offset", offset)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["followers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getFollowing(email: String, limit: Int = 50, offset: Int = 0): List<String> {
        return try {
            val resp = httpClient.get("$BASE_URL/api/social/following/$email") {
                bearerAuth(token() ?: "")
                parameter("limit", limit)
                parameter("offset", offset)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["following"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getProfileSocial(email: String): SocialProfile? {
        return try {
            val resp = httpClient.get("$BASE_URL/api/social/profile/$email") {
                bearerAuth(token() ?: "")
                header("X-Caller-Email", callerEmail())
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            SocialProfile(
                email = email,
                followerCount = body["follower_count"]?.jsonPrimitive?.int ?: 0,
                followingCount = body["following_count"]?.jsonPrimitive?.int ?: 0,
                followedByPreview = body["followed_by_preview"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                youFollow = body["you_follow"]?.jsonPrimitive?.boolean ?: false
            )
        } catch (_: Exception) { null }
    }

    // ── Friend requests ───────────────────────────────────────────────────────

    suspend fun sendFriendRequest(targetEmail: String): Result<Unit> = runCatching {
        httpClient.post("$BASE_URL/api/social/friend-request") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
            contentType(ContentType.Application.Json)
            setBody("""{"target":"$targetEmail"}""")
        }
    }

    suspend fun respondFriendRequest(fromEmail: String, accept: Boolean): Result<Unit> = runCatching {
        httpClient.put("$BASE_URL/api/social/friend-request/$fromEmail") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
            contentType(ContentType.Application.Json)
            setBody("""{"action":"${if (accept) "accept" else "reject"}"}""")
        }
    }

    suspend fun unfriend(targetEmail: String): Result<Unit> = runCatching {
        httpClient.delete("$BASE_URL/api/social/friend/$targetEmail") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
        }
    }

    suspend fun getFriends(email: String, limit: Int = 50, offset: Int = 0): List<String> {
        return try {
            val resp = httpClient.get("$BASE_URL/api/social/friends/$email") {
                bearerAuth(token() ?: "")
                parameter("limit", limit)
                parameter("offset", offset)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["friends"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getPendingRequests(): List<PendingRequest> {
        return try {
            val resp = httpClient.get("$BASE_URL/api/social/friend-requests/pending") {
                bearerAuth(token() ?: "")
                header("X-Caller-Email", callerEmail())
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["requests"]?.jsonArray?.map { req ->
                val obj = req.jsonObject
                PendingRequest(
                    fromEmail = obj["from_email"]?.jsonPrimitive?.content ?: "",
                    toEmail = obj["to_email"]?.jsonPrimitive?.content ?: ""
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // ── Block / Report ─────────────────────────────────────────────────────────

    suspend fun block(targetEmail: String): Result<Unit> = runCatching {
        httpClient.post("$BASE_URL/api/social/block") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
            contentType(ContentType.Application.Json)
            setBody("""{"target":"$targetEmail"}""")
        }
    }

    suspend fun unblock(targetEmail: String): Result<Unit> = runCatching {
        httpClient.delete("$BASE_URL/api/social/block/$targetEmail") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
        }
    }

    suspend fun report(targetEmail: String, reason: String, detail: String? = null): Result<Unit> = runCatching {
        val detailPart = if (detail != null) ""","detail":"$detail"""" else ""
        httpClient.post("$BASE_URL/api/social/report") {
            bearerAuth(token() ?: "")
            header("X-Caller-Email", callerEmail())
            contentType(ContentType.Application.Json)
            setBody("""{"target":"$targetEmail","reason":"$reason"$detailPart}""")
        }
    }
}

// ─── Data models ─────────────────────────────────────────────────────────────

data class SocialProfile(
    val email: String,
    val followerCount: Int,
    val followingCount: Int,
    val followedByPreview: List<String>,   // up to 3 emails
    val youFollow: Boolean
)

data class PendingRequest(
    val fromEmail: String,
    val toEmail: String
)
