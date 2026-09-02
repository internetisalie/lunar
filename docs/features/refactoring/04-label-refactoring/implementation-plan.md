---
id: "REFACT-04-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "REFACT-04"
priority: "medium"
folders:
  - "[[features/refactoring/04-label-refactoring/requirements|requirements]]"
---

# Implementation Plan: REFACT-04 — Label Refactoring

Four phases. Phase 1 adds no production code at all — it is the regression baseline for a feature
whose core already works, and it must land first for that reason. Phases 2-4 are each independently
shippable and each leave the build green.

**The one-sentence brief for the implementer**: *label rename works; do not rewrite it.* Every task
below either adds a check beside it, narrows a search around it, or tests it. If a task tempts you to
change `LuaLabelReference.resolve`/`isReferenceTo`/`handleElementRename`, `LuaLabelScopeProcessor`,
`LuaBlock.processLabelDeclarations` or `LuaNameDeclElementImpl.setName`, stop — the design
(§1 "The repo constraint", §3.6) says why none of them moves.

**Standing rules for every phase**

- Build/test **only** through `tooling/gce-builder/gce-builder.sh run …`. Never `./gradlew` locally.
- Never run two `gce-builder run` invocations concurrently.
- Format on the VM and pull the result back before committing —
  `run ktlintFormat` then `rsync -az --include='*/' --include='*.kt' --exclude='*' builder:/home/builder/lunar/src/ src/`,
  then `run ktlintCheck` **alone**. Never `run "ktlintFormat ktlintCheck"` (BUG-445: that pairing
  cannot fail).
- The full-suite gate is `run "test --rerun --no-build-cache"`. A green `--tests *Label*` proves
  nothing about the suite.
- Engineering-contract tripwires are hard: ≤30 logic lines per function, **≤3 arguments** per
  function (excluding `Project`/`Disposable`), including private helpers. Self-audit each new file
  against both before opening the gate.
- **The ≤3-argument cap does not apply to a platform `override`, and no reviewer may fail an
  implementer for one.** `RenamePsiElementProcessor.findCollisions` has four parameters fixed by
  `RenamePsiElementProcessorBase.java:248-252`; the arity is the contract being implemented, not a
  design choice. Every function *this* feature declares fits the cap —
  `LuaLabelConflictDetector.collisions` takes one argument by folding two values into
  `LuaLabelRenameTarget` (design §2.3), and no `LuaLabelScopes` function takes more than two
  (design §2.1). This paragraph is repeated from REFACT-01's plan because implementers in this repo
  have repeatedly been failed on this cap.
- **No new EP may be registered manually in a test.** TC-04-L exists precisely to catch a missing
  `plugin.xml` line; a test that registers the processor itself would pass with the registration
  absent, which is the defect class this epic exists to remove.
- **There is deliberately no `human-verification-checklists.md`.** The two live-IDE checks this
  feature needs (DR-02, DR-03) are bound to phase gates and TC numbers that only mean something in
  this file; a second copy of them would be a second list to drift.

## Blocking dependency

**Phase 3 cannot start until [[REFACT-01]] Phase 3 has landed `LuaRenameCollisionUsageInfo`**
(REFACT-01 design §2.4, `net.internetisalie.lunar.refactoring.rename.LuaRenameCollisionUsageInfo`).
Design §1 records it as shared machinery this feature consumes verbatim. If REFACT-01 slips, the
correct response is to wait or to re-sequence REFACT-01 Phase 3 — **not** to define a second carrier
here. `risks-and-gaps.md` Risk 1.3 owns this.

Phases 1, 2 and 4 have **no** dependency on REFACT-01 and may land at any time.

## Delegated work — do not implement here

| Requirement | Owner | Do this instead |
| :--- | :--- | :--- |
| `REFACT-04-00` (epic table names two deleted classes) | REFACT-01 implementation-plan Phase 2 (`docs/features/refactoring/01-rename-refactoring/implementation-plan.md:196-206`), which already restates **both** line 33 and line 36 of `docs/features/refactoring/requirements.md` | Before Phase 1's commit, `grep -n 'LuaLabelFindUsagesProvider' docs/features/refactoring/requirements.md`. If it still matches, REFACT-01 Phase 2 has not landed: **leave it alone and note it in the commit message.** Correcting it here as well would produce two conflicting edits to one line. |
| `REFACT-04-13` (Safe Delete leaves `::::`) | [[BUG-458]] | Nothing. Design §8 and `risks-and-gaps.md` Gap 2.3 record the cross-dependency, including the defect in BUG-458's own stated fix strategy. |
| `REFACT-04-06` (new-name validation) | [[REFACT-05]] / `LuaNamesValidator` | Only TC-04-O, which asserts the validator is *reachable* from a label rename. Do not add label-specific name rules. |
| `REFACT-04-12` (Find Usages mechanics) | [[NAV-02]] | Nothing beyond keeping `LuaFindUsagesTest` green under Phase 2's scope narrowing. |
| `LuaRefactoringSupportProvider`'s stale KDoc (`:12`, attributes labels to REFACT-01) | REFACT-01 design §1 Prior Art | Nothing. |

