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
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaAliasNameIndexId: @NonNls ID<String, String> = ID.create("lunar.luacats.alias.name")

/**
 * File-based index of every LuaCATS `@alias` name (NAV-03-04).
 *
 * Unlike [LuaAliasIndex] — a stub index keyed on the `LuaLocalVarDecl` a `@alias` happens to sit
 * above — this captures the alias straight from its `LuaCatsAliasTag`, so a *bare*
 * `--- @alias Name type` comment (the normal LuaCATS form, with no following `local Name = {}`) is
 * found too. The value is unused; navigation re-resolves the tag PSI on demand
 * (see [net.internetisalie.lunar.lang.navigation.LuaAliasNavigation]).
 */
class LuaAliasNameIndex : FileBasedIndexExtension<String, String>() {
    private val externalizer: DataExternalizer<String> = StringDataExternalizer()
    private val indexer: DataIndexer<String, String, FileContent> = Indexer()

    override fun getName(): ID<String, String> = LuaAliasNameIndexId
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
            PsiTreeUtil.findChildrenOfType(psiFile, LuaCatsAliasTag::class.java).forEach { tag ->
                val name = PsiTreeUtil.getChildOfType(tag, LuaCatsArgName::class.java)?.text?.trim()
                if (!name.isNullOrEmpty()) result[name] = ""
            }
            return result
        }
    }

    companion object {
        val KEY: ID<String, String> = LuaAliasNameIndexId
    }
}
