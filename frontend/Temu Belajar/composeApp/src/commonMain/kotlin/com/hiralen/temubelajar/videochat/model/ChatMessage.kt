package com.hiralen.temubelajar.videochat.model

import kotlinx.serialization.Serializable

/**
 * A single chat message in the ephemeral video-chat conversation.
 * Lives only in-memory (never persisted) — resets on Next/disconnect.
 */
@Serializable
data class ChatMessage(
    val text: String,
    val emoji: String? = null,          // if this is an emoji-only message
    val fromSelf: Boolean,
    val timestampMs: Long = 0L,
    val type: Type = Type.TEXT
) {
    enum class Type { TEXT, EMOJI, TYPING }

    /** Display text: emoji takes priority over text */
    val displayText: String get() = emoji ?: text
}
