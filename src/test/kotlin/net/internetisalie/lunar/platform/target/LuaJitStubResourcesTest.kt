package net.internetisalie.lunar.platform.target

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * TARGET-12: the bundled `runtime/luajit/` resources, checked as text. The stdlib copies must be
 * byte-exact rebuilds of `runtime/standard/lua-5.1/` plus the design §5 edits (TC 11), so a fix to
 * either side that is not mirrored on the other fails here.
 */
@RunWith(JUnit4::class)
class LuaJitStubResourcesTest : BasePlatformTestCase() {
    @Test
    fun testTc11StdlibCopiesRebuildFromLua51() {
        val mismatches =
            VERSIONS.flatMap { version ->
                STDLIB_FILES
                    .filter { file -> rebuild(version, file) != copyText(version, "$file.lua") }
                    .map { file -> "luajit-$version/$file.lua" }
            }
        assertEquals("stdlib copies that differ from their lua-5.1 rebuild", emptyList<String>(), mismatches)
    }

    @Test
    fun testTc10SpecificFilesDeclareExactlyTheDesignedNames() {
        val mismatches =
            SPECIFIC_NAMES.flatMap { (version, files) ->
                files.mapNotNull { (file, expected) ->
                    val actual = declaredNames(copyText(version, file))
                    val extra = actual - expected
                    val missing = expected - actual
                    if (actual == expected) null else "luajit-$version/$file: $extra extra, $missing missing"
                }
            }
        assertEquals("declared-name mismatches", emptyList<String>(), mismatches)
    }

    @Test
    fun testTc12SpecificFilesParseWithoutErrors() {
        val failures =
            SPECIFIC_NAMES.flatMap { (version, files) ->
                files.keys.mapNotNull { file -> parseErrors(version, file).takeIf { it.isNotEmpty() } }
            }
        assertEquals("parse errors", emptyList<String>(), failures)
    }

    @Test
    fun testTc13SpecificFilesStartWithTheLuaJitHeader() {
        val failures =
            SPECIFIC_NAMES.flatMap { (version, files) ->
                files.keys
                    .filter { file ->
                        copyText(version, file).lines().take(HEADER.size) != HEADER
                    }.map { file -> "luajit-$version/$file" }
            }
        assertEquals("files without the exact LuaJIT header", emptyList<String>(), failures)
    }

    @Test
    fun testTc17ThirdPartyNoticesCoverTheLuaJitRoots() {
        val notices = File(System.getProperty("user.dir"), "THIRD-PARTY.md").readText()
        val missing = THIRD_PARTY_LINES.filterNot { notices.contains(it) }
        assertEquals("THIRD-PARTY.md lines missing", emptyList<String>(), missing)
        val luaLicenseEntry = notices.substringAfter("- **MIT (Lua)**").substringBefore("\n- ")
        assertTrue(
            "MIT (Lua) entry names the LuaJIT copies: $luaLicenseEntry",
            luaLicenseEntry.contains("runtime/luajit/"),
        )
    }

