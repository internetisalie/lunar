package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.psi.stubs.PsiFileStub
import net.internetisalie.lunar.lang.psi.LuaFile

interface LuaFileStub : PsiFileStub<LuaFile> {
    val exportedTypeString: String?
}
