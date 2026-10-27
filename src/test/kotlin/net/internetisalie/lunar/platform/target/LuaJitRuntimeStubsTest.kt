package net.internetisalie.lunar.platform.target

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndWait
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.lang.LuaRequireReference
import net.internetisalie.lunar.lang.path.runtimeRootVerdict
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TARGET-12: the bundled `runtime/luajit/luajit-2.0|2.1` roots, driven through the real
 * resolution, highlighting and completion paths under a `Target(LUAJIT, …)`, plus the
 * runtime-root module rule (design §1.1).
 */
@RunWith(JUnit4::class)
class LuaJitRuntimeStubsTest : IndexedBasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    override fun tearDown() {
        try {
            useTarget(LuaPlatform.STANDARD, "5.4")
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testTc01FixtureAHasNoUndeclaredNamesUnderLuaJit21() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        assertEquals("undeclared names", emptyList<String>(), undeclaredNames(FIXTURE_A))
    }

    @Test
    fun testTc02FixtureAHasNoUndeclaredNamesUnderLuaJit20() {
        useTarget(LuaPlatform.LUAJIT, "2.0")
        assertEquals("undeclared names", emptyList<String>(), undeclaredNames(FIXTURE_A))
    }

    @Test
    fun testTc03JitAndBitStayUndeclaredUnderStandard51() {
        useTarget(LuaPlatform.STANDARD, "5.1")
        assertEquals("undeclared names", listOf("jit", "bit"), undeclaredNames(FIXTURE_A))
    }

