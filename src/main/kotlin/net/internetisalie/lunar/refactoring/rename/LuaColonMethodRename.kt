package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaColonCallResolution
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaField
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaFuncNameProperty
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaMethodExpr
import net.internetisalie.lunar.lang.psi.LuaNameAndArgs
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaUnionType

/**
 * REFACT-09: which occurrences of a colon method's name would a rename leave bound to the old name?
 *
 * [[NAV-13]] made `ReferencesSearch` return a colon method's call sites, so `LuaRenameProcessor`
 * needs no help *rewriting* them. What it has no answer for is whether that set is **all** of them:
 * NAV-13 resolves a plain local table, an in-file global, `setmetatable` OO and a `---@class`
 * receiver, and resolves nothing for a `require`d module, an alias, a parameter receiver, `self:`,
 * a factory-returned table or a chain's second segment. A rename over the resolvable subset that
 * reports success is a half-applied rename of the BUG-457 class.
 *
 * This object answers that question, and only that question. It rewrites nothing and refuses
 * nothing; [LuaRenameConflictDetector] turns each returned occurrence into a conflict the user must
 * answer before any write happens.
 *
 * **The rule (design §3.1).** A rename is complete iff every occurrence of the method's name, in a
 * *member position*, anywhere in the refactoring scope, is either **(a)** in the usage set the
 * platform collected, **(b)** a colon call site that [LuaColonCallResolution.declarationLeafOf]
 * binds to some *other* declaration, or **(c)** the declaration leaf being renamed. Everything else
 * is undecided and is reported. The predicate is written *decided only if*, so an un-enumerated
 * position costs a spurious conflict rather than a silent half-rename.
 *
 * **The occurrence set is closed over `lua.bnf`, not listed by shape** (design §3.3): every rule
 * `grep -n 'nameRef\|IDENTIFIER' lua.bnf` returns is classified there, and the one member position
 * that is deliberately *not* an occurrence — `funcNameMethod`, i.e. a second `function q:m()` — is
 * excluded so that two receivers sharing a member name stay independent (`requirements.md` rows 3
 * and 12). Its cost is a redefinition of the same member on the same receiver, which row 27 pins
 * and `risks-and-gaps.md` Gap 2.10 sizes.
 *
 * **Threading**: called only from [LuaRenameConflictDetector.collisions], i.e. inside the background
 * read action `BaseRefactoringProcessor` wraps around `findUsages` — **never the EDT**. That is not
 * a preference: the predicate costs p50 23 ms but p99 525 ms and max 3 163 ms on ZeroBrane, and max
 * 9 957 ms on the annotated substitute (`risks-and-gaps.md` DR-03), which is why design §9
 * Alternative B rejects refusing from `substituteElementToRename`.
 *
 * **Cancellation**: `ProgressManager.checkCanceled()` is the first statement of every iteration
 * block here — per candidate file, per name ref, per bracket step, per constructor key and per
 * usage — because every one of those bodies can resolve, load PSI or read the type snapshot.
 *
 * Stateless: no `Project`, `Editor`, `PsiFile` or `VirtualFile` is retained, and no write action is
 * opened.
 *
 * See `docs/features/refactoring/09-colon-method-rename/design.md` §2.1, §3.1–§3.4, §3.7.
 */
internal object LuaColonMethodRename {
    /** One occurrence the rename would not rewrite, and how it spells the member. */
    internal data class Undecided(
        val occurrence: PsiElement,
        val spelling: Spelling,
    )

    /** The member spellings design §3.3 enumerates, each rendering its own message (design §7.2). */
    internal enum class Spelling { COLON_CALL, DOTTED, BRACKET, FIELD_KEY }

    /**
     * Every occurrence of [target]'s name, in the refactoring scope, that this rename would leave
     * bound to the old name. Empty means the usage set is complete.
     *
     * Within a file the results come out in walk order — name refs, then bracket steps, then
     * constructor keys — and files in the order `CacheManager` returns them. **No caller may depend
     * on that order**: [LuaRenameConflictDetector] wraps each in its own collision and the dialog
     * groups by file and line itself. DR-01 records what happened to a measurement that did depend
     * on it.
     */
    fun undecidedOccurrences(
        target: LuaRenameTarget,
        usages: Set<PsiElement>,
    ): List<Undecided> {
        if (target.kind != LuaDeclarationKind.METHOD_FUNCTION) return emptyList()
        return candidateFiles(target).flatMap { file ->
            ProgressManager.checkCanceled()
            undecidedIn(file, target, usages)
        }
    }

