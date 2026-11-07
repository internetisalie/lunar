package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameAndArgs
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import java.io.File

/** The file being scanned and the checkout it was copied from (design §3.2 step 5). */
internal class ScanScope(
    val file: LuaFile,
    checkoutDir: File,
) {
    val relativePath: String = AuditPaths.relativePath(file)
    val checkoutPath: String = checkoutDir.canonicalPath
}

/** A resolved callee declaration: a [LuaFuncDecl] or a [LuaLocalFuncDecl]. */
internal data class Callee(
    val decl: LuaCommentOwner,
    val parList: LuaParList?,
    val form: CalleeForm,
)

/** Where a declaration lives: its [CalleePath], and its temp-root path when PROJECT_FILE. */
internal data class Location(
    val path: CalleePath,
    val declFile: String?,
)

internal object AuditPaths {
    /** [file]'s path relative to the fixture's temp root (the light project's source root). */
    fun relativePath(file: PsiFile): String {
        val virtualFile = file.virtualFile ?: file.originalFile.virtualFile ?: return file.name
        val root = ProjectFileIndex.getInstance(file.project).getSourceRootForFile(virtualFile)
        return root?.let { VfsUtilCore.getRelativePath(virtualFile, it) } ?: virtualFile.name
    }

    fun locate(
        declaring: PsiElement,
        scope: ScanScope,
    ): Location {
        val declFile = declaring.containingFile
        if (declFile == scope.file) return Location(CalleePath.SAME_FILE, null)
        val virtualFile = declFile?.virtualFile ?: return Location(CalleePath.LIBRARY, null)
        return when {
            virtualFile.url.startsWith("temp://") -> Location(CalleePath.PROJECT_FILE, relativePath(declFile))
            virtualFile.path.startsWith(scope.checkoutPath) -> Location(CalleePath.CHECKOUT_FILE, null)
            else -> Location(CalleePath.LIBRARY, null)
        }
    }

    fun declRange(
        location: Location,
        decl: PsiElement,
    ): TextRange? = if (location.path == CalleePath.SAME_FILE) decl.textRange else null
}

/** Design §3.2 steps 2–4: from a call's first `nameAndArgs` to the callee's declaration and form. */
internal object AuditCallResolution {
    fun resolve(
        call: LuaFuncCall,
        nameAndArgs: LuaNameAndArgs,
    ): Callee? {
        val callee = call.varOrExp.`var`
        val ref =
            nameAndArgs.methodExpr?.nameRef
                ?: callee
                    ?.varSuffixList
                    ?.lastOrNull()
                    ?.indexExpr
                    ?.nameRef
                ?: callee?.nameRef
        val decl = declarationOf(ref?.reference?.resolve()) ?: globalRoute(call, nameAndArgs, ref)
        return when (decl) {
            is LuaLocalFuncDecl -> Callee(decl, decl.parList, CalleeForm.LOCAL)
            is LuaFuncDecl -> Callee(decl, decl.parList, formOf(decl))
            else -> null
        }
    }

    fun formOf(decl: LuaFuncDecl): CalleeForm =
        when {
            decl.funcName.funcNameMethod != null -> CalleeForm.METHOD
            decl.funcName.funcNamePropertyList.isNotEmpty() -> CalleeForm.MEMBER
            else -> CalleeForm.GLOBAL
        }

    /** What `resolve()` returns, by the DR-01b resolution rows. */
    private fun declarationOf(target: PsiElement?): PsiElement? {
        if (target is LuaFuncDecl) return target
        val parent = target?.parent
        val grandParent = parent?.parent
        return when {
            parent is LuaNameRef && grandParent is LuaLocalFuncDecl -> grandParent
            grandParent is LuaFuncNameMethod -> PsiTreeUtil.getParentOfType(target, LuaFuncDecl::class.java)
            else -> null
        }
    }

    /** The engine's own cross-file route (DR-01d), for `Recv:m(…)` and `Recv.m(…)` only. */
    private fun globalRoute(
        call: LuaFuncCall,
        nameAndArgs: LuaNameAndArgs,
        ref: LuaNameRef?,
    ): PsiElement? {
        val callee = call.varOrExp.`var`
        val receiver = callee?.nameRef ?: return null
        val suffixes = callee.varSuffixList.size
        val isMethod = nameAndArgs.methodExpr != null
        if (ref == null || (isMethod && suffixes != 0) || (!isMethod && suffixes != 1)) return null
        val manager = LuaTypeManager.getInstance(call.project)
        val source = manager.resolveGlobal(receiver.text, call)?.resolveMember(ref.text)?.sourceElement
        if (PsiTreeUtil.getParentOfType(source, LuaFuncName::class.java, false) == null) return null
        return PsiTreeUtil.getParentOfType(source, LuaFuncDecl::class.java, false)
    }
}
