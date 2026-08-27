package net.internetisalie.lunar.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.stubs.LuaFileStub

open class LuaFile(
    viewProvider: FileViewProvider,
) : PsiFileBase(viewProvider, LuaLanguage) {
    override fun getStub(): LuaFileStub? = super.getStub() as? LuaFileStub

    constructor(
        elementType: IElementType,
        contentElementType: IElementType,
        viewProvider: FileViewProvider,
    ) : this(viewProvider) {
        init(elementType, contentElementType)
    }

    override fun getFileType(): FileType = LuaFileType

    override fun toString(): String = "Lua"

    fun getBlockList(): List<LuaBlock> = LuaPsiImplUtil.getBlockList(this)

    override fun processDeclarations(
        processor: com.intellij.psi.scope.PsiScopeProcessor,
        state: com.intellij.psi.ResolveState,
        lastParent: com.intellij.psi.PsiElement?,
        place: com.intellij.psi.PsiElement,
    ): Boolean {
        // File scope = root block + global function declarations + global variable assignments

        // Left in source order deliberately: under `root ::= block*` (lua.bnf:96) a file's direct
        // children are LuaBlock and whitespace only — measured — so no branch below is reachable
        // and BUG-472's nearest-first ordering has nothing to correct here.
        for (child in children) {
            // Visibility filtering: stop if we reached the place of completion
            if (lastParent != null && child.textOffset >= lastParent.textOffset) {
                break
            }
            when (child) {
                is LuaFuncDecl -> if (!processor.execute(child, state)) return false
                is LuaGlobalFuncDecl -> if (!processor.execute(child, state)) return false
                is LuaGlobalVarDecl -> if (!processor.execute(child, state)) return false
                is LuaAssignmentStatement -> if (!processor.execute(child, state)) return false
            }
        }

        // Then process blocks
        val blocks = getBlockList()
        if (blocks.isEmpty()) {
            return true // No blocks, continue walk to parent scope
        }

        // Nearest block first. `root ::= block*` (lua.bnf:96) and a mid-file `return` closes one
        // block and opens the next, so a file really can hold more than one — measured — and a
        // later block's declarations shadow an earlier block's (BUG-472).
        for (block in blocks.asReversed()) {
            if (!block.processDeclarations(processor, state, lastParent, place)) {
                return false // Processor found match, stop walk
            }
        }

        return true // Continue walk to parent scope
    }
}