    @Test
    fun testTc04StringFormatResolvesIntoTheLuaJit21Copy() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        myFixture.configureByText("tc04.lua", FIXTURE_A)
        assertSingleTargetIn("format", "luajit/luajit-2.1/string.lua")
    }

    @Test
    fun testTc05JitVersionResolvesIntoJitLua() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        myFixture.configureByText("tc05.lua", FIXTURE_A)
        assertSingleTargetIn("version", "luajit-2.1/jit.lua")
    }

    @Test
    fun testTc06RequireFfiResolvesToTheFfiModule() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        myFixture.configureByText("tc06.lua", "local ffi = require(\"ffi\")\nprint(ffi.new)")
        val paths = requirePaths()
        assertEquals("require targets: $paths", 1, paths.size)
        assertTrue("require(\"ffi\") target: $paths", paths.single()?.endsWith("luajit-2.1/ffi.lua") == true)
        assertSingleTargetIn("new", "luajit-2.1/ffi.lua")
    }

    @Test
    fun testTc07BareFfiStaysUndeclared() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        assertEquals("undeclared names", listOf("ffi"), undeclaredNames("print(ffi.new)"))
    }

    @Test
    fun testTc08DottedModulesResolveUnderLuaJit21() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        myFixture.configureByText("tc08.lua", DOTTED_MODULES.joinToString(separator = "\n") { "require(\"$it\")" })
        val expected = DOTTED_MODULES.map { "luajit-2.1/${it.replace('.', '/')}.lua" }
        val paths = requirePaths()
        assertEquals("require targets: $paths", expected.size, paths.size)
        expected.zip(paths).forEach { (suffix, path) ->
            assertTrue("expected …$suffix, got $path", path?.endsWith(suffix) == true)
        }
    }

    @Test
    fun testTc09OnlyJitUtilResolvesUnderLuaJit20() {
        useTarget(LuaPlatform.LUAJIT, "2.0")
        myFixture.configureByText("tc09.lua", DOTTED_MODULES.joinToString(separator = "\n") { "require(\"$it\")" })
        val paths = requirePaths()
        assertEquals("require targets: $paths", DOTTED_MODULES.size, paths.size)
        assertTrue("jit.util target: ${paths.first()}", paths.first()?.endsWith("luajit-2.0/jit/util.lua") == true)
        assertEquals("2.1-only modules under 2.0", listOf(null, null, null, null), paths.drop(1))
    }

    @Test
    fun testTc15JitCompletionOffersItsMembers() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        myFixture.configureByText("tc15.lua", "jit.<caret>")
        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings.orEmpty()
        assertTrue("jit. completion: $offered", offered.containsAll(listOf("version", "on", "status")))
    }

    @Test
    fun testTc16CompatFunctionsResolveUnderLuaJit21() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        myFixture.configureByText("tc16.lua", COMPAT_LINE)
        assertSingleTargetIn("traceback", "luajit-2.1/debug.lua")
        assertSingleTargetIn("upvalueid", "luajit-2.1/debug.lua")
        assertSingleTargetIn("move", "luajit-2.1/table.lua")
        assertSingleTargetIn("isyieldable", "luajit-2.1/coroutine.lua")
    }

    @Test
    fun testTc16Lua53AdditionsDoNotResolveUnderLuaJit20() {
        useTarget(LuaPlatform.LUAJIT, "2.0")
        myFixture.configureByText("tc16_20.lua", COMPAT_LINE)
        assertEquals("table.move targets", emptyList<String>(), targetPaths("move"))
        assertEquals("coroutine.isyieldable targets", emptyList<String>(), targetPaths("isyieldable"))
    }

    @Test
    fun testTc18BareRequiresDoNotLandOnNestedRuntimeFiles() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        val source = BARE_MODULES.joinToString(separator = "\n") { "local _ = require(\"$it\")" }
        myFixture.configureByText("tc18.lua", source)
        val paths = requirePaths()
        assertEquals("require targets: $paths", BARE_MODULES.size, paths.size)
        assertEquals("bare nested names", listOf(null, null, null, null, null), paths.take(5))
        assertTrue("jit.util target: ${paths[5]}", paths[5]?.endsWith("luajit-2.1/jit/util.lua") == true)
        assertTrue("string target: ${paths[6]}", paths[6]?.endsWith("luajit-2.1/string.lua") == true)
    }

    @Test
    fun testTc18ProjectUtilStillWins() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        myFixture.addFileToProject("util.lua", "return {}")
        myFixture.configureByText("tc18_project.lua", "local _ = require(\"util\")")
        assertEquals("require(\"util\") target", listOf("/src/util.lua"), requirePaths())
    }

    @Test
    fun testTc19RuntimeRootVerdict() {
        val tempDir = myFixture.tempDirFixture
        val root = tempDir.findOrCreateDir("vroot")
        val initFile = tempDir.createFile("vroot/pkg/init.lua", "return {}")
        val nested = tempDir.createFile("vroot/jit/util.lua", "return {}")
        val top = tempDir.createFile("vroot/top.lua", "return {}")
        val outside = tempDir.createFile("elsewhere/util.lua", "return {}")
        val verdicts =
            runReadActionBlocking {
                listOf(
                    runtimeRootVerdict(initFile, root, "pkg"),
                    runtimeRootVerdict(initFile, root, "pkg.init"),
                    runtimeRootVerdict(initFile, root, "init"),
                    runtimeRootVerdict(nested, root, "util"),
                    runtimeRootVerdict(nested, root, "jit.util"),
                    runtimeRootVerdict(top, root, "top"),
                    runtimeRootVerdict(outside, root, "util"),
                    runtimeRootVerdict(top, null, "top"),
                )
            }
        assertEquals("verdicts", listOf(true, true, false, false, true, true, null, null), verdicts)
    }

    private fun useTarget(
        platform: LuaPlatform,
        label: String,
    ) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(platform, label))
        runInEdtAndWait {
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(Target(platform, version))
            RuntimeLibraryProvider(project).getLibraryRoot(settings.state.getTarget())?.let { root ->
                VfsUtil.collectChildrenRecursively(root)
                VfsUtil.markDirtyAndRefresh(false, true, true, root)
            }
            PlatformLibraryIndex.reload()
        }
    }

    /** The names reported as `Undeclared variable '<name>'` in [text], in document order. */
    private fun undeclaredNames(text: String): List<String> {
        myFixture.configureByText("undeclared.lua", text)
        return myFixture
            .doHighlighting()
            .filter { it.description?.startsWith("Undeclared variable") == true }
            .sortedBy { it.startOffset }
            .mapNotNull { it.description?.substringAfter('\'')?.substringBefore('\'') }
    }

    private fun assertSingleTargetIn(
        member: String,
        pathSuffix: String,
    ) {
        val paths = targetPaths(member)
        assertEquals("targets of $member: $paths", 1, paths.size)
        assertTrue("$member target: $paths", paths.single().endsWith(pathSuffix))
    }

    /** File paths of every `multiResolve` target of the first name reference spelled [member]. */
    private fun targetPaths(member: String): List<String> =
        runReadActionBlocking {
            val offset = Regex("\\b${Regex.escape(member)}\\b").find(myFixture.file.text)?.range?.first ?: -1
            val reference = myFixture.file.findReferenceAt(offset) as? PsiPolyVariantReference
            reference
                ?.multiResolve(false)
                ?.mapNotNull {
                    it.element
                        ?.containingFile
                        ?.virtualFile
                        ?.path
                }.orEmpty()
        }

    /** The resolved file path of every `require` reference in the file, in document order. */
    private fun requirePaths(): List<String?> =
        runReadActionBlocking {
            PsiTreeUtil
                .collectElements(myFixture.file) { true }
                .flatMap { it.references.asIterable() }
                .filterIsInstance<LuaRequireReference>()
                .map {
                    it
                        .resolve()
                        ?.containingFile
                        ?.virtualFile
                        ?.path
                }
        }

    private companion object {
        const val FIXTURE_A =
            "local v = jit.version\nlocal b = bit.band(1, 2)\nprint(string.format(\"%d\", b), table.concat({}), v)"

        const val COMPAT_LINE =
            "print(debug.traceback(), debug.upvalueid(print, 1), table.move, coroutine.isyieldable)"

        val DOTTED_MODULES = listOf("jit.util", "jit.profile", "string.buffer", "table.new", "table.clear")

        val BARE_MODULES = listOf("util", "new", "clear", "buffer", "profile", "jit.util", "string")
    }
}
