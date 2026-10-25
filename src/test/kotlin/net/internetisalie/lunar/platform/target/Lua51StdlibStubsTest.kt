package net.internetisalie.lunar.platform.target

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndWait
import net.internetisalie.lunar.analysis.inspections.LuaDeprecatedApiInspection
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-513: the bundled `runtime/standard/lua-5.1/` stubs must declare Lua 5.1's API (the 5.1
 * reference manual's function index, plus the compatibility functions shared by PUC 5.1.5 and
 * LuaJIT 2.0/2.1) and nothing else. Resolution is driven through the real `multiResolve` path
 * under a `Target(STANDARD, "5.1")`, so a green assertion is what a 5.1 user actually gets.
 */
@RunWith(JUnit4::class)
class Lua51StdlibStubsTest : IndexedBasePlatformTestCase() {
    override fun tearDown() {
        try {
            useStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testManualFunctionsMissingFromTheStubsResolve() {
        assertResolveCounts(MANUAL_NAMES, expected = 1)
    }

    @Test
    fun testCompatibilityFunctionsResolve() {
        assertResolveCounts(COMPATIBILITY_NAMES, expected = 1)
    }

    @Test
    fun testNamesOutsideLua51DoNotResolve() {
        assertResolveCounts(NON_LUA51_NAMES, expected = 0)
    }

    @Test
    fun testDebugFunctionsAfterGetlocalResolve() {
        assertResolveCounts(DEBUG_NAMES_AFTER_GETLOCAL, expected = 1)
    }

    /**
     * The compatibility functions removed after 5.1 carry `---@deprecated`; `package.config`, which
     * 5.2 documented and code reads for the directory separator, must not. The inspection does not
     * honour `---@deprecated` on a field assignment, so the inspection run cannot see a tag on
     * `package.config`; the stub text is checked for it as well.
     */
    @Test
    fun testOnlyRemovedCompatibilityFunctionsWarnAsDeprecated() {
        val packageConfigComment =
            stubText("package")
                .substringBefore("\npackage.config =")
                .substringAfterLast("\n\n")
        assertFalse("package.config is not deprecated", packageConfigComment.contains("---@deprecated"))
        useStandardTarget("5.1")
        myFixture.enableInspections(LuaDeprecatedApiInspection())
        myFixture.configureByText(
            "bug513_deprecated.lua",
            COMPATIBILITY_NAMES.joinToString(separator = "") { "print($it)\n" },
        )
        val warned =
            myFixture
                .doHighlighting()
                .filter { it.description?.startsWith("Deprecated API") == true }
                .sortedBy { it.startOffset }
                .map { myFixture.file.text.substring(it.startOffset, it.endOffset) }
        assertEquals("names warned as deprecated", listOf("gcinfo", "newproxy", "foreach", "foreachi", "getn"), warned)
    }

    /**
     * `File` is a file-local class, so its methods are checked in the stub text: a colon call
     * through the `---@type File` receiver (`io.stdout:close()`) resolves to nothing even for an
     * existing method, and cannot tell a declared method from a missing one.
     */
    @Test
    fun testIoStubDeclaresSetvbufMethodAndNotIseof() {
        val text = stubText("io")
        assertEquals("occurrences of File:setvbuf", 1, text.split("function File:setvbuf(").size - 1)
        assertFalse("File:iseof is not a Lua 5.1 file method", text.contains("function File:iseof("))
    }

    @Test
    fun testDebugStubParsesWithoutErrors() {
        val psi = myFixture.configureByText("bug513_debug_stub.lua", stubText("debug"))
        val errors =
            runReadActionBlocking {
                PsiTreeUtil.findChildrenOfType(psi, PsiErrorElement::class.java).map { error ->
                    val line = psi.text.substring(0, error.textOffset).count { it == '\n' } + 1
                    "$line: ${error.errorDescription}"
                }
            }
        assertEquals("parse errors in lua-5.1/debug.lua", emptyList<String>(), errors)
    }

    private fun assertResolveCounts(
        names: List<String>,
        expected: Int,
    ) {
        useStandardTarget("5.1")
        val source = names.joinToString(separator = "") { "print($it)\n" }
        myFixture.configureByText("bug513_names.lua", source)
        var lineStart = 0
        val mismatches =
            names.mapNotNull { name ->
                val leafOffset = lineStart + "print(".length + name.lastIndexOf('.') + 1
                lineStart += "print($name)\n".length
                val count = resolveCountAt(leafOffset)
                if (count == expected) null else "$name -> $count"
            }
        assertEquals("names not resolving to exactly $expected target(s)", emptyList<String>(), mismatches)
    }

    private fun resolveCountAt(offset: Int): Int =
        runReadActionBlocking {
            when (val reference = myFixture.file.findReferenceAt(offset)) {
                null -> -1
                is PsiPolyVariantReference -> reference.multiResolve(false).size
                else -> if (reference.resolve() == null) 0 else 1
            }
        }

    private fun useStandardTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, label))
        runInEdtAndWait {
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, version))
            RuntimeLibraryProvider(project).getLibraryRoot(settings.state.getTarget())?.let { root ->
                VfsUtil.collectChildrenRecursively(root)
                VfsUtil.markDirtyAndRefresh(false, true, true, root)
            }
            PlatformLibraryIndex.reload()
        }
    }

    private fun stubText(module: String): String =
        requireNotNull(javaClass.classLoader.getResource("runtime/standard/lua-5.1/$module.lua")).readText()

    private companion object {
        val MANUAL_NAMES =
            listOf(
                "debug.debug",
                "debug.getmetatable",
                "debug.getregistry",
                "debug.getupvalue",
                "debug.setmetatable",
                "debug.setupvalue",
                "math.deg",
                "math.rad",
                "os.clock",
                "os.difftime",
                "os.setlocale",
                "os.tmpname",
                "package.loaders",
                "package.preload",
                "string.gmatch",
                "string.match",
            )

        val COMPATIBILITY_NAMES =
            listOf("gcinfo", "newproxy", "package.config", "table.foreach", "table.foreachi", "table.getn")

        val NON_LUA51_NAMES =
            listOf(
                "debug.getfunc",
                "dostring",
                "io.setvbuf",
                "os.getwd",
                "os.tmpfile",
                "package.preloaded",
                "package.searchers",
                "rawlen",
            )

        val DEBUG_NAMES_AFTER_GETLOCAL =
            listOf(
                "debug.getlocal",
                "debug.setlocal",
                "debug.sethook",
                "debug.gethook",
                "debug.setfenv",
                "debug.getfenv",
                "debug.traceback",
            )
    }
}
