package net.internetisalie.lunar.analysis.redis

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.platform.target.Target
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Derives the sandbox API allowlist for a Redis/Valkey target from its bundled stub roots
 * (design §3.7, RISK-R07 single source of truth).
 *
 * The allowlist encodes which root-level names are accessible inside a Redis script sandbox
 * and, for `os`, which specific members are permitted (only those declared in the target's
 * `os.lua` stub). The stub files are the ground truth — adding or removing a function from
 * `os.lua` automatically adjusts what the sandbox inspection permits.
 *
 * Representation:
 * - [Allowlist.stubRoots]: the set of root names that have a corresponding stub file (e.g.
 *   `os`, `cjson`, `redis`). Any root name whose name is also a Lua-stdlib blocked name
 *   (e.g. `io`) is treated as blocked regardless.
 * - [Allowlist.osMembers]: the specific `os.<member>` names declared in `os.lua` (e.g.
 *   `time`, `clock`). Used to distinguish `os.time` (allowed) from `os.getenv` (blocked).
 *
 * Computed per target path segment (cached in a companion map) from the classloader's
 * bundled resource tree — no VFS/VirtualFile access so no VFS side-effects during
 * inspection execution. The [Project] parameter is accepted for API symmetry with the
 * design (§3.7 describes `RuntimeLibraryProvider(project)`), but the actual lookup
 * goes through the classloader directly to avoid VFS refresh events in tests.
 */
object RedisSandboxAllowlist {

    /** Root names from Lua stdlib that the Redis sandbox blocks entirely (no stub). */
    private val BLOCKED_ROOTS = setOf("io", "require", "dofile", "loadfile", "print", "load")

    private val KNOWN_STUB_NAMES = setOf("bit", "cjson", "cmsgpack", "os", "redis", "struct")

    private val cacheLock = Any()
    private val cache = mutableMapOf<String, Allowlist>()

    /**
     * Returns the allowlist for [target].
     *
     * [project] is accepted per the design API contract (§3.7) but is not retained.
     * The actual resource scan uses the classloader to avoid VFS side-effects.
     */
    @Suppress("UNUSED_PARAMETER")
    fun forTarget(project: Project, target: Target): Allowlist {
        val key = target.version.pathSegment
        synchronized(cacheLock) {
            return cache.getOrPut(key) { computeAllowlist(target) }
        }
    }

    private fun computeAllowlist(target: Target): Allowlist {
        val basePath = target.getLibraryRootPath()
        val stubRoots = KNOWN_STUB_NAMES.filter { name ->
            javaClass.classLoader.getResource("$basePath/$name.lua") != null
        }.toSet()
        val osMembers = parseOsMembers(basePath)
        return Allowlist(stubRoots, osMembers)
    }

    /**
     * Parses the `function os.<name>(` declarations from the bundled `os.lua` resource
     * to determine which members are available in the sandbox allowlist.
     *
     * Reads from the classloader resource stream (no VirtualFile / VFS events).
     */
    private fun parseOsMembers(basePath: String): Set<String> {
        val url = javaClass.classLoader.getResource("$basePath/os.lua") ?: return emptySet()
        val content = runCatching {
            url.openStream().use { stream ->
                InputStreamReader(stream, StandardCharsets.UTF_8).readText()
            }
        }.getOrNull() ?: return emptySet()
        val members = mutableSetOf<String>()
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("function os.")) {
                val name = trimmed.removePrefix("function os.").substringBefore('(').trim()
                if (name.isNotEmpty()) members += name
            }
        }
        return members
    }

    /**
     * The sandbox allowlist for one target.
     *
     * @param stubRoots All root names with bundled stubs (e.g. `os`, `cjson`, `redis`).
     * @param osMembers The specific `os.*` member names declared in the target's `os.lua`.
     */
    data class Allowlist(
        val stubRoots: Set<String>,
        val osMembers: Set<String>,
    ) {
        /**
         * Returns `true` when accessing [rootName] at the top level is blocked in the sandbox.
         * A root is blocked when it is a known Lua stdlib name AND has no stub file.
         */
        fun isBlockedRoot(rootName: String): Boolean =
            rootName in BLOCKED_ROOTS && rootName !in stubRoots

        /**
         * Returns `true` when `os.<memberName>` is allowed in the sandbox.
         * [isBlockedRoot] for `os` itself returns `false` (os has a stub); member-level
         * allowance is gated here instead.
         */
        fun isAllowedOsMember(memberName: String): Boolean = memberName in osMembers
    }
}
