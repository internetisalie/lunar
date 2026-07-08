package net.internetisalie.lunar.toolchain.provision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Pure-plan snapshot tests for [PucLuaBuildRecipe] (design §3.5, §3.6). They assert the
 * EXACT `BuildPlan.steps` command lists for a controlled `src/` file set across 5.4.8/linux
 * (requirements TC 1), 5.1/linux, and 5.2/linux — exercising the std/compat/ldflags/`print.o`
 * differences byte-for-byte — plus the `patchLuaconf` splice for one version.
 *
 * A representative source set (`lapi.c`, `lauxlib.c`, `lua.c`, `luac.c`, and `print.c` for 5.1)
 * is used so the assertions stay tractable while still covering the per-TU compile ordering,
 * the `ar` object exclusion (lua.o/luac.o/print.o), and both link commands.
 */
class PucLuaBuildRecipeTest {
    private val cc = Path.of("/usr/bin/gcc")
    private val ar = Path.of("/usr/bin/ar")
    private val ranlib = Path.of("/usr/bin/ranlib")
    private val prefix = Path.of("/p/.lua")

    private fun toolchain() = LuaCompilerProbe.Toolchain(cc, ar, ranlib, Path.of("/usr/bin/make"))

    private fun buildTree(version: String, sources: List<String>): Path {
        val buildDir = Files.createTempDirectory("lunar-puc-$version")
        val src = buildDir.resolve("src").also { it.createDirectories() }
        sources.forEach { src.resolve(it).writeText("/* $it */") }
        for (header in listOf("lua.h", "luaconf.h", "lualib.h", "lauxlib.h", "lua.hpp")) {
            src.resolve(header).writeText("/* $header */")
        }
        return buildDir
    }

    private fun plan(version: String, sources: List<String>): BuildPlan {
        val buildDir = buildTree(version, sources)
        return PucLuaBuildRecipe.plan(LuaBuildRecipeInput(version, LuaOs.LINUX, toolchain(), buildDir, prefix))
    }

    @Test
    fun lua548LinuxCommandSequenceMatchesTc1() {
        val sources = listOf("lapi.c", "lauxlib.c", "lua.c", "luac.c")
        val steps = plan("5.4.8", sources).steps
        val cflags = listOf(
            "-O2", "-Wall", "-Wextra", "-std=gnu99",
            "-DLUA_USE_POSIX", "-DLUA_USE_DLOPEN", "-DLUA_COMPAT_5_3",
        )
        val ldflags = listOf("-Wl,-E", "-ldl", "-lm")
        val expected = listOf(
            listOf("/usr/bin/gcc") + cflags + listOf("-c", "-o", "lapi.o", "lapi.c"),
            listOf("/usr/bin/gcc") + cflags + listOf("-c", "-o", "lauxlib.o", "lauxlib.c"),
            listOf("/usr/bin/gcc") + cflags + listOf("-c", "-o", "lua.o", "lua.c"),
            listOf("/usr/bin/gcc") + cflags + listOf("-c", "-o", "luac.o", "luac.c"),
            listOf("/usr/bin/ar", "rcu", "liblua54.a", "lapi.o", "lauxlib.o"),
            listOf("/usr/bin/ranlib", "liblua54.a"),
            listOf("/usr/bin/gcc", "-o", "luac", "luac.o", "liblua54.a") + ldflags,
            listOf("/usr/bin/gcc", "-o", "lua", "lua.o", "liblua54.a") + ldflags,
        )
        assertEquals(expected, steps.map { it.command })
    }

    @Test
    fun lua51LinuxHasNoStdNoCompatAndKeepsPrintObjectInLuacLink() {
        val sources = listOf("lapi.c", "lauxlib.c", "lua.c", "luac.c", "print.c")
        val steps = plan("5.1.5", sources).steps
        val cflags = listOf("-O2", "-Wall", "-Wextra", "-DLUA_USE_POSIX", "-DLUA_USE_DLOPEN")
        val ldflags = listOf("-Wl,-E", "-ldl", "-lm")
        val compile = steps.filter { it.command.contains("-c") }
        compile.forEach { assertEquals(listOf("/usr/bin/gcc") + cflags + it.command.takeLast(4), it.command) }
        assertTrue("5.1 has no -std", steps.none { it.command.contains("-std=gnu99") })
        assertTrue("5.1 has no compat defines", steps.none { it.command.any { arg -> arg.startsWith("-DLUA_COMPAT") } })
        val archive = steps.first { it.command.firstOrNull() == "/usr/bin/ar" }.command
        assertEquals(listOf("/usr/bin/ar", "rcu", "liblua51.a", "lapi.o", "lauxlib.o"), archive)
        val luacLink = steps.first { it.command.contains("luac") && it.command.contains("-o") }.command
        assertEquals(
            listOf("/usr/bin/gcc", "-o", "luac", "luac.o", "print.o", "liblua51.a") + ldflags,
            luacLink,
        )
    }