    /**
     * True when the receiver of one of [usages] already resolves a member called
     * [LuaRenameTarget.newName] — i.e. the rename would merge two members rather than move one
     * (design §3.7, `REFACT-09-08`).
     *
     * **The handle is a usage's receiver, never the declaration's.** `LuaTypesVisitor.visitFuncDecl`
     * never registers `funcName`'s `nameRef` in `elementNodes`, so a declaration-side receiver types
     * as `unknown` in *every* receiver shape and a rule keyed on it is inert everywhere:
     * `R09C[local|annotated|global] M1declSideValueType type='unknown'` (DR-05 Finding 1). Design §9
     * Alternative F records why `getGlobalType(receiverText)` is not adopted as a fallback — it
     * answers for a global receiver only, and keying on the receiver's *spelling* re-introduces
     * `globalNameTaken`'s error in miniature.
     *
     * The measured misses are all in the "report nothing" direction, which is deliberate: a false
     * conflict on a correct rename is the failure `requirements.md` row 12 exists to prevent. A
     * declaration with no bound call site has no handle at all (row 29, Gap 2.8), and a member
     * declared in another file on a global receiver is not seen (Gap 2.9).
     */
    fun receiverAlreadyHasNewName(
        target: LuaRenameTarget,
        usages: Set<PsiElement>,
    ): Boolean =
        usages.any { usage ->
            ProgressManager.checkCanceled()
            hasMember(receiverTypeOf(usage), target.newName)
        }

    /**
     * Every file in the refactoring scope whose text carries the method's name.
     *
     * **`UsageSearchContext.ANY`, not `IN_CODE`.** [net.internetisalie.lunar.lang.insight.LuaNameReferenceSearcher]
     * can use `IN_CODE` because a usage is always code; this scan must also see `t["m"]` and
     * `{ ["m"] = 1 }`, where the name sits inside a **string** token. Executed: a three-file project
     * whose only occurrence in two of them is a constructor key still yields
     * `R09B[candidateFiles] files=[a.lua, b.lua, c.lua]` (DR-05).
     *
     * **`projectScope`, not `allScope`**, to match the scope the usage set was collected over
     * (`BaseRefactoringProcessor.myRefactoringScope`). Scanning wider would report library
     * occurrences no rename can rewrite. Executed: the two scopes produce the same verdict for every
     * declaration on both trees and differ only in which clause declines first (DR-03).
     */
    private fun candidateFiles(target: LuaRenameTarget): Collection<PsiFile> =
        CacheManager
            .getInstance(target.identifier.project)
            .getFilesWithWord(
                target.identifier.text,
                UsageSearchContext.ANY,
                GlobalSearchScope.projectScope(target.identifier.project),
                true,
            ).toList()

