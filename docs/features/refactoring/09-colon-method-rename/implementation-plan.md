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

- [x] `undecidedOccurrences(target, usages)` — §3.2.
- [x] `candidateFiles` — `CacheManager.getFilesWithWord(name, UsageSearchContext.ANY, projectScope, true)`,
      §3.3. Not `IN_CODE`: the bracket spelling puts the name inside a string token.
- [x] `undecidedIn` — the `when (nameRef.parent)` of §3.3, with **no** `LuaFuncNameMethod` branch,
      and appending **both** `bracketOccurrences` and `fieldOccurrences`.
- [x] `colonCallVerdict` — §3.4, "resolves ⇒ decided".
- [x] `bracketOccurrences` + `fieldOccurrences` + `fieldKeyName` + `literalName` — §3.3, the literal
      format of §4. One `literalName` serves `t["m"]` and `{ ["m"] = 1 }`; do not write a second copy.
- [x] `receiverAlreadyHasNewName` + `receiverTypeOf` + `colonCallReceiver` — §3.7. The receiver comes
      from a **usage**, never from `funcName.nameRef`: DR-05 measured the declaration-side receiver
      as `unknown` in every shape. Keep the union-arm loop — without it the rule is inert for every
      `---@class` receiver.
- [x] Add every Phase-1 key `design.md` §7.2 lists to `LuaBundle.properties`.
- [x] `ProgressManager.checkCanceled()` as the first statement of every iteration block.

**Verification (Phase 1):**
- [x] `LuaColonMethodRenameTest` (new, `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/`)
      drives `undecidedOccurrences` directly with a usage set built from
      `ReferencesSearch.search(declarationLeaf, GlobalSearchScope.projectScope(project))`, and
      reproduces the DR-03 control table (`c01`-`c14`) and the DR-05 field-scan fixtures
      (`fieldKeyOnSameTable`, `fieldKeyOtherTable`, `bracketKeyInConstructor`, and the
      `controlPositionalValue` / `controlOtherFieldName` negatives). One `configureByText` per method
      except each control whose fixture names a second file.
- [x] Each of `requirements.md` rows 6, 7, 8, 9, 10, 11, 20, 23, 24, 25, 26, 27 and 28 has a method
      here, and each named mutation is applied to the shipped code, executed, and observed reddening
      **that** row while row 1's control stays green.
- [x] Row 28 builds its usage set by hand (a set containing the non-resolving colon occurrence), which
      is the only reachable falsifier for design §3.4 clause (a): DR-01 Finding 4 measured 0
      occurrences of that shape over both trees.
- [x] Rows 24 and 26 are the pair that keeps the field clause honest — one asserts a report on an
      unrelated table's key, the other asserts **no** report for a positional value, a computed key
      and a literal that cannot spell an identifier.
- [x] A cancellation test in the shape `LuaRenameConflictTest.testCancellationIsChecked…` uses,
      differential over the **occurrence** count so it cannot be satisfied by the entry checks.
- [x] `test --rerun --tests '*LuaColonMethodRename*'` green, then the full suite green.

### Phase 2: turn rename on — remove the refusal, add the EDT guards [Must]

- [x] `LuaRenameProcessor.substituteElementToRename`: replace the `METHOD_FUNCTION → refuse` arm
      with `colonMethodSubstitution(leaf, editor)` (`design.md` §2.2).
- [x] Add `caretRefusal` and `outOfProjectRefusal` (`design.md` §3.6). Import
      `com.intellij.psi.util.PsiUtilBase` and `com.intellij.psi.search.GlobalSearchScope`.
- [x] Delete `refactoring.rename.colonMethod` from `LuaBundle.properties` and add every Phase-2
      key `design.md` §7.2 lists (`REFACT-09-09`).
- [x] **Do not touch** `findReferences`, `findCollisions` or `renameElement`. DR-02 Finding 1
      measured `findReferences` already returning the usage set.

**Verification (Phase 2):**
- [x] `LuaColonMethodRenameTest` gains `requirements.md` rows 1, 2, 3, 4, 5, 13, 14, 15, 16, 19 and 21.
- [x] Row 13 is driven through `myFixture.renameElementAtCaret`, **not** through
      `LuaRenameTest.assertRefusedWith`, which passes a null editor and cannot reach the caret guard
      (`design.md` §3.6).
- [x] Row 14's fixture is `local f = io.open("x")` / `f:<caret>write("y")`; assert the refusal names
      `io.lua`. Its mutation — delete `outOfProjectRefusal` — must be executed and observed.
- [x] Row 16 uses the `UndoManager` idiom of
      [LuaRenameUndoTest.kt:43-49](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/LuaRenameUndoTest.kt).
