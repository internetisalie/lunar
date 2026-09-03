---
id: "REFACT-09"
title: "09: Colon-method rename"
type: "feature"
status: "planned"
priority: "medium"
parent_id: "REFACT/INTENT"
folders:
  - "[[features/refactoring/requirements|requirements]]"
---

# REFACT-09: Colon-method rename

## Overview

Rename a `function Obj:m()` declaration and every colon call site bound to it. This is the half of
`REFACT-01-08` that did not ship: the dotted form (`function M.run()`) renames from both the
declaration and a call site since [[BUG-465]], while the colon form is refused by name in
[LuaRenameProcessor.kt:111-112](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)
with `refactoring.rename.colonMethod`:

> Renaming a `function Obj:method()` declaration is not supported yet: calls written
> `obj:method()` are not resolved, so they would be left bound to the old name.

**That sentence is now false.** [[NAV-13]] shipped at `1afdfdca`: a colon call site resolves to its
method declaration's IDENTIFIER leaf, `isReferenceTo` answers true, and `ReferencesSearch` returns
a usage set. `LuaColonCallFindUsagesTest` pins it, and this feature re-measured the consequence for
rename directly — `LuaRenameProcessor.findReferences`, **unchanged**, already returns that set:

```
R09PROBE[F01] kind=METHOD_FUNCTION refs=[34, 40]
```

on `local t = {}` / `function t:m() end` / `t:m()` / `t:m()` (`risks-and-gaps.md` DR-02). Removing
the message's own premise is therefore a requirement of this feature (`REFACT-09-09`), not a side
effect.

### The usage set is real but partial, and that is what this feature is about

[[NAV-13]] resolves a plain local table, an in-file global table, `setmetatable` OO through its
supertype chain, and a `---@class`/`---@type` annotated receiver including its aliased
`local b = Builder` form. It resolves **nothing** for a `require`d module, a chain's second segment,
an alias `local u = t`, a parameter receiver, `self:`, a factory-returned table, `a.b:m()` and
`("s"):m()`.

So a colon-method rename can rewrite a *subset* of its call sites and report success. Executed at
`2a15cfcd` with the blanket refusal lifted and nothing else changed (`risks-and-gaps.md` DR-02);
each of these is a half-applied rename of the [[BUG-457]] class:

| fixture | outcome with the refusal lifted |
| :-- | :-- |
| `local C = {}` / `function C:<caret>m() end` / `function C:a() self:m() end` / `C:m()` | `RENAMED` → `function C:n() end / function C:a() self:m() end / C:n()` — the `self:` call left behind |
| `local t = {}` / `function t:<caret>m() end` / `t:m()` / `print(t.m)` | `RENAMED` → `print(t.m)` left behind |
| `Obj = {}` / `function Obj:<caret>m() end` / `Obj:m()`, and `b.lua` = `Obj:m()` | `RENAMED` in the caret's file; `b.lua` still reads `Obj:m()` |
| `local t = {}` / `function t:<caret>m() end` / `t:m()`, and `b.lua` = `local function f(x) x:m() end` | `RENAMED`; `b.lua` still reads `x:m()` |

**The feature therefore is: rename where the usage set is provably complete, and report every
occurrence that makes it incomplete before anything is written.** `design.md` §3 specifies the
predicate that tells those apart and `risks-and-gaps.md` DR-01 measures what it accepts.

### What is superseded from the previous plan, and by what

The artifacts this replaces were planned to the bar twice on the premise that
`ReferencesSearch` returns **0** for a colon method in every receiver shape. Every conclusion built
on that premise is withdrawn; the measurements are kept where they still hold.

