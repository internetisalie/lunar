package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndWait
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TYPE-12 — the requirements cases that assert one fixture's errors, types or closure.
 *
 * Each method is named after its requirements case number, so a failure names the case. Fixture
 * text is design Appendix C's, from [TypeGoldenFixtures]; every `(start,end)` below is an offset
 * into that exact text. Errors are rendered `SEVERITY|message|(start,end)` and compared as sorted
 * lists, because the requirement is on the multiset, not the order.
 */
class LuaMemberDemandHubTest : BaseDocumentTest() {
    @Test
    fun case01ClosureGrowsLinearly() {
        val closures = listOf(40, 80, 160).map { n -> closureOf(TypeGoldenFixtures.annotated(n)) }

        assertEquals(listOf(1123, 2203, 4363), closures)
        assertTrue(closures[1] <= closures[0] * 2.2 && closures[2] <= closures[1] * 2.2, "ratio > 2.2: $closures")
        assertTrue(closures[2] <= 5000, "closure at n = 160 above 5 000: ${closures[2]}")
    }

    @Test
    fun case02ReplicatedHypothesesKeepTheAnnotated3List() {
        assertEquals(ANNOTATED3_ERRORS.sorted(), errorsOf(TypeGoldenFixtures.annotated(3)))
    }

    @Test
    fun case03ReverseMemberEdgeKeepsTheAnnotated3List() {
        assertEquals(ANNOTATED3_ERRORS.sorted(), errorsOf(TypeGoldenFixtures.annotated(3)))
    }

    @Test
    fun case04ReplicatedHypothesesKeepTheChainList() {
        assertEquals(CHAIN_ERRORS.sorted(), errorsOf(shape("chain")))
    }

    /**
     * The three `Pipe | nil is not assignable to { ... }` hypotheses this case used to pin came from
     * `fh:read()` failing `Pipe:read(fmt?)`'s declared arity, which ignored the `?` (BUG-554). With
     * the marker honoured, `Pipe` satisfies every site's demand and the fixture reports nothing.
     */
    @Test
    fun case05PerSiteCallShapeAddsNoPipeHypothesis() {
        assertEquals(emptyList(), errorsOf(shape("pipeUnion")))
    }

    @Test
    fun case06ChainTypesAreUnchanged() {
        val types =
            withSnapshot(shape("chain")) { file, snapshot ->
                listOf(
                    snapshot.getValueType(elementAt(file, TextSpan(162, 163, LuaNameRef::class.java))),
                    snapshot.getValueType(elementAt(file, TextSpan(114, 115, LuaNameRef::class.java))),
                    snapshot.getValueType(elementAt(file, TextSpan(129, 137, LuaFuncCall::class.java))),
                ).map { it.displayName() }
            }

        assertEquals(listOf("Bd | Bd | { ... }", "Bd | Bd | { ... }", "Bd | { ... } | Bd"), types)
    }

    @Test
    fun case07SingleSiteClosureIsUnchanged() {
        assertEquals(70, closureOf(TypeGoldenFixtures.annotated(1)))
    }

    private class TextSpan<T : PsiElement>(
        val start: Int,
        val end: Int,
        val psiClass: Class<T>,
    )

    private fun <R> withSnapshot(
        text: String,
        body: (PsiFile, LuaTypesSnapshot) -> R,
    ): R {
        var result: R? = null
        runInEdtAndWait {
            val file = myFixture.configureByText("test.lua", text)
            result = body(file, LuaTypesSnapshot.forFile(file) as LuaTypesSnapshot)
        }
        return checkNotNull(result)
    }

    private fun closureOf(text: String): Int = withSnapshot(text) { _, snapshot -> snapshot.closureSize() }

    private fun errorsOf(text: String): List<String> =
        withSnapshot(text) { _, snapshot ->
            snapshot
                .getErrors()
                .map {
                    val range = it.element.textRange
                    "${it.severity}|${it.message}|(${range.startOffset},${range.endOffset})"
                }.sorted()
        }

    private fun <T : PsiElement> elementAt(
        file: PsiFile,
        span: TextSpan<T>,
    ): T {
        val element = PsiTreeUtil.findElementOfClassAtRange(file, span.start, span.end, span.psiClass)
        assertNotNull(element, "no ${span.psiClass.simpleName} at (${span.start},${span.end})")
        return element
    }

    private fun shape(name: String): String =
        checkNotNull(TypeGoldenFixtures.NAMED_SHAPES.toMap()[name]) { "no shape $name in NAMED_SHAPES" }

    private companion object {
        /** Requirements Appendix A. */
        val ANNOTATED3_ERRORS =
            listOf(
                "HYPOTHESIS|Too few arguments: expected at least 1, got 0|(53,61)",
                "HYPOTHESIS|Too many arguments: expected at most 0, got 1|(104,119)",
                "HYPOTHESIS|Too many arguments: expected at most 0, got 1|(105,113)",
                "HYPOTHESIS|Too many arguments: expected at most 0, got 1|(120,135)",
                "HYPOTHESIS|Too many arguments: expected at most 0, got 1|(121,129)",
                "HYPOTHESIS|Too many arguments: expected at most 0, got 1|(88,103)",
                "HYPOTHESIS|Too many arguments: expected at most 0, got 1|(89,97)",
                "HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(105,113)",
                "HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(121,129)",
                "HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(53,61)",
                "HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(89,97)",
                "HYPOTHESIS|fun(n) | fun() is not assignable to fun()|(53,61)",
                "HYPOTHESIS|{ ... } | Builder is not assignable to { ... }|(105,113)",
                "HYPOTHESIS|{ ... } | Builder is not assignable to { ... }|(121,129)",
                "HYPOTHESIS|{ ... } | Builder is not assignable to { ... }|(89,97)",
            )

        /** Requirements Appendix B. The call argument at (135,136) is ERROR since BUG-566. */
        val CHAIN_ERRORS =
            listOf(
                "ERROR|number is not assignable to string|(135,136)",
                "ERROR|number is not assignable to string|(60,94)",
                "ERROR|number is not assignable to string|(76,77)",
                "ERROR|string | number is not assignable to string|(60,94)",
                "ERROR|string | number is not assignable to string|(76,77)",
                "ERROR|{ ... } | Bd is not assignable to Bd|(60,94)",
                "HYPOTHESIS|Bd | Bd | { ... } is not assignable to { ... }|(114,115)",
                "HYPOTHESIS|Bd | Bd | { ... } is not assignable to { ... }|(118,128)",
                "HYPOTHESIS|Bd | Bd | { ... } is not assignable to { ... }|(130,134)",
                "HYPOTHESIS|Bd | { ... } | Bd is not assignable to Bd|(60,94)",
                "HYPOTHESIS|Bd | { ... } | Bd is not assignable to { ... }|(114,115)",
                "HYPOTHESIS|Bd | { ... } | Bd is not assignable to { ... }|(118,128)",
                "HYPOTHESIS|Bd | { ... } | Bd is not assignable to { ... }|(130,134)",
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(119,123)",
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(139,143)",
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(167,171)",
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(71,75)",
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(p)|(118,128)",
                "HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(p)|(119,123)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(119,123)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(130,134)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(139,143)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(167,171)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(71,75)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(118,128)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(119,123)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(129,137)",
                "HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(130,134)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to Bd|(19,21)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to Bd|(72,75)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(114,115)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(118,128)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(119,123)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(130,134)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(139,143)",
                "HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(167,171)",
            )
    }
}