- [x] Row 22: `LuaBundle` has no `refactoring.rename.colonMethod` key —
      `git grep -n 'rename\.colonMethod=' src/main/resources` returns nothing.
- [x] The full suite fails on exactly the methods that **assert** the removed refusal. **Measured at
      Phase 2: FOUR methods, not the two the `assertRefusedWith` grep predicts.** Full suite
      `test --rerun --no-build-cache`: 487 classes, 3087 tests (3076 baseline + 11 new), 4 failures,
      0 errors, 1 skipped.

      | failing method | how it asserts the refusal | repaired by |
      | :-- | :-- | :-- |
      | `LuaRenameTest.testColonMethodDeclarationIsRefused` | `assertRefusedWith("function Obj:method()", ...)` | Phase 4 |
      | `LuaRenameTest.testSelfInsideAMethodIsRefusedAsTheMethod` | `assertRefusedWith("function Obj:method()", ...)` | Phase 4 |
      | `LuaColonCallRenameRefusalTest.renamingAColonCallSiteIsRefusedInsteadOfRetargetingASameNamedLocal` | `expectThrows(RefactoringErrorHintException) { substituteElementToRename(callSite, null) }` | Phase 4 |
      | `LuaColonCallRenameHandlerTest.invokingTheOfferedHandlerRefusesAndStartsNoTemplate` | drives the registered handler and expects a refusal | Phase 3 + 4 |

      **`git grep -n 'assertRefusedWith("function Obj:method()"' src/test` is NOT a sufficient list**
      — it structurally cannot match the last two, which assert the same removed behaviour through
      `expectThrows` and through the handler. `requirements.md` row 2 already named
      `LuaColonCallRenameRefusalTest…` as "the existing gate for the refusal this replaces", so the
      set was known to the design and only the grep was too narrow. Only 1 of 8 and 1 of 3 methods
      in those two classes fail; the rest stay green, so this is the one obsolete assertion in each
      and not a broad breakage.
- [x] `LuaDeclarationSiteTest` stayed green throughout (6 tests, 0 failures) — the wider
      `git grep -n 'function Obj:method()' src/test` returns it, but there the text is a Lua
      **fixture**, not an assertion, and it is out of scope for Phase 4.

### Phase 3: the conflict arm [Must]

- [x] `LuaRenameConflictDetector.collisions`: add the `METHOD_FUNCTION` branch of `design.md` §5,
      selecting `colonMethodCollisions(target, usages)` and leaving the existing branch untouched
      for every other kind. The pre-existing branch moved into the `else` **verbatim** — the only
      `-` lines in the file's diff are its re-indentation and one corrected KDoc sentence.
- [x] Add `colonMethodCollisions` and `incompleteMessage` (`design.md` §5). Build the usage set as
      an `IdentityHashMap`-backed set, the idiom `distinctByAnchor` already uses in this file.
- [x] Add `refactoring.rename.conflict.memberExists`, the Phase-3 key of `design.md` §7.2.
- [x] `colonMethodCollisions` passes the usage element set to **both** `undecidedOccurrences` and
      `receiverAlreadyHasNewName` (`design.md` §5); the second argument is not optional.
- [x] The class KDoc's claim that "no rule walks the project's PSI looking for candidates" was
      **falsified by this arm** and is corrected rather than left standing: the sentence is now
      scoped to the other kinds, and the KDoc enumerates C1/C2/C3/C4 with the reason each is wrong
      (or inert) for `METHOD_FUNCTION`, read off the source as `design.md` §5 requires.

**Verification (Phase 3):**
- [x] `requirements.md` rows 12, 17 and 18, each in `LuaColonMethodRenameTest`, catching
      `BaseRefactoringProcessor.ConflictsInTestsException` through `conflictsFromRenamingTo`, which
      also asserts **no file was written** while the messages were collected. 16 methods added; the
      class runs 56, 0 failures.
- [x] Row 12's mutation — keep `ambiguousGlobal` for this kind — applied and observed producing
      `ConflictsInTestsException: 't:m' is declared in 2 places; while more than one declaration
      exists its usages do not resolve, so they will not be rewritten`, the transcript DR-02
      Finding 6 records. **Reddens row 12 alone** (1 of 56).