| Previously | Now | Because |
| :-- | :-- | :-- |
| `ReferencesSearch` returns 0 at every scope, so the feature must build its own usage set | `findReferences` returns it unchanged | [[NAV-13]]; DR-02 finding F01 |
| Completeness proved **syntactically**, by a file-local containment argument over the receiver's binding (`planFor` R1-R5, the escape set) | Completeness decided by a **homonym scan** over the usage set the platform now supplies | the syntactic predicate accepts **0 of 941** corpus declarations ([[NAV-13]] requirements; the old `risks-and-gaps.md` Gap 2.3). It is not revived |
| `LuaColonMethodRename.callSiteReferences` supplies `findReferences`' result | no `findReferences` change | F01 |
| `declarationLeafOfCallSite` / `selfRouteDeclaration` carry the call-site caret | no substitution branch; `resolvedDeclarationLeaf` already reaches the declaration through NAV-13's resolve | DR-02 R02 renames from a call-site caret with no new substitution code |
| the module `return M` shape escapes and is refused | accepted when no undecided homonym exists | DR-03 control `c12` = `accepted`; containment is no longer the evidence |
| the `---@field` spelling is out of scope and left on the old name | unchanged, and now also **measured unreachable** by a `PsiComment` scan | `LuaCatsLazyCommentImpl` extends `LazyParseablePsiElement`, not `PsiComment`; DR-03 control `c09` |
| the caret-on-`self` guard (`caret.text != leaf.text`) | **kept verbatim** | DR-02 R04: without it, `se<caret>lf:m()` renames the *enclosing* method — executed |
| the `METHOD_FUNCTION` arm of `LuaRenameConflictDetector` | **kept**, with `design.md` §3.7's `receiverAlreadyHasNewName` re-specified over the receiver's type as seen from a bound call site | DR-02 R11/R12: C3 and C4 both fire today, C4 on a fixture whose rename is correct; DR-05 measures the replacement |

## Scope

### In Scope
- Renaming `function R:m()` and every call site [[NAV-13]] binds to it, from the declaration caret
  and from a resolving call site's caret.
- A **completeness report** before any write: every occurrence of the method's name, in a member
  position anywhere in the refactoring scope, that is neither in the usage set nor provably a
  different member, is raised as a conflict naming its file, line and spelling.
- A refusal, on the EDT, for the decisions that are O(1): the caret is not on the method name
  (`REFACT-09-04`), and the declaration is not in this project (`REFACT-09-05`).
- The member-name collision report (`REFACT-09-08`), through the existing
  `LuaRenameCollisionUsageInfo` carrier.
- Replacing `refactoring.rename.colonMethod`, whose text [[NAV-13]] falsified.

### Out of Scope
- **A dynamically indexed member** — `t[k]`, `t[a .. b]`, `_G["x"]`. Undecidable, and refusing on it
  refuses everything: the pinned corpus carries **3 088** such index steps in ZeroBrane alone and
  **5 424** across the pinned checkouts (`risks-and-gaps.md` DR-01). Same class as `REFACT-01-20`
  (`Won't`). `risks-and-gaps.md` Gap 2.2 states the residual; DR-03 control `c13` pins that such a
  step does **not** block a rename.
- **`---@field m` naming the same member.** The rename proceeds and leaves the annotation on the old
  name. `risks-and-gaps.md` Gap 2.3 records why the scan cannot see it and what would be needed.
- **Rewriting the dotted spelling** (`t.m`, `function t.m()`, `t.m = f`) alongside the colon one.
  These name the same member; this feature *reports* them and does not rewrite them, because they
  resolve through `getQualifiedName` / `LuaGlobalDeclarationIndex` rather than through the type
  engine — a different mechanism, not a wider version of this one.
- **Rewriting a table-constructor key** (`{ m = 1 }`, `{ ["m"] = 1 }`). Same disposition and same
  reason as the dotted spelling: the key declares the member and this feature reports it. It is a
  member spelling `design.md` §3.3's grammar closure now enumerates, and rows 23, 24 and 26 pin
  what is and is not an occurrence.
- **A redefinition of the same method on the same receiver** — a second `function t:m()`. The scan
  deliberately excludes `LuaFuncNameMethod` so that a *different* receiver's `function q:m()` and an
  identical shape in another file are not reported (rows 3 and 12), and the price is that a
  redefinition is neither rewritten nor reported. Measured: the call site binds to the **first**
  declaration (`risks-and-gaps.md` DR-06), so renaming it rewrites the call and leaves the second
  definition on the old name. Rare on real code — 3 same-file, same-receiver-text redefinitions in
  luacheck and none in luarocks, penlight, zerobrane or the substitute (`risks-and-gaps.md` Gap 2.10).
  Row 27 pins the behaviour.
- **Making un-annotated or aliased receivers resolve further** — [[TYPE-13]] Gaps 2.7, 2.11, 2.12
  and [[NAV-13]]'s Out of Scope. Each such call site is reported, not resolved.
- **Renaming a method declared outside the project**, e.g. `File:write` in the plugin's own
  `runtime/standard/lua-5.4/io.lua`. Measured reachable: a colon call resolves there
  (`risks-and-gaps.md` DR-04).
- `self` and `...` as rename targets (`REFACT-01-19`, `Won't`), dynamic `_G["x"]`
  (`REFACT-01-20`, `Won't`).
