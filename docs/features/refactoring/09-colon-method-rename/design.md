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
| `LuaRenameProcessor` | `LuaRenameProcessor.kt:65` | **Extended.** Three overrides gain a `METHOD_FUNCTION` branch; the write path (`renameElement`, `preparedUsageRewrites`, `preparedDeclarationRewrite`, the non-cancelable section) is reused verbatim and is what delivers `REFACT-09-06`. |
| `LuaRenameConflictDetector` / `LuaRenameCollisionUsageInfo` | `LuaRenameConflictDetector.kt:57`, `:117` | **Extended.** `collisions` gains a `METHOD_FUNCTION` arm; the carrier is the existing `LuaRenameCollisionUsageInfo` and no new `UsageInfo` subclass is introduced. |
| `LuaLabelRenameProcessor` (REFACT-04) | `LuaLabelRenameProcessor.kt:33-36` | **Precedent, not touched.** It is the worked example of `RenamePsiElementProcessor` + `DumbAware` with only `canProcessElement` / `substituteElementToRename` / `findCollisions` overridden. This feature does **not** add a second processor — see §9 Alternative A. |
| `LuaDeclarationSite` | `LuaDeclarationSite.kt:41` | **Reused unchanged.** `kindOf`, `identifierLeafOf` and `functionNameLeafOf` are the classifier; no new classification rule is added. |
| `LuaMemberDeclarations.declarationOf` | `LuaMemberDeclarations.kt:46` | **Reused unchanged.** Its `public` visibility exists for this caller ([[TYPE-13]] design §3.5). |
| `LuaTypeInlayHintProvider.unwrapExpression` | `LuaTypeInlayHintProvider.kt:20` | **Reused unchanged.** The receiver handle rule ([[TYPE-13]] requirements case 17: the handle is the `LuaNameRef`, not `LuaFuncCall.varOrExp`). |
| `LuaTargetElementEvaluator.adjustTargetElement` | `LuaTargetElementEvaluator.kt:117-127` | **Not touched.** Its body opens `targetElement as? LuaFuncDecl ?: return targetElement`. Requirements case 2's executed mutation shows the call-site caret is carried by §2.2's new substitution branch alone: removing that branch refuses the call-site caret while the declaration caret still renames. |
| `LuaInplaceRenameHandler` / `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` | `LuaInplaceRenameHandler.kt:129-136`, `LuaRefactoringSupportProvider.kt:87-95` | **Not touched, and inert by construction.** Both gate on `kindOf(...)?.isFileLocal == true`; `METHOD_FUNCTION.isFileLocal` is `false` ([LuaDeclarationSite.kt:27](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)), so a colon method never reaches an in-place template and always takes the dialog path. |

### Target state

One new file — `LuaColonMethodRename` — decides, for a colon-method declaration leaf, either a
**Plan** (the declaration plus the exact set of call-site `LuaNameRef`s to rewrite) or a **refusal
message**. `LuaRenameProcessor` consults it from three hooks and otherwise behaves as it does today.

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

### 2.2 `LuaRenameProcessor` — three edits, nothing else

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
        return leaf?.let { LuaColonMethodRename.declarationLeafOfCallSite(it) }
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
is **unchanged**; the new arm lives in `LuaRenameConflictDetector.collisions` (§5), which is where the
other three rules live.

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
   `receiverName = declaration.funcName.nameRef.text`.
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
        val receiverName = declaration.funcName.nameRef.text
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
> declaration; the head of a function name; the head of a colon call; or the head of a `var` whose
> steps are all index steps and whose first index step does not name the member being renamed.

