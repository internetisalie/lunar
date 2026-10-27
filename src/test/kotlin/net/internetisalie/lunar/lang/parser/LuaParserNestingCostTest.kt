package net.internetisalie.lunar.lang.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.impl.PsiBuilderAdapter
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaParserDefinition
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * BUG-517: parsing work must grow linearly with expression nesting depth.
 *
 * Counts `PsiBuilder.mark()` calls (every parser section opens one), so the check is
 * deterministic: a parser that re-parses a nested expression k times per level makes
 * `marks(6) / marks(3)` about k^3, while a linear parser keeps it under 2. Every shape is
 * checked and all super-linear ones are reported together.
 */
class LuaParserNestingCostTest : BaseDocumentTest() {
    private class MarkCountingBuilder(
        delegate: PsiBuilder,
    ) : PsiBuilderAdapter(delegate) {
        var marks = 0

        override fun mark(): PsiBuilder.Marker {
            marks++
            return super.mark()
        }
    }

    private val shapes: Map<String, (Int) -> String> =
        mapOf(
            "parens" to { d -> "local x = " + "(".repeat(d) + "a" + ")".repeat(d) },
            "calls" to { d -> "local x = " + "f(".repeat(d) + "a" + ")".repeat(d) },
            "index" to { d -> "local x = " + "t[".repeat(d) + "a" + "]".repeat(d) },
            "callMulParens" to { d -> "local x = " + "f(a * (".repeat(d) + "b" + "))".repeat(d) },
            "callbacks" to { d -> "f(function()\n".repeat(d) + "x()\n" + "end)\n".repeat(d) },
        )

    private fun marksToParse(text: String): Int {
        val definition = LuaParserDefinition()
        val lexer = definition.createLexer(myFixture.project)
        val builder = MarkCountingBuilder(PsiBuilderFactory.getInstance().createBuilder(definition, lexer, text))
        LuaParser().parseLight(definition.fileNodeType, builder)
        return builder.marks
    }

    @Test
    fun testParseWorkIsLinearInNestingDepth() {
        val superLinear =
            shapes.mapNotNull { (shape, text) ->
                val shallow = marksToParse(text(3))
                val deep = marksToParse(text(6))
                "$shape: depth 3 took $shallow marks, depth 6 took $deep".takeIf { deep >= 2 * shallow }
            }
        Assertions.assertTrue(superLinear.isEmpty(), superLinear.joinToString("; "))
    }
}
