package net.internetisalie.lunar.refactoring

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [LuaIntroduceVariableHandler] (REFACT-02).
 *
 * The handler is invoked headlessly via [com.intellij.refactoring.RefactoringActionHandler.invoke];
 * under unit-test mode the occurrence chooser and inline-rename template are skipped, all
 * equivalent occurrences in the block are replaced, and the suggested name is committed.
 *
 * The names asserted here are the deterministic output of the in-handler `suggestName` heuristic
 * (binary expression -> `result`, call -> callee name), not the illustrative names in the spec.
 */
@RunWith(JUnit4::class)
class LuaIntroduceVariableTest : BasePlatformTestCase() {

    private val handler = LuaIntroduceVariableHandler()

    private fun introduceSelected(text: String) {
        val withoutMarkers = text.replace("<selection>", "").replace("</selection>", "")
        val start = text.indexOf("<selection>")
        val end = text.indexOf("</selection>") - "<selection>".length
        myFixture.configureByText("test.lua", withoutMarkers)
        myFixture.editor.selectionModel.setSelection(start, end)
        handler.invoke(project, myFixture.editor, myFixture.file, null)
    }

    // TC-REFACT-02-01: single occurrence (binary expression -> name "result")
    @Test
    fun testSingleOccurrence() {
        introduceSelected("print(<selection>1 + 2</selection>)")
        myFixture.checkResult("local result = 1 + 2\nprint(result)")
    }

    // TC-REFACT-02-02: replace all identical occurrences in the block
    @Test
    fun testReplaceAllOccurrences() {
        introduceSelected("print(<selection>x*2</selection>)\nreturn x*2")
        myFixture.checkResult("local result = x*2\nprint(result)\nreturn result")
    }

    // TC-REFACT-02-03: insert inside an if block before the return
    @Test
    fun testIntroduceInsideIfBlock() {
        introduceSelected("if a then return <selection>f(a)</selection> end")
        val result = myFixture.file.text
        // The local is introduced inside the if block, before the return, and the call is replaced.
        assertTrue("expected introduced local, got: $result", result.contains("local f = f(a)"))
        assertTrue("expected return to reference the new variable, got: $result", result.contains("return f"))
        assertFalse("the original call should be replaced inside return, got: $result", result.contains("return f(a)"))
        assertTrue("the local must precede the return, got: $result", result.indexOf("local f = f(a)") < result.indexOf("return f"))
    }

    // suggestName: call callee name is used for a function call
    @Test
    fun testCalleeNameSuggestion() {
        introduceSelected("print(<selection>compute()</selection>)")
        myFixture.checkResult("local compute = compute()\nprint(compute)")
    }

    // INTENT-03 TC1: accessor prefix is stripped (getUser -> user)
    @Test
    fun testPrefixStrippedSuggestion() {
        introduceSelected("print(<selection>getUser()</selection>)")
        myFixture.checkResult("local user = getUser()\nprint(user)")
    }

    // INTENT-03 TC3: method-call callee name is derived (getName -> name)
    @Test
    fun testMethodCallSuggestion() {
        introduceSelected("print(<selection>obj:getName()</selection>)")
        myFixture.checkResult("local name = obj:getName()\nprint(name)")
    }
}
