package net.internetisalie.lunar.redis.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaStatement
import net.internetisalie.lunar.run.LuaDebuggerEditorsProvider

/**
 * The Redis-LDB line breakpoint type (design §2.5, §3.9).
 *
 * A distinct type id (`"redis-lua-line"`) so Redis-script breakpoints are managed independently of
 * the MobDebug `"lua-line"` type. [canPutAt] mirrors `run/LuaLineBreakpointType` — a position is
 * breakable iff its enclosing element on that line is a [LuaStatement] — but guards the iteration
 * result with an Elvis default instead of the MobDebug `result.get()!!` (contract §1; design §3.9).
 */
class LuaLdbBreakpointType : XLineBreakpointTypeBase(
    "redis-lua-line",
    "Redis Lua Line Breakpoints",
    LuaDebuggerEditorsProvider(),
) {

    override fun getDisplayText(breakpoint: XLineBreakpoint<XBreakpointProperties<*>?>): String {
        val sourcePosition: XSourcePosition = breakpoint.sourcePosition ?: return "Unknown position"
        val displayPath = FileUtil.toSystemDependentName(sourcePosition.file.path)
        return "Line ${sourcePosition.line} in file $displayPath"
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        val luaFile = PsiManager.getInstance(project).findFile(file) as? LuaFile ?: return false
        val document = PsiDocumentManager.getInstance(project).getDocument(luaFile) ?: return false

        val result = Ref.create(false)
        XDebuggerUtil.getInstance().iterateLine(project, document, line) { element ->
            if (!isComment(element) && hasStatementAncestor(element)) result.set(true)
            true
        }
        return result.get() ?: false
    }

    private fun isComment(element: PsiElement?): Boolean =
        element is PsiWhiteSpace || PsiTreeUtil.getParentOfType(element, PsiComment::class.java) != null

    private fun hasStatementAncestor(element: PsiElement?): Boolean =
        PsiTreeUtil.getParentOfType(element, LuaStatement::class.java, false) != null
}
