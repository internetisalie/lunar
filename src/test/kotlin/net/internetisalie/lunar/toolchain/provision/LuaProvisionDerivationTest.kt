package net.internetisalie.lunar.toolchain.provision

import junit.framework.TestCase
import org.junit.Test

/**
 * Pure derivation/validation tests for the provisioning dialogs (design §2.12, §2.13, §3.10) —
 * covers TC 16 (validation) and TC 18 (batch derivation) plus `toRequest` item ordering, without
 * showing any Swing dialog.
 */
class LuaProvisionDerivationTest {

    private fun form(
        name: String = "lua-5.4.8",
        rootDir: String = "/tmp/does-not-exist-lunar-env",
        includeLuaRocks: Boolean = true,
        tools: List<LuaToolChoice> = emptyList(),
    ) = LuaProvisionFormState(
        name = name,
        rootDir = rootDir,
        runtime = LuaToolChoice("lua", "5.4.8"),
        luaRocks = LuaRocksChoice(includeLuaRocks, "latest"),
        selectedTools = tools,
    )

    @Test
    fun `toRequest orders runtime, luarocks, release-binary tools, rock tools`() {
        val request = form(
            tools = listOf(
                LuaToolChoice("luacov", "0.16.0"),
                LuaToolChoice("stylua", "2.5.2"),
                LuaToolChoice("busted", "2.2.0"),
                LuaToolChoice("luacheck", "1.2.0"),
            ),
        ).toRequest()
        val kinds = request.items.map { it.kindId }
        TestCase.assertEquals(
            listOf("lua", "luarocks", "luacheck", "stylua", "busted", "luacov"),
            kinds,
        )
    }

    @Test
    fun `toRequest omits luarocks when not included`() {
        val request = form(includeLuaRocks = false, tools = emptyList()).toRequest()
        TestCase.assertEquals(listOf("lua"), request.items.map { it.kindId })
    }

    @Test
    fun `runtime combo offers lua and luajit with the luajit gate open`() {
        TestCase.assertEquals(listOf("lua", "luajit"), LuaToolCatalog.RUNTIME_KINDS)
        TestCase.assertFalse("luajit is a runtime, not a dev tool", LuaToolCatalog.TOOL_KINDS.contains("luajit"))
    }

    @Test
    fun `rock tools are classified apart from release-binary tools`() {
        TestCase.assertEquals(listOf("busted", "luacov"), LuaToolCatalog.ROCK_TOOL_KINDS)
        TestCase.assertEquals(
            listOf("luacheck", "stylua", "lua-language-server"),
            LuaToolCatalog.RELEASE_BINARY_TOOL_KINDS,
        )
    }

    @Test
    fun `blank name fails validation on the name field`() {
        val outcome = form(name = "   ").validate(emptySet())
        TestCase.assertNotNull(outcome)
        TestCase.assertEquals(LuaProvisionField.NAME, outcome?.field)
    }

    @Test
    fun `duplicate name fails validation on the name field`() {
        val outcome = form(name = "existing").validate(setOf("existing"))
        TestCase.assertEquals(LuaProvisionField.NAME, outcome?.field)
    }

    @Test
    fun `quotes and semicolons in rootDir are rejected`() {
        val quoted = form(rootDir = "/tmp/a\"b").validate(emptySet())
        TestCase.assertEquals(LuaProvisionField.ROOT_DIR, quoted?.field)
        val semi = form(rootDir = "/tmp/a;b").validate(emptySet())
        TestCase.assertEquals(LuaProvisionField.ROOT_DIR, semi?.field)
    }

    @Test
    fun `non-empty non-Lunar directory fails on the rootDir field`() {
        val dir = createTempDirectory()
        java.io.File(dir, "some-file.txt").writeText("x")
        val outcome = form(rootDir = dir.absolutePath).validate(emptySet())
        TestCase.assertEquals(LuaProvisionField.ROOT_DIR, outcome?.field)
        TestCase.assertTrue(outcome?.message?.contains("not a Lunar environment") == true)
    }

    @Test
    fun `empty and absent directories pass rootDir validation`() {
        val empty = createTempDirectory()
        TestCase.assertNull(form(rootDir = empty.absolutePath).validate(emptySet()))
        TestCase.assertNull(form(rootDir = "/tmp/lunar-absent-${System.nanoTime()}").validate(emptySet()))
    }

    @Test
    fun `blank runtime version fails on the runtime field`() {
        val state = form().copy(runtime = LuaToolChoice("lua", ""))
        TestCase.assertEquals(LuaProvisionField.RUNTIME_VERSION, state.validate(emptySet())?.field)
    }

    @Test
    fun `batch derivation shapes rootDir and items per row`() {
        val rows = listOf(LuaBatchRow("lua", "5.4.8"), LuaBatchRow("lua", "5.1.5"))
        val requests = LuaBatchDerivation.toRequests("/base/.lua-matrix", rows)
        TestCase.assertEquals(2, requests.size)
        val first = requests.first()
        TestCase.assertEquals("lua-5.4.8", first.environmentName)
        TestCase.assertEquals("/base/.lua-matrix/lua-5.4.8", first.rootDir)
        TestCase.assertEquals(
            listOf(LuaProvisionItem("lua", "5.4.8"), LuaProvisionItem("luarocks", "latest")),
            first.items,
        )
    }

    private fun createTempDirectory(): java.io.File =
        java.nio.file.Files.createTempDirectory("lunar-prov-test").toFile().also { it.deleteOnExit() }
}