- In-place (inline) rename for a colon method. Both gates require
  `LuaDeclarationSite.kindOf(...)?.isFileLocal == true` and `METHOD_FUNCTION.isFileLocal` is `false`
  ([LuaDeclarationSite.kt:27](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)),
  so a colon method always takes the dialog path.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| `REFACT-09-01` | **Rename from the declaration caret** | M | Not Implemented | Caret on `m` in `function R:m()` renames the declaration and every call site `ReferencesSearch` returns for it. |
| `REFACT-09-02` | **Rename from a call site** | M | Not Implemented | Caret on `m` in `r:m()`, where that site resolves, renames the same set. |
| `REFACT-09-03` | **Report every occurrence that makes the rename incomplete** | M | Not Implemented | Before any write, every member-position occurrence of the method's name in the refactoring scope that is neither in the usage set nor resolves to a different declaration is raised as a conflict naming its spelling and location. `design.md` §3.3 specifies the occurrence set and §3.4 the verdict. |
| `REFACT-09-04` | **Caret on `self` does not rename the method** | M | Not Implemented | With the caret on `self` inside a method body, the rename is refused and the method is not renamed. |
| `REFACT-09-05` | **A declaration outside the project is refused** | M | Not Implemented | A colon call that resolves into a library — the plugin's bundled stdlib stubs included — refuses with a message naming the file, instead of attempting to rewrite it. |
| `REFACT-09-06` | **Caret on the receiver renames the receiver** | M | Not Implemented | Caret on `r` in `r:m()` renames `r`, not `m`. |
| `REFACT-09-07` | **Atomic** | M | Not Implemented | The rename is one undoable write action; a refusal or a cancelled conflict leaves every file byte-identical. |
| `REFACT-09-08` | **The new name is reported when the receiver already has it** | S | Not Implemented | Renaming `R:m` to a name `R`'s type already resolves as a member reports a conflict, rather than silently merging the two members. |
| `REFACT-09-09` | **The replaced refusal's text is removed** | M | Not Implemented | `refactoring.rename.colonMethod` and its one call site are deleted; no shipped string claims colon calls are unresolved. |
| `REFACT-09-10` | **No regression in the shapes that already work** | M | Not Implemented | The dotted form, local/global/label renames, Safe Delete and the REFACT-01 conflict rules behave exactly as at `2a15cfcd`, except for the assertions `implementation-plan.md` Phase 4 rewrites. |

## Behavior Rules

- **The usage set comes from the platform, not from this feature.** `findReferences` is unchanged;
  `findCollisions` consumes the very list `processUsages` just filled
  (`RenameUtil.java:97-103`), so the completeness scan compares against the same usages that are
  about to be rewritten and never runs a second `ReferencesSearch`.
- **Undecided is the default.** The occurrence scan is written *decided only if*: a member-position
  occurrence is dismissed only when it is in the usage set or resolves to a declaration other than
  the one being renamed. An occurrence in an un-enumerated position is not silently dropped —
  `design.md` §3.3 closes the occurrence set over the grammar by classifying **every** `lua.bnf` rule
  that `grep -n 'nameRef\|IDENTIFIER' lua.bnf` returns, and the one position that is a member
  spelling and is still not an occurrence — `funcNameMethod` — is named there with its cost and
  pinned by row 25.
- **Incompleteness is reported, not refused, and the reason is measured.** Deciding it costs a
  word-index scan plus a resolve per candidate occurrence: **p50 23 ms, p99 525 ms, max 3 163 ms**
  on ZeroBrane and **max 9 957 ms** on the annotated substitute (`risks-and-gaps.md` DR-03). That
  cannot run on the EDT, where `substituteElementToRename` lives, and the platform's only channel
  for aborting after background analysis is the conflicts dialog
  (`RenameProcessor.java:166-188` → `BaseRefactoringProcessor.showConflicts`). This is the same
  disposition, for the same stated reason, that `LuaRenameConflictDetector`'s C4 rule already takes
  ([:215-226](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt)).
  `risks-and-gaps.md` Risk 1.1 carries the residual.
- **Success is never reported silently for a partial rename.** Every occurrence that would be left
  behind is listed, by file and line, in a dialog the user must answer.
- **A colon member name is a table key, never a variable**, so the conflict rules that reason about
  lexical binding are not applicable to this kind. `design.md` §5 states each inherited rule's
  premise and why it is false here.

## Test Cases

Rows marked **(executed)** transcribe output from the de-risking probes recorded in
`risks-and-gaps.md`, run at `2a15cfcd` on the gce builder and reverted (`git status --porcelain`
empty in the working tree and on the builder). Rows without that marker specify behaviour the
implementation phase must execute, and `implementation-plan.md` makes each named mutation a
verification task.

