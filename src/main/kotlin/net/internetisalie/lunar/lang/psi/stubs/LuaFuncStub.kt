package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

interface LuaFuncStub : StubElement<LuaFuncDecl> {
    val name: String?
    val luacatsReturnType: String?
    val luacatsParams: List<LuaCatsDeclarations.ParamDecl>

    /** TYPE-14-06: the comment's `@generic` names and bound texts, in declaration order. */
    val luacatsGenericParams: List<LuaCatsDeclarations.GenericParameter>

    /** BUG-509: the name's first segment is bound by a file-local declaration in scope. */
    val rootIsLocal: Boolean
}
