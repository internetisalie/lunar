package net.internetisalie.lunar.analysis.redis

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Loads, parses (Gson), and caches the bundled per-version Redis command spec for
 * a [Target] (design §2.2, §3.2, §4.1). The single reusable command-surface seam
 * consumed by REDIS-04 completion/inspection/doc and by REDIS-05.
 *
 * Light application service: no PSI/VFS access, so it is callable from read
 * actions. Parsing happens lazily inside a `synchronized` memo keyed on the
 * resolved resource path (Redis versions sharing a file share one parsed
 * instance). Defensive throughout — any missing/unparseable resource yields
 * [RedisCommandSpec.EMPTY]; it never throws.
 *
 * @see RedisCommandSpec
 * @see RedisCommandInfo
 */
@Service(Service.Level.APP)
class RedisCommandSpecService {

    private val cacheLock = Any()
    private val cache = mutableMapOf<String, RedisCommandSpec>()

    /**
     * Returns the bundled spec for [target], or [RedisCommandSpec.EMPTY] when no
     * spec is bundled (non-Redis/Valkey target, or an absent/unparseable resource).
     */
    fun specFor(target: Target): RedisCommandSpec {
        val resourcePath = resourcePathFor(target) ?: return RedisCommandSpec.EMPTY
        synchronized(cacheLock) {
            return cache.getOrPut(resourcePath) { loadSpec(resourcePath) }
        }
    }

    /** Maps a [Target] to its bundled spec resource path, or `null` (design §3.2). */
    private fun resourcePathFor(target: Target): String? {
        val platform = target.platform
        if (platform != LuaPlatform.REDIS && platform.name != "VALKEY") return null
        val segment = when (target.version.pathSegment) {
            "redis-5" -> "redis-5"
            "redis-6" -> "redis-6"
            "redis-7" -> "redis-7"
            "valkey-7.2", "valkey-8" -> "valkey-8"
            else -> return null
        }
        return "commandspec/$segment.json"
    }

    /** Reads and parses one resource; [RedisCommandSpec.EMPTY] on any failure. */
    private fun loadSpec(resourcePath: String): RedisCommandSpec {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: return RedisCommandSpec.EMPTY
        return stream.use { input ->
            runCatching {
                val root = JsonParser.parseReader(
                    InputStreamReader(input, StandardCharsets.UTF_8),
                ).asJsonObject
                parseSpec(root)
            }.getOrElse { failure ->
                LOG.warn("Failed to parse Redis command spec '$resourcePath'", failure)
                RedisCommandSpec.EMPTY
            }
        }
    }

    /** Parses the top-level command map; malformed entries are skipped + logged. */
    private fun parseSpec(root: JsonObject): RedisCommandSpec {
        val commands = mutableMapOf<String, RedisCommandInfo>()
        for ((name, element) in root.entrySet()) {
            val info = parseEntry(name, element) ?: continue
            commands[info.name] = info
        }
        return RedisCommandSpec(commands.toMap())
    }

    /** Parses one command entry defensively (design §4.1); `null` if malformed. */
    private fun parseEntry(name: String, element: JsonElement): RedisCommandInfo? {
        if (!element.isJsonObject) {
            LOG.warn("Skipping non-object Redis command entry '$name'")
            return null
        }
        val body = element.asJsonObject
        return RedisCommandInfo(
            name = name.uppercase(),
            arity = body.get("arity")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
            since = body.get("since")?.takeIf { it.isJsonPrimitive }?.asString ?: "0",
            summary = body.get("summary")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
            flags = parseFlags(body),
        )
    }

    /** Reads the `flags` array as a lower-cased set; empty when absent/malformed. */
    private fun parseFlags(body: JsonObject): Set<String> {
        val array = body.get("flags")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptySet()
        return array.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.lowercase() }.toSet()
    }

    companion object {
        private val LOG = Logger.getInstance(RedisCommandSpecService::class.java)

        fun getInstance(): RedisCommandSpecService =
            ApplicationManager.getApplication().getService(RedisCommandSpecService::class.java)
    }
}