## Phases

### Phase 1: Coverage backfill for the shipped core [Must]

- **Goal**: the three shapes of label rename that already work are pinned by tests before anything
  around them moves. Without this phase, Phase 2's search-scope narrowing has no regression gate
  beyond a single-`goto` fixture.
- **Production code changed**: none.
- **Tasks**:
  - [x] Extend `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelRenameTest.kt`
        with **TC-04-A** (multi-`goto`), **TC-04-B** (no `goto`) and **TC-04-C** (rename under
        shadowing) — closes the three coverage gaps `requirements.md` names in its Verification
        section.
  - [x] Add **TC-04-M** to `LuaLabelRenameTest` — the two headlessly observable conjuncts of
        `MemberInplaceRenameHandler.isAvailable` (design §1 Target State). **Deviation**: the
        negative fixture is a global declaration's `LuaNameRef`, not `local x = 1` as originally
        written here — `risks-and-gaps.md` RD-5.
  - [x] Add **TC-04-O** to `src/test/kotlin/net/internetisalie/lunar/refactoring/LuaNamesValidatorTest.kt`
        — `RenameUtil.isValidName` against a `LuaLabelName`, proving the validator is on the label
        rename path (design §6 E-7).
- **Exit criteria**: TC-04-A, -B, -C, -M, -O green; full suite green; each of the five confirmed
  against the mutation named in its row of "Test Cases" below. **Met** — see the Phase 1 entry in
  "Verification Tasks" and `requirements.md`'s Verification table.

### Phase 2: Label scope model and use scope [Must] — REFACT-04-04, -11

