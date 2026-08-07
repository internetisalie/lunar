package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.stubs.*
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.impl.LuaLocalVarDeclImpl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalVarStub
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations

class LuaLocalVarStubElementType(debugName: String) :
    IStubElementType<LuaLocalVarStub, LuaLocalVarDecl>(debugName, LuaLanguage) {

    override fun createPsi(stub: LuaLocalVarStub): LuaLocalVarDecl {
        return LuaLocalVarDeclImpl(stub, this)
    }

    override fun createStub(psi: LuaLocalVarDecl, parentStub: StubElement<out com.intellij.psi.PsiElement>?): LuaLocalVarStub {
        val names = psi.attNameList.map { it.nameRef.text }
        val catsComment = LuaPsiImplUtil.getCatsComment(psi)
        
        val type = catsComment?.getTypeTagList()?.firstOrNull()?.argType?.text
        val classTag = catsComment?.getClassTagList()?.firstOrNull()
        val className = classTag?.argType?.text
        val extendsType = classTag?.parentTypes?.text?.removePrefix(":")?.trim()
        
        val aliasTag = catsComment?.getAliasTagList()?.firstOrNull()
        val aliasName = aliasTag?.argName?.text
        val aliasTarget = aliasTag?.argType?.text
        
        // MAINT-34-01: read through the one shared extractor rather than a private copy of the
        // rule. This branch and `materializeClass`'s were copy-paste siblings, and BUG-401 is what
        // that costs — the stub kept `---@field beta? number`'s marker in the member key, producing
        // a `beta?` no lookup could match, while the AST branch had always stripped it.
        val fields = catsComment
            ?.let { LuaCatsDeclarations.fieldMembers(it).associate { field -> field.name to field.typeName } }
            ?: emptyMap()
        
        return LuaLocalVarStubImpl(parentStub, names, type, className, aliasName, aliasTarget, extendsType, fields)
    }

    override fun getExternalId(): String = "lunar.local.var.decl"

    override fun serialize(stub: LuaLocalVarStub, dataStream: StubOutputStream) {
        dataStream.writeInt(stub.names.size)
        stub.names.forEach { dataStream.writeName(it) }
        dataStream.writeName(stub.luacatsType)
        dataStream.writeName(stub.luacatsClassName)
        dataStream.writeName(stub.luacatsAliasName)
        dataStream.writeName(stub.luacatsAliasTarget)
        dataStream.writeName(stub.luacatsExtends)
        dataStream.writeInt(stub.luacatsFields.size)
        stub.luacatsFields.forEach { (name, type) ->
            dataStream.writeName(name)
            dataStream.writeName(type)
        }
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): LuaLocalVarStub {
        val nameCount = dataStream.readInt()
        val names = mutableListOf<String>()
        repeat(nameCount) {
            dataStream.readName()?.string?.let { names.add(it) }
        }
        val type = dataStream.readName()?.string
        val className = dataStream.readName()?.string
        val aliasName = dataStream.readName()?.string
        val aliasTarget = dataStream.readName()?.string
        val extendsType = dataStream.readName()?.string
        val fieldCount = dataStream.readInt()
        val fields = mutableMapOf<String, String>()
        repeat(fieldCount) {
            val fName = dataStream.readName()?.string ?: ""
            val fType = dataStream.readName()?.string ?: ""
            fields[fName] = fType
        }
        return LuaLocalVarStubImpl(parentStub, names, type, className, aliasName, aliasTarget, extendsType, fields)
    }

    override fun indexStub(stub: LuaLocalVarStub, sink: IndexSink) {
        stub.luacatsClassName?.let { sink.occurrence(LuaClassNameIndex.KEY, it) }
        stub.luacatsAliasName?.let { sink.occurrence(LuaAliasIndex.KEY, it) }
    }
}
