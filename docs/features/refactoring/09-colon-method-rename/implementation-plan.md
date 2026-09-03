---
id: "REFACT-09-PLAN"
title: "09: Implementation Plan"
type: "plan"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# REFACT-09: Implementation Plan

Phase 1 builds the occurrence scan and pins it in isolation. Phase 2 removes the blanket refusal and
adds the EDT guards — this is the phase that turns rename on. Phase 3 adds the member-collision
rule. Phase 4 rewrites every existing test whose assertion the removed message breaks. Phase 5 is
the corpus re-measurement and the live check.

**Phases 2 and 4 land in one commit** if the branch must stay green commit-by-commit: Phase 2 is
where every test asserting the removed message goes red, and Phase 4 is where those tests are
rewritten. Every other phase leaves the build green on its own.

**Standing constraints from [`docs/engineering-contract.md`](../../../engineering-contract.md), each
of which this feature has tripped over before:**
- **≤3 arguments per function, private helpers included.** `design.md` §2.1 gives every signature;
  the context is bundled into the existing `LuaRenameTarget`. Self-audit every helper before opening
  a PR — implementor subagents fail review on this rule more than on any other.
- **≤30 logic lines per function.** `design.md` §2.1 names the split —
  `undecidedOccurrences` / `candidateFiles` / `undecidedIn` / `colonCallVerdict` /
  `bracketOccurrences` / `fieldOccurrences` / `fieldKeyName` / `literalName` / `colonCallReceiver` /
  `receiverTypeOf` / `hasMember` — and is the source of truth for it.
  Do not inline them back.
- **No `!!`, no wildcard imports, `val` over `var`, read-only collection types on public API.**
- **Threading**: nothing this feature adds may run on the EDT except the guards
  `design.md` §3.6 specifies, each O(1). `ProgressManager.checkCanceled()` is the first statement of every
  iteration block in `LuaColonMethodRename`.
- **Formatting**: `tooling/gce-builder/gce-builder.sh run ktlintFormat` on the VM, then rsync the
  `.kt` files back, then `ktlintCheck` **alone**. Never `run "ktlintFormat ktlintCheck"` (BUG-445).
- **Every test invocation carries `--rerun`**, and the deletion-heavy phases add `--no-build-cache`.

## Phases

### Phase 1: `LuaColonMethodRename` — the occurrence scan [Must]

Create `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaColonMethodRename.kt`
exactly as `design.md` §2.1 declares it, with the bodies of §3.2, §3.3, §3.4 and §3.7.

- [ ] `undecidedOccurrences(target, usages)` — §3.2.
- [ ] `candidateFiles` — `CacheManager.getFilesWithWord(name, UsageSearchContext.ANY, projectScope, true)`,
      §3.3. Not `IN_CODE`: the bracket spelling puts the name inside a string token.
- [ ] `undecidedIn` — the `when (nameRef.parent)` of §3.3, with **no** `LuaFuncNameMethod` branch,
      and appending **both** `bracketOccurrences` and `fieldOccurrences`.
- [ ] `colonCallVerdict` — §3.4, "resolves ⇒ decided".
- [ ] `bracketOccurrences` + `fieldOccurrences` + `fieldKeyName` + `literalName` — §3.3, the literal
      format of §4. One `literalName` serves `t["m"]` and `{ ["m"] = 1 }`; do not write a second copy.
- [ ] `receiverAlreadyHasNewName` + `receiverTypeOf` + `colonCallReceiver` — §3.7. The receiver comes
      from a **usage**, never from `funcName.nameRef`: DR-05 measured the declaration-side receiver
      as `unknown` in every shape. Keep the union-arm loop — without it the rule is inert for every
      `---@class` receiver.
- [ ] Add every Phase-1 key `design.md` §7.2 lists to `LuaBundle.properties`.
- [ ] `ProgressManager.checkCanceled()` as the first statement of every iteration block.

**Verification (Phase 1):**
- [ ] `LuaColonMethodRenameTest` (new, `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/`)
      drives `undecidedOccurrences` directly with a usage set built from
      `ReferencesSearch.search(declarationLeaf, GlobalSearchScope.projectScope(project))`, and
      reproduces the DR-03 control table (`c01`-`c14`) and the DR-05 field-scan fixtures
      (`fieldKeyOnSameTable`, `fieldKeyOtherTable`, `bracketKeyInConstructor`, and the
      `controlPositionalValue` / `controlOtherFieldName` negatives). One `configureByText` per method
      except each control whose fixture names a second file.
