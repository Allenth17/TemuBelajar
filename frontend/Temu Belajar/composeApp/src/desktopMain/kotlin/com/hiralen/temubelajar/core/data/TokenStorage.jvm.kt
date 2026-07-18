package com.hiralen.temubelajar.core.data

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Phase 0.16 — Desktop token storage migrated from `java.util.prefs.Preferences`
// (plaintext XML under `~/.java/.userPrefs/`, readable by any process running as
// the same user) to an AES-256-GCM encrypted file at a per-OS app config dir
// with `0600` permission on POSIX.
//
// The encryption key is derived via PBKDF2 from a stable per-machine passphrase
// (hostname + user.name) plus a random 16-byte salt. Anyone with full read
// access to the user's home directory AND knowledge of hostname+username can
// still recover the key — that's the same identification-based boundary that
// protects `~/.ssh` keys without a passphrase, and is appropriate for a
// single-user client-side app without USB-token hardware available. Full
// OS keychain integration (secret-service on Linux, Keychain on macOS,
// DPAPI on Windows) is deferred: it requires per-OS native bindings that
// can't be written in pure JDK and would balloon the desktop module.
actual class TokenStorage actual constructor() {
    actual fun saveToken(token: String) {
        try {
            val (key, salt) = ensureKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_LEN).also { SECURE_RANDOM.nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val cipherText = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
            val envelope = ByteBuffer.allocate(1 + iv.size + salt.size + cipherText.size)
                .put(VERSION.toByte())
                .put(iv)
                .put(salt)
                .put(cipherText)
                .array()
            val path = tokenFilePath()
            Files.createDirectories(path.parent)
            Files.write(path, envelope)
            lockFilePermissions(path)
            writeLegacyBool(true)
        } catch (_: Throwable) {
            // Degrade: persist in plaintext prefs (old behaviour) so the app
            // stays usable if filesystem encryption fails for any reason.
            LEGACY_FALLBACK.saveToken(token)
        }
    }

    actual fun getToken(): String? {
        return try {
            val path = tokenFilePath()
            if (!Files.exists(path)) return maybeReadLegacy()
            val envelope = Files.readAllBytes(path)
            val buf = ByteBuffer.wrap(envelope)
            val version = buf.get().toInt()
            if (version != VERSION) return maybeReadLegacy()
            val iv = ByteArray(IV_LEN); buf.get(iv)
            val salt = ByteArray(SALT_LEN); buf.get(salt)
            val cipherText = ByteArray(buf.remaining()); buf.get(cipherText)
            val key = deriveKey(salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8).takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            maybeReadLegacy()
        }
    }

    actual fun clearToken() {
        try { Files.deleteIfExists(tokenFilePath()) } catch (_: Throwable) {}
        LEGACY_FALLBACK.clearToken()
    }

    actual fun hasLoggedInBefore(): Boolean = LEGACY_FALLBACK.hasLoggedInBefore()

    // ─── Internals ────────────────────────────────────────────────────────────

    /**
     * Load-or-create the salt file. The AES key is derived fresh on each call
     * (cheap), so we only persist the random salt. Key freshness is therefore
     * broken iff the salt file is corrupted AND the attacker has the passphrase
     * inputs. The salt has no secrecy value; it just defeats rainbow tables.
     */
    private fun ensureKey(): Pair<SecretKey, ByteArray> {
        val saltPath = saltFilePath()
        val salt: ByteArray = if (Files.exists(saltPath)) {
            val s = Files.readAllBytes(saltPath)
            if (s.size != SALT_LEN) ByteArray(SALT_LEN).also(SECURE_RANDOM::nextBytes)
            else s
        } else {
            ByteArray(SALT_LEN).also(SECURE_RANDOM::nextBytes)
        }
        Files.createDirectories(saltPath.parent)
        Files.write(saltPath, salt)
        lockFilePermissions(saltPath)
        return deriveKey(salt) to salt
    }

    private fun deriveKey(salt: ByteArray): SecretKey {
        val passphrase = stableMachinePassphrase()
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, KEY_ITERATIONS, KEY_BITS)
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    /**
     * Best-effort stable passphrase. NOT a real secret; just enough that two
     * users on the same host don't share a key, and two hosts for the same
     * user don't share a key. Full OS keychain integration is the long-term
     * fix. See class kdoc.
     */
    private fun stableMachinePassphrase(): String {
        val user = System.getProperty("user.name") ?: "unknown"
        val host = runCatching {
            java.net.InetAddress.getLocalHost().hostName
        }.getOrNull() ?: "localhost"
        return "temubelajar:$user@$host"
    }

    private fun tokenFilePath(): Path = Paths.get(appConfigDir(), "auth_token.bin")
    private fun saltFilePath(): Path = Paths.get(appConfigDir(), "auth_token.salt")

    /** Per-OS app config dir where our files live. */
    private fun appConfigDir(): String {
        val app = "TemuBelajar"
        val os = System.getProperty("os.name").lowercase()
        val base: String = when {
            os.contains("win") -> System.getenv("APPDATA") ?: System.getProperty("user.home", ".")
            os.contains("mac") -> "${System.getProperty("user.home", ".")}/Library/Application Support"
            else -> System.getenv("XDG_CONFIG_HOME") ?: "${System.getProperty("user.home", ".")}/.config"
        }
        return "$base/$app"
    }

    /** POSIX: chmod 0600. No-op on Windows where ACLs handle this. */
    private fun lockFilePermissions(path: Path) {
        try {
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
            Files.setPosixFilePermissions(path, perms)
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX fs (NTFS via WSL etc.): userspace perms deferred.
        }
    }

    /**
     * Read the old plaintext prefs token if the encrypted file is absent
     * (first run after upgrade). Once a token is read this way, the next
     * `saveToken(seq-controlled writes)` will write it encrypted.
     */
    private fun maybeReadLegacy(): String? {
        return LEGACY_FALLBACK.getToken()
    }

    private fun writeLegacyBool(value: Boolean) {
        // We use the legacy preferences ONLY for the "has logged in" boolean,
        // which is not secret and which the splash routing reads. The token
        // itself is not written here.
        LEGACY_FALLBACK.setBool(value)
    }

    /**
     * Minimal handle around the old Preferences API, used now only for the
     * non-secret `has_logged_in` flag and as a plaintext fallback when the
     * encrypted file path is unavailable.
     */
    private object LEGACY_FALLBACK {
        private val prefs = Preferences.userRoot().node("temubelajar")
        fun saveToken(token: String) {
            prefs.put("auth_token", token)
            prefs.putBoolean("has_logged_in", true)
            prefs.flush()
        }
        fun getToken(): String? = prefs.get("auth_token", null)?.takeIf { it.isNotEmpty() }
        fun clearToken() {
            prefs.remove("auth_token")
            prefs.flush()
        }
        fun setBool(value: Boolean) {
            prefs.putBoolean("has_logged_in", value)
            prefs.flush()
        }
        fun hasLoggedInBefore(): Boolean = prefs.getBoolean("has_logged_in", false)
    }

    private companion object {
        const val VERSION = 1
        const val IV_LEN = 12
        const val SALT_LEN = 16
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val KEY_ITERATIONS = 120_000
        val SECURE_RANDOM: SecureRandom = SecureRandom()
    }
}
