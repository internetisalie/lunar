package net.internetisalie.lunar.toolchain.exec

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.rocks.DiscoveredRockspec
import net.internetisalie.lunar.rocks.RockspecRunPathProvider
import net.internetisalie.lunar.rocks.RockspecSourcePathProvider
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Tests for [LuaExecutionEnvironmentBuilder] (TOOLING-03-07/08/09/11/14/16 → TCs 10-13, 16, 20-22).
 *
 * Tools are seeded straight into the real TOOLING-02 registry with explicit health at the TC paths
 * (resolution never touches disk, so nonexistent paths are fine) and bound per kind so
 * `LuaToolResolver.resolve(project, kindId)` returns them. Rockspec-derived LUA_PATH/LUA_CPATH use
 * the `RockspecSourcePathProvider` discovery seam, mirroring `RockspecRunPathProviderTest`.
 */
class LuaExecutionEnvironmentBuilderTest : ToolchainSettingsTestCase() {

    private val builder: LuaExecutionEnvironmentBuilder
        get() = LuaExecutionEnvironmentBuilder.getInstance(project)

    override fun setUp() {
        super.setUp()
        LuaProjectSettings.getInstance(project).state.sourcePath = ""
        builder.invalidate()
    }

    override fun tearDown() {
        try {
            RockspecSourcePathProvider.testDiscoverySeam = null
            RockspecSourcePathProvider.invalidateCache(project)
        } finally {
            super.tearDown()
        }
    }

    private fun bindTool(kindId: String, path: String): LuaRegisteredTool {
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = kindId,
            path = path,
            version = "1.0.0",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(
                fileExists = true,
                executable = true,
                probeOk = true,
                probedAtMtime = 1L,
                reason = null,
            ),
        )
        registry.registerProvisioned(tool)
        settings.setBinding(kindId, tool.id)
        return tool
    }

    // TC 10
    fun testPathPrependDirsDedupedInDeclarationOrder() {
        bindTool("lua", "/usr/bin/lua")
        bindTool("luacheck", "/usr/bin/luacheck")
        bindTool("stylua", "/opt/x/stylua")

        assertEquals(listOf(Path.of("/usr/bin"), Path.of("/opt/x")), builder.pathPrependDirs())
    }

    // TC 11: LUA_PATH = rockspec prefix + expanded source path, ';'-normalized + trailing ";;".
    fun testLuaPathUnionOfRockspecPrefixAndSourcePath() {
        val prefix = seedRockspec()
        LuaProjectSettings.getInstance(project).state.sourcePath = "/p/src/?.lua"

        val expected = (prefix + "/p/src/?.lua").trimEnd(';') + ";;"
        assertEquals(expected, builder.build().luaPath)
    }

    // TC 12
    fun testEmptyPrefixAndSourcePathYieldsNullLuaPath() {
        RockspecSourcePathProvider.testDiscoverySeam = { emptyList() }
        RockspecSourcePathProvider.invalidateCache(project)
        LuaProjectSettings.getInstance(project).state.sourcePath = ""

        assertNull(builder.build().luaPath)
    }

    // TC 13
    fun testLuaCPathFromBuiltinCRockspec() {
        // TOOLING-05 Phase 3: RockspecBridge.read resolves the runtime via the resolver; bind a real
        // interpreter so the bridge can launch and parse the C-module rockspec.
        net.internetisalie.lunar.rocks.RockspecRuntimeTestSupport.registerRealLuaRuntime(project)
        val projectRoot = project.basePath ?: error("no base path")
        Files.createDirectories(Path.of(projectRoot).resolve("lua_modules"))
        val rockspec = writeCModuleRockspec()
        RockspecSourcePathProvider.testDiscoverySeam = { listOf(DiscoveredRockspec(rockspec, "c")) }
        RockspecSourcePathProvider.invalidateCache(project)
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA54

        val expected = "${projectRoot.replace('\\', '/')}/lua_modules/lib/lua/5.4/?.so;;"
        assertEquals(expected, builder.build().luaCPath)
    }

    // TC 16
    fun testCacheInvalidatedByToolchainTopic() {
        bindTool("lua", "/usr/bin/lua")
        assertEquals(listOf(Path.of("/usr/bin")), builder.pathPrependDirs())

        bindTool("stylua", "/opt/x/stylua")

        assertEquals(listOf(Path.of("/usr/bin"), Path.of("/opt/x")), builder.pathPrependDirs())
    }

    // TC 20
    fun testSourcePathOverrideSetsOnlyLuaPathVerbatim() {
        seedRockspec()
        LuaProjectSettings.getInstance(project).state.sourcePath = "/p/src/?.lua"

        val env = builder.build(sourcePathOverride = "/x/?.lua")

        assertEquals("/x/?.lua", env.luaPath)
        assertNull(env.luaCPath)
    }

    // TC 21
    fun testLuarocksConfigWhenActiveEnvHasConfigFile() {
        val envRoot = Files.createTempDirectory("lunar_env").toFile()
        val configFile = File(envRoot, "luarocks-config.lua")
        configFile.writeText("rocks_trees = {}")
        activateEnvironment(envRoot.path)

        assertEquals(configFile.path, builder.build().luarocksConfig)
    }

    // TC 22
    fun testLuarocksConfigNullWhenNoConfigFile() {
        val envRoot = Files.createTempDirectory("lunar_env_empty").toFile()
        activateEnvironment(envRoot.path)

        assertNull(builder.build().luarocksConfig)
    }

    fun testLuarocksConfigNullWithNoActiveEnvironment() {
        assertNull(builder.build().luarocksConfig)
    }

    /** Seeds a builtin-Lua rockspec via the discovery seam and returns its derived LUA_PATH prefix. */
    private fun seedRockspec(): String {
        val root = Files.createTempDirectory("lunar_rs")
        val rockspec = root.resolve("r-1.0.rockspec")
        Files.writeString(
            rockspec,
            """
            package = "r"
            version = "1.0"
            build = { type = "builtin", modules = { ["r"] = "src/r.lua" } }
            """.trimIndent(),
        )
        RockspecSourcePathProvider.testDiscoverySeam = { listOf(DiscoveredRockspec(rockspec, "r")) }
        RockspecSourcePathProvider.invalidateCache(project)
        return RockspecRunPathProvider.luaPathPrefix(project)
    }

    private fun writeCModuleRockspec(): Path {
        val root = Files.createTempDirectory("lunar_crocks")
        val rockspec = root.resolve("c-1.0.rockspec")
        Files.writeString(
            rockspec,
            """
            package = "c"
            version = "1.0"
            build = { type = "builtin", modules = { ["cjson"] = { "src/cjson.c" } } }
            """.trimIndent(),
        )
        return rockspec
    }

    private fun activateEnvironment(rootDir: String) {
        val projectSettings = LuaToolchainProjectSettings.getInstance(project)
        val spec = LuaEnvironmentState(
            id = UUID.randomUUID().toString(),
            name = "env",
            rootDir = rootDir,
        )
        projectSettings.upsertEnvironmentAndActivate(spec)
    }
}
