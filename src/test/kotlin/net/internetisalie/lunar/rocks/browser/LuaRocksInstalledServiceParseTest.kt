package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TC-ROCKS-16-05: [LuaRocksInstalledService.parseInstalled] reads name + version per tree row
 * (design §4.1), skipping malformed short lines.
 */
class LuaRocksInstalledServiceParseTest {

    @Test
    fun `TC-ROCKS-16-05 parses name and version per row`() {
        val stdout = """
            inspect	3.1.3-0	installed	/proj/lua_modules/lib/luarocks/rocks-5.4
            luassert	1.9.0-1	installed	/proj/lua_modules/lib/luarocks/rocks-5.4
        """.trimIndent()

        val rows = LuaRocksInstalledService.parseInstalled(stdout)

        assertEquals(
            listOf(InstalledRockRow("inspect", "3.1.3-0"), InstalledRockRow("luassert", "1.9.0-1")),
            rows,
        )
    }

    @Test
    fun `skips blank and single-field lines`() {
        val stdout = "inspect\t3.1.3-0\tinstalled\n\nonlyname\n   \nluassert\t1.9.0-1"
        val rows = LuaRocksInstalledService.parseInstalled(stdout)
        assertEquals(listOf(InstalledRockRow("inspect", "3.1.3-0"), InstalledRockRow("luassert", "1.9.0-1")), rows)
    }

    @Test
    fun `empty stdout yields empty list`() {
        assertEquals(emptyList<InstalledRockRow>(), LuaRocksInstalledService.parseInstalled(""))
    }
}
