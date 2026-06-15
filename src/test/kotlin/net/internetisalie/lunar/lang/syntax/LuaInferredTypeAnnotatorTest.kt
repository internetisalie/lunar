package net.internetisalie.lunar.lang.syntax

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import net.internetisalie.lunar.IndexedDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaNameRef
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [LuaInferredTypeAnnotator] covering SYNTAX-17 requirements.
 *
 * Uses [myFixture.doHighlighting] with TEXT_ATTRIBUTES severity and matches on
 * [forcedTextAttributesKey] — the same pattern as [TestLuaNumeralAnnotator].
 *
 * Extends [IndexedDocumentTest] so that stub indexes are populated: TC-02 (INFERRED_CLASS)
 * requires `@class` tags to be resolved via [LuaClassNameIndex], which needs the stub index.
 */
class LuaInferredTypeAnnotatorTest : IndexedDocumentTest() {

    // ── helpers ─────────────────────────────────────────────────────────────────

    private fun highlightedKey(text: String, expectedKey: TextAttributesKey): Boolean {
        val infos = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        return infos.any { it.forcedTextAttributesKey == expectedKey && it.text == text }
    }

    private fun assertKey(code: String, identifierText: String, expectedKey: TextAttributesKey) {
        myFixture.configureByText(LuaFileType, code)
        val found = highlightedKey(identifierText, expectedKey)
        if (!found) {
            val all = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
                .filter { it.text == identifierText }
                .map { "${it.text}=${it.forcedTextAttributesKey?.externalName}" }
            assert(false) {
                "Expected '$identifierText' to carry ${expectedKey.externalName}. " +
                    "Actual attributes for this text: $all"
            }
        }
    }

    // ── TC-01: local function call → INFERRED_LOCAL_CALL ────────────────────────

    /**
     * TC-01: `local x = function() end; x()` — the `x` in callee position carries
     * INFERRED_LOCAL_CALL because it resolves to a local declaration.
     */
    @Test
    fun `TC-01 local function call is highlighted as INFERRED_LOCAL_CALL`() {
        assertKey(
            "local x = function() end\nx()",
            "x",
            LuaHighlight.INFERRED_LOCAL_CALL,
        )
    }

    @Test
    fun `TC-01 local function decl call is highlighted as INFERRED_LOCAL_CALL`() {
        assertKey(
            "local function f() end\nf()",
            "f",
            LuaHighlight.INFERRED_LOCAL_CALL,
        )
    }

    // ── TC-02: class constructor reference → INFERRED_CLASS ─────────────────────

    /**
     * TC-02: `---@class MyClass; local o = MyClass()` — the `MyClass` in callee position
     * carries INFERRED_CLASS because its inferred type is a named Table whose className == "MyClass".
     */
    @Test
    fun `TC-02 class constructor call is highlighted as INFERRED_CLASS`() {
        // @class creates a Union of Table(null) | Table(className="MyClass"); extractClassName
        // recurses into Union.types and returns "MyClass".
        assertKey(
            "---@class MyClass\nlocal MyClass = {}\nlocal o = MyClass()",
            "MyClass",
            LuaHighlight.INFERRED_CLASS,
        )
    }

    // ── TC-03: field vs method ───────────────────────────────────────────────────

    /**
     * TC-03a: `t.data = 1` then `t.data` — the member `data` carries INFERRED_FIELD because
     * its resolved type is a Number (not a Function).
     *
     * Note: member coloring requires the type engine to have observed the assignment so it
     * can resolve the receiver `t`'s member set.
     */
    @Test
    fun `TC-03a table field access is highlighted as INFERRED_FIELD`() {
        myFixture.configureByText(
            LuaFileType,
            """
            local t = {}
            t.data = 1
            local _ = t.data
            """.trimIndent(),
        )
        val infos = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        // Find a highlight for "data" at the read site (last occurrence)
        val fieldHighlight = infos.lastOrNull {
            it.text == "data" && it.forcedTextAttributesKey == LuaHighlight.INFERRED_FIELD
        }
        assertNotNull(fieldHighlight) {
            val allData = infos.filter { it.text == "data" }.map { "${it.forcedTextAttributesKey?.externalName}" }
            "Expected 'data' to carry INFERRED_FIELD. Got: $allData"
        }
    }

