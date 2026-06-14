package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgName
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaCatsTypeNameIndexId: @NonNls ID<String, String> = ID.create("lunar.luacats.typename")

/**
 * File-based index of every LuaCATS type name — both `@class` (NAV-03-01) and `@alias` (NAV-03-04).
 *
 * Unlike the stub indexes (`LuaClassNameIndex` / `LuaAliasIndex`), which read the name off the stub of
 * whatever `LuaLocalVarDecl` a tag happens to sit above, this reads it straight from the
 * `LuaCatsClassTag` / `LuaCatsAliasTag`. So a *bare* `--- @class Name` / `--- @alias Name` (the normal
 * LuaCATS form, with no following `local Name = {}`) is indexed too. The value is unused; navigation
 * re-resolves the tag PSI on demand and derives the kind/icon from it (see
 * [net.internetisalie.lunar.lang.navigation.LuaCatsTypeNavigation]).
 *
 * Note the name-slot asymmetry: a class name is the tag's `LuaCatsArgType`, an alias name its
 * `LuaCatsArgName` (mirrors `LuaLocalVarStubElementType.createStub`).
 */
class LuaCatsTypeNameIndex : FileBasedIndexExtension<String, String>() {
    private val externalizer: DataExternalizer<String> = StringDataExternalizer()
    private val indexer: DataIndexer<String, String, FileContent> = Indexer()

    override fun getName(): ID<String, String> = LuaCatsTypeNameIndexId
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<String> = externalizer
    override fun getIndexer(): DataIndexer<String, String, FileContent> = indexer
    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true
    override fun indexDirectories(): Boolean = false

    override fun getInputFilter(): FileBasedIndex.InputFilter = InputFilter()

    private class InputFilter : FileBasedIndex.InputFilter {
        override fun acceptInput(file: VirtualFile): Boolean = file.extension == "lua"
    }

    private class StringDataExternalizer : DataExternalizer<String> {
        override fun save(output: DataOutput, value: String) = output.writeUTF(value)
        override fun read(input: DataInput): String = input.readUTF()
    }

    private class Indexer : DataIndexer<String, String, FileContent> {
        override fun map(inputData: FileContent): Map<String, String> {
            val psiFile = inputData.psiFile
            if (psiFile !is LuaFile) return emptyMap()
            val result = mutableMapOf<String, String>()
            PsiTreeUtil.findChildrenOfType(psiFile, LuaCatsClassTag::class.java).forEach { tag ->
                val name = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java)?.text?.trim()
                if (!name.isNullOrEmpty()) result[name] = ""
            }
            PsiTreeUtil.findChildrenOfType(psiFile, LuaCatsAliasTag::class.java).forEach { tag ->
                val name = PsiTreeUtil.getChildOfType(tag, LuaCatsArgName::class.java)?.text?.trim()
                if (!name.isNullOrEmpty()) result[name] = ""
            }
            return result
        }
    }

    companion object {
        val KEY: ID<String, String> = LuaCatsTypeNameIndexId
    }
}
