package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
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
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaReceiverMemberIndexId: @NonNls ID<String, List<LuaReceiverMember>> = ID.create("lunar.receiver.member")

/**
 * COMP-09-09's instrument: index **entries** handed to the value processor by the last enumeration
 * on this thread, and the **files** they came from.
 *
 * The acceptance criterion asks for a count, not a duration, and there is no platform metric for
 * "entries traversed" — a timing probe cannot answer it, because an implementation that scanned the
 * whole key space *quickly* would pass a latency gate while failing this one outright. This is the
 * cheapest thing that can: `processValues` calls back once per (key, file) pair it visits, so
 * counting the callbacks **is** the traversal count, taken at the only place the traversal happens.
 *
 * **Thread-local, not the DR-11 spike's `@Volatile` global.** A global mutable static on a hot path
 * is exactly the kind of thing that survives into shipped code because it is cheap, and two
 * concurrent completions would overwrite each other's counts. Completion runs one session per
 * thread, so per-thread counters stay attributable. Design §4.10b.
 *
 * **Only the union entry point records into it today.** `membersInFile` — and therefore
 * `globalMembership`, the door TC 9 measures — runs `processValues` without touching it, so design
 * §4.10b's assertion 4 has no instrument yet. Extending it to both entry points is Phase 1's work
 * (BL-5); `MemberEnumerationWorkBoundGateTest` holds the assertion that goes green when it lands.
 */
object LuaReceiverMemberWork {
    private val entryCount: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    private val fileCount: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    val entries: Int get() = entryCount.get()

    val files: Int get() = fileCount.get()

    fun reset() = record(0, 0)

    fun record(
        entriesTraversed: Int,
        filesVisited: Int,
    ) {
        entryCount.set(entriesTraversed)
        fileCount.set(filesVisited)
    }
}

