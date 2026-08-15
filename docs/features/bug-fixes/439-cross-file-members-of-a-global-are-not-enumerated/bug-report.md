---
id: "BUG-439"
title: "A global's members declared in a *sibling* file are never offered — love2d's whole submodule API is unreachable"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-439: cross-file one-segment members of a global receiver are not enumerated

Found by COMP-09's **live IDE verification** (2026-08-14), while running human-checklist
scenarios 1.1 and 2.3 against real definition libraries. Neither scenario asked about this;
it was visible the moment a real library was loaded, and **no fixture in the feature could
have shown it** — see "Why every gate missed it".

## Reproduce

Two files in the same root:

```lua
-- probe_a.lua
---@class Probe
Probe = {}
function Probe.sameFileFn() end
Probe.sameFileVal = 1
```
```lua
-- probe_b.lua
Probe.otherFileVal = 2
function Probe.otherFileFn() end
Probe.nested = {}
```

`Probe.<caret>` offers **only `sameFileFn` and `sameFileVal`** — the two declared in the file
that also declares `Probe`. The three from `probe_b.lua` are absent.

The control that makes this attributable to the *file boundary* rather than to the assignment
form: `ngx.` **does** offer same-file `ngx.X = …` assignments (`AGAIN`, `CRIT`, `DEBUG`, …).
So plain assignment enumerates fine; crossing a file is what loses it.

## Consequence on shipped material

Measured on `LuaCATS/love2d` (pinned in `lunar-definitions-catalog.json`, sha256-verified):

- `love.` offers exactly the **40** members declared in `love.lua`, and **none of the 19
  submodules** — `love.graphics`, `love.audio`, `love.filesystem`, … each of which is a plain
  `love.graphics = {}` assignment in a sibling file.
- Typing `love.gr` collapses the popup to empty.
- `love.graphics.` yields nothing either, consistent with scenario 2.2's "`Foo.bar.` stays empty".

**So love2d's 100-function `love.graphics` API is unreachable by completion from either
direction**, on a library the product ships in its own catalog.

## Why every gate missed it

Scenario 2.2 expects `Foo.` to offer `[bar, direct]` and passes — but its fixture keeps every
declaration in **one** library file, so the cross-file case is untested. The same uniformity
that hid [[BUG-436]] (every fixture used `.lua`, so a non-`.lua` filter was invisible) hides
this: every fixture declares a receiver and its members together.

A subset defect is also invisible to the instruments this feature relies on — the golden diffs
for *added* rows, the corpus ratchet stops on *movement*, and COMP-09-06's acceptance is "if any
baseline moves, stop". Members that quietly fail to exist pass all three.

## Open question — regression or pre-existing? MEASURE IT, do not infer

**Do not take the following as settled.** The documentary evidence says pre-existing:
COMP-09 design §4.5's selection rule is *first-declaring-file-only within a scope-precedence
chain*, chosen explicitly to reproduce `typeOfGlobalIn`, after DR-09 measured that a flat
`membersOf(receiver, allScope)` union returned a **superset** (`[alsoPrivate,
privateToThisFile, real]` against a golden of `[real]`). On that reading the narrow answer is
deliberate and predates the feature.

But this feature has been burned three times by exactly that kind of reasoning. Settle it the
way [[BUG-435]] was settled: build a detached worktree at the **pre-COMP-09** commit
(`fb79c038` is before Phase 2; `87875c9f` is before Phase 3), load the same two-file probe, and
compare the offered set. If it is pre-existing, say so with the output pasted. If COMP-09
narrowed it, that is a regression against COMP-09-07 and the phase that did it must own the fix.


## Attempt 1 (2026-08-14) — REVERTED. The naive union works, and breaks COMP-09's contract.

Two changes made the two new fixtures pass, and the second is the non-obvious one:

- `membershipOver` unions every declaring file instead of `perFile.first()`.
- **`candidates` must change too, or the union is a no-op.** It asks `LuaGlobalAssignmentIndex`,
  which lists files that assign the **global**; a file that merely *extends* it
  (`Probe.otherFileVal = 2`, `love.graphics = {}`) never appears there, so there was only ever one
  candidate to union. Adding this index's own `getContainingFiles(KEY, receiver, scope)` fixes that.

With both, `testSiblingFileMembersAreOffered` passes and the same-file control still passes.

**The full suite then fails in six places, and they are the design, not incidental coverage:**

| test | what it pins |
| :-- | :-- |
| `MemberEnumerationWorkBoundGateTest.testCompletionDoorReadsExactlyOneFile` | COMP-09's **acceptance criterion** — design §4.10b assertion 4. A union reads N files by construction |
| `LuaReceiverMemberIndexTest.testAFileLocalReceiverIsNotASelectableDeclaringFile` | **DR-09's superset guard** — the exact defect this report predicted a naive widening would re-introduce |
| `LuaReceiverMemberIndexTest.testTheTwoDoorsDisagreeAboutASecondDeclaringFile` | first-declaring-file, pinned deliberately |
| `LuaReceiverMemberDoorParityTest` | the two doors' agreed divergence |
| `MemberEnumerationGoldenTest.testGoldenIsUnchanged` | the golden member set |
| `LuaReceiverMemberIndexTest.testEveryFileTypeRegistrationIsIndexed` | — |

