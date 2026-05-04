package net.internetisalie.lunar.lang.indexing

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl

class LuaAliasIndex : StringStubIndexExtension<LuaLocalVarDecl>() {
    override fun getKey(): StubIndexKey<String, LuaLocalVarDecl> = KEY

    companion object {
        val KEY: StubIndexKey<String, LuaLocalVarDecl> =
            StubIndexKey.createIndexKey("lunar.luacats.alias")
    }
}
