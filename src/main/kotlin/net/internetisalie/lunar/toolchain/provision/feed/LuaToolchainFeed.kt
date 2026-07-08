package net.internetisalie.lunar.toolchain.provision.feed

/**
 * Typed model of the bundled version feed (`/toolchain/lunar-toolchain-feed.json`).
 * See design §2.5, §4.1. All fields are populated by [LuaToolchainFeedLoader] with
 * explicit null checks — no Gson reflection defaults are relied upon, so a missing
 * pin surfaces as a corrupt-feed error rather than a silent default.
 */
data class LuaToolchainFeed(val feedVersion: Int, val kinds: Map<String, LuaFeedKind>)

data class LuaFeedKind(val aliases: Map<String, String>, val versions: List<LuaFeedVersion>)

data class LuaFeedVersion(
    val version: String,
    val gatedOn: String?,
    val source: LuaFeedSource?,
    val assets: List<LuaFeedAsset>,
    val rock: LuaFeedRock?,
)

data class LuaFeedSource(
    val urls: List<String>,
    val sha256: String,
    val size: Long,
    val rootPrefix: String,
)

data class LuaFeedAsset(
    val os: String,
    val arch: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val packaging: String,
    val rootPrefix: String?,
    val layout: String,
    val binaryPath: String,
)

data class LuaFeedRock(
    val rockName: String,
    val pinnedVersion: String?,
    val binName: String,
    val needsCToolchain: Boolean,
)
