package net.internetisalie.lunar.lang.indexing

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import net.internetisalie.lunar.lang.psi.LuaFuncDecl

class LuaGlobalDeclarationIndex : StringStubIndexExtension<LuaFuncDecl>() {
    override fun getKey(): StubIndexKey<String, LuaFuncDecl> = KEY

    companion object {
        val KEY: StubIndexKey<String, LuaFuncDecl> = StubIndexKey.createIndexKey("lunar.global.decl")
    }
}
