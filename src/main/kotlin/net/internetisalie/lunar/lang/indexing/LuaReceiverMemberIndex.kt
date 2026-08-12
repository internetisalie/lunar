package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
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
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
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
 * **Both entry points record into it** (Phase 1, BL-5). Each entry point calls [reset] on the way in
 * and every `processValues` callback calls [recordVisit], so the totals describe one enumeration
 * however many files it read — which is what design §4.10b's assertion 4 needs, since it is stated
 * against `globalMembership` and that door reaches the index through `membersInFile`.
 */
object LuaReceiverMemberWork {
    private val entryCount: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    private val fileCount: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    val entries: Int get() = entryCount.get()

    val files: Int get() = fileCount.get()

    fun reset() {
        entryCount.set(0)
        fileCount.set(0)
    }

    /** One `processValues` callback: one file, and the entries it handed over. */
    internal fun recordVisit(entriesInFile: Int) {
        entryCount.set(entryCount.get() + entriesInFile)
        fileCount.set(fileCount.get() + 1)
    }
}

/** The indexer's accumulator: receiver name → the members it declares in the file being indexed. */
private typealias MemberSink = MutableMap<String, MutableList<LuaReceiverMember>>

private fun MemberSink.record(
    receiver: String,
    member: LuaReceiverMember,
) {
    getOrPut(receiver) { mutableListOf() }.add(member)
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
 * COMP-09 — receiver-keyed member enumeration. Design §4.2–§4.5c.
 *
 * Every figure in design §4 comes from a run against this class, not from a reading of it: §4 was
 * written twice from reading and failed a Step 9 review both times (§4.9 D1–D3). **Nothing in the
 * plugin consumes it yet** — the completion consumer is Phase 2 and materialization is Phase 3.
 *
 * The value type is what `LuaMemberFieldIndex`'s `<String, String>` could not be: a
 * `FileBasedIndex` value is per (key, file), so key `wx` carries one list per declaring file.
 * Collection-valued precedent is [LuaFileBindingsIndex].
 *
 * **Two entry points, two selection rules, and no third** (design §4.5). [membersOfGlobal] /
 * [globalMembership] serve completion — scope precedence, first declaring file, context excluded —
 * and [membersIn] serves materialization with the union over a scope. Collapsing them into one
 * `membersOf` is how review defects D1, D2 and B2 all happened, so that name deliberately does not
 * exist: each door is verified against the door it actually serves.
 */
class LuaReceiverMemberIndex : FileBasedIndexExtension<String, List<LuaReceiverMember>>() {
    private val externalizer: DataExternalizer<List<LuaReceiverMember>> = MemberListExternalizer()
    private val indexer: DataIndexer<String, List<LuaReceiverMember>, FileContent> = Indexer()

    override fun getName(): ID<String, List<LuaReceiverMember>> = LuaReceiverMemberIndexId

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<LuaReceiverMember>> = externalizer

    override fun getIndexer(): DataIndexer<String, List<LuaReceiverMember>, FileContent> = indexer

    /**
     * 1 → 2: the input filter widened from `extension == "lua"` to every registration `LuaFileType`
     * carries, so the index's **content** changed. Without the bump a persisted index keeps the
     * `.lua`-only entries and the fix is invisible on any machine that has indexed before it.
     */
    override fun getVersion(): Int = 2

    override fun dependsOnFileContent(): Boolean = true

    override fun indexDirectories(): Boolean = false

    /**
     * **Derived from the file type, not from a second list of extensions.** `plugin.xml` registers
     * `LuaFileType` for `extensions="lua;rockspec"` *and* `fileNames=".luacheckrc;.busted"`, and this
     * index's own filter re-stated only the first half of the first half: `file.extension == "lua"`.
     * Measured, a receiver whose members came from a `.rockspec`, a `.luacheckrc` and a `.busted`
     * beside its `.lua` file lost all three at the `@class` door
     * (`[fromBusted, fromLua, fromLuacheckrc, fromRockspec, same]` → `[fromLua, same]`), because the
     * `StubIndex` key scan Phase 3 replaced was filtered by the *stub* builder — which runs for the
     * file type — and this index was not.
     *
     * `DefaultFileTypeSpecificInputFilter` is instantiated directly rather than subclassed on
     * purpose: `RequiredIndexesEvaluator.toHint` turns it into a real file-type predicate only when
     * `filter.javaClass == DefaultFileTypeSpecificInputFilter::class.java` — "yes, we want to check
     * exact class" — because a subclass could override `acceptInput`. A subclass here would silently
     * degrade the filter to "accept everything" and lean on [Indexer]'s `psiFile !is LuaFile` guard
     * for correctness while offering every file in the project to this indexer.
     */
    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(LuaFileType)

    /**
     * What the index knows about a receiver, and **whether it is authoritative**.
     *
     * [authoritative] is false when a declaring file binds the receiver to something the index cannot
     * see through (DR-19b). Callers must then use the type graph; emptiness is not a sufficient test,
     * because a receiver can be opaquely bound *and* syntactically extended — `OM = require("x")`
     * plus `function OM.extra() end` leaves the index non-empty and incomplete, which is how the
     * first two remedies for design §4.5c both failed.
     *
     * Declared on the class rather than inside the companion so consumers can name the type
     * (`LuaReceiverMemberIndex.Membership`) without going through `Companion`.
     */
    data class Membership(
        val members: List<LuaReceiverMember>,
        val authoritative: Boolean,
        val found: Boolean,
    )

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
            indexFunctionDeclarations(psiFile, byReceiver)
            indexMemberAssignments(psiFile, byReceiver)
            indexTableLiteralFields(psiFile, byReceiver)
            indexOpaqueBindings(psiFile, byReceiver)
            indexClassFields(psiFile, byReceiver)
            return byReceiver
        }

        /**
         * Source 1 — `function R.m()` / `function R:m()`. Read through FUNC_NAME, the same text
         * `LuaFuncStubElementType.createStub` uses, so the two agree by construction.
         */
        private fun indexFunctionDeclarations(
            psiFile: LuaFile,
            byReceiver: MemberSink,
        ) {
            PsiTreeUtil.findChildrenOfType(psiFile, LuaFuncDecl::class.java).forEach { decl ->
                val qualified = decl.node.findChildByType(LuaElementTypes.FUNC_NAME)?.text ?: return@forEach
                split(qualified)?.let { (receiver, name, separator) ->
                    byReceiver.record(receiver, LuaReceiverMember(name, LuaReceiverMember.Kind.FUNCTION, separator))
                }
            }
        }

        /**
         * Source 2 — `R.f = value`, at any depth: a member assignment inside a function still
         * declares a member. Deliberately unlike `LuaGlobalAssignmentIndex`, which is top-level only
         * because a nested BARE assignment may target an enclosing local.
         *
         * §4.9 D3: an assignment whose RHS is literally `function() end` declares a FUNCTION.
         * `R.f = someOtherFn` cannot be classified without resolution and is recorded FIELD; the
         * golden diff measures how much that costs.
         */
        private fun indexMemberAssignments(
            psiFile: LuaFile,
            byReceiver: MemberSink,
        ) {
            forEachAssignedTarget(psiFile) { target, assigned ->
                val (receiver, name) = dottedTarget(target) ?: return@forEachAssignedTarget
                byReceiver.record(receiver, LuaReceiverMember(name, kindOf(assigned), LuaReceiverMember.Separator.DOT))
            }
        }

        /**
         * Source 4 (DR-19) — `R = { a = 1, b = 2 }`. Without this the index is blind to a
         * table-literal-bound global, and worse, PARTIALLY blind to the canonical module idiom
         * `M = { VERSION = "1" }` + `function M.f() end`, where source 1 makes the index non-empty
         * so an "empty means fall back" rule never fires and VERSION is silently lost.
         */
        private fun indexTableLiteralFields(
            psiFile: LuaFile,
            byReceiver: MemberSink,
        ) {
            forEachBareBinding(psiFile) { receiver, bound ->
                val literal = bound as? LuaTableConstructor ?: return@forEachBareBinding
                literal.fieldList?.fieldList?.forEach { field ->
                    val name = field.identifier?.text ?: return@forEach
                    val member =
                        LuaReceiverMember(name, kindOf(field.exprList.firstOrNull()), LuaReceiverMember.Separator.DOT)
                    byReceiver.record(receiver, member)
                }
            }
        }

        /**
         * DR-19b — mark a receiver whose binding the index cannot see through, at any depth.
         *
         * Any depth is deliberate (design §4.5c): a missed sentinel marks a receiver authoritative
         * when it is not and silently drops whatever only the type graph knows, where a spurious one
         * only costs latency.
         */
        private fun indexOpaqueBindings(
            psiFile: LuaFile,
            byReceiver: MemberSink,
        ) {
            forEachBareBinding(psiFile) { receiver, bound ->
                if (bound is LuaTableConstructor) return@forEachBareBinding
                byReceiver.record(
                    receiver,
                    LuaReceiverMember(
                        LuaReceiverMember.OPAQUE_BINDING,
                        LuaReceiverMember.Kind.FIELD,
                        LuaReceiverMember.Separator.DOT,
                    ),
                )
            }
        }

        /** Source 3 — `---@field` on a `---@class R` comment. */
        private fun indexClassFields(
            psiFile: LuaFile,
            byReceiver: MemberSink,
        ) {
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
                    byReceiver.record(receiver, LuaReceiverMember(field.name, kind, LuaReceiverMember.Separator.DOT))
                }
            }
        }

        /** Every assignment target in the file, paired with the expression positionally bound to it. */
        private fun forEachAssignedTarget(
            psiFile: LuaFile,
            visit: (LuaVar, LuaExpr?) -> Unit,
        ) {
            PsiTreeUtil.findChildrenOfType(psiFile, LuaAssignmentStatement::class.java).forEach { stmt ->
                val exprs = stmt.exprList.exprList
                stmt.varList.varList.forEachIndexed { i, target -> visit(target, exprs.getOrNull(i)) }
            }
        }

        /** As [forEachAssignedTarget], restricted to a suffix-free `R = <expr>` — a whole-receiver bind. */
        private fun forEachBareBinding(
            psiFile: LuaFile,
            visit: (String, LuaExpr?) -> Unit,
        ) {
            forEachAssignedTarget(psiFile) { target, assigned ->
                if (target.varSuffixList.isNotEmpty()) return@forEachAssignedTarget
                val receiver = target.nameRef?.text ?: return@forEachAssignedTarget
                visit(receiver, assigned)
            }
        }

        private fun kindOf(assigned: LuaExpr?): LuaReceiverMember.Kind =
            if (assigned is LuaFuncDef) LuaReceiverMember.Kind.FUNCTION else LuaReceiverMember.Kind.FIELD

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

        /**
         * **Materialization door** (design §4.6): every member of [receiver] visible in [scope],
         * unioned across every declaring file. `addMethodsOf` genuinely wants all of them (BUG-399).
         *
         * This is the union the prototype called `membersOf`. It is **not** the completion rule —
         * DR-14 measured the union as a membership superset at that call site (defect D2), so the
         * name `membersOf` no longer exists and each door has to be asked for by name.
         *
         * Returns empty while [DumbService] reports dumb (design §4.9, DR-10): the platform throws
         * `IndexNotReadyException` from `processValues` during indexing, and today's completion
         * quietly offers `[]` rather than reporting a crash.
         *
         * **The `checkCanceled()` below has no regression test, and that is a measurement rather
         * than an omission.** Design §4.9 requires it and it stays, but it cannot be gated from
         * outside: probed on gce-builder 2026-08-09, `processValues` under a cancelled indicator
         * throws `ProcessCanceledException` **before invoking any callback** — the probe's raw
         * callback never ran, while the counter showed `reset()` had happened and `files == 0`. A
         * test asserting the throw passes with this line deleted, which is the vacuous shape Phase
         * 0's remediation was about, so it was written, measured and removed rather than kept as a
         * gate that cannot fail.
         */
        fun membersIn(
            receiver: String,
            project: Project,
            scope: GlobalSearchScope,
        ): List<LuaReceiverMember> {
            LuaReceiverMemberWork.reset()
            if (DumbService.isDumb(project)) return emptyList()
            val seen = LinkedHashMap<String, LuaReceiverMember>()
            FileBasedIndex.getInstance().processValues(KEY, receiver, null, { _, members ->
                ProgressManager.checkCanceled()
                LuaReceiverMemberWork.recordVisit(members.size)
                members.forEach { seen.putIfAbsent(it.name, it) }
                true
            }, scope)
            return seen.values.filterNot { it.name == LuaReceiverMember.OPAQUE_BINDING }
        }

        /**
         * **Completion door** (design §4.5) — the members [globalMembership] found, discarding the
         * authority flag. Callers that must decide between the index and the type graph want
         * [globalMembership] instead; this is for callers that only want the names.
         *
         * It does not invent its own file selection. `typeOfGlobalIn` picks the declaring file from
         * `LuaGlobalAssignmentIndex` (bare top-level globals only — a *different* candidate set from
         * this index's keys), excludes the context file, and takes the first that yields a type.
         * This mirrors the first three of those and deliberately cannot mirror the fourth: skipping a
         * file whose global has no useful type requires the graph build the whole feature exists to
         * avoid. DR-14 measured that residual at nothing on the golden receivers.
         *
         * [exclude] must be `context.containingFile?.originalFile` and not `containingFile`
         * (design §4.5, BL-8): during completion the PSI file is a **copy** with its own
         * `VirtualFile`, so the plain `containingFile` matches no candidate and the exclusion
         * silently never fires.
         */
        fun membersOfGlobal(
            receiver: String,
            project: Project,
            exclude: PsiFile?,
        ): List<LuaReceiverMember> = globalMembership(receiver, project, exclude).members

        /**
         * While dumb the answer is `Membership(emptyList(), authoritative = true, found = false)` —
         * design §4.9, measured by DR-10.
         *
         * `authoritative = true` is deliberate and is the whole point of returning rather than
         * throwing: it stops the caller taking §4.5c's graph fallback, which is itself dumb-guarded
         * (`LuaTypeManagerImpl:129`) and would return null anyway. The user sees `[]`, which is what
         * completion offers today. The prototype threw `IndexNotReadyException` here, and matching
         * `resolveType` instead would have propagated BUG-432 — an index exception logged through
         * `Logger.error`, i.e. a crash report shown while the IDE is merely indexing.
         */
        fun globalMembership(
            receiver: String,
            project: Project,
            exclude: PsiFile?,
        ): Membership {
            LuaReceiverMemberWork.reset()
            if (DumbService.isDumb(project)) return Membership(emptyList(), authoritative = true, found = false)
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
         *
         * Each candidate is read **once** and the per-file lists are reused for both questions. The
         * earlier shape asked `membersInFile` for the first file twice — once inside the opacity
         * `any`, once for membership — which double-counted that file in `LuaReceiverMemberWork` the
         * moment the counter reached this door, and design §4.10b's assertion 4 pins it at one.
         */
        private fun membershipOver(
            receiver: String,
            project: Project,
            files: List<VirtualFile>,
        ): Membership {
            val perFile = files.map { membersInFile(receiver, project, it) }
            val opaque = perFile.any { declared -> declared.any { it.name == LuaReceiverMember.OPAQUE_BINDING } }
            val members = perFile.first().filterNot { it.name == LuaReceiverMember.OPAQUE_BINDING }
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
                ProgressManager.checkCanceled()
                LuaReceiverMemberWork.recordVisit(members.size)
                members.forEach { seen.putIfAbsent(it.name, it) }
                true
            }, GlobalSearchScope.allScope(project))
            return seen.values.toList()
        }
    }
}
