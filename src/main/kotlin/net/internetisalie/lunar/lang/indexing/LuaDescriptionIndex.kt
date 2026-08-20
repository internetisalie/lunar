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
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.syntax.collectDescriptionText
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaDescriptionIndexName: @NonNls ID<String, String> = ID.create("lunar.luacats.descriptions")

class LuaDescriptionIndex : FileBasedIndexExtension<String, String>() {
    private val myExternalizer: DataExternalizer<String> = StringDataExternalizer()
    private val myIndexer: DataIndexer<String, String, FileContent> = Indexer()

    override fun getName(): ID<String, String> = LuaDescriptionIndexName

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = myExternalizer

    override fun getIndexer(): DataIndexer<String, String, FileContent> = myIndexer

    // BUG-408 changed the record encoding (separators are now percent-escaped), so previously
    // written values decode differently. Bumped to force a rebuild.
    // 3 -> 4 (BUG-436): the filter widened, so the index CONTENT changed. Without the bump a
    // persisted index keeps its `.lua`-only entries and the fix is invisible on any machine
    // that has indexed before it.
    override fun getVersion(): Int = 4

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
        ) {
            output.writeUTF(value)
        }

        override fun read(input: DataInput): String = input.readUTF()
    }

    private class Indexer : DataIndexer<String, String, FileContent> {
        override fun map(inputData: FileContent): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val psiFile = inputData.psiFile

            if (psiFile !is LuaFile) return result

            val fileUrl = inputData.file.url
            PsiTreeUtil.findChildrenOfType(psiFile, LuaCommentOwner::class.java).forEach { owner ->
                val catsComment = owner.catsComment ?: return@forEach
                val descriptionText = collectDescriptionText(catsComment)
                if (descriptionText.isBlank()) return@forEach

                val tokens =
                    descriptionText
                        .lowercase()
                        .split(Regex("[^a-zA-Z0-9_]+"))
                        .filter { it.length >= 2 }
                        .distinct()

                if (tokens.isEmpty()) return@forEach

                val rawName =
                    when (owner) {
                        is LuaLocalVarDecl ->
                            owner.attNameList
                                .firstOrNull()
                                ?.nameRef
                                ?.text
                        // BUG-436: null-safe, because widening the filter means this indexer now
                        // sees `.rockspec`/`.luacheckrc`/`.busted` too, and a generated `@NotNull`
                        // getter does NOT return null on a missing child — `notNullChild` calls
                        // `LOG.error` (`PsiElementBase:293`), i.e. a reported IDE exception. An
                        // indexer sees every file in the project and a file being edited is
                        // malformed most of the time (`local function repeat(…)` — `repeat` is a
                        // keyword — yields a `LuaLocalFuncDecl` with no name node at all). Same
                        // defect class as BUG-441; latent here until the version bump forced a
                        // rebuild.
                        is LuaFuncDecl -> PsiTreeUtil.getChildOfType(owner, LuaFuncName::class.java)?.text
                        is LuaLocalFuncDecl -> PsiTreeUtil.getChildOfType(owner, LuaNameRef::class.java)?.text
                        else -> null
                    } ?: owner.text

                val record = DescriptionRecord(rawName.take(50), fileUrl, owner.textOffset)

                for (token in tokens) {
                    // Concatenation goes through `join` as well as `encode`: hand-writing the
                    // separator here is what let the record format live in two places, which is the
                    // shape BUG-408 was.
                    result.merge(token, DescriptionRecord.join(listOf(record))) { existing, new ->
                        DescriptionRecord.concat(existing, new)
                    }
                }
            }

            return result
        }
    }

    companion object {
        val KEY: ID<String, String> = LuaDescriptionIndexName
    }
}

/**
 * One indexed description: the declaration's name, the file it lives in, and its offset.
 *
 * BUG-408: the record format lives here and **only** here. It was previously interpolated at the
 * writer (`"$ownerName\t$fileUrl\t$offset"`) and hand-split at every reader, which is the shape
 * that made BUG-407 dangerous — a delimiter in the payload silently changes the arity, and nothing
 * owns the contract.
 *
 * `ownerName` was already sanitised; **`fileUrl` was not**, and a tab, `|`, newline or carriage
 * return is legal in a POSIX filename (verified: `we\tird.lua` creates fine). Such a file produced a
 * record of the wrong arity, which the reader dropped via `if (parts.size != 3) continue` — so its
 * documentation vanished from Search Everywhere with no error anywhere.
 *
 * Sanitising the URL is not an option: it has to round-trip or the file cannot be reopened. The
 * separators are therefore **escaped**, not replaced.
 */
data class DescriptionRecord(
    val ownerName: String,
    val fileUrl: String,
    val offset: Int,
) {
    fun encode(): String = listOf(escape(ownerName), escape(fileUrl), offset.toString()).joinToString(FIELD)

    companion object {
        private const val FIELD = "\t"
        private const val RECORD = "|"

        /**
         * Percent-escapes the two separators plus the line breaks that would corrupt a record.
         *
         * `%` is escaped **first** and unescaped **last**, so the mapping is a bijection: without
         * that, a path containing the literal text `%09` would decode into a tab.
         */
        private fun escape(raw: String): String =
            raw
                .replace("%", "%25")
                .replace("\t", "%09")
                .replace("\n", "%0A")
                .replace("\r", "%0D")
                .replace("|", "%7C")

        private fun unescape(encoded: String): String =
            encoded
                .replace("%7C", "|")
                .replace("%0D", "\r")
                .replace("%0A", "\n")
                .replace("%09", "\t")
                .replace("%25", "%")

        /** Joins several records into one index value. */
        fun join(records: List<DescriptionRecord>): String = records.joinToString(RECORD) { it.encode() }

        /** Appends one already-encoded index value to another, using the same separator as [join]. */
        fun concat(
            first: String,
            second: String,
        ): String = first + RECORD + second

        /**
         * Every record in an index value. Malformed records are skipped rather than throwing: an
         * index built by an older plugin version is data we do not control.
         */
        fun parseAll(value: String): List<DescriptionRecord> =
            value.split(RECORD).mapNotNull { record ->
                val parts = record.split(FIELD)
                if (parts.size != 3) return@mapNotNull null
                val offset = parts[2].toIntOrNull() ?: return@mapNotNull null
                DescriptionRecord(unescape(parts[0]), unescape(parts[1]), offset)
            }
    }
}
