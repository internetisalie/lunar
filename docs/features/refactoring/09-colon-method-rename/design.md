---
id: "REFACT-09-DESIGN"
title: "09: Design — colon-method rename over NAV-13's usage set"
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
refuses every `LuaDeclarationKind.METHOD_FUNCTION` with one message. Since [[NAV-13]] a caret on a
colon **call site** reaches that same branch: `LuaDeclarationSite.identifierLeafOf` is null for the
call site's leaf, so `resolvedDeclarationLeaf`
([:372-382](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt))
resolves it, and `LuaNameReference.multiResolve`'s colon branch now returns the declaration's
IDENTIFIER leaf. `LuaColonCallRenameRefusalTest.renamingAColonCallSiteIsRefusedInsteadOfRetargetingASameNamedLocal`
pins that route.

Everything else in the rename machinery already does the right thing for a colon method, measured
rather than inferred (`risks-and-gaps.md` DR-02):

- `findReferences` returns the call sites — `R09PROBE[F01] kind=METHOD_FUNCTION refs=[34, 40]`.
  `METHOD_FUNCTION.isFileLocal` is `false`, so the `LocalSearchScope` narrowing is skipped and the
  method takes the plain `ReferencesSearch.search(declarationLeaf, searchScope)` branch
  ([:141-146](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)).
- `renameElement` rewrites them — `R09PROBE[R01] RENAMED | local t = {} / function t:n() end /
  t:n() / t:n()`.

What is missing is a verdict on whether that set is *all* of them, and the caret and out-of-project
guards §3.6 specifies.

### Prior art in this repo — extended, not duplicated

| Component | file:line | This design |
| :-- | :-- | :-- |
| `LuaRenameProcessor` | `LuaRenameProcessor.kt:65` | **Extended.** `substituteElementToRename` loses the blanket refusal and gains the O(1) guards of §3.6. `findReferences`, `renameElement`, `preparedUsageRewrites`, `preparedDeclarationRewrite` and the non-cancelable section are **unchanged** and are what deliver `REFACT-09-01`, `-02` and `-07`. |
| `LuaRenameConflictDetector` / `LuaRenameCollisionUsageInfo` | `LuaRenameConflictDetector.kt:120`, `:54` | **Extended.** `collisions` gains a `METHOD_FUNCTION` arm (§5); the carrier is the existing `LuaRenameCollisionUsageInfo` and no new `UsageInfo` subclass is introduced. |
| `LuaColonCallResolution` | `LuaColonCallResolution.kt:29` | **Reused unchanged.** `declarationLeafOf` is the occurrence scan's decision procedure (§3.4); `isColonCallMemberName` is not used — the scan already has the `LuaMethodExpr` parent in hand. Its `receiverOf` and its union-arm member lookup are **private**, so §3.7 restates both rather than widening this object's API for one caller; both restatements are cited line for line there. |
| `LuaNameReferenceSearcher` | `LuaNameReferenceSearcher.kt:44` | **Not touched.** §3.3's candidate-file lookup uses the same `CacheManager.getFilesWithWord` idiom (`:93-96`) rather than a second searcher. |
| `LuaDeclarationSite` | `LuaDeclarationSite.kt:41` | **Reused unchanged.** `kindOf` and `identifierLeafOf` are the classifier; no new classification rule is added. |
| `LuaCatsTypeRenameProcessor.substituteElementToRename` | `LuaCatsTypeRenameProcessor.kt:85-93` | **Precedent, not touched.** The out-of-project refusal of §3.6 is the same rule (`GlobalSearchScope.projectScope(project).contains(virtualFile)`, `LuaCatsTypeDeclarations.kt:206-209`) and the same message shape. |
| `LuaLabelRenameProcessor` (REFACT-04) | `LuaLabelRenameProcessor.kt:38-40` | **Precedent, not touched.** This feature adds **no** second processor — see §9 Alternative A. |
| `LuaTargetElementEvaluator`, `LuaInplaceRenameHandler`, `LuaRefactoringSupportProvider` | `LuaTargetElementEvaluator.kt:119`, `LuaInplaceRenameHandler.kt:129-136`, `LuaRefactoringSupportProvider.kt:87-95` | **Not touched, and the in-place pair is inert by construction.** Both gate on `kindOf(...)?.isFileLocal == true`; `METHOD_FUNCTION.isFileLocal` is `false` ([LuaDeclarationSite.kt:27](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)), so a colon method always takes the dialog path. |

### Target state

One new file — `LuaColonMethodRename` — answers one question in the background: *which occurrences
of this method's name would this rename leave behind?* `LuaRenameConflictDetector` turns each into
a conflict. `LuaRenameProcessor` keeps the O(1) refusals of §3.6 on the EDT and otherwise stops
refusing.

```
caret ──▶ LuaRenameProcessor.substituteElementToRename            [EDT, O(1)]
              ├─ identifierLeafOf ────────▶ declaration leaf
              └─ resolvedDeclarationLeaf ─▶ declaration leaf | refuse   (NAV-13 resolve)
          kindOf(leaf) == METHOD_FUNCTION ──▶ caretRefusal (§3.6)  ──▶ refuse
                                          └▶ outOfProjectRefusal   ──▶ refuse
          findReferences  ─────────────────▶ UNCHANGED — ReferencesSearch (NAV-13's usage set)
          findCollisions  ─────────────────▶ LuaRenameConflictDetector, METHOD_FUNCTION arm (§5)
                                              ├─ LuaColonMethodRename.undecidedOccurrences (§3)
                                              └─ receiverAlreadyHasNewName (§3.7)      [background]
          renameElement   ─────────────────▶ UNCHANGED (REFACT-01 design §3.3)
```

## 2. Core components

### 2.1 `net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename` (new)

- **Responsibility**: given the declaration being renamed and the usage set the platform collected,
  name every occurrence of the method's name that the rename would leave behind.
- **Threading**: called only from `LuaRenameConflictDetector.collisions`, i.e. inside the background
  read action `BaseRefactoringProcessor` wraps around `findUsages` — **never the EDT**. Pure PSI
  reads, one word-index read, `LuaColonCallResolution.declarationLeafOf` per candidate occurrence
  and one `LuaTypesSnapshot.forFile` per usage (each a `CachedValuesManager`-backed snapshot read).
  Opens no write action, retains no `Project` / `Editor` / `PsiFile` / `VirtualFile`.
- **Collaborators**: `LuaColonCallResolution`, `LuaDeclarationSite`, `LuaTypesSnapshot`,
  `CacheManager`.
- **Key API** — every function takes ≤3 arguments; the three-value context is bundled into the
  existing `LuaRenameTarget` (`LuaRenameConflictDetector.kt:25-29`):

