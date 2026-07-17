---
id: "MAINT-28-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-28"
folders:
  - "[[features/maint/28-completion-correctness/requirements|requirements]]"
---

# Technical Design: MAINT-28 — Completion Correctness & Performance

## 1. Architecture Overview

### Current State

The completion stack is `lang/LuaCompletionContributor.kt` (one `CompletionContributor` with
six `extend(...)` providers) plus `lang/completion/**` support classes
(`LuaCrossFileCompletionProvider`, `GlobalSymbolRankingService`, `ProximityCalculator`,
`LuaEnterBetweenBlockHandler`). Six defects (`docs/review.md` #24, #25, #39, #40, #62, §2.5.5)
are localized to these five files. The type engine, lexer/parser, and index schemas are
consumed as-is and are **out of scope**.

Key drift already landed (do not re-do): MAINT-31 removed the dead prefix param + `prevType`
from the contributor and the constant `isImported` check from the cross-file provider; TYPE-10
rewired member completion's snapshot call to `LuaTypesSnapshot.forFile(receiverExpr.containingFile)`
with a comment justifying the **copy**-file source (PSI identity for the `elementNodes` lookup —
`lang/psi/types/LuaTypes.kt:53`, `getValueType` keys the `elementNodes` map on `PsiElement` identity).

### Prior Art in This Repo

Every fix **edits an existing component** — nothing new is created except test classes. Verified:

- `lang/LuaCompletionContributor.kt` — the six providers; `addSymbolCompletions` at
  `:216` and `:241` (both under the same `canBeExpressionStart` guard) + `:258` (identifier
  provider). This is the #39 target. Edited, not replaced.
- `lang/completion/LuaCrossFileCompletionProvider.kt:42` — `position.containingFile` (the copy).
  `extractRequires` (`:101-118`) already migrated to `CachedValuesManager` by an earlier pass
  (`docs/review.md:85` marks it PARTIAL). #24 edits the remaining index/proximity halves.
- `lang/completion/ProximityCalculator.kt:49-66` — `calculateProximityWeight`; the "same file"
  (0.9) tier is unreachable while completion passes the copy file. Consumed unchanged; #24 fixes
  it by feeding it the **original** file.
- `lang/completion/GlobalSymbolRankingService.kt` — `@Service(Level.PROJECT)`; `getAllKeys`
  at `:81`/`:151`; `extractFuncDeclName` at `:213-217`. #40 + §2.5.5 targets. Edited.
- `lang/completion/LuaEnterBetweenBlockHandler.kt:45` — the unsatisfiable guard (#25). Edited.
- `LuaRockspecDiscoveryService.kt:40-49` — the **grounded caching precedent** for §2.5.5: a
  `@Service(Level.PROJECT)` holding a `CachedValue<List<…>>` built via
  `CachedValuesManager.getManager(project).createCachedValue { Result.create(compute(),
  PsiModificationTracker.getInstance(project)) }`, guarded by `DumbService.isDumb`. MAINT-28-05
  copies this pattern into `GlobalSymbolRankingService`.
- `LuaCrossFileCompletionHeavyTest.kt` — the **grounded real-disk test precedent** (heavy
  `IdeaTestFixtureFactory` + `EmptyModuleFixtureBuilder.addSourceContentRoot` + `TempDirTestFixtureImpl`
  + `runInEdtAndWait` + `myFixture.completeBasic()`). The #24 regression test extends this class.

### Target State

Same five files, corrected: cross-file index/proximity work reads `parameters.originalFile`;
member-completion snapshot reads the copy file (unchanged); one `addSymbolCompletions` call site;
method-receiver decls excluded from global ranking; the keyword-gate prefix test uses the real
`CompletionResultSet.prefixMatcher.prefix`; the enter-between guard corrected; and the two
`getAllKeys` scans cached on `PsiModificationTracker.MODIFICATION_COUNT`.

## 2. Core Components

All edits are to existing types. No new production class is introduced. FQ names:
`net.internetisalie.lunar.lang.LuaCompletionContributor`,
`net.internetisalie.lunar.lang.completion.LuaCrossFileCompletionProvider`,
`net.internetisalie.lunar.lang.completion.GlobalSymbolRankingService`,
`net.internetisalie.lunar.lang.completion.LuaEnterBetweenBlockHandler`.

### 2.1 net.internetisalie.lunar.lang.completion.LuaCrossFileCompletionProvider
- **Responsibility**: cross-file (require-graph + project-global) completion candidates.
- **Threading**: EDT (completion `addCompletions`) reading PSI/indexes — reads are implicitly in
  a read action (completion runs under one). No new threading.
- **Collaborators**: `CompletionParameters.getOriginalFile()` (platform, verified
  `intellij-community/.../CompletionParameters.java:81`), `FileBasedIndex`, `StubIndex`,
  `GlobalSymbolRankingService`, `ProximityCalculator`.
- **Key API** (signature change — `originalFile` threaded through):
  ```kotlin
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val originalFile = parameters.originalFile as? LuaFile ?: return   // #24: index/proximity subject
      val project = originalFile.project
      val prefix = result.prefixMatcher.prefix
      processCrossFileSymbols(project, originalFile, prefix, result)
  }
  ```
  `processCrossFileSymbols`, `extractRequires`, `getLocalSymbolNames`, and
  `processGlobalsWithRanking` all take `originalFile` (was the copy). No other parameter change.

### 2.2 net.internetisalie.lunar.lang.completion.GlobalSymbolRankingService
- **Responsibility**: project-wide global symbol candidates with proximity ranking.
- **Threading**: EDT under completion read action; `DumbService.isDumb` short-circuit retained.
- **Collaborators**: `StubIndex.getAllKeys`, `CachedValuesManager`, `PsiModificationTracker`,
  `LuaFuncName`/`LuaFuncNameMethod`/`LuaFuncNamePropertyList` (verified
  `src/main/gen/.../psi/LuaFuncName.java:8-19`).
- **Key API** (two additions):
  ```kotlin
  // §2.5.5 — cached key snapshots, MODIFICATION_COUNT-invalidated (see §3.4)
  private val funcKeyCache: CachedValue<List<String>>   // LuaGlobalDeclarationIndex.KEY keys
  private val classKeyCache: CachedValue<List<String>>  // LuaClassNameIndex.KEY keys
  private fun allFuncKeys(): List<String> = funcKeyCache.value
  private fun allClassKeys(): List<String> = classKeyCache.value

  // #40 — receiver-decl exclusion, replaces the bare-identifier extraction
  private fun extractGlobalFuncName(funcDecl: LuaFuncDecl): String?  // see §3.2
  ```

### 2.3 net.internetisalie.lunar.lang.LuaCompletionContributor
- **Responsibility**: keyword + local/param/global symbol + member completion providers.
- **Threading**: EDT completion. Unchanged.
- **Collaborators**: `CompletionResultSet.getPrefixMatcher().getPrefix()` (platform), the existing
  `addSymbolCompletions` helper, `LuaTypesSnapshot.forFile`.
- **Key API** (call-site edits only, no new methods): §3.3 (#39 single call site) and §3.5 (#62
  prefix gate).

### 2.4 net.internetisalie.lunar.lang.completion.LuaEnterBetweenBlockHandler
- **Responsibility**: `DefaultForceIndent` when Enter is pressed inside an open block body.
- **Threading**: EDT (`preprocessEnter`), read after `commitDocument`. Unchanged.
- **Collaborators**: `LuaBlockPairs.terminatorForOwner` (verified
  `lang/syntax/LuaBlockPairs.kt:57`), `PsiTreeUtil.getParentOfType`.
- **Key API**: `preprocessEnter(...)` — guard corrected (§3.1). No signature change.

## 3. Algorithms

### 3.1 #25 — Enter-between-blocks guard correction (MAINT-28-04)
- **Input → Output**: `(caretOffset: Int, owner: LuaBlockParent|LuaTableConstructor,
  terminator: ASTNode)` → `EnterHandlerDelegate.Result`.
- **Root cause**: current guard is
  `offset in (leaf.textRange.endOffset + 1) until terminator.startOffset`, where
  `leaf = file.findElementAt(offset - 1)`. By definition `leaf.textRange.endOffset >= offset`, so
  the lower bound `leaf.textRange.endOffset + 1 > offset` **always** — the range is empty and the
  branch is dead (`docs/review.md:86`). `leaf` is also the wrong anchor: it is the caret-adjacent
  leaf, not the block opener.
- **Corrected steps** (replace only the final `return if` expression at `:45`):
  1. `val openerStart = owner.textRange.startOffset` (the owner node starts at its opener leaf —
     `if`/`while`/`for`/`function`/`do`/`repeat`/`{`).
  2. `val terminatorStart = terminator.startOffset` (absolute; `terminator` is an `ASTNode`
     child, `startOffset` is document-absolute).
  3. Fire indent iff the caret is strictly after the opener start and at-or-before the terminator
     start: `return if (offset in (openerStart + 1)..terminatorStart) Result.DefaultForceIndent
     else Result.Continue`.
- **Rules / edge handling**:
  - Caret exactly at `terminatorStart` (immediately before `end`/`until`/`}`) → indent (the body
    line is opened above the terminator). This is the COMP-08-04 "between an already-matched
    opener and its terminator" case the doc-comment at `:20-21` describes.
  - Caret at `openerStart` or earlier, or past `terminatorStart` → `Continue`.
  - `offset == 0` early-return and non-`LuaFile` early-return at `:30-33` are retained.
  - The `leaf` local (`file.findElementAt(offset - 1)`) is retained only to seed
    `getParentOfType(leaf, …)`; it is no longer used in the bound.

### 3.2 #40 — Method-receiver exclusion from global ranking (MAINT-28-03)
- **Input → Output**: `LuaFuncDecl` → `String?` (the global name, or `null` to skip the decl).
- **Root cause**: `extractFuncDeclName` (`:213-217`) returns `funcName.nameRef.identifier.text`,
  the **base** identifier. For `function Class:method()` and `function M.fn()` the stub key is
  `"<Class>:<method>"` / `"<Class>.<fn>"` (per `.agents/AGENTS.md` "class/method idioms"), so the
  base identifier is the **receiver** (`Class` / `M`), which is then offered as a standalone
  global-function candidate (`docs/review.md:101`).
- **Steps** (`extractGlobalFuncName`, replacing `extractFuncDeclName`):
  1. `val funcName = funcDecl.funcName ?: return null`.
  2. If `funcName.funcNameMethod != null` → the decl has a `:method` separator → `return null`
     (it is a method, not a global). `LuaFuncName.getFuncNameMethod()` verified
     `src/main/gen/.../psi/LuaFuncName.java:11`.
  3. If `funcName.funcNamePropertyList.isNotEmpty()` → the decl has a `.property` path
     (`M.fn`, `a.b.c`) → `return null` (its base is a table receiver, not a standalone global).
     `LuaFuncName.getFuncNamePropertyList()` verified `LuaFuncName.java:14`.
  4. Otherwise it is a bare `function name()` → `return funcName.nameRef.identifier?.text`.
- **Rules / edge handling**: a `null` return `continue`s the ranking loop (existing `?: continue`
  at `:104`). Class-symbol extraction (`extractClassElementName`, `:223-227`) is **unchanged** —
  `@class` locals have no method separator and are a distinct index.
- **Complexity**: O(1) per decl; two null/empty checks. No new traversal.

### 3.3 #39 — Single symbol pass (MAINT-28-02)
- **Root cause**: within the keyword provider, `addSymbolCompletions(position, result)` is called
  at both `:216` and `:241`, both guarded by the same `if (canBeExpressionStart)`. A third call
  is the whole body of the dedicated IDENTIFIER-pattern provider (`:251-261`). Each call runs the
  full enclosing-scope walk in `addSymbols` (`:93-152`); results dedupe only by lookup-element
  equality (`docs/review.md:100`).
- **Steps**:
  1. Delete the second call at `:241` (and its now-empty `if (canBeExpressionStart) { … }` block
     comment at `:236-242`). Keep the `:216` call (inside the `canBeExpressionStart` branch, where
     the expression-keyword gate also lives).
  2. Delete the entire IDENTIFIER-pattern symbol provider (`extend(..., psiElement().withElementType(
     LuaElementTypes.IDENTIFIER), object : CompletionProvider… { addSymbolCompletions(position,
     result) })`, `:247-261`). This is NOT a universal strict subset of `:216` — the two patterns
     diverge, and the divergence is analyzed context-by-context below; the dropped contexts are
     the INTENDED semantic change.
  3. Net: exactly one `addSymbolCompletions` invocation per completion session (at `:216`).
- **Pattern-by-pattern divergence analysis** (replaces the earlier subset claim, which was wrong):
  - The `:216` call fires only under `canBeExpressionStart` (`:198-212`): the enclosing `LuaExpr`
    starts at the caret identifier, OR `prevLeaf` ∈ {ASSIGN, LPAREN, LBRACK, LCURLY, COMMA, …}.
    Note precisely: the `:200` gate checks `getParentOfType(position, LuaExpr)`; a `LuaNameRef`
    is a `LuaNameRefElement`, NOT itself a `LuaExpr` — the gate keys on the enclosing
    expression's start offset, not on the nameRef.
  - Contexts where the IDENTIFIER provider fires but `canBeExpressionStart` is FALSE:
    **after `.` or `:`** (`t.field<caret>`, `obj:meth<caret>`): the enclosing `LuaExpr` starts at
    `t`/`obj` (offset ≠ caret identifier), and `prevLeaf` is DOT/COLON — not in the leaf set.
    Today the IDENTIFIER provider leaks the WHOLE standalone scope walk (locals, params, globals)
    into these member positions.
  - **Decision: dropping the leak is intended.** After `.`/`:` the member-completion provider
    (`:270-305`) owns the position; standalone scope symbols there are noise (a local `price`
    is not a member of `t`). This matches standard IDE member-completion UX and the review's
    intent for #39 (one symbol pass in symbol positions, not three passes everywhere).
  - Any other non-expression-start IDENTIFIER context (e.g. inside a statement keyword sequence)
    similarly loses the scope walk — same rationale: those are not symbol-insertion positions.
- **Fold-in verification**: in every position where standalone symbols BELONG
  (expression starts), `:216` fires; the deletion removes only the member-position leak. The
  `psiElement().withElementType(IDENTIFIER)` **cross-file** provider registration (`:263-268`)
  is a **different** provider and is retained.
- **Regression guard**: TC-39 (statement-start `pri<caret>` still offers locals, exactly once)
  AND TC-39b (after-dot `t.pri<caret>` offers NO standalone locals — the leak is gone; member
  results unaffected).

### 3.4 §2.5.5 — Modification-tracked key caching (MAINT-28-05)
- **Input → Output**: none → `List<String>` (the `getAllKeys` result), memoized until the next
  PSI modification.
- **Root cause**: `StubIndex.getInstance().getAllKeys(...)` is called on **every** completion
  invocation, twice (`:81`, `:151`) — once per index (`docs/review.md:254`). Keys change only on
  a PSI/stub edit.
- **Steps** (in `GlobalSymbolRankingService`, mirroring `LuaRockspecDiscoveryService.kt:40-49`):
  1. Add two fields:
     ```kotlin
     private val funcKeyCache: CachedValue<List<String>> =
         CachedValuesManager.getManager(project).createCachedValue({
             CachedValueProvider.Result.create(
                 if (DumbService.isDumb(project)) emptyList()
                 else StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project).toList(),
                 PsiModificationTracker.getInstance(project),
             )
         }, /* trackValue = */ false)
     private val classKeyCache: CachedValue<List<String>> = /* same, LuaClassNameIndex.KEY */
     ```
  2. Replace `:81` with `val allFuncKeys = funcKeyCache.value` and `:151` with
     `val allClassKeys = classKeyCache.value`.
  3. The `.toList()` snapshots the key `Collection` into an immutable `List` so the cached value
     is stable across the modification-count window.
- **Rules / edge handling**: `PsiModificationTracker.getInstance(project)` as the dependency
  invalidates on any PSI change (new/removed global or class). `DumbService.isDumb` inside the
  provider returns `emptyList()` during indexing (matches the existing early-out at `:55-57`).
- **Member-snapshot cost — re-verified, PARTLY MOOT**: §2.5.5 also flags "member completion builds
  a full snapshot incl. `checkTypes` per session" (`docs/review.md:265`,
  `LuaCompletionContributor.kt` member provider `:290`). `LuaTypesSnapshot.forFile`
  (`lang/psi/types/LuaTypes.kt:140-151`) **already memoizes** via `PsiFile.getUserData` keyed on
  `text.hashCode() * 31 + target.hashCode()` (`:153-157`); `checkTypes()` runs only on the first
  build for a given file-text (`LuaTypesVisitor.kt:927-938`). Within a session the snapshot is
  built at most once and reused by later invocations on the **same completion copy text**. So the
  "per session" waste is **not** eliminable by adding another cache here — it is one build per
  distinct copy-file text, which the existing UserData cache already bounds. **MAINT-28-05 scopes
  the member-snapshot item to a no-op (documented as already-cached); the actionable half is the
  `getAllKeys` caching above.** No change to the member provider for perf.
- **Cross-feature ordering note (review #21 / MAINT-30)**: the no-op rationale depends on the
  memo's EXISTENCE, not its keying. Review finding #21 (MAINT-30's scope) replaces the
  `FileUserData` text-hash keying with `CachedValuesManager.getCachedValue(file)` — that
  re-keying preserves (indeed strengthens) the memoization, so this no-op conclusion survives
  MAINT-30 regardless of implementation order. If MAINT-30 lands first, re-verify only that the
  member provider still hits the (re-keyed) cache.

### 3.5 #62 — Prefix-gate correction (MAINT-28-03)
- **Root cause**: the expression-keyword gate at `:220-223` computes
  `hasPrefix = prevLeaf != null && prevLeaf.node.elementType == IDENTIFIER` after re-declaring a
  **shadowing** `prevLeaf` local (`:220` shadows the outer `:169`). The dummy identifier
  `IntellijIdeaRulezzz` merges into the token the user is typing, so when a prefix exists the
  caret leaf *is* the identifier and `prevVisibleLeaf(position)` is the token **before** it — this
  cannot see the user's own prefix (`.agents/AGENTS.md` completion-context lesson;
  `docs/review.md:128`). The guard's intent: suppress `nil`/`true`/`false`/`not`/`function`
  (`EXPRESSION_KEYWORDS`, `:53-55`) once the user has typed a prefix, because a partial word should
  not be swamped by keyword literals.