- [x] Rows 17, 17a, 17b, 17c and 17d cover the receiver shapes `receiverAlreadyHasNewName` decides.
      Row 17's mutation — keep `globalNameTaken` for this kind — applied and observed producing
      `A global named 't:n' already exists in this project; renaming would merge the two`. **Its
      blast radius is 5 methods, not 1, and row 17 is not among the two that redden behaviourally**
      — see `requirements.md` row 17, which this phase corrects. Row 17b's mutation — drop the
      union-arm loop — applied and observed reddening **the annotated row alone** (this phase's
      conflict-path row plus Phase 1's direct-mechanism row; 17 and 17a stay green, their receivers
      typing as a plain `{ }`). Row 17d is the fixture a receiver-*text* rule gets wrong, and
      mutation `globalNameTaken` is what demonstrates it.
- [x] Row 29 asserts the measured miss: with no bound call site, **no** conflict is reported and the
      file ends carrying two `function t:n()`.
- [x] Row 18's mutation — let `captures` run for this kind — applied and observed producing
      `ConflictsInTestsException: Renaming to 'n' would bind a usage of 'm' to a different
      declaration that is already visible here`. **Reddens row 18 alone** (1 of 56).
- [x] Rows 6, 7, 8, 9, 10, 11 and 24 are re-driven **end to end** through
      `myFixture.renameElementAtCaret` (Phase 1 drove the scan in isolation), asserting
      `ConflictsInTestsException`, the **exact** message set, and a byte-identical file — including
      the *other* file for the two cross-file rows. Rows 20 and 17c are their negative controls.
- [x] Two further mutations, not named in the plan, separate the arm's two halves — each is a
      falsifier for one call that no row-level mutation reaches, because both calls are new wiring:
      dropping `receiverAlreadyHasNewName` reddens **exactly** rows 17, 17a and 17b; dropping
      `undecidedOccurrences` reddens **exactly** rows 6, 7, 8, 9, 10, 11 and 24. No row is reddened
      by both, which is the disjointness `design.md` §5 asserts between the two questions.
- [x] Full suite `test --rerun --no-build-cache`: **487 classes, 3103 tests (3087 + 16 new), 4
      failures, 0 errors, 1 skipped** — the Phase-2 failure list and nothing else. Every
      `build/test-results` XML timestamp falls inside this run's window, so the read is not stale.
      **The fourth method's failure MESSAGE changed, as Phase 2 predicted it would**: it no longer
      reaches `globalNameTaken` + `capture` but the new arm, and now fails with
      `ConflictsInTestsException: This table already has a member named 'm'; renaming would merge
      the two`. It is still Phase 4's to repair.
- [x] `ktlintCheck` alone (never paired with `ktlintFormat` — BUG-445): BUILD SUCCESSFUL.

### Phase 4: repair the REFACT-01 tests the removed message breaks [Must]