- [ ] Each of `requirements.md` rows 6, 7, 8, 9, 10, 11, 20, 23, 24, 25, 26, 27 and 28 has a method
      here, and each named mutation is applied to the shipped code, executed, and observed reddening
      **that** row while row 1's control stays green.
- [ ] Row 28 builds its usage set by hand (a set containing the non-resolving colon occurrence), which
      is the only reachable falsifier for design §3.4 clause (a): DR-01 Finding 4 measured 0
      occurrences of that shape over both trees.
- [ ] Rows 24 and 26 are the pair that keeps the field clause honest — one asserts a report on an
      unrelated table's key, the other asserts **no** report for a positional value, a computed key
      and a literal that cannot spell an identifier.
- [ ] A cancellation test in the shape `LuaRenameConflictTest.testCancellationIsChecked…` uses,
      differential over the **occurrence** count so it cannot be satisfied by the entry checks.
- [ ] `test --rerun --tests '*LuaColonMethodRename*'` green, then the full suite green.

### Phase 2: turn rename on — remove the refusal, add the EDT guards [Must]

- [ ] `LuaRenameProcessor.substituteElementToRename`: replace the `METHOD_FUNCTION → refuse` arm
      with `colonMethodSubstitution(leaf, editor)` (`design.md` §2.2).
- [ ] Add `caretRefusal` and `outOfProjectRefusal` (`design.md` §3.6). Import
      `com.intellij.psi.util.PsiUtilBase` and `com.intellij.psi.search.GlobalSearchScope`.
- [ ] Delete `refactoring.rename.colonMethod` from `LuaBundle.properties` and add every Phase-2
      key `design.md` §7.2 lists (`REFACT-09-09`).
- [ ] **Do not touch** `findReferences`, `findCollisions` or `renameElement`. DR-02 Finding 1
      measured `findReferences` already returning the usage set.

**Verification (Phase 2):**
- [ ] `LuaColonMethodRenameTest` gains `requirements.md` rows 1, 2, 3, 4, 5, 13, 14, 15, 16, 19 and 21.
- [ ] Row 13 is driven through `myFixture.renameElementAtCaret`, **not** through
      `LuaRenameTest.assertRefusedWith`, which passes a null editor and cannot reach the caret guard
      (`design.md` §3.6).
- [ ] Row 14's fixture is `local f = io.open("x")` / `f:<caret>write("y")`; assert the refusal names
      `io.lua`. Its mutation — delete `outOfProjectRefusal` — must be executed and observed.
- [ ] Row 16 uses the `UndoManager` idiom of
      [LuaRenameUndoTest.kt:43-49](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/LuaRenameUndoTest.kt).
- [ ] Row 22: `LuaBundle` has no `refactoring.rename.colonMethod` key —
      `git grep -n 'rename\.colonMethod=' src/main/resources` returns nothing.
- [ ] The full suite is expected to fail on exactly the methods that **assert** the removed fragment —
      `LuaRenameTest.testColonMethodDeclarationIsRefused` and
      `testSelfInsideAMethodIsRefusedAsTheMethod` today, and whatever
      `git grep -n 'assertRefusedWith("function Obj:method()"' src/test` reports at the time. The
      wider `git grep -n 'function Obj:method()' src/test` is **not** that list: it also returns
      `LuaDeclarationSiteTest.kt:85`, where the text is a Lua fixture rather than an assertion, and
      that test must stay green throughout. Any failure outside the assertion set is a regression,
      not an expected one — record the observed failure list before proceeding to Phase 4.

### Phase 3: the conflict arm [Must]

- [ ] `LuaRenameConflictDetector.collisions`: add the `METHOD_FUNCTION` branch of `design.md` §5,
      selecting `colonMethodCollisions(target, usages)` and leaving the existing branch untouched
      for every other kind.
- [ ] Add `colonMethodCollisions` and `incompleteMessage` (`design.md` §5). Build the usage set as
      an `IdentityHashMap`-backed set, the idiom `distinctByAnchor` already uses in this file.
- [ ] Add `refactoring.rename.conflict.memberExists`, the Phase-3 key of `design.md` §7.2.
- [ ] `colonMethodCollisions` passes the usage element set to **both** `undecidedOccurrences` and
      `receiverAlreadyHasNewName` (`design.md` §5); the second argument is not optional.

