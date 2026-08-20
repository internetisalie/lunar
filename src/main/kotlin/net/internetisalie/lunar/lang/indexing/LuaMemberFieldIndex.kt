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
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaFile
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaMemberFieldIndexId: @NonNls ID<String, String> = ID.create("lunar.member.field")

/**
 * File-based index of qualified member-field declarations (NAV-12-01): every assignment whose target
 * is a dotted `LuaVar` (`receiver.field = value`) is indexed by its qualified name `receiver.field`.
 *
 * Field assignments are not stubbed (only `LuaFuncDecl`/`LuaLocalVarDecl`/`LuaLocalFuncDecl` are), so
 * — like [LuaCatsTypeNameIndex] — this reads them straight from the PSI. Keying by the full qualified
 * name (not the bare member) is what keeps `package.path` from colliding with an unrelated `path.*`
 * module. The value is unused; navigation re-resolves the field identifier on demand
 * (see [net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation]).
 */
class LuaMemberFieldIndex : FileBasedIndexExtension<String, String>() {
    private val externalizer: DataExternalizer<String> = StringDataExternalizer()
    private val indexer: DataIndexer<String, String, FileContent> = Indexer()

    override fun getName(): ID<String, String> = LuaMemberFieldIndexId

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
            PsiTreeUtil.findChildrenOfType(psiFile, LuaAssignmentStatement::class.java).forEach { stmt ->
                stmt.varList.varList.forEach { target ->
                    dottedMemberName(target)?.let { result[it] = "" }
                }
            }
            return result
        }
    }

    companion object {
        val KEY: ID<String, String> = LuaMemberFieldIndexId
    }
}
