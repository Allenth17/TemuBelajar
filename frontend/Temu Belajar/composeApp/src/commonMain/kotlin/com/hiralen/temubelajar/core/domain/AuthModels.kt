package com.hiralen.temubelajar.core.domain

import kotlinx.serialization.Serializable

// 3.25 — single source of truth for AccountLogin. The duplicate at
// `auth/domain/AccountLogin.kt` is dead code and will be removed there.
// Constraint field name is `emailOrUsername` even though the wire field
// is `email_or_username`; the repository maps it explicitly.
@Serializable data class AccountLogin(val email: String, val password: String)

@Serializable data class AccountRegister(
    val email: String,
    val password: String,
    val username: String,
    val name: String,
    val phone: String,
    val university: String
)

@Serializable data class LoginResponse(val token: String)

// 3.15 — backend `/api/me` returns email, name, username, phone,
// university, verified, last_login (auth_service). The wider
// `major`, `bio`, `avatar_url` fields live in user_service's User
// schema and are surfaced here too so callers that fetch via a
// profile-aware endpoint don't lose them. All nullable: the auth
// `/api/me` response omits major/bio/avatar_url today, and Ktor's
// `ignoreUnknownKeys=true` + `coerceInputValues=true` keep parsing
// safe when fields are absent.
@Serializable data class MeResponse(
    val email: String,
    val name: String,
    val university: String? = null,
    val username: String? = null,
    val phone: String? = null,
    val major: String? = null,
    val bio: String? = null
)
