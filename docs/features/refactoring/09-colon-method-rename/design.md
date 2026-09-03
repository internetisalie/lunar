---
id: "REFACT-09-DESIGN"
title: "09: Design — colon-method rename, refusing by default"
type: "design"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# REFACT-09 Design

## 1. Architecture overview

### Current state

`LuaRenameProcessor.substituteElementToRename`
([LuaRenameProcessor.kt:101-117](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt))
refuses every `LuaDeclarationKind.METHOD_FUNCTION` with one message. A caret on a colon **call
site** never reaches that branch at all: `LuaDeclarationSite.identifierLeafOf` is null for it
(`kindOf` of `m` in `t:m()` is null — measured, `REFACT-09-00-DR-02` fixture K), so
`resolvedDeclarationLeaf` runs, `LuaNameReference.resolve()` returns null — `getQualifiedName`
returns null for a colon because it requires a `DOT`
([LuaNameReference.kt:205-208](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt)),
and the declaration is stub-keyed `"t:m"`, not `"m"` — and the rename refuses with
`refactoring.rename.unresolved`.

Both refusals are correct today, and `REFACT-09-00-DR-02` measured why lifting either is not enough:
`ReferencesSearch.search(<colon declaration leaf>, allScope)` returns **0** references in every
receiver shape (requirements Overview). `LuaNameReferenceSearcher`
([LuaNameReferenceSearcher.kt:47-79](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt))
does scan every `LuaNameRef` of the right text, colon call sites included, but gates on
`reference.isReferenceTo(target)`, which resolves — and a colon call site resolves to nothing.

### Prior art in this repo — extended, not duplicated

| Component | file:line | This design |
| :-- | :-- | :-- |
| `LuaRenameProcessor` | `LuaRenameProcessor.kt:65` | **Extended.** `substituteElementToRename` and `findReferences` gain a `METHOD_FUNCTION` branch and `findCollisions` is untouched (§2.2); the write path (`renameElement`, `preparedUsageRewrites`, `preparedDeclarationRewrite`, the non-cancelable section) is reused verbatim and is what delivers `REFACT-09-06`. |
| `LuaRenameConflictDetector` / `LuaRenameCollisionUsageInfo` | `LuaRenameConflictDetector.kt:111`, `:54` | **Extended.** `collisions` gains a `METHOD_FUNCTION` arm; the carrier is the existing `LuaRenameCollisionUsageInfo` and no new `UsageInfo` subclass is introduced. |
| `LuaLabelRenameProcessor` (REFACT-04) | `LuaLabelRenameProcessor.kt:38-40` | **Precedent, not touched.** It is the worked example of `RenamePsiElementProcessor` + `DumbAware` with only `canProcessElement` / `substituteElementToRename` / `findCollisions` overridden. This feature does **not** add a second processor — see §9 Alternative A. |
| `LuaDeclarationSite` | `LuaDeclarationSite.kt:41` | **Reused unchanged.** `kindOf`, `identifierLeafOf` and `functionNameLeafOf` are the classifier; no new classification rule is added. |
| `LuaMemberDeclarations.declarationOf` | `LuaMemberDeclarations.kt:48` | **Reused unchanged.** Its `public` visibility exists for this caller ([[TYPE-13]] design §3.5). |
| `LuaTypeInlayHintProvider.unwrapExpression` | `LuaTypeInlayHintProvider.kt:20` | **Reused unchanged.** The receiver handle rule ([[TYPE-13]] requirements case 17: the handle is the `LuaNameRef`, not `LuaFuncCall.varOrExp`). |
| `LuaTargetElementEvaluator.adjustTargetElement` | `LuaTargetElementEvaluator.kt:119-129` | **Not touched.** Its body opens `targetElement as? LuaFuncDecl ?: return targetElement`. Requirements case 2's executed mutation shows the call-site caret is carried by §2.2's new substitution branch alone: removing that branch refuses the call-site caret while the declaration caret still renames. |
| `LuaInplaceRenameHandler` / `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` | `LuaInplaceRenameHandler.kt:129-136`, `LuaRefactoringSupportProvider.kt:87-95` | **Not touched, and inert by construction.** Both gate on `kindOf(...)?.isFileLocal == true`; `METHOD_FUNCTION.isFileLocal` is `false` ([LuaDeclarationSite.kt:27](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)), so a colon method never reaches an in-place template and always takes the dialog path. |

### Target state

One new file — `LuaColonMethodRename` — decides, for a colon-method declaration leaf, either a
**Plan** (the declaration plus the exact set of call-site `LuaNameRef`s to rewrite) or a **refusal
message**. `LuaRenameProcessor` consults it from its substitution and usage-collection hooks, `LuaRenameConflictDetector` consults it for the member-name conflict (§5), and everything else behaves as it does today.

```
caret ──▶ LuaRenameProcessor.substituteElementToRename
              ├─ identifierLeafOf ─────────────▶ declaration leaf
              ├─ colonCallSiteDeclarationLeaf ─▶ declaration leaf   (new, §2.2)
              └─ resolvedDeclarationLeaf ──────▶ leaf | refuse
          kindOf(leaf) == METHOD_FUNCTION ──▶ LuaColonMethodRename.planFor ──▶ leaf | refuse
          findReferences  ──────────────────▶ LuaColonMethodRename.callSiteReferences
          findCollisions  ──────────────────▶ LuaRenameConflictDetector (METHOD_FUNCTION arm, §5)
          renameElement   ──────────────────▶ unchanged (REFACT-01 design §3.3)
```

## 2. Core components

### 2.1 `net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename`

- **Responsibility**: decide whether a colon-method rename is safe, and if so name every site.
- **Threading**: pure PSI reads plus `LuaTypesSnapshot.forFile` (itself `CachedValuesManager`-cached,
  [LuaTypes.kt:280-286](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt)).
  Callers hold read access: `substituteElementToRename` runs on the EDT before the refactoring
  starts; `findReferences` and `findCollisions` run inside `BaseRefactoringProcessor`'s background
  read action. It opens no write action, retains no `Project`/`Editor`/`PsiFile`, and reads no index.
- **Collaborators**: `LuaDeclarationSite`, `LuaMemberDeclarations`, `LuaTypesSnapshot`,
  `LuaTypeInlayHintProvider.unwrapExpression`, `LuaBundle`.
- **Key API** — every function takes ≤3 arguments; the four-value context is bundled into `Target`,
  the same idiom `LuaRenameTarget` uses at `LuaRenameConflictDetector.kt:25-29`:

```kotlin
internal object LuaColonMethodRename {
    /** A colon-method rename that is safe to perform, or the reason it is not. */
    internal data class Plan(
        val callSites: List<LuaNameRef>,
        val refusal: String?,
    )

    /** The declaration, its name leaf, its receiver binding leaf and the member name. */
    private data class Target(
        val declaration: LuaFuncDecl,
        val declarationLeaf: PsiElement,
        val receiverLeaf: PsiElement,
        val memberName: String,
    )

    fun planFor(declarationLeaf: PsiElement): Plan
    fun callSiteReferences(declarationLeaf: PsiElement): List<PsiReference>
    fun declarationLeafOfCallSite(leaf: PsiElement): PsiElement?
    fun memberDeclarationsNamed(declarationLeaf: PsiElement, memberName: String): List<PsiElement>
}
```

### 2.2 `LuaRenameProcessor` — what changes, and what does not

`substituteElementToRename` ([:101-117](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)):

```kotlin
        val leaf =
            LuaDeclarationSite.identifierLeafOf(element)
                ?: colonCallSiteDeclarationLeaf(element)
                ?: resolvedDeclarationLeaf(element, editor)
                ?: return null
        receiverSegmentRefusal(leaf)?.let { return refuse(leaf, editor, it) }
        return when (LuaDeclarationSite.kindOf(leaf)) {
            LuaDeclarationKind.METHOD_FUNCTION -> colonMethodSubstitution(leaf, editor)
            LuaDeclarationKind.LABEL -> null
            else -> leaf
        }
```

