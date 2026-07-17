package net.internetisalie.lunar.rocks

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RockspecRunPathProviderTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // TOOLING-05 Phase 3: RockspecBridge.read resolves the runtime via the resolver (no more
        // hardcoded "lua" default), so bind a real interpreter for the bridge to launch.
        RockspecRuntimeTestSupport.registerRealLuaRuntime(project)
    }

    fun testRockspecRunPathProvider() {
        val physicalDirA = Files.createTempDirectory("lunar_rocks_test_a")
        val rockspecA = physicalDirA.resolve("a-1.0.rockspec")
        Files.writeString(rockspecA, """
            package = "a"
            version = "1.0"
            build = { type = "builtin", modules = { ["a"] = "src/a.lua" } }
        """.trimIndent())

        val physicalDirB = Files.createTempDirectory("lunar_rocks_test_b")
        val rockspecB = physicalDirB.resolve("b-1.0.rockspec")
        Files.writeString(rockspecB, """
            package = "b"
            version = "1.0"
            build = { type = "builtin", modules = { ["b"] = "lua/b.lua" } }
        """.trimIndent())
        
        val rockspecC = physicalDirB.resolve("c-1.0.rockspec")
        Files.writeString(rockspecC, """
            package = "c"
            version = "1.0"
            build = { type = "builtin", modules = { ["cjson"] = { "src/cjson.c" } } }
        """.trimIndent())

        RockspecSourcePathProvider.testDiscoverySeam = { _ ->
            listOf(
                DiscoveredRockspec(rockspecA, "a"),
                DiscoveredRockspec(rockspecB, "b"),
                DiscoveredRockspec(rockspecC, "c")
            )
        }
        RockspecSourcePathProvider.invalidateCache(project)
        
        val projPathNio = Path.of(project.basePath!!)
        val luaModulesPath = projPathNio.resolve("lua_modules")
        Files.createDirectories(luaModulesPath)
        
        val lua54Version = PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.4")
            ?: error("Standard 5.4 not registered")
        LuaProjectSettings.getInstance(project).state.setTarget(Target(LuaPlatform.STANDARD, lua54Version))

        // Prime cache
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            RockspecSourcePathProvider.getInstance(project).derivedPatterns()
        }.get()

        // TC #7: LUA_PATH Union
        val prefix = RockspecRunPathProvider.luaPathPrefix(project)
        val dirA = physicalDirA.toString().replace('\\', '/')
        val dirB = physicalDirB.toString().replace('\\', '/')
        
        val expectedPrefix = "$dirA/src/?.lua;$dirA/src/?/init.lua;$dirB/lua/?.lua;$dirB/lua/?/init.lua;"
        assertEquals(expectedPrefix, prefix)

        val dataC = RockspecBridge.read(project, rockspecC)
        println("DATA C: " + dataC)
        
        val cRocks = RockspecSourcePathProvider.getInstance(project).cModuleRockspecs()
        println("C ROCKS: " + cRocks)
        
        // TC #8: C-Module LUA_CPATH
        val cPath = RockspecRunPathProvider.luaCPath(project)
        val projPath = project.basePath?.replace('\\', '/')
        assertEquals("$projPath/lua_modules/lib/lua/5.4/?.so;;", cPath)
    }

    // BUG-384: nativeModuleExtension must match the host platform

    fun testNativeModuleExtensionMatchesPlatform() {
        val ext = RockspecRunPathProvider.nativeModuleExtension()
        if (SystemInfo.isWindows) {
            assertEquals("dll", ext)
        } else {
            assertEquals("so", ext)
        }
    }

    // BUG-384: luaCPath must embed the native extension, not a hardcoded ".so"

    fun testLuaCPathUsesNativeExtension() {
        val physicalDir = Files.createTempDirectory("lunar_rocks_ext_test")
        val rockspec = physicalDir.resolve("c-1.0.rockspec")
        Files.writeString(rockspec, """
            package = "c"
            version = "1.0"
            build = { type = "builtin", modules = { ["cjson"] = { "src/cjson.c" } } }
        """.trimIndent())

        RockspecSourcePathProvider.testDiscoverySeam = { _ ->
            listOf(DiscoveredRockspec(rockspec, "c"))
        }
        RockspecSourcePathProvider.invalidateCache(project)

        val projPathNio = Path.of(project.basePath!!)
        Files.createDirectories(projPathNio.resolve("lua_modules"))

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            RockspecSourcePathProvider.getInstance(project).derivedPatterns()
        }.get()

        val cPath = RockspecRunPathProvider.luaCPath(project)
        val expectedExt = RockspecRunPathProvider.nativeModuleExtension()
        assertNotNull(cPath)
        assertTrue("Expected LUA_CPATH to contain '?.$expectedExt', got: $cPath", cPath!!.contains("?.$expectedExt"))
    }

    override fun tearDown() {
        try {
            RockspecSourcePathProvider.testDiscoverySeam = null
            RockspecRuntimeTestSupport.reset(project)
        } finally {
            super.tearDown()
        }
    }
}