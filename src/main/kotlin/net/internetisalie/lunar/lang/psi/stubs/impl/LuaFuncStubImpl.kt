package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaStubElementTypes
import net.internetisalie.lunar.lang.psi.stubs.LuaFuncStub
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

class LuaFuncStubImpl(
    parent: StubElement<*>?,
    override val name: String?,
    override val luacatsReturnType: String?,
    override val luacatsParams: List<LuaCatsDeclarations.ParamDecl>,
    override val rootIsLocal: Boolean,
) : StubBase<LuaFuncDecl>(parent, LuaStubElementTypes.FUNC_DECL),
    LuaFuncStub