> **Scope corrected by Phase 2's measurement — this phase repairs FOUR methods, not two.** The two
> `LuaRenameTest` methods below are the `assertRefusedWith` pair; Phase 2's full-suite run also
> reddened `LuaColonCallRenameRefusalTest.renamingAColonCallSiteIsRefusedInsteadOfRetargetingASameNamedLocal`
> (which asserts the same removed clause through `expectThrows` + a direct
> `substituteElementToRename(callSite, null)`, and which `requirements.md` row 2 already names as
> "the existing gate for the refusal this replaces" — `REFACT-09-02` requires that rename to
> SUCCEED, so the test is obsolete by design) and
> `LuaColonCallRenameHandlerTest.invokingTheOfferedHandlerRefusesAndStartsNoTemplate` (which now
> reaches the pre-existing `globalNameTaken` + `capture` rules, i.e. DR-02 Finding 6's `R12`, so it
> needs Phase 3's arm before it can be rewritten). Both are recorded in Phase 2's verification block.

Each test named below **asserts** the fragment `function Obj:method()` as the refusal message that
`refactoring.rename.colonMethod` supplied and Phase 2 deleted. That grep,
`git grep -n 'assertRefusedWith("function Obj:method()"' src/test`, is *not*
`git grep -n 'function Obj:method()' src/test`: that wider grep also returns
[LuaDeclarationSiteTest.kt:85](../../../../src/test/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSiteTest.kt),
where the same text is a **Lua fixture** (`configure("Obj = {}\nfunction Obj:method() end\n")`) and
not an assertion about any message. That file is out of scope for this phase and must not be edited.

- [x] `LuaRenameTest.testColonMethodDeclarationIsRefused`
      ([:597-606](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameTest.kt)).
      Its fixture is `Obj = {}` / `function Obj:<caret>m() end` / `local o = Obj` / `o:m()`. An
      aliased receiver reaches no declaration ([[NAV-13]] Out of Scope), so `o:m()` is an undecided
      occurrence: rewrite the test to assert a **conflict** and a byte-identical file, and rename it
      to say so. Keep the `myFixture.checkResult(source)` assertion verbatim — it is what makes the
      case worth having.
      **Done** as `testColonMethodDeclarationWithAnAliasedCallSiteReportsAConflict`: it catches
      `ConflictsInTestsException` and asserts the message names *why* the site cannot be rewritten
      (`cannot be bound to a declaration`, the `colonMethod.undecidedCall` key) rather than merely
      that something was reported. `myFixture.checkResult(source)` is kept verbatim, and the KDoc
      now records that the surviving hazard is the **half-applied** rename, not the refusal.
- [x] `LuaRenameTest.testSelfInsideAMethodIsRefusedAsTheMethod`
      ([:402-412](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameTest.kt)).
      Keep both `TargetElementUtil` assertions — they pin `LuaScopeProcessor`'s behaviour and are
      unaffected — and replace `assertRefusedWith("function Obj:method()", target)` with a drive
      through `myFixture.renameElementAtCaret`, asserting the caret-guard message. The KDoc's claim
      that "there is no `self` guard and there must not be one" is superseded by `design.md` §3.6
      and must be rewritten with it; `function T.m(self, x)` still renames normally because its
      `kindOf` is `PARAMETER` and the guard is inside the `METHOD_FUNCTION` arm (`REFACT-01` TC-19c
      is the standing gate).
      **Done**, method name unchanged (four docs outside this feature cite it, and the name stays
      true — `self` is still refused, and part (a) still resolves it as the method). Both
      `TargetElementUtil` assertions are kept verbatim; part (b) now drives
      `myFixture.renameElementAtCaret` and asserts `'self' is not the method name` plus a
      byte-identical file. The superseded KDoc paragraph is rewritten against `design.md` §3.6.

**Two further methods, named in Phase 2's measurement, are repaired here.** Both pin routes that
still exist — a colon call site's rename and the handler the registry offers — so both are rewritten
against the new behaviour rather than deleted:
- [x] `LuaColonCallRenameRefusalTest.renamingAColonCallSiteIsRefusedInsteadOfRetargetingASameNamedLocal`
      → `renamingAColonCallSiteTargetsTheMethodInsteadOfASameNamedLocal`. The case was always about
      *which element* a rename at `t:m()` would rewrite, and `REFACT-09-02` requires it to succeed,
      so the `expectThrows` is replaced by an assertion on the substituted leaf: offset **24**, the
      `m` of `function t:m()`, and not the local `m` at 38. That keeps `requirements.md` #1's
      mutation (delete the colon branch from `LuaNameReference.multiResolve`, measured returning
      `LeafPsiElement@38 'm'`) reachable from this case — a bare refusal could not distinguish it.
      The end-to-end rewrite is row 2's and stays in `LuaColonMethodRenameTest`; this fixture is the
      only one carrying a same-named local.
- [x] `LuaColonCallRenameHandlerTest.invokingTheOfferedHandlerRefusesAndStartsNoTemplate`
      → `invokingTheOfferedHandlerRenamesOnlyTheMethodAndItsCallSite`. What this class exists to
      catch is the **in-place template**, not the refusal, so the case is rewritten into its positive
      form: the two `t:m` sites move, the local function `m` and its `m()` call do not, and
      `getTemplateState` stays null. The new name is supplied through `UiInterceptors`, the seam
      `LuaRenameTest.renameViaSelectedHandler` already uses — **without it the platform's unit-test
      branch sorts `dialog.getSuggestedNames()` and picks the element's own name**
      (`PsiElementRenameHandler.java:207-224`), renaming `m` to `m` and stopping on a degenerate
      self-collision (`This table already has a member named 'm'`, the message Phase 3 recorded).
      That message is a fact about the harness's name selection, not about Lunar, and is not
      asserted.

**Verification (Phase 4):**
- [x] `test --rerun --no-build-cache` — the **full** suite, 0 failures, 0 errors. An isolated
      `--tests '*LuaRenameTest*'` does not discharge this.
      **487 classes, 3103 tests, 0 failures, 0 errors, 1 skipped** — the same 3103 as Phase 3, since
      this phase rewrote tests and added none. Freshness checked rather than assumed: all 487
      result XMLs carry a `timestamp` inside the run window `22:31:47Z`-`22:40:51Z` (min
      `22:32:09.520Z`, max `22:40:49.198Z`), so no stale file was read.
- [x] `git grep -n 'assertRefusedWith("function Obj:method()"' src/test` returns nothing — no test
      still asserts the deleted message. **Confirmed** (exit 1, no output); `assertRefusedWith`
      itself survives, still used by the three `receiver part of a function name` cases.
- [x] `git grep -n 'rename\.colonMethod=' src/main/resources` returns nothing, and every key
      `design.md` §7.2 names is present. **Confirmed** — all seven keys present.
- [x] `git grep -n 'function Obj:method()' src/test` returns **exactly** the
      `LuaDeclarationSiteTest.kt` fixture line. A second hit means an assertion was missed; zero hits
      means the fixture was edited out of scope. **Confirmed** — one hit, `:85`, and that file is
      unedited and green (6 tests, 0 failures).
- [x] `ktlintCheck` alone (never paired with `ktlintFormat` — BUG-445): BUILD SUCCESSFUL.
- [x] No production file is in this phase's diff: three files, all under `src/test/`.

### Phase 5: re-measure and verify live [Must]

- [x] Re-run DR-01's reach instrument against the **shipped** `LuaColonMethodRename` rather than a
      transcription, over the pinned checkouts and the annotated substitute staged per DR-01
      Method point 6. Record the verdict table in `risks-and-gaps.md` beside the planning figures.
      The denominators must reproduce (734 / 941 / 14 116 and 195 / 268 / 2 446); a change in them
      means the scope moved, not the code. The verdict columns that must reproduce are
      DR-01 Finding 2's re-measured ones — corpus `accepted` 20, `acceptedNoCallSites` 120,
      `blocked` 801; substitute 50 / 44 / 174 — and the sole-blocker decomposition beside them.
      Classify by **blocker set**, not by first undecided occurrence: that classification depends on
      the order `CacheManager` returns candidate files and is not reproducible (DR-01 Finding 2).
      **Done.** All three denominators and **every** verdict column reproduce, including the
      sole-blocker decomposition — recorded in `risks-and-gaps.md` beside the planning figures. One
      figure missed on the first run and it was the instrument, not the code: filtering by Lua *file
      type* rather than the `.lua` extension read 851 corpus files against 734, with every other
      column identical. Corrected and re-run; 734 reproduced.
- [x] Close Gap 2.5: run both instruments and **name** the ZeroBrane declaration on which they
      disagree, or record that they now agree.
      **They now agree** — 0 disagreements on all five trees, ZeroBrane's 572 declarations included,
      over three independent passes. There is no declaration to name. The old 99/98 pair was on the
      superseded occurrence set and was not re-run, so whether the `field` clause absorbed a real
      split or the superseded instrument produced an artifact is not established; Gap 2.5 says so.
- [x] `test -PwithCorpus --rerun --no-build-cache` — no `Corpus regression:` line. Check the
      `timestamp` attributes of `LuaCorpusSweepTest`'s XMLs fall inside the run window; `--rerun`
      does not clear `build/test-results/test/` when a task is skipped.
      **Green — 491 classes, 3113 tests, 0 failures, 0 errors, 1 skipped** in 22 m 06 s. Freshness
      was checked the hard way rather than by reading the console, because the gate's own failure
      mode is a task that did not run: **all 491** result XMLs were parsed and every `timestamp`
      falls inside the window, 0 outside, and `LuaCorpusSweepTest` shows all four members
      (`testLuacheckCorpus`, `testLuarocksCorpus`, `testPenlightCorpus`, `testZerobraneCorpus`) at
      `tests="4" failures="0" errors="0"`. No `Corpus regression:` and no `[corpus] IMPROVED` — no
      baseline was re-recorded. The sweep's own `files=` counts (132/159/56/72) are its narrower
      `roots` scope and are **not** comparable with the reach instrument's whole-tree denominators.
- [x] `ktlintFormat` on the VM → rsync the `.kt` files back → `ktlintCheck` alone.
      Run as three separate invocations, never the paired one (BUG-445). `ktlintFormat` rewrote
      nothing — the rsync back left `git status -- src/` empty — and `ktlintCheck` alone is green.
      Expected: this phase changes no Kotlin, and the instrument was reverted before either ran.
- [ ] Work `human-verification-checklists.md` in the containerised GoLand. The conflicts dialog and
      the refusal balloons are the surfaces no headless test can see.
      **Deliberately not done by the Phase 5 implementor, and its 38 boxes are all still unticked.**
      The builder cannot serve two `run` invocations at once, and this phase held it for ~35 minutes
      across the reach re-runs, the corpus lane and the full suite. V7 is the supervisor's step, and
      `requirements.md`'s front-matter stays `in_progress` until it is worked.
- [x] Update `REFACT-01-08` to `Full` and delete REFACT-09's roadmap row (`docs/roadmap.md`).
      Both done; REFACT-01's "two rows at `Partial`" prose is corrected to one at the same time.
- [x] `CHANGELOG.md` entry under the current milestone (`[0.21]`).

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
