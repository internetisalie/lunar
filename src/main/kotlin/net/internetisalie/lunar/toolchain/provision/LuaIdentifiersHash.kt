package net.internetisalie.lunar.toolchain.provision

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets

/**
 * The nine inputs to the identifiers hash (design §3.3). Bundled as a single value object
 * so [LuaIdentifiersHash.compute] stays a one-argument function (engineering-contract §2.1).
 *
 * @param artifact the artifact-identity line's value: a download strategy passes the
 *   `sha256` of the feed source/asset; `luarocks-install` passes `rock={name}@{version}`;
 *   the gated git strategy passes `git={ref}`. Callers (Phase 4/5/6) derive it.
 * @param compatDefines space-joined compat defines from §3.5; empty for non-source kinds.
 */
data class LuaIdentifiersHashInput(
    val kindId: String,
    val resolvedVersion: String,
    val strategyId: String,
    val os: LuaOs,
    val arch: LuaArch,
    val canonicalRootDir: String,
    val artifact: String,
    val compatDefines: String,
)

/**
 * Computes the idempotency identifiers hash (design §3.3): SHA-256 over the UTF-8 bytes of
 * nine fixed-order lines joined with `"\n"`, rendered as 64 lowercase hex characters.
 *
 * The line order and the fixed `readline=false` line are part of the hash contract: any
 * future opt-in (e.g. readline) invalidates prior manifests by changing the digest.
 */
object LuaIdentifiersHash {
    fun compute(input: LuaIdentifiersHashInput): String {
        val lines = listOf(
            "kind=${input.kindId}",
            "version=${input.resolvedVersion}",
            "strategy=${input.strategyId}",
            "os=${input.os}",
            "arch=${input.arch}",
            "root=${input.canonicalRootDir}",
            "artifact=${input.artifact}",
            "readline=false",
            "compat=${input.compatDefines}",
        )
        return Hashing.sha256().hashString(lines.joinToString("\n"), StandardCharsets.UTF_8).toString()
    }
}
