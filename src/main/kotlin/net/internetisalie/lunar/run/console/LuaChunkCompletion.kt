package net.internetisalie.lunar.run.console

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl

/**
 * Client-side trial parse that decides whether accumulated REPL input is a complete Lua chunk
 * (RUN-03-03). A chunk is **incomplete** only when a parse error sits at end-of-input — i.e. more
 * input could still close an open `function`/`if`/`do`/`(`/long-string. A mid-chunk error is a
 * real syntax error and is treated as complete so the interpreter can report it.
 *
 * SYNTAX-18 note: grammar-kit `pin`+`recoverWhile` on block constructs may place the error
 * BEFORE EOF for unfinished function declarations. Specifically, `funcBody` has no pin of its own
 * and rolls back its tokens (including `(parList)`) when `END` is missing; the error is placed
 * before the rolled-back `(` rather than at EOF, and `(parList)` is re-parsed as a subsequent
 * expression statement — so the primary EOF-error check would miss this case. We add a secondary
 * check: if a func-decl node (LuaFuncDecl / LuaLocalFuncDecl / LuaGlobalFuncDecl) has a
 * PsiErrorElement child AND the node ends before EOF, the chunk is incomplete.
 */
object LuaChunkCompletion {
    fun isComplete(project: Project, text: String): Boolean = runReadActionBlocking {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("repl.lua", LuaFileType, text)
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)

        // Primary check: error whose range ends exactly at end-of-input.
        val errorAtEof = errors.any { it.textRange.endOffset == text.length }
        if (errorAtEof) return@runReadActionBlocking false

        // Secondary check (SYNTAX-18): funcBody rollback places error before `(` rather than EOF.
        // If any func-decl node has a direct PsiErrorElement child and ends before EOF, the
        // function body was rolled back — the chunk is incomplete (the programmer is still typing).
        val hasPartialFuncDecl = errors.any { err ->
            val parent = err.parent ?: return@any false
            parent.textRange.endOffset < text.length &&
                (parent is LuaFuncDecl || parent is LuaLocalFuncDecl || parent is LuaGlobalFuncDecl)
        }
        !hasPartialFuncDecl
    }
}
