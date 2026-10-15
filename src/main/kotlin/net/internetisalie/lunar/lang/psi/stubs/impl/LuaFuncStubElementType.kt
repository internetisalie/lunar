package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.*
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.lang.psi.impl.LuaFuncDeclImpl
import net.internetisalie.lunar.lang.psi.stubs.LuaFuncStub
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

class LuaFuncStubElementType(
    debugName: String,
) : IStubElementType<LuaFuncStub, LuaFuncDecl>(debugName, LuaLanguage) {
    override fun createPsi(stub: LuaFuncStub): LuaFuncDecl = LuaFuncDeclImpl(stub, this)

    override fun createStub(
        psi: LuaFuncDecl,
        parentStub: StubElement<out com.intellij.psi.PsiElement>?,
    ): LuaFuncStub {
        // SYNTAX-18: a pinned partial `function` decl may lack its funcName; the hand-stubbed
        // getter is @NotNull, so read the child off the node instead of throwing.
        val name = psi.node.findChildByType(LuaElementTypes.FUNC_NAME)?.text ?: ""
        val catsComment = LuaPsiImplUtil.getCatsComment(psi)
        // MAINT-34-03: one extractor, not a copy. This block and its twin in the sibling
        // *FuncStubElementType were byte-identical, and funcTypeFromStub held a third.
        val returnType = catsComment?.let { LuaCatsDeclarations.returnTypeName(it) }
        val paramTypes = catsComment?.let { LuaCatsDeclarations.paramTypes(it) } ?: emptyMap()

        return LuaFuncStubImpl(parentStub, name, returnType, paramTypes, LuaFuncNameBinding.rootIsLocal(psi))
    }

    override fun getExternalId(): String = "lunar.func.decl"

    override fun serialize(
        stub: LuaFuncStub,
        dataStream: StubOutputStream,
    ) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.luacatsReturnType)
        dataStream.writeInt(stub.luacatsParamTypes.size)
        stub.luacatsParamTypes.forEach { (name, type) ->
            dataStream.writeName(name)
            dataStream.writeName(type)
        }
        dataStream.writeBoolean(stub.rootIsLocal)
    }

    override fun deserialize(
        dataStream: StubInputStream,
        parentStub: StubElement<*>?,
    ): LuaFuncStub {
        val name = dataStream.readName()?.string
        val returnType = dataStream.readName()?.string
        val paramCount = dataStream.readInt()
        val paramTypes = mutableMapOf<String, String>()
        repeat(paramCount) {
            val pName = dataStream.readName()?.string ?: ""
            val pType = dataStream.readName()?.string ?: ""
            paramTypes[pName] = pType
        }
        return LuaFuncStubImpl(parentStub, name, returnType, paramTypes, dataStream.readBoolean())
    }

    override fun indexStub(
        stub: LuaFuncStub,
        sink: IndexSink,
    ) {
        stub.name?.let {
            // BUG-509: a name bound by a `local` is never a project-wide global. For a bare
            // `function f()` the full name is that name; for `function R.a()` / `R:m()` the full
            // name stays (class members resolve through it) and only the root is withheld.
            val isBare = it.none { char -> char == '.' || char == ':' }
            if (!(isBare && stub.rootIsLocal)) sink.occurrence(LuaGlobalDeclarationIndex.KEY, it)
            // If it's a dotted name like 'cjson.decode', also index the base 'cjson'
            // to allow basic resolution of the module/table global.
            if (it.contains('.') && !stub.rootIsLocal) {
                sink.occurrence(LuaGlobalDeclarationIndex.KEY, it.substringBefore('.'))
            }
        }
    }
}
