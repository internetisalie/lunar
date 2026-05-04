package net.internetisalie.lunar.lang.psi

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.stubs.LuaFuncStub
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalFuncStub
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalVarStub
import net.internetisalie.lunar.lang.psi.stubs.impl.LuaFuncStubElementType
import net.internetisalie.lunar.lang.psi.stubs.impl.LuaLocalFuncStubElementType
import net.internetisalie.lunar.lang.psi.stubs.impl.LuaLocalVarStubElementType
import java.util.concurrent.ConcurrentHashMap

object LuaStubElementTypes {
    private val elementTypes = ConcurrentHashMap<String, IElementType>()

    val LOCAL_VAR_DECL: IStubElementType<LuaLocalVarStub, LuaLocalVarDecl> = LuaLocalVarStubElementType("LOCAL_VAR_DECL")
    val FUNC_DECL: IStubElementType<LuaFuncStub, LuaFuncDecl> = LuaFuncStubElementType("FUNC_DECL")
    val LOCAL_FUNC_DECL: IStubElementType<LuaLocalFuncStub, LuaLocalFuncDecl> = LuaLocalFuncStubElementType("LOCAL_FUNC_DECL")

    @JvmStatic
    fun getStubElementType(name: String): IElementType {
        val type = when (name) {
            "LOCAL_VAR_DECL" -> LOCAL_VAR_DECL
            "FUNC_DECL" -> FUNC_DECL
            "LOCAL_FUNC_DECL" -> LOCAL_FUNC_DECL
            else -> elementTypes.computeIfAbsent(name) { LuaElementType(it) }
        }
        // println("getStubElementType($name) -> $type")
        return type
    }
}