(E) closes over the grammar's step alphabet. `var ::= nameRef varSuffix*` and
`varSuffix ::= nameAndArgs* indexExpr` ([lua.bnf:292-294](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf))
give a `var` exactly two step kinds — an **index** step and a **call** step — so both must be tested.
Counting suffixes tests only the index steps: `t().x` is **one** `varSuffix` whose `nameAndArgsList`
is `[()]`. That clause is falsified from its own fixture by requirements case 12.

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
            return if (nameLeaf.text == target.memberName) Verdict.MemberDeclaration(nameLeaf) else Verdict.Unrelated
        }
        if (parent !is LuaVar) return Verdict.Escape("parent=${parent?.javaClass?.simpleName}")
        val suffixes = parent.varSuffixList
        if (suffixes.isEmpty()) return bareOccurrence(parent, target)
        if (suffixes.any { it.nameAndArgsList.isNotEmpty() }) return Verdict.Escape("call step in a suffix")
        val firstIndexName = suffixes.first().indexExpr.nameRef?.text
        return if (firstIndexName == target.memberName) Verdict.DottedMember else Verdict.Unrelated
    }

    private fun bareOccurrence(
        luaVar: LuaVar,
        target: Target,
    ): Verdict {
        val varOrExp = luaVar.parent as? LuaVarOrExp ?: return Verdict.Escape("bare, parent not varOrExp")
        val call = varOrExp.parent as? LuaFuncCall ?: return Verdict.Escape("bare, not a call head")
        val first = call.nameAndArgsList.firstOrNull() ?: return Verdict.Escape("bare, no nameAndArgs")
        val methodExpr = first.methodExpr ?: return Verdict.Escape("bare, dotted call")
        return if (methodExpr.nameRef.text == target.memberName) Verdict.CallSite(methodExpr.nameRef) else Verdict.Unrelated
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
- **`DottedMember`** is a refusal, not a rename: `t.m` is the same member as `t:m` under a spelling
  this feature does not rewrite. Its measured value is requirements case 11 (`print(t.m)`) and
  case 18 (`self.m = 1`).

#### 3.3.1 `declarationLeavesNamed(file, name)` — R1's binding lookup

```kotlin
    private fun declarationLeavesNamed(
        file: PsiFile,
        name: String,
    ): List<PsiElement> =
        PsiTreeUtil
            .findChildrenOfType(file, LuaNameRef::class.java)
            .mapNotNull { it.identifier }
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
([LuaDeclarationSite.kt:236](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt))
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
        val enclosing = PsiTreeUtil.getParentOfType(resolved, LuaFuncDecl::class.java) ?: return false
        return enclosing.funcName.nameRef.text == target.receiverLeaf.text
    }
```

**`self` is a second spelling of the receiver, and the type engine cannot see it.** [[TYPE-13]]
Gap 2.7 measured `self:b()`'s winning member node with an **empty `upSet`**, so `declarationOf` is
null for every `self:` call — and `function C:a() self:b() end` is ordinary Lua OO, not a corner.
This design decides it **syntactically instead**, which is sound under R1+R3: `self` inside a method
declared on the binding denotes that binding, and R3 has already refused every position through
which some *other* value could reach that parameter.

`selfBindsTo` uses `resolve()` — unlike §3.3.1 — because here resolution is exactly right:
`LuaScopeProcessor` resolves `self` to the enclosing method's `funcNameMethod.nameRef.identifier`
([LuaScopeProcessor.kt:87-92](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)),
measured as `SELF nameRef=LuaNameRefImpl@47 resolve=LeafPsiElement@43` on the `function C:a()`
fixture. Going through the reference is also what makes a user-written `local self = …` fall out:
it resolves to that local, whose `kindOf` is `LOCAL_VARIABLE`, and the guard declines.

This one clause carries both directions and each has its own fixture: requirements case 4 is a
`self:` call the scan must find (dropping the clause refuses a rename that should succeed), and
case 18 is a `self.m = 1` write the scan must refuse (dropping the clause performs a rename that
leaves the write behind).

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

Three properties, each grounded:

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
  ([:126](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaTargetElementEvaluator.kt)).
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
        return (declaration as? LuaFuncDecl)?.funcName?.funcNameMethod?.nameRef?.identifier
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
        val enclosing = PsiTreeUtil.getParentOfType(resolved, LuaFuncDecl::class.java) ?: return null
        return PsiTreeUtil
            .findChildrenOfType(enclosing.containingFile, LuaFuncDecl::class.java)
            .firstOrNull { candidate ->
                candidate.funcName.funcNameMethod?.nameRef?.text == methodExpr.nameRef.text &&
                    candidate.funcName.nameRef.text == enclosing.funcName.nameRef.text
            }
    }