So the fix is **not** "union the declaring files". Any real fix has to keep the one-file work bound
(or renegotiate it with a measurement, since it is a latency criterion with a recorded number), and
must still exclude the unrelated file-local `wx` DR-09 caught. A conditional union — same library
root, or only when the receiver is a table the first file binds — is the shape to explore, and it
needs COMP-09-08's latency harness re-run, not just the golden.

**The regression-vs-pre-existing question is now answered as a side effect: pre-existing.** The
behaviour is `perFile.first()`, which shipped with COMP-09 Phase 2 *preserving* `typeOfGlobalIn` —
the tests above pin it as intended behaviour, so it was never a regression.


### Attempt 2 (2026-08-14) — the blocker is REAL, and it is one specific test

Re-ran with the two bug-codifying tests treated as changeable (they are: one asserts the door reads
*one* file, which is this defect stated as intent). Both BUG-439 fixtures pass. Five of the six
failures are consequential and fine to update.

**The sixth is not, and it kills the `candidates` widening as written:**
`LuaReceiverMemberIndexTest.testAFileLocalReceiverIsNotASelectableDeclaringFile`.

```lua
-- wx.lua         ---@class wx / wx = {} / function wx.real() end
-- unrelated.lua  local wx = {} / function wx.privateToThisFile() end / wx.alsoPrivate()
```

`unrelated.lua`'s `wx` is a **file-local**. It is correctly absent from `LuaGlobalAssignmentIndex` —
which is exactly the rule that saved the old selection — but it **is** present in *this* index under
key `wx`, because the indexer records members by receiver name without caring how the receiver is
bound. So adding `getContainingFiles(KEY, receiver, scope)` to `candidates` re-admits it and the
global `wx` is contaminated with `privateToThisFile` / `alsoPrivate`: **DR-09's measured defect,
reproduced exactly.**

This is not a test pinning a bug. It is the correctness property, and the fix must satisfy it.

### The remedy this points at

The union needs sibling files that extend a **global** receiver while excluding files where the
receiver is *file-local*. That distinction exists at index time and nowhere else — by the time
`candidates` runs, a key is just a name.

So: record a **local-binding sentinel** beside the existing `OPAQUE_BINDING` one. The indexer already
walks bare bindings for opacity (`forEachBareBinding`); a `local R = …` in the file is the same shape
and can emit a `LOCAL_BINDING` marker. `candidates` then unions the KEY files *minus* those carrying
it, and the file-local `wx` is excluded for the right reason rather than as a side effect of which
index was consulted.

That is a contained change — one sentinel, one indexer branch, one filter — but it is index-format
work (another `getVersion` bump) and it must be measured against COMP-09-08's latency harness, since
`candidates` now reads two key spaces on every completion.

## Attempt 3 (2026-08-15) — FIXED. The sentinel, and what it cost.

`LuaReceiverMember.LOCAL_BINDING`, exactly as attempt 2's remedy named it, plus the two-tier
`candidates` it exists to make safe:

- **Tier 1** — files that assign the global, from `LuaGlobalAssignmentIndex`. Unchanged, and it is
  the whole of what the door ever consulted.
- **Tier 2** — files that *extend* it, from `LuaReceiverMemberIndex`'s own key space, **minus** any
  file carrying the sentinel. Tier 1 is exempt from the filter: a file that assigns the global
  declares it however many same-named locals it also contains.
- `membershipOver` unions the candidates' members instead of taking the first file's.
- `getVersion` 2 → 3. Without it a persisted index reads back with no marks — which makes every
  file-local receiver look global at the widened door, i.e. DR-09's contamination, silently, on
  exactly the machines that had indexed before.

**The sentinel is emitted only for a receiver name the file already declares members for.** Marking
every `local` would mint an index key per local variable in the project to answer a question about a
handful of names; this way the key space is unchanged and the cost is one bookkeeping entry per
(file, receiver) that is locally bound. `local R = …` and `local function R() end` are both marked;
a `for`-bound or parameter-bound receiver is not, which is a deliberate under-approximation — a
missed mark costs one spurious candidate file, a wrong mark drops a real declaring file.

### The four tests that had to change, and one that did not

`testAFileLocalReceiverIsNotASelectableDeclaringFile` — attempt 2's blocker, DR-09's superset guard
— **stayed green untouched**, which is the point of the whole exercise. So did
`MemberEnumerationGoldenTest` and `LuaReceiverMemberDoorParityTest`, both of which attempt 1 broke.

