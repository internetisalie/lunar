package net.internetisalie.lunar.toolchain.provision.feed

import net.internetisalie.lunar.toolchain.provision.LuaArch
import net.internetisalie.lunar.toolchain.provision.LuaHostPlatform
import net.internetisalie.lunar.toolchain.provision.LuaOs
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException

/**
 * Resolves a user version spec to a concrete [LuaFeedVersion] per design §3.2.
 *
 * Aliases are applied at most once per step; the instant the current value names a shipped,
 * on-platform-provisionable version entry, resolution stops (it is never re-fed through the
 * alias map). Falls through to a bare-line prefix match (§3.2 step 4) and finally an error.
 */
internal class LuaFeedVersionResolver(
    private val kindId: String,
    private val kind: LuaFeedKind,
    private val platform: LuaHostPlatform,
) {
    private val bareLine = Regex("""^\d+(\.\d+)?$""")

    fun resolve(rawSpec: String): LuaFeedVersion {
        val originalSpec = rawSpec.trim().ifEmpty { "latest" }.let { if (it == "^") "latest" else it }
        aliasResolve(originalSpec)?.let { return it }
        // Steps 4a/4b: a bare-line spec prefix-matches provisionable versions. When a bare-line
        // originalSpec alias-resolved to an on-platform-unprovisionable patch (Windows 5.4.8),
        // aliasResolve returned null and this re-prefixes originalSpec — yielding the newest
        // platform-provisionable patch (§3.2 step 4).
        prefixMatch(originalSpec)?.let { return it }
        throw unknown(originalSpec)
    }

    /**
     * Steps 2–3: exact-first termination + single alias application, looping until stable.
     * A shipped entry stops alias-chasing whether or not it is provisionable here; a
     * provisionable one returns, a gated one errors, and a visible-but-unprovisionable one
     * (e.g. Windows lacking a 5.4.8 asset) falls through to the prefix match (step 4).
     */
    private fun aliasResolve(startSpec: String): LuaFeedVersion? {
        var spec = startSpec
        val maxApplications = kind.aliases.size + 1
        var applications = 0
        while (true) {
            val entry = findEntry(spec)
            if (entry != null) {
                if (entry.gatedOn != null) {
                    throw LuaProvisionException(
                        "Version '$spec' of '$kindId' is gated on ${entry.gatedOn} and not yet available.",
                    )
                }
                return if (isProvisionable(entry)) entry else null
            }
            val next = kind.aliases[spec] ?: return null
            spec = next
            if (++applications > maxApplications) {
                throw LuaProvisionException(
                    "Corrupt toolchain feed: alias cycle resolving '$kindId' version '$startSpec'",
                )
            }
        }
    }

    /** Step 4a: a bare-line spec prefix-matches provisionable versions; take the max. */
    private fun prefixMatch(spec: String): LuaFeedVersion? {
        if (!bareLine.matches(spec)) return null
        return kind.versions
            .filter { isProvisionable(it) && it.version.startsWith("$spec.") }
            .maxWithOrNull(compareBy(LuaFeedVersionComparator) { it.version })
    }

    private fun findEntry(version: String): LuaFeedVersion? =
        kind.versions.firstOrNull { it.version == version }

    private fun isVisible(entry: LuaFeedVersion): Boolean = entry.gatedOn == null

    private fun isProvisionable(entry: LuaFeedVersion): Boolean {
        if (!isVisible(entry)) return false
        val hasMatchingAsset = entry.assets.any { assetMatches(it) }
        val sourceBuildable = entry.source != null && platform.os != LuaOs.WINDOWS
        return hasMatchingAsset || sourceBuildable || entry.rock != null
    }

    private fun assetMatches(asset: LuaFeedAsset): Boolean =
        asset.os.equals(osToken(), ignoreCase = true) && asset.arch.equals(archToken(), ignoreCase = true)

    private fun osToken(): String =
        when (platform.os) {
            LuaOs.LINUX -> "linux"
            LuaOs.MACOS -> "macos"
            LuaOs.WINDOWS -> "windows"
        }

    private fun archToken(): String =
        when (platform.arch) {
            LuaArch.X86_64 -> "x86_64"
            LuaArch.AARCH64 -> "aarch64"
        }

    private fun unknown(spec: String): LuaProvisionException {
        val known = kind.versions
            .filter { isProvisionable(it) }
            .map { it.version }
            .sortedWith(LuaFeedVersionComparator.reversed())
            .joinToString(", ")
        return LuaProvisionException("Unknown $kindId version '$spec'. Known: $known")
    }
}
