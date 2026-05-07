package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.tree.IStubFileElementType
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.stubs.LuaFileStub

class LuaFileStubImpl(
    file: LuaFile?,
    override val exportedTypeString: String?
) : PsiFileStubImpl<LuaFile>(file), LuaFileStub {
    override fun getType(): IStubFileElementType<*> {
        return net.internetisalie.lunar.lang.LuaParserDefinition.FILE
    }
}
