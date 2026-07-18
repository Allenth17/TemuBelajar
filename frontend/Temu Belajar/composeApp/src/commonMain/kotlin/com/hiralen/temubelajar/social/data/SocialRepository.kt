package com.hiralen.temubelajar.social.data

import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.presentation.BASE_URL
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.mp.KoinPlatform

/**
 * Repository for all social-graph operations — proxied via api_gateway.
 * Caller identity is derived server-side from the Bearer token by the gateway;
 * the client never sends X-Caller-Email (that was an auth-bypass primitive).
 */
class SocialRepository {
    private val httpClient: HttpClient = KoinPlatform.getKoin().get()
    private val accountRepository: AccountRepository = KoinPlatform.getKoin().get()

    private suspend fun token(): String? = accountRepository.getToken()

    /** Returns the token or fails fast — never sends an empty bearer. */
    private suspend fun requireToken(): String =
        token() ?: throw IllegalStateException("Not authenticated")

    // ── Follow ────────────────────────────────────────────────────────────────

    suspend fun follow(targetEmail: String): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.post("$BASE_URL/api/social/follow") {
            bearerAuth(t)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("target", targetEmail) }.toString())
        }
    }

    suspend fun unfollow(targetEmail: String): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.delete("$BASE_URL/api/social/follow/${targetEmail.encodeURLPathPart()}") {
            bearerAuth(t)
        }
    }

    suspend fun getFollowers(email: String, limit: Int = 50, offset: Int = 0): List<String> {
        return try {
            val t = token() ?: return emptyList()
            val resp = httpClient.get("$BASE_URL/api/social/followers/${email.encodeURLPathPart()}") {
                bearerAuth(t)
                parameter("limit", limit)
                parameter("offset", offset)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["followers"]?.jsonArray?.mapNotNull { it.contentOrNull() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getFollowing(email: String, limit: Int = 50, offset: Int = 0): List<String> {
        return try {
            val t = token() ?: return emptyList()
            val resp = httpClient.get("$BASE_URL/api/social/following/${email.encodeURLPathPart()}") {
                bearerAuth(t)
                parameter("limit", limit)
                parameter("offset", offset)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["following"]?.jsonArray?.mapNotNull { it.contentOrNull() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getProfileSocial(email: String): SocialProfile? {
        return try {
            val t = token() ?: return null
            val resp = httpClient.get("$BASE_URL/api/social/profile/${email.encodeURLPathPart()}") {
                bearerAuth(t)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            SocialProfile(
                email = email,
                followerCount = body["follower_count"]?.intOrNull() ?: 0,
                followingCount = body["following_count"]?.intOrNull() ?: 0,
                followedByPreview = body["followed_by_preview"]?.jsonArray?.mapNotNull { it.contentOrNull() } ?: emptyList(),
                youFollow = body["you_follow"]?.booleanOrNull() ?: false
            )
        } catch (_: Exception) { null }
    }

    /**
     * Phase 5.35 — fetch a user's public profile (name/username/university/
     * major/bio/avatar_url) from user_service via the gateway's
     * `/api/user/:email` proxy. Without this, `ProfileComponent.loadProfile`
     * only had access to the social-graph counts, and the screen rendered
     * empty name/username/<university>/<major>/<bio>/<avatar> fields for every
     * profile view. Any authenticated caller may read a profile (the user_service
     * controller documents this); it's the list_users enumeration that's
     * admin-gated.
     *
     * The gateway forwards the bearer token on to user_service so the caller's
     * identity is verified server-side (no `X-Caller-Email` shenanigans).
     *
     * Returns null on any failure (404 user deleted, token revoked, network
     * blip) — the component degrades to showing only the email + social counts
     * rather than crashing the screen.
     */
    suspend fun getPublicProfile(email: String): UserProfile? {
        return try {
            val t = token() ?: return null
            val resp = httpClient.get("$BASE_URL/api/user/${email.encodeURLPathPart()}") {
                bearerAuth(t)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            UserProfile(
                email = body["email"]?.contentOrNull() ?: email,
                name = body["name"]?.contentOrNull() ?: "",
                username = body["username"]?.contentOrNull(),
                phone = body["phone"]?.contentOrNull(),
                university = body["university"]?.contentOrNull(),
                major = body["major"]?.contentOrNull(),
                bio = body["bio"]?.contentOrNull(),
                avatarUrl = body["avatar_url"]?.contentOrNull()
            )
        } catch (_: Exception) { null }
    }

    // ── Friend requests ───────────────────────────────────────────────────────

    suspend fun sendFriendRequest(targetEmail: String): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.post("$BASE_URL/api/social/friend-request") {
            bearerAuth(t)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("target", targetEmail) }.toString())
        }
    }

    suspend fun respondFriendRequest(fromEmail: String, accept: Boolean): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.put("$BASE_URL/api/social/friend-request/${fromEmail.encodeURLPathPart()}") {
            bearerAuth(t)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("action", if (accept) "accept" else "reject") }.toString())
        }
    }

    suspend fun unfriend(targetEmail: String): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.delete("$BASE_URL/api/social/friend/${targetEmail.encodeURLPathPart()}") {
            bearerAuth(t)
        }
    }

    suspend fun getFriends(email: String, limit: Int = 50, offset: Int = 0): List<String> {
        return try {
            val t = token() ?: return emptyList()
            val resp = httpClient.get("$BASE_URL/api/social/friends/${email.encodeURLPathPart()}") {
                bearerAuth(t)
                parameter("limit", limit)
                parameter("offset", offset)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["friends"]?.jsonArray?.mapNotNull { it.contentOrNull() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getPendingRequests(): List<PendingRequest> {
        return try {
            val t = token() ?: return emptyList()
            val resp = httpClient.get("$BASE_URL/api/social/friend-requests/pending") {
                bearerAuth(t)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["requests"]?.jsonArray?.mapNotNull { req ->
                val obj = req.jsonObject
                val from = obj["from_email"]?.contentOrNull() ?: return@mapNotNull null
                val to = obj["to_email"]?.contentOrNull() ?: ""
                PendingRequest(fromEmail = from, toEmail = to)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // ── Block / Report ─────────────────────────────────────────────────────────

    suspend fun block(targetEmail: String): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.post("$BASE_URL/api/social/block") {
            bearerAuth(t)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("target", targetEmail) }.toString())
        }
    }

    suspend fun unblock(targetEmail: String): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.delete("$BASE_URL/api/social/block/${targetEmail.encodeURLPathPart()}") {
            bearerAuth(t)
        }
    }

    suspend fun report(targetEmail: String, reason: String, detail: String? = null): Result<Unit> = runCatching {
        val t = requireToken()
        httpClient.post("$BASE_URL/api/social/report") {
            bearerAuth(t)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("target", targetEmail)
                put("reason", reason)
                detail?.let { put("detail", it) }
            }.toString())
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

/**
 * Phase 5.35 — public profile metadata returned by user_service through the
 * `/api/user/:email` gateway proxy. Mirrors the `profile_json/1` shape
 * produced by `UserServiceWeb.UserController`. All identity fields except
 * email are nullable because the underlying DB columns are nullable and a
 * freshly-registered user has only email/name/username/university populated
 * (major/bio/avatar_url ship as null until first PUT).
 */
data class UserProfile(
    val email: String,
    val name: String,
    val username: String?,
    val phone: String?,
    val university: String?,
    val major: String?,
    val bio: String?,
    val avatarUrl: String?
)

data class PendingRequest(
    val fromEmail: String,
    val toEmail: String
)

// ─── JSON null-safe helpers ──────────────────────────────────────────────────
// .jsonPrimitive throws IllegalStateException on JsonNull before the elvis
// operator sees null. These helpers guard against that NPE pattern.

private fun JsonElement?.contentOrNull(): String? =
    (this as? JsonPrimitive)?.let { if (it == JsonNull) null else it.content }

private fun JsonElement?.intOrNull(): Int? =
    (this as? JsonPrimitive)?.let { if (it == JsonNull) null else it.intOrNull }

private fun JsonElement?.booleanOrNull(): Boolean? =
    (this as? JsonPrimitive)?.let { if (it == JsonNull) null else it.booleanOrNull }