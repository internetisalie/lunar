package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaStubElementTypes
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalFuncStub
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

class LuaLocalFuncStubImpl(
    parent: StubElement<*>?,
    override val name: String?,
    override val luacatsReturnType: String?,
    override val luacatsParams: List<LuaCatsDeclarations.ParamDecl>,
) : StubBase<LuaLocalFuncDecl>(parent, LuaStubElementTypes.LOCAL_FUNC_DECL),
    LuaLocalFuncStub