with two new private members:

```kotlin
    private fun colonCallSiteDeclarationLeaf(element: PsiElement): PsiElement? {
        val leaf = if (element is LuaNameRef) element.identifier else element
        return LuaColonMethodRename.declarationLeafOfCallSite(leaf)
    }

    private fun colonMethodSubstitution(
        leaf: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        caretRefusal(leaf, editor)?.let { return refuse(leaf, editor, it) }        // §3.7
        val plan = LuaColonMethodRename.planFor(leaf)
        plan.refusal?.let { return refuse(leaf, editor, it) }
        return leaf
    }
```

`findReferences` ([:136-147](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)):
insert, immediately after `kind` is computed and before the `effectiveScope` line,

```kotlin
        if (kind == LuaDeclarationKind.METHOD_FUNCTION) {
            return LuaColonMethodRename.callSiteReferences(declarationLeaf)
        }
```

The colon branch supplies its own set instead of narrowing a scope, because the scope is not the
problem: `ReferencesSearch` returns 0 at every scope (requirements Overview).

`findCollisions` ([:169-179](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt))
is **unchanged**; the new arm lives in `LuaRenameConflictDetector.collisions` (§5), which is where
**every** rule that function selects by `LuaDeclarationKind` already lives. §5 enumerates them from
the source rather than by number.

## 3. Algorithms

### 3.1 What "complete" means here — the rule `REFACT-09-00-DR-02` settled

> A colon-method rename is **complete** iff every colon call site that could bind to the renamed
> declaration is in the rewrite set. That is decided in two layers:
> **(a) containment** — the receiver's binding is file-local and its value never leaves the file, so
> no site outside the declaring file can bind to it; and
> **(b) decidability** — inside that file, every colon call named `m` is bound to *some*
> declaration, so each is either rewritten or provably unrelated.

Neither layer uses `declarationOf` for containment. `declarationOf` is used only in layer (b), to
separate same-named members of *different* receivers — the one job DR-02 measured it doing reliably
(`t:m()` → `LuaFuncDeclImpl@13` vs `q:m()` → `LuaFuncDeclImpl@57`).

### 3.2 `planFor` — the predicate

**Input**: an IDENTIFIER leaf with `LuaDeclarationSite.kindOf(leaf) == METHOD_FUNCTION`.
**Output**: `Plan(callSites, refusal = null)` or `Plan(emptyList(), refusal = <message>)`.

1. `declaration = PsiTreeUtil.getParentOfType(leaf, LuaFuncDecl::class.java)`. Non-null by grammar:
   `funcNameMethod` appears only in `funcName`, which appears only in `funcDecl`
   ([lua.bnf:164-166, 189](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)).
   A null is refused with `colonMethod.notADeclaration`.
2. `file = declaration.containingFile`; `memberName = leaf.text`;
   `receiverName = funcNameOf(declaration)?.nameRef?.text` (§3.8 — the AST-node read, never
   `LuaFuncDecl.getFuncName()`). A null is refused with `colonMethod.notADeclaration`.
3. **R1 — a unique file-local receiver binding.** `bindings = declarationLeavesNamed(file, receiverName)`
   (§3.3.1). Accept only if `bindings.size == 1` **and**
   `LuaDeclarationSite.kindOf(bindings.single())?.isFileLocal == true`. Otherwise refuse with
   `colonMethod.receiverNotLocal`. `receiverLeaf = bindings.single()`.
   *This is what excludes a global receiver (whose call sites are project-wide and report no
   declaration cross-file) and a shadowed receiver name.*
4. **R2 — no occurrence precedes the binding.** `occurrences = receiverOccurrences(target)` (§3.4).
   If any occurrence has `textOffset < receiverLeaf.textOffset`, refuse with
   `colonMethod.receiverNotLocal`: a `receiverName` written above a file-scope `local receiverName`
   is a *global* read and does not denote this binding.
5. **R3 — every occurrence is in a permitted position.** Classify each occurrence with §3.3.
   `Escape` refuses with `colonMethod.receiverEscapes`; `DottedMember` refuses with
   `colonMethod.dottedAccess`. `MemberDeclaration` and `CallSite` verdicts accumulate into
   `memberDeclarations` and `recordedCalls`; `Unrelated` is dropped.
6. **R4 — exactly one declaration of this member.** Accept only if `memberDeclarations.size == 1`
   and `memberDeclarations.single() === declarationLeaf`. Otherwise refuse with
   `colonMethod.ambiguousDeclaration`.
7. **R5 — every colon call named `memberName` in the file is decided.** §3.5, which returns the
   final `Plan(renameSet, refusal = null)`.

The steps are ordered R1 → R5 so that the message a user sees names the *first* clause that
declined, and so that the later steps may assume the earlier ones (R3 classifies against a receiver
binding R1 has already proved unique).

#### The decomposition — no function over 30 logic lines, none over 3 arguments

`planFor` is the orchestration and nothing else; each clause is a named helper. This split is
prescriptive, not illustrative — do not inline it back.

```kotlin
    fun planFor(declarationLeaf: PsiElement): Plan {
        val declaration =
            PsiTreeUtil.getParentOfType(declarationLeaf, LuaFuncDecl::class.java)
                ?: return refusal(LuaBundle.message("refactoring.rename.colonMethod.notADeclaration"))
        val file = declaration.containingFile
        val receiverName =
            funcNameOf(declaration)?.nameRef?.text
                ?: return refusal(LuaBundle.message("refactoring.rename.colonMethod.notADeclaration"))
        val receiverLeaf =
            uniqueFileLocalBinding(file, receiverName)
                ?: return refusal(LuaBundle.message("refactoring.rename.colonMethod.receiverNotLocal", receiverName))
        val target = Target(declaration, declarationLeaf, receiverLeaf, declarationLeaf.text)
        val occurrences = receiverOccurrences(target)
        if (occurrences.any { it.textOffset < receiverLeaf.textOffset }) {
            return refusal(LuaBundle.message("refactoring.rename.colonMethod.receiverNotLocal", receiverName))
        }
        val scan = scanOccurrences(target, occurrences)
        scan.refusal?.let { return refusal(it) }
        if (scan.memberDeclarations.singleOrNull() !== declarationLeaf) {
            return refusal(
                LuaBundle.message(
                    "refactoring.rename.colonMethod.ambiguousDeclaration",
                    target.memberName,
                    scan.memberDeclarations.size,
                ),
            )
        }
        return decideRemainingSites(target, scan.calls)
    }

    private fun refusal(message: String) = Plan(emptyList(), message)

    private data class Scan(
        val memberDeclarations: List<PsiElement>,
        val calls: List<LuaNameRef>,
        val refusal: String?,
    )
```

- `uniqueFileLocalBinding(file, name)` is step 3: `declarationLeavesNamed(file, name)` (§3.3.1),
  returning its single element when there is exactly one and its `kindOf(...)?.isFileLocal` is true,
  and null otherwise.
- `scanOccurrences(target, occurrences)` is step 5: fold `classify` (§3.3) over the occurrences,
  short-circuiting on the first `Escape` or `DottedMember` into `Scan(refusal = …)`.
- `decideRemainingSites(target, recorded)` is step 7 (§3.5).
- `singleOrNull() !== declarationLeaf` covers both halves of step 6: a size other than one makes
  `singleOrNull()` null, which is never identical to a real leaf.

### 3.3 The position whitelist — `classify(occurrence, target)`

The rule, stated as a property rather than as a shape list, following [[TYPE-13]] design §3.3:

