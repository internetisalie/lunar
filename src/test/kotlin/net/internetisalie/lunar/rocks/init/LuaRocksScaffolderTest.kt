package net.internetisalie.lunar.rocks.init

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.run.LuaRunConfigurationType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Files

/**
 * Headless scaffold tests verifying generated file content against design §3/§4.
 *
 * Uses a real temp directory refreshed into the VFS so [LuaRocksScaffolder] can
 * call VFS write operations without a sandbox IDE.  All assertions check file text
 * from the filesystem (temp dir), not through VFS, to avoid editor infrastructure.
 */
@RunWith(JUnit4::class)
class LuaRocksScaffolderTest : BasePlatformTestCase() {

    private lateinit var tempDir: File
    private lateinit var baseDir: VirtualFile

    override fun setUp() {
        super.setUp()
        tempDir = Files.createTempDirectory("rocks-scaffold-test").toFile()
        baseDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir)
            ?: error("Cannot find VirtualFile for $tempDir")
    }

    override fun tearDown() {
        try {
            tempDir.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    // ------------------------------------------------------------------ TC-ROCKS-01-01

    /** TC-ROCKS-01-01: minimal single rock library — no options. */
    @Test
    fun testMinimalSingleRockLibrary() {
        val settings = LuaRocksProjectSettings(
            name = "my-lib",
            type = RockType.LIBRARY,
        )
        scaffold(settings)

        assertFileExists("my-lib-scm-1.rockspec")
        assertFileContains("my-lib-scm-1.rockspec", "my-lib")
        assertFileExists("src/my-lib.lua")
        assertFileExists("lua_modules")
        assertFileExists(".gitignore")
        assertFileContains(".gitignore", "/lua_modules/")
        assertFileNotExists("src/setup.lua")
        assertFileNotExists("spec")
        assertFileNotExists("Makefile")
    }

    // ------------------------------------------------------------------ TC-ROCKS-01-02

    /** TC-ROCKS-01-02: single rock application with loader setup. */
    @Test
    fun testApplicationWithLoaderSetup() {
        val settings = LuaRocksProjectSettings(
            name = "my-app",
            type = RockType.APPLICATION,
            loaderSetup = true,
        )
        scaffold(settings)

        assertFileExists("my-app-scm-1.rockspec")
        assertFileExists("src/main.lua")
        assertFileExists("src/setup.lua")
        assertFileContains("src/setup.lua", "lua_modules/share/lua/")
        assertFileExists("lua_modules")
        assertFileExists(".gitignore")
        assertFileNotExists("spec")
        assertFileNotExists("Makefile")
    }

    // ------------------------------------------------------------------ TC-ROCKS-01-03

    /** TC-ROCKS-01-03: single rock library with busted configuration. */
    @Test
    fun testLibraryWithBustedConfig() {
        val settings = LuaRocksProjectSettings(
            name = "my-lib",
            type = RockType.LIBRARY,
            bustedConfig = true,
        )
        scaffold(settings)

        assertFileExists("my-lib-scm-1.rockspec")
        assertFileExists("src/my-lib.lua")
        assertFileExists("spec/my-lib_spec.lua")
        assertFileContains("spec/my-lib_spec.lua", "require(\"my-lib\")")
        assertFileExists("lua_modules")
        assertFileExists(".gitignore")
        assertFileNotExists("src/setup.lua")
    }

    // ------------------------------------------------------------------ TC-ROCKS-01-04

    /** TC-ROCKS-01-04: single rock application with all options. */
    @Test
    fun testApplicationWithAllOptions() {
        val settings = LuaRocksProjectSettings(
            name = "my-app",
            type = RockType.APPLICATION,
            loaderSetup = true,
            bustedConfig = true,
            makefile = true,
        )
        scaffold(settings)

        assertFileExists("my-app-scm-1.rockspec")
        assertFileContains("my-app-scm-1.rockspec", "install")
        assertFileExists("src/main.lua")
        assertFileExists("src/setup.lua")
        assertFileExists("spec/my-app_spec.lua")
        assertFileExists("Makefile")
        assertFileContains("Makefile", ".PHONY: build test lint format coverage rocks clean")
        assertFileContains("Makefile", "luarocks make")
        assertFileContains("Makefile", "busted")
        assertFileContains("Makefile", "luacheck src spec")
        assertFileContains("Makefile", "stylua src spec")
        assertFileContains("Makefile", "busted --coverage")
        assertFileContains("Makefile", "luacov")
        assertFileContains("Makefile", "luarocks install --local my-app-scm-1.rockspec")
        assertFileContains("Makefile", "rm -rf lua_modules .luarocks luacov.stats.out luacov.report.out")
        assertFileExists("lua_modules")
        assertFileExists(".gitignore")
    }

    // ------------------------------------------------------------------ rockspec content

    @Test
    fun testRockspecLibraryContent() {
        scaffold(LuaRocksProjectSettings(name = "lib", type = RockType.LIBRARY))
        assertFileContains("lib-scm-1.rockspec", "rockspec_format = \"3.0\"")
        assertFileContains("lib-scm-1.rockspec", "package = \"lib\"")
        assertFileContains("lib-scm-1.rockspec", "version = \"scm-1\"")
        assertFileContains("lib-scm-1.rockspec", "[\"lib\"] = \"src/lib.lua\"")
    }

    @Test
    fun testRockspecApplicationContent() {
        scaffold(LuaRocksProjectSettings(name = "app", type = RockType.APPLICATION))
        assertFileContains("app-scm-1.rockspec", "src/main.lua")
        assertFileContains("app-scm-1.rockspec", "install")
    }

    // ------------------------------------------------------------------ BUG-385

    /**
     * BUG-385: [LuaRocksScaffolder.patchRunConfigTemplate] must operate on the registered
     * [LuaRunConfigurationType] singleton, not a fresh unregistered instance.
     *
     * This test verifies that [ConfigurationTypeUtil.findConfigurationType] returns the same
     * singleton that the platform registered via plugin.xml — i.e. the scaffolder no longer
     * constructs a detached copy.
     */
    @Test
    fun testScaffolderUsesRegisteredConfigTypeSingleton() {
        val registered = ConfigurationTypeUtil.findConfigurationType(LuaRunConfigurationType::class.java)
        assertNotNull("LuaRunConfigurationType must be registered in plugin.xml", registered)
        val fresh = LuaRunConfigurationType()
        assertNotSame(
            "Scaffolder must use the registered singleton, not a fresh LuaRunConfigurationType()",
            fresh,
            registered,
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun scaffold(settings: LuaRocksProjectSettings) {
        WriteAction.runAndWait<Throwable> {
            LuaRocksScaffolder.scaffold(project, baseDir, settings)
        }
    }

    private fun assertFileExists(relativePath: String) {
        val file = File(tempDir, relativePath)
        assertTrue("Expected '$relativePath' to exist under $tempDir", file.exists())
    }

    private fun assertFileNotExists(relativePath: String) {
        val file = File(tempDir, relativePath)
        assertFalse("Expected '$relativePath' to NOT exist under $tempDir", file.exists())
    }

    private fun assertFileContains(relativePath: String, expected: String) {
        val file = File(tempDir, relativePath)
        assertTrue("Expected file '$relativePath' to exist", file.exists())
        val content = file.readText()
        assertTrue(
            "Expected '$relativePath' to contain: $expected\nActual content:\n$content",
            content.contains(expected),
        )
    }
}
