package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.*
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.impl.LuaLocalFuncDeclImpl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalFuncStub

class LuaLocalFuncStubElementType(debugName: String) :
    IStubElementType<LuaLocalFuncStub, LuaLocalFuncDecl>(debugName, LuaLanguage) {

    override fun createPsi(stub: LuaLocalFuncStub): LuaLocalFuncDecl {
        return LuaLocalFuncDeclImpl(stub, this)
    }

    override fun createStub(psi: LuaLocalFuncDecl, parentStub: StubElement<out com.intellij.psi.PsiElement>?): LuaLocalFuncStub {
        // SYNTAX-18: a pinned partial `local function` decl may lack its nameRef; the hand-stubbed
        // getter is @NotNull, so read the child off the node instead of throwing.
        val name = psi.node.findChildByType(LuaElementTypes.NAME_REF)?.text ?: ""
        val catsComment = LuaPsiImplUtil.getCatsComment(psi)
        val returnType = catsComment?.getReturnTagList()?.flatMap { it.returnTypeDescriptorList }?.firstOrNull()?.argType?.text
        val paramTypes = catsComment?.getParamTagList()?.associate {
            val pName = it.argName?.text ?: ""
            val pType = it.argType.text
            pName to pType
        } ?: emptyMap()

        return LuaLocalFuncStubImpl(parentStub, name, returnType, paramTypes)
    }

    override fun getExternalId(): String = "lunar.local.func.decl"

    override fun serialize(stub: LuaLocalFuncStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.luacatsReturnType)
        dataStream.writeInt(stub.luacatsParamTypes.size)
        stub.luacatsParamTypes.forEach { (name, type) ->
            dataStream.writeName(name)
            dataStream.writeName(type)
        }
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): LuaLocalFuncStub {
        val name = dataStream.readName()?.string
        val returnType = dataStream.readName()?.string
        val paramCount = dataStream.readInt()
        val paramTypes = mutableMapOf<String, String>()
        repeat(paramCount) {
            val pName = dataStream.readName()?.string ?: ""
            val pType = dataStream.readName()?.string ?: ""
            paramTypes[pName] = pType
        }
        return LuaLocalFuncStubImpl(parentStub, name, returnType, paramTypes)
    }

    override fun indexStub(stub: LuaLocalFuncStub, sink: IndexSink) {
        // Local functions are not indexed in GlobalDeclarationIndex
    }
}
