package net.internetisalie.lunar.analysis

import net.internetisalie.lunar.corpus.CorpusTarget
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-552. A `---@param mode? string` accepts `nil`, whether the argument is omitted or passed
 * explicitly, so `loadfile("x.lua", nil, env)` must not report *nil value is not assignable*.
 * A wrong non-nil type, and a `nil` for a required parameter, are still reported.
 */
@RunWith(JUnit4::class)
class LuaOptionalParamExplicitNilTest : IndexedBasePlatformTestCase() {
    private val declaration =
        """
        ---@param name string
        ---@param mode? string
        function open(name, mode) end
        """.trimIndent()

    private fun assignabilityHits(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("is not assignable to") }
    }

    private fun luaJitHits(text: String): List<String> {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.LUAJIT, "2.1"))
        val saved = CorpusTarget.pin(project, Target(LuaPlatform.LUAJIT, version))
        try {
            return assignabilityHits(text)
        } finally {
            CorpusTarget.restore(project, saved)
        }
    }

    private fun crossFileHits(call: String): List<String> {
        myFixture.addFileToProject("defs.lua", declaration)
        return assignabilityHits(call)
    }

    @Test
    fun testExplicitNilForOptionalStubParamIsSilent() =
        assertEquals(
            emptyList<String>(),
            luaJitHits("local env = {}\nlocal f = loadfile(\"x.lua\", nil, env)\nprint(f)\n"),
        )

    @Test
    fun testExplicitNilForOptionalCrossFileParamIsSilent() =
        assertEquals(emptyList<String>(), crossFileHits("open(\"x\", nil)\n"))

    @Test
    fun testExplicitNilForOptionalInFileParamIsSilent() =
        assertEquals(emptyList<String>(), assignabilityHits("$declaration\nopen(\"x\", nil)\n"))

    @Test
    fun testWrongTypeForOptionalStubParamIsReported() {
        val hits = luaJitHits("loadfile(\"x.lua\", 1, {})\n")
        assertEquals(hits.toString(), 1, hits.size)
        assertTrue(hits.toString(), hits.single().contains("number is not assignable to"))
    }

    @Test
    fun testExplicitNilForRequiredCrossFileParamIsReported() {
        val hits = crossFileHits("open(nil, \"r\")\n")
        assertEquals(hits.toString(), 1, hits.size)
        assertTrue(hits.toString(), hits.single().contains("nil value is not assignable to string"))
    }

    @Test
    fun testExplicitNilForRequiredInFileParamIsReported() {
        val hits = assignabilityHits("$declaration\nopen(nil, \"r\")\n")
        assertEquals(hits.toString(), 1, hits.size)
        assertTrue(hits.toString(), hits.single().contains("nil value is not assignable to string"))
    }
}