> **(E)** An occurrence of the receiver binding **escapes** unless it is one of: the binding's own
> declaration; the head of a function name; the head of a bare colon call; or the head of a `var`
> whose steps are all index steps, whose **first** index step is a `.name` step naming a member
> **other than** the one being renamed, and which is a **write** — the `var` is an assignment
> target and that step is its only step.
>
> A `var` whose first index step is a `.name` step naming the member **being** renamed is not an
> escape but a `DottedMember` refusal, which carries its own message.

(E) closes over the grammar's step alphabet **and over both spellings of an index step**.
`var ::= nameRef varSuffix*` and `varSuffix ::= nameAndArgs* indexExpr`
([lua.bnf:292-294](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf))
give a `var` exactly two step kinds — an **index** step and a **call** step — and
`indexExpr ::= ('[' expr ']') | ('.' nameRef)` ([lua.bnf:301](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf))
gives an index step exactly two spellings, of which only the second names its member in the PSI:
`LuaIndexExpr.getNameRef()` is `@Nullable`
(`src/main/gen/net/internetisalie/lunar/lang/psi/LuaIndexExpr.java`) and is null for `t["m"]`.
Each way of writing a step — a call step, a `.name` index step, a bracket index step — is decided
below; none falls through to a silent drop.
Counting suffixes tests only the index steps: `t().x` is **one** `varSuffix` whose `nameAndArgsList`
is `[()]`. That clause is falsified from its own fixture by requirements case 12.

#### Each index-step spelling, and the executed counterexample behind its clause

Probe-label legend for the transcripts below: `-reviewed` is the run with the clause under
discussion **removed**, `-fixed` the run with it present; `CLASSIFY[…]` lines are the PSI shape of
the same fixture, and `WPROBE[…]` lines come from the write/read fixtures. All were executed on the
gce builder against a throwaway prototype at `128ba091`, since reverted.

**A bracket step names its member too, and `nameRef` cannot read it.** Executed on `128ba091`
against a throwaway prototype of this design, fixture
`local t = {}` / `function t:<caret>m() end` / `t:m()` / `print(t["m"])`, PSI shape first:

```
CLASSIFY[F1] ref='t' var='t["m"]' varParent=LuaVarOrExpImpl suffixes=1 callStep=false firstIndexNameRef=null firstIndexExpr="m"
```

With the bracket spelling left to compare `null` against the member name, the whole predicate
**accepts** and the rename half-applies:

```
PROBE[F1-reviewed] RENAMED
PROBE[F1-reviewed]   2| function t:n() end
PROBE[F1-reviewed]   4| print(t["m"])          <- left on the old name
```

With the escape below, the same fixture refuses and the file is byte-identical:

```
PROBE[F1-fixed] REFUSED  Cannot perform refactoring. | The receiver's value escapes at 't' (bracket index step), so not every call site of this method can be found.
PROBE[F1-fixed]   text-unchanged=true
```

**The decision, stated:** a bracket index step **escapes**, without reading the expression inside
it. The alternative — treat `t["m"]` as in scope by matching a string literal's content — needs a
literal reader that is correct for `"m"`, `'m'`, `[[m]]` and every long-bracket level, and is
*still* undecidable for `t[k]`; the delimiter-as-a-grammar lesson of [[BUG-467]] is the record of
what that costs. Escaping needs none of it and is the refusing direction. Its price is that a
`t[...]` read or write anywhere in the declaring file refuses the rename, which is stated as a
residual in `risks-and-gaps.md` Gap 2.6. `t["m"]` therefore refuses through `receiverEscapes`, not
through `dottedAccess`; both refuse and leave the file byte-identical.

**Obtaining a member's value rebinds `self`.** §3.4 rewrites `self:m()` sites on the argument that
`self` denotes the receiver binding. `self` is bound at *call* time, and a dotted invocation
supplies it explicitly, so that argument survives only while no member of the receiver can be
obtained as a value. Executed, on
`local t = {}` / `function t:<caret>m() end` / `function t:a() self:m() end` / `local other = {}` /
`function other:m() end` / `t.a(other)`:

```
CLASSIFY[F2] ref='t' var='t.a' varParent=LuaVarOrExpImpl suffixes=1 callStep=false firstIndexNameRef=a firstIndexExpr=null

PROBE[F2-reviewed] RENAMED
PROBE[F2-reviewed]   2| function t:n() end
PROBE[F2-reviewed]   3| function t:a() self:n() end
PROBE[F2-reviewed]   5| function other:m() end     <- not renamed, and `t.a(other)` calls it
PROBE[F2-reviewed]   6| t.a(other)
```

Without the member-read escape, a first index step naming a member *other* than the renamed one is
`Unrelated`, the whole predicate accepts, and it rewrites `self:m()` into a call on a member `other`
does not have. With the escape the same fixture refuses:

```
PROBE[F2-fixed] REFUSED  Cannot perform refactoring. | The receiver's value escapes at 't' (member read '.a'), so not every call site of this method can be found.
PROBE[F2-fixed]   text-unchanged=true
```

**The escape is on obtaining the value, not on the call shape**, and that distinction was measured.
Storing the member first defeats a rule keyed on "the `var` is a suffixed call head", because
`local f = t.a` is not a call head at all. Same fixture with `t.a(other)` replaced by
`local f = t.a` / `f(other)`:

```
PROBE[F2b-reviewed] RENAMED       -- accepted, `self:n()` rewritten, `function other:m()` untouched
PROBE[F2b-fixed] REFUSED  Cannot perform refactoring. | The receiver's value escapes at 't' (member read '.a'), so not every call site of this method can be found.
```

**A write is not a read, and the exception is what keeps the feature's own shape acceptable.** A
`var` that is an assignment target with exactly one index step obtains nothing, so it stays
`Unrelated`; anything else — a read, or a target with a step in front of the last one — escapes.
Executed:

| fixture (caret on the declared `m`) | outcome |
| :-- | :-- |
| `local t = {}` / `t.count = 0` / `function t:m() end` / `t:m()` | `PROBE[WRITE-other] RENAMED` |
| `local C = {}` / `function C:m() self.count = 1 end` / `C:m()` | `PROBE[WRITE-self] RENAMED` |
| `local t = {}` / `function t:m() end` / `local c = t.count` / `t:m()` | `PROBE[READ-other] REFUSED … escapes at 't' (member read '.count')` |
| `local t = {}` / `function t:m() end` / `t.a.b = 1` / `t:m()` | `PROBE[WRITE-nested] REFUSED … escapes at 't' (member read '.a')` |

`t.a.b = 1` refuses because its first step obtains `t.a` before the write happens to `b` — which is
why the exception is written over the step **count** and not over "is an assignment target".

