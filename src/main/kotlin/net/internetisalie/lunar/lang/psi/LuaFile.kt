package net.internetisalie.lunar.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.ResolveState
import com.intellij.psi.PsiElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.stubs.LuaFileStub

open class LuaFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, LuaLanguage) {

    override fun getStub(): LuaFileStub? {
        return super.getStub() as? LuaFileStub
    }

    constructor(
        elementType : IElementType,
        contentElementType: IElementType,
        viewProvider: FileViewProvider,
    ) : this(viewProvider) {
        init(elementType, contentElementType)
    }

    override fun getFileType(): FileType {
        return LuaFileType
    }

    override fun toString(): String {
        return "Lua"
    }

    fun getBlockList() : List<LuaBlock> {
        return LuaPsiImplUtil.getBlockList(this)
    }

    override fun processDeclarations(
        processor: com.intellij.psi.scope.PsiScopeProcessor,
        state: com.intellij.psi.ResolveState,
        lastParent: com.intellij.psi.PsiElement?,
        place: com.intellij.psi.PsiElement
    ): Boolean {
        // File scope = root block; delegate to block's processDeclarations
        val blocks = getBlockList()
        if (blocks.isEmpty()) {
            return true  // No blocks, continue walk to parent scope
        }

        // Process each block (typically there's only one at file level)
        for (block in blocks) {
            if (!block.processDeclarations(processor, state, lastParent, place)) {
                return false  // Processor found match, stop walk
            }
        }

        return true  // Continue walk to parent scope
    }
}
