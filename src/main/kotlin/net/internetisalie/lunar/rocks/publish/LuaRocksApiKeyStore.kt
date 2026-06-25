package net.internetisalie.lunar.rocks.publish

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores the LuaRocks upload API key in the platform credential store (ROCKS-08-02, ROCKS-06-07).
 *
 * Keys are secret, so they live in [PasswordSafe] — never in persisted settings XML.
 * Each credential is keyed by the resolved server URL so that a custom registry gets its own
 * API key while the existing luarocks.org key is preserved via [LEGACY_KEY] back-compat.
 *
 * Key derivation:
 * - `server` non-null/non-blank → `"luarocks API key:<server>"` (per-server key)
 * - `server` null/blank         → [LEGACY_KEY] `"luarocks.org API key"` (back-compat, TC 8)
 *
 * [SUBSYSTEM] and [LEGACY_KEY] are stable across releases; do not rename them.
 */
object LuaRocksApiKeyStore {
    /** Credential-store subsystem namespace. Stable — do not change. */
    const val SUBSYSTEM: String = "Lunar LuaRocks"

    /**
     * Legacy credential key used before ROCKS-06. Retained as the fallback for a null/blank
     * server so existing stored API keys continue to work without re-prompting (TC 8).
     */
    const val LEGACY_KEY: String = "luarocks.org API key"

    /**
     * The stable service name for the legacy key (null/blank server). Preserved for callers
     * that previously referenced [serviceName] directly.
     */
    val serviceName: String = generateServiceName(SUBSYSTEM, LEGACY_KEY)

    /** Returns the credential-store key string for [server]. */
    fun keyFor(server: String?): String =
        if (server.isNullOrBlank()) LEGACY_KEY else "luarocks API key:$server"

    private fun attributesFor(server: String?): CredentialAttributes =
        CredentialAttributes(generateServiceName(SUBSYSTEM, keyFor(server)))

    /** Returns the stored API key for [server], or `null` if none is set (or it is blank). */
    fun getApiKey(server: String?): String? =
        PasswordSafe.instance.getPassword(attributesFor(server))?.takeIf { it.isNotBlank() }

    /** Stores [apiKey] for [server]; a `null`/blank value clears the stored key. */
    fun setApiKey(server: String?, apiKey: String?) {
        PasswordSafe.instance.setPassword(attributesFor(server), apiKey?.takeIf { it.isNotBlank() })
    }
}