    /**
     * The undecided occurrences in one file.
     *
     * The `when` has **no** `LuaFuncNameMethod` branch and falls to `else` for it, together with
     * every `LuaNameRef` that is an ordinary variable — a `LuaVar` head, a `nameList` entry, a
     * `funcName` receiver. That exclusion is what makes rows 3 and 12 pass.
     */
    private fun undecidedIn(
        file: PsiFile,
        target: LuaRenameTarget,
        usages: Set<PsiElement>,
    ): List<Undecided> {
        val name = target.identifier.text
        val fromNameRefs =
            PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).mapNotNull { nameRef ->
                ProgressManager.checkCanceled()
                if (nameRef.identifier.text != name) return@mapNotNull null
                when (nameRef.parent) {
                    is LuaMethodExpr -> colonCallVerdict(nameRef, target.identifier, usages)
                    is LuaIndexExpr -> Undecided(nameRef, Spelling.DOTTED)
                    is LuaFuncNameProperty -> Undecided(nameRef, Spelling.DOTTED)
                    else -> null
                }
            }
        return fromNameRefs + bracketOccurrences(file, name) + fieldOccurrences(file, name)
    }

    /**
     * Clauses (a) and (b) of design §3.1 — the only clause that resolves.
     *
     * **A site is decided by resolving ELSEWHERE, not by resolving at all (BUG-479).** Binding to
     * some other [declarationLeaf] names a different member and is none of this rename's business;
     * binding to *this* declaration while absent from [usages] is an occurrence the rename will not
     * rewrite, because the rewrite is over [usages] — so it is reported rather than dismissed.
     *
     * The clause used to read "resolves ⇒ decided", which put that second case in neither net: not
     * renamed, because the rename rewrites [usages]; not reported, because only *undecided*
     * occurrences are reported. That is the silent half-rename of the BUG-457 class this feature
     * exists against, and it was measured live on the `---@class` receiver reached through an alias
     * — the shape the feature reaches most. The identity comparison is what makes design §3.1's
     * *decided only if* true of this clause too: an occurrence that is ours and uncollected now
     * costs a conflict rather than a silent drop.
     *
     * **This is a fail-safe, not a completeness claim.** It makes an uncollected occurrence loud;
     * it does not collect it. Every shape measured under BUG-479 has `ReferencesSearch` returning
     * the occurrence — identically under `projectScope` and `allScope`, same file and cross-file —
     * so no fixture in this suite reaches the reporting branch through a real usage set, and the
     * falsifier is the synthetic set `requirements.md` row 28 constructs.
     */
    private fun colonCallVerdict(
        nameRef: LuaNameRef,
        declarationLeaf: PsiElement,
        usages: Set<PsiElement>,
    ): Undecided? {
        if (nameRef in usages) return null
        val resolved =
            LuaColonCallResolution.declarationLeafOf(nameRef)
                ?: return Undecided(nameRef, Spelling.COLON_CALL)
        if (resolved !== declarationLeaf) return null
        return Undecided(nameRef, Spelling.COLON_CALL)
    }

    /**
     * `t["m"]` — the bracket half of `indexExpr ::= ('[' expr ']') | ('.' nameRef)`, which has no
     * `LuaNameRef` and so cannot reach the walk in [undecidedIn].
     *
     * A step whose index is not a plain literal — `t[k]`, `t[a .. b]` — names nothing in the PSI and
     * is **not** an occurrence. That is not a simplification: the pinned corpus carries 5 424 such
     * steps, so treating any of them as an occurrence refuses every rename in it (`requirements.md`
     * row 20, Gap 2.2).
     */
    private fun bracketOccurrences(
        file: PsiFile,
        name: String,
    ): List<Undecided> =
        PsiTreeUtil.findChildrenOfType(file, LuaIndexExpr::class.java).mapNotNull { index ->
            ProgressManager.checkCanceled()
            if (index.nameRef != null) return@mapNotNull null
            if (literalName(index.expr) != name) return@mapNotNull null
            Undecided(index, Spelling.BRACKET)
        }

    /**
     * `{ m = 1 }` and `{ ["m"] = 1 }` — a table-constructor key *declares* the member, so it still
     * spells the old name after the rename.
     *
     * No membership test is needed: `ReferencesSearch` returns `LuaNameRef`s and
     * `LuaColonCallResolution.methodNameLeafOf` refuses a [LuaField] outright, so a field key is
     * never in the usage set. Executed on every field fixture: `inUsages=false` (DR-05).
     *
     * The spelling is `FIELD_KEY` rather than `BRACKET` even in the bracketed form, because
     * "declares this member" is a different thing to tell a user than "reads this member through a
     * string key", and design §7.2 gives them different messages.
     */
    private fun fieldOccurrences(
        file: PsiFile,
        name: String,
    ): List<Undecided> =
        PsiTreeUtil.findChildrenOfType(file, LuaField::class.java).mapNotNull { field ->
            ProgressManager.checkCanceled()
            if (fieldKeyName(field) != name) return@mapNotNull null
            Undecided(field, Spelling.FIELD_KEY)
        }

    /**
     * The member `{ m = 1 }` / `{ ["m"] = 1 }` names, or null when this field names no member.
     *
     * The discriminator is *identifier, else a two-expression field whose first expression is a
     * plain identifier-shaped literal*, read off the executed PSI shapes (design §3.3): a positional
     * value (`1`, `m`, `f()`) has one expression and no identifier, a computed key (`[k] = 4`) has
     * two expressions but no literal, and `["a b"] = 5` has a literal that cannot name an
     * identifier. `requirements.md` row 26 is the control that keeps this from becoming
     * "every `LuaField` is an occurrence".
     */
    private fun fieldKeyName(field: LuaField): String? {
        field.identifier?.let { return it.text }
        if (field.exprList.size != 2) return null
        return literalName(field.exprList[0])
    }

    /**
     * The member a `t["m"]` step or a `["m"] =` key names, or null when it is not a plain literal.
     *
     * Accepts exactly the form that can *name an identifier*: one `'` or `"` delimiter pair around
     * `[A-Za-z0-9_]+`. Anything else — a long bracket, an escape, a concatenation, a variable —
     * returns null and the step is not an occurrence. The alternative is decoding every Lua string
     * form including long brackets, which is the surface BUG-467 is the record of; escapes cannot
     * spell a new identifier character, so refusing to decode them costs no reachable occurrence.
     *
     * `t["m"]` and `{ ["m"] = 1 }` are the same question about the same text, so they share this
     * reader rather than each carrying a copy (design §4).
     *
     * **The identifier-character clause has no reachable falsifier through this object's callers,
     * and is kept anyway.** Both callers compare the result to a Lua identifier, and any literal the
     * clause rejects contains a character no identifier can carry — so dropping it turns `["a b"]`
     * from `null` into `"a b"`, which still matches no member name. Measured: the Phase-1 mutation
     * that drops the whole discriminator (`M09`) reddens `requirements.md` row 26, but no mutation
     * of this clause alone can. It is stated here rather than paired with a test that cannot fail,
     * which is the disposition `LuaColonCallResolution.methodNameLeafOf`'s `takeIf` already takes.
     */
    private fun literalName(expr: LuaExpr?): String? {
        val text = expr?.text?.trim() ?: return null
        if (text.length < 2) return null
        val quote = text.first()
        if ((quote != '"' && quote != '\'') || text.last() != quote) return null
        val inner = text.substring(1, text.length - 1)
        return inner.takeIf { it.isNotEmpty() && it.all { char -> char.isLetterOrDigit() || char == '_' } }
    }

    /**
     * `t:m()`'s `t`, or null for every shape [LuaColonCallResolution] also refuses.
     *
     * This restates that object's private `receiverOf` rather than widening its API for one caller;
     * the refusals are the same three it documents — a chain's later segment, a parenthesised head,
     * and a suffixed `a.b:m()` whose graph anchors every suffix on the bare head. "The receiver
     * already has this member" and "a call to that member would resolve" are then one question asked
     * of one element.
     */
    private fun colonCallReceiver(usage: PsiElement): LuaNameRef? {
        val nameRef = usage as? LuaNameRef ?: return null
        val methodExpr = nameRef.parent as? LuaMethodExpr ?: return null
        val nameAndArgs = methodExpr.parent as? LuaNameAndArgs ?: return null
        val call = nameAndArgs.parent as? LuaFuncCall ?: return null
        if (call.nameAndArgsList.firstOrNull() !== nameAndArgs) return null
        val receiverVar = call.varOrExp.`var` ?: return null
        if (receiverVar.varSuffixList.isNotEmpty()) return null
        return receiverVar.nameRef
    }

    private fun receiverTypeOf(usage: PsiElement): LuaType? {
        val receiver = colonCallReceiver(usage) ?: return null
        val types = LuaTypesSnapshot.forFile(receiver.containingFile)
        return types.graphTypeToLuaType(types.getValueType(receiver))
    }

    /**
     * **The union-arm loop is required, not optional.** [LuaUnionType.resolveMember] returns null
     * unless *every* arm carries the name, and a `---@class`-annotated receiver types as
     * `{ … } | Builder` whose anonymous arm has no such member — exactly the shape `REFACT-09-08` is
     * most likely to meet. Executed on one fixture with and without the loop:
     * `R09E[annotated] type='{  } | Builder' plain=false unionAware=true` (DR-05 Finding 4).
     */
    private fun hasMember(
        type: LuaType?,
        name: String,
    ): Boolean {
        if (type == null) return false
        if (type.resolveMember(name) != null) return true
        return type is LuaUnionType && type.types.any { it.resolveMember(name) != null }
    }
}