**One `configureByText` per test method, except rows whose Given names a second file.**
`LuaTypeManagerImpl` searches `GlobalSearchScope.allScope(project)`
([LuaTypeManagerImpl.kt:231](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt)),
so a stray sibling binds a member to the wrong file.

| # | Requirement | Given (fixture, caret marked) | When | Then | Mutation that turns it red |
|---|-------------|-------------------------------|------|------|---------------------------|
| 1 | `REFACT-09-01` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `t:m()` | rename to `n` | all three sites read `n` | **(executed)** restore the `METHOD_FUNCTION → refuse` clause of `substituteElementToRename` → the rename is refused and the file is byte-identical. The positive outcome is DR-02 `R09PROBE[R01] RENAMED \| local t = {} / function t:n() end / t:n() / t:n()` |
| 2 | `REFACT-09-02` | `local t = {}` ; `function t:m() end` ; `t:<caret>m()` ; `t:m()` | rename to `n` | all three sites read `n` | **(executed)** as row 1 → `LuaColonCallRenameRefusalTest.renamingAColonCallSiteIsRefusedInsteadOfRetargetingASameNamedLocal` is the existing gate for the refusal this replaces; the positive outcome is `R09PROBE[R02] RENAMED \| local t = {} / function t:n() end / t:n() / t:n()` |
| 3 | `REFACT-09-01` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local q = {}` ; `function q:m() end` ; `q:m()` | rename to `n` | `t`'s two sites read `n`; `function q:m()` and `q:m()` are **untouched**, and no conflict is reported | **(executed)** dismiss an occurrence only when it is in the usage set, dropping design §3.4's *resolves-elsewhere* clause → `q:m()` becomes an undecided occurrence and the rename reports a conflict that does not exist. Positive outcome: `R09PROBE[R06] RENAMED \| … function t:n() end / t:n() / local q = {} / function q:m() end / q:m()`; predicate verdict `R09PRED[c04] verdict=accepted` |
| 4 | `REFACT-09-01` | `---@class Builder` ; `local Builder = {}` ; `function Builder:<caret>setName(x) end` ; `local b = Builder` ; `b:setName("x")` | rename to `withName` | both sites read `withName` | **(executed)** as row 1. Positive outcome `R09PROBE[R07] RENAMED \| … function Builder:withName(x) end / local b = Builder / b:withName("x")`; predicate `R09PRED[c11] verdict=accepted` |
| 5 | `REFACT-09-01`, `REFACT-09-07` | `local t = {}` ; `function t:<caret>m() end` | rename to `n` | `function t:n() end`; no conflict | **(executed)** predicate `R09PRED[c10] verdict=acceptedNoCallSites`; rename `R09PROBE[R14] RENAMED`. Falsifier: make design §3.4 refuse an empty usage set → this row reports a conflict while row 1 stays green |
| 6 | `REFACT-09-03` | `local C = {}` ; `function C:<caret>m() end` ; `function C:a() self:m() end` ; `C:m()` | rename to `n` | a conflict is reported for the `self:m()` occurrence on line 3; no file is written unless it is acknowledged | **(executed)** delete design §3.3's `LuaMethodExpr` occurrence row → `R09PROBE[R05] RENAMED \| local C = {} / function C:n() end / function C:a() self:m() end / C:n()`, the half-rename. With the row present, `R09PRED[c02] verdict=undecidedColonCall` |
| 7 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `print(t.m)` | rename to `n` | a conflict is reported for the `.m` occurrence on line 4 | **(executed)** delete design §3.3's `LuaIndexExpr` row → `R09PROBE[R08] RENAMED \| … function t:n() end / t:n() / print(t.m)`. With the row present, `R09PRED[c03] verdict=dottedSpelling` |
| 8 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `function t.m() end` ; `t:m()` | rename to `n` | a conflict is reported for the `function t.m()` declaration | **(executed)** delete design §3.3's `LuaFuncNameProperty` row → the dotted declaration is not seen and the rename completes silently. With the row present, `R09PRED[c14] verdict=dottedSpelling`. Row 7 does **not** reach this clause: `t.m` in an expression is a `LuaIndexExpr`, `function t.m()` is a `LuaFuncNameProperty`, and `funcNameProperty`/`indexExpr` are different rules ([lua.bnf:165, :301](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) |
| 9 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `print(t["m"])` | rename to `n` | a conflict is reported for the bracket occurrence | **(executed)** delete design §3.3's bracket row → the rename completes and leaves `t["m"]` on the old name. With the row present, `R09PRED[c08] verdict=bracketSpelling` |
| 10 | `REFACT-09-03` | `Obj = {}` ; `function Obj:<caret>m() end` ; `Obj:m()`, plus `b.lua` = `Obj:m()` | rename to `n` | a conflict is reported for `b.lua`'s occurrence; declining leaves both files byte-identical | **(executed)** scan only the declaring file instead of the refactoring scope (design §3.3 step 1) → `R09PROBE[R10] RENAMED` in the caret's file with `b.lua` still `Obj:m()`. With the project scan, `R09PRED[c05] verdict=undecidedColonCall` |
| 11 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()`, plus `b.lua` = `local function f(x) x:m() end` ; `f(nil)` | rename to `n` | a conflict is reported for `b.lua`'s `x:m()` | **(executed)** as row 10 → `R09PROBE[R09] RENAMED`, `b.lua` still `local function f(x) x:m() end`. With the project scan, `R09PRED[c06] verdict=undecidedColonCall` |
| 12 | `REFACT-09-10` | `a.lua` and `b.lua` each = `local t = {}` ; `function t:m() end` ; `t:m()`, caret in `a.lua` | rename to `n` | `a.lua` renames, `b.lua` is **untouched**, and **no** conflict is reported | **(executed)** keep `ambiguousGlobal` (C4) in the rule set for this kind → `R09PROBE[R11] THREW ConflictsInTestsException: 't:m' is declared in 2 places; while more than one declaration exists its usages do not resolve, so they will not be rewritten` — a conflict whose premise [[NAV-13]] falsified. Predicate: `R09PRED[c07] verdict=accepted` |
| 13 | `REFACT-09-04` | `local C = {}` ; `function C:m() end` ; `function C:a() se<caret>lf:m() end` ; `C:m()` | rename to `n` **through `myFixture.renameElementAtCaret`**, which passes the fixture editor (`CodeInsightTestFixtureImpl.java:1104`) | refused, file byte-identical, message names `self` | **(executed)** delete design §3.6's caret guard → `R09PROBE[R04] RENAMED \| local C = {} / function C:m() end / function C:n() self:m() end / C:m()` — the **enclosing** method renamed, because `LuaScopeProcessor` resolves `self` to `funcName.funcNameMethod.nameRef.identifier` ([LuaScopeProcessor.kt:87-92](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)) |
| 14 | `REFACT-09-05` | `local f = io.open("x")` ; `f:<caret>write("y")` | rename to `emit` | refused, file byte-identical, message names `io.lua` | **(executed)** delete design §3.6's out-of-project refusal → the substitution returns a leaf inside `lunar-<version>.jar!/runtime/standard/lua-5.4/io.lua`, measured `R09PRED[j01] resolved=write … writable=false`, and `myFixture.renameElementAtCaret` fails with `AssertionError: element not found in file` rather than refusing. The discriminator is `GlobalSearchScope.projectScope(project).contains(virtualFile)`, executed: `R09SCOPE projectScopeContainsStub=false projectScopeContainsOwnFile=true` |
| 15 | `REFACT-09-06` | `local t = {}` ; `function t:m() end` ; `<caret>t:m()` | rename to `renamedTable` | `local renamedTable = {}` and `renamedTable:m()`; `m` untouched | broaden design §3.6's `kind == METHOD_FUNCTION` guard to every kind → the `LOCAL_VARIABLE` rename is routed through the colon path, whose occurrence scan is keyed on the *method* name and reports the receiver's sites as undecided. `LuaColonCallUsageWithdrawalTest` (NAV-13 case 26b) is the existing gate for the receiver half; see `risks-and-gaps.md` Gap 2.1 and [[BUG-476]] for the pre-existing receiver-segment defect this row sits beside |
| 16 | `REFACT-09-07` | row 1's fixture | rename to `n`, then `UndoManager.getInstance(project).undo(editor as? TextEditor)` — the idiom `LuaRenameUndoTest.undoAfterRenameRestoresTheDocument` already uses ([LuaRenameUndoTest.kt:43-49](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/LuaRenameUndoTest.kt)) | the file returns to its original text in one undo | inherited from `LuaRenameProcessor.renameElement`'s single non-cancelable section (REFACT-01 design §3.3); `LuaRenameUndoTest` is the existing gate for the mechanism and this row extends it to the colon form |
| 17 | `REFACT-09-08` | `local t = {}` ; `function t:<caret>m() end` ; `function t:n() end` ; `t:m()` ; `t:n()` | rename to `n` | a conflict is reported: the receiver already has a member named `n` | **(executed)** `R09F[local] MECHANISM receiverAlreadyHasNewName=true` on this fixture, and `R09F[localNegative] … =false` on the same fixture without `function t:n()` (`risks-and-gaps.md` DR-05). Mutation: drop design §3.7's union-arm loop → row 17a stays green and **row 17b reddens**, because an annotated receiver types as `{ … } \| Builder` whose anonymous arm has no `withName` (`R09E[annotated] plain=false unionAware=true`). Second mutation: key §3.7 on `funcName.nameRef` instead of the usage's receiver → **every** row 17x reddens, `R09C[…] M1declSideValueType type='unknown'` |
| 17a | `REFACT-09-08` | `Obj = {}` ; `function Obj:<caret>m() end` ; `function Obj:n() end` ; `Obj:m()` | rename to `n` | the same conflict is reported for a global receiver | **(executed)** `R09F[global] … =true`; and the rule must **not** be `globalNameTaken`: row 12's fixture is the falsifier for that arm |
| 17b | `REFACT-09-08` | `---@class Builder` ; `local Builder = {}` ; `function Builder:<caret>setName(x) end` ; `function Builder:withName(x) end` ; `Builder:setName("x")` | rename to `withName` | the conflict is reported for an annotated receiver | **(executed)** `R09F[annotated] … =true`, against `R09F[annotatedNegative] … =false` on the same fixture without `function Builder:withName` |
| 17c | `REFACT-09-08` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local u = { n = 1 }` | rename to `n` | **no** conflict — another table's member is not this receiver's | **(executed)** `R09F[fieldKeyOther] … =false`, against `R09F[fieldKey] … =true` on `local t = { n = 1 }` ; `function t:m() end` ; `t:m()`. Mutation: ask the *file* for a member named `n` instead of the usage's receiver type → this row reddens while 17 stays green |
| 17d | `REFACT-09-08` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `do local t = {} ; function t:n() end ; t:n() end` | rename to `n` | **no** conflict — the shadowing `t` is a different receiver | **(executed)** `R09F[shadowed] … =false`. This row is what a receiver-*text* rule (the removed `globalNameTaken`) gets wrong |
| 18 | `REFACT-09-10` | `local n = 1` ; `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `print(n)` | rename to `n` | the rename applies; **no** capture conflict is reported | let `captures` (C1) run for `METHOD_FUNCTION` → `visibleDeclarationOf("n", <the t:m() site>)` finds the visible `local n` and reports a capture that cannot happen, because a member name is not a lexical binding |
| 19 | `REFACT-09-10` | `local M = {}` ; `function M.<caret>run() end` ; `M.run()` | rename to `n` | `function M.n()` and `M.n()` — the dotted form is unchanged by this feature | this row asserts an unchanged route; `LuaRenameTest`'s BUG-465 cases are its gate |
| 20 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local k = 'm'` ; `print(t[k])` | rename to `n` | the rename applies with **no** conflict — a dynamically indexed member is not decided and does not block | **(executed)** `R09PRED[c13] verdict=accepted`. This row is the **cost** of the Out-of-Scope decision rather than its benefit, pinned so the residual is visible rather than discovered; `risks-and-gaps.md` Gap 2.2. Falsifier in the other direction: make design §3.3 treat any bracket step as an occurrence → this row reports a conflict while row 1 stays green |
| 21 | `REFACT-09-03` | `local M = {}` ; `function M:<caret>m() end` ; `M:m()` ; `return M` | rename to `n` | the rename applies with no conflict — nothing in the project names `m` undecidedly | **(executed)** `R09PRED[c12] verdict=accepted`. The row exists because the superseded plan **refused** this shape as an escaping receiver; it is accepted now, and `risks-and-gaps.md` Gap 2.4 states the residual (a consumer outside the project) |
| 22 | `REFACT-09-09` | — | `LuaBundle.getMessage("refactoring.rename.colonMethod")` | the key is absent | delete only the call site and keep the key → the key survives with no renderer, and `LuaRenameTest`'s replaced assertions still pass against a string no code produces |
| 23 | `REFACT-09-03` | `local t = { m = 1 }` ; `function t:<caret>m() end` ; `t:m()` | rename to `n` | a conflict is reported for the constructor key on line 1 | **(executed)** with design §3.3's field row: `R09B[fieldKeyOnSameTable] usages=1 OLD=[] NEW=[FIELD@12(inUsages=false)]`. **Mutation** — delete `fieldOccurrences` from `undecidedIn` → this row reports nothing and the rename completes silently, which is the [[BUG-457]] class arriving on the receiver's own table |
| 24 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local u = { m = 1 }` | rename to `n` | a conflict is reported for `u`'s key | **(executed)** `R09B[fieldKeyOtherTable] OLD=[] NEW=[FIELD@50…]`. The scan does not ask whose table it is — the same rule that reports `print(u.m)` in row 7's shape (`R09B[dottedUnrelatedReceiver] OLD=[DOTTED@59] NEW=[DOTTED@59]`), applied to the spelling row 21 introduces. **Mutation**: as row 23 |
| 25 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local u = { ["m"] = 1 }` | rename to `n` | a conflict is reported for the bracketed key | **(executed)** `R09B[bracketKeyInConstructor] OLD=[] NEW=[FIELD@50…]`. **Mutation** — make `fieldKeyName` read only `field.identifier` → this row reports nothing while rows 23 and 24 stay green |
| 26 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local m = 1` ; `local u = { m }` , and separately `local u = { mm = 1, [k] = 2, 3 }` | rename to `n` | **no** conflict for either — a positional value, a computed key and a different name are not member spellings | **(executed)** `R09B[controlPositionalValue] OLD=[] NEW=[]` and `R09B[controlOtherFieldName] OLD=[] NEW=[]`. **Mutation** — treat every `LuaField` as an occurrence → these rows report a conflict while row 1 stays green. This is the row that stops the field clause from becoming the bracket clause's mistake |
| 27 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `function t:m() end` ; `t:m()` | rename to `n` | the first declaration and `t:m()` read `n`; the **second** `function t:m()` is untouched and **no** conflict is reported | **(executed)** `R09H[localRedef] decl#0@24 usages=[LuaNameRefImpl@53]`, `decl#1@43 usages=[]`, `callSite@53 resolvesTo=24` (`risks-and-gaps.md` DR-06). This row is the **cost** of excluding `LuaFuncNameMethod` from the occurrence set — the exclusion rows 3 and 12 require — pinned so the residual is visible rather than discovered. Falsifier in the other direction: add a `LuaFuncNameMethod` branch to design §3.3's `when` → this row reports a conflict, **and rows 3 and 12 redden with it** |
| 28 | `REFACT-09-03` | `undecidedOccurrences` driven directly with a usage set containing a colon occurrence that `LuaColonCallResolution.declarationLeafOf` does **not** bind — the `self:m()` occurrence of row 6's fixture, passed in by the test | call `undecidedOccurrences(target, usages)` | that occurrence is **not** reported | **mutation** — delete design §3.4's `if (nameRef in usages) return null` → this row reports it. The clause has no *fixture-level* falsifier: over 14 116 corpus and 2 446 substitute colon call sites, no occurrence was ever in the usage set while failing to resolve (`R09R[<tree>] clauseAliveDecls project=0 all=0`), so the falsifier is the synthetic usage set this row constructs |
| 29 | `REFACT-09-08` | `local t = {}` ; `function t:<caret>m() end` ; `function t:n() end` | rename to `n` | the rename applies with **no** conflict — with no bound call site there is no receiver handle | **(executed)** `R09F[noCallSites] usages=[] MECHANISM receiverAlreadyHasNewName=false`. As row 20, this row is the **cost** of the mechanism rather than its benefit; `risks-and-gaps.md` Gap 2.8 states it and names what would close it. Falsifier in the other direction: fall back to the first `local <receiver>` in the file → row 17d reddens, because the fallback picks the wrong `t` |