- **Steps**:
  1. Delete the shadowing `val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)` at `:220`.
  2. Compute the prefix from the result set, not PSI:
     `val hasPrefix = result.prefixMatcher.prefix.isNotEmpty()`.
     `CompletionResultSet.getPrefixMatcher().getPrefix()` is the platform's authoritative typed
     prefix (used already at `LuaCrossFileCompletionProvider.kt:45`), immune to the dummy-token
     merge.
  3. Keep the surrounding gate unchanged: `if (!hasPrefix && !isStatementStart) addKeywords(result,
     EXPRESSION_KEYWORDS)`.
- **Rules / edge handling**: empty prefix (caret at a fresh expression start) → keywords offered
  (unchanged intent). Non-empty prefix → keywords suppressed, only symbols offered. `isStatementStart`
  suppression is orthogonal and retained.

### 3.6 #24 — Original-file discipline, per-call-site classification (MAINT-28-01)
The one cross-cutting decision. Below is the exhaustive classification of every
`parameters.*` / `position.containingFile` / `originalFile` use across
`lang/LuaCompletionContributor.kt` + `lang/completion/**`. **Rule**: index queries + proximity
→ `parameters.originalFile` (the real, indexed, on-disk file); type-snapshot `elementNodes`
lookup → the **copy** file that owns the position PSI.

