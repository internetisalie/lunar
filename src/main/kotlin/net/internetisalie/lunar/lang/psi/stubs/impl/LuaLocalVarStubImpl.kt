package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaStubElementTypes
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalVarStub

class LuaLocalVarStubImpl(
    parent: StubElement<*>?,
    override val names: List<String>,
    override val luacatsType: String?,
    override val luacatsClassName: String?,
    override val luacatsAliasName: String?,
    override val luacatsAliasTarget: String?,
    override val luacatsExtends: String?,
    override val luacatsFields: Map<String, String>
) : StubBase<LuaLocalVarDecl>(parent, LuaStubElementTypes.LOCAL_VAR_DECL), LuaLocalVarStub
