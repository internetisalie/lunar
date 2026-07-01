package net.internetisalie.lunar.platform

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class LuaInterpreterSearchPathGlobTest : BasePlatformTestCase() {

    private lateinit var base: Path

    override fun setUp() {
        super.setUp()
        base = Files.createTempDirectory("lunar-glob-test")
    }

    override fun tearDown() {
        try {
            base.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun mkdirs(vararg relatives: String) {
        for (relative in relatives) Files.createDirectories(base.resolve(relative))
    }

    private fun mkfile(relative: String) {
        Files.createFile(base.resolve(relative))
    }

    fun testExpandsGlobDirectoriesExcludingFiles() {
        mkdirs("Lua 5.1", "Lua 5.4")
        mkfile("README")

        val result = expandSearchPath("$base/Lua 5.*")

        assertEquals(listOf(base.resolve("Lua 5.1"), base.resolve("Lua 5.4")), result)
    }

    fun testExpandsMidSegmentGlob() {
        mkdirs("lua5.1/bin", "lua5.4/bin", "lua5.4/share")

        val result = expandSearchPath("$base/lua5.*/bin")

        assertEquals(listOf(base.resolve("lua5.1/bin"), base.resolve("lua5.4/bin")), result)
    }

    fun testLiteralPathReturnsSingleElement() {
        mkdirs("Lua 5.1")

        val result = expandSearchPath("$base/Lua 5.1")

        assertEquals(listOf(Path.of("$base/Lua 5.1")), result)
    }

    fun testLiteralNonExistentPathReturnsSingleElement() {
        val result = expandSearchPath("/no/such/literal/dir")

        assertEquals(listOf(Path.of("/no/such/literal/dir")), result)
    }

    fun testDottedGlobRequiresDot() {
        mkdirs("Lua 5.1", "Lua 5.4", "Lua 5")

        val result = expandSearchPath("$base/Lua 5.*")

        assertEquals(listOf(base.resolve("Lua 5.1"), base.resolve("Lua 5.4")), result)
    }

    fun testMatchesAreSortedAscending() {
        mkdirs("lua5.4", "lua5.1", "lua5.2")

        val result = expandSearchPath("$base/lua5.*")

        assertEquals(
            listOf(base.resolve("lua5.1"), base.resolve("lua5.2"), base.resolve("lua5.4")),
            result,
        )
    }

    fun testMissingBaseReturnsEmpty() {
        val result = expandSearchPath("/no/such/base/Lua 5.*")

        assertEquals(emptyList<Path>(), result)
    }

    fun testNoMatchReturnsEmpty() {
        val result = expandSearchPath("$base/Ruby *")

        assertEquals(emptyList<Path>(), result)
    }

    fun testQuestionMarkMatchesSingleChar() {
        mkdirs("lua51", "lua54", "luaX")

        val result = expandSearchPath("$base/lua5?")

        assertEquals(listOf(base.resolve("lua51"), base.resolve("lua54")), result)
    }
}