| Call site | Current | Concern | Correct source | Change |
|---|---|---|---|---|
| `LuaCrossFileCompletionProvider.kt:42` | `position.containingFile` (copy) | FileBasedIndex `fileScope` (`extractRequires`) + StubIndex proximity | **originalFile** | **FIX** — copy is never indexed → cross-file phase returns nothing |
| `ProximityCalculator.kt:49-66` (via `GlobalSymbolRankingService` `currentFile` arg) | receives the copy | proximity tiers (same-file 0.9, same-dir 0.7) compare `currentFile.virtualFile.path` | **originalFile** | **FIX** (transitively, once `:42` passes originalFile) — copy `virtualFile` differs from disk → 0.9/0.7 unreachable |
| `LuaCrossFileCompletionProvider.getLocalSymbolNames` (`:82-99`) | the copy | dedup set of the current file's own decls | **originalFile** | **FIX** (transitive) — dedup must match the indexed globals' file |
| `LuaCompletionContributor.kt:290` member provider — `LuaTypesSnapshot.forFile(receiverExpr.containingFile)` | the **copy** (owns `receiverExpr`) | type-snapshot `elementNodes` PSI-identity lookup (`LuaTypes.kt:53`) | **copy (unchanged)** | **KEEP** — TYPE-10 comment `:287-289`; originalFile PSI identities differ, lookup would miss |
| `LuaCompletionContributor.kt:216/241/258` — `addSymbolCompletions(position, …)` | `parameters.position` (copy) | in-file enclosing-scope PSI walk (`:93-129`) | **copy/position (unchanged)** | **KEEP** — local/param/enclosing scope must read the copy's live PSI (the caret's own tree) |
| `LuaCompletionContributor.kt:166/316/337` — `parameters.editor.project` | editor project | project lookup only | **either (unchanged)** | **KEEP** — project identity is copy-independent |
| `LuaCompletionContributor.kt` `<`/`const`/`close` providers (`:307-350`) | `parameters.position`, `parameters.editor` | in-file leaf inspection | **copy (unchanged)** | **KEEP** — pure in-file PSI, no index/proximity |

