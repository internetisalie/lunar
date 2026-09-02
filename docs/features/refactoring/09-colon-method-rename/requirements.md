---
id: "REFACT-09"
title: "09: Colon-method rename"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "REFACT/INTENT"
folders:
  - "[[features/refactoring/requirements|requirements]]"
---

# REFACT-09: Colon-method rename

## Overview

Rename a `function Obj:m()` declaration and every `obj:m()` call site that binds to it, **or refuse
with a reason**. This is the half of `REFACT-01-08` that did not ship: the dotted form
(`function M.run()`) renames from both the declaration and a call site since [[BUG-465]], while the
colon form is refused by name in
[LuaRenameProcessor.kt:111](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)
with `refactoring.rename.colonMethod`:

> Renaming a `function Obj:method()` declaration is not supported yet: calls written
> `obj:method()` are not resolved, so they would be left bound to the old name.

That message is exactly right about the mechanism, and `REFACT-09-00-DR-02` measured it directly:

```
SEARCH A t:m       leaf=LeafPsiElement@24[a.lua]  references=0
SEARCH B Obj:m     leaf=LeafPsiElement@22[b1.lua] references=0
SEARCH C Class:b   leaf=LeafPsiElement@54[c.lua]  references=0
SEARCH E t:m       leaf=LeafPsiElement@50[e.lua]  references=0
SEARCH F M:m       leaf=LeafPsiElement@24[mod.lua] references=0
SEARCH G Builder:setName leaf=LeafPsiElement@54[g.lua] references=0
```

**`ReferencesSearch` finds zero call sites for a colon-method declaration in every receiver shape,
the `---@class`-annotated one included.** So this feature is not "lift the guard": the platform's
usage set for a colon method is empty, and the rename must build its own — or decline.

## What [[TYPE-13]] supplies, and what it does not

[[TYPE-13]] made a structurally-resolved `LuaTypeMember` carry `sourceElement`, and
`LuaMemberDeclarations.declarationOf(member)` maps that to a declaration
([LuaMemberDeclarations.kt:46](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaMemberDeclarations.kt)).
`REFACT-09-00-DR-02` re-measured its reach from the *call-site* direction this feature needs — for
each colon call in a fixture, resolve the receiver, `resolveMember`, `declarationOf`:

| fixture | call | `resolveMember` | `declarationOf` |
| :-- | :-- | :-- | :-- |
| `local t = {}` ; `function t:m()` ; `t:m()` ×2 ; `local q = {}` ; `function q:m()` ; `q:m()` | `t:m()` | HIT | `LuaFuncDeclImpl@13` |
| the same fixture | `q:m()` | HIT | `LuaFuncDeclImpl@57` |
| `Obj = {}` ; `function Obj:m()` ; `Obj:m()` (file `b1.lua`) | `Obj:m()` in `b1.lua` | HIT | `LuaFuncDeclImpl@9` |
| the same global, called from a **second file** `b2.lua` | `Obj:m()` in `b2.lua` | HIT | **null** |
| `setmetatable` OO | `o:b()` | HIT | `LuaFuncDeclImpl@39` |
| `setmetatable` OO | `self:b()` | HIT | **null** |
| plain local table with a `self:` call | `self:b()` | HIT | **null** |
| factory-returned table | `o:m()` | HIT | **null** |
| `local M = require('mod')` ; `M:m()` | `M:m()` | HIT | **null** |
| `local u = t` ; `u:m()` | `u:m()` | HIT | **null** |
| `local function use(x) x:m() end` | `x:m()` | HIT | **null** |
| `---@class Builder` ; `local b = Builder` ; `b:setName()` | `b:setName()` | **MISS** | null |

Two consequences fix this feature's shape:

1. **A `null` declaration is common in ordinary code**, not a corner: an alias, a parameter
   receiver, a `self:` call, a required module and a cross-file global all report it. `declarationOf`
   answers *one* question — where is this member declared — and answers it for a minority of sites.
2. **Resolving one site says nothing about the others.** `TYPE-13-00-DR-02`'s question was
   whether a *complete* usage set is computable. It is not computable from `declarationOf` alone,
   because `declarationOf` is null exactly where completeness is at risk. This feature therefore
   decides completeness **syntactically, over the receiver's binding**, and uses `declarationOf`
   only to separate same-named members of different receivers.

