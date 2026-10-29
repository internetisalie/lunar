package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

interface LuaLocalFuncStub : StubElement<LuaLocalFuncDecl> {
    val name: String?
    val luacatsReturnType: String?
    val luacatsParams: List<LuaCatsDeclarations.ParamDecl>
}