```kotlin
    private sealed interface Verdict {
        data class Escape(val why: String) : Verdict
        data class MemberDeclaration(val nameLeaf: PsiElement) : Verdict
        data class CallSite(val nameRef: LuaNameRef) : Verdict
        data object DottedMember : Verdict
        data object Unrelated : Verdict
    }

    private fun classify(
        occurrence: LuaNameRef,
        target: Target,
    ): Verdict {
        if (occurrence.identifier === target.receiverLeaf) return Verdict.Unrelated
        val parent = occurrence.parent
        if (parent is LuaFuncName) {
            val nameLeaf = LuaDeclarationSite.functionNameLeafOf(parent)
            return if (nameLeaf.text == target.memberName) {
                Verdict.MemberDeclaration(nameLeaf)
            } else {
                Verdict.Unrelated
            }
        }
        if (parent !is LuaVar) return Verdict.Escape("parent=${parent?.javaClass?.simpleName}")
        val suffixes = parent.varSuffixList
        if (suffixes.isEmpty()) return bareOccurrence(parent, target)
        if (suffixes.any { it.nameAndArgsList.isNotEmpty() }) return Verdict.Escape("call step in a suffix")
        val firstIndexName =
            suffixes.first().indexExpr.nameRef?.text
                ?: return Verdict.Escape("bracket index step")
        if (firstIndexName == target.memberName) return Verdict.DottedMember
        if (!isSoleAssignmentTarget(parent)) return Verdict.Escape("member read '.$firstIndexName'")
        return Verdict.Unrelated
    }

    /**
     * A write that obtains nothing: `t.a = 1`. `assignmentStatement ::= varList '=' exprList`
     * (lua.bnf:122) puts an assignment target's `var` under a `LuaVarList` and nothing else does,
     * and one step means the whole `var` is the write's destination.
     */
    private fun isSoleAssignmentTarget(luaVar: LuaVar): Boolean =
        luaVar.varSuffixList.size == 1 && luaVar.parent is LuaVarList

    private fun bareOccurrence(
        luaVar: LuaVar,
        target: Target,
    ): Verdict {
        val varOrExp = luaVar.parent as? LuaVarOrExp ?: return Verdict.Escape("bare, parent not varOrExp")
        val call = varOrExp.parent as? LuaFuncCall ?: return Verdict.Escape("bare, not a call head")
        val first = call.nameAndArgsList.firstOrNull() ?: return Verdict.Escape("bare, no nameAndArgs")
        val methodExpr = first.methodExpr ?: return Verdict.Escape("bare, dotted call")
        return if (methodExpr.nameRef.text == target.memberName) {
            Verdict.CallSite(methodExpr.nameRef)
        } else {
            Verdict.Unrelated
        }
    }
```

Why each branch is where it is, and what it refuses:

- **`parent !is LuaVar` → escape.** This is the branch that catches `return M`, `local u = t`,
  `f(t)` and `setmetatable({}, Class)` — every position that stores or passes the table's value.
  Written as *reject unless a `LuaVar`* rather than as a list of storing positions, so an
  unenumerated position takes the escape branch.
- **A bare `var` that is not a colon-call head → escape.** `t` alone in `return t` is also a
  suffix-free `LuaVar`; only the colon-call-head shape is admitted. `t.x()` is *not* here — its
  `var` is `t.x`, which has a suffix and is judged by the index branch.
- **A bracket index step → escape**, before any name comparison, because the step's name is not in
  the PSI to compare. This is the only branch that reads a `null` as a refusal rather than as a
  mismatch, and it is the one the executed counterexample above reached.
- **`DottedMember`** is a refusal, not a rename: `t.m` is the same member as `t:m` under a spelling
  this feature does not rewrite. Its measured value is requirements case 11 (`print(t.m)`) and
  case 18 (`self.m = 1`). It is tested **before** the write exception, so `t.m = 1` refuses as
  dotted access rather than passing as a harmless write.
- **A member read → escape; a sole-step member write → `Unrelated`.** This is what makes §3.4's
  `self` clause sound; requirements case 25 is the read it must refuse and case 26 the write it
  must not.

#### 3.3.1 `declarationLeavesNamed(file, name)` — R1's binding lookup

```kotlin
    private fun declarationLeavesNamed(
        file: PsiFile,
        name: String,
    ): List<PsiElement> =
        PsiTreeUtil
            .findChildrenOfType(file, LuaNameRef::class.java)
            .map { it.identifier }
            .filter { leaf ->
                leaf.text == name &&
                    LuaDeclarationSite.kindOf(leaf) != null &&
                    !isFunctionNameReceiverSegment(leaf)
            }

    private fun isFunctionNameReceiverSegment(leaf: PsiElement): Boolean {
        val funcName = PsiTreeUtil.getParentOfType(leaf, LuaFuncName::class.java, /* strict = */ false) ?: return false
        return LuaDeclarationSite.functionNameLeafOf(funcName) !== leaf
    }
```

**The receiver-segment exclusion is load-bearing and was measured.** `kindOf` of the `t` in
`function t:m()` is `GLOBAL_FUNCTION`, not null — `kindFromNameRefGrandParent`'s
`grandParent is LuaFuncName -> GLOBAL_FUNCTION` row
([LuaDeclarationSite.kt:240](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt))
— so without the exclusion every `function t:…()` counts as a second declaration of `t`, R1's
`bindings.size == 1` fails, and **every** accepting fixture refuses. Executed: with the exclusion
removed, every fixture whose unmutated outcome is a rename or a conflict — requirements cases 1, 2,
3, 4, 5, 21, 22 and the `---@class Builder` fixture — flips to
`REFUSED … receiver '<name>' is not a file-local table`, and no refusing fixture changes.

**Resolution is not used to find the binding, deliberately.** `declaration.funcName.nameRef.reference?.resolve()`
returns **the funcName's own `t` leaf**, not the `local t`: `LuaScopeProcessor`'s
`is LuaFuncDecl` branch matches `element.funcName.nameRef.identifier.text == name` for recursion
([LuaScopeProcessor.kt:79-84](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)),
and the enclosing `LuaFuncDecl` is on the crawl path. Measured: an earlier prototype using `resolve()`
refused **every** fixture, including the plain local table. The text-plus-uniqueness lookup above is
what R1 uses instead, and R1's `bindings.size == 1` plus R2's offset test are what make a text match
sound: with exactly one declaration of the name in the file and no occurrence above it, every
occurrence of that name in the file denotes that one binding.

### 3.4 `receiverOccurrences(target)` — where an occurrence of the receiver can be written

```kotlin
    private fun receiverOccurrences(target: Target): List<LuaNameRef> {
        val all = PsiTreeUtil.findChildrenOfType(target.declaration.containingFile, LuaNameRef::class.java)
        val direct = all.filter { it.text == target.receiverLeaf.text }
        val selves = all.filter { it.text == "self" && selfBindsTo(it, target) }
        return (direct + selves).filter { it.identifier !== target.receiverLeaf }
    }

    private fun selfBindsTo(
        selfRef: LuaNameRef,
        target: Target,
    ): Boolean {
        val resolved = selfRef.reference?.resolve() ?: return false
        if (LuaDeclarationSite.kindOf(resolved) != LuaDeclarationKind.METHOD_FUNCTION) return false
        val enclosing = PsiTreeUtil.getParentOfType(resolved, LuaFuncName::class.java) ?: return false
        return enclosing.nameRef.text == target.receiverLeaf.text
    }
```

**`self` is a second spelling of the receiver, and the type engine cannot see it.** [[TYPE-13]]
Gap 2.7 measured `self:b()`'s winning member node with an **empty `upSet`**, so `declarationOf` is
null for every `self:` call — and `function C:a() self:b() end` is ordinary Lua OO, not a corner.
This design decides it **syntactically instead**.

**What that rests on, stated precisely.** `self` is bound at *call* time by the invoking receiver,
so "`self` inside a method declared on the binding denotes that binding" is not a property of the
declaration — it is a property of every invocation of that method, and a dotted invocation supplies
the first argument explicitly. The clause is therefore sound only while **no value of any member of
the receiver can be obtained anywhere in the file**: obtaining `t.a` is exactly what lets
`t.a(other)`, or `local f = t.a` followed by `f(other)`, run `a`'s body with `self` bound to
something else. §3.3's member-read escape **is** that condition, and it is what this clause rests
on. It does not rest on R3 refusing "every position through which some other value could reach that
parameter": that is false as written, and §3.3 records the executed fixture on which the predicate
accepted and rewrote a `self:m()` site into a call on a member the actual receiver does not have.

These consequences are what make the argument closed rather than merely plausible, and each is
checked against the classifier rather than assumed:

- **A member value cannot be obtained any other way.** With the receiver's own value already
  escaping at every non-`var` position (`parent !is LuaVar`) and at every bare non-colon-call
  position, and with bracket steps escaping too, a `.name` index step is the only remaining route
  to a member of the binding. A colon call does not count: it pins the receiver by construction.