    private fun parseErrors(
        version: String,
        file: String,
    ): String {
        val fixtureName = "tc12_${version}_${file.replace('/', '_')}"
        val psiFile = myFixture.configureByText(fixtureName, copyText(version, file))
        val errors =
            runReadActionBlocking {
                PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java).map { error ->
                    "${psiFile.text.substring(0, error.textOffset).count { it == '\n' } + 1}: ${error.errorDescription}"
                }
            }
        return if (errors.isEmpty()) "" else "luajit-$version/$file $errors"
    }

    private fun declaredNames(text: String): Set<String> =
        text
            .lines()
            .mapNotNull { line -> DECLARATION_PATTERNS.firstNotNullOfOrNull { it.find(line)?.groupValues?.get(1) } }
            .toSet()

    private fun rebuild(
        version: String,
        file: String,
    ): String =
        STDLIB_EDITS
            .filter { it.file == file && version in it.versions }
            .fold(resourceText("standard/lua-5.1/$file.lua")) { text, edit -> applyEdit(text, edit) }

    private fun applyEdit(
        text: String,
        edit: StdlibEdit,
    ): String {
        val old = edit.old ?: return text + edit.new
        assertEquals("occurrences of the ${edit.file} anchor plus one", 2, text.split(old).size)
        return text.replace(old, edit.new)
    }

    private fun copyText(
        version: String,
        file: String,
    ): String = resourceText("luajit/luajit-$version/$file")

    private fun resourceText(relativePath: String): String =
        requireNotNull(LuaJitStubResourcesTest::class.java.classLoader.getResource("runtime/$relativePath")) {
            "missing resource runtime/$relativePath"
        }.readText()

    private data class StdlibEdit(
        val file: String,
        val versions: Set<String>,
        val old: String?,
        val new: String,
    )

    private companion object {
        val VERSIONS = listOf("2.0", "2.1")
        val BOTH = setOf("2.0", "2.1")
        val ONLY_21 = setOf("2.1")

        val STDLIB_FILES =
            listOf("builtin", "coroutine", "debug", "io", "math", "os", "package", "string", "table")

        fun lines(vararg text: String): String = text.joinToString(separator = "") { "$it\n" }

        val THIRD_PARTY_LINES =
            listOf(
                "### LuaJIT API stubs (Mike Pall)",
                "`src/main/resources/runtime/luajit/luajit-2.{0,1}/" +
                    "{builtin,coroutine,debug,io,math,os,package,string,table}.lua` (copies of `lua-5.1`)",
                "- **MIT (LuaJIT)** — the full notice is preserved at the top of each LuaJIT-specific stub file " +
                    "under `src/main/resources/runtime/luajit/`.",
            )

        val DECLARATION_PATTERNS =
            listOf(
                Regex("^function\\s+([A-Za-z_][A-Za-z0-9_.:]*)\\s*\\("),
                Regex("^([A-Za-z_][A-Za-z0-9_.]*)\\s*="),
                Regex("^local\\s+function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\("),
            )

        val HEADER =
            listOf(
                "-- LuaJIT -- a Just-In-Time Compiler for Lua. https://luajit.org/",
                "--",
                "-- Copyright (C) 2005-2026 Mike Pall. All rights reserved.",
                "--",
                "-- Permission is hereby granted, free of charge, to any person obtaining a copy",
                "-- of this software and associated documentation files (the \"Software\"), to deal",
                "-- in the Software without restriction, including without limitation the rights",
                "-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell",
                "-- copies of the Software, and to permit persons to whom the Software is",
                "-- furnished to do so, subject to the following conditions:",
                "--",
                "-- The above copyright notice and this permission notice shall be included in",
                "-- all copies or substantial portions of the Software.",
                "--",
                "-- THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR",
                "-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,",
                "-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE",
                "-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER",
                "-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,",
                "-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN",
                "-- THE SOFTWARE.",
                "--",
                "-- API stubs for Lunar, describing the API documented at https://luajit.org/.",
                "",
                "---@meta",
            )

        val JIT_COMMON =
            setOf(
                "jit",
                "jit.version",
                "jit.version_num",
                "jit.os",
                "jit.arch",
                "jit.opt",
                "jit.on",
                "jit.off",
                "jit.flush",
                "jit.status",
                "jit.attach",
                "jit.opt.start",
            )

        val BIT_NAMES =
            setOf("bit") +
                listOf(
                    "tobit",
                    "tohex",
                    "bnot",
                    "bor",
                    "band",
                    "bxor",
                    "lshift",
                    "rshift",
                    "arshift",
                    "rol",
                    "ror",
                    "bswap",
                ).map { "bit.$it" }

        val FFI_COMMON =
            setOf("cb:free", "cb:set", "ffi.C", "ffi.os", "ffi.arch") +
                listOf(
                    "cdef",
                    "load",
                    "new",
                    "typeof",
                    "cast",
                    "metatype",
                    "gc",
                    "sizeof",
                    "alignof",
                    "offsetof",
                    "istype",
                    "errno",
                    "string",
                    "copy",
                    "fill",
                    "abi",
                ).map { "ffi.$it" }

        val JIT_UTIL_NAMES =
            listOf(
                "funcinfo",
                "funcbc",
                "funck",
                "funcuvname",
                "traceinfo",
                "traceir",
                "tracek",
                "tracesnap",
                "tracemc",
                "traceexitstub",
                "ircalladdr",
            ).map { "util.$it" }.toSet()

        val BUFFER_NAMES =
            listOf(
                "reset",
                "free",
                "put",
                "putf",
                "putcdata",
                "set",
                "reserve",
                "commit",
                "skip",
                "get",
                "tostring",
                "ref",
                "encode",
                "decode",
            ).map { "buf:$it" }.toSet() + setOf("buffer.new", "buffer.encode", "buffer.decode")

        val SPECIFIC_NAMES: Map<String, Map<String, Set<String>>> =
            mapOf(
                "2.0" to
                    mapOf(
                        "jit.lua" to JIT_COMMON + "jit.util",
                        "bit.lua" to BIT_NAMES,
                        "ffi.lua" to FFI_COMMON,
                        "jit/util.lua" to JIT_UTIL_NAMES,
                    ),
                "2.1" to
                    mapOf(
                        "jit.lua" to JIT_COMMON + "jit.security",
                        "bit.lua" to BIT_NAMES,
                        "ffi.lua" to FFI_COMMON + "ffi.typeinfo",
                        "jit/util.lua" to JIT_UTIL_NAMES,
                        "jit/profile.lua" to setOf("profile.start", "profile.stop", "profile.dumpstack"),
                        "string/buffer.lua" to BUFFER_NAMES,
                        "table/new.lua" to setOf("new"),
                        "table/clear.lua" to setOf("clear"),
                    ),
            )

        val STDLIB_EDITS =
            listOf(
                StdlibEdit(
                    "builtin",
                    BOTH,
                    lines(
                        "---Loads Lua code from reader function",
                        "---@param reader fun(): string|nil",
                        "---@param chunkname? string",
                        "---@return function|nil, string|nil",
                        "function load(reader, chunkname) end",
                    ),
                    lines(
                        "---Loads a chunk from a string or a reader function; " +
                            "LuaJIT accepts the Lua 5.2 mode and env arguments",
                        "---@param chunk string|fun(): string|nil",
                        "---@param chunkname? string",
                        "---@param mode? string",
                        "---@param env? table",
                        "---@return function|nil, string|nil",
                        "function load(chunk, chunkname, mode, env) end",
                    ),
                ),
                StdlibEdit(
                    "builtin",
                    BOTH,
                    lines(
                        "---Loads Lua code from file",
                        "---@overload fun(): function",
                        "---@overload fun(filename: string): function|nil",
                        "---@param filename? string",
                        "---@return function|nil, string|nil",
                        "function loadfile(filename) end",
                    ),
                    lines(
                        "---Loads a chunk from a file, or from stdin when filename is nil; " +
                            "LuaJIT accepts the Lua 5.2 mode and env arguments",
                        "---@param filename? string",
                        "---@param mode? string",
                        "---@param env? table",
                        "---@return function|nil, string|nil",
                        "function loadfile(filename, mode, env) end",
                    ),
                ),
                StdlibEdit(
                    "math",
                    BOTH,
                    lines("---@param x number", "---@return number", "function math.log(x) end"),
                    lines(
                        "---@param x number",
                        "---@param base? number",
                        "---@return number",
                        "function math.log(x, base) end",
                    ),
                ),
                StdlibEdit(
                    "string",
                    BOTH,
                    lines("---@param n integer", "---@return string", "function string.rep(s, n) end"),
                    lines(
                        "---@param n integer",
                        "---@param sep? string",
                        "---@return string",
                        "function string.rep(s, n, sep) end",
                    ),
                ),
                StdlibEdit(
                    "os",
                    BOTH,
                    lines("---@param code? boolean|integer", "function os.exit(code) end"),
                    lines(
                        "---@param code? boolean|integer",
                        "---@param close? boolean",
                        "function os.exit(code, close) end",
                    ),
                ),
                StdlibEdit(
                    "package",
                    BOTH,
                    null,
                    lines(
                        "",
                        "---Searches path for name, replacing each sep in name with rep " +
                            "(default: the directory separator)",
                        "---@param name string",
                        "---@param path string",
                        "---@param sep? string",
                        "---@param rep? string",
                        "---@return string|nil filename",
                        "---@return string|nil errmsg",
                        "function package.searchpath(name, path, sep, rep) end",
                    ),
                ),
                StdlibEdit(
                    "debug",
                    BOTH,
                    null,
                    lines(
                        "",
                        "---Returns a unique identifier for upvalue n of function f",
                        "---@param f function",
                        "---@param n integer",
                        "---@return lightuserdata",
                        "function debug.upvalueid(f, n) end",
                        "",
                        "---Makes upvalue n1 of closure f1 refer to upvalue n2 of closure f2",
                        "---@param f1 function",
                        "---@param n1 integer",
                        "---@param f2 function",
                        "---@param n2 integer",
                        "function debug.upvaluejoin(f1, n1, f2, n2) end",
                    ),
                ),
                StdlibEdit(
                    "table",
                    ONLY_21,
                    null,
                    lines(
                        "",
                        "---Moves elements a1[f..e] to a2[t..] and returns a2 (a2 defaults to a1)",
                        "---@param a1 table",
                        "---@param f integer",
                        "---@param e integer",
                        "---@param t integer",
                        "---@param a2? table",
                        "---@return table",
                        "function table.move(a1, f, e, t, a2) end",
                    ),
                ),
                StdlibEdit(
                    "coroutine",
                    ONLY_21,
                    null,
                    lines(
                        "",
                        "---Returns true when the running coroutine can yield",
                        "---@return boolean",
                        "function coroutine.isyieldable() end",
                    ),
                ),
            )
    }
}
