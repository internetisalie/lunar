package net.internetisalie.lunar.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl

interface LuaLocalVarStub : StubElement<LuaLocalVarDecl> {
    val names: List<String>
    val luacatsType: String?

    /** The class's head name: `Box` for `---@class Box<T>` (TYPE-14-08). */
    val luacatsClassName: String?

    /** The class head's own type parameters, `[T]` for `---@class Box<T>`; empty for a plain head. */
    val luacatsTypeParameters: List<String>
    val luacatsAliasName: String?
    val luacatsAliasTarget: String?
    val luacatsParents: List<String>
    val luacatsFields: Map<String, String>
}
