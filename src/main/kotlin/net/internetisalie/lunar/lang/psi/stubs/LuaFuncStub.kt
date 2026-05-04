package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl

interface LuaFuncStub : StubElement<LuaFuncDecl> {
    val name: String?
    val luacatsReturnType: String?
    val luacatsParamTypes: Map<String, String>
}
