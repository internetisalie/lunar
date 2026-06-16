package net.internetisalie.lunar.rocks.publish

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores the luarocks.org upload API key in the platform credential store (ROCKS-08-02).
 *
 * The key is a secret, so it lives in [PasswordSafe] — never in persisted settings XML. The
 * credential is keyed by a stable [CredentialAttributes] derived from [SUBSYSTEM]/[KEY] via
 * [generateServiceName]; those constants must stay fixed across releases so an upgrade does not
 * orphan a previously-stored key.
 */
object LuaRocksApiKeyStore {
    /** Credential-store subsystem namespace. Stable — do not change. */
    const val SUBSYSTEM: String = "Lunar LuaRocks"

    /** Credential-store key within [SUBSYSTEM]. Stable — do not change. */
    const val KEY: String = "luarocks.org API key"

    /** The stable service name used to key the credential. */
    val serviceName: String = generateServiceName(SUBSYSTEM, KEY)

    private val attributes: CredentialAttributes
        get() = CredentialAttributes(serviceName)

    /** Returns the stored API key, or `null` if none is set (or it is blank). */
    fun getApiKey(): String? = PasswordSafe.instance.getPassword(attributes)?.takeIf { it.isNotBlank() }

    /** Stores [apiKey]; a `null`/blank value clears the stored key. */
    fun setApiKey(apiKey: String?) {
        PasswordSafe.instance.setPassword(attributes, apiKey?.takeIf { it.isNotBlank() })
    }
}