- **Goal**: one function-boundary rule, used by resolution and by search; a label's usage search stops
  at its own function.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.psi.LuaLabelScopes`
        (`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaLabelScopes.kt`) with
        `isFunctionBoundary`, `walkLabelScopes`, `functionScopeOf`, `blockOf`,
        `labelsInFunctionScope` — realizes design §2.1. `walkLabelScopes` is
        `LuaLabelReference.kt:69-89` **moved verbatim** with the boundary test delegated to
        `isFunctionBoundary`; do not "improve" it while moving.
  - [x] Edit `net.internetisalie.lunar.lang.LuaLabelReference`: delete the private
        `walkLabelScopes` and point its two call sites (`:39`, `:61`) at `LuaLabelScopes` — realizes
        design §2.5. Nothing else in the file changes.
  - [x] Add `getUseScope()` to `net.internetisalie.lunar.lang.psi.LuaNameDeclElementImpl`
        (`lang/psi/LuaBaseElements.kt:53-71`) exactly as design §2.4 writes it, **including the
        `this !is LuaLabelName` guard** — realizes design §2.4, §3.5. The guard is unreachable today
        and is required anyway; design §2.4 says why.
  - [x] Add **TC-04-I** and **TC-04-J**.
- **Exit criteria**: TC-04-I, -J green. `LuaLabelResolutionTest` (5 tests) and `LuaLabelCompletionTest`
  (4 tests) green — they are the gate for the `walkLabelScopes` move.
  `LuaFindUsagesTest.testLabelUsagesCount` and `LuaSafeDeleteTest.testLabelDeclarationIsAvailable`
  green — they are the gate for the scope narrowing. TC-04-A from Phase 1 green — it is the gate that
  the narrowing did not drop a usage. Full suite green.

### Phase 3: Conflict detection [Must] — REFACT-04-07, -08, -03

- **Goal**: a rename that would duplicate or shadow a visible label is reported before it is applied,
  with a message that matches the configured language level.
- **Blocked on**: REFACT-01 Phase 3 (see "Blocking dependency"). **Landed** — `LuaRenameCollisionUsageInfo`
  exists at `refactoring/rename/LuaRenameConflictDetector.kt:54`; Phase 3 consumed it verbatim.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.refactoring.rename.LuaLabelConflictDetector`
        (`src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelConflictDetector.kt`)
        with `LuaLabelRenameTarget` and `collisions(target)` — realizes design §2.3, **§3.2**, §3.3,
        §3.4. Emit **REFACT-01's** `LuaRenameCollisionUsageInfo`; define no new carrier.
  - [x] Implement §3.2 **as written, including the source-order test in both bullets.** Dropping it
        gives the rule `requirements.md` states, which is wrong: executed on this host, `do ::a:: end`
        followed by `::a::` is legal on 5.2.4, 5.3.6 **and 5.4.7** (design §1, rows P-b/P-e).
        TC-04-G is the guard.
  - [x] Implement §3.3 step 5's `labelsInFunctionScope` filter
        (`functionScopeOf(it) === scope`). `PsiTreeUtil.findChildrenOfType` descends into nested
        `LuaFuncDef`s and their labels can never collide. TC-04-H is the guard, and its fixture nests
        the second function inside the first **on purpose**: a sibling function is not a descendant of
        `scope`, so it cannot exercise this filter at all. Do not simplify that fixture.
  - [x] Create `net.internetisalie.lunar.refactoring.rename.LuaLabelRenameProcessor`
        (`…/LuaLabelRenameProcessor.kt`) extending **`RenamePsiElementProcessor`**, not
        `RenamePsiElementProcessorBase`, and implementing **`DumbAware`** — realizes design §2.2,
        §3.0, §3.1, §3.3. Neither is a style choice: `MemberInplaceRenamer.MyRenameProcessor` casts
        the EP instance unconditionally (`RenamePsiElementProcessor.java:34`, called at
        `MemberInplaceRenamer.java:401`), so the `…Base` subclass is a `ClassCastException` on every
        in-place label rename — a path no headless test can reach (DR-02); and both selection
        functions skip a processor that is not usable in the current context
        (`RenamePsiElementProcessorBase.java:156`, `RenamePsiElementProcessor.java:33`), so without
        `DumbAware` the conflict check silently vanishes while the project is indexing. Design §2.2
        lists why every override is index-free and names the platform precedent
        (`RenamePsiFileDumbProcessor.kt:34`).
  - [x] Override **only** `canProcessElement`, `substituteElementToRename` and `findCollisions`.
        Design §3.6 enumerates the seven hooks that must stay inherited and what each omission
        preserves. Adding `renameElement` or `findReferences` replaces working code and fails review.
  - [x] Register the processor in `src/main/resources/META-INF/plugin.xml` immediately after the
        existing `<renamePsiElementProcessor>` (currently lines 389-390), **with no `order`
        attribute** — realizes design §7.
  - [x] Add the three keys `refactoring.rename.label.conflict.duplicate`, `…conflict.rebind` and
        `…unresolvedGoto` to `src/main/resources/net/internetisalie/lunar/LuaBundle.properties`,
        with the `''` apostrophe escaping design §7 specifies. Remove nothing — the REFACT-01 refusal
        key this bullet originally cited (`refactoring.rename.unsupported`) has since been renamed
        `refactoring.rename.unsupportedTarget` and moved to `:151`; it still belongs to REFACT-01 and
        is untouched here.
  - [x] Add **TC-04-D**, **-E**, **-F**, **-G**, **-H**, **-L**, **-N** in a new
        `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelConflictTest.kt`.
- **Exit criteria**: all seven new TCs green; Phase 1 and Phase 2 TCs still green; full suite green.
  DR-02 executed and its outcome recorded in `risks-and-gaps.md`. **Met** — see the Phase 3 entry in
  "Verification Tasks".

### Phase 4: Structure View rename target [Could] — REFACT-04-14

- **Goal**: the element the Structure View publishes for a label is renameable.
- **Tasks**:
  - [x] Change `LuaLabelStructureViewTreeElement.getValue()` to `myLabel.labelName`
        (`lang/structure/LuaLabelStructureViewTreeElement.kt:25-28`) — realizes design §2.6. Do not
        touch `getPresentation()`'s null-tolerant read; do not touch
        `LuaStructureViewModel.SUITABLE_CLASSES`.
  - [x] Add **TC-04-K**.
