package net.internetisalie.lunar.redis.connection

import com.intellij.openapi.progress.ProcessCanceledException
import net.internetisalie.lunar.redis.resp.RespClient
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.redis.resp.RespException
import net.internetisalie.lunar.redis.resp.RespValue

/** Result of a Test Connection probe (design §4.3), marshalled back to the EDT for display. */
sealed interface TestOutcome {
    /**
     * A successful probe. [flavor] is the detected server flavor (null when no `INFO server` body was
     * available), consumed by the REDIS-03 §7.3 mismatch warning at the connect site.
     */
    data class Success(val summary: String, val flavor: ServerFlavor? = null) : TestOutcome
    data class Failure(val message: String) : TestOutcome
}

/**
 * Server flavor/version parsed from an `INFO server` bulk reply (design §4.3).
 *
 * Flavor derivation is centralized in [LuaRedisServerFlavor.detect] (REDIS-03 §7.3 — single source
 * of truth for the `valkey_version` heuristic); [version] preserves the REDIS-01 display contract:
 * `redis_version` with `valkey_version` as fallback, else `"unknown"`. Pure parsing — no I/O.
 */
data class RespServerInfo(val flavor: String, val version: String) {
    fun summary(): String = "Connected to $flavor $version"

    companion object {
        fun parse(body: String): RespServerInfo {
            val fields = body.split("\r\n", "\n")
                .filter { it.contains(':') }
                .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
            val flavor = when (LuaRedisServerFlavor.detect(body).flavor) {
                ServerFlavor.VALKEY -> "Valkey"
                ServerFlavor.REDIS -> "Redis"
            }
            val version = fields["redis_version"] ?: fields["valkey_version"] ?: "unknown"
            return RespServerInfo(flavor, version)
        }
    }
}

/**
 * Opens a [RespClient] to [endpoint], runs `INFO server`, and reports the flavor/version (design §4.3).
 *
 * Suspending — the caller runs it off the EDT under a background progress indicator. Every failure is
 * converted to a [TestOutcome.Failure] with a user-facing message; cancellation propagates so the
 * background task aborts cleanly (engineering-contract §2).
 */
suspend fun probe(endpoint: RespEndpoint): TestOutcome {
    var client: RespClient? = null
    return try {
        val opened = RespClient.open(endpoint)
        client = opened
        val reply = opened.command("INFO", "server")
        successFrom(reply, opened)
    } catch (cancelled: ProcessCanceledException) {
        throw cancelled
    } catch (timeout: RespException.Timeout) {
        TestOutcome.Failure(timeout.message ?: "Connection timed out")
    } catch (failure: RespException) {
        TestOutcome.Failure(failure.message ?: "Connection failed")
    } finally {
        client?.dispose()
    }
}

private fun successFrom(reply: RespValue, client: RespClient): TestOutcome = when (reply) {
    is RespValue.Error -> TestOutcome.Failure("${reply.klass} ${reply.message}".trim())
    is RespValue.Bulk -> {
        val body = reply.asString().orEmpty()
        val info = RespServerInfo.parse(body)
        TestOutcome.Success("${info.summary()} (${client.protocol})", LuaRedisServerFlavor.detect(body).flavor)
    }
    else -> TestOutcome.Success("Connected (${client.protocol})")
}