    @Test
    fun lua52LinuxHasCompatAllAndFivePointTwoExtras() {
        val sources = listOf("lapi.c", "lua.c", "luac.c")
        val steps = plan("5.2.4", sources).steps
        val firstCompile = steps.first { it.command.contains("-c") }.command
        val expectedCflags = listOf(
            "-O2", "-Wall", "-Wextra",
            "-DLUA_USE_POSIX", "-DLUA_USE_DLOPEN",
            "-DLUA_USE_STRTODHEX", "-DLUA_USE_AFORMAT", "-DLUA_USE_LONGLONG",
            "-DLUA_COMPAT_ALL",
        )
        assertEquals(listOf("/usr/bin/gcc") + expectedCflags + listOf("-c", "-o", "lapi.o", "lapi.c"), firstCompile)
        assertTrue("5.2 has no -std", steps.none { it.command.contains("-std=gnu99") })
    }

    @Test
    fun lua54InstallCopiesAndExecutablesAreCorrect() {
        val buildDir = buildTree("5.4.8", listOf("lapi.c", "lua.c", "luac.c"))
        val plan = PucLuaBuildRecipe.plan(LuaBuildRecipeInput("5.4.8", LuaOs.LINUX, toolchain(), buildDir, prefix))
        val src = buildDir.resolve("src")
        assertTrue(plan.installCopies.contains(src.resolve("lua") to prefix.resolve("bin/lua")))
        assertTrue(plan.installCopies.contains(src.resolve("luac") to prefix.resolve("bin/luac")))
        assertTrue(plan.installCopies.contains(src.resolve("liblua54.a") to prefix.resolve("lib/liblua54.a")))
        assertTrue(plan.installCopies.contains(src.resolve("luaconf.h") to prefix.resolve("include/luaconf.h")))
        assertEquals(listOf(prefix.resolve("bin/lua"), prefix.resolve("bin/luac")), plan.executables)
    }

    @Test
    fun patchLuaconfInsertsBlockBeforeLastEndifWithFivePointFourPaths() {
        val original = "#ifndef luaconf_h\n#define luaconf_h\n#define LUA_ROOT \"/x\"\n#endif\n"
        val patched = PucLuaBuildRecipe.patchLuaconf(original, "5.4.8", prefix)
        val expectedBlock = listOf(
            "/* patched by Lunar provisioner */",
            "#undef LUA_PATH_DEFAULT",
            "#define LUA_PATH_DEFAULT \"/p/.lua/share/lua/5.4/?.lua;/p/.lua/share/lua/5.4/?/init.lua;./?.lua;./?/init.lua\"",
            "#undef LUA_CPATH_DEFAULT",
            "#define LUA_CPATH_DEFAULT \"/p/.lua/lib/lua/5.4/?.so;/p/.lua/lib/lua/5.4/loadall.so;./?.so\"",
        ).joinToString("\n")
        val expected = "#ifndef luaconf_h\n#define luaconf_h\n#define LUA_ROOT \"/x\"\n" +
            expectedBlock + "\n#endif\n"
        assertEquals(expected, patched)
    }

    @Test
    fun patchLuaconfFivePointOneUsesLeadingCurrentDirPaths() {
        val original = "#ifndef luaconf_h\n#endif\n"
        val patched = PucLuaBuildRecipe.patchLuaconf(original, "5.1.5", prefix)
        assertTrue(
            patched.contains(
                "#define LUA_PATH_DEFAULT \"./?.lua;/p/.lua/share/lua/5.1/?.lua;/p/.lua/share/lua/5.1/?/init.lua\"",
            ),
        )
        assertTrue(
            patched.contains(
                "#define LUA_CPATH_DEFAULT \"./?.so;/p/.lua/lib/lua/5.1/?.so;/p/.lua/lib/lua/5.1/loadall.so\"",
            ),
        )
    }
}