```kotlin
package net.internetisalie.lunar.refactoring.rename

internal object LuaColonMethodRename {
    /** One occurrence the rename would not rewrite, and how it spells the member. */
    internal data class Undecided(
        val occurrence: PsiElement,
        val spelling: Spelling,
    )

    /** The member spellings §3.3 enumerates, each rendering its own message (§7.2). */
    internal enum class Spelling { COLON_CALL, DOTTED, BRACKET, FIELD_KEY }

    /**
     * Every occurrence of [target]'s name, in the refactoring scope, that this rename would leave
     * bound to the old name. Empty means the usage set is complete.
     */
    fun undecidedOccurrences(
        target: LuaRenameTarget,
        usages: Set<PsiElement>,
    ): List<Undecided>

    /**
     * True when the receiver of one of [usages] already resolves a member called
     * [LuaRenameTarget.newName] (§3.7).
     */
    fun receiverAlreadyHasNewName(
        target: LuaRenameTarget,
        usages: Set<PsiElement>,
    ): Boolean

    private fun candidateFiles(target: LuaRenameTarget): Collection<PsiFile>
    private fun undecidedIn(file: PsiFile, target: LuaRenameTarget, usages: Set<PsiElement>): List<Undecided>
    private fun colonCallVerdict(nameRef: LuaNameRef, usages: Set<PsiElement>): Undecided?
    private fun bracketOccurrences(file: PsiFile, name: String): List<Undecided>
    private fun fieldOccurrences(file: PsiFile, name: String): List<Undecided>
    private fun fieldKeyName(field: LuaField): String?
    private fun literalName(expr: LuaExpr?): String?
    private fun colonCallReceiver(usage: PsiElement): LuaNameRef?
    private fun receiverTypeOf(usage: PsiElement): LuaType?
    private fun hasMember(type: LuaType?, name: String): Boolean
}
```

Imports are explicit (no wildcards): `com.intellij.openapi.progress.ProgressManager`,
`com.intellij.psi.PsiElement`, `com.intellij.psi.PsiFile`,
`com.intellij.psi.impl.cache.CacheManager`, `com.intellij.psi.search.GlobalSearchScope`,
`com.intellij.psi.search.UsageSearchContext`, `com.intellij.psi.util.PsiTreeUtil`, and from
`net.internetisalie.lunar.lang.psi`: `LuaColonCallResolution`, `LuaDeclarationKind`, `LuaExpr`,
`LuaField`, `LuaFuncCall`, `LuaFuncNameProperty`, `LuaIndexExpr`, `LuaMethodExpr`,
`LuaNameAndArgs`, `LuaNameRef`; from `net.internetisalie.lunar.lang.psi.types`: `LuaType`,
`LuaTypesSnapshot`, `LuaUnionType`.

### 2.2 `LuaRenameProcessor` — what changes, and what does not

`substituteElementToRename`
([:101-117](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt))
becomes:

```kotlin
        val leaf =
            LuaDeclarationSite.identifierLeafOf(element)
                ?: resolvedDeclarationLeaf(element, editor)
                ?: return null
        receiverSegmentRefusal(leaf)?.let { return refuse(leaf, editor, it) }
        return when (LuaDeclarationSite.kindOf(leaf)) {
            LuaDeclarationKind.METHOD_FUNCTION -> colonMethodSubstitution(leaf, editor)
            // Unreachable — canProcessElement excludes labels — but the invariant stays local.
            LuaDeclarationKind.LABEL -> null
            else -> leaf
        }
```

with this new private member, which dispatches to the guards §3.6 specifies (`caretRefusal` and
`outOfProjectRefusal`, both private members of the same class):

```kotlin
    private fun colonMethodSubstitution(
        leaf: PsiElement,
        editor: Editor?,
    ): PsiElement? {
        caretRefusal(leaf, editor)?.let { return refuse(leaf, editor, it) }
        outOfProjectRefusal(leaf)?.let { return refuse(leaf, editor, it) }
        return leaf
    }
```

`findReferences`, `findCollisions`, `renameElement` and every accessor of [[REFACT-01]] design §2.9 (`LuaRefactoringSettings`) are **unchanged**.
`findCollisions` already delegates to `LuaRenameConflictDetector.collisions`
([:169-179](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)),
which is where every rule selected by `LuaDeclarationKind` lives; the new arm goes there (§5).

## 3. Algorithms

### 3.1 What "complete" means here — the rule `REFACT-09-00-DR-01` settled

> A colon-method rename is **complete** iff every occurrence of the method's name, in a *member
> position*, anywhere in the refactoring scope, is either
> **(a)** in the usage set the platform collected — it is about to be rewritten; or
> **(b)** a colon call site that `LuaColonCallResolution.declarationLeafOf` binds to some *other*
> declaration — it provably names a different member; or
> **(c)** the declaration leaf being renamed.
> Any other member-position occurrence is **undecided** and is reported.

Each property of that rule below is measured:

1. **It is sound modulo dynamic indexing.** A member reached as `t[k]` names nothing in the PSI, so
   no rule can decide it. Refusing on any bracket step refuses everything — the pinned corpus
   carries 5 424 non-literal bracket index steps. §"Out of Scope" of `requirements.md` states the
   residual; `risks-and-gaps.md` Gap 2.2 sizes it.
2. **Clause (b) is what makes two receivers with a same-named method independent.** Without it,
   `local t` and `local q` each carrying `m` would each report the other's call site.
   `R09PRED[c04] verdict=accepted` is the executed control.
3. **It is not the superseded containment argument.** That predicate reasoned about where the
   *receiver's value* can travel and accepted 0 of 941 declarations. This one reasons about where
   the *member's name* appears, which is a decidable question because `ReferencesSearch` now answers
   most of it.

### 3.2 `undecidedOccurrences(target, usages)` — the entry point

- **Input → Output**: the `LuaRenameTarget` (declaration leaf, kind, new name) and the usage
  elements the platform collected → the undecided occurrences. Within a file they come out in walk
  order — the `LuaNameRef` spellings, then the bracket steps, then the constructor keys, each in
  document order — and files come out in the order `CacheManager` returns them. **No caller may
  depend on that order.** `LuaRenameConflictDetector` turns each occurrence into its own
  `LuaRenameCollisionUsageInfo`, which the conflicts dialog groups by file and line itself, and
  DR-01 records what happens to a measurement that does depend on it: the superseded reach table
  classified each blocked declaration by its *first* undecided occurrence and is not reproducible
  cell for cell as a result.
- **Steps**:
  1. `if (target.kind != LuaDeclarationKind.METHOD_FUNCTION) return emptyList()`
  2. `return candidateFiles(target).flatMap { ProgressManager.checkCanceled(); undecidedIn(it, target, usages) }`
- **Complexity**: `O(files with the word) × O(name refs + index steps + fields in each)`, plus one
  `declarationLeafOf` per same-named colon call site. Measured in `risks-and-gaps.md` DR-03.

### 3.3 `candidateFiles` and `undecidedIn` — the occurrence set, closed over the grammar

```kotlin
    private fun candidateFiles(target: LuaRenameTarget): Collection<PsiFile> =
        CacheManager
            .getInstance(target.identifier.project)
            .getFilesWithWord(
                target.identifier.text,
                UsageSearchContext.ANY,
                GlobalSearchScope.projectScope(target.identifier.project),
                /* caseSensitively = */ true,
            ).toList()
```

