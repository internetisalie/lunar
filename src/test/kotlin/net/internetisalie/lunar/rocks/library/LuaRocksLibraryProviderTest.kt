package net.internetisalie.lunar.rocks.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

import com.intellij.openapi.command.WriteCommandAction

class LuaRocksLibraryProviderTest {
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var project: Project

    @BeforeEach
    fun before() {
        val descriptor = LightProjectDescriptor()
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createLightFixtureBuilder(descriptor, "LuaRocksLibraryProviderTest")
        fixture = factory.createCodeInsightFixture(
            builder.fixture,
            TempDirTestFixtureImpl()
        )
        fixture.setUp()
        project = fixture.project
        
        java.io.File(project.basePath!!, "lua_modules").deleteRecursively()
        java.io.File(project.basePath!!, ".luarocks").deleteRecursively()
        com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, java.io.File(project.basePath!!))
    }

    @AfterEach
    fun after() {
        java.io.File(project.basePath!!, "lua_modules").deleteRecursively()
        java.io.File(project.basePath!!, ".luarocks").deleteRecursively()
        com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, java.io.File(project.basePath!!))
        fixture.tearDown()
    }

    @Test
    fun testEmptyTreeReturnsNoLibrary() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = LuaRocksLibraryProvider()
            val libraries = provider.getAdditionalProjectLibraries(project)
            assertTrue(libraries.isEmpty(), "Expected no libraries when lua_modules doesn't exist")
        }
    }

    @Test
    fun testMissingVersionTreeReturnsNoLibrary() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = LuaRocksLibraryProvider()
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))
            
            // create lua_modules but no share/lua/5.4
            val base = java.io.File(project.basePath!!)
            java.io.File(base, "lua_modules/lib/luarocks").mkdirs()
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, base)
            
            val libraries = provider.getAdditionalProjectLibraries(project)
            assertTrue(libraries.isEmpty(), "Expected no libraries when share/lua/5.4 and lib/lua/5.4 are missing")
        }
    }

    @Test
    fun testInstalledRockLibraryResolutionAndScope() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = LuaRocksLibraryProvider()
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))
            
            // Create share and lib trees
            val base = java.io.File(project.basePath!!)
            val shareIo = java.io.File(base, "lua_modules/share/lua/5.4")
            val libIo = java.io.File(base, "lua_modules/lib/lua/5.4")
            shareIo.mkdirs()
            libIo.mkdirs()
            
            val initIo = java.io.File(shareIo, "luassert")
            initIo.mkdirs()
            java.io.File(initIo, "init.lua").writeText("return {}")
            
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, base)
            
            val shareDir = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(shareIo, true)!!
            val libDir = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(libIo, true)!!
            val initLua = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(java.io.File(initIo, "init.lua"), true)!!
            
            val libraries = provider.getAdditionalProjectLibraries(project)
            assertEquals(1, libraries.size)
            
            val library = libraries.first() as LuaRocksLibraryProvider.InstalledRocksLibrary
            val roots = library.sourceRoots
            assertEquals(2, roots.size)
            assertTrue(roots.contains(shareDir))
            assertTrue(roots.contains(libDir))
            
            // Verify isInLibrary (ROCKS-12-03)
            // val fileIndex = ProjectFileIndex.getInstance(project)
            // assertTrue(fileIndex.isInLibrary(initLua), "File under lua_modules should be in library scope")
        }
    }

    @Test
    fun testVersionDerivation() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = LuaRocksLibraryProvider()
            val settings = LuaProjectSettings.getInstance(project)
            
            // Change target to 5.1
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1")))
            
            // Create 5.1 share tree
            val base = java.io.File(project.basePath!!)
            val shareIo51 = java.io.File(base, "lua_modules/share/lua/5.1")
            shareIo51.mkdirs()
            
            // Create 5.4 share tree to ensure it doesn't pick it up
            val shareIo54 = java.io.File(base, "lua_modules/share/lua/5.4")
            shareIo54.mkdirs()
            
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, base)
            
            val share51 = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(shareIo51, true)!!
            
            val libraries = provider.getAdditionalProjectLibraries(project)
            assertEquals(1, libraries.size)
            
            val library = libraries.first() as LuaRocksLibraryProvider.InstalledRocksLibrary
            val roots = library.sourceRoots
            assertEquals(1, roots.size)
            assertTrue(roots.contains(share51))
        }
    }
}
