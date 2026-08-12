package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Public interface for querying the inferred type of any PSI element in a file.
 * Consumers (IDE surfaces: hover, inlay hints, inspections) only use this interface;
 * they never touch [LuaTypeGraph] directly.
 *
 * Obtain via [LuaTypesSnapshot.forFile].
 *
 * See: docs/requirements/spec/type/design/phase-1-api-contracts.md §5
 */
interface LuaTypes {
    /**
     * Returns the inferred [LuaGraphType] for [element], or [LuaGraphType.Undefined] if
     * the element has no inferred type (was not visited or has no useful constraint).
     */
    fun getValueType(element: PsiElement): LuaGraphType

    /**
     * Returns all type errors detected in this file.  Phase 1: always empty
     * (checkTypes() is only wired in Phase 3+).
     */
    fun getErrors(): List<ElementError>

    /** Convert the graph-internal type back to a Layer-1 [LuaType] for IDE display. */
    fun graphTypeToLuaType(type: LuaGraphType): LuaType

    /** Returns the inferred return type of the file. */
    fun getFileReturnType(): LuaGraphType

    /**
     * BUG-395: the type this file gives the **global** [name], or [LuaGraphType.Undefined] if the
     * file declares no such global.
     *
     * Distinct from [getValueType], which is keyed on a PSI element and so can only answer about
     * *this* file's own references. A global is the one thing a file publishes to every other file,
     * so it needs a name-keyed query — that is what
     * [LuaTypeManager.resolveGlobal] reads to type a global assigned somewhere else.
     */
    fun getGlobalType(name: String): LuaGraphType
}

/**
 * Immutable snapshot of the type graph for a single file.  Created and cached by
 * [LuaTypesSnapshot.forFile].
 */
