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
        // BUG-402: stored as a LIST. Flattening to `parentTypes.text` forced the reader to re-split
        // on ',', which cuts a parameterized parent (`Base<string, number>`) in half.
        val parents = classTag?.let { LuaCatsDeclarations.parentTypeNames(it) }.orEmpty()
        
        // The alias NAME is read only here — materializeAlias takes it from the index key — so it
        // stays inline. The TARGET has two readers and so goes through the shared one (MAINT-34-04).
        val aliasName = catsComment?.getAliasTagList()?.firstOrNull()?.argName?.text
        val aliasTarget = catsComment?.let { LuaCatsDeclarations.aliasTarget(it) }
        
        // MAINT-34-01: read through the one shared extractor rather than a private copy of the
        // rule. This branch and `materializeClass`'s were copy-paste siblings, and BUG-401 is what
        // that costs — the stub kept `---@field beta? number`'s marker in the member key, producing
        // a `beta?` no lookup could match, while the AST branch had always stripped it.
        val fields = catsComment
            ?.let { LuaCatsDeclarations.fieldMembers(it).associate { field -> field.name to field.typeName } }
            ?: emptyMap()

        // Named arguments are mandatory at both construction sites, not a style choice:
        // `luacatsParents` is now `List<String>`, type-identical to `names`, so transposing the two
        // would compile silently and corrupt both the declared names and the parent list.
        return LuaLocalVarStubImpl(
            parent = parentStub,
            names = names,
            luacatsType = type,
            luacatsClassName = className,
            luacatsAliasName = aliasName,
            luacatsAliasTarget = aliasTarget,
            luacatsParents = parents,
            luacatsFields = fields,
        )
    }

    override fun getExternalId(): String = "lunar.local.var.decl"

    override fun serialize(stub: LuaLocalVarStub, dataStream: StubOutputStream) {
        dataStream.writeInt(stub.names.size)
        stub.names.forEach { dataStream.writeName(it) }
        dataStream.writeName(stub.luacatsType)
        dataStream.writeName(stub.luacatsClassName)
        dataStream.writeName(stub.luacatsAliasName)
        dataStream.writeName(stub.luacatsAliasTarget)
        dataStream.writeInt(stub.luacatsParents.size)
        stub.luacatsParents.forEach { dataStream.writeName(it) }
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
        val parentCount = dataStream.readInt()
        val parents = mutableListOf<String>()
        repeat(parentCount) { dataStream.readName()?.string?.let { parents.add(it) } }
        val fieldCount = dataStream.readInt()
        val fields = mutableMapOf<String, String>()
        repeat(fieldCount) {
            val fName = dataStream.readName()?.string ?: ""
            val fType = dataStream.readName()?.string ?: ""
            fields[fName] = fType
        }
        return LuaLocalVarStubImpl(
            parent = parentStub,
            names = names,
            luacatsType = type,
            luacatsClassName = className,
            luacatsAliasName = aliasName,
            luacatsAliasTarget = aliasTarget,
            luacatsParents = parents,
            luacatsFields = fields,
        )
    }

    override fun indexStub(stub: LuaLocalVarStub, sink: IndexSink) {
        stub.luacatsClassName?.let { sink.occurrence(LuaClassNameIndex.KEY, it) }
        stub.luacatsAliasName?.let { sink.occurrence(LuaAliasIndex.KEY, it) }
    }
}