- **`self` cannot be reassigned into, stored, or passed.** Measured — every `self` occurrence of
  each fixture, with the branch `classify`/`bareOccurrence` would take:

  ```
  SELF[assign] varParent=LuaVarImpl grandParent=LuaVarListImpl  resolvedKind=METHOD_FUNCTION verdict=Escape(bare, parent not varOrExp)
  SELF[assign] varParent=LuaVarImpl grandParent=LuaVarOrExpImpl resolvedKind=null            verdict=CallSite/Unrelated (colon call head)
  SELF[stored] varParent=LuaVarImpl grandParent=LuaVarOrExpImpl resolvedKind=METHOD_FUNCTION verdict=Escape(bare, not a call head)
  SELF[passed] varParent=LuaVarImpl grandParent=LuaVarOrExpImpl resolvedKind=METHOD_FUNCTION verdict=Escape(bare, not a call head)
  ```

  `varList ::= var {',' var}*` occurs only in `assignmentStatement` (lua.bnf:122, :167), so an
  assignment target is the one `var` position whose parent is a `LuaVarList` and never a
  `LuaVarOrExp` — `self = C` therefore escapes before anything is rewritten. The `assign` fixture's
  **second** row is the one worth keeping: after `self = C`, the `self` of the following `self:m()`
  resolves to the assignment rather than to the method, so `LuaDeclarationSite.kindOf` is `null`,
  `selfBindsTo` declines it, and R5 then finds an undecided `LuaMethodExpr` and refuses on that
  route as well. Both halves decline, independently.

`selfBindsTo` uses `resolve()` — unlike §3.3.1 — because here resolution is exactly right:
`LuaScopeProcessor` resolves `self` to the enclosing method's `funcNameMethod.nameRef.identifier`
([LuaScopeProcessor.kt:87-92](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)),
measured as `SELF nameRef=LuaNameRefImpl@47 resolve=LeafPsiElement@43` on the `function C:a()`
fixture. Going through the reference is also what makes a user-written `local self = …` fall out:
it resolves to that local, whose `kindOf` is `LOCAL_VARIABLE`, and the guard declines.

This one clause carries both directions and each has its own fixture: requirements case 4 is a
`self:` call the scan must find (dropping the clause refuses a rename that should succeed), and
case 18 is a `self.m = 1` write the scan must refuse (dropping the clause performs a rename that
leaves the write behind). Requirements case 25 is the third direction the counterexample added: a
`self:` call the scan must find **and** a dotted read of another member that must refuse it.

### 3.5 `decideRemainingSites` — R5

```kotlin
    private fun decideRemainingSites(
        target: Target,
        recorded: List<LuaNameRef>,
    ): Plan {
        val renameSet = recorded.toMutableList()
        for (methodExpr in colonSitesNamed(target)) {
            val nameRef = methodExpr.nameRef
            if (renameSet.any { it === nameRef }) continue
            val decided =
                structuralDeclarationOf(methodExpr)
                    ?: return refusal(LuaBundle.message("refactoring.rename.colonMethod.undecided", nameRef.text, lineOf(nameRef)))
            if (decided === target.declaration) renameSet += nameRef
        }
        return Plan(renameSet, refusal = null)
    }
```

- `colonSitesNamed(target)` is
  `PsiTreeUtil.findChildrenOfType(file, LuaMethodExpr::class.java).filter { it.nameRef.text == target.memberName }`.
- **Identity (`===`), never `equals`.** These are PSI nodes in one file; identity is the intended
  comparison and `LuaFuncDecl` overrides no `equals`.
- **A `null` from `structuralDeclarationOf` refuses; a non-null one that is not our declaration is
  skipped.** *Undecided* and *decided elsewhere* are different answers, and only the first is unsafe
  — the mirror of [[TYPE-13]]'s "no declaration and no such member are different answers".
- **The union with `recorded` is not redundant.** `recorded` holds the sites R3 found through the
  receiver binding (including every `self:` call); the loop adds any site the *structural* route
  independently binds to this declaration. A site in neither is undecided and refuses.
- Requirements case 14 is the falsifier (a parameter receiver `x:m()`, measured
  `declarationOf=null`), case 13 the chain, case 24 the required module.

### 3.6 `structuralDeclarationOf` and the receiver handle

```kotlin
    private fun receiverOf(methodExpr: LuaMethodExpr): PsiElement? {
        val nameAndArgs = methodExpr.parent as? LuaNameAndArgs ?: return null
        val call = nameAndArgs.parent as? LuaFuncCall ?: return null
        if (call.nameAndArgsList.firstOrNull() !== nameAndArgs) return null
        return LuaTypeInlayHintProvider.unwrapExpression(call.varOrExp)
    }

    private fun structuralDeclarationOf(methodExpr: LuaMethodExpr): PsiElement? {
        val receiver = receiverOf(methodExpr) ?: return null
        val types = LuaTypesSnapshot.forFile(methodExpr.containingFile)
        val member = types.graphTypeToLuaType(types.getValueType(receiver)).resolveMember(methodExpr.nameRef.text)
        return member?.let { LuaMemberDeclarations.declarationOf(it) }
    }
```

Each property below is grounded:

1. **Only the first `nameAndArgs` of a `LuaFuncCall` has a receiver this can type.** [[TYPE-13]]
   Gap 2.12 measured `visitFuncCall` seeding the whole call's value from the **first** segment's
   declared return, so `x:m1():m2()` reports `x:m1()`'s type for the second segment — a silently
   wrong value, not a null. Returning null here converts that into the refusing direction. Falsified
   by requirements case 13.
2. **A `methodExpr` whose `nameAndArgs` parent is a `LuaVarSuffix`** (`a:m().b`) also returns null,
   by the same `as? LuaFuncCall` cast.
3. **The handle is `unwrapExpression(call.varOrExp)`, not `varOrExp` itself.** [[TYPE-13]]
   requirements case 17 measured `varOrExp` typing as `Undefined` while the `LuaNameRef` at the same
   offset types as the receiver.

`declarationOf` may return a `LuaAssignmentStatement` or a `LuaField` rather than a `LuaFuncDecl`
([[TYPE-13]] design §4.5). The `===` test against `target.declaration` is false for those, so such a
site is *decided elsewhere* and left alone — which is correct: a member declared by `t.m = f` is
declared by a statement this rename does not own, and R3's `DottedMember` verdict has already
refused the case where it is the *same* receiver.

### 3.7 `caretRefusal` — the `self` guard (`REFACT-09-04`)

```kotlin
    private fun caretRefusal(
        leaf: PsiElement,
        editor: Editor?,
    ): String? {
        val caret = editor?.let { PsiUtilBase.getElementAtCaret(it) } ?: return null
        if (caret.text == leaf.text) return null
        return LuaBundle.message("refactoring.rename.colonMethod.caretNotOnMethod", caret.text)
    }
```

- **Keyed on the caret, not on the resolved element** — `REFACT-01` risks Gap 2.4 records why a
  guard on the *element* cannot work: `TargetElementUtilBase` tries `REFERENCED_ELEMENT_ACCEPTED`
  first, so the processor receives the resolved `m` leaf whose text is `"m"`.
  `PsiUtilBase.getElementAtCaret(editor)` (`platform/analysis-api/src/com/intellij/psi/util/PsiUtilBase.java:91-97`)
  is `file.findElementAt(editor.caretModel.offset)` and gives the token the user actually selected.
  It is not yet imported anywhere in `src/main`; the repo's existing caller is
  [LuaJsonSchemaEngineTest.kt:163](../../../../src/test/kotlin/net/internetisalie/lunar/lang/schema/LuaJsonSchemaEngineTest.kt),
  so `grep PsiUtilBase src/main` returning nothing is expected rather than a fictional symbol.