- **Why `projectScope` and not `allScope`.** The usage set this is compared against is collected
  over `BaseRefactoringProcessor`'s `myRefactoringScope`, which defaults to
  `GlobalSearchScope.projectScope(project)` (`BaseRefactoringProcessor.java:186`, passed through
  `RenameProcessor.java:324` → `RenameUtil.java:87-103`). Scanning wider would report library
  occurrences that no rename can rewrite and that no call can make. **Executed**: `allScope` and a
  project-only scan produce the **same verdict for every declaration** on both trees — they differ
  only in *which* clause declines first, for the declarations the probe printed as `DIFF` lines
  (`risks-and-gaps.md` DR-03, the `DIFF` lines).
- **Why `UsageSearchContext.ANY` and not `IN_CODE`.** `LuaNameReferenceSearcher` can use `IN_CODE`
  because a usage is always code ([:93-96](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt)).
  This scan must also see `t["m"]` and `{ ["m"] = 1 }`, where the name is inside a **string** token,
  so it needs `ANY`. `UsageSearchContext.ANY` is a declared constant of that class.
- **A file whose only occurrence is a table-constructor key is a candidate.** Executed on a
  three-file project whose `b.lua` is `local u = { m = 1 }` and whose `c.lua` is
  `local v = { ["m"] = 1 }`: `R09B[candidateFiles] files=[a.lua, b.lua, c.lua]` (`risks-and-gaps.md`
  DR-05). Both key forms index the word, so no separate lookup is needed for them.

```kotlin
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
                    is LuaMethodExpr -> colonCallVerdict(nameRef, usages)
                    is LuaIndexExpr -> Undecided(nameRef, Spelling.DOTTED)
                    is LuaFuncNameProperty -> Undecided(nameRef, Spelling.DOTTED)
                    else -> null
                }
            }
        return fromNameRefs + bracketOccurrences(file, name) + fieldOccurrences(file, name)
    }
```

**The member positions, read off `lua.bnf` mechanically rather than listed by shape.** A member name
is spelled two ways: as an `IDENTIFIER` — directly or through `nameRef` — or, in the two bracket
positions, as a **STRING**. The identifier half is enumerated by
`grep -n 'nameRef\|IDENTIFIER' src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf`, and every
line it returns is classified below.

