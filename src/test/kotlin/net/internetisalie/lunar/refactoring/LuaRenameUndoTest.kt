package net.internetisalie.lunar.refactoring

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * A committed Lunar rename must be a single undoable command.
 *
 * `LuaRenameProcessor.renameElement`'s KDoc asserts this and, until [[BUG-471]], nothing executed
 * it — that report claimed undo was inert on both rename routes. It is not: this restores, and so
 * do both routes in a live IDE. The report closed as an artifact of the deprecated container.
 *
 * Kept as a gate rather than discarded with the investigation, because the property is easy to
 * lose: `renameElement` runs inside the platform's write action and opens none of its own, so a
 * future change to that framing would break undo silently and no other test would notice.
 */
@RunWith(JUnit4::class)
class LuaRenameUndoTest : BasePlatformTestCase() {
    @Test
    fun undoAfterRenameRestoresTheDocument() {
        val before =
            """
            local counter = 0
            print(counter)
            counter = counter + 1
            """.trimIndent()
        myFixture.configureByText("undo.lua", before)

        // Caret on the declaration.
        val declOffset = before.indexOf("counter")
        myFixture.editor.caretModel.moveToOffset(declOffset + 1)

        myFixture.renameElementAtCaret("total")
        val afterRename = myFixture.editor.document.text
        println("rename-undo | after rename   = ${afterRename.replace("\n", " / ")}")

        val editor = FileEditorManager.getInstance(project).getSelectedEditor(myFixture.file.virtualFile)
        val undo = UndoManager.getInstance(project)
        println("rename-undo | editor         = ${editor?.let { it::class.java.simpleName } ?: "null"}")
        println("rename-undo | isUndoAvailable= ${undo.isUndoAvailable(editor as? TextEditor)}")

        undo.undo(editor as? TextEditor)
        val afterUndo = myFixture.editor.document.text
        println("rename-undo | after undo     = ${afterUndo.replace("\n", " / ")}")
        println("rename-undo | RESTORED       = ${afterUndo == before}")

        assertEquals("undo must restore the pre-rename text", before, afterUndo)
    }
}
