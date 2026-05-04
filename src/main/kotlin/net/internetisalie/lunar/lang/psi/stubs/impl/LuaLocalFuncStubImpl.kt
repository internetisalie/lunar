package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaStubElementTypes
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalFuncStub

class LuaLocalFuncStubImpl(
    parent: StubElement<*>?,
    override val name: String?,
    override val luacatsReturnType: String?,
    override val luacatsParamTypes: Map<String, String>
) : StubBase<LuaLocalFuncDecl>(parent, LuaStubElementTypes.LOCAL_FUNC_DECL), LuaLocalFuncStub
