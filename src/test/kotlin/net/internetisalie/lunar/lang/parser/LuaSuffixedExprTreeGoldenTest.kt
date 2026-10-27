package net.internetisalie.lunar.lang.parser

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.DebugUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaLanguage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * BUG-517: the single-pass suffixed-expression parser must build exactly the trees the former
 * ordered choice (`funcCall | prefixExpr`, `assignmentStatement` before `exprStatement`) built.
 *
 * [GOLDEN_PATH] was recorded from the parser BEFORE the fix (main at `a5be6b08`) and is compared
 * byte for byte, error elements and their messages included. The snippets cover every suffix
 * combination on name and paren primaries, assignment targets, comments and newlines inside a
 * chain, callback statements, `global` declarations, and broken input. Each block is
 * `=== <snippet, newlines escaped>` followed by `DebugUtil.psiToString(file, false, true)`.
 * A difference is a tree-shape regression unless it is argued otherwise before re-recording.
 */
class LuaSuffixedExprTreeGoldenTest : BaseDocumentTest() {
    private fun dump(snippet: String): String =
        runReadActionBlocking {
            val file = PsiFileFactory.getInstance(myFixture.project).createFileFromText("s.lua", LuaLanguage, snippet)
            "=== ${snippet.replace("\n", "\\n")}\n" + DebugUtil.psiToString(file, false, true)
        }

    @Test
    fun testTreesMatchThePreFixParser() {
        val golden = File(System.getProperty("user.dir"), GOLDEN_PATH)
        Assertions.assertTrue(golden.isFile, "the golden is missing at ${golden.path}")
        val recorded = golden.readText()
        val rendered = SNIPPETS.joinToString("\n") { dump(it) }
        val moved = blocks(rendered) - blocks(recorded).toSet()
        Assertions.assertEquals(
            recorded,
            rendered,
            "trees moved for ${moved.size} snippet(s): " + moved.joinToString("; ") { it.substringBefore('\n') },
        )
    }

    private fun blocks(dumps: String): List<String> = dumps.split(BLOCK_START).filter { it.isNotEmpty() }

    private companion object {
        const val GOLDEN_PATH = "src/test/resources/parser/suffixed-expr-trees.golden"
        val BLOCK_START = Regex("(?m)^(?==== )")

        val SNIPPETS =
            listOf(
                "x = a",
                "x = a.b",
                "x = a[b]",
                "x = a.b.c",
                "x = a()",
                "x = a.b()",
                "x = a:b()",
                "x = a:b():c()",
                "x = a(b).c",
                "x = a(b).c(d)",
                "x = a(b)(c)",
                "x = a(b)(c).d",
                "x = a.b(c).d(e).f",
                "x = (a)",
                "x = (a).b",
                "x = (a)[b]",
                "x = (a)()",
                "x = (a)(b).c",
                "x = (a)(b).c(d)",
                "x = (a).b:c(d)",
                "x = ((a))",
                "x = (f(a))",
                "x = f{}",
                "x = f\"s\"",
                "x = f[[s]]",
                "x = f{a}.b",
                "x = a.b \"s\" {c}",
                "f(a)",
                "a.b(c)",
                "(f)(a)",
                "a:b()",
                "a.b.c = 1",
                "(a).b = 1",
                "a(b).c = 1",
                "x = - a.b(c)",
                "x = not (a)(b)",
                "x = a.b + c(d) * (e)",
                "x = #a[b]",
                "x = a .. b(c) .. (d)",
                "-- c1\nx = a -- c2\n  . b --[[c3]] (c) -- c4\n",
                "x = a\n.b\n(c)\n[d]",
                "x = (a",
                "x = a.",
                "x = a:b",
                "x = a(",
                "x = a[",
                "x = (a).",
                "x = (a)(",
                "x = f(a,",
                "x = a.b(c",
                "x = (",
                "x = ((a)",
                "x = a(b))",
                "x = a.b..",
                "f(a",
                "a:",
                "(a",
                "(a).",
                "a.b(c) d",
                "x = a b",
                "x = a:b c",
                "local function f() return a(b).c end",
                "if a(b) then end",
                "for i = f(a), g(b) do end",
                "x = f(function() return (a) end)(b)",
                "x = {a(b), [c(d)] = (e), f = g.h}",
                "global x = f(a).b",
                "global function g() return f(a).b end",
                "f(function() g(function() x() end) end)",
                "o:m(\"s\", function() end)",
                "x = f(function() end)",
                "a, b = f(function() end), 2",
                "f(function() end).x = 1",
                "(f)(function() end).x, y = 1, 2",
                "f() = 1",
                "a.b(c) = 1",
                "f(a), g(b)",
                "x.y, f() = 1",
                "a, = 1",
                "f(function() x = end)",
            )
    }
}
