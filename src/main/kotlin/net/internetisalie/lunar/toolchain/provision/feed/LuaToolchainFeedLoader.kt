package net.internetisalie.lunar.toolchain.provision.feed

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import net.internetisalie.lunar.toolchain.provision.LuaHostPlatform
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Loads and caches the bundled version feed and resolves version specs against it.
 * See design §2.5, §3.2, §4.1. Pure JVM code — safe on any thread; no PSI/VFS/EDT contact.
 */
object LuaToolchainFeedLoader {
    const val RESOURCE = "/toolchain/lunar-toolchain-feed.json"

    @Volatile
    private var cached: LuaToolchainFeed? = null

    /** Parses the bundled resource once and caches it. Corrupt/missing resource → [LuaProvisionException]. */
    fun load(): LuaToolchainFeed = cached ?: synchronized(this) { cached ?: parse().also { cached = it } }

    /** Resolves [spec] for [kindId] against [feed] on [platform] per design §3.2. */
    fun resolveVersion(
        feed: LuaToolchainFeed,
        kindId: String,
        spec: String,
        platform: LuaHostPlatform,
    ): LuaFeedVersion {
        val kind = feed.kinds[kindId]
            ?: throw LuaProvisionException("Unknown toolchain kind '$kindId' in feed.")
        return LuaFeedVersionResolver(kindId, kind, platform).resolve(spec)
    }

    private fun parse(): LuaToolchainFeed {
        val stream = LuaToolchainFeedLoader::class.java.getResourceAsStream(RESOURCE)
            ?: throw LuaProvisionException("Corrupt toolchain feed: bundled resource '$RESOURCE' is missing.")
        return stream.use {
            val root = runCatching {
                JsonParser.parseReader(InputStreamReader(it, StandardCharsets.UTF_8)).asJsonObject
            }.getOrElse { failure ->
                throw LuaProvisionException("Corrupt toolchain feed: ${describe(failure)}", failure)
            }
            LuaFeedJsonParser.parseFeed(root)
        }
    }

    private fun describe(failure: Throwable): String =
        when (failure) {
            is JsonSyntaxException -> "invalid JSON syntax."
            else -> "top-level value is not a JSON object."
        }
}
