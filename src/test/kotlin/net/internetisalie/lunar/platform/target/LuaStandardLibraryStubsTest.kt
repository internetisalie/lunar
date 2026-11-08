package net.internetisalie.lunar.platform.target

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.runInEdtAndWait
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-46: the bundled Lua 5.2–5.4 stubs for `math`, `string`, `os`, `debug`, `coroutine`, `utf8` and
 * `bit32` declare each version's library, so a call resolves into that version's stub with its
 * signature, and a function the version's manual no longer documents does not resolve. The basic
 * library follows the manual too: no `getfenv`/`setfenv` after 5.1, and `warn` on 5.4.
 *
 * TARGET-07: Lua 5.5 has its own stub set, the 5.4 set plus the 5.5 manual's changes (`table.create`,
 * `math.frexp`/`math.ldexp`, an optional `coroutine.close` argument, a second `utf8.offset` result,
 * `_VERSION`), and nothing the 5.5 manual does not list.
 *
 * A signature is asserted as the engine renders a cross-file member, which shows `integer` as `number`.
 */
@RunWith(JUnit4::class)
class LuaStandardLibraryStubsTest : IndexedBasePlatformTestCase() {
    override fun tearDown() {
        try {
            useTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testCommonFunctionsResolveOnEveryLevel() {
        listOf("5.2", "5.3", "5.4", "5.5").forEach { label ->
            useTarget(label)
            myFixture.configureByText("common.lua", COMMON_CALLS)
            listOf("format" to "string", "floor" to "math", "time" to "os").forEach { (member, module) ->
                assertSingleTargetIn(member, "lua-$label/$module.lua")
            }
        }
    }

    @Test
    fun testLua52DeclaresItsAdditionsAndDropsTheRemovedFunctions() {
        useTarget("5.2")
        assertSignature("debug.upvalueid", "fun(f: function, n: number): userdata")
        assertSignature("bit32.extract", "fun(n: number, field: number, width?: number): number")
        assertSignature("os.exit", "fun(code?: boolean | number, close?: boolean): unknown")
        assertUnresolved(
            "local a, b, c = debug.setfenv, string.gfind, math.tointeger",
            listOf("setfenv", "gfind", "tointeger"),
        )
        assertUnresolved("local a, b = getfenv, setfenv", listOf("getfenv", "setfenv"))
    }

    @Test
    fun testLua53DeclaresItsAdditionsAndDropsTheRemovedFunctions() {
        useTarget("5.3")
        assertSignature("math.tointeger", "fun(x: any): nil | number")
        assertSignature("utf8.offset", "fun(s: string, n: number, i?: number): nil | number")
        assertSignature("string.dump", "fun(f: function, strip?: boolean): string")
        assertUnresolved("local a, b, c = math.pow, math.atan2, coroutine.close", listOf("pow", "atan2", "close"))
        assertUnresolved("local a, b = getfenv, setfenv", listOf("getfenv", "setfenv"))
    }

    @Test
    fun testLua54DeclaresItsAdditionsAndDropsTheRemovedFunctions() {
        useTarget("5.4")
        assertSignature("coroutine.close", "fun(co: thread): boolean")
        assertGlobalResolvesIn("warn", "lua-5.4/builtin.lua")
        assertSignature("math.randomseed", "fun(x?: number, y?: number): number")
        assertSignature("utf8.len", "fun(s: string, i?: number, j?: number, lax?: boolean): nil | number")
        assertUnresolved("local a, b, c = math.pow, math.log10, string.gfind", listOf("pow", "log10", "gfind"))
        assertUnresolved("local a, b = table.create, math.frexp", listOf("create", "frexp"))
    }

    @Test
    fun testLua55DeclaresItsAdditionsAndDropsTheRemovedFunctions() {
        useTarget("5.5")
        assertSignature("table.create", "fun(nseq: number, nrec?: number): {  }")
        assertSignature("math.frexp", "fun(x: number): number")
        assertSignature("math.ldexp", "fun(m: number, e: number): number")
        assertSignature("coroutine.close", "fun(co?: thread): boolean")
        assertSignature("utf8.offset", "fun(s: string, n: number, i?: number): nil | number")
        assertUnresolved("local a, b, c = math.pow, math.log10, io.setvbuf", listOf("pow", "log10", "setvbuf"))
    }

    @Test
    fun testLua55ResolvesTheBasicIoAndTableLibraries() {
        useTarget("5.5")
        assertGlobalResolvesIn("print", "lua-5.5/builtin.lua")
        assertMemberResolvesIn("io.open", "lua-5.5/io.lua")
        assertMemberResolvesIn("table.insert", "lua-5.5/table.lua")
        assertMemberResolvesIn("table.create", "lua-5.5/table.lua")
    }

    @Test
    fun testLua55StubSetMirrors54WithItsOwnVersionString() {
        val provider = RuntimeLibraryProvider(project)
        val names =
            listOf("5.4", "5.5").map { label ->
                provider.getLibraryFiles(targetFor(label)).map { it.name }.toSet()
            }
        assertEquals("lua-5.5 file names", names[0], names[1])
        assertTrue(
            "_VERSION in lua-5.5/builtin.lua",
            resourceText("lua-5.5/builtin.lua").contains("_VERSION = \"Lua 5.5\""),
        )
        names[1].forEach { name ->
            assertTrue("copyright in $name", header("lua-5.5/$name").contains(COPYRIGHT))
            assertEquals("licence header of $name", header("lua-5.4/$name"), header("lua-5.5/$name"))
        }
    }

    private fun targetFor(label: String): Target =
        Target(LuaPlatform.STANDARD, requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, label)))

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.classLoader.getResource("runtime/standard/$path")) { path }.readText()

    /** The licence block: every line before `---@meta`. */
    private fun header(path: String): String = resourceText(path).substringBefore("---@meta")

    private fun useTarget(label: String) {
        runInEdtAndWait {
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(targetFor(label))
            RuntimeLibraryProvider(project).getLibraryRoot(settings.state.getTarget())?.let { root ->
                VfsUtil.collectChildrenRecursively(root)
                VfsUtil.markDirtyAndRefresh(false, true, true, root)
            }
            PlatformLibraryIndex.reload()
        }
    }

    /** [qualified] (`module.member`) is a member of the module's stub type, rendered as [expected]. */
    private fun assertSignature(
        qualified: String,
        expected: String,
    ) {
        val (module, member) = qualified.split('.')
        myFixture.configureByText("signature.lua", "local f = $qualified\n")
        val rendered =
            runReadActionBlocking {
                LuaTypeManager
                    .getInstance(project)
                    .resolveGlobal(module, myFixture.file)
                    ?.resolveMember(member)
                    ?.type
                    ?.name
            }
        assertEquals("signature of $qualified", expected, rendered)
        assertTrue("$qualified resolves", targetPaths(member).isNotEmpty())
    }

    private fun assertMemberResolvesIn(
        qualified: String,
        pathSuffix: String,
    ) {
        myFixture.configureByText("member.lua", "local f = $qualified\n")
        assertSingleTargetIn(qualified.substringAfter('.'), pathSuffix)
    }

    private fun assertGlobalResolvesIn(
        name: String,
        pathSuffix: String,
    ) {
        myFixture.configureByText("global.lua", "$name(\"message\")\n")
        assertSingleTargetIn(name, pathSuffix)
    }

    private fun assertUnresolved(
        source: String,
        members: List<String>,
    ) {
        myFixture.configureByText("removed.lua", source)
        members.forEach { assertEquals("targets of removed $it", emptyList<String>(), targetPaths(it)) }
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

    private companion object {
        const val COPYRIGHT = "Copyright © 1994–2025 Lua.org, PUC-Rio."
        const val COMMON_CALLS = "print(string.format(\"%d\", 1), math.floor(1.5), os.time())\n"
    }
}
