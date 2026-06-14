package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFinalStatement

/** Whether a module exports via a root `return` (return-style) or only defines globals. */
enum class LuaExportStyle { RETURN_STYLE, GLOBAL_STYLE }

/**
 * Determines a module's export style to pick the auto-import template (COMP-03-AC-02).
 *
 * Fast path uses the file stub's `exportedTypeString`; otherwise scans the root block for
 * a `return` final statement. Falls back to RETURN_STYLE in dumb mode (the safer default,
 * since most modules return a table).
 */
class LuaExportStyleDetector {

    fun detect(targetFile: VirtualFile, project: Project): LuaExportStyle {
        if (DumbService.isDumb(project)) return LuaExportStyle.RETURN_STYLE

        val psiFile = PsiManager.getInstance(project).findFile(targetFile) as? LuaFile
            ?: return LuaExportStyle.GLOBAL_STYLE

        val stub = psiFile.stub
        if (stub != null && stub.exportedTypeString != null) {
            return LuaExportStyle.RETURN_STYLE
        }
        return detectFromPsi(psiFile)
    }

    private fun detectFromPsi(file: LuaFile): LuaExportStyle {
        val hasRootReturn = file.getBlockList().any { block ->
            block.statementList.any { it is LuaFinalStatement }
        }
        return if (hasRootReturn) LuaExportStyle.RETURN_STYLE else LuaExportStyle.GLOBAL_STYLE
    }
}