## Scope

### In Scope
- Renaming `function R:m()` and every colon call site bound to it, **only** when the receiver `R`
  is a file-local binding whose value provably does not leave the file, and every colon call named
  `m` in that file is decided. `REFACT-09-00-DR-02` defines "decided" and `design.md` §3 specifies it.
- A refusal, naming its reason, for every other shape. The refusal is the `else` branch: a receiver
  occurrence in a position this feature does not enumerate is an **escape**, and an escape refuses.
- The caret-on-`self` guard (`REFACT-09-04`).
- The member-name collision report (`REFACT-09-07`), through the existing
  `LuaRenameCollisionUsageInfo` carrier.

### Out of Scope — each measured, each refused rather than half-applied
- **Global receivers** (`Obj = {}`): call sites in other files report no declaration (DR-02 fixture B).
- **Receivers whose value escapes the file** — a module `return M`, an alias `local u = t`, a
  `setmetatable({}, Class)` argument, a receiver passed to a function.
- **`require`d module receivers** ([[TYPE-13]] Gap 2.11 — `getModuleType` reads `getFileReturnType()`,
  so the module type carries no members).
- **The second segment of a chain** `t:m():m()` ([[TYPE-13]] Gap 2.12 — `visitFuncCall` models
  `nameAndArgsList.firstOrNull()` only, and reports the first segment's type for the whole expression).
- **Dotted access to the same member** (`t.m`, `function t.m()`, `t.m = f`): the same member under
  another spelling, which this rename does not rewrite, so it refuses instead.
- Making un-annotated receivers resolve further — that is the type engine's, not this feature's.
- `self` and `...` as rename targets (`REFACT-01-19`, `Won't`), dynamic `_G["x"]` (`REFACT-01-20`, `Won't`).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| `REFACT-09-01` | **Rename from the declaration caret** | M | Not Implemented | Caret on `m` in `function R:m()` renames the declaration and every colon call site bound to it, when the predicate of `design.md` §3.2 accepts. |
| `REFACT-09-02` | **Rename from a call site** | M | Not Implemented | Caret on `m` in `r:m()` — including `self:m()` — substitutes to the declaration and does the same. |
| `REFACT-09-03` | **Refuse rather than half-rename** | M | Not Implemented | Where the predicate does not accept, the rename is refused with a message naming which clause declined. No partial write is committed and the file is byte-identical. |
| `REFACT-09-04` | **Caret on `self` does not rename the method** | M | Not Implemented | With the caret on `self` inside a method body, the method is not renamed. |
| `REFACT-09-05` | **Caret on the receiver renames the receiver** | M | Not Implemented | Caret on `r` in `r:m()` renames `r`, not `m`. |
| `REFACT-09-06` | **Atomic** | M | Not Implemented | The rename is one undoable write action; a refusal leaves the file byte-identical. |
| `REFACT-09-07` | **The new name is reported when the receiver already has it** | S | Not Implemented | Renaming `R:m` to a name `R` already declares reports a conflict through `LuaRenameCollisionUsageInfo` rather than silently merging the two members. |
| `REFACT-09-08` | **No regression in the shapes that already work** | M | Not Implemented | The dotted form, local/global/label renames and the conflict rules behave exactly as they do at `0bccadae`, except for the refusal-message assertions `implementation-plan.md` Phase 4 updates. |

## Behavior Rules

- **The refusal is the default, not the fallback.** Every clause of the predicate is written as
  *accept only if*; a receiver occurrence in an unenumerated position is an escape and refuses.
  `design.md` §3.3 states the position whitelist as a closure over the `var` grammar's two step
  kinds, following the property-not-shape-list rule [[TYPE-13]] design §3.3 established.
- **Success is never reported for a partial rename.** This is the measured failure mode
  (`REFACT-01-00-DR-03`), and `REFACT-09-00-DR-02` measured its mechanism: an empty
  `ReferencesSearch` result.
- **`refactoring.rename.colonMethod` is deliberately replaced.** After this feature the blanket
  colon refusal is unreachable; the narrower messages of `design.md` §7.2 take its place, each naming
  the clause that declined. The old key and its `LuaBundle` entry are removed with it.

## Test Cases

Every row was executed on the gce builder against a throwaway prototype of `design.md` §2–§4
(`Refact09PrototypeProbe`, reverted; no production or test file carries it). The "Then" column is
transcribed output, and every mutation in the last column was **applied to the prototype and the
fixture re-run**, with the differing outcome quoted. Each fixture is alone in its own test method
with one `configureByText`, except the two rows that name a second file — `LuaTypeManagerImpl`
searches `GlobalSearchScope.allScope(project)`, so a stray sibling binds a member to the wrong file.

| # | Requirement | Given (fixture, caret marked) | When | Then | Mutation that turns it red (executed) |
|---|-------------|-------------------------------|------|------|---------------------------|
| 1 | `REFACT-09-01` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `t:m()` | rename to `n` | all three sites read `n` | delete the `METHOD_FUNCTION` branch of `findReferences` (design §2.2) → **observed** `function t:n() end` with both `t:m()` call sites left behind — `REFACT-01-00-DR-03`'s half-rename verbatim |
| 2 | `REFACT-09-02` | `local t = {}` ; `function t:m() end` ; `t:<caret>m()` ; `t:m()` | rename to `n` | all three sites read `n` | delete `colonCallSiteDeclarationLeaf` from `substituteElementToRename` (design §2.2) → **observed** `REFUSED … Cannot determine which declaration this name refers to`, while row 1's declaration caret still renames |
| 3 | `REFACT-09-02` | `local C = {}` ; `function C:m() end` ; `function C:a() self:<caret>m() end` ; `C:m()` | rename to `n` | all three sites read `n` | as row 2 → **observed** the same refusal on this fixture |
| 4 | `REFACT-09-01` | `local C = {}` ; `function C:<caret>m() end` ; `function C:a() self:m() end` ; `C:m()` | rename to `n` | `function C:n()`, `self:n()`, `C:n()` | drop the `self` half of `receiverOccurrences` (design §3.4) → **observed** `REFUSED … The call 'm' on line 3 cannot be bound to a declaration` |
| 5 | `REFACT-09-01` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local q = {}` ; `function q:m() end` ; `q:m()` | rename to `n` | `t:n()` renamed, `function q:m()` and `q:m()` **untouched** | drop the `decided === declaration` test in design §3.5 → the unrelated receiver's sites join the rename set. The separating measurement is DR-02's: `t:m()` reports `declarationOf=LuaFuncDeclImpl@13`, `q:m()` reports `LuaFuncDeclImpl@57` |
| 6 | `REFACT-09-03` | `Obj = {}` ; `function Obj:<caret>m() end` ; `Obj:m()`, plus `p07b.lua` = `Obj:m()` | rename to `n` | refused, both files byte-identical | drop the `isFileLocal` test in design §3.2 clause R1 → **observed** `RENAMED`, `function Obj:n()` / `Obj:n()` in the caret's file with `p07b.lua`'s `Obj:m()` left behind |
| 7 | `REFACT-09-03` | `local M = {}` ; `function M:<caret>m() end` ; `M:m()` ; `return M` | rename to `n` | refused, file byte-identical, message names the escape: `The receiver's value escapes at 'M' (bare, not a call head)` | drop the bare-occurrence clause of design §3.3 → the escaping module receiver is accepted; the same clause is falsified from its own fixture by row 12 |
| 8 | `REFACT-09-03` | `local Class = {}` ; `Class.__index = Class` ; `function Class:<caret>m() end` ; `local o = setmetatable({}, Class)` ; `o:m()` | rename to `n` | refused, byte-identical, `escapes at 'Class'` | as row 7 |
| 9 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `local u = t` ; `u:m()` | rename to `n` | refused, byte-identical, `escapes at 't'` | as row 7 |
| 10 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `function t:m() end` ; `t:m()` | rename to `n` | refused, byte-identical, `'m' is declared 2 times on this receiver` | delete design §3.5's `memberDeclarations.size != 1` test → **observed** `RENAMED`: the first declaration becomes `t:n()` and the second `function t:m()` is left declaring the old name |
| 11 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `print(t.m)` | rename to `n` | refused, byte-identical, `also accessed as '.m'` | delete design §3.3's `DottedMember` verdict → **observed** `RENAMED` with `print(t.m)` left behind |
| 12 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t().x = 1` ; `t:m()` | rename to `n` | refused, byte-identical, `escapes at 't' (call step in a suffix)` | delete design §3.3's `suffixes.any { it.nameAndArgsList.isNotEmpty() }` clause → **observed** `RENAMED`. The suffix `.x` is named differently from the method deliberately: with a `.m` suffix the `DottedMember` verdict would refuse anyway and the clause would be untestable from that fixture |
| 13 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m():m()` | rename to `n` | refused, byte-identical, `The call 'm' on line 3 cannot be bound to a declaration` | delete design §3.6's `call.nameAndArgsList.firstOrNull() !== nameAndArgs` guard → **observed** `RENAMED`, the chain's second segment rewritten from a receiver type [[TYPE-13]] Gap 2.12 measured to be the *first* segment's |
| 14 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `local function f(x) x:m() end` | rename to `n` | refused, byte-identical, `The call 'm' on line 4 cannot be bound to a declaration` | delete design §3.5's undecided-site loop → the parameter-receiver call, which DR-02 measured as `declarationOf=null`, is silently left behind |
| 15 | `REFACT-09-03` | `t:m()` ; `local t = {}` ; `function t:<caret>m() end` ; `t:m()` | rename to `n` | refused, byte-identical | delete design §3.2's clause R2 (`textOffset < receiverLeaf.textOffset`) → **observed** `RENAMED`, rewriting the line-1 `t:m()`, which is a call on the *global* `t` and not on the local at all |
| 16 | `REFACT-09-03` | `local t = {}` ; `function t:<caret>m() end` ; `t:m()` ; `do local t = {} end` | rename to `n` | refused, byte-identical, `receiver 't' is not a file-local table` | delete design §3.2's `bindings.size != 1` test → a shadowed receiver name is accepted and its occurrences are classified against the wrong binding |
| 17 | `REFACT-09-04` | `local C = {}` ; `function C:m() end` ; `function C:a() se<caret>lf:m() end` ; `C:m()` | rename to `n` **through `myFixture.renameElementAtCaret`**, which passes the fixture editor | refused, byte-identical, `'self' is not the method name` | delete design §3.7's caret guard → **observed** `RENAMED`, producing `function C:n() self:m() end` — the *enclosing* method `a` renamed, because `LuaScopeProcessor` resolves `self` to `funcName.funcNameMethod.nameRef.identifier` ([LuaScopeProcessor.kt:87-92](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)) |
| 18 | `REFACT-09-04` | `local C = {}` ; `function C:<caret>m() end` ; `function C:a() self.m = 1 end` ; `C:m()` | rename to `n` | refused, byte-identical, `also accessed as '.m'` | drop the `self` half of `receiverOccurrences` (design §3.4) → **observed** `RENAMED` with `self.m = 1` left behind. Row 4 and this row are the two halves of that one clause: row 4 is a `self:` call the scan must **find**, this is a `self.` write the scan must **refuse** |
| 19 | `REFACT-09-05` | `local t = {}` ; `function t:m() end` ; `<caret>t:m()` | rename to `renamedTable` | `local renamedTable = {}` and `renamedTable:m()`; `m` untouched | none is needed for `m`: this row asserts an unchanged route, and its falsifier is row 1, which is the route this feature adds. See `risks-and-gaps.md` Gap 2.1 for the pre-existing receiver-segment defect this row also measures |
| 20 | `REFACT-09-06` | row 1's fixture | rename to `n`, then `UndoManager.getInstance(project).undo(editor as? TextEditor)` — the idiom `LuaRenameUndoTest.undoAfterRenameRestoresTheDocument` already uses ([LuaRenameUndoTest.kt:43-49](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/LuaRenameUndoTest.kt)) | the file returns to its original text in one undo | inherited from `LuaRenameProcessor.renameElement`'s single non-cancelable section (REFACT-01 design §3.3); `LuaRenameUndoTest` is the existing gate for the mechanism, and this row extends it to the colon form |
| 21 | `REFACT-09-07` | `local t = {}` ; `function t:<caret>m() end` ; `function t:n() end` ; `t:m()` ; `t:n()` | rename to `n` | a conflict is reported: `This table already has a member named 'n'` | replace design §5's `METHOD_FUNCTION` arm of `LuaRenameConflictDetector.collisions` with the pre-existing global arm → **observed** `A global named 't:n' already exists in this project`, and row 22 then reports a conflict that does not exist |
| 22 | `REFACT-09-08` | `p21a.lua` = `local t = {}` ; `function t:<caret>m() end` ; `t:m()`, and `p21b.lua` = the identical text | rename to `n` | `p21a.lua` renames, `p21b.lua` is **untouched**, and no conflict is reported | as row 21 → **observed** `THREW ConflictsInTestsException: 't:m' is declared in 2 places…` — C4's premise (usages stop resolving) is false for a colon method, whose usages are not collected through `resolve` at all |
| 23 | `REFACT-09-08` | `local M = {}` ; `function M.<caret>run() end` ; `M.run()` | rename to `n` | `function M.n()` and `M.n()` — the dotted form is unchanged by this feature | this row asserts an unchanged route; `LuaRenameTest`'s BUG-465 cases are its gate |
| 24 | `REFACT-09-03` | `p22mod.lua` = `local M = {}` ; `function M:m() end` ; `return M`, and `p22.lua` = `local M = require('p22mod')` ; `M:<caret>m()` | rename to `n` | refused, both files byte-identical | design §3.6's structural route returns null here ([[TYPE-13]] Gap 2.11), so `colonCallSiteDeclarationLeaf` yields nothing and `resolvedDeclarationLeaf` refuses. Falsified by row 2, which is the same code path on a fixture where it must succeed |

## Acceptance Criteria

- [ ] [[TYPE-13]] is `done` — satisfied at `0bccadae`.
- [ ] `REFACT-09-00-DR-02` has run and its result is recorded above and in `risks-and-gaps.md`.
- [ ] Every `M` requirement has an executed test with a named, reachable mutation.
- [ ] The three shapes `REFACT-01-00-DR-03` measured half-renaming are each covered: the **plain
      local table** by a correct rename (row 1), the **global table** by a refusal (row 6), and
      **`setmetatable` OO** by a refusal (row 8).
- [ ] `refactoring.rename.colonMethod` is removed from `LuaBundle.properties` together with its one
      call site, and `design.md` §7.2 records the messages that replace it.
- [ ] The full unit suite is green. Measured against the prototype at `0bccadae`: **2 979 tests, 2
      failures, 0 errors**, the two being `LuaRenameTest.testColonMethodDeclarationIsRefused` and
      `testSelfInsideAMethodIsRefusedAsTheMethod`, which assert the replaced message.
      `implementation-plan.md` Phase 4 rewrites both.
- [ ] `REFACT-01-08` is updated to `Full` only once this ships.

## Non-Functional Requirements

- **Threading.** Every clause of the predicate is a PSI read plus one `LuaTypesSnapshot.forFile`
  per file, and runs where the platform already puts it:
  `substituteElementToRename` on the EDT before the refactoring starts, `findReferences` and
  `findCollisions` inside `BaseRefactoringProcessor`'s background read action. The write path is
  `LuaRenameProcessor.renameElement`, unchanged. No `Project`, `Editor`, `PsiFile` or `VirtualFile`
  is retained; `Plan` holds PSI for the duration of one call.
- **Cost.** The predicate is bounded by the declaring **file**: two `PsiTreeUtil.findChildrenOfType`
  passes over it and at most one `LuaTypesSnapshot.forFile`, which is `CachedValuesManager`-cached
  ([LuaTypes.kt:280-286](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt)).
  No index read and no project-wide scan. It runs once per rename, never on the typing path.

## De-risking

| ID | Question | Blocks | Status |
|----|----------|--------|--------|
| `REFACT-09-00-DR-02` | What does a **complete** usage set mean operationally for a colon method, and can it be computed without a whole-project scan? | the refusal predicate | **done — see "Overview" and `risks-and-gaps.md` DR-02.** Adopted from `TYPE-13-00-DR-02`, which named [[REFACT-09]] as its owner. |

## Dependencies

- **[[TYPE-13]]** supplies `LuaMemberDeclarations.declarationOf`, which is `public` for this feature.
- Extends `LuaRenameProcessor` and `LuaRenameConflictDetector` (REFACT-01). It does **not** extend
  `LuaTargetElementEvaluator.adjustTargetElement`: test case 2's mutation measured the call-site
  caret to be carried by `substituteElementToRename`'s new branch and by nothing else — removing
  that branch refuses the call-site caret while the declaration caret still renames, with
  `adjustTargetElement` untouched throughout. `design.md` §1 records it.
