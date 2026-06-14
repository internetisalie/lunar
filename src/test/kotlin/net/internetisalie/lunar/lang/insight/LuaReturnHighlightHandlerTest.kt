package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers NAV-09-01 (same-scope returns highlighted) and NAV-09-02 (nested returns excluded).
 *
 * Strategy: configure a file with a `<caret>` marker on the target `return` keyword, then
 * directly construct [LuaReturnHighlightUsagesHandlerFactory] and invoke the handler to collect
 * occurrences, asserting on [getReadUsages()] text ranges.  This avoids the need for test-data
 * files required by `myFixture.testHighlightUsages(filename)`.
 */
class LuaReturnHighlightHandlerTest : BaseDocumentTest() {

    private val factory = LuaReturnHighlightUsagesHandlerFactory()

    // TC-NAV-09-01 — caret on a `return` highlights all same-scope returns
    @Test
    fun testSameScopeReturnsHighlighted() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val file = configureByText(
                    "function f() if a then <caret>return 1 end return 2 end"
                )
                val editor = myFixture.editor
                val caretEl = file.findElementAt(myFixture.caretOffset)
                assertNotNull(caretEl, "Expected element at caret")

                val handler = factory.createHighlightUsagesHandler(editor, file, caretEl)
                assertNotNull(handler, "Handler must be created for a return keyword")

                handler.computeUsages(handler.targets)
                val ranges = handler.readUsages

                // Both `return` keywords in `f` must be highlighted
                assertEquals(2, ranges.size, "Expected exactly 2 return highlights for same-scope returns")
            }
        }
    }

    // TC-NAV-09-02 — nested function's returns are NOT included
    @Test
    fun testNestedFunctionReturnsExcluded() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val file = configureByText(
                    "function f() local g = function() return 1 end <caret>return 2 end"
                )
                val editor = myFixture.editor
                val caretEl = file.findElementAt(myFixture.caretOffset)
                assertNotNull(caretEl, "Expected element at caret")

                val handler = factory.createHighlightUsagesHandler(editor, file, caretEl)
                assertNotNull(handler, "Handler must be created for a return keyword")

                handler.computeUsages(handler.targets)
                val ranges = handler.readUsages

                // Only `return 2` (in `f`) highlighted; `return 1` (in nested `g`) excluded
                assertEquals(1, ranges.size, "Only the outer return should be highlighted; nested excluded")
            }
        }
    }

    // Sanity: factory returns null for non-return/non-function keyword elements
    @Test
    fun testFactoryReturnsNullForNonKeyword() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val file = configureByText("local <caret>x = 1")
                val editor = myFixture.editor
                val caretEl = file.findElementAt(myFixture.caretOffset)
                assertNotNull(caretEl, "Expected element at caret")

                val handler = factory.createHighlightUsagesHandler(editor, file, caretEl)
                assertNull(handler, "Handler must be null for non-return/function keyword")
            }
        }
    }

    // NAV-09-02: caret on `function` keyword also activates the handler
    @Test
    fun testFunctionKeywordActivatesHandler() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val file = configureByText("<caret>function f() return 1 end")
                val editor = myFixture.editor
                val caretEl = file.findElementAt(myFixture.caretOffset)
                assertNotNull(caretEl, "Expected element at caret")

                val handler = factory.createHighlightUsagesHandler(editor, file, caretEl)
                assertNotNull(handler, "Handler must be created for a function keyword (NAV-09-02)")

                handler.computeUsages(handler.targets)
                val ranges = handler.readUsages

                // The `return` inside `f` + the `function` keyword itself
                assertEquals(2, ranges.size, "Expected return and function keyword highlighted for NAV-09-02")
            }
        }
    }
}