- **The discriminator is `caret.text != leaf.text`, not the literal `"self"`.** The same comparison
  `LuaTargetElementEvaluator.adjustTargetElement` already uses
  ([:128](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaTargetElementEvaluator.kt)).
  A text test for `"self"` would be wrong in the other direction: `function T.m(self, x)` is legal
  Lua whose `self` is an ordinary `PARAMETER` and must rename normally (`REFACT-01` TC-19c, still
  green). It would also be wrong for a **call-site** caret, where `caret.text == leaf.text == "m"`
  and the rename must proceed (`REFACT-09-02`).
- **`editor == null` means there is no caret, so there is nothing to guard.** The two production
  entry points that pass a possibly-null editor —
  `PsiElementRenameHandler.rename` and `RenamePsiElementProcessorBase.substituteElementToRename(element, editor, callback)`
  — take it from the data context, and a null editor is a Project-View invocation with no caret at
  all. A test must therefore drive `REFACT-09-04` through `myFixture.renameElementAtCaret`, which
  passes the fixture editor (`CodeInsightTestFixtureImpl.java:1104`), and **not** through
  `LuaRenameTest.assertRefusedWith`, which passes `null`.

### 3.8 `declarationLeafOfCallSite` — the call-site caret (`REFACT-09-02`)

```kotlin
    fun declarationLeafOfCallSite(leaf: PsiElement): PsiElement? {
        val nameRef = leaf.parent as? LuaNameRef ?: return null
        val methodExpr = nameRef.parent as? LuaMethodExpr ?: return null
        val declaration = structuralDeclarationOf(methodExpr) ?: selfRouteDeclaration(methodExpr) ?: return null
        return funcNameOf(declaration as? LuaFuncDecl ?: return null)?.funcNameMethod?.nameRef?.identifier
    }
```

`selfRouteDeclaration` is the caret-side counterpart of §3.4 and exists so a caret on the `m` of
`self:m()` reaches the declaration at all — `structuralDeclarationOf` is null there:

```kotlin
    private fun selfRouteDeclaration(methodExpr: LuaMethodExpr): PsiElement? {
        val receiver = receiverOf(methodExpr) as? LuaNameRef ?: return null
        if (receiver.text != "self") return null
        val resolved = receiver.reference?.resolve() ?: return null
        if (LuaDeclarationSite.kindOf(resolved) != LuaDeclarationKind.METHOD_FUNCTION) return null
        val enclosingReceiver =
            PsiTreeUtil.getParentOfType(resolved, LuaFuncName::class.java)?.nameRef?.text ?: return null
        return PsiTreeUtil
            .findChildrenOfType(resolved.containingFile, LuaFuncName::class.java)
            .firstOrNull { candidate ->
                candidate.funcNameMethod?.nameRef?.text == methodExpr.nameRef.text &&
                    candidate.nameRef.text == enclosingReceiver
            }?.let { PsiTreeUtil.getParentOfType(it, LuaFuncDecl::class.java) }
    }
```

It only **substitutes**; the returned leaf then goes through `planFor` like any other, so a wrong
guess is refused there rather than acted on. Requirements case 3 is its fixture and case 2's
mutation is its falsifier.

#### The traversal enumerates `LuaFuncName`, never `LuaFuncDecl.getFuncName()`