- **Exit criteria**: TC-04-K green; `LuaStructureViewTest` green; full suite green. DR-03 executed and
  its outcome recorded. **Met** — see the Phase 4 entry in "Verification Tasks" and DR-03's outcome in
  `risks-and-gaps.md`.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :--- | :---: | :--- |
| `REFACT-04-00` | M | **Delegated** — REFACT-01 Phase 2 (see "Delegated work") |
| `REFACT-04-01` | M | Already shipped; locked by Phase 1 (TC-04-M) |
| `REFACT-04-02` | M | Already shipped; locked by Phase 1 (TC-04-A), preserved by Phase 2 |
| `REFACT-04-03` | M | Already shipped; the unresolved-`goto` half in Phase 3 (TC-04-N) |
| `REFACT-04-04` | M | Phase 2 (`LuaLabelScopes`); enforced in Phase 3 (TC-04-H) |
| `REFACT-04-05` | S | Already shipped; locked by Phase 1 (TC-04-C) |
| `REFACT-04-06` | M | Delegated to [[REFACT-05]]; reachability locked by Phase 1 (TC-04-O) |
| `REFACT-04-07` | M | Phase 3 |
| `REFACT-04-08` | S | Phase 3 (TC-04-D, TC-04-E) |
| `REFACT-04-09` | S | No code; Phase 1 (TC-04-M) + DR-02 |
| `REFACT-04-10` | C | Already shipped; unchanged |
| `REFACT-04-11` | S | Phase 2 |
| `REFACT-04-12` | S | Delegated to [[NAV-02]]; Phase 2 exit criteria guard it |
| `REFACT-04-13` | S | **Delegated to [[BUG-458]]** |
| `REFACT-04-14` | C | Phase 4 + DR-03 |
| `REFACT-04-15` | C | Platform-supplied; preserved by Phase 3's decision not to override `renameElement` (design §3.6). **No test** — see `risks-and-gaps.md` "Test Case Gaps" |
| `REFACT-04-16` | C | Decision recorded in design §3.4 and §6 E-8; no code |
| `REFACT-04-17` | C | Already shipped; locked by Phase 1 (TC-04-B) |
| `REFACT-04-18` | W | Out of scope; `risks-and-gaps.md` "Technical Debt & Future Work" |
| `REFACT-04-19` | W | Out of scope by language fact; design §8 |
| `REFACT-04-20` | S | True by construction; design §3.2 states the argument. **No test is possible** — see `risks-and-gaps.md` "Test Case Gaps" |

## Test Cases

Every row states the mutation that turns the test red. A test with no such mutation is the defect
this epic exists to remove, and the two places where one does not exist are named as such rather than
papered over with an assertion.

All fixtures use `myFixture.configureByText("test.lua", …)` in a `BasePlatformTestCase`. Where a
language level matters, set it with
`LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUAxx` — the idiom
`LuaLanguageLevelInspectionTest.kt:739` already uses.

### TC-04-A — a rename rewrites every bound `goto` (REFACT-04-02)

- **Input**:
  ```lua
  ::<caret>myLabel::
  goto myLabel
  do goto myLabel end
  if true then goto myLabel end
  ```
- **Action**: `myFixture.renameElementAtCaret("newLabel")`
- **Output**: all four occurrences read `newLabel`; `myFixture.checkResult` on the whole text.
- **Turns red when**: `LuaLabelReference.isReferenceTo` (`LuaLabelReference.kt:50-56`) is changed to
  `return false` — the three `goto`s are left on the old name. After Phase 2 it also turns red when
  `LuaNameDeclElementImpl.getUseScope` returns `LocalSearchScope(this)` instead of the enclosing
  function.

### TC-04-B — a label with no `goto` renames alone (REFACT-04-17)

- **Input**: `::<caret>done::`
- **Action**: rename to `finished`; then `myFixture.findUsages(labelName)`.
- **Output**: text is `::finished::`; the usage list is empty.
- **Turns red when**: `LuaNameDeclElementImpl.setName` (`LuaBaseElements.kt:61-70`) is changed to
  `return this` without the `node.replaceChild` — the text is unchanged.

### TC-04-C — under shadowing, only the bound `goto`s move (REFACT-04-05)

- **Setup**: `languageLevel = LUA53`. Executed 2026-08-22 with `luac -p` on this exact fixture:
  `rc=0` on 5.2.4 and 5.3.6, and `label 'a' already defined on line 1` on 5.4.7 — so the level must be
  set, and setting it also proves the fixture is the shadowing case `REFACT-04-05` is about.