## Acceptance Criteria

- [ ] [[NAV-13]] is `done` — satisfied at `1afdfdca`.
- [ ] Every `REFACT-09-00-DR-*` action listed under De-risking has run and its result is recorded in
      `risks-and-gaps.md`.
- [ ] Every `M` requirement has a test with a named, reachable mutation, and every mutation has been
      executed against the shipped code.
- [ ] Every half-rename the Overview transcribes is covered by a conflict-reporting row: the
      `self:` call by row 6, the dotted spelling by row 7, the cross-file global by row 10 and the
      parameter receiver in another file by row 11.
- [ ] Every value of `LuaColonMethodRename.Spelling` has a reporting row (`COLON_CALL` row 6,
      `DOTTED` rows 7 and 8, `BRACKET` row 9, `FIELD_KEY` rows 23-25) **and** a non-reporting control
      that the same clause must not fire on (rows 3, 19, 20 and 26).
- [ ] Every member spelling `design.md` §3.3's `lua.bnf` table marks "yes" is either an occurrence
      with a row, or is named Out of Scope with a row pinning what it costs.
- [ ] `refactoring.rename.colonMethod` is removed from `LuaBundle.properties` together with its one
      call site, and `design.md` §7.2 records the keys that replace it.
- [ ] `LuaRenameTest.testColonMethodDeclarationIsRefused` and
      `testSelfInsideAMethodIsRefusedAsTheMethod` are rewritten by `implementation-plan.md` Phase 4;
      both assert the fragment `function Obj:method()`, which no longer exists.
