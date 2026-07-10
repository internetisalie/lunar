package net.internetisalie.lunar.toolchain.registry

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.discovery.isGlob
import net.internetisalie.lunar.toolchain.discovery.matchesGlob
import net.internetisalie.lunar.toolchain.model.Capability
import net.internetisalie.lunar.toolchain.model.LanguageLevelRule
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.ProbeSpec
import net.internetisalie.lunar.toolchain.model.RuntimeProbeSpec
import net.internetisalie.lunar.toolchain.model.SemanticVersion

object LuaToolKindRegistry {

    val BUILT_IN: List<LuaToolKind> = listOf(
        // 1. lua
        LuaToolKind(
            id = "lua",
            displayName = "Lua",
            binaryNames = listOf("lua", "lua5.*", "lua-5.*"),
            probe = ProbeSpec(
                args = listOf("-v"),
                versionRegex = Regex("""Lua\s+(\d+\.\d+(?:\.\d+)?)"""),
                runtime = RuntimeProbeSpec(
                    productToken = "Lua",
                    platform = LuaPlatform.STANDARD,
                    languageLevel = LanguageLevelRule.ByVersionPrefix(
                        prefixes = listOf(
                            "5.1" to LuaLanguageLevel.LUA51,
                            "5.2" to LuaLanguageLevel.LUA52,
                            "5.3" to LuaLanguageLevel.LUA53,
                            "5.4" to LuaLanguageLevel.LUA54,
                            "5.5" to LuaLanguageLevel.LUA55
                        ),
                        fallback = LuaLanguageLevel.LUA50
                    )
                )
            ),
            capabilities = setOf(Capability.RUNTIME)
        ),
        // 2. luajit
        LuaToolKind(
            id = "luajit",
            displayName = "LuaJIT",
            binaryNames = listOf("luajit", "luajit-2.*", "luajit2.*"),
            probe = ProbeSpec(
                args = listOf("-v"),
                versionRegex = Regex("""LuaJIT\s+(\d[\w.]*)"""),
                runtime = RuntimeProbeSpec(
                    productToken = "LuaJIT",
                    platform = LuaPlatform.LUAJIT,
                    languageLevel = LanguageLevelRule.Fixed(LuaLanguageLevel.LUA51)
                )
            ),
            capabilities = setOf(Capability.RUNTIME)
        ),
        // 3. tarantool
        LuaToolKind(
            id = "tarantool",
            displayName = "Tarantool",
            binaryNames = listOf("tarantool"),
            probe = ProbeSpec(
                args = listOf("-v"),
                versionRegex = Regex("""Tarantool\s+(\S+)"""),
                runtime = RuntimeProbeSpec(
                    productToken = "Tarantool",
                    platform = LuaPlatform.TARANTOOL,
                    languageLevel = LanguageLevelRule.Fixed(LuaLanguageLevel.LUA51)
                )
            ),
            capabilities = setOf(Capability.RUNTIME)
        ),
        // 4. luarocks
        LuaToolKind(
            id = "luarocks",
            displayName = "LuaRocks",
            binaryNames = listOf("luarocks"),
            probe = ProbeSpec(
                args = listOf("--version"),
                versionRegex = Regex("""LuaRocks\s+(\S+)""", RegexOption.IGNORE_CASE),
                luaVersionRegex = Regex("""for Lua\s+([\d.]+)""")
            ),
            capabilities = setOf(Capability.PACKAGE_MANAGER),
            minVersion = SemanticVersion(3, 0, 0)
        ),
        // 5. luacheck
        LuaToolKind(
            id = "luacheck",
            displayName = "luacheck",
            binaryNames = listOf("luacheck"),
            probe = ProbeSpec(
                args = listOf("--version"),
                versionRegex = Regex("""[Ll]uacheck[:\s]+(\S+)""")
            ),
            capabilities = setOf(Capability.LINTER)
        ),
        // 6. stylua
        LuaToolKind(
            id = "stylua",
            displayName = "StyLua",
            binaryNames = listOf("stylua"),
            probe = ProbeSpec(
                args = listOf("--version"),
                versionRegex = Regex("""stylua\s+(\S+)""", RegexOption.IGNORE_CASE)
            ),
            capabilities = setOf(Capability.FORMATTER)
        ),
        // 7. luacov
        LuaToolKind(
            id = "luacov",
            displayName = "LuaCov",
            binaryNames = listOf("luacov"),
            probe = ProbeSpec(
                args = listOf("--help"),
                versionRegex = Regex("""LuaCov\s+(\S+)""", RegexOption.IGNORE_CASE)
            ),
            capabilities = setOf(Capability.COVERAGE)
        ),
        // 8. busted
        LuaToolKind(
            id = "busted",
            displayName = "Busted",
            binaryNames = listOf("busted"),
            probe = ProbeSpec(
                args = listOf("--version"),
                versionRegex = Regex("""(?:busted\s+)?(\d[\w.\-]*)""", RegexOption.IGNORE_CASE)
            ),
            capabilities = setOf(Capability.TEST_RUNNER)
        ),
        // 9. redis-server (REDIS-01 §2.9, §4.2)
        LuaToolKind(
            id = "redis-server",
            displayName = "Redis Server",
            binaryNames = listOf("redis-server"),
            probe = ProbeSpec(
                args = listOf("--version"),
                versionRegex = Regex("""v=(\d[\w.]*)""")
            ),
            capabilities = emptySet()
        ),
        // 10. valkey-server (REDIS-01 §2.9, §4.2; provisioning strategy deferred to REDIS-03)
        LuaToolKind(
            id = "valkey-server",
            displayName = "Valkey Server",
            binaryNames = listOf("valkey-server"),
            probe = ProbeSpec(
                args = listOf("--version"),
                versionRegex = Regex("""v=(\d[\w.]*)""")
            ),
            capabilities = emptySet()
        )
    )

    private val byIdMap: Map<String, LuaToolKind> = BUILT_IN.associateBy { it.id }

    fun all(): List<LuaToolKind> = BUILT_IN

    fun findById(id: String): LuaToolKind? = byIdMap[id]

    fun inferKind(fileName: String): LuaToolKind? {
        val base = when {
            fileName.endsWith(".exe", ignoreCase = true) -> fileName.dropLast(4)
            fileName.endsWith(".bat", ignoreCase = true) -> fileName.dropLast(4)
            fileName.endsWith(".cmd", ignoreCase = true) -> fileName.dropLast(4)
            else -> fileName
        }.lowercase()

        // Pass 1: Exact matches (non-glob binaryNames)
        for (kind in BUILT_IN) {
            for (name in kind.binaryNames) {
                if (!isGlob(name) && name.lowercase() == base) {
                    return kind
                }
            }
        }

        // Pass 2: Glob matches (glob binaryNames)
        for (kind in BUILT_IN) {
            for (name in kind.binaryNames) {
                if (isGlob(name) && matchesGlob(name.lowercase(), base)) {
                    return kind
                }
            }
        }

        return null
    }
}
