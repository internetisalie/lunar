---
id: "BUG-435"
title: "Inside `if type(x) == \"table\"`, the narrowed variable offers no members at all"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-435: type-guard narrowing replaces a table literal's members with nothing

Found by COMP-09 Phase 2's TC 10j, which set out to measure whether Rule S needed an eighth clause
for the `LuaScope.declare` site at `LuaTypesVisitor.kt:462` and answered a different question on the
way. **It is not a COMP-09 regression** — see the parent-commit measurement below.

A local bound to a table literal completes its members normally. Wrap the *same* local in the
idiomatic `type(x) == "table"` guard and completion inside the guard offers **no member at all** —
only the keywords an open `if` block contributes.

## Reproduce

Consumer file:

```lua
local Shadow = { fromLocal = 1 }

if type(Shadow) == "table" then
    Shadow.<caret>
end
```

| caret | offered |
| :-- | :-- |
| `Shadow.<caret>` at file scope | `[fromLocal]` |
| `Shadow.<caret>` **inside the guard** | `[else, elseif, end]` — three keywords, zero members |

## Expected

Inside the guard, `Shadow.` offers at least what it offers outside it: `[fromLocal]`. Narrowing a
value to `table` should not be able to *remove* members — the guard asserts the value **is** a table,
so the narrowed type should be at worst the unnarrowed one.

## Actual

Every member is gone. The three strings that remain are keyword completions, not members.

## Measured — and proven pre-existing (2026-08-12)

Taken on a detached worktree at **`fb79c038`**, the commit *before* COMP-09 Phase 2 landed — a tree
with no `LuaLocalBindingScan` and no hoisted index arm:

```
REVIEW-PARENT TC10j offered=[else, elseif, end]
REVIEW-PARENT TC10a offered=[fromLocal]
REVIEW-PARENT TC10f offered=[fromLibrary]
REVIEW-PARENT TC10d offered=[end]
```

`TC10j` is the guarded caret and `TC10a` the unguarded one, on identical fixtures. The behaviour is
byte-identical before and after Phase 2, which is expected: Rule S **declines** on this fixture (the
consumer binds `Shadow`), so the hoisted arm never runs and everything below the insertion point
executes exactly as it did. `TC10f` is the control — an arm that *does* run, offering `fromLibrary`.

## Where to look

`LuaTypesVisitor.kt:462` re-declares the already-bound name into the narrowed scope
(`scope.declare(guard.variableName, narrowedVar)`). The hypothesis from Phase 2's measurement, **not
yet confirmed by a fix**: the narrowed variable node carries the *guard's* type (`table`, with no
members) rather than the table literal's inferred members, so the re-declaration overwrites a
populated node with an empty one.

Anyone taking this should confirm that by reading the node the guard installs, not by assuming this
paragraph — that is the failure mode COMP-09 spent three review rounds on.

## FIXED 2026-08-20 — the hypothesis was right, and it was confirmed by reading the node

This report said the cause was a hypothesis and told the next reader to *"confirm that by reading the
node the guard installs, not by assuming this paragraph"*. Done, with a probe inside
`injectNarrowedBinding`:

```
B435-NODE var=Shadow matchBranch=true
  originalWrite = Table(className=null, localMembers={fromLocal=…}, isExact=false)
  guardNarrowed = Table(className=null, localMembers={},            isExact=false)
  chosen        = Table(className=null, localMembers={},            isExact=false)
```

The match branch took `guard.narrowedType` **wholesale**, so a populated table was replaced by the
guard's bare one and every member vanished.

### The fix

`narrowType(original, to)` — when `original` is already of the narrowed kind, keep it. Inside
`if type(Shadow) == "table"` the value **is** a table, so a guard that only restates what the type
already says adds nothing and must not subtract. A guard that genuinely contradicts or refines the
type still wins, and the else branch's `subtractType` is untouched.

### Mutation proof — 2/2, and the second is the load-bearing one

| mutation | red |
| :-- | :-- |
| revert to `guard.narrowedType` wholesale | both BUG-435 tests |
| make `narrowType` always keep the original (never narrow) | **5 `TestFlowSensitiveType` tests** — typeof-equality→string, nil equality, nil inequality, elseif chains |

The second is what shows the fix does not simply *disable* narrowing. TYPE-08's own pre-existing
tests guard that direction, and they fire when the rule is made too permissive — so the implemented
rule sits between the two failure modes rather than at one end.

### COMP-09's TC 10j moved, and its own KDoc said which half to trust

`MemberEnumerationShadowingTest.testTypeGuardNarrowingIsCoveredTransitively` pinned
`[else, elseif, end]`. Its **verdict** is `fromLibrary`'s *absence* (is the site transitively covered
by Rule S?); the set was explicitly recorded there as the defect, against TC 10j's own prediction of
`[fromLocal]`. Measured after the fix: **`[fromLocal, else, elseif, end]`** — `fromLocal` gained,
`fromLibrary` still absent. The verdict is intact and only the half that method recorded as broken
moved, which is what made it safe to update.

### Gates

`test --rerun --no-build-cache -PwithCorpus`: **green, zero `IMPROVED` lines**. A narrowing that only
*adds* members inside a guard moves no inspection count, which is the expected result rather than a
lucky one.

## Scope note

Out of scope for COMP-09, which is enumeration rather than inference: this defect is upstream of the
member-enumeration door and reproduces with the whole feature reverted. Related: [[COMP-09]] TC 10j,
which stays green because its verdict clause is `fromLibrary`'s **absence** — the site really is
transitively covered by Rule S, and that half of the measurement stands.