- **Compatibility note**: #24 (originalFile for index/proximity) and the TYPE-10 comment (copy for
  the type snapshot) are **not** in conflict — they govern **different concerns on different call
  sites**, exactly as tabled. The design draws the line explicitly: only the cross-file provider's
  index/proximity path moves to `originalFile`; the member-snapshot and in-file scope walks stay on
  the copy.
- **Failure handling**: `parameters.originalFile as? LuaFile ?: return` — a non-Lua original file
  yields no cross-file candidates (unchanged fail-soft behavior).

## 4. External Data & Parsing

None. This feature consumes only PSI, the type engine, and stub/file indexes — no CLI output,
no file parsing, no network. (The require-string extraction reads a pre-built
`LuaFileBindingsIndex` record, not raw text.)

## 5. Data Flow

### Example 1: Cross-file completion (#24)
User types `foo<caret>` in `main.lua` which `require("mod")`s a file defining global `foobar`.
`LuaCrossFileCompletionProvider.addCompletions` → reads `parameters.originalFile` (`main.lua` on
disk) → `extractRequires(originalFile)` hits `FileBasedIndex.fileScope(originalFile)` (now an
indexed file) → resolves `mod.lua` → `addSymbolsFromFile` offers `foobar`; then
`processGlobalsWithRanking` ranks project globals with `ProximityCalculator` comparing the real
`main.lua` path. **Before the fix**: `fileScope(copy)` returns nothing → `foobar` never offered.