**Verification (Phase 3):**
- [ ] `requirements.md` rows 12, 17 and 18, each in `LuaColonMethodRenameTest` or
      `LuaRenameConflictTest`, catching `BaseRefactoringProcessor.ConflictsInTestsException`.
- [ ] Row 12's mutation — keep `ambiguousGlobal` for this kind — is applied and observed producing
      `'t:m' is declared in 2 places…`, the transcript DR-02 Finding 6 records.
- [ ] Rows 17, 17a, 17b, 17c and 17d cover the receiver shapes `receiverAlreadyHasNewName` decides.
      Row 17's mutation — keep `globalNameTaken` for this kind — is applied and observed producing
      `A global named 't:n' already exists in this project`; row 17b's mutation — drop the union-arm
      loop — is applied and observed reddening 17b alone; row 17d is the fixture that a
      receiver-*text* rule gets wrong.
- [ ] Row 29 asserts the measured miss: with no bound call site, **no** conflict is reported.
- [ ] Row 18's mutation — let `captures` run for this kind — is applied and observed producing a
      capture conflict on the `t:m()` site.
- [ ] Rows 6, 7, 8, 9, 10 and 11 are re-driven **end to end** through `myFixture.renameElementAtCaret`
      here (Phase 1 drove the scan in isolation), asserting `ConflictsInTestsException` and a
      byte-identical file.
- [ ] Full suite: the Phase-2 failure list and nothing else.

### Phase 4: repair the REFACT-01 tests the removed message breaks [Must]

Each test named below **asserts** the fragment `function Obj:method()` as the refusal message that
`refactoring.rename.colonMethod` supplied and Phase 2 deleted. The list is
`git grep -n 'assertRefusedWith("function Obj:method()"' src/test`, and it is *not*
`git grep -n 'function Obj:method()' src/test`: that wider grep also returns
[LuaDeclarationSiteTest.kt:85](../../../../src/test/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSiteTest.kt),
where the same text is a **Lua fixture** (`configure("Obj = {}\nfunction Obj:method() end\n")`) and
not an assertion about any message. That file is out of scope for this phase and must not be edited.

- [ ] `LuaRenameTest.testColonMethodDeclarationIsRefused`
      ([:597-606](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameTest.kt)).
      Its fixture is `Obj = {}` / `function Obj:<caret>m() end` / `local o = Obj` / `o:m()`. An
      aliased receiver reaches no declaration ([[NAV-13]] Out of Scope), so `o:m()` is an undecided
      occurrence: rewrite the test to assert a **conflict** and a byte-identical file, and rename it
      to say so. Keep the `myFixture.checkResult(source)` assertion verbatim — it is what makes the
      case worth having.
- [ ] `LuaRenameTest.testSelfInsideAMethodIsRefusedAsTheMethod`
      ([:402-412](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameTest.kt)).
      Keep both `TargetElementUtil` assertions — they pin `LuaScopeProcessor`'s behaviour and are
      unaffected — and replace `assertRefusedWith("function Obj:method()", target)` with a drive
      through `myFixture.renameElementAtCaret`, asserting the caret-guard message. The KDoc's claim
      that "there is no `self` guard and there must not be one" is superseded by `design.md` §3.6
      and must be rewritten with it; `function T.m(self, x)` still renames normally because its
      `kindOf` is `PARAMETER` and the guard is inside the `METHOD_FUNCTION` arm (`REFACT-01` TC-19c
      is the standing gate).

**Verification (Phase 4):**
- [ ] `test --rerun --no-build-cache` — the **full** suite, 0 failures, 0 errors. An isolated
      `--tests '*LuaRenameTest*'` does not discharge this.
- [ ] `git grep -n 'assertRefusedWith("function Obj:method()"' src/test` returns nothing — no test
      still asserts the deleted message.
- [ ] `git grep -n 'rename\.colonMethod=' src/main/resources` returns nothing, and every key
      `design.md` §7.2 names is present.
- [ ] `git grep -n 'function Obj:method()' src/test` returns **exactly** the
      `LuaDeclarationSiteTest.kt` fixture line. A second hit means an assertion was missed; zero hits
      means the fixture was edited out of scope.

### Phase 5: re-measure and verify live [Must]

