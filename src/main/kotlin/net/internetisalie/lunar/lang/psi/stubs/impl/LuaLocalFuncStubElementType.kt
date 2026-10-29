package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.*
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.lang.psi.impl.LuaLocalFuncDeclImpl
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalFuncStub
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

class LuaLocalFuncStubElementType(
    debugName: String,
) : IStubElementType<LuaLocalFuncStub, LuaLocalFuncDecl>(debugName, LuaLanguage) {
    override fun createPsi(stub: LuaLocalFuncStub): LuaLocalFuncDecl = LuaLocalFuncDeclImpl(stub, this)

    override fun createStub(
        psi: LuaLocalFuncDecl,
        parentStub: StubElement<out com.intellij.psi.PsiElement>?,
    ): LuaLocalFuncStub {
        // SYNTAX-18: a pinned partial `local function` decl may lack its nameRef; the hand-stubbed
        // getter is @NotNull, so read the child off the node instead of throwing.
        val name = psi.node.findChildByType(LuaElementTypes.NAME_REF)?.text ?: ""
        val catsComment = LuaPsiImplUtil.getCatsComment(psi)
        // MAINT-34-03: one extractor, not a copy. This block and its twin in the sibling
        // *FuncStubElementType were byte-identical, and funcTypeFromStub held a third.
        val returnType = catsComment?.let { LuaCatsDeclarations.returnTypeName(it) }
        val params = catsComment?.let { LuaCatsDeclarations.params(it) } ?: emptyList()

        return LuaLocalFuncStubImpl(parentStub, name, returnType, params)
    }

    override fun getExternalId(): String = "lunar.local.func.decl"

    override fun serialize(
        stub: LuaLocalFuncStub,
        dataStream: StubOutputStream,
    ) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.luacatsReturnType)
        LuaCatsParamStubCodec.write(stub.luacatsParams, dataStream)
    }

    override fun deserialize(
        dataStream: StubInputStream,
        parentStub: StubElement<*>?,
    ): LuaLocalFuncStub {
        val name = dataStream.readName()?.string
        val returnType = dataStream.readName()?.string
        val params = LuaCatsParamStubCodec.read(dataStream)
        return LuaLocalFuncStubImpl(parentStub, name, returnType, params)
    }

    override fun indexStub(
        stub: LuaLocalFuncStub,
        sink: IndexSink,
    ) {
        // Local functions are not indexed in GlobalDeclarationIndex
    }
}