- [ ] The full unit suite is green, run as `test --rerun --no-build-cache`.
- [ ] The corpus lane is green: `test -PwithCorpus --rerun --no-build-cache` reports no
      `Corpus regression:` line.
- [ ] `REFACT-01-08` is updated to `Full` only once this ships.

## Non-Functional Requirements

- **Threading.** `substituteElementToRename` runs on the EDT and performs only O(1) reads there:
  `PsiUtilBase.getElementAtCaret`, a `LuaDeclarationSite` classification and one
  `GlobalSearchScope.contains`. The completeness scan runs in `findCollisions`, inside
  `BaseRefactoringProcessor`'s background read action — never the EDT, which is the constraint
  `LuaRenameConflictDetector`'s own KDoc states
  ([:76-77](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt)).
  The write path (`renameElement`) is unchanged. No `Project`, `Editor`, `PsiFile` or `VirtualFile`
  is retained.
- **Cancellation.** The scan is the most expensive rule in `findCollisions` and obeys that object's
  standing invariant: `ProgressManager.checkCanceled()` is the first statement of every iteration
  block — per candidate file and per occurrence — because both loops resolve.
- **Cost.** One `CacheManager.getFilesWithWord` read plus, per candidate occurrence, one
  `LuaColonCallResolution.declarationLeafOf`, plus — for `REFACT-09-08` — one
  `LuaTypesSnapshot.forFile` and one member lookup per usage, short-circuited on the first hit. No second `ReferencesSearch`: the usage set arrives as
  `findCollisions`' `result` argument. Measured per rename (`risks-and-gaps.md` DR-03): p50 23 ms,
  p90 135 ms, p99 525 ms on ZeroBrane (325 files, 572 declarations); p50 14 ms, p99 1 466 ms,
  max 9 957 ms on the annotated substitute (195 files). It runs once per rename, never on the
  typing path.

