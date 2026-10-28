package net.internetisalie.lunar.corpus

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-515: a corpus member pinned to `LUA51` must be measured against its target's runtime —
 * `runtime/standard/lua-5.1`, or the manifest's `target` (MAINT-39).
 *
 * Each case starts from a STORED Standard 5.4 target, the state the shared light project is in
 * when the sweep reaches its first member. Writing `languageLevel` alone leaves that target, and
 * with it the `lua-5.4` stdlib root, in place.
 *
 * Routine suite, deliberately: it never reads the corpus, and its name stays outside `*Corpus*`.
 */
@RunWith(JUnit4::class)
class MemberTargetPinTest : BasePlatformTestCase() {
    private var initial: SavedTarget? = null

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
        initial = CorpusTarget.pin(project, LuaLanguageLevel.LUA54)
        // The sweep's starting state, as probed: a stored target in Auto mode.
        LuaProjectSettings.getInstance(project).state.explicitTarget = false
        assertEquals("precondition: a stored 5.4 target", "lua-5.4", stdlibRootName())
    }

    override fun tearDown() {
        try {
            initial?.let { CorpusTarget.restore(project, it) }
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testPinMovesTheStdlibRootOffAStoredTarget() {
        CorpusTarget.pin(project, LuaLanguageLevel.LUA51)
        assertEquals("lua-5.1", stdlibRootName())
        assertEquals(LuaLanguageLevel.LUA51, LuaProjectSettings.getInstance(project).state.languageLevel)
    }

    @Test
    fun testPinnedMemberSeesTheLua51Globals() {
        CorpusTarget.pin(project, LuaLanguageLevel.LUA51)
        assertEquals(listOf("rawlen"), undeclaredNames("print(rawlen, setfenv, unpack, newproxy)\n"))
    }

    @Test
    fun testRestorePutsTheStoredTargetBack() {
        CorpusTarget.restore(project, CorpusTarget.pin(project, LuaLanguageLevel.LUA51))
        val state = LuaProjectSettings.getInstance(project).state
        assertEquals("lua-5.4", stdlibRootName())
        assertEquals(LuaLanguageLevel.LUA54, state.languageLevel)
        assertFalse("explicitTarget must be restored", state.explicitTarget)
    }

    /** MAINT-39: a manifest `target` reaches the pin, so a LuaJIT member sees `jit` and `bit`. */
    @Test
    fun testManifestTargetPinsTheLuaJitRuntime() {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.LUAJIT, "2.1"))
        val entry =
            CorpusEntry(
                name = "jit",
                commit = "cafebabe",
                roots = listOf("src"),
                luaLevel = LuaLanguageLevel.LUA51,
                moduleRoot = null,
                target = Target(LuaPlatform.LUAJIT, version),
            )
        CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))
        assertEquals("luajit-2.1", stdlibRootName())
        assertEquals(listOf("rawlen"), undeclaredNames("print(jit, bit, setfenv, rawlen)\n"))
    }

    private fun stdlibRootName(): String? = PlatformLibraryIndex.getPlatformLibraryFolder(project)?.name

    /** The names reported as `Undeclared variable '<name>'` in [text], in document order. */
    private fun undeclaredNames(text: String): List<String> {
        myFixture.configureByText("member.lua", text)
        return myFixture
            .doHighlighting()
            .filter { it.description?.startsWith("Undeclared variable") == true }
            .sortedBy { it.startOffset }
            .mapNotNull { it.description?.substringAfter('\'')?.substringBefore('\'') }
    }
}
