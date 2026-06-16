package net.internetisalie.lunar.run.console

import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project

/**
 * Decides, on Enter, whether the accumulated REPL input is a complete Lua chunk (RUN-03-03).
 *
 * - Complete chunk (or blank force-submit escape hatch) → delegate to the platform handler, which
 *   clears the input editor, records history (RUN-03-05) and sends the text to the process stdin.
 * - Incomplete chunk → insert a newline into the input editor and stay in multi-line mode.
 *
 * stdout/stderr routing (RUN-03-07) is handled by the console's process attachment, not here.
 */
class LuaConsoleExecuteHandler(
    private val project: Project,
    processHandler: ProcessHandler,
) : ProcessBackedConsoleExecuteActionHandler(processHandler, false) {

    override fun runExecuteAction(consoleView: LanguageConsoleView) {
        val text = consoleView.editorDocument.text
        if (text.isBlank() || LuaChunkCompletion.isComplete(project, text)) {
            super.runExecuteAction(consoleView)
        } else {
            insertContinuationNewline(consoleView)
        }
    }

    private fun insertContinuationNewline(consoleView: LanguageConsoleView) {
        val editor = consoleView.consoleEditor
        WriteCommandAction.runWriteCommandAction(project) {
            EditorModificationUtil.insertStringAtCaret(editor, "\n")
        }
    }
}