`LuaFuncDecl.getFuncName()` is `findNotNullChildByClass`
([LuaFuncDeclImpl.java:46-50](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/impl/LuaFuncDeclImpl.java)),
and `funcDecl ::= FUNCTION funcName funcBody` carries `pin = 1`
([lua.bnf:189-190](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) — so a
`LuaFuncDecl` node exists with **no** `FUNC_NAME` child whenever a keyword sits in the name slot,
and the getter raises `TestLoggerAssertionError` from `PsiElementBase.notNullChild` (an
internal-error balloon in production). This is the SYNTAX-18 hazard
[LuaDeclarationSite.kt:203-229](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)
already closes for its own rows, by reading `node.findChildByType(LuaElementTypes.FUNC_NAME)`
instead of the getter.

A traversal is where that bites hardest: it evaluates the accessor on **every** declaration in the
file until its predicate matches, so one malformed `function` above the matching method raises —
and half-typed `function` headers are an ordinary transient state, on the EDT, in
`substituteElementToRename`. §6's row covers only a caret **on** the malformed declaration, which
is a different position. Executed, on the fixture of requirements case 30:

```
B1[b1broken] decl0 hasFuncNameNode=true  text='function C:a() self:m() end'
B1[b1broken] decl1 hasFuncNameNode=false text='function'
B1[b1broken] specifiedGetterForm=THREW TestLoggerAssertionError
B1[b1broken] fixedNodeForm=ok(null)
B1[b1control] decl0 hasFuncNameNode=true text='function C:a() self:m() end'
B1[b1control] decl1 hasFuncNameNode=true text='function C:m() end'
B1[b1control] specifiedGetterForm=ok(function C:m() end)
B1[b1control] fixedNodeForm=ok(function C:m() end)
```

Enumerating `LuaFuncName` rather than `LuaFuncDecl` removes the accessor entirely instead of
guarding it: `funcName ::= nameRef funcNameProperty* funcNameMethod?` declares **no pin**
([lua.bnf:164-166](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)), so a
partial `funcName` is rolled back rather than built, a malformed declaration contributes no
`LuaFuncName` at all and is simply absent from the collection, and `LuaFuncName.getNameRef()` /
`LuaFuncNameMethod.getNameRef()` are safe for every node that *is* in it.

**The same reading is used at every site that needs a declaration's `funcName`**, not only at the
one that can currently reach a malformed node — `planFor` step 2 (§3.2), `selfBindsTo` (§3.4),
`declarationLeafOfCallSite` above, and `memberDeclarationsNamed` (§3.9) all go through:

```kotlin
    private fun funcNameOf(declaration: LuaFuncDecl): LuaFuncName? =
        declaration.node
            .findChildByType(LuaElementTypes.FUNC_NAME)
            ?.psi as? LuaFuncName
```

The three sites other than `selfRouteDeclaration` are reached only from a leaf or a resolve result
whose own ancestor chain contains the `funcName`, so the getter would not raise there today. They
are written the same way regardless: a hazard closed at some sites and left open at others is the
shape that put this defect in the design in the first place, and `funcNameOf` returning null is
already a refusal (`colonMethod.notADeclaration`) rather than a new branch.

### 3.9 `callSiteReferences` and `memberDeclarationsNamed` — the two remaining public functions

**`callSiteReferences(declarationLeaf)`** is `planFor`'s accepted set, expressed as the
`PsiReference`s `LuaRenameProcessor.findReferences` must return:

```kotlin
    fun callSiteReferences(declarationLeaf: PsiElement): List<PsiReference> =
        planFor(declarationLeaf).callSites.mapNotNull { it.reference }
```

- **A refusing plan yields an empty list, and that is not a silent skip.** `findReferences` is
  reached only after `substituteElementToRename` returned non-null, which requires the same
  `planFor` call to have accepted (§2.2). An empty list here therefore means "this declaration has
  no call sites", which is `§6`'s zero-call-site row and is correct. The one route that could reach
  it with a refusing plan is a `kind == METHOD_FUNCTION` guard broadened to other kinds — which is
  exactly requirements case 19's executed mutant, and it is red there.
- **`mapNotNull` drops nothing reachable.** `LuaNameRefBaseImpl.getReference()`
  ([LuaBaseElements.kt:130-137](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt))
  returns null only when `getName()` is null, and every element in `callSites` is a
  `LuaMethodExpr.nameRef` the classifier matched **by text**, so its name is non-null by
  construction. The reference it returns is a `LuaNameReference` whose `element` is that
  `LuaNameRef` — which is precisely what `preparedUsageRewrite` requires: it tests
  `reference is LuaNameReference` and then rewrites `hostNode.findChildByType(IDENTIFIER)`
  ([LuaRenameProcessor.kt:471-485](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)).
  A different `PsiReference` type would take the delegating `RenameUtil.rename` branch instead.

**`memberDeclarationsNamed(declarationLeaf, memberName)`** answers §5's question — *does this
receiver already declare a member with this name?* — and it does **not** re-run `planFor`'s scan
under the new name:

```kotlin
    fun memberDeclarationsNamed(
        declarationLeaf: PsiElement,
        memberName: String,
    ): List<PsiElement> {
        val declaration =
            PsiTreeUtil.getParentOfType(declarationLeaf, LuaFuncDecl::class.java) ?: return emptyList()
        val receiverName = funcNameOf(declaration)?.nameRef?.text ?: return emptyList()
        return PsiTreeUtil
            .findChildrenOfType(declaration.containingFile, LuaFuncName::class.java)
            .filter { it.nameRef.text == receiverName }
            .map { LuaDeclarationSite.functionNameLeafOf(it) }
            .filter { it.text == memberName }
    }
```

Why each decision, since both were open:

- **It enumerates declarations, not occurrences.** Re-running `scanOccurrences` with
  `memberName = newName` would inherit that function's short-circuit: a `Verdict.Escape` or
  `DottedMember` under the *new* name would abandon the fold with `memberDeclarations` only
  partially filled, and a partial list read as a complete one reports "no conflict" for a receiver
  that has one. The enumeration above has no early exit and no refusal channel.
- **Matching on the receiver's *text* is sound here, and only here.** `planFor` has already
  accepted, so R1 has proved the receiver name has exactly one file-local binding in this file and
  R2 has proved no occurrence precedes it (§3.2). Under those two facts every `function <name>…`
  in the file names that one binding. This function must therefore never be called on a leaf that
  has not been through `planFor` — the sole caller is §5's `memberNameTaken`, on
  `LuaRenameConflictDetector`'s path, which `RenameUtil.findUsages` reaches only after
  substitution succeeded.
- **It matches both spellings of a member declaration.** `functionNameLeafOf`
  ([LuaDeclarationSite.kt:111-117](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt))
  returns the `funcNameMethod` leaf when there is one and the last `funcNameProperty` leaf
  otherwise, so `function t:n()` **and** `function t.n()` both count — both really do declare a
  member `n` on `t`, and renaming `t:m` to `n` merges with either.
- **What it does not see, stated rather than left to be found**: a member introduced by assignment
  (`t.n = f`) or inside the table constructor (`local t = { n = f }`) has no `LuaFuncName` and is
  not reported. Neither is reachable from an accepted plan in the `t.n = f` form — §3.3 classifies
  a sole-step assignment target as `Unrelated`, so `t.n = 1` beside `function t:m()` accepts and
  then reports no conflict for `m` → `n`. That is a **missed** conflict, not a wrong rewrite: the
  rename still rewrites only its own sites and the file stays consistent, with two members named
  `n` where the user expected one. Recorded as `risks-and-gaps.md` Gap 2.8; it is a `Should`
  requirement's residual, not a `Must`'s.
- **Totality**: a null `funcNameOf` (the SYNTAX-18 hazard, §3.8) or a leaf with no enclosing
  `LuaFuncDecl` returns an empty list — no conflict rather than an exception, on a path that runs
  inside `BaseRefactoringProcessor`'s read action.

## 4. External data & parsing

**None.** This feature consumes no CLI output, no file format and no network response. Its only
inputs are PSI and the type engine's in-memory snapshot.

## 5. `LuaRenameConflictDetector` — the `METHOD_FUNCTION` arm

`collisions` ([LuaRenameConflictDetector.kt:120-132](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt))
becomes:

```kotlin
        val found =
            if (target.kind == LuaDeclarationKind.METHOD_FUNCTION) {
                memberNameTaken(target)
            } else {
                captures(target, usages) +
                    if (target.kind.isFileLocal) {
                        shadows(target)
                    } else {
                        globalNameTaken(target) + ambiguousGlobal(target)
                    }
            }
```

```kotlin
    private fun memberNameTaken(target: LuaRenameTarget): List<LuaRenameCollision> =
        LuaColonMethodRename.memberDeclarationsNamed(target.identifier, target.newName).map { existing ->
            LuaRenameCollision(existing, LuaBundle.message("refactoring.rename.conflict.memberExists", target.newName))
        }
```

**Why each rule of the pre-existing set must not run for a colon method.** `collisions` selects a
rule set by `LuaDeclarationKind`, so the `METHOD_FUNCTION` arm has to account for every rule the
function can reach. The list below is read off `LuaRenameConflictDetector.kt` — `captures`
(`:154`), `shadows` (`:178`), `globalNameTaken` (`:208`), `ambiguousGlobal` (`:227`) — rather than
recalled as a number, and it must be re-read, not re-trusted, if a rule is added.

- **`captures`** asks whether a *lexically visible* declaration of the new name would capture a
  usage. A member name is not a lexical binding; a `local n` in scope has nothing to do with `t:n`.
- **`shadows` never ran for this kind, and the new arm changes nothing about that.** It is reached
  only through `if (target.kind.isFileLocal)` (`:126`), and the kind is declared
  `METHOD_FUNCTION("global function", false)` — `isFileLocal = false`
  ([LuaDeclarationSite.kt:27](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)).
  Executed on the prototype at `128ba091`: `FACT isFileLocal(METHOD_FUNCTION)=false`. It is listed
  because an enumeration that silently omits an inert rule is indistinguishable from one that
  overlooked a live one, and because that assumption dies without a symptom if a later change makes
  a method kind file-local — the same assumption `risks-and-gaps.md` "Test Case Gaps" records for
  in-place rename.
- **`globalNameTaken`** does fire on the right key — `searchKeyOf` prefixes the receiver, so it
  searches `"t:n"` ([:259-262](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt))
  — but against the **project-wide** `LuaGlobalDeclarationIndex`, so a different file's `local t`
  with a `t:n` reports a merge that cannot happen. `memberNameTaken` asks the same question over the
  declaring file's own receiver binding, with no index read.
- **`ambiguousGlobal`** rests on "`LuaNameReference.resolve()` returns null with two
  declarations, so usages stop being findable". A colon method's usages are **not** collected
  through `resolve` at all — §2.2's `findReferences` branch supplies them — so the premise does not
  hold. Measured (requirements case 22): two files each containing `local t = {}` /
  `function t:m() end` / `t:m()` raise
  `'t:m' is declared in 2 places; while more than one declaration exists its usages do not resolve`,
  while the rename is in fact correct and file-local. With the arm in place the same fixture renames
  cleanly and leaves the sibling untouched.

`LuaRenameCollisionUsageInfo` is reused unchanged and no new `UsageInfo` subclass is defined
(`REFACT-09-07`).

## 6. Edge cases

| Case | Handling | Where |
| :-- | :-- | :-- |
| Caret on the receiver of `function t:m()` | already refused by `receiverSegmentRefusal`, unchanged | `LuaRenameProcessor.kt:399-403` |
| Caret on `...` | `canProcessElement` is false for an `ELLIPSIS`, unchanged | `REFACT-01` TC-19b |
| `function t:m()` with no `funcName` node (`function repeat() end`), **caret on it** | `kindOf` is null, so the `METHOD_FUNCTION` branch is never entered | `LuaDeclarationSite.kt:224-229` (SYNTAX-18) |
| A malformed `function` declaration **elsewhere in the file**, above the matching method | every traversal enumerates `LuaFuncName`, which a malformed declaration does not produce, so it is absent rather than raising | §3.8, requirements case 30 |
| The new name is not a Lua identifier | `LuaNamesValidator` rejects it in the dialog before this code runs | `plugin.xml` `<lang.namesValidator>` |
| Rename invoked while indexing | `LuaRenameProcessor` is `DumbAware` and the predicate reads no index, so it behaves identically | §2.2, and `LuaRenameProcessor.kt:53-63` |
| A `self` written as an explicit parameter, `function T.m(self, x)` | `kindOf` is `PARAMETER`, so §3.7's guard is not reached and the parameter renames normally | `REFACT-01` TC-19c |
| Two receivers with the same method name in one file | both decided; only the target's sites are rewritten | §3.5, requirements case 5 |
| Zero call sites (`function t:m() end` alone) | `Plan(emptyList(), null)` — the declaration renames alone, which is correct | §3.5 |
| `t["m"]` / `t[k]` anywhere in the declaring file | escapes: an index step whose name is not in the PSI is refused rather than compared against `null` | §3.3, requirements case 27 |
| `t.other` read anywhere in the declaring file | escapes: obtaining a member value is what can rebind `self` | §3.3, §3.4, requirements case 26 |
| `t.other = 1` / `self.other = 1` | `Unrelated` — a sole-step assignment target obtains nothing | §3.3, requirements case 25 |

## 7. Integration points

### 7.1 `plugin.xml` — no change

Every class this feature touches is already registered:

```xml
<!-- src/main/resources/META-INF/plugin.xml:392-395 — unchanged -->
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor"/>
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaLabelRenameProcessor"/>
```

`LuaColonMethodRename` is a plain `object` reached only from `LuaRenameProcessor` and
`LuaRenameConflictDetector`; it is not an extension and gets no entry. No new index, no new service,
no new settings key.

### 7.2 `LuaBundle.properties` — the blanket key removed, narrower ones added

Removed (`src/main/resources/net/internetisalie/lunar/LuaBundle.properties:153`):

```
refactoring.rename.colonMethod=…
```

Added, in the `refactoring.rename.*` block. The keys are split by the phase that introduces them,
because `implementation-plan.md` Phase 1 adds one group and Phase 5 the other; adding a key early
is harmless but adding Phase 5's key while missing one of Phase 1's is not, and a single
undifferentiated block invites exactly that.

**Phase 1 — every key `LuaColonMethodRename` itself renders.** Add all of these, and no others:

```
refactoring.rename.colonMethod.receiverNotLocal=The receiver ''{0}'' is not a file-local table, so call sites in other files cannot be found.
refactoring.rename.colonMethod.receiverEscapes=The receiver''s value escapes at ''{0}'' ({1}), so not every call site of this method can be found.
refactoring.rename.colonMethod.dottedAccess=This method is also accessed as ''.{0}'', which this rename does not rewrite.
refactoring.rename.colonMethod.ambiguousDeclaration=''{0}'' is declared {1} times on this receiver, so renaming one would leave the others behind.
refactoring.rename.colonMethod.undecided=The call ''{0}'' on line {1} cannot be bound to a declaration, so it may or may not be a usage of this method.
refactoring.rename.colonMethod.notADeclaration=This is not a method declaration, so there is nothing to rename.
```

`notADeclaration` is rendered by every `planFor` step that cannot find the declaration or its
`funcName` — steps 1 and 2 today. Both are unreachable from the function's stated input: `kindOf`
is `METHOD_FUNCTION` only when the leaf's grandparent is a `LuaFuncNameMethod`, `funcNameMethod`
occurs only inside `funcName` and `funcName` only inside `funcDecl` (lua.bnf:164-166, :189), so
both ancestors exist by construction. The key is in Phase 1's group because Phase 1 is where the
code that renders it is written.

**Phase 3 — the key `LuaRenameProcessor` renders, not `LuaColonMethodRename`:**

```
refactoring.rename.colonMethod.caretNotOnMethod=''{0}'' is not the method name; put the caret on the method name to rename it.
```

**Phase 5 — the conflict arm's key (§5), not a `colonMethod.*` key at all:**

```
refactoring.rename.conflict.memberExists=This table already has a member named ''{0}''; renaming would merge the two.
```

**No key is needed for the bracket step or the member read.** Both are `Verdict.Escape` values and
render through `receiverEscapes`, whose `{1}` carries the reason — measured as
`escapes at 't' (bracket index step)` and `escapes at 't' (member read '.a')` (§3.3). The `{1}` is
the `Verdict.Escape.why` string, which is developer-facing detail inside a user-facing sentence;
keep it short and lower-case.

## 8. Requirement coverage

| Requirement | Priority | Implemented by |
| :-- | :-- | :-- |
| `REFACT-09-01` | M | §2.2 (`findReferences` branch), §3.2, §3.3, §3.4, §3.5 |
| `REFACT-09-02` | M | §2.2 (`colonCallSiteDeclarationLeaf`), §3.8 |
| `REFACT-09-03` | M | §3.2 R1-R5, §3.3 (E) — including the bracket-step and member-read escapes, requirements cases 25 and 27-29 — §7.2 |
| `REFACT-09-04` | M | §3.7 |
| `REFACT-09-05` | M | unchanged route — `LuaRenameProcessor`'s existing `LOCAL_VARIABLE` path; §6 row 1 covers the declaration-side caret. **Measured** that no branch of this design is on that route, and which mutation does reach it: requirements case 19 |
| `REFACT-09-06` | M | unchanged route — `LuaRenameProcessor.renameElement` (REFACT-01 design §3.3); refusal returns before any write (§2.2) |
| `REFACT-09-07` | S | §5 |
| `REFACT-09-08` | M | §1 prior-art table (each row states *not touched* or *extended*), §5 (the arm that keeps C4 off this kind) |

## 9. Alternatives considered

**A. A separate `LuaColonMethodRenameProcessor`, mirroring `LuaLabelRenameProcessor`.** Rejected.
`RenamePsiElementProcessorBase.forPsiElement` returns the **first** matching extension and
`LuaRenameProcessor.canProcessElement` already claims every `LuaNameRef`, so a new processor would
have to be registered ahead of it — and would then inherit
`RenamePsiElementProcessorBase.renameElement`, losing REFACT-01's precomputed, non-cancelable write
path. That path is exactly what `REFACT-09-06` asks for, and BUG-468 is the record of what its
absence costs. REFACT-04 could take the separate-processor route because label rename needed no
write path at all.

**B. Make `LuaNameReference` resolve colon call sites.** This would make `ReferencesSearch` find
them and need no new collector — and would also change Go to Declaration, Find Usages, the undeclared-name
inspection and every consumer of `resolve()`, on a route measured to return `declarationOf == null`
for aliases, parameters, `self`, modules and cross-file globals. The blast radius belongs to a
navigation feature, not to a rename. Recorded in `risks-and-gaps.md` as future work.

**C. Decide completeness project-wide** — scan every colon site named `m` in the project via the
word index and require all of them decidable. Rejected: sound but unpredictable, since acceptance
would depend on unrelated files using the same method name, over a corpus this feature measures at
941 colon-method declarations across 734 files (`risks-and-gaps.md` Gap 2.3). §3.1's containment layer gets the same soundness
from a file-local argument.

**D. Special-case `setmetatable(instance, Class)` so class receivers survive the escape test.**
Rejected: that is a shape list, and [[TYPE-13]] design §3.3 records what shape lists cost — a check
derived from one observed shape leaves the other step kind open. Widening reach here means widening
`upSet` reach in the engine ([[TYPE-13]] Gap 2.7), which is a merge change.

## 10. Open Questions

_No design decision is left to the implementer._ One **product** decision is open and is tracked,
not deferred: `risks-and-gaps.md` Gap 2.3 measures the predicate accepting **0** of the corpus's 941
colon-method declarations, and whether to ship, defer or cancel on that basis is not a design
question. `status:` stays `todo` until it is answered.
