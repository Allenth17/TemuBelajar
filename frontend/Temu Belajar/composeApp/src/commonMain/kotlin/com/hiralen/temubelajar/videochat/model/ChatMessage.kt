package com.hiralen.temubelajar.videochat.model

import kotlinx.serialization.Serializable

/**
 * A single chat message in the ephemeral video-chat conversation.
 * Lives only in-memory (never persisted) — resets on Next/disconnect.
 *
 * The [id] is a monotonically-increasing Long so consecutive identical
 * messages (same millis, same text, same sender — e.g. two rapid-fire "ok"
 * replies) still carry distinct LazyList keys. Phase 5.23 fixed the old key
 * strategy that hashed `timestampMs + text.take(5)` and thus collided for
 * fast-typed duplicates → `IllegalArgumentException: Key was already used`.
 *
 * The counter is mutated only on `Dispatchers.Main` (both WebSocket readers
 * and chat-action callers in [VideoChatComponent] are launched on Main),
 * which is the existing single-thread contract already used by `ref` in
 * `VideoChatComponent.kt`. We avoid `kotlinx.atomicfu` so no new module
 * wiring is required.
 */
@Serializable
data class ChatMessage(
    val text: String,
    val emoji: String? = null,          // if this is an emoji-only message
    val fromSelf: Boolean,
    val timestampMs: Long = 0L,
    val type: Type = Type.TEXT,
    val id: Long = ChatMessageIdGenerator.nextId()
) {
    enum class Type { TEXT, EMOJI, TYPING }

    /** Display text: emoji takes priority over text */
    val displayText: String get() = emoji ?: text

    /** Stable sender tag for keying/UI: "me" or "peer" — drives bubble alignment too. */
    val sender: String get() = if (fromSelf) "me" else "peer"

    /** Stable content tag for keying — emoji messages report the emoji, text the text. */
    val content: String get() = emoji ?: text
}

/**
 * Process-wide monotonic id source — avoids same-millisecond duplicate keys
 * for fast-typed consecutive identical messages (see Phase 5.23).
 *
 * Mutation contract: callers must run on `Dispatchers.Main`. Both WebSocket
 * reader coroutines and the public `sendMessage/sendEmoji`/etc. entry points
 * in [com.hiralen.temubelajar.videochat.component.VideoChatComponent] are
 * already launched on `Dispatchers.Main` — the same contract that protects
 * the `ref` Phoenix frame counter in that component.
 */
private object ChatMessageIdGenerator {
    private var counter: Long = 0L
    fun nextId(): Long {
        counter += 1L
        return counter
    }
}
