package net.internetisalie.lunar.redis.functions

import java.security.MessageDigest

/** Verdict for a local-vs-server library source comparison (design §2.8). */
enum class DriftStatus {
    /** SHA-1 hashes of normalized server and local sources match. */
    IN_SYNC,

    /** SHA-1 hashes differ — the local file and the loaded library diverge. */
    DRIFTED,

    /** Server did not report `library_code` (no `WITHCODE` reply) — drift cannot be assessed. */
    UNKNOWN,
}

/**
 * Compares a server `library_code` to a local file body and returns a [DriftStatus] (design §2.8, §3.10).
 *
 * Normalization strips `\r\n` → `\n` and trims trailing whitespace before hashing, so
 * platform line-ending differences and trailing-newline variations do not produce spurious drift.
 * Uses SHA-1 via [MessageDigest] (the same idiom as [net.internetisalie.lunar.redis.run.LuaRedisScriptExecutor.sha1Hex]).
 * Pure — no I/O, no retained state.
 */
object LuaRedisFunctionDrift {

    /**
     * Returns [DriftStatus.UNKNOWN] when [serverCode] is null (server omitted `library_code`);
     * [DriftStatus.IN_SYNC] when the normalized SHA-1 hashes match; [DriftStatus.DRIFTED] otherwise.
     */
    fun compare(serverCode: String?, localBody: String): DriftStatus {
        serverCode ?: return DriftStatus.UNKNOWN
        return if (sha1(norm(serverCode)) == sha1(norm(localBody))) DriftStatus.IN_SYNC else DriftStatus.DRIFTED
    }

    private fun norm(s: String): String = s.replace("\r\n", "\n").trimEnd()

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
