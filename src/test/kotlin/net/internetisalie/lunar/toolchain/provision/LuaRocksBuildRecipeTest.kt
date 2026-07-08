package net.internetisalie.lunar.toolchain.provision

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

/**
 * Pure-plan snapshot test for [LuaRocksBuildRecipe] (design §3.5 step L): the exact
 * `configure` / `make build` / `make install` sequence with the baked prefix, plus the
 * verified `bin/luarocks` executable and the CFLAGS config-append text.
 */
class LuaRocksBuildRecipeTest {
    @Test
    fun posixConfigureMakeBuildMakeInstallSequence() {
        val buildDir = Path.of("/p/.lua/.build/luarocks-3.13.0")
        val prefix = Path.of("/p/.lua")
        val toolchain = LuaCompilerProbe.Toolchain(
            Path.of("/usr/bin/gcc"), Path.of("/usr/bin/ar"), Path.of("/usr/bin/ranlib"), Path.of("/usr/bin/make"),
        )
        val plan = LuaRocksBuildRecipe.plan(LuaBuildRecipeInput("3.13.0", LuaOs.LINUX, toolchain, buildDir, prefix))

        assertEquals(
            listOf(
                listOf("./configure", "--prefix=/p/.lua", "--with-lua=/p/.lua"),
                listOf("make", "build"),
                listOf("make", "install"),
            ),
            plan.steps.map { it.command },
        )
        plan.steps.forEach { assertEquals(buildDir, it.workDir) }
        assertEquals(listOf(prefix.resolve("bin/luarocks")), plan.executables)
        assertEquals("variables = {\n   CFLAGS = \"-O2 -fPIC\",\n}", LuaRocksBuildRecipe.CONFIG_APPEND)
    }
}
