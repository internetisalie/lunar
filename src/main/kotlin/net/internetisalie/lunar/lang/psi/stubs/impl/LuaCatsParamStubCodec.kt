package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

/**
 * The one serializer of a func stub's `@param` list, shared by [LuaFuncStubElementType] and
 * [LuaLocalFuncStubElementType] so that the twins cannot drift. Each record carries the optional
 * marker (BUG-554), which the former name → type map had nowhere to store.
 */
internal object LuaCatsParamStubCodec {
    fun write(
        params: List<LuaCatsDeclarations.ParamDecl>,
        out: StubOutputStream,
    ) {
        out.writeInt(params.size)
        params.forEach {
            out.writeName(it.name)
            out.writeName(it.typeName)
            out.writeBoolean(it.isOptional)
        }
    }

    /** Each `@generic` parameter's name and bound text; a null bound is written as a null name. */
    fun writeGenerics(
        generics: List<LuaCatsDeclarations.GenericParameter>,
        out: StubOutputStream,
    ) {
        out.writeInt(generics.size)
        generics.forEach {
            out.writeName(it.name)
            out.writeName(it.boundText)
        }
    }

    fun readGenerics(input: StubInputStream): List<LuaCatsDeclarations.GenericParameter> =
        List(input.readInt()) {
            LuaCatsDeclarations.GenericParameter(input.readName()?.string ?: "", input.readName()?.string)
        }

    /** `List(n) { }` evaluates its elements in index order, so the read order is the write order. */
    fun read(input: StubInputStream): List<LuaCatsDeclarations.ParamDecl> =
        List(input.readInt()) {
            LuaCatsDeclarations.ParamDecl(
                input.readName()?.string ?: "",
                input.readName()?.string ?: "",
                input.readBoolean(),
            )
        }
}