    /**
     * TC-03b: `function t:func() end; t:func()` — the method `func` in the colon-call site
     * carries INFERRED_METHOD because its resolved member type is a Function.
     *
     * Known limitation: the type engine records methods via the assignment-constraint path
     * (a table member whose write type is Function), so this is classified as a method
     * only when the engine tracked the member.  See TC-03 gotcha notes in design.md.
     */
    @Test
    fun `TC-03b table method call is highlighted as INFERRED_METHOD`() {
        myFixture.configureByText(
            LuaFileType,
            """
            local t = {}
            t.func = function() end
            t.func()
            """.trimIndent(),
        )
        val infos = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        val methodHighlight = infos.lastOrNull {
            it.text == "func" && it.forcedTextAttributesKey == LuaHighlight.INFERRED_METHOD
        }
        assertNotNull(methodHighlight) {
            val all = infos.filter { it.text == "func" }.map { "${it.forcedTextAttributesKey?.externalName}" }
            "Expected 'func' to carry INFERRED_METHOD. Got: $all"
        }
    }

    // ── TC-04: global function call → INFERRED_GLOBAL_CALL ─────────────────────

    /**
     * TC-04: `function globalFn() end; globalFn()` — `globalFn` in callee position carries
     * INFERRED_GLOBAL_CALL because it resolves to a non-local declaration (LuaFuncDecl).
     *
     * In the full IDE the builtin `print` also receives this color (platform library is loaded),
     * but a light-fixture unit test has no platform files; a user-defined global is used instead.
     */
    @Test
    fun `TC-04 global function call is highlighted as INFERRED_GLOBAL_CALL`() {
        assertKey(
            "function globalFn() end\nglobalFn()",
            "globalFn",
            LuaHighlight.INFERRED_GLOBAL_CALL,
        )
    }

    // ── TC-05: dumb mode → no inferred colors ────────────────────────────────────

    /**
     * TC-05 (platform contract — the real dumb-mode guarantee): [LuaInferredTypeAnnotator] must NOT
     * be [DumbAware]. The highlighting machinery only runs DumbAware annotators while indexes
     * rebuild, so being non-DumbAware is what actually stops inferred colors — which need type
     * resolution over the (unavailable) indexes — from appearing during indexing.
     *
     * This is asserted directly rather than via [doHighlighting]: driving the daemon inside
     * [DumbModeTestUtils.runInDumbModeSynchronously] deadlocks, because highlighting waits for smart
     * mode on the EDT while the forced dumb block is held open. If someone makes this annotator
     * DumbAware, this test fails — flagging that the platform would then run it during indexing.
     */
    @Test
    fun `TC-05 annotator is not dumb-aware so the platform skips it while indexing`() {
        // Typed as Any so the `is` check is a genuine runtime test (and not a compile-time
        // "always false" that KTLC-365 will turn into an error): it fails the day someone makes
        // the annotator DumbAware, which is exactly the regression we want to catch.
        val annotator: Any = LuaInferredTypeAnnotator()
        assertFalse(
            annotator is DumbAware,
            "LuaInferredTypeAnnotator must not be DumbAware, or the platform would run it during " +
                "indexing when type resolution is unavailable.",
        )
    }

    /**
     * TC-05 (defense in depth): even if the annotator is invoked while the project is dumb, its
     * [DumbService.isDumb] guard returns early and produces no annotations. Invoked directly into a
     * real [AnnotationHolderImpl] (not via [doHighlighting], which deadlocks in a forced dumb block).
     */
    @Test
    fun `TC-05 dumb guard produces no highlights even if invoked`() {
        myFixture.configureByText(LuaFileType, "local x = function() end\nx()")
        DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
            runReadAction {
                val annotator = LuaInferredTypeAnnotator()
                val holder = AnnotationHolderImpl(AnnotationSession(myFixture.file), false)
                PsiTreeUtil.collectElementsOfType(myFixture.file, LuaNameRef::class.java).forEach {
                    holder.runAnnotatorWithContext(it, annotator)
                }
                assertTrue(holder.isEmpty()) {
                    "Expected no annotations in dumb mode but got: " +
                        holder.joinToString { it.textAttributes?.externalName ?: "?" }
                }
            }
        }
    }
}
