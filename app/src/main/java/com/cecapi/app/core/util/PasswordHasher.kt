package com.cecapi.app.core.util

import java.security.MessageDigest

/**
 * Minimal SHA-256 hashing so `usuarios.contrasena_hash` never stores plaintext,
 * even for the CECAPI demo account. Good enough for a student prototype; a
 * production rollout should move to a salted scheme (e.g. bcrypt/argon2) before
 * handling real institutional accounts — noted in section 7 of the proposal
 * ("funciones futuras") as a hardening item.
 */
object PasswordHasher {
    fun hash(rawPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(rawPassword.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
