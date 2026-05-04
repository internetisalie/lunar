package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl

interface LuaLocalVarStub : StubElement<LuaLocalVarDecl> {
    val names: List<String>
    val luacatsType: String?
    val luacatsClassName: String?
    val luacatsAliasName: String?
    val luacatsAliasTarget: String?
    val luacatsExtends: String?
    val luacatsFields: Map<String, String>
}