- [ ] Re-run DR-01's reach instrument against the **shipped** `LuaColonMethodRename` rather than a
      transcription, over the pinned checkouts and the annotated substitute staged per DR-01
      Method point 6. Record the verdict table in `risks-and-gaps.md` beside the planning figures.
      The denominators must reproduce (734 / 941 / 14 116 and 195 / 268 / 2 446); a change in them
      means the scope moved, not the code. The verdict columns that must reproduce are
      DR-01 Finding 2's re-measured ones — corpus `accepted` 20, `acceptedNoCallSites` 120,
      `blocked` 801; substitute 50 / 44 / 174 — and the sole-blocker decomposition beside them.
      Classify by **blocker set**, not by first undecided occurrence: that classification depends on
      the order `CacheManager` returns candidate files and is not reproducible (DR-01 Finding 2).
- [ ] Close Gap 2.5: run both instruments and **name** the ZeroBrane declaration on which they
      disagree, or record that they now agree.
- [ ] `test -PwithCorpus --rerun --no-build-cache` — no `Corpus regression:` line. Check the
      `timestamp` attributes of `LuaCorpusSweepTest`'s XMLs fall inside the run window; `--rerun`
      does not clear `build/test-results/test/` when a task is skipped.
- [ ] `ktlintFormat` on the VM → rsync the `.kt` files back → `ktlintCheck` alone.
- [ ] Work `human-verification-checklists.md` in the containerised GoLand. The conflicts dialog and
      the refusal balloons are the surfaces no headless test can see.
- [ ] Update `REFACT-01-08` to `Full` and delete REFACT-09's roadmap row (`docs/roadmap.md`).
- [ ] `CHANGELOG.md` entry under the current milestone.

## Requirement → Phase coverage

| Requirement | Phase | Pinned by |
| :-- | :-- | :-- |
| `REFACT-09-01` | 2 | rows 1, 3, 4, 5, 21 |
| `REFACT-09-02` | 2 | row 2 |
| `REFACT-09-03` | 1 (scan), 3 (end to end) | rows 6-11, 20, 23-28 |
| `REFACT-09-04` | 2 | row 13 |
| `REFACT-09-05` | 2 | row 14 |
| `REFACT-09-06` | 2 | row 15 |
| `REFACT-09-07` | 2 | row 16, plus the byte-identical assertions of rows 13, 14 |
| `REFACT-09-08` | 3 | rows 17, 17a-17d, 29 |
| `REFACT-09-09` | 2 | row 22 |
| `REFACT-09-10` | 3, 4 | rows 12, 18, 19 and the full-suite gate |

## Verification tasks

| # | Task | Phase | Gate |
| :-- | :-- | :-- | :-- |
| V1 | Every mutation named in `requirements.md` is applied to the shipped code, executed, and observed reddening its own row while a control row stays green | 1-3 | a mutation that reddens nothing, or reddens every row, fails the phase |
| V2 | Full unit suite, `test --rerun --no-build-cache`, 0 failures | 2-5 | an isolated `--tests` run does not discharge it — a green filtered run has hidden a full-suite failure before |
| V3 | Corpus lane, `test -PwithCorpus --rerun --no-build-cache` | 5 | no `Corpus regression:` line; XML timestamps inside the run window |
| V4 | Reach re-measurement against the shipped class, denominators reproduced | 5 | recorded in `risks-and-gaps.md` |
| V5 | `ktlintCheck` alone, after formatting on the VM and rsyncing back | 5 | never `run "ktlintFormat ktlintCheck"` (BUG-445) |
| V6 | `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py docs` | 5 | 0 errors each |
| V7 | Live verification in the containerised GoLand | 5 | `human-verification-checklists.md` fully worked |

## Task summary

| Phase | Deliverable | Priority |
| :-- | :-- | :-- |
| 1 | `LuaColonMethodRename` + the Phase-1 bundle keys + `LuaColonMethodRenameTest` | Must |
| 2 | `LuaRenameProcessor` guards, the blanket refusal removed, the Phase-2 keys | Must |
| 3 | `LuaRenameConflictDetector`'s `METHOD_FUNCTION` arm + the Phase-3 key | Must |
| 4 | The rewritten `LuaRenameTest` methods | Must |
| 5 | Re-measurement, corpus lane, lint, live verification, docs and CHANGELOG | Must |
