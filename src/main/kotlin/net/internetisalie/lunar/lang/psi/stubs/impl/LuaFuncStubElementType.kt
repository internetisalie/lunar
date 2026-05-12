package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.*
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.impl.LuaFuncDeclImpl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.lang.psi.stubs.LuaFuncStub

class LuaFuncStubElementType(debugName: String) :
    IStubElementType<LuaFuncStub, LuaFuncDecl>(debugName, LuaLanguage) {

    override fun createPsi(stub: LuaFuncStub): LuaFuncDecl {
        return LuaFuncDeclImpl(stub, this)
    }

    override fun createStub(psi: LuaFuncDecl, parentStub: StubElement<out com.intellij.psi.PsiElement>?): LuaFuncStub {
        val name = psi.funcName.text
        val catsComment = LuaPsiImplUtil.getCatsComment(psi)
        val returnType = catsComment?.getReturnTagList()?.firstOrNull()?.argType?.text
        val paramTypes = catsComment?.getParamTagList()?.associate {
            val pName = it.argName?.text ?: ""
            val pType = it.argType.text
            pName to pType
        } ?: emptyMap()

        return LuaFuncStubImpl(parentStub, name, returnType, paramTypes)
    }

    override fun getExternalId(): String = "lunar.func.decl"

    override fun serialize(stub: LuaFuncStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.luacatsReturnType)
        dataStream.writeInt(stub.luacatsParamTypes.size)
        stub.luacatsParamTypes.forEach { (name, type) ->
            dataStream.writeName(name)
            dataStream.writeName(type)
        }
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): LuaFuncStub {
        val name = dataStream.readName()?.string
        val returnType = dataStream.readName()?.string
        val paramCount = dataStream.readInt()
        val paramTypes = mutableMapOf<String, String>()
        repeat(paramCount) {
            val pName = dataStream.readName()?.string ?: ""
            val pType = dataStream.readName()?.string ?: ""
            paramTypes[pName] = pType
        }
        return LuaFuncStubImpl(parentStub, name, returnType, paramTypes)
    }

    override fun indexStub(stub: LuaFuncStub, sink: IndexSink) {
        stub.name?.let { 
            sink.occurrence(LuaGlobalDeclarationIndex.KEY, it)
            // If it's a dotted name like 'cjson.decode', also index the base 'cjson'
            // to allow basic resolution of the module/table global.
            if (it.contains('.')) {
                sink.occurrence(LuaGlobalDeclarationIndex.KEY, it.substringBefore('.'))
            }
        }
    }
}