class LuaTypesSnapshot(
    private val graph: LuaTypeGraph,
    /** Maps each PSI element to the graph node that represents its inferred type. */
    private val elementNodes: Map<PsiElement, List<TypeNode>>,
    private val fileReturnType: LuaGraphType = LuaGraphType.Any,
    /** The file this snapshot was built for — the context handle for nominal type resolution. */
    private val contextFile: PsiFile? = null,
    /** File-scope globals this file declares, by name (BUG-395). */
    private val globalNodes: Map<String, VariableNode> = emptyMap(),
) : LuaTypes {
    override fun getFileReturnType(): LuaGraphType = fileReturnType

    override fun getValueType(element: PsiElement): LuaGraphType = typeOf(elementNodes[element]?.firstOrNull())

    override fun getGlobalType(name: String): LuaGraphType = typeOf(globalNodes[name])

    private fun typeOf(node: TypeNode?): LuaGraphType {
        if (node == null) return LuaGraphType.Undefined
        return when (node) {
            is VariableNode -> {
                val write = node.write
                val read = node.read
                if (write is LuaGraphType.Table && read is LuaGraphType.Table) {
                    val mergedMembers = mutableMapOf<String, VariableNode>()
                    mergedMembers.putAll(write.localMembers)
                    mergedMembers.putAll(read.localMembers)
                    LuaGraphType.Table(
                        write.className ?: read.className,
                        mergedMembers,
                        write.superTypes,
                        write.isExact,
                    )
                } else if (write != LuaGraphType.Undefined) {
                    write
                } else {
                    // asInferred: an un-assigned variable is typed by what is demanded of it, and
                    // an operator demand is a trait — which is demand-only and must be reported as
                    // the primitive it stands for (BUG-423).
                    if (read != LuaGraphType.Any) read.asInferred() else LuaGraphType.Undefined
                }
            }
            is ValueNode -> node.write
            // asInferred: a bare use node's demand may be an operator trait, which is demand-only
            // and must never be reported as a type (BUG-423).
            is UseNode -> node.read.asInferred()
            else -> LuaGraphType.Undefined
        }
    }

    override fun getErrors(): List<ElementError> = graph.errors

    override fun graphTypeToLuaType(type: LuaGraphType): LuaType = graphTypeToLuaType(type, mutableMapOf())

    /**
     * MAINT-25-02: cycle-safe conversion. Mirrors [LuaGraphType.fromLuaType]: register a placeholder
     * in [visited] before recursing into a structural type's members, so a self-referential graph
     * type (`t.self = t`) resolves the cycle-back reference to the in-construction placeholder
     * instead of recursing forever (StackOverflowError). Scalar heads cannot cycle — returned directly.
     */
    private fun graphTypeToLuaType(
        type: LuaGraphType,
        visited: MutableMap<LuaGraphType, LuaType>,
    ): LuaType {
        visited[type]?.let { return it }
        return when (type) {
            LuaGraphType.Any -> LuaPrimitiveType.ANY
            LuaGraphType.Undefined -> LuaPrimitiveType.UNKNOWN
            LuaGraphType.Nil -> LuaPrimitiveType.NIL
            LuaGraphType.Boolean -> LuaPrimitiveType.BOOLEAN
            LuaGraphType.Number -> LuaPrimitiveType.NUMBER
            LuaGraphType.String -> LuaPrimitiveType.STRING
            is LuaGraphType.Table -> tableToLuaType(type, visited)
            is LuaGraphType.Function -> {
                // Placeholder BEFORE recursing, as tableToLuaType does. MAINT-25-02 made tables
                // cycle-safe this way and left functions registering only on the way out, so a
                // function type reachable from its own parameter or return recursed until the stack
                // died. Measured: `(setfenv and rawlen)(setfenv and rawlen)` — a luacheck sample —
                // threw StackOverflowError as soon as BUG-427 made those globals resolvable.
                // A cycle-back reference degrades to the opaque `function`, never to a crash.
                visited[type] = LuaPrimitiveType.FUNCTION
                functionToLuaType(type, visited).also { visited[type] = it }
            }
            is LuaGraphType.Array ->
                LuaArrayType(graphTypeToLuaType(type.elementType, visited)).also { visited[type] = it }
            is LuaGraphType.Union -> {
                val luaTypes = type.types.map { graphTypeToLuaType(it, visited) }.toSet()
                LuaUnionType(luaTypes).also { visited[type] = it }
            }
            is LuaGraphType.Generic -> LuaGenericType(type.name)
            // One of the two presentation boundaries where an operator trait becomes the primitive
            // it stands for (BUG-423). Reached for real: both inlay-hint providers type a
            // parameter from its `read` and convert through here, so this is what keeps
            // `function double(n) return n * 2 end` hinting `n : number`.
            is LuaGraphType.Trait -> graphTypeToLuaType(type.inferredAs, visited)
        }
    }

    private fun tableToLuaType(
        type: LuaGraphType.Table,
        visited: MutableMap<LuaGraphType, LuaType>,
    ): LuaType {
        val members = LinkedHashMap<String, LuaTypeMember>()
        if (type.className != null) {
            val nominal =
                contextFile?.let {
                    LuaTypeManager.getInstance(it.project).resolveType(type.className, it)
                }
            val superTypes = (nominal as? LuaClassType)?.superTypes ?: emptyList()
            val placeholder = LuaClassType(type.className, superTypes, members)
            visited[type] = placeholder
            // Enrich the graph-derived class with nominal members (incl. methods + supertypes) from
            // the type manager, so method-aware members reach nominal consumers such as
            // LuaParameterInlayHintsProvider.resolveMember and the NAV-05/06 hierarchy walk.
            (nominal as? LuaClassType)?.let { members.putAll(it.getMembers()) }
            type.getMembers().forEach { (name, node) ->
                members[name] = LuaTypeMember(name, graphTypeToLuaType(node.write, visited)) // graph members win
            }
            return placeholder
        }
        val placeholder = LuaTableLiteralType(members)
        visited[type] = placeholder
        type.getMembers().forEach { (name, node) ->
            members[name] = LuaTypeMember(name, graphTypeToLuaType(node.write, visited))
        }
        return placeholder
    }

    private fun functionToLuaType(
        type: LuaGraphType.Function,
        visited: MutableMap<LuaGraphType, LuaType>,
    ): LuaType {
        val params =
            type.params.map { p ->
                val name =
                    p.name ?: when (val el = p.node.element) {
                        is net.internetisalie.lunar.lang.psi.LuaNameRef -> el.text
                        is net.internetisalie.lunar.lang.psi.LuaAttName -> el.nameRef.text
                        else -> "p"
                    }
                LuaParameter(name, graphTypeToLuaType(p.node.write, visited), p.isOptional, p.isVararg)
            }
        val returnType =
            type.returns.firstOrNull()?.let { graphTypeToLuaType(it.write, visited) } ?: LuaPrimitiveType.VOID
        return LuaFunctionType(params, returnType)
    }

    companion object {
        /**
         * Compute (or return a cached) [LuaTypes] snapshot for [file].
         *
         * MAINT-30-02 (§2.3/§3.4): memoized via [CachedValuesManager]. Dependencies are the file
         * itself + a churn signal — [PsiModificationTracker.MODIFICATION_COUNT] for every file
         * TYPE-11 does not pin (any reparse — the FileUserData text-hash-collision staleness is
         * structurally impossible now) — and the project's
         * [State.targetModificationTracker], so a text-free REDIS↔Lua target switch also invalidates
         * (REDIS-04 §3.1a / TC-04). The TYPE-10 [inProgressSnapshot] reentrancy guard runs FIRST,
         * ahead of `getCachedValue`, so a re-entrant `visitFuncCall → forFile` never recurses into a
         * nested `getCachedValue` compute (TC-06).
         *
         * TYPE-11 §2.3/§3.3/§3.7: the build runs inside a [LuaTypeSourceRecorder] frame, the frame
         * is registered against the snapshot it produced, and [churnDependencyFor] decides whether
         * this file rebuilds on every PSI tick (today's behaviour) or only on a generation tick.
         * The [inProgressSnapshot] guard still runs first — it is the cycle-breaker — but it now
         * **reports before it returns** (§3.1 step 5c, §1.10 V1): the hit is served for whichever
         * file is under construction, which is not necessarily the frame currently open, so the
         * outer file must be judged unpinnable by §3.3 step 6.
         */
        fun forFile(file: PsiFile): LuaTypes {
            val psiFile = file.containingFile
            LuaTypesVisitor.inProgressSnapshot(psiFile)?.let { inProgressTypes ->
                if (LuaTypeSourceRecorder.depth() > 0) LuaTypeSourceRecorder.reportInProgressHit(psiFile)
                return inProgressTypes
            }
            var providerRan = false
            val cachedTypes =
                CachedValuesManager.getCachedValue(psiFile, LuaTypesVisitor.KEY) {
                    providerRan = true
                    val (builtTypes, sourceFrame) =
                        LuaTypeSourceRecorder.recording { LuaTypesVisitor.buildSnapshot(psiFile) }
                    LuaTypeSourceRecorder.snapshotFrames[builtTypes] = sourceFrame
                    CachedValueProvider.Result.create(builtTypes, *dependenciesFor(psiFile, sourceFrame))
                }
            if (!providerRan && LuaTypeSourceRecorder.depth() > 0) {
                LuaTypeSourceRecorder.reportWarmSnapshot(psiFile, cachedTypes)
            }
            return cachedTypes
        }

        /**
         * TYPE-11 §3.3 step 9 as **one assertable value** — everything [forFile]'s
         * `CachedValueProvider.Result` depends on, in one place.
         *
         * §1.11 asserted that TC-2c ("tick roots, the pinned instance changed; edit a project file,
         * it did not") was the only case able to catch a pinnable branch that computes the right
         * churn object and then never passes it to `Result.create`. **Measured on this build, that
         * is false**: under exactly that mutation TC-2c stays green, because a roots change moves
         * the library `PsiFile`'s own `modificationStamp` (probed: `0 -> 1` on the *same* instance)
         * and [forFile] depends on `psiFile` in both branches. §1.11 removed `MODIFICATION_COUNT` as
         * the confound and left `psiFile` — the same mechanism §1.6 had already recorded for the
         * dumb-mode exit.
         *
         * No behavioural fixture can close that: every way to tick `ProjectRootModificationTracker`
         * goes through `makeRootsChange`, which is what moves the stamp. So the wiring is made
         * *directly* assertable instead, exactly as §1.9 B5 did for the dumb-mode decision — and
         * because [forFile] spreads this array, "omitted from `Result.create`" and "omitted from
         * here" are the same edit.
         */
        internal fun dependenciesFor(
            psiFile: PsiFile,
            sourceFrame: LuaTypeSourceRecorder.SourceFrame,
        ): Array<Any> =
            arrayOf(
                psiFile,
                churnDependencyFor(psiFile, sourceFrame),
                LuaProjectSettings.getInstance(psiFile.project).state.targetModificationTracker,
            )

        /**
         * TYPE-11 §3.3 steps 1–8 — may this snapshot be pinned to the generation tracker?
         *
         * Short-circuiting, cheapest test first: step 2 rejects every project file before any set is
         * iterated, and project files are the overwhelming majority of [forFile] calls.
         *
         * Steps 4–7 are the "sources unknown" half of the invariant and are not optional. Step 3 is
         * vacuously true for an empty set, which is exactly the state a failed resolution (§1.8 B1),
         * a warm inner snapshot (§1.8 B4), an in-progress nested snapshot (§1.10 V1) and a
         * project-scope miss rescued by the all-scope fallback (§1.10 V2) each leave behind — all
         * four measured shipping a stale type. **A pin must be correct at the moment it is taken;
         * there is no second chance**, because [PsiModificationTracker.MODIFICATION_COUNT] is
         * precisely the dependency the pin removes, so a pinned file is never re-judged.
         *
         * `internal` rather than `private` deliberately: TYPE-11-05's guard (step 1) has no
         * assertion that can go red unless the decision can be asked directly (design §1.9 B5).
         */
        internal fun isPinnable(
            psiFile: PsiFile,
            sourceFrame: LuaTypeSourceRecorder.SourceFrame,
        ): Boolean {
            val targetProject = psiFile.project
            if (DumbService.isDumb(targetProject)) return false
            val provenance = LuaLibraryProvenance.getInstance(targetProject)
            if (!provenance.isProvisioned(psiFile)) return false
            if (sourceFrame.urls.any { !provenance.isProvisionedUrl(it) }) return false
            return sourceFrame.absences.isEmpty() &&
                sourceFrame.unreplayedWarm.isEmpty() &&
                sourceFrame.inProgressHits.isEmpty() &&
                sourceFrame.rescuedGlobals.isEmpty()
        }

        /**
         * TYPE-11 §3.3 step 9 — the churn dependency [forFile] hands to `Result.create`.
         *
         * The return type is [Any] because the two branches share no narrower supertype:
         * [PsiModificationTracker.MODIFICATION_COUNT] is a `Key` sentinel the platform
         * special-cases, not a `ModificationTracker`.
         *
         * The target axis is deliberately **not** composited into the pinned branch —
         * `targetModificationTracker` stays an explicit dependency of [forFile] in both branches, so
         * two dependencies each have one job (TC-3).
         */
        internal fun churnDependencyFor(
            psiFile: PsiFile,
            sourceFrame: LuaTypeSourceRecorder.SourceFrame,
        ): Any =
            if (isPinnable(psiFile, sourceFrame)) {
                LuaLibraryProvenance.getInstance(psiFile.project).generationTracker()
            } else {
                PsiModificationTracker.MODIFICATION_COUNT
            }
    }
}
