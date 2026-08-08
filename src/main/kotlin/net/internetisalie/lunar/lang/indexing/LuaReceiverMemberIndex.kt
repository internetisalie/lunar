package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaReceiverMemberIndexId: @NonNls ID<String, List<LuaReceiverMember>> = ID.create("lunar.receiver.member")

/** One member a receiver declares in one file. [kind] serves the isColon filter and the icon. */
data class LuaReceiverMember(
    val name: String,
    val kind: Kind,
    val separator: Separator,
) {
    enum class Kind { FUNCTION, FIELD }

    enum class Separator { DOT, COLON }
}

/**
 * COMP-09 DR-09 PROTOTYPE — receiver-keyed member enumeration.
 *
 * Exists to be **measured**, not to be believed. Design §4 was written twice from reading the code
 * and failed a Step 9 review both times (§4.9 D1–D3); this is the prototype that §4 must be
 * rewritten from. Nothing in the plugin consumes it yet.
 *
 * The value type is what `LuaMemberFieldIndex`'s `<String, String>` could not be: a
 * `FileBasedIndex` value is per (key, file), so key `wx` carries one list per declaring file and
 * [membersOf] unions them. Collection-valued precedent is [LuaFileBindingsIndex].
 */
class LuaReceiverMemberIndex : FileBasedIndexExtension<String, List<LuaReceiverMember>>() {
    private val externalizer: DataExternalizer<List<LuaReceiverMember>> = MemberListExternalizer()
    private val indexer: DataIndexer<String, List<LuaReceiverMember>, FileContent> = Indexer()

    override fun getName(): ID<String, List<LuaReceiverMember>> = LuaReceiverMemberIndexId

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<LuaReceiverMember>> = externalizer

    override fun getIndexer(): DataIndexer<String, List<LuaReceiverMember>, FileContent> = indexer

    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true

    override fun indexDirectories(): Boolean = false

    override fun getInputFilter(): FileBasedIndex.InputFilter = InputFilter()

    private class InputFilter : FileBasedIndex.InputFilter {
        override fun acceptInput(file: VirtualFile): Boolean = file.extension == "lua"
    }

    /**
     * Wire format: count, then per member `name`, `kind.ordinal`, `separator.ordinal`. Ordinals are
     * safe only because [getVersion] gates the format — reordering either enum requires a bump.
     */
    private class MemberListExternalizer : DataExternalizer<List<LuaReceiverMember>> {
        override fun save(
            output: DataOutput,
            value: List<LuaReceiverMember>,
        ) {
            output.writeInt(value.size)
            value.forEach {
                output.writeUTF(it.name)
                output.writeByte(it.kind.ordinal)
                output.writeByte(it.separator.ordinal)
            }
        }

        override fun read(input: DataInput): List<LuaReceiverMember> {
            val size = input.readInt()
            val members = ArrayList<LuaReceiverMember>(size)
            repeat(size) {
                val name = input.readUTF()
                val kind = LuaReceiverMember.Kind.entries[input.readByte().toInt()]
                val separator = LuaReceiverMember.Separator.entries[input.readByte().toInt()]
                members.add(LuaReceiverMember(name, kind, separator))
            }
            return members
        }
    }

