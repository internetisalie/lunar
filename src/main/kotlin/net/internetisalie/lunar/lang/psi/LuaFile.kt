package net.internetisalie.lunar.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguage

class LuaFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, LuaLanguage) {
    override fun getFileType(): FileType {
        return LuaFileType
    }

    override fun toString(): String {
        return "Lua File"
    }
}