> **The grep alone is not the closure, and the difference matters when the grammar next changes.**
> `indexExpr ::= ('[' expr ']') | ('.' nameRef)` ([:301](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf))
> and `field ::= '[' expr ']' '=' expr | IDENTIFIER '=' expr | expr`
> ([:319](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) are returned only
> because each of those *lines* also carries an identifier alternative. Factor either bracket
> alternative into a rule of its own and the grep would stop returning it while the member position
> remained. **So the re-run check is two greps, not one**: the identifier one above, and
> `grep -n "'\.'\|':'\|'\['" …/lua.bnf`, whose member-position rules must be exactly `:165`, `:166`,
> `:300`, `:301`, `:319`. The second derivation shares no method with the first, which is the point:
> a closure verified only by the method that produced it is the defect this section exists to fix,
> and it is what [[NAV-13]]'s review rounds kept finding one layer up. Re-running the identifier grep
> after a grammar
change is what tells an implementer the set has moved:

| `lua.bnf` | Rule | Names a table member? | PSI the scan sees | Verdict |
| :-- | :-- | :-- | :-- | :-- |
| `:69` | `IDENTIFIER = 'regexp:…'` | — | token definition | not an occurrence |
| `:152` | `numericForStatement ::= FOR IDENTIFIER '=' …` | no — loop variable | — | not an occurrence |
| `:164` | `funcName ::= nameRef funcNameProperty* funcNameMethod?` | no — the leading `nameRef` is the **receiver** | — | not an occurrence (`REFACT-09-06`, §6 row 1) |
| `:165` | `funcNameProperty ::= '.' nameRef` | **yes** — `function t.m()` | `LuaFuncNameProperty` parent | `DOTTED` |
| `:166` | `funcNameMethod ::= ':' nameRef` | **yes** — `function t:m()` | `LuaFuncNameMethod` parent | **not an occurrence** — a declaration of *some* member, decided by whether its own call sites resolve (see below) |
| `:168` | `nameList ::= nameRef {',' nameRef}*` | no — locals / parameters | — | not an occurrence |
| `:169` | `nameRef ::= IDENTIFIER` | — | the leaf rule itself | not an occurrence |
| `:176` | `localFuncDecl ::= LOCAL FUNCTION nameRef funcBody` | no — a local name | — | not an occurrence |
| `:212-213` | a comment on the `global` soft keyword | — | not a rule | not an occurrence |
| `:229` | `globalFuncDecl ::= <<globalKeyword>> FUNCTION nameRef funcBody` | no — a global name | — | not an occurrence |
| `:243` | `attName ::= nameRef attrib?` | no — a local name | — | not an occurrence |
| `:247`, `:251` | `labelRef`, `labelName` | no — labels | — | not an occurrence |
| `:292` | `var ::= nameRef varSuffix*` | no — the variable head | `LuaVar` parent | not an occurrence (`{ m }`'s bare `m` lands here — §6) |
| `:300` | `methodExpr ::= ':' nameRef` | **yes** — `t:m(…)` | `LuaMethodExpr` parent | decided by §3.4 |
| `:301` | `indexExpr ::= ('[' expr ']') \| ('.' nameRef)` | **yes** — `t.m` and `t["m"]` | `LuaIndexExpr` parent, or a `LuaIndexExpr` with a null `nameRef` | `DOTTED` / `BRACKET` |
| `:319` | `field ::= '[' expr ']' '=' expr \| IDENTIFIER '=' expr \| expr` | **yes** for the first two alternatives — `{ m = 1 }`, `{ ["m"] = 1 }`; no for the third (a positional value) | `LuaField` | `FIELD_KEY` |

The `when` has no `LuaFuncNameMethod` branch and falls to `else -> null` for it, together with every
`LuaNameRef` that is an ordinary variable. That is deliberate and is what makes rows 3 and 12 of
`requirements.md` pass: a second `function q:m()` is not a reason to refuse renaming `t:m`. Its cost
is a redefinition of the *same* member on the *same* receiver, which `requirements.md` states Out of
Scope and row 27 pins.

**A spelling with no `LuaNameRef` needs its own walk, and the table above marks each one.** `indexExpr`'s first alternative
is `'[' expr ']'`, so `LuaIndexExpr.getNameRef()` is null there
([LuaIndexExpr.java:13-14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaIndexExpr.java));
`field` spells its key either as a bare `IDENTIFIER` leaf or as a bracketed expression, and
`LuaField.getIdentifier()` returns a plain `PsiElement`
([LuaField.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaField.java)) — so
neither reaches the `LuaNameRef` walk above.

**Executed** — the PSI shape each `field` alternative produces, from one `configureByText` per row
(`risks-and-gaps.md` DR-05):

```
R09B[p1a] field text='m = 1'      identifier=m    exprs=[1]      exprCount=1
R09B[p1b] field text='["m"] = 1'  identifier=null exprs=["m", 1] exprCount=2
R09B[p1d] field text='1'          identifier=null exprs=[1]      exprCount=1
R09B[p1d] field text='m'          identifier=null exprs=[m]      exprCount=1
R09B[p1d] field text='f()'        identifier=null exprs=[f()]    exprCount=1
R09B[p1d] field text='[k] = 4'    identifier=null exprs=[k, 4]   exprCount=2
R09B[p1d] field text='["a b"] = 5' identifier=null exprs=["a b", 5] exprCount=2
R09B[p1a] nameRefParentsFor'm'=[]           indexExprs=[]
R09B[p1d] nameRefParentsFor'm'=[LuaVarImpl] indexExprs=[]
```

so the discriminator is exactly *identifier, else a two-expression field whose first expression is a
plain identifier-shaped literal*: a positional value (`1`, `m`, `f()`) has one expression and no
identifier, a computed key (`[k] = 4`) has two but no literal, and `["a b"] = 5` has a literal that
cannot name an identifier.

```kotlin
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

    private fun fieldOccurrences(
        file: PsiFile,
        name: String,
    ): List<Undecided> =
        PsiTreeUtil.findChildrenOfType(file, LuaField::class.java).mapNotNull { field ->
            ProgressManager.checkCanceled()
            if (fieldKeyName(field) != name) return@mapNotNull null
            Undecided(field, Spelling.FIELD_KEY)
        }

    /** The member `{ m = 1 }` / `{ ["m"] = 1 }` names, or null when this field names no member. */
    private fun fieldKeyName(field: LuaField): String? {
        field.identifier?.let { return it.text }
        if (field.exprList.size != 2) return null
        return literalName(field.exprList[0])
    }
```

```kotlin
    /** The member a `t["m"]` step or a `["m"] =` key names, or null when it is not a plain literal. */
    private fun literalName(expr: LuaExpr?): String? {
        val text = expr?.text?.trim() ?: return null
        if (text.length < 2) return null
        val quote = text.first()
        if ((quote != '"' && quote != '\'') || text.last() != quote) return null
        val inner = text.substring(1, text.length - 1)
        return inner.takeIf { it.isNotEmpty() && it.all { char -> char.isLetterOrDigit() || char == '_' } }
    }
```

- **Why one literal reader for both, and not the `LuaCatsTypeRenameProcessor`-style resolve.** The
  alternative is decoding every Lua string form, including long brackets — the surface [[BUG-467]] is
  the record of. This reader accepts exactly the form that can *name an identifier*: one `'` or `"`
  delimiter pair around `[A-Za-z0-9_]+`. Anything else — a long bracket, an escape, a concatenation,
  a variable — returns null and the step is **not** an occurrence, which is the Out-of-Scope
  disposition `requirements.md` states and rows 20 and 26 pin. Escapes cannot spell a new identifier
  character, so refusing to decode them costs no reachable occurrence. `t["m"]` and `{ ["m"] = 1 }`
  are the same question about the same text, so they share the reader rather than each carrying a
  copy.
- **The field spelling is `FIELD_KEY`, not `BRACKET`, even in its bracketed form.** Both alternatives
  of `field` *declare* a member of the table being constructed, which is a different thing to say to
  a user than "a string key reads this member", and §7.2 gives them different messages.
- **A field key is never in the usage set**, so `fieldOccurrences` needs no membership test:
  `ReferencesSearch` returns `LuaNameRef`s, and `LuaColonCallResolution.methodNameLeafOf` refuses a
  `LuaField` outright ([LuaColonCallResolution.kt:136](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaColonCallResolution.kt)).
  **Executed** on every field fixture: `inUsages=false` (DR-05).

### 3.4 `colonCallVerdict` — clauses (a) and (b), the only clause that resolves

```kotlin
    private fun colonCallVerdict(
        nameRef: LuaNameRef,
        usages: Set<PsiElement>,
    ): Undecided? {
        if (nameRef in usages) return null
        if (LuaColonCallResolution.declarationLeafOf(nameRef) != null) return null
        return Undecided(nameRef, Spelling.COLON_CALL)
    }
```

- **A site that resolves at all is decided, whether or not it resolves to this declaration.** If it
  binds elsewhere it is a different member; if it binds *here* but is missing from `usages`, it is a
  usage the platform did not collect, and reporting it as undecided would raise a conflict on a site
  that is about to be renamed anyway. That second case is not hypothetical: `risks-and-gaps.md`
  DR-01 measured `ReferencesSearch` and `declarationLeafOf` disagreeing for exactly one ZeroBrane
  declaration, and Gap 2.5 tracks it. The clause is written as "resolves ⇒ decided" so the
  disagreement costs a missing conflict rather than a false one.
- **Clause (a) is measurably never load-bearing on real code, and is still specified.** Over both
  measured trees, **no** colon occurrence was in the usage set while failing to resolve —
  `R09R[<tree>] clauseAliveDecls project=0 all=0` for every tree measured, across 14 116 corpus and
  2 446 substitute colon call sites (`risks-and-gaps.md` DR-01, re-measured). It is kept because
  Gap 2.5 records one declaration on which the two instruments disagree, and because dropping it
  would make such a site a *false* conflict on something about to be rewritten. Its falsifier is
  reachable at the unit level rather than through a Lua fixture: `undecidedOccurrences` takes the
  usage set as a parameter, so `requirements.md` row 28 passes a set containing a non-resolving
  colon occurrence and asserts it is not reported.
- **Membership is by identity, and `usages` is an identity set.** `findCollisions` receives
  `UsageInfo`s whose `getElement()` re-derives from a `SmartPsiElementPointer`
  (`LuaRenameConflictDetector.kt:98-100` records the same hazard), so §5 builds the set once, in
  the same read action, and the `LuaNameRef`s the scan produces come from the same PSI.
- **Complexity**: one `declarationLeafOf` per same-named colon call site that is not already a
  usage. This is the dominant cost and the reason DR-03 timed the whole predicate.

### 3.5 Why the usage set is taken from `findCollisions`' argument, not searched again

`RenameUtil.findUsages` fills `result` from `processUsages` — which is what calls
`LuaRenameProcessor.findReferences` — and then hands that very list to `findCollisions`
(`RenameUtil.java:93-105`). `LuaRenameProcessor.findCollisions` already snapshots it before
appending ([:169-179](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)),
and `LuaRenameConflictDetector.collisions` already takes it as a parameter
([:120-132](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt)).
So §5's arm converts it to an element set and passes it down; **no second `ReferencesSearch` is
performed anywhere in this feature.**

### 3.6 The EDT refusals

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

- **Keyed on the caret, not on the resolved element.** `REFACT-01` risks Gap 2.4 records why an
  element guard cannot work: `TargetElementUtilBase` tries `REFERENCED_ELEMENT_ACCEPTED` first, so
  the processor receives the resolved `m` leaf whose text is `"m"`.
  `PsiUtilBase.getElementAtCaret(editor)` is
  `file.findElementAt(editor.caretModel.offset)`
  (`platform/analysis-api/src/com/intellij/psi/util/PsiUtilBase.java:91-97`) and gives the token the
  user selected. It is not imported anywhere in `src/main` today; the repo's existing caller is
  [LuaJsonSchemaEngineTest.kt:163](../../../../src/test/kotlin/net/internetisalie/lunar/lang/schema/LuaJsonSchemaEngineTest.kt),
  so `grep PsiUtilBase src/main` returning nothing is expected rather than a fictional symbol.
- **The discriminator is `caret.text != leaf.text`, not the literal `"self"`** — an **analogy** to,
  not the same comparison as, `LuaTargetElementEvaluator.adjustTargetElement`
  ([:136](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaTargetElementEvaluator.kt)),
  which compares `caretReference.canonicalText == declaredNameLeaf.text` — a *reference's* canonical
  text, where this guard compares a caret *leaf's* text. The shape is shared; the operands are not.
  A `"self"` test would be wrong in both directions: `function T.m(self, x)` is legal Lua whose
  `self` is an ordinary `PARAMETER` and must rename normally (`REFACT-01` TC-19c), and a **call-site**
  caret has `caret.text == leaf.text == "m"` and must proceed (`REFACT-09-02`).
- **`editor == null` means there is no caret, so there is nothing to guard.** Both production entry
  points that pass a possibly-null editor — `PsiElementRenameHandler.rename` and
  `RenamePsiElementProcessorBase.substituteElementToRename(element, editor, callback)` — take it
  from the data context, and a null editor is a Project-View invocation. A test must therefore drive
  `REFACT-09-04` through `myFixture.renameElementAtCaret`, which passes the fixture editor
  (`CodeInsightTestFixtureImpl.java:1104`), and **not** through `LuaRenameTest.assertRefusedWith`
  ([:1205-1216](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameTest.kt)),
  which passes `null`.

```kotlin
    private fun outOfProjectRefusal(leaf: PsiElement): String? {
        val file = leaf.containingFile?.virtualFile ?: return null
        if (GlobalSearchScope.projectScope(leaf.project).contains(file)) return null
        return LuaBundle.message("refactoring.rename.colonMethod.outOfProject", file.presentableUrl)
    }
```

- **This is reachable, not defensive.** `local f = io.open("x")` / `f:write("y")` resolves into the
  plugin's own bundled stub. Executed (`risks-and-gaps.md` DR-04):
  `R09PRED[j01] resolved=write file=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua
  writable=false`, and `R09SCOPE projectScopeContainsStub=false projectScopeContainsOwnFile=true`.
  Without the guard, `myFixture.renameElementAtCaret` fails with
  `AssertionError: element not found in file` — a fixture-level symptom of the substitution handing
  back an element in a different file, not a production verdict, which is precisely why an explicit
  refusal is specified rather than left to the platform's read-only check.
- **Prior art**: `LuaCatsTypeRenameProcessor.substituteElementToRename` refuses a type declared
  outside the project by the same rule ([:85-93](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaCatsTypeRenameProcessor.kt)),
  through `LuaCatsTypeDeclarations.outOfProjectDeclarationFiles`
  ([:202-212](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsTypeDeclarations.kt)),
  which is `projectScope.contains` + `presentableUrl`. This is that rule applied to one element
  instead of an index result, so no helper is shared and none is duplicated.
- **Cost on the EDT**: one `GlobalSearchScope.contains`, no index read, no parse.

### 3.7 `receiverAlreadyHasNewName` — `REFACT-09-08`

```kotlin
    fun receiverAlreadyHasNewName(
        target: LuaRenameTarget,
        usages: Set<PsiElement>,
    ): Boolean = usages.any { hasMember(receiverTypeOf(it), target.newName) }

    private fun receiverTypeOf(usage: PsiElement): LuaType? {
        val receiver = colonCallReceiver(usage) ?: return null
        val types = LuaTypesSnapshot.forFile(receiver.containingFile)
        return types.graphTypeToLuaType(types.getValueType(receiver))
    }

    /** `t:m()`'s `t`, or null for every shape [LuaColonCallResolution] also refuses. */
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

    private fun hasMember(
        type: LuaType?,
        name: String,
    ): Boolean {
        if (type == null) return false
        if (type.resolveMember(name) != null) return true
        return type is LuaUnionType && type.types.any { it.resolveMember(name) != null }
    }
```

- **The question is asked of a CALL-SIDE receiver, because the declaration side has no type.**
  `LuaTypesVisitor.visitFuncDecl` looks its receiver up with `scope.lookup(baseName)` and never
  registers the `funcName`'s `nameRef` in `elementNodes`
  ([LuaTypesVisitor.kt:817-826](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)),
  and `LuaTypesSnapshot.getValueType` is `typeOf(elementNodes[element]?.firstOrNull())`
  ([LuaTypes.kt:75](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt)),
  so a declaration-side receiver types as `unknown` in **every** receiver shape. Executed
  (`risks-and-gaps.md` DR-05):

```
R09C[local]     M1declSideValueType type='unknown'  M3global type='unknown'
R09C[annotated] M1declSideValueType type='unknown'  M3global type='unknown'
R09C[global]    M1declSideValueType type='unknown'  M3global type='{  }' plain=true decl='function Obj:n() end'
```

  A rule keyed on the declaration-side receiver is therefore inert for every local and annotated
  receiver, which is why this section takes its handle from the usage set instead.
- **`resolveMember` plus the union-arm loop is the same lookup `LuaColonCallResolution` performs**
  ([:100-110](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaColonCallResolution.kt)),
  on the same kind of element: `colonCallReceiver` here and `receiverOf` there
  ([:79-89](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaColonCallResolution.kt))
  both take `call.varOrExp.var.nameRef` off a colon call and refuse the same chain, parenthesised and
  suffixed shapes. So "the receiver already has this member" and "a call to that member would
  resolve" are one question asked of one element.
- **The union-arm loop is required, not optional.** A `---@class`-annotated receiver types as
  `{ … } | Builder`, whose anonymous arm carries no `withName`, so the plain `resolveMember` returns
  null for exactly the shape `REFACT-09-08` is most likely to meet. Executed — the same fixture with
  and without the loop:

```
R09E[annotated]         receiver='Builder' type='{  } | Builder' plain=false unionAware=true
R09E[annotatedNegative] receiver='Builder' type='{  } | Builder' plain=false unionAware=false
```

- **Executed verdicts, one `configureByText` per row** (`risks-and-gaps.md` DR-05, the `R09F` lines).
  Every row is a fixture this design must decide, and the rule decides every one of them:

| fixture | rename | `receiverAlreadyHasNewName` |
| :-- | :-- | :-- |
| `local t = {}` / `function t:m()` / `function t:n()` / `t:m()` / `t:n()` | `m`→`n` | **true** — `requirements.md` row 17 |
| `local t = {}` / `function t:m()` / `t:m()` | `m`→`n` | false |
| `---@class Builder` … `function Builder:setName` / `function Builder:withName` / `Builder:setName("x")` | `setName`→`withName` | **true** |
| the same without `function Builder:withName` | `setName`→`withName` | false |
| `Obj = {}` / `function Obj:m()` / `function Obj:n()` / `Obj:m()` | `m`→`n` | **true** |
| `local t = {}` / `function t:m()` / `local q = {}` / `function q:n()` / `q:n()` / `t:m()` | `m`→`n` | false — a different receiver's member |
| `local t = { n = 1 }` / `function t:m()` / `t:m()` | `m`→`n` | **true** — a constructor key is a member |
| `local t = {}` / `function t:m()` / `t:m()` / `local u = { n = 1 }` | `m`→`n` | false — another table's key |
| `local t = {}` / `function t:m()` / `function t.n()` / `t:m()` | `m`→`n` | **true** — the dotted spelling names the same key |
| `local t = {}` / `function t:m()` / `t.n = 1` / `t:m()` | `m`→`n` | **true** |
| outer `local t` / `function t:m()` / `t:m()` beside an inner `do local t = {} function t:n() end t:n() end` | `m`→`n` | false — the shadowing `t` is a different receiver |
| `---@class Builder` … `function Builder:setName` / `function Builder:withName` / `local b = Builder` / `b:setName("x")` | `setName`→`withName` | **true** — the usage's receiver is the alias, and the class arm still carries the member |

- **Cost**: one `LuaTypesSnapshot.forFile` and one `resolveMember` per usage, short-circuited by
  `any`, on top of the occurrence scan §3.2 already pays for. The snapshot is
  `CachedValuesManager`-backed and, for the common single-file rename, already warm from the scan.
  This runs in the same background read action, never on the EDT.

- **This replaces `globalNameTaken` for this kind, and the shadowing row is why.** The index rule
  searches the key `"t:n"` project-wide (`searchKeyOf`, `LuaRenameConflictDetector.kt:259-262`), so
  any unrelated `t` anywhere in the project reports a merge that cannot happen; §5 records the
  executed transcript. What the replacement gives up is stated with it: see §5.
- **The measured misses are all in the "no conflict reported" direction.** Reporting nothing is the
  same failure mode the shipped build has today, whereas a false conflict on a correct rename is the
  failure `requirements.md` row 12 exists to prevent, so the rule is written to miss rather than to
  over-report.
  1. **A declaration with no bound call site has no handle.** `usages` is empty for
     `local t = {}` / `function t:m() end` / `function t:n() end`, so the rule returns false and the
     merge is not reported: `R09F[noCallSites] usages=[] MECHANISM receiverAlreadyHasNewName=false`.
     `requirements.md` row 29 pins it as a property rather than leaving it to be discovered.
     `risks-and-gaps.md` Gap 2.8 records what would close it.
  2. **A member declared in another file, on a global receiver, is not seen.** With `a.lua` =
     `Obj = {}` / `function Obj:m() end` / `Obj:m()` and `b.lua` = `function Obj:n() end`, the
     receiver's type in `a.lua` carries no `n`:
     `R09F[crossFile] MECHANISM receiverAlreadyHasNewName=false`. This is the one case
     `globalNameTaken` did decide, and Gap 2.9 states the trade.

## 4. External data & parsing

**One parsed input: a Lua short-string literal used as a table key**, in both places one can appear —
`t["m"]` (a read) and `{ ["m"] = 1 }` (a constructor key). Its format is §3.3's `literalName`:
delimiter `'` or `"`, matching at both ends, contents `[A-Za-z0-9_]+`, no escape processing,
anything else rejected. One reader serves both callers. There is no CLI output, file format or
network response in this feature.

## 5. `LuaRenameConflictDetector` — the `METHOD_FUNCTION` arm

`collisions` ([:120-132](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt))
becomes:

```kotlin
        val found =
            if (target.kind == LuaDeclarationKind.METHOD_FUNCTION) {
                colonMethodCollisions(target, usages)
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
    private fun colonMethodCollisions(
        target: LuaRenameTarget,
        usages: List<UsageInfo>,
    ): List<LuaRenameCollision> {
        val usageElements = Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
        usages.mapNotNullTo(usageElements) { ProgressManager.checkCanceled(); it.element }
        val incomplete =
            LuaColonMethodRename.undecidedOccurrences(target, usageElements).map { undecided ->
                LuaRenameCollision(undecided.occurrence, incompleteMessage(target, undecided))
            }
        if (!LuaColonMethodRename.receiverAlreadyHasNewName(target, usageElements)) return incomplete
        val taken =
            LuaRenameCollision(
                target.identifier,
                LuaBundle.message("refactoring.rename.conflict.memberExists", target.newName),
            )
        return incomplete + taken
    }

    private fun incompleteMessage(
        target: LuaRenameTarget,
        undecided: LuaColonMethodRename.Undecided,
    ): String =
        LuaBundle.message(
            when (undecided.spelling) {
                LuaColonMethodRename.Spelling.COLON_CALL -> "refactoring.rename.colonMethod.undecidedCall"
                LuaColonMethodRename.Spelling.DOTTED -> "refactoring.rename.colonMethod.dottedSpelling"
                LuaColonMethodRename.Spelling.BRACKET -> "refactoring.rename.colonMethod.bracketSpelling"
                LuaColonMethodRename.Spelling.FIELD_KEY -> "refactoring.rename.colonMethod.fieldKey"
            },
            target.identifier.text,
        )
```

`Collections`, `IdentityHashMap` and `ProgressManager` are already imported by this file
([:3, :21-22](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt));
the identity set is the same idiom `distinctByAnchor` uses there.

**Why each pre-existing rule must not run for a colon method.** `collisions` selects a rule set by
`LuaDeclarationKind`, so the arm has to account for every rule the function can reach. The list is
read off the source — `captures` (`:154`), `shadows` (`:178`), `globalNameTaken` (`:208`),
`ambiguousGlobal` (`:227`) — rather than recalled as a number, and it must be re-read, not
re-trusted, if a rule is added.

- **`captures`** asks whether a *lexically visible* declaration of the new name would capture a
  usage. Since [[NAV-13]] a colon member name has **no** lexical binding at all — that withdrawal is
  `NAV-13-05` — so a visible `local n` has nothing to do with `t:n`. Left running, it reports a
  capture on every colon call site whenever a `local n` is in scope; `requirements.md` row 18 is the
  fixture and the mutation.
- **`shadows` never ran for this kind and still does not.** It is reached only through
  `if (target.kind.isFileLocal)` (`:126`), and the kind is declared
  `METHOD_FUNCTION("global function", false)`
  ([LuaDeclarationSite.kt:27](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)).
  It is listed because an enumeration that silently omits an inert rule is indistinguishable from
  one that overlooked a live one.
- **`globalNameTaken`** fires on the right key — `searchKeyOf` prefixes the receiver, so it searches
  `"t:n"` (`:259-262`) — but against the project-wide `LuaGlobalDeclarationIndex`, so a different
  file's `local t` with a `t:n` reports a merge that cannot happen. **Executed**:
  `R09PROBE[R12] THREW ConflictsInTestsException: A global named 't:n' already exists in this
  project; renaming would merge the two` on `local t = {} / function t:m() end / function t:n() end
  / t:m() / t:n()` — the right verdict from a rule that is wrong one file away. §3.7 asks the
  receiver's own type instead. **What the replacement gives up, measured:** the index rule is the
  only one that can see a member declared in *another* file on a global receiver, and §3.7 cannot —
  `R09F[crossFile] MECHANISM receiverAlreadyHasNewName=false` on `a.lua` = `Obj = {}` /
  `function Obj:m() end` / `Obj:m()` beside `b.lua` = `function Obj:n() end`. The trade is a missing
  conflict in that shape against a false conflict in every file that happens to spell a receiver
  `t`; `risks-and-gaps.md` Gap 2.9 carries it.
- **`ambiguousGlobal`** rests on "with two declarations, `LuaNameReference.resolve()` returns null,
  so usages stop being findable". For a colon method the usages are found through the receiver's
  *type*, per file, so the premise is false. **Executed**: two files each containing
  `local t = {}` / `function t:m() end` / `t:m()` raise
  `R09PROBE[R11] THREW ConflictsInTestsException: 't:m' is declared in 2 places; while more than
  one declaration exists its usages do not resolve, so they will not be rewritten`, while the
  predicate answers `R09PRED[c07] verdict=accepted` and the rename is in fact correct and
  file-local.

**The two rules ask different questions and must not be conflated.** `undecidedOccurrences` looks for
the **old** name in any member position anywhere in the scope and does not ask whose table it is —
`local u = { m = 1 }` beside `function t:m()` is reported (`requirements.md` row 24).
`receiverAlreadyHasNewName` looks for the **new** name on **this receiver's type** only —
`local u = { n = 1 }` beside `function t:m()` is not a conflict (row 17c). An implementation that
answered either question with the other's scope fails one of those two rows.

`LuaRenameCollisionUsageInfo` is reused unchanged and no new `UsageInfo` subclass is defined
(`REFACT-09-08`). `distinctByAnchor` still runs over the combined list, so several spellings at one
anchor report once.

## 6. Edge cases

| Case | Handling | Where |
| :-- | :-- | :-- |
| Caret on the receiver of `function t:m()` | already refused by `receiverSegmentRefusal`, unchanged | `LuaRenameProcessor.kt:399-403` |
| Caret on `...` | `canProcessElement` is false for an `ELLIPSIS`, unchanged | `REFACT-01` TC-19b |
| Caret on the `m` of `self:m()` | `self` reaches no declaration ([[NAV-13]] Out of Scope), so `resolvedDeclarationLeaf` refuses with `refactoring.rename.unresolved` — unchanged behaviour. **Executed**: `R09PROBE[R03] THREW RefactoringErrorHintException` | §2.2, unchanged route |
| `function t:m()` with no `funcName` node (`function repeat() end`) | `kindOf` is null, so the `METHOD_FUNCTION` branch is never entered | `LuaDeclarationSite.kt:224-229` (SYNTAX-18) |
| A malformed `function` elsewhere in a scanned file | the scan enumerates `LuaNameRef`, `LuaIndexExpr` and `LuaField`, never `LuaFuncDecl.getFuncName()`, so a rolled-back `funcName` contributes nothing | §3.3 |
| The new name is not a Lua identifier | `LuaNamesValidator` rejects it in the dialog before this code runs | `plugin.xml` `<lang.namesValidator>` |
| Rename invoked while indexing | `LuaRenameProcessor` is `DumbAware`; `CacheManager.getFilesWithWord` is an index read, so a dumb-mode rename ends as the platform's "not available while indexing" report or as a refusal — never as a half-applied rename, which is the property `LuaRenameProcessor`'s KDoc already argues (`:53-63`) | §5 |
| `function T.m(self, x)` | `kindOf` is `PARAMETER`, so §3.6's caret guard is not reached and the parameter renames normally | `REFACT-01` TC-19c |
| Two receivers with the same method name in one file | each call site resolves to its own declaration, so neither is undecided | §3.4, `requirements.md` row 3 |
| Zero call sites (`function t:m() end` alone) | no usages and no occurrences → no conflict; the declaration renames alone | §3.2, `requirements.md` row 5 |
| `t[k]` / `t[a .. b]` anywhere | not an occurrence — the index names nothing in the PSI | §3.3, `requirements.md` row 20 |
| `{ m = 1 }` or `{ ["m"] = 1 }` on the renamed method's own table | reported as `FIELD_KEY`: the constructor key still declares the old member after the rename | §3.3, `requirements.md` row 23 |
| `{ 1, m, f() }` — a positional value spelled like the member | not an occurrence: the field has no identifier and one expression, and a bare `m` is a `LuaVar` head | §3.3, `requirements.md` row 26 |
| `{ [k] = 1 }` / `{ ["a b"] = 1 }` | not an occurrence: no literal key, and a literal that cannot spell an identifier | §3.3, `requirements.md` row 26 |
| A second `function t:m()` on the **same** receiver | not an occurrence and not a usage: `LuaFuncNameMethod` is excluded so that rows 3 and 12 pass. Executed — the call site binds to the FIRST declaration (`R09H[localRedef] callSite@53 resolvesTo=24`), so renaming it rewrites the call and leaves the second definition on the old name | Out of Scope, `requirements.md` row 27, `risks-and-gaps.md` Gap 2.10 |
| `---@field m` on the receiver's class | not seen by the scan: `LuaCatsLazyCommentImpl` is a `LazyParseablePsiElement`, not a `PsiComment`, and a tag is not a `LuaNameRef` | `risks-and-gaps.md` Gap 2.3 |
| The user narrows the rename dialog's scope | the usage set narrows with it while §3.3 still scans the project, so occurrences outside the chosen scope are reported — conservative, never unsound | `risks-and-gaps.md` Gap 2.6 |

## 7. Integration points

### 7.1 `plugin.xml` — no change

Every class this feature touches is already registered:

```xml
<!-- src/main/resources/META-INF/plugin.xml:397-402 — unchanged, verbatim -->
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor"/>
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaLabelRenameProcessor"/>
<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaCatsTypeRenameProcessor"/>
```

**The order is load-bearing and must not change.** `RenamePsiElementProcessorBase.forPsiElement`
returns the *first* matching extension, and `LuaRenameProcessor` is registered first — which is why
§9 Alternative A cannot add a fourth entry, and why the guards of §3.6 live inside
`LuaRenameProcessor` rather than in a processor of their own.

`LuaColonMethodRename` is a plain `internal object` reached only from `LuaRenameConflictDetector`;
it is not an extension and gets no entry. No new index, no new service, no new settings key, and no
change to `<referencesSearch>`, `<lang.findUsagesProvider>` or any `psi.referenceContributor`.

### 7.2 `LuaBundle.properties` — the falsified key removed, and one key per spelling and per refusal

Removed
([:153](../../../../src/main/resources/net/internetisalie/lunar/LuaBundle.properties)) —
`REFACT-09-09`, because [[NAV-13]] made its text untrue:

```
refactoring.rename.colonMethod=…
```

Added, in the existing `refactoring.rename.*` block, split by the phase that introduces them so a
phase cannot ship a renderer without its key:

**Phase 1 — the keys `LuaRenameConflictDetector`'s arm renders (§5):**

```
refactoring.rename.colonMethod.undecidedCall=This call to ''{0}'' cannot be bound to a declaration, so it may be a call of this method and will not be renamed.
refactoring.rename.colonMethod.dottedSpelling=This names the same member as ''{0}'' in the dotted form, which this rename does not rewrite.
refactoring.rename.colonMethod.bracketSpelling=This names the same member as ''{0}'' through a string key, which this rename does not rewrite.
refactoring.rename.colonMethod.fieldKey=This table-constructor key declares the same member as ''{0}'', which this rename does not rewrite.
```

Every value of `LuaColonMethodRename.Spelling` has an entry above, and §5's `incompleteMessage` is
an exhaustive `when` over that enum, so a spelling added without its key does not compile.

**Phase 2 — the keys `LuaRenameProcessor` renders (§3.6):**

```
refactoring.rename.colonMethod.caretNotOnMethod=''{0}'' is not the method name; put the caret on the method name to rename it.
refactoring.rename.colonMethod.outOfProject=This method is declared outside this project, in ''{0}'', so it cannot be renamed here.
```

**Phase 3 — the member-collision key (§5), which is a `conflict.*` key, not a `colonMethod.*` one:**

```
refactoring.rename.conflict.memberExists=This table already has a member named ''{0}''; renaming would merge the two.
```

Every message uses `''` for a literal apostrophe, as every neighbouring entry does — the file is
read through `MessageFormat`. Phase 4's exit gate is that **each key named in this section** is
present and `refactoring.rename.colonMethod` is absent, not a count of them.

## 8. Requirement coverage

| Requirement | Priority | Implemented by |
| :-- | :-- | :-- |
| `REFACT-09-01` | M | unchanged route — `findReferences` + `renameElement`; §2.2 removes the refusal that blocked it. Executed: DR-02 `R01` |
| `REFACT-09-02` | M | unchanged route — `resolvedDeclarationLeaf` + [[NAV-13]]'s resolve; §2.2. Executed: DR-02 `R02` |
| `REFACT-09-03` | M | §3.1-§3.5, §5, §7.2 Phase 1. The occurrence set is closed over `lua.bnf` by §3.3's table |
| `REFACT-09-04` | M | §3.6 `caretRefusal`, §7.2 Phase 2 |
| `REFACT-09-05` | M | §3.6 `outOfProjectRefusal`, §7.2 Phase 2 |
| `REFACT-09-06` | M | unchanged route — `LuaRenameProcessor`'s existing `LOCAL_VARIABLE` path; §6 row 1 covers the declaration-side caret |
| `REFACT-09-07` | M | unchanged route — `renameElement`'s single non-cancelable section (REFACT-01 design §3.3); a refusal returns before any write (§2.2) and a declined conflict aborts in `preprocessUsages` before `performRefactoring` |
| `REFACT-09-08` | S | §3.7, §5, §7.2 Phase 3. Each measured miss is stated in §3.7 and pinned — `requirements.md` row 29 and `risks-and-gaps.md` Gaps 2.8 and 2.9 |
| `REFACT-09-09` | M | §7.2 |
| `REFACT-09-10` | M | §1 prior-art table (each row states *not touched* or *extended*), §5 (the arm that keeps C1/C3/C4 off this kind) |

## 9. Alternatives considered

**A. A separate `LuaColonMethodRenameProcessor`, mirroring `LuaLabelRenameProcessor`.** Rejected.
`RenamePsiElementProcessorBase.forPsiElement` returns the **first** matching extension and
`LuaRenameProcessor.canProcessElement` already claims every `LuaNameRef`, so a new processor would
have to be registered ahead of it — and would then inherit
`RenamePsiElementProcessorBase.renameElement`, losing REFACT-01's precomputed, non-cancelable write
path. That path is exactly what `REFACT-09-07` asks for, and BUG-468 is the record of what its
absence costs. REFACT-04 could take the separate-processor route because label rename needed no
write path at all.

**B. Refuse the incomplete case on the EDT, from `substituteElementToRename`.** Rejected on a
measurement, not a preference. The verdict needs a word-index read plus a resolve per candidate
occurrence: p50 23 ms but p99 525 ms and max 3 163 ms on ZeroBrane, and max **9 957 ms** on the
annotated substitute (`risks-and-gaps.md` DR-03, reproduced across two runs). `substituteElementToRename`
runs on the EDT, and the engineering contract forbids blocking it with heavy parsing. The platform's
only channel for aborting *after* background analysis is the conflicts dialog
(`RenameProcessor.java:166-188`), which is the disposition `LuaRenameConflictDetector`'s C4 rule
already took for the same reason (`:215-226`: *"Reported rather than refused … whereas refusing
would have to run these lookups on the EDT."*). `risks-and-gaps.md` Risk 1.1 carries the residual —
a user who acknowledges the dialog gets a rename that leaves the listed occurrences behind.

**C. Prove completeness syntactically, from the receiver's binding.** This is the superseded
design's predicate. Rejected: measured to accept **0 of 941** corpus declarations, with 929 declined
by two or more clauses independently, so no relaxation recovers it ([[NAV-13]] requirements, "Why
this is filed now"). It is not revived, and `requirements.md` says so where a reader would look for
it.

**D. Rewrite the dotted spelling alongside the colon one, instead of reporting it.** Rejected for
this feature. `t.m` resolves through `getQualifiedName` / `LuaGlobalDeclarationIndex`, a different
mechanism with its own decidability question, and folding it in would make one rename depend on two
resolution engines agreeing. It is `risks-and-gaps.md`'s named future work, and the reporting
clause is what makes the gap visible rather than silent — measured cost (DR-01, re-measured over the
occurrence set §3.3 now closes): the dotted spelling is the **sole** blocker for 33 of 941 corpus
declarations and 32 of 268 in the substitute, and among the declarations that have a bound call site
for 0 of ZeroBrane's 51 and 10 of the substitute's 121.

**E. Scan only the declaring file.** Rejected: measured to produce the cross-file half-renames the
`requirements.md` Overview transcribes (`R09PROBE[R09]`, `R09PROBE[R10]`), because an annotated
receiver resolves cross-file and an unannotated same-named call in another file is exactly the case
that cannot be decided from one file.

**F. Key `receiverAlreadyHasNewName` on the declaration-side receiver (`funcName.nameRef`).**
Rejected on a measurement. `LuaTypesVisitor.visitFuncDecl` never registers that `nameRef` in
`elementNodes`, so `getValueType` returns `Undefined` for it in every receiver shape and the rule
would be inert everywhere: `R09C[local|annotated|global] M1declSideValueType type='unknown'`
(`risks-and-gaps.md` DR-05). `getGlobalType(receiverText)` answers for a *global* receiver only, and
keying on the receiver's spelling in a file-scope global map re-introduces `globalNameTaken`'s error
in miniature — a file with both a global `t` and a shadowing `local t` would answer for the wrong
one. §3.7 takes the handle from the usage set instead.

## 10. Open Questions

_None — no design decision is left to the implementer, and every residual this design accepts is stated in `requirements.md` "Out of Scope" and tracked in `risks-and-gaps.md` under Design Gaps, each with its measurement and its future-work owner._