| test | what changed |
| :-- | :-- |
| `MemberEnumerationWorkBoundGateTest.testCompletionDoorReadsExactlyOneFile` | renamed `…ReadsOnlyTheDeclaringFiles`. The `== 1` was never the bound it looked like: `Target` is bare-assigned in one file, so the count was a property of the *fixture* that happened to also be the acceptance criterion. Now pinned at the receiver's declaring-file count (2) **and** against noise — the second assertion is what a key-space scan actually fails, and the constant 1 made it untestable |
| `LuaReceiverMemberIndexTest.testTheTwoDoorsDisagreeAboutASecondDeclaringFile` | renamed `testBothDoorsSeeASecondDeclaringFile` and inverted. Its reasoning described the mechanism correctly and drew the wrong conclusion from it |
| `LuaReceiverMemberIndexTest.testEveryFileTypeRegistrationIsIndexed` | the completion door now sees all four `LuaFileType` registrations, not just `.lua` — **[[BUG-436]]'s residual closes as a side effect**, because tier 2 reads an index whose filter is file-type-derived. That method pinned the residual "so that closing it elsewhere is a deliberate, visible move"; this is that move |
| `MemberEnumerationMaterializationTest.…WorkDoesNotMoveWhenUnrelatedContentIsAdded` | `METHOD_COUNT` → `METHOD_COUNT + 1`. Its `local Widget = {}` is now marked, and the counter counts raw index entries rather than offerable members |

### A second defect the fix introduced, and a third it nearly shipped

Neither was anticipated; both came out of runs.

**`scopeChain`.** The chain was `projectScope` then `allScope`, which was only safe while a project
file entered the candidate set by *bare-assigning* the global — a non-empty project tier meant a
project declaration. Admitting extenders breaks that implication: one `Lib.myHelper = 1` in your own
code makes the project tier non-empty, `allScope` is never consulted, and the library's members
disappear behind your one addition. That is strictly worse than the reported bug, because it takes
members away from a case that already worked. `scopeChain` now asks `LuaGlobalAssignmentIndex`
directly whether the *project* declares the receiver.

**A logged IDE error on malformed source.** `localNamesIn` read `decl.nameRef.text`. Grammar-Kit
generates required children as `findNotNullChildByClass`, and `notNullChild` does **not** return null
when the child is missing — `PsiElementBase:293` calls `LOG.error`, i.e. a reported exception in
production and a hard failure under `TestLoggerFactory`. An indexer sees every file in the project
and a file being typed into is malformed most of the time, so this is the normal case, not the edge.

It was caught by `TestLuaTypeEnginePhase1.testComplexPhase1File` — a test in an unrelated package,
whose fixture writes `local function repeat(count)`, and `repeat` is a keyword, so the parser yields
a `LuaLocalFuncDecl` with no name node at all. **Every test of this index stayed green**, so a
targeted `--tests '*ReceiverMember*'` run would have shipped it. Settled as caused-by-this-change,
not pre-existing, by running the identical suite against HEAD: 2 677 green, versus 2 682 with one
failure here (the +5 being the new test class).

### Mutation proof — 4/4 CAUGHT

Each mutation was checked to compile, and each landed on exactly its intended test:

| mutation | red |
| :-- | :-- |
| `candidates` tier 2 → `emptyList()` | 6 tests, incl. both cross-file cases and `testCompletionDoorReadsOnlyTheDeclaringFiles` |
| local-binding filter → `.filter { true }` | `testAFileLocalReceiverIsNotASelectableDeclaringFile` |
| `nameOf(decl)` → `decl.nameRef.text` | `testAMalformedLocalDeclarationDoesNotLogAnError` |
| `scopeChain` → unconditional `[project, all]` | `testExtendingALibraryGlobalDoesNotHideTheLibrarysMembers` |

The first is the check on the renegotiated acceptance criterion: widening `filesVisited == 1` to a
declaring-file count plus noise-invariance did **not** defang it — it still fails when the door stops
reading the files it should.

### Gates

`test --rerun --no-build-cache`: **2 683 green**. With `-PwithCorpus`: **2 691 green**, baselines
unmoved (27 min). `ktlintCheck` green, `lint_docs` 0 errors, `lint_planning` 0 errors.

### Regression coverage

`LuaCrossFileMemberEnumerationTest` — the report's own two-file reproduction, the same-file control,
an opaque-sibling authority case, and one that asserts `LuaGlobalAssignmentIndex` does **not** list a
purely-extending sibling. That last one exists because the tier-2 half is the easy half to omit and
the union is a silent no-op without it, which is how attempt 1 nearly shipped as "the union works".

## What a fix has to reckon with

The narrow rule exists for a reason — DR-09's superset was real, and widening naively
re-introduces it, which COMP-09-06 forbids outright (BUG-395/397 reverted exactly that once).
So this is not "use `allScope`". A fix needs a rule that admits sibling declarations **within
the same library root / declaring scope** while still excluding the unrelated file-local `wx`
that DR-09 caught. Related: [[BUG-430]] (`a.b.c = v` flattens onto the root) is the nested-
qualifier half of the same area and may share a root cause.

Relevant: `LuaReceiverMemberIndex` (the receiver-keyed index), design §4.5/§4.5a, and
`LuaGlobalAssignmentIndex`, which selects the declaring file and is itself `.lua`-only
([[BUG-436]]).
