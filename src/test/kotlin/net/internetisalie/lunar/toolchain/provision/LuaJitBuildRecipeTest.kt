package net.internetisalie.lunar.toolchain.provision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Pure-plan snapshot tests for [LuaJitBuildRecipe] (design §3.9, dossier §2c; requirements TC 17).
 * Assert the EXACT git-clone (FULL) / checkout / `make PREFIX=` step list, the hand-copy install
 * set (binary, static/shared libs, headers, `jit/` runtime), and the macOS deployment-target env
 * branch — all without invoking git/make (mirror [PucLuaBuildRecipeTest]).
 */
class LuaJitBuildRecipeTest {
    private val buildDir = Path.of("/p/.lua/.build/luajit-v2.1")
    private val prefix = Path.of("/p/.lua")

    private fun toolchain() = LuaCompilerProbe.Toolchain(
        Path.of("/usr/bin/gcc"), Path.of("/usr/bin/ar"), Path.of("/usr/bin/ranlib"), Path.of("/usr/bin/make"),
    )

    private fun plan(os: LuaOs) =
        LuaJitBuildRecipe.plan(LuaBuildRecipeInput("v2.1", os, toolchain(), buildDir, prefix))

    @Test
    fun linuxStepsAreFullCloneCheckoutAndMakePrefix() {
        val steps = plan(LuaOs.LINUX).steps
        assertEquals(
            listOf(
                listOf("git", "clone", "https://github.com/LuaJIT/LuaJIT", "/p/.lua/.build/luajit-v2.1"),
                listOf("git", "-C", "/p/.lua/.build/luajit-v2.1", "checkout", "v2.1"),
                listOf("make", "PREFIX=/p/.lua"),
            ),
            steps.map { it.command },
        )
        assertEquals("make runs in the clone dir", buildDir, steps[2].workDir)
        assertTrue("linux make has no deployment-target env", steps[2].env.isEmpty())
    }

    @Test
    fun installCopiesCoverBinaryLibsHeadersAndExecutable() {
        val plan = plan(LuaOs.LINUX)
        val src = buildDir.resolve("src")
        assertTrue(plan.installCopies.contains(src.resolve("luajit") to prefix.resolve("bin/lua")))
        assertTrue(plan.installCopies.contains(src.resolve("libluajit.a") to prefix.resolve("lib/libluajit-5.1.a")))
        assertTrue(plan.installCopies.contains(src.resolve("libluajit.so") to prefix.resolve("lib/libluajit-5.1.so.2")))
        for (header in listOf("lua.h", "lauxlib.h", "lualib.h", "luaconf.h", "lua.hpp", "luajit.h")) {
            assertTrue("header $header copied", plan.installCopies.contains(src.resolve(header) to prefix.resolve("include/$header")))
        }
        assertEquals(listOf(prefix.resolve("bin/lua")), plan.executables)
    }

    @Test
    fun jitRuntimeSourceAndDestArePinned() {
        assertEquals(buildDir.resolve("src/jit"), LuaJitBuildRecipe.jitRuntimeSource(buildDir))
        assertEquals(prefix.resolve("share/lua/5.1/jit"), LuaJitBuildRecipe.jitRuntimeDest(prefix))
    }

    @Test
    fun macosSetsDeploymentTargetWhenUnset() {
        // The recipe only injects MACOSX_DEPLOYMENT_TARGET when the ambient var is unset;
        // guard so the assertion is deterministic regardless of the CI environment.
        if (System.getenv("MACOSX_DEPLOYMENT_TARGET") != null) return
        val makeStep = plan(LuaOs.MACOS).steps.first { it.command.firstOrNull() == "make" }
        assertEquals(mapOf("MACOSX_DEPLOYMENT_TARGET" to "11.0"), makeStep.env)
    }
}