## De-risking

| ID | Question | Blocks | Status |
|----|----------|--------|--------|
| `REFACT-09-00-DR-01` | With [[NAV-13]] shipped, how many colon-method declarations can be renamed **completely**, on the pinned corpus and on the annotated substitute, and what blocks the rest? | the whole feature | **done — `risks-and-gaps.md` DR-01** |
| `REFACT-09-00-DR-02` | What does the *unchanged* rename machinery do for a colon method once the blanket refusal is lifted — which hooks already work, and which produce a half-rename or a false conflict? | design §2, §5 | **done — `risks-and-gaps.md` DR-02** |
| `REFACT-09-00-DR-03` | Does the completeness predicate decide the fixtures in both directions, and what does it cost? | the report-don't-refuse decision; design §3, §6 | **done — `risks-and-gaps.md` DR-03** |
| `REFACT-09-00-DR-04` | Can a colon call resolve to a declaration this project must not rewrite? | `REFACT-09-05`, design §3.6 | **done — `risks-and-gaps.md` DR-04** |
| `REFACT-09-00-DR-05` | Which handle answers "does this receiver already have a member called *n*", and in which receiver shapes does it answer at all? | `REFACT-09-08`, design §3.7, §9 Alternative F | **done — `risks-and-gaps.md` DR-05** |
| `REFACT-09-00-DR-06` | What does the machinery do with a second `function t:m()` on the same receiver — which declaration do its call sites bind to? | the `funcNameMethod` exclusion, design §3.3, §6; row 27 | **done — `risks-and-gaps.md` DR-06** |

## Dependencies

- **[[NAV-13]]** supplies the usage set (`LuaColonCallResolution`, `LuaNameReference.multiResolve`'s
  colon branch) and `LuaColonCallResolution.declarationLeafOf`, which the occurrence scan calls
  directly.
- **[[TYPE-13]]** supplies `LuaMemberDeclarations.declarationOf`, used through [[NAV-13]].
- Extends `LuaRenameProcessor` and `LuaRenameConflictDetector` (REFACT-01). It does **not** touch
  `LuaTargetElementEvaluator`, `LuaInplaceRenameHandler`, `LuaNameReferenceSearcher` or
  `LuaRenameProcessor.findReferences`.
