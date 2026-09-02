---
id: "REFACT-09-RISKS"
title: "09: Risks & Gaps"
type: "risk"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# REFACT-09: Risks and Gaps

## Critical Risks

### Risk 1.1 — The predicate accepts a shape whose call sites it cannot see [Medium]

- **Impact**: `REFACT-01-00-DR-03`'s measured failure, verbatim — the declaration renamed, call
  sites left bound to the old name, success reported. This is the only failure mode that matters;
  everything else in this feature is a refusal.
- **Why it is not Low**: `upSet` reach is not a characterised property ([[TYPE-13]] requirements,
  DR-05a), so the type engine cannot be reasoned about — only enumerated. The predicate therefore
  does **not** rest on the engine for containment (`design.md` §3.1 layer (a)); it rests on a
  syntactic whitelist over the receiver binding's occurrences, which is closed over the `var`
  grammar's two step kinds.
- **Mitigation**:
  1. Every clause is written *accept only if*, so an unenumerated occurrence position falls to
     `Verdict.Escape` (design §3.3 property (E)).
  2. Every clause has an executed mutation, and each mutant was measured to flip exactly its own
     fixture — see `requirements.md` "Test Cases". The mutants that produce a half-rename rather than
     a refusal — dropping the `findReferences` arm, and dropping the `self` occurrence half — were
     each observed doing so on their own fixture.
  3. The whitelist is stated as a property over the grammar rather than as a shape list, so a Lua
     form nobody has written down is already refused rather than newly unhandled.
- **Residual**: a *decidable-looking* site whose structural declaration is wrong rather than absent.
  [[TYPE-13]] Gap 2.12 is the one known instance and design §3.6 property 1 converts it to a null.

### Risk 1.2 — Replacing `refactoring.rename.colonMethod` silently drops a refusal [Low]

- **Impact**: a rename that used to refuse now proceeds because a branch was rewired rather than
  re-decided.
- **Mitigation**: the old key is deleted from `LuaBundle.properties` in the same commit as its call
  site (`implementation-plan.md` Phase 3), so a surviving reference is a compile-or-`MissingResourceException`
  failure rather than a silent gap. Phase 4 rewrites the two tests that assert the old message
  rather than deleting them, keeping both their assertions.

### Risk 1.3 — The predicate runs on the EDT [Low]

- **Impact**: a rename-action invocation stalls the UI.
- **Mitigation**: `substituteElementToRename` is the only EDT caller, and its work is two
  `PsiTreeUtil.findChildrenOfType` passes over **one file** plus one
  `LuaTypesSnapshot.forFile`, which is `CachedValuesManager`-cached
  ([LuaTypes.kt:280-286](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt))
  and, on the rename path, already warm from the daemon. No index read, no VFS, no project-wide
  scan. It runs once per <kbd>Shift+F6</kbd>, not per keystroke: `isAvailableOnDataContext` — the
  hook that *does* run per action update — is `LuaInplaceRenameHandler`'s, and it declines a
  `METHOD_FUNCTION` before reaching any of this (`design.md` §1, prior-art table, last row).

## Design Gaps

### Gap 2.1 — Renaming a table leaves its `function t:m()` receiver segment behind [pre-existing, not caused here]

Measured during `REFACT-09-00-DR-02`, on the **unmodified** rename path and confirmed against the
dotted control:

```
P06 caret on the receiver of a colon call      → RENAMED
    | local renamedTable = {}
    | function t:m() end          ← left behind
    | renamedTable:m()
P19 CONTROL: the receiver of a DOTTED declaration → RENAMED
    | local renamedTable = {}
    | function M.run() end        ← left behind
    | renamedTable.run()
```

The mechanism: `LuaNameReference.isReferenceTo` resolves the `t` of `function t:m()` through
`LuaScopeProcessor`'s `is LuaFuncDecl` recursion branch
([LuaScopeProcessor.kt:79-84](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)),
which returns **that leaf itself**, so the identity test against the `local t` leaf is false and the
segment is never collected.

- **Not this feature's**: it is the same in both spellings, it predates [[TYPE-13]], and
  `REFACT-09-05` (caret on the receiver renames the receiver, not the method) still holds — `m` is
  untouched. `requirements.md` case 19 pins the measurement so a later reader does not rediscover it.
- **Action**: file a `BUG` with the two fixtures above and add its roadmap row. It belongs to
  `REFACT-01`'s declaration-collection rules, not here; fixing it inside this feature would widen a
  colon-method rename into a receiver rename.

### Gap 2.2 — `refactoring.rename.colonMethod` becomes unreachable