    private class Indexer : DataIndexer<String, List<LuaReceiverMember>, FileContent> {
        override fun map(inputData: FileContent): Map<String, List<LuaReceiverMember>> {
            val psiFile = inputData.psiFile
            if (psiFile !is LuaFile) return emptyMap()
            val byReceiver = mutableMapOf<String, MutableList<LuaReceiverMember>>()

            // Source 1 — `function R.m()` / `function R:m()`. Read through FUNC_NAME, the same text
            // LuaFuncStubElementType.createStub uses, so the two agree by construction.
            PsiTreeUtil.findChildrenOfType(psiFile, LuaFuncDecl::class.java).forEach { decl ->
                val qualified = decl.node.findChildByType(LuaElementTypes.FUNC_NAME)?.text ?: return@forEach
                split(qualified)?.let { (receiver, name, separator) ->
                    byReceiver
                        .getOrPut(receiver) { mutableListOf() }
                        .add(LuaReceiverMember(name, LuaReceiverMember.Kind.FUNCTION, separator))
                }
            }

            // Source 2 — `R.f = value`, at any depth: a member assignment inside a function still
            // declares a member. Deliberately unlike LuaGlobalAssignmentIndex, which is top-level
            // only because a nested BARE assignment may target an enclosing local.
            PsiTreeUtil.findChildrenOfType(psiFile, LuaAssignmentStatement::class.java).forEach { stmt ->
                val exprs = stmt.exprList.exprList
                stmt.varList.varList.forEachIndexed { i, target ->
                    val (receiver, name) = dottedTarget(target) ?: return@forEachIndexed
                    // §4.9 D3: an assignment whose RHS is literally `function() end` declares a
                    // FUNCTION. `R.f = someOtherFn` cannot be classified without resolution and is
                    // recorded FIELD; the golden diff measures how much that costs.
                    val kind =
                        if (exprs.getOrNull(i) is LuaFuncDef) {
                            LuaReceiverMember.Kind.FUNCTION
                        } else {
                            LuaReceiverMember.Kind.FIELD
                        }
                    byReceiver
                        .getOrPut(receiver) { mutableListOf() }
                        .add(LuaReceiverMember(name, kind, LuaReceiverMember.Separator.DOT))
                }
            }

            // Source 3 — `---@field` on a `---@class R` comment.
            PsiTreeUtil.findChildrenOfType(psiFile, LuaCatsClassTag::class.java).forEach { tag ->
                val receiver = tag.argType.text.trim()
                val comment = PsiTreeUtil.getParentOfType(tag, LuaCatsComment::class.java) ?: return@forEach
                LuaCatsDeclarations.fieldMembers(comment).forEach { field ->
                    val kind =
                        if (field.typeName.trimStart().startsWith("fun(")) {
                            LuaReceiverMember.Kind.FUNCTION
                        } else {
                            LuaReceiverMember.Kind.FIELD
                        }
                    byReceiver
                        .getOrPut(receiver) { mutableListOf() }
                        .add(LuaReceiverMember(field.name, kind, LuaReceiverMember.Separator.DOT))
                }
            }

            return byReceiver
        }

        /** `R.f` → the receiver and member; null for a keyed or call suffix, or a nested qualifier. */
        private fun dottedTarget(target: LuaVar): Pair<String, String>? {
            val receiver = target.nameRef?.text ?: return null
            val suffix = target.varSuffixList.singleOrNull() ?: return null
            if (suffix.nameAndArgsList.isNotEmpty()) return null
            val name = suffix.indexExpr.nameRef?.text ?: return null
            return receiver to name
        }

        /**
         * The nested-qualifier rule, preserved from `LuaTypeManagerImpl.memberNameOf`: a member has
         * **exactly one** separator, so `a.b.c` contributes nothing to `a`.
         */
        private fun split(qualified: String): Triple<String, String, LuaReceiverMember.Separator>? {
            val dot = qualified.indexOf('.')
            val colon = qualified.indexOf(':')
            val at =
                when {
                    dot < 0 && colon < 0 -> return null
                    dot < 0 -> colon
                    colon < 0 -> dot
                    else -> minOf(dot, colon)
                }
            val remainder = qualified.substring(at + 1)
            if (remainder.contains('.') || remainder.contains(':')) return null
            if (remainder.isEmpty()) return null
            val separator =
                if (qualified[at] == ':') LuaReceiverMember.Separator.COLON else LuaReceiverMember.Separator.DOT
            return Triple(qualified.substring(0, at), remainder, separator)
        }
    }

    companion object {
        val KEY: ID<String, List<LuaReceiverMember>> = LuaReceiverMemberIndexId

        /** Every member of [receiver] visible in [scope], unioned across declaring files. */
        fun membersOf(
            receiver: String,
            project: Project,
            scope: GlobalSearchScope,
        ): List<LuaReceiverMember> {
            val seen = LinkedHashMap<String, LuaReceiverMember>()
            FileBasedIndex.getInstance().processValues(KEY, receiver, null, { _, members ->
                members.forEach { seen.putIfAbsent(it.name, it) }
                true
            }, scope)
            return seen.values.toList()
        }

        /** As [membersOf], but reporting which file each member came from — DR-09's D2 probe. */
        fun membersByFile(
            receiver: String,
            project: Project,
            scope: GlobalSearchScope,
        ): Map<VirtualFile, List<LuaReceiverMember>> {
            val byFile = LinkedHashMap<VirtualFile, List<LuaReceiverMember>>()
            FileBasedIndex.getInstance().processValues(KEY, receiver, null, { file, members ->
                byFile[file] = members
                true
            }, scope)
            return byFile
        }
    }
}
