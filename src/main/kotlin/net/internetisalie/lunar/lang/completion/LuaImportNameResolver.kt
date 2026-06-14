package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaKeywords
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil

/**
 * Derives the local binding name for a return-style auto-import (COMP-03-AC-04).
 *
 * Priority: a single `@class` name (verbatim), else the filename (hyphens/spaces -> underscores,
 * lowercased). The result is made keyword-safe (suffix `_module`) and collision-safe (numeric
 * suffix if already bound locally). Returns null for GLOBAL_STYLE (no binding).
 */
class LuaImportNameResolver {

    fun resolve(
        file: VirtualFile,
        exportStyle: LuaExportStyle,
        currentFile: LuaFile,
        project: Project,
    ): String? {
        if (exportStyle == LuaExportStyle.GLOBAL_STYLE) return null

        val base = resolveFromClassAnnotation(file, project) ?: resolveFromFilename(file)
        val guarded = if (LuaKeywords.isReserved(base)) "${base}_module" else base
        return resolveConflict(guarded, currentFile)
    }

    /** The module's single `@class` name, read the same way the type engine does (stub or cats tag). */
    private fun resolveFromClassAnnotation(file: VirtualFile, project: Project): String? {
        if (DumbService.isDumb(project)) return null
        val psiFile = PsiManager.getInstance(project).findFile(file) as? LuaFile ?: return null
        val classNames = PsiTreeUtil.findChildrenOfType(psiFile, LuaLocalVarDecl::class.java)
            .mapNotNull { classNameOf(it) }
            .filter { it.isNotEmpty() }
        return classNames.singleOrNull()
    }

    private fun classNameOf(decl: LuaLocalVarDecl): String? {
        decl.stub?.luacatsClassName?.let { return it.trim() }
        val cats = LuaPsiImplUtil.getCatsComment(decl) ?: return null
        return cats.classTagList.firstOrNull()?.argType?.text?.trim()
    }

    private fun resolveFromFilename(file: VirtualFile): String =
        file.nameWithoutExtension
            .replace(Regex("[-\\s]+"), "_")
            .lowercase()

    private fun resolveConflict(name: String, currentFile: LuaFile): String {
        val localNames = collectLocalNames(currentFile)
        if (name !in localNames) return name
        var suffix = 2
        while ("$name$suffix" in localNames) suffix++
        return "$name$suffix"
    }

    /** Names already bound at top level (locals + local functions), for collision avoidance. */
    private fun collectLocalNames(currentFile: LuaFile): Set<String> {
        val names = mutableSetOf<String>()
        PsiTreeUtil.findChildrenOfType(currentFile, LuaLocalVarDecl::class.java).forEach { decl ->
            decl.attNameList.forEach { att -> att.nameRef?.text?.let(names::add) }
        }
        PsiTreeUtil.findChildrenOfType(currentFile, LuaLocalFuncDecl::class.java).forEach { fn ->
            fn.nameRef?.text?.let(names::add)
        }
        return names
    }
}