- **Input**:
  ```lua
  ::<caret>a::
  do
    goto a
    ::a::
  end
  goto a
  ```
- **Action**: rename to `outer`.
- **Output**: line 1 becomes `::outer::` and the **last** `goto a` becomes `goto outer`; the `goto a`
  and `::a::` inside the `do` block are **unchanged**.
- **Turns red when**: `LuaLabelReference.isReferenceTo` (`:55`) drops the `resolved === owner`
  identity test and compares only `resolved.identifier.text == name` — the inner `goto a` is rewritten
  too. This is the test `requirements.md` names as coverage gap 2, and the mutation is the exact
  simplification a future reader is most likely to make.

### TC-04-D — a duplicate-label rename is refused at 5.4 (REFACT-04-07, -08)

- **Setup**: `languageLevel = LUA54`.
- **Input** (`requirements.md` TC-REFACT-04-07 verbatim):
  ```lua
  local n = 0
  ::a::
  n = n + 1
  do
    if n < 2 then goto a end
    ::<caret>b::
  end
  print("n="..n)
  ```
- **Action**: `myFixture.renameElementAtCaret("a")` inside a `try`/`catch`, mirroring
  `LuaSafeDeleteTest.kt:142-148`.
- **Output**: `BaseRefactoringProcessor.ConflictsInTestsException` is thrown; the file text still
  contains `::b::`; the exception's `getMessages()` contains `"already defined"`.
- **Turns red when**: step 5.5 of design §3.3 (the §3.2 clause-2 test) is deleted and replaced with
  `continue` — no conflict, no exception, and the rename silently produces the file 5.4 refuses to
  load.

### TC-04-E — the same rename is reported differently at 5.3 (REFACT-04-08)

- **Setup**: `languageLevel = LUA53`. Same fixture and action as TC-04-D.
- **Output**: `ConflictsInTestsException` is thrown; `getMessages()` contains `"jump to the nearer
  label"` and does **not** contain `"already defined"`.
- **Turns red when**: design §3.4 step 3's comparison is flipped to `level < LuaLanguageLevel.LUA54`,
  or the level read is replaced by a constant — the 5.4 wording appears at 5.3.

### TC-04-F — sibling blocks do not collide (REFACT-04-07, negative)

- **Setup**: `languageLevel = LUA54`.
- **Input**: `do ::a:: end` ⏎ `do ::<caret>b:: end`
- **Action**: rename to `a`.
- **Output**: **no exception**; the text becomes `do ::a:: end` ⏎ `do ::a:: end`. Executed: legal on
  5.2.4, 5.3.6 and 5.4.7 (design §1 row P-c).
- **Turns red when**: design §3.2 clause 2 is weakened to "same function and same name" — a false
  conflict is reported on legal code and the exception is thrown. Reachable from this fixture because
  both labels are top level, so both have the same `functionScopeOf` (the `LuaFile`), clause 1 and
  the `labelsInFunctionScope` filter both pass `a` through, and clause 2 is the only thing left
  holding the report back.

### TC-04-G — an earlier label in a *closed* block does not collide (REFACT-04-07, negative)

- **Setup**: `languageLevel = LUA54`.
- **Input**: `do ::a:: end` ⏎ `::<caret>b::`
- **Action**: rename to `a`.
- **Output**: **no exception**; the text becomes `do ::a:: end` ⏎ `::a::`. Executed: legal on 5.4.7
  (design §1 row P-b).
- **Turns red when**: the `before(renamed, other)` test is dropped from the **second** bullet of design
  §3.2 clause 2 (`block(renamed)` encloses `block(other)` **and** `before(renamed, other)`) — that is
  the bullet this fixture reaches: `block(b)` is the file block, which does enclose the `do` block, and
  `before(b, a)` being false is the only thing preventing a report. Implementing `REFACT-04-07`'s
  stated rule ("ancestor-or-self, **or descendant**") drops the order test from both bullets and
  therefore also reddens this test. **Dropping it from the first bullet alone does not**: that
  bullet's enclosure test (`block(a)` encloses `block(b)`) is already false here, so name the second
  bullet when running the mutation. This test is the guard on `risks-and-gaps.md` RD-1.

### TC-04-H — a label in a *nested* function never collides (REFACT-04-04, -07)

