package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import net.internetisalie.lunar.lang.LuaFileType
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

    // 1 -> 2 (BUG-436): the filter widened, so the index CONTENT changed. Without the bump a
    // persisted index keeps its `.lua`-only entries and the fix is invisible on any machine
    // that has indexed before it.
    override fun getVersion(): Int = 2

    override fun dependsOnFileContent(): Boolean = true

    override fun indexDirectories(): Boolean = false

    /**
     * BUG-436: derived from the file type, never re-stated as an extension. `plugin.xml:99-100`
     * registers `LuaFileType` for `extensions="lua;rockspec"` **and** `fileNames=".luacheckrc;.busted"`;
     * this filter used to read `file.extension == "lua"`, so three of the four registrations were
     * silently unindexed — absent, not stale, and absent in the direction no gate here looks.
     *
     * **Instantiated, never subclassed.** `RequiredIndexesEvaluator.toHint` turns this into a real
     * file-type predicate only when `filter.javaClass == DefaultFileTypeSpecificInputFilter::class.java`
     * — a subclass silently loses the hint and is evaluated per file instead. `LuaReceiverMemberIndex`
     * (fixed first, in `fcce5966`) is the worked example.
     */
    override fun getInputFilter(): FileBasedIndex.InputFilter =
        DefaultFileTypeSpecificInputFilter(LuaFileType)

    private class StringDataExternalizer : DataExternalizer<String> {
        override fun save(
            output: DataOutput,
            value: String,
        ) = output.writeUTF(value)

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