`requirements.md`'s acceptance criteria require this to be answered either way. **Answer: deliberately
replaced.** After `design.md` §2.2, the `METHOD_FUNCTION` arm calls `colonMethodSubstitution`, whose
own refusals are the six narrower messages of §7.2 plus `notADeclaration` for a step-1 null that the
grammar makes unreachable (`funcNameMethod` occurs only inside `funcName`, which occurs only inside
`funcDecl`). The old key and its call site are removed together in Phase 3.

### Gap 2.3 — The accepted set is narrow, and deliberately so

Of the receiver shapes measured, the predicate accepts a **file-local table whose value never leaves
the file**, including its `self:` calls and its `---@class`-annotated form, and refuses everything
else: global receivers, module receivers, aliases, parameters, factories, `setmetatable` OO, chains
and mixed dot/colon access to the same member.

- **This is the honest set, not a placeholder.** Every refused shape was measured reporting
  `declarationOf == null` at one or more of its call sites (`requirements.md` Overview), so accepting
  it would mean renaming on a guess.
- **Where the width would come from**: widening `upSet` reach to the factory / `self` /
  nested-constructor shapes ([[TYPE-13]] Gap 2.7) and giving a `require`d module's type its members
  ([[TYPE-13]] Gap 2.11) are both engine changes with the member-map blast radius [[TYPE-13]]
  Risk 1.1 describes. Neither belongs to a rename.
- **Action**: none now. No roadmap row is minted: nothing regresses, and the widening belongs with
  whichever consumer next needs those shapes.

### Gap 2.4 — Making colon call sites resolve would remove the need for a private collector

`design.md` §9 Alternative B. If `LuaNameReference.multiResolve` gained a colon-method branch,
`ReferencesSearch` would find call sites, `findReferences` would need no arm, and Go to Declaration
and Find Usages would start working on `obj:m()` as a side effect.

- **Why not here**: measured, that route reports `declarationOf == null` for aliases, parameter
  receivers, `self`, required modules and cross-file globals. A resolution that is null in those
  cases is a navigation feature with its own requirements, and folding it into a rename would put a
  rename's safety on a hook every inspection also reads.
- **Action**: recorded as future work; it is the natural predecessor of a wider `REFACT-09`.

### Gap 2.5 — The conflict arm changes which rules run for `METHOD_FUNCTION`, and that kind reaches them for the first time

Before this feature no `METHOD_FUNCTION` ever reached `findCollisions` — `substituteElementToRename`
refused first. `design.md` §5 states each inherited rule's premise and why it is false here; the C4
case was **measured** producing a conflict that does not exist (`requirements.md` case 22).

- **Risk of the arm**: `captures` (C1) no longer runs for this kind. C1's premise is lexical capture
  of the *renamed name*, which a table member does not participate in.
- **Action**: `implementation-plan.md` Phase 5 pins both directions — case 21 (a real member
  collision is reported) and case 22 (an unrelated same-named receiver in another file is not).

## Technical Debt & Future Work

- **TBD: dotted access to a colon-declared member.** `t.m`, `function t.m()` and `t.m = f` name the
  same member as `t:m` and are currently a refusal (design §3.3 `DottedMember`). Rewriting them
  alongside is a coherent extension; it needs its own decidability rule for the dotted call sites,
  which resolve through `getQualifiedName`/`LuaGlobalDeclarationIndex` rather than through the type
  engine — a different mechanism, not a wider version of this one.
- **TBD: `setmetatable` OO.** The instance call `o:b()` was measured resolving to its declaration;
  what refuses the shape is that `Class` is passed as an argument and therefore escapes. Admitting
  it needs a value-flow rule, not a shape exception (design §9 Alternative D).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| `REFACT-09-00-DR-02` | Define a **complete** usage set for a colon method operationally, and measure whether it is computable without a whole-project scan. | Risk 1.1; adopted from `TYPE-13-00-DR-02`, which named [[REFACT-09]] as its owner | **done — result below** |

### DR-02 result — executed on the gce builder at `0bccadae`

Two probes, both reverted (`git status --porcelain` empty in the working tree and on the builder):
`Refact09Dr02CompletenessProbe` (reach, no production change) and `Refact09PrototypeProbe` driving a
throwaway prototype of `design.md` §2-§4 end to end through `myFixture.renameElementAtCaret`.

**Finding 1 — the platform's usage set for a colon method is empty at every scope.**
`ReferencesSearch.search(<declaration leaf>, GlobalSearchScope.allScope(project))` returned
`references=0` for the plain local table, the global table, `setmetatable` OO, the factory, the
`require`d module and the `---@class`-annotated receiver. `LuaNameReferenceSearcher` *does* scan
colon call sites, but gates on `isReferenceTo`, which resolves, and a colon call site resolves to
nothing. So completeness cannot be delegated to the platform; the feature must supply the set or refuse.

