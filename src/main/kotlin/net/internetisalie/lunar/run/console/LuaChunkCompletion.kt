package net.internetisalie.lunar.run.console

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaFileType

/**
 * Client-side trial parse that decides whether accumulated REPL input is a complete Lua chunk
 * (RUN-03-03). A chunk is **incomplete** only when a parse error sits at end-of-input — i.e. more
 * input could still close an open `function`/`if`/`do`/`(`/long-string. A mid-chunk error is a
 * real syntax error and is treated as complete so the interpreter can report it.
 */
object LuaChunkCompletion {
    fun isComplete(project: Project, text: String): Boolean = runReadActionBlocking {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("repl.lua", LuaFileType, text)
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        val errorAtEof = errors.any { it.textRange.endOffset == text.length }
        !errorAtEof
    }
}