```

It only **substitutes**; the returned leaf then goes through `planFor` like any other, so a wrong
guess is refused there rather than acted on. Requirements case 3 is its fixture and case 2's
mutation is its falsifier.

## 4. External data & parsing

**None.** This feature consumes no CLI output, no file format and no network response. Its only
inputs are PSI and the type engine's in-memory snapshot.

## 5. `LuaRenameConflictDetector` — the `METHOD_FUNCTION` arm

`collisions` ([LuaRenameConflictDetector.kt:122-133](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt))
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

**Why each inherited rule must not run for a colon method** — every premise below is false here, and
C4's was measured producing a conflict that does not exist:

- **C1 `captures`** asks whether a *lexically visible* declaration of the new name would capture a
  usage. A member name is not a lexical binding; a `local n` in scope has nothing to do with `t:n`.
- **C3 `globalNameTaken`** does fire on the right key — `searchKeyOf` prefixes the receiver, so it
  searches `"t:n"` ([:271](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt))
  — but against the **project-wide** `LuaGlobalDeclarationIndex`, so a different file's `local t`
  with a `t:n` reports a merge that cannot happen. `memberNameTaken` asks the same question over the
  declaring file's own receiver binding, with no index read.
- **C4 `ambiguousGlobal`** rests on "`LuaNameReference.resolve()` returns null with two
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
| `function t:m()` with no `funcName` node (`function repeat() end`) | `kindOf` is null, so the `METHOD_FUNCTION` branch is never entered | `LuaDeclarationSite.kt:224-229` (SYNTAX-18) |
| The new name is not a Lua identifier | `LuaNamesValidator` rejects it in the dialog before this code runs | `plugin.xml` `<lang.namesValidator>` |
| Rename invoked while indexing | `LuaRenameProcessor` is `DumbAware` and the predicate reads no index, so it behaves identically | §2.2, and `LuaRenameProcessor.kt:53-63` |
| A `self` written as an explicit parameter, `function T.m(self, x)` | `kindOf` is `PARAMETER`, so §3.7's guard is not reached and the parameter renames normally | `REFACT-01` TC-19c |
| Two receivers with the same method name in one file | both decided; only the target's sites are rewritten | §3.5, requirements case 5 |
| Zero call sites (`function t:m() end` alone) | `Plan(emptyList(), null)` — the declaration renames alone, which is correct | §3.5 |

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

Added, in the `refactoring.rename.*` block:

```
refactoring.rename.colonMethod.receiverNotLocal=The receiver ''{0}'' is not a file-local table, so call sites in other files cannot be found.
refactoring.rename.colonMethod.receiverEscapes=The receiver''s value escapes at ''{0}'' ({1}), so not every call site of this method can be found.
refactoring.rename.colonMethod.dottedAccess=This method is also accessed as ''.{0}'', which this rename does not rewrite.
refactoring.rename.colonMethod.ambiguousDeclaration=''{0}'' is declared {1} times on this receiver, so renaming one would leave the others behind.
refactoring.rename.colonMethod.undecided=The call ''{0}'' on line {1} cannot be bound to a declaration, so it may or may not be a usage of this method.
refactoring.rename.colonMethod.caretNotOnMethod=''{0}'' is not the method name; put the caret on the method name to rename it.
refactoring.rename.conflict.memberExists=This table already has a member named ''{0}''; renaming would merge the two.
```

Plus `refactoring.rename.colonMethod.notADeclaration` for §3.2 step 1's unreachable branch:

```
refactoring.rename.colonMethod.notADeclaration=This is not a method declaration, so there is nothing to rename.
```

The `{1}` of `receiverEscapes` is the `Verdict.Escape.why` string, which is developer-facing detail
inside a user-facing sentence; keep it short and lower-case.

## 8. Requirement coverage

| Requirement | Priority | Implemented by |
| :-- | :-- | :-- |
| `REFACT-09-01` | M | §2.2 (`findReferences` branch), §3.2, §3.3, §3.4, §3.5 |
| `REFACT-09-02` | M | §2.2 (`colonCallSiteDeclarationLeaf`), §3.8 |
| `REFACT-09-03` | M | §3.2 R1-R5, §3.3 (E), §7.2 |
| `REFACT-09-04` | M | §3.7 |
| `REFACT-09-05` | M | unchanged route — `LuaRenameProcessor`'s existing `LOCAL_VARIABLE` path; §6 row 1 covers the declaration-side caret |
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
would depend on unrelated files using the same method name, over a corpus [[TYPE-13]] requirements
measures at 809 colon-method declarations across 734 files. §3.1's containment layer gets the same soundness
from a file-local argument.

**D. Special-case `setmetatable(instance, Class)` so class receivers survive the escape test.**
Rejected: that is a shape list, and [[TYPE-13]] design §3.3 records what shape lists cost — a check
derived from one observed shape leaves the other step kind open. Widening reach here means widening
`upSet` reach in the engine ([[TYPE-13]] Gap 2.7), which is a merge change.

## 10. Open Questions

_None — feature has cleared the planning bar._
