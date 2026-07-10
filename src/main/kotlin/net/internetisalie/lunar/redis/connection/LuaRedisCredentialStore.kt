package net.internetisalie.lunar.redis.connection

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores a Redis/Valkey AUTH password in the platform credential store (design §2.9).
 *
 * Mirrors `rocks/publish/LuaRocksApiKeyStore` (the "ROCKS-06 pattern" the requirements name):
 * `CredentialAttributes` + `generateServiceName(SUBSYSTEM, key)`, keyed by the connection [id] so the
 * secret is never written to the persisted `lunar-redis.xml` (engineering-contract §4;
 * risks-and-gaps "Secret keying"). [SUBSYSTEM] is stable across releases — do not rename it.
 */
object LuaRedisCredentialStore {

    /** Credential-store subsystem namespace. Stable — do not change. */
    const val SUBSYSTEM: String = "Lunar Redis"

    /** Returns the credential-store key string for [connectionId]. */
    fun keyFor(connectionId: String): String = "redis connection:$connectionId"

    private fun attributesFor(connectionId: String): CredentialAttributes =
        CredentialAttributes(generateServiceName(SUBSYSTEM, keyFor(connectionId)))

    /** Returns the stored password for [connectionId], or `null` if none is set (or it is blank). */
    fun getPassword(connectionId: String): String? =
        PasswordSafe.instance.getPassword(attributesFor(connectionId))?.takeIf { it.isNotBlank() }

    /** Stores [password] for [connectionId]; a `null`/blank value clears the stored password. */
    fun setPassword(connectionId: String, password: String?) {
        PasswordSafe.instance.setPassword(attributesFor(connectionId), password?.takeIf { it.isNotBlank() })
    }
}