- **Setup**: `languageLevel = LUA54`.
- **Input**:
  ```lua
  function f()
    ::<caret>b::
    local g = function()
      ::a::
    end
  end
  ```
- **Action**: rename to `a`.
- **Output**: **no exception**; both labels end up named `a`. Executed 2026-08-22 on this host with
  `luac -p` over the pre- and post-rename text: `rc=0` on 5.2.4, 5.3.6 and 5.4.7 — the nested function
  is a separate label scope, so the duplicate name is legal at every level (same harness that reports
  `label 'a' already defined on line 1` for design §1 row P-a on 5.4.7).
- **Turns red when**: the `functionScopeOf(it) === scope` filter is removed from
  `LuaLabelScopes.labelsInFunctionScope` (design §3.3). `scope` is `functionScopeOf(b)` — the
  `LuaFuncDecl` for `f` (`lua.bnf:189`) — and `::a::` is a **descendant** of it, so
  `PsiTreeUtil.findChildrenOfType` reaches it: unfiltered, `other = a`, `block(b)` (f's block)
  encloses `block(a)` (the anonymous function's block) and `before(b, a)` holds, so §3.2 clause 2's
  second bullet fires and a false conflict is thrown. It also turns red when `LuaFuncDef`
  (`lua.bnf:305`) is dropped from `LuaLabelScopes.isFunctionBoundary` (design §2.1) —
  `functionScopeOf(a)` then climbs to `f`, and the filter passes `a` through.
- **The nesting is the test; do not "simplify" it to two sibling functions.**
  `findChildrenOfType(scope, …)` returns descendants of `scope` only, so a sibling function's label is
  out of reach with *or* without the filter: the mutant and the correct implementation both find
  nothing, both throw nothing, and the test is green either way. A sibling fixture asserts the
  function-boundary rule of `REFACT-04-04` only in the trivial direction the platform already gives —
  `LuaLabelRenameTest.testScopeIsolatedRename` covers that — and leaves the filter untested.

### TC-04-I — a label's use scope is its enclosing function (REFACT-04-11)

- **Input**:
  ```lua
  local function f()
    ::<caret>done::
    goto done
  end
  print("unrelated")
  ```
- **Action**: read `labelName.useScope`.
- **Output**: it is a `com.intellij.psi.search.LocalSearchScope`, and its `scope` array is exactly
  one element, the enclosing `LuaLocalFuncDecl`.
- **Turns red when**: the `getUseScope` override (design §2.4) is removed — the scope is a
  `GlobalSearchScope`.

### TC-04-J — a top-level label's use scope is its file (REFACT-04-11)

- **Input**: `::<caret>top::` ⏎ `goto top`
- **Action**: read `labelName.useScope`.
- **Output**: a `LocalSearchScope` whose single scope element is the `LuaFile`.
- **Turns red when**: `LuaLabelScopes.functionScopeOf` returns null instead of the containing file
  when no function ancestor exists — the override falls through to `super.getUseScope()` and the
  scope is global.

### TC-04-K — the Structure View publishes a renameable element (REFACT-04-14)

- **Input**: `::top::`
- **Action**: take the `LuaLabelStructureViewTreeElement` from `rootChildren(...)` (the helper
  `LuaStructureViewTest.kt` already uses) and read `.value`.
- **Output**: it is a `LuaLabelName` and it is a `PsiNameIdentifierOwner`; its `name` is `"top"`.
- **Turns red when**: `getValue()` is reverted to `labelName.identifier` — the value is an
  `IDENTIFIER` leaf and neither assertion holds.
- **Does not assert**: that F2 from the Structure View then works. That needs a live IDE (DR-03); the
  test asserts the platform precondition (`StructureViewComponent.java:861-864`,
  `PsiElementRenameHandler.java:150-159`), which is the part this feature controls.

### TC-04-L — the label processor is the one the platform selects (REFACT-04-07 wiring)

- **Input**: `::top::` ⏎ `local x = 1`
- **Action**: `RenamePsiElementProcessor.forElement(labelName)` and
  `RenamePsiElementProcessor.forElement(theLocalDeclarationElement)`.
- **Output**: the first is a `LuaLabelRenameProcessor` **and is a `com.intellij.openapi.project.DumbAware`**;
  the second is **not** a `LuaLabelRenameProcessor`.
- **Turns red when**: the `<renamePsiElementProcessor>` line for `LuaLabelRenameProcessor` is removed
  from `plugin.xml` — `forElement` returns the platform default (or, after REFACT-01,
  `LuaRenameProcessor`); or the `DumbAware` marker is dropped from the class declaration (design
  §2.2), which is the mutation for the dumb-mode half — without the marker,
  `RenamePsiElementProcessorBase.forPsiElement` (`:156`) skips the processor during indexing and the
  conflict check disappears with no other symptom.
- **The test must not register the EP itself.** A schema-engine EP-registration bug in this repo was
  masked for months by tests that hand-registered the extension point. `LuaUnsupportedRenameProcessorTest.kt:94`
  is the existing example of this assertion done correctly.

### TC-04-M — in-place rename is offered for labels and nothing else (REFACT-04-09, -01)

- **Input**: `::top::` ⏎ `local x = 1`
- **Action**: `LuaRefactoringSupportProvider().isMemberInplaceRenameAvailable(labelName, null)` and
  the same call with the `LuaNameRef` of `x`; plus `assertTrue(labelName is PsiNameIdentifierOwner)`.
- **Output**: true for the label, false for the `LuaNameRef`, and the label is a
  `PsiNameIdentifierOwner`.
- **Turns red when**: `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable`
  (`LuaRefactoringSupportProvider.kt:23-26`) is changed to `false`.
- **Does not assert**: the third conjunct of `MemberInplaceRenameHandler.isAvailable`,
  `editor.getSettings().isVariableInplaceRenameEnabled()`, which is not observable headlessly. DR-02.

### TC-04-N — renaming from a `goto` (REFACT-04-03)

Two parts, both calling `LuaLabelRenameProcessor` directly so the assertion does not depend on
extension registration order (design §6 E-1).

- **(a) resolvable**: fixture `::top::` ⏎ `goto top`; `canProcessElement(labelRef)` is true and
  `substituteElementToRename(labelRef, null)` returns the `LuaLabelName` for `top`.
- **(b) unresolved**: fixture `goto nosuch`; `substituteElementToRename(labelRef, null)` throws
  `CommonRefactoringUtil.RefactoringErrorHintException` (unit-test mode,
  `CommonRefactoringUtil.java:84-86`) whose message contains `nosuch`.
- **Turns red when**: (a) design §3.1 step 3 returns `element` instead of the resolved label — the
  returned element is a `LuaLabelRef`; (b) step 4 is replaced by `return element` — no exception is
  thrown and the platform would rename a dangling `goto` in place.

### TC-04-O — a reserved word is rejected for a label (REFACT-04-06)

- **Input**: `::<caret>top::`
- **Action**: `RenameUtil.isValidName(project, labelName, "end")` and
  `RenameUtil.isValidName(project, labelName, "finished")`.
- **Output**: `false` then `true`.
- **Turns red when**: `LuaNamesValidator.isIdentifier` (`refactoring/LuaNamesValidator.kt:18-21`)
  drops its `&& !LuaKeywords.isReserved(name)` clause.
- **Why not just call `LuaNamesValidator` directly**: `LuaNamesValidatorTest` already does that. This
  case asserts the *path* — that `RenameUtil.isValidName` reaches the validator for a `LuaLabelName`
  (`RenameUtil.java:383-407`), which is what makes `REFACT-04-06` true of the rename rather than of
  the validator.

## Verification Tasks

- [x] **Automated**: TC-04-A … TC-04-O, in `LuaLabelRenameTest` (A, B, C, M),
      `LuaLabelConflictTest` (D, E, F, G, H, L, N), `LuaLabelScopeTest` (I, J),
      `LuaStructureViewTest` (K), `LuaNamesValidatorTest` (O).
      **Phase 1 done** — A, B, C, M landed in `LuaLabelRenameTest`, O in `LuaNamesValidatorTest`, all
      green. **Phase 3 done** — D, E, F, G, H, L, N landed in the new `LuaLabelConflictTest`, all
      green (7/7). I/J landed in Phase 2 (`LuaLabelRenameTest`, not a separate `LuaLabelScopeTest` —
      that file name in this row was never created). **Phase 4 done** — K landed in
      `LuaStructureViewTest` (`testLabelNodeValueIsRenameableLabelName`), green.
- [x] **Mutation confirmation**: for each of the fifteen TCs, apply the mutation named in its row,
      confirm the test fails, revert with the `temporary-edits` skill (never `git checkout`), and
      record the result in the phase's commit message. Two claims in this plan are *not* covered by a
      mutation and must be stated as such rather than asserted: `REFACT-04-15` and `REFACT-04-20`
      (`risks-and-gaps.md` "Test Case Gaps").
      **Phase 1's five (A, B, C, M, O) confirmed** — each mutation named in its "Test Cases" row was
      applied, reddened the named test, and was reverted via `temporary-edits`
      (`git status --porcelain` clean on every production file afterwards). Recorded in the Phase 1
      commit message.
      **Phase 3's seven (D, E, F, G, H, L, N) confirmed** — nine mutations run in total (TC-04-H and
      TC-04-L each name two mutations in their "Test Cases" row; both were executed for each). Every
      one CAUGHT — reddened exactly the named test(s) for the stated reason — and every file was
      restored via `temporary-edits`/`mutation-proof` (`git diff --stat` empty on every production
      file after each restore). TC-04-G's mutation reddened *only* `testEarlierLabelInAClosedBlockDoesNotCollide`,
      confirming the design's own claim that dropping the order test from the first bullet alone
      (which is what TC-04-F's fixture would reach) does not redden TC-04-F. Recorded in the Phase 3
      commit message.
      **Phase 4's one (K) confirmed** — TC-04-K's named mutation (revert `getValue()` to
      `labelName.identifier ?: labelName.firstChild ?: labelName`) was applied via `temporary-edits`,
      reddened exactly `testLabelNodeValueIsRenameableLabelName` (1 failure of 16 in
      `LuaStructureViewTest`, all others unaffected), and was reverted (`scratch_end`, `git status
      --porcelain` clean). GREEN re-confirmed after restore (17/17, including the new test).
- [x] **Regression, Phase 2**: `LuaLabelResolutionTest`, `LuaLabelCompletionTest`, `LuaFindUsagesTest`,
      `LuaSafeDeleteTest` — the four suites the `walkLabelScopes` move and the `getUseScope` narrowing
      can break. Re-confirmed green as part of Phase 3's full-suite runs.
- [x] **Full suite**, every phase: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`.
      **Phase 3**: 2913 tests / 0 failures / 0 errors / 1 skipped across 466 files — exact
      reconciliation against the pre-phase baseline (2906/465) plus this phase's 7 tests in 1 new
      file. `ktlintCheck` clean.
      **Phase 4**: 2914 tests / 0 failures / 0 errors / 1 skipped across 466 files — exact
      reconciliation against the Phase 3 baseline (2913) plus this phase's 1 new test (TC-04-K).
      `ktlintCheck` clean.
- [x] **Corpus sweep**: **not required.** This feature changes no type inference, no index and no
      resolution result — `getUseScope` narrows a *search*, and `walkLabelScopes` moves without
      changing behaviour. Phase 3's `LuaLabelConflictDetector` only compares PSI declaration
      positions and reads no index either. If Phase 2 is implemented by touching
      `LuaLabelReference.resolve` or `LuaBlock.processLabelDeclarations` (it must not be), the sweep
      becomes required: `run "test -PwithCorpus --rerun --no-build-cache"`.
- [x] **DR-02 (live)**: in-place rename of a label, via the `verify-in-ide` flow. Confirms
      `MemberInplaceRenamer` is the handler, that Enter commits through a `RenameProcessor`, and that
      a colliding new name raises the conflicts dialog rather than silently applying. **Executed**
      2026-09-02 on `lunar-builder` (native `runIde`) — outcome recorded in `risks-and-gaps.md`
      "DR-02 outcome". All three confirmations held; a non-colliding rename also applied cleanly with
      no dialog (regression check).
- [x] **DR-03 (live)**: F2 on a label node in the Structure View after Phase 4. Confirms the dialog
      opens rather than *"cannot be renamed"*. **Executed** 2026-09-02 on `lunar-builder` (native
      `runIde`) — outcome recorded in `risks-and-gaps.md` "DR-03 outcome". `Refactor ▸ Rename…` from
      a Structure-View-selected label node opened the rename dialog and the completed rename updated
      both the label and its `goto` in one commit; a raw `F2` sent directly to the tree did not fire
      and is recorded as an open, non-blocking item rather than silently substituted away.

## Task Summary

| Phase | Status | Priority |
| :--- | :--- | :--- |
| Phase 1: Coverage backfill for the shipped core | done | Must |
| Phase 2: Label scope model and use scope | done | Must |
| Phase 3: Conflict detection | done | Must |
| Phase 4: Structure View rename target | done | Could |