### Example 2: Global ranking excludes a method receiver (#40)
Project defines `function Account:deposit()`. User types `Acc<caret>`. `getProjectGlobalSymbols`
iterates `allFuncKeys()` (cached), for the `"Account:deposit"` decl calls `extractGlobalFuncName`
→ `funcName.funcNameMethod != null` → `null` → skipped. `Account` is **not** offered as a global
function. (The class-name index still offers `Account` as a `@class` type if declared as one.)

### Example 3: Enter between blocks (#25)
Buffer `if x then<caret>end` on one line, caret after `then`. `preprocessEnter` → `owner` = the
`if` statement, `terminator` = the `END` node. `openerStart` = `if` offset, `terminatorStart` =
`end` offset; caret ∈ `(openerStart+1)..terminatorStart` → `DefaultForceIndent` → the platform
opens an indented blank body line above `end`.

## 6. Edge Cases

- **Copy file with no virtualFile**: `parameters.originalFile.virtualFile` is non-null for a real
  editor file; the `as? LuaFile ?: return` guards a scratch/non-Lua original.
- **`Account.fn` property decl (#40)**: caught by `funcNamePropertyList.isNotEmpty()`, distinct
  from the `:method` case.
- **Empty prefix keyword gate (#62)**: `prefixMatcher.prefix == ""` → keywords still offered.
- **Dumb mode (§2.5.5)**: the `CachedValue` provider returns `emptyList()` and caches it against
  `MODIFICATION_COUNT`; leaving dumb mode bumps the tracker → recompute.
- **Member snapshot on unchanged text**: reused UserData snapshot; `checkTypes` not re-run (§3.4).

## 7. Integration Points

No `plugin.xml` change. All four edited classes are already registered:

```xml
<!-- src/main/resources/META-INF/plugin.xml (existing, verified :361-366) -->
<enterHandlerDelegate implementation="net.internetisalie.lunar.lang.completion.LuaEnterBetweenBlockHandler"/>
<completion.contributor language="Lua"
    implementationClass="net.internetisalie.lunar.lang.LuaCompletionContributor"/>
```

`GlobalSymbolRankingService` is a `@Service(Level.PROJECT)` obtained via `project.getService(...)`
(`GlobalSymbolRankingService.kt:232-233`) — services need no `plugin.xml` entry. Deleting the
IDENTIFIER symbol provider (#39) removes an `extend(...)` call **inside** `LuaCompletionContributor`
— no registration change (the contributor itself stays registered).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-28-01 (original-file discipline) | M | §2.1, §3.6 |
| MAINT-28-02 (single symbol pass) | S | §2.3, §3.3 |
| MAINT-28-03 (ranking accuracy: #40 + #62) | S | §2.2, §2.3, §3.2, §3.5 |
| MAINT-28-04 (enter-between guard) | C | §2.4, §3.1 |
| MAINT-28-05 (session cost) | S | §2.2, §3.4 |

## 9. Alternatives Considered

- **#39 — keep the IDENTIFIER symbol provider, dedupe results instead**: rejected; the scope walk
  runs regardless of dedup, so the cost stays. Deleting the redundant provider removes the work.
- **#40 — parse the stub-key string (`indexOf(':')`)**: rejected; the `LuaFuncName` PSI already
  exposes `funcNameMethod`/`funcNamePropertyList`, a grounded structural check that does not
  depend on the key-format string.
- **§2.5.5 — cache the member snapshot in a service**: rejected; `LuaTypesSnapshot.forFile` already
  memoizes per file-text (§3.4), so a second cache is redundant. Scoped out honestly.
- **#25 — drop the `+1` only** (the review's suggestion): insufficient; `leaf` is the wrong anchor
  (caret-adjacent leaf, not the opener). Anchoring on `owner.textRange.startOffset` is correct.

## 10. Open Questions

_None — feature has cleared the planning bar._
