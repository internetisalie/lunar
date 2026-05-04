package net.internetisalie.lunar.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguage

open class LuaFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, LuaLanguage) {

    override fun getStub(): com.intellij.psi.stubs.PsiFileStub<out LuaFile>? {
        return super.getStub() as? com.intellij.psi.stubs.PsiFileStub<out LuaFile>
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
}
