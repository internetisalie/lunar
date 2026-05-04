package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaStubElementTypes
import net.internetisalie.lunar.lang.psi.stubs.LuaFuncStub

class LuaFuncStubImpl(
    parent: StubElement<*>?,
    override val name: String?,
    override val luacatsReturnType: String?,
    override val luacatsParamTypes: Map<String, String>
) : StubBase<LuaFuncDecl>(parent, LuaStubElementTypes.FUNC_DECL), LuaFuncStub