/** One member a receiver declares in one file. [kind] serves the isColon filter and the icon. */
data class LuaReceiverMember(
    val name: String,
    val kind: Kind,
    val separator: Separator,
) {
    enum class Kind { FUNCTION, FIELD }

    enum class Separator { DOT, COLON }

    companion object {
        /**
         * DR-19b: the sentinel that says "this file binds the receiver to something the index cannot
         * see through" — `R = require(...)`, `R = f()`, `R = other`. Its presence means the index is
         * **not** authoritative for that receiver and the caller must use the type graph.
         *
         * A marker is needed because emptiness is not a sufficient test: `M = require("x")` plus
         * `function M.extra() end` leaves the index non-empty and still incomplete, which is round
         * five's BLOCKER 2 surviving its own first remedy.
         */
        const val OPAQUE_BINDING: String = "\u0000opaque"
    }
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

            // Source 4 (DR-19) — `R = { a = 1, b = 2 }`. Without this the index is blind to a
            // table-literal-bound global, and worse, PARTIALLY blind to the canonical module idiom
            // `M = { VERSION = "1" }` + `function M.f() end`, where source 1 makes the index
            // non-empty so an "empty means fall back" rule never fires and VERSION is silently lost.
            PsiTreeUtil.findChildrenOfType(psiFile, LuaAssignmentStatement::class.java).forEach { stmt ->
                val exprs = stmt.exprList.exprList
                stmt.varList.varList.forEachIndexed { i, target ->
                    if (target.varSuffixList.isNotEmpty()) return@forEachIndexed
                    val receiver = target.nameRef?.text ?: return@forEachIndexed
                    val literal = exprs.getOrNull(i) as? LuaTableConstructor ?: return@forEachIndexed
                    literal.fieldList?.fieldList?.forEach { field ->
                        val name = field.identifier?.text ?: return@forEach
                        val kind =
                            if (field.exprList.firstOrNull() is LuaFuncDef) {
                                LuaReceiverMember.Kind.FUNCTION
                            } else {
                                LuaReceiverMember.Kind.FIELD
                            }
                        byReceiver
                            .getOrPut(receiver) { mutableListOf() }
                            .add(LuaReceiverMember(name, kind, LuaReceiverMember.Separator.DOT))
                    }
                }
            }

            // DR-19b — mark a receiver whose binding the index cannot see through.
            PsiTreeUtil.findChildrenOfType(psiFile, LuaAssignmentStatement::class.java).forEach { stmt ->
                val exprs = stmt.exprList.exprList
                stmt.varList.varList.forEachIndexed { i, target ->
                    if (target.varSuffixList.isNotEmpty()) return@forEachIndexed
                    val receiver = target.nameRef?.text ?: return@forEachIndexed
                    val rhs = exprs.getOrNull(i)
                    if (rhs is LuaTableConstructor) return@forEachIndexed
                    byReceiver
                        .getOrPut(receiver) { mutableListOf() }
                        .add(
                            LuaReceiverMember(
                                LuaReceiverMember.OPAQUE_BINDING,
                                LuaReceiverMember.Kind.FIELD,
                                LuaReceiverMember.Separator.DOT,
                            ),
                        )
                }
            }

            // Source 3 — `---@field` on a `---@class R` comment.
            PsiTreeUtil.findChildrenOfType(psiFile, LuaCatsClassTag::class.java).forEach { tag ->
                // Nullable in practice on a partial `---@class` even though the generated getter is
                // not annotated — LuaTypeManagerImpl:348 reads the same accessor as `argType?.text`.
                val receiver = tag.argType?.text?.trim() ?: return@forEach
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
            var entries = 0
            var files = 0
            FileBasedIndex.getInstance().processValues(KEY, receiver, null, { _, members ->
                files++
                entries += members.size
                members.forEach { seen.putIfAbsent(it.name, it) }
                true
            }, scope)
            LuaReceiverMemberWork.record(entries, files)
            return seen.values.toList()
        }

        /**
         * DR-14 candidate for design §4.5 — the completion door's rule, built to be **measured**.
         *
         * It does not invent its own file selection. `typeOfGlobalIn` picks the declaring file from
         * `LuaGlobalAssignmentIndex` (bare top-level globals only — a *different* candidate set from
         * this index's keys), excludes the context file, and takes the first that yields a type.
         * This mirrors the first three of those and deliberately cannot mirror the fourth: skipping a
         * file whose global has no useful type requires the graph build the whole feature exists to
         * avoid. DR-14 measures what that costs.
         */
        fun membersOfGlobal(
            receiver: String,
            project: Project,
            exclude: PsiFile?,
        ): List<LuaReceiverMember> = globalMembership(receiver, project, exclude).members

        /**
         * What the index knows about [receiver], and **whether it is authoritative**.
         *
         * [Membership.authoritative] is false when the declaring file binds the receiver to something
         * the index cannot see through (DR-19b). Callers must then use the type graph; emptiness is
         * not a sufficient test, because a receiver can be opaquely bound *and* syntactically
         * extended.
         */
        data class Membership(
            val members: List<LuaReceiverMember>,
            val authoritative: Boolean,
            val found: Boolean,
        )

        fun globalMembership(
            receiver: String,
            project: Project,
            exclude: PsiFile?,
        ): Membership {
            candidates(receiver, project, GlobalSearchScope.projectScope(project), exclude)
                .takeIf { it.isNotEmpty() }
                ?.let { return membershipOver(receiver, project, it) }
            candidates(receiver, project, GlobalSearchScope.allScope(project), exclude)
                .takeIf { it.isNotEmpty() }
                ?.let { return membershipOver(receiver, project, it) }
            return Membership(emptyList(), authoritative = false, found = false)
        }

        /**
         * DR-19c: **authority is a property of the receiver, not of one file.** If ANY declaring file
         * in the chosen scope binds the receiver opaquely, the index is not authoritative — even if
         * the file selection happened to pick did not.
         *
         * Two DR-19 runs disagreed about `assert` before this: `getContainingFiles` is unordered, so
         * "the first declaring file" is not a stable choice, and an assertion added to the harness
         * caught the flip. Today's `typeOfGlobalIn` has the same non-determinism, but a rule that
         * *reports authority* must not inherit it — a wrong `authoritative = true` silently drops
         * members, where a wrong `false` only costs latency.
         *
         * Membership still comes from the first candidate, preserving `typeOfGlobalIn`'s behaviour.
         */
        private fun membershipOver(
            receiver: String,
            project: Project,
            files: List<VirtualFile>,
        ): Membership {
            val opaque =
                files.any { f ->
                    membersInFile(receiver, project, f).any {
                        it.name ==
                            LuaReceiverMember.OPAQUE_BINDING
                    }
                }
            val members =
                membersInFile(receiver, project, files.first())
                    .filter { it.name != LuaReceiverMember.OPAQUE_BINDING }
            return Membership(members, authoritative = !opaque, found = true)
        }

        private fun candidates(
            receiver: String,
            project: Project,
            scope: GlobalSearchScope,
            exclude: PsiFile?,
        ): List<VirtualFile> =
            FileBasedIndex
                .getInstance()
                .getContainingFiles(LuaGlobalAssignmentIndex.KEY, receiver, scope)
                .filter { vf -> exclude?.virtualFile != vf }

        private fun membersInFile(
            receiver: String,
            project: Project,
            file: VirtualFile,
        ): List<LuaReceiverMember> {
            val seen = LinkedHashMap<String, LuaReceiverMember>()
            FileBasedIndex.getInstance().processValues(KEY, receiver, file, { _, members ->
                members.forEach { seen.putIfAbsent(it.name, it) }
                true
            }, GlobalSearchScope.allScope(project))
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