**Finding 2 — `declarationOf` answers "where is this member declared", not "is this set complete",
and is null wherever completeness is at risk.** The full reach table is in `requirements.md`
Overview. The shapes reporting `declarationOf == null` include an alias (`local u = t; u:m()`), a
parameter receiver, every `self:` call, a `require`d module, and a **global receiver called from
another file** — while the same global's in-file call resolves. A completeness rule built on
`declarationOf` alone would therefore accept the global shape and miss every cross-file call site.

**Finding 3 — completeness is decidable syntactically, and the answer is a file-local containment
argument plus a per-site decidability test.** `design.md` §3.1 states the rule; §3.2-§3.5 specify it. The measured outcome
of the resulting predicate, driven end to end:

| fixture | outcome |
| :-- | :-- |
| plain local table, declaration caret | RENAMED — all three sites |
| plain local table, call-site caret | RENAMED |
| `self:` call-site caret | RENAMED |
| plain local table + a `self:` call | RENAMED — declaration, `self:` call and direct call |
| two receivers sharing a method name | RENAMED — the other receiver untouched |
| `---@class` annotated local | RENAMED |
| identical shape in a second file | RENAMED, sibling file untouched, no conflict |
| the new name already a member of the receiver | CONFLICT reported |
| global receiver (with a cross-file call) | REFUSED — `receiver 'Obj' is not a file-local table` |
| module `return M` | REFUSED — `escapes at 'M' (bare, not a call head)` |
| alias `local u = t` | REFUSED — same |
| `setmetatable({}, Class)` | REFUSED — same |
| `t().x = 1` in the receiver's own suffix | REFUSED — `escapes at 't' (call step in a suffix)` |
| `t:m()` written above the `local t` | REFUSED — `receiver 't' is not a file-local table` |
| receiver name declared twice in the file | REFUSED — same |
| the method declared twice on one receiver | REFUSED — `'m' is declared 2 times on this receiver` |
| `print(t.m)` beside `function t:m()` | REFUSED — `also accessed as '.m'` |
| `self.m = 1` beside `function C:m()` | REFUSED — same |
| `t:m():m()` | REFUSED — `The call 'm' on line 3 cannot be bound to a declaration` |
| `local function f(x) x:m() end` | REFUSED — `The call 'm' on line 4 …` |
| `require`d module receiver, call-site caret | REFUSED — `Cannot determine which declaration this name refers to` |
| caret on `self` | REFUSED — `'self' is not the method name` |
| caret on the receiver of `t:m()` | RENAMED (the receiver; `m` untouched) — see Gap 2.1 |
| control: dotted `function M.run()` | RENAMED, unchanged by this feature |

Every refusal above left its file byte-identical.

**Finding 4 — the regression surface is the two `LuaRenameTest` methods that assert the replaced
message, and nothing else.** Full suite with the prototype applied:
**2 979 tests, 2 failures, 0 errors** — `LuaRenameTest.testColonMethodDeclarationIsRefused` and
`testSelfInsideAMethodIsRefusedAsTheMethod` — each failing only on the refusal *message*, each still
refusing. `implementation-plan.md` Phase 4 rewrites them.

**Finding 5 — resolution cannot be used to find the receiver binding.** The first prototype used
`declaration.funcName.nameRef.reference?.resolve()` and **refused every fixture**, because
`LuaScopeProcessor`'s recursion branch returns the funcName's own leaf. `design.md` §3.3.1 records
the mechanism and the text-plus-uniqueness lookup that replaces it. Recorded because it reads
correct and is not.

## Test Case Gaps

- **The dialog and its refusal balloons are not exercised headlessly.**
  `CommonRefactoringUtil.showErrorHint` throws instead of painting under `BasePlatformTestCase`, so
  every refusal is asserted through an exception message and nobody has seen the balloon.
  `human-verification-checklists.md` covers it.
- **No corpus measurement of how often the predicate accepts.** The accepted shape is narrow
  (Gap 2.3) and the corpus ratchet does not exercise rename. A `-PwithCorpus` sweep counting
  accept/refuse verdicts over the 809 colon-method declarations would size the feature's real reach;
  it is not needed to ship a refusing-by-default rename and is not planned.
- **In-place rename is not covered, because it is unreachable.** Both in-place gates require
  `kindOf(...)?.isFileLocal == true` and `METHOD_FUNCTION.isFileLocal` is `false`. If a later change
  makes a method kind file-local, that assumption dies silently — `design.md` §1's prior-art table is
  where it is written down.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- [[TYPE-13]] risks and gaps — Gaps 2.7, 2.11 and 2.12 are the engine limits this feature refuses around.
