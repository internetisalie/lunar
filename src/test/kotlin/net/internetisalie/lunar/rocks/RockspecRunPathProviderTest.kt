package net.internetisalie.lunar.rocks

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RockspecRunPathProviderTest : BasePlatformTestCase() {

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
        
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA54

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

    override fun tearDown() {
        try {
            RockspecSourcePathProvider.testDiscoverySeam = null
        } finally {
            super.tearDown()
        }
    }
}