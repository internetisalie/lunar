---
id: "REFACT-04-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "REFACT-04"
priority: "medium"
folders:
  - "[[features/refactoring/04-label-refactoring/requirements|requirements]]"
---

# REFACT-04: Risks & Gaps

Every other feature in this epic risks shipping something broken. This one risks **breaking something
that works** — label rename is the only rename in Lunar that does its job today. The risk register is
weighted accordingly: the first four risks are all about the blast radius of additive changes, and
the requirement defects are recorded because implementing `requirements.md` verbatim would produce a
false conflict on legal Lua.

## Critical Risks

### Risk 1.1: Regressing the one refactoring in the plugin that works

- **Impact**: total. `LuaLabelRenameTest`'s three rename cases are the only end-to-end evidence that
  any rename in this plugin is correct. A change that breaks them removes the plugin's only working
  refactoring and, because `LuaLabelReference` and `LuaNameDeclElementImpl` are also on the
  resolution and completion paths, can take label *binding* with it.
- **Likelihood**: medium. The two edits with real reach are the `walkLabelScopes` move (design §2.5)
  and the `getUseScope` override (design §2.4), and both look trivial.
- **Mitigation**:
  1. **Phase 1 lands no production code.** TC-04-A/-B/-C exist so that the multi-`goto`, zero-`goto`
     and shadowed shapes are pinned *before* Phase 2 touches anything. `requirements.md` names all
     three as coverage gaps found while writing it; that they are gaps is the reason the risk is
     medium and not low.
  2. Design §3.6 enumerates the seven platform hooks that must stay inherited, and the plan's Phase 3
     forbids overriding `renameElement`/`findReferences` explicitly. The rename itself is not new
     code and must not become new code.
  3. `walkLabelScopes` moves **verbatim**; `LuaLabelResolutionTest` (5 tests) and
     `LuaLabelCompletionTest` (4 tests) are its declared gate in the Phase 2 exit criteria.

### Risk 1.2: A conflict detector that reports on legal code

- **Impact**: a conflicts dialog on every innocuous rename trains users to click Continue, which
  destroys the value of the dialog for the case that matters (Example 2 in design §5) — a worse
  outcome than the current silence, because it is the current silence with extra clicks.
- **Likelihood**: **high if `REFACT-04-07` is implemented as written.** The requirement's rule
  ("ancestor-or-self, **or descendant**") is too wide in the descendant direction; executed, both
  descendant cases are legal on 5.4.7 (design §1 rows P-b, P-e). See RD-1.
- **Mitigation**: design §3.2 is the corrected rule and design §3.3 the exact loop; TC-04-F, TC-04-G
  and TC-04-H are three *negative* tests, one per way the rule can be widened, each with a named
  mutation **that its own fixture can reach** — which is the bar these three are held to, because a
  negative test whose mutation is unreachable is green either way and asserts nothing:
  - **TC-04-F** — sibling `do` blocks in one function. Both labels share a `functionScopeOf`, so the
    scope filter passes the other label through and **clause 2 is the only thing withholding the
    report**; weakening clause 2 to "same function, same name" reddens it.
  - **TC-04-G** — a closed `do` block before a file-level label. Clause 2's *second* bullet is the one
    this fixture reaches (the file block does enclose the `do` block), so `before(renamed, other)` is
    the only thing withholding the report; dropping the order test from that bullet — or implementing
    `REFACT-04-07` verbatim, which drops it from both — reddens it. Dropping it from the first bullet
    alone does not, and TC-04-G's row says so.
  - **TC-04-H** — a function **nested inside** the renamed label's function. `findChildrenOfType`
    returns descendants of `scope` only, so the nested label is reachable and the
    `functionScopeOf(it) === scope` filter is the only thing withholding the report. A *sibling*
    function would not be reachable at all and the mutation would be green either way; the fixture
    was changed to a nested one for exactly that reason, and both fixture and result are executed
    (`luac -p`, `rc=0` on 5.2.4 / 5.3.6 / 5.4.7).
  The detector runs on declarations only, never on `goto` text (design §6 E-9).

### Risk 1.3: Phase 3 starting before REFACT-01 has landed `LuaRenameCollisionUsageInfo`

- **Impact**: an implementer who finds the class missing writes a second one. Two collision carriers
  in one package, diverging bundle keys, and the "reference, do not duplicate" boundary REFACT-01's
  design §1 sets is broken on its first use.
- **Likelihood**: medium — REFACT-01 is `status: planned`, not `done`, and its Phase 3 is a `Should`.
- **Mitigation**: the plan states the dependency as a **blocking** precondition on Phase 3 alone, and
  records that the correct response to a slip is to wait or re-sequence REFACT-01 Phase 3, never to
  define a local carrier. Phases 1, 2 and 4 are independent of REFACT-01 and can absorb the wait.
- **Detection**: `ls src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameCollisionUsageInfo.kt`
  — REFACT-01 design §2.4 puts it in the same file as `LuaRenameConflictDetector`, so check for the
  class, not the filename.

### Risk 1.4: The `getUseScope` narrowing dropping a real usage

- **Impact**: silent. A `goto` outside the computed scope is simply not found, and the rename
  half-applies — the exact defect class [[BUG-457]] exists for. There is **no safety net**: a
  `LocalSearchScope` returned from `getUseScope` is passed to `findReferences` *unintersected*
  (`RenameUtil.java:129-131`), so it replaces `RenameProcessor`'s project scope rather than narrowing
  it. A too-narrow scope is not merely slower or partially wrong; it is the whole search.
- **Likelihood**: low. The scope is derived from `LuaLabelScopes.functionScopeOf`, which is the same
  boundary `LuaLabelReference.walkLabelScopes` uses to *resolve*, so a `goto` outside it could not
  have been bound in the first place (executed: a label is invisible from another function). The
  fallback when no function ancestor exists is the whole file, not nothing.
- **Mitigation**: TC-04-A (four usages, one of them inside a nested `do` block) is green in Phase 1
  *before* the narrowing and is re-run as a Phase 2 exit criterion; TC-04-J pins the file-level
  fallback; `LuaFindUsagesTest.testLabelUsagesCount` searches with no explicit scope and therefore
  exercises `getUseScope` directly.
- **Residual**: a `goto` inside a nested function that the resolver *would* bind is unreachable by
  construction, but if `walkLabelScopes` were ever changed to cross function boundaries, the scope
  and the resolver would drift. `LuaLabelScopes.isFunctionBoundary` being the single source of the
  rule (design §2.1) is the structural mitigation.

### Risk 1.5: Lua 5.5's duplicate-label rule assumed rather than observed

- **Impact**: at `LUA55` the conflict message would claim the file will not compile when it might.
  Low severity — a warning, not a refusal (design §3.4 uses one mechanism for both tiers) — but it is
  a claim the plugin makes to the user.
- **Likelihood**: low. `checkrepeated`/`findlabel` (`lua-5.4.7/src/lparser.c:1448-1455`, `:544-554`)
  is the mechanism, and 5.5 is a continuation of the 5.4 parser, not a rewrite.
- **Mitigation**: the tier test is `>= LUA54`, so 5.5 inherits the **stricter** message. A false alarm
  on a permissive version is recoverable; the reverse silently ships a broken file. **DR-01** is the
  check.

## Requirement Defects

`requirements.md` is input to this design and is not edited. Each defect below is recorded here with
the evidence, and each has a design section and a test that encodes the corrected behaviour.

### RD-1: `REFACT-04-07`'s conflict rule is too wide, and the widening reports on legal code

`REFACT-04-07` states:

> conflict iff another label of the new name is declared in a block that is an **ancestor-or-self, or
> descendant**, of this label's block **within the same function**

The descendant half is wrong. Executed on this host, 2026-08-22:

```
do ::a:: end        --  P-b   5.2.4 ok   5.3.6 ok   5.4.7 ok
::a::

do                  --  P-e   5.2.4 ok   5.3.6 ok   5.4.7 ok
  do ::a:: end
end
::a::
```

versus the ancestor direction, which does error:

```
::a::               --  P-a   5.2.4 ok   5.3.6 ok   5.4.7 "label 'a' already defined on line 1"
do ::a:: end
```

The rule is **directional**: the collision exists only when the label in the enclosing-or-same block
comes **first**. The mechanism is `findlabel` (`lua-5.4.7/src/lparser.c:544-554`), which scans only
labels already declared in still-open blocks of the current function — a block that has closed has
had its labels popped.

- **Corrected in**: design §3.2, implemented by design §3.3 step 5.5.
- **Guarded by**: TC-04-G, whose named mutation is precisely "drop the source-order test", i.e.
  implement the requirement verbatim.
- **Proposed requirement edit** (not applied): replace the rule sentence in `REFACT-04-07` with
  *"conflict iff another label of the new name is declared in the same function and one of the two
  is in an enclosing-or-same block of the other **and comes first in source order**"*, and add
  `do ::a:: end` / `::a::` to the executed matrix as a legal case.

### RD-2: `REFACT-04-07`/`-08` ask for a "blocking conflict" the platform cannot express truthfully

`REFACT-04-07`'s test case expects *"at level 5.4/5.5, a blocking conflict"* and *"at 5.2/5.3, a
warning"* — two severities. The IntelliJ rename pipeline has exactly one conflict surface:
`findCollisions`/`findExistingNameConflicts` both funnel into `RenameProcessor.preprocessUsages`
(`RenameProcessor.java:166-182`), which shows a conflicts dialog with **Continue**. There is no
severity parameter.

The one hook that can genuinely block is `renameInputValidator`, and it cannot say why: a false
`isInputValid` yields *"'a' is not a valid identifier"* (`LangBundle.properties:235`), and the
custom-message escape `RenameInputValidatorEx.getErrorMessage(newName, project)`
(`RenameInputValidatorEx.java:23-24`) is given no element, so it cannot name the colliding label, its
line, or the language level. Design §9 Alternative B is the full argument.

- **Resolved in**: design §3.4 — one mechanism, two messages. The 5.4+ message states that the file
  will not load; the 5.2/5.3 message states that a `goto` will rebind.
- **Guarded by**: TC-04-D and TC-04-E, which assert the two message texts against the two levels.
- **Proposed requirement edit** (not applied): reword `-07`'s expectation as *"a conflict the user
  must acknowledge, whose message states, at 5.4+, that the file will not compile"*, and note in
  `-08` that the level selects the wording, not the severity.

### RD-3: `REFACT-04-07`'s premise "no `renamePsiElementProcessor` is registered" is now false

`REFACT-04-07` says *"No `renamePsiElementProcessor` is registered, so `findExistingNameConflicts` is
the platform's empty default"*, citing `grep -rn 'RenamePsiElementProcessor\|renamePsiElementProcessor' src/`
as empty. It is not, and the registered class has since **changed identity**:

| When | Registered at `plugin.xml:389-390` | Treatment of the label pair |
| :--- | :--- | :--- |
| BUG-457 (`b2cb211c`) → REFACT-01 Phase 2 | `LuaUnsupportedRenameProcessor` — a blanket refusal | excluded `LuaLabelName` only, so it **claimed** `LuaLabelRef` and refused a `goto` as a side effect of the blanket refusal (the overlap window design §6 E-1 records) |
| REFACT-01 Phase 2 (`2026-08-23`) onwards | `LuaRenameProcessor` — a real rename | excludes **both** `LuaLabelName` and `LuaLabelRef` (REFACT-01 design §3.0 rule 1), so the whole label pair reaches the platform default |

`LuaUnsupportedRenameProcessor` and its test were **deleted** in that commit; do not write new work
against either. This section previously described the deleted class as the registered processor.

The **conclusion still holds for labels**, and now for a simpler reason: `LuaRenameProcessor`
excludes the label pair outright, so a label reaches `RenamePsiElementProcessorBase.DEFAULT`, whose
conflict hooks are empty (`RenamePsiElementProcessorBase.java:129-147`, `:248-252`). Only the stated
reason is stale.

**What the identity change costs REFACT-04**, and it is a real inheritance rather than a wording
fix: the old processor's incidental claim on `LuaLabelRef` was the only thing refusing a `goto` whose
label does not exist. With it gone, `goto miss<caret>ing` reaches the platform default, which
rewrites that one leaf and collects no usages — measured `goto missing` → `goto renamed`. REFACT-01
records this as its `risks-and-gaps.md` **Gap 2.11** and deliberately does **not** guard it (claiming
`LuaLabelRef` would put `LuaRenameProcessor` first in `forPsiElement`'s order for the label pair and
take REFACT-04's working rename away — the mutation `LuaRenameTest.testLabelsAreNotClaimed` exists to
redden). **REFACT-04 §3.1 owns the closure**, and its design already assumes this ordering.

- **Proposed requirement edit** (not applied): replace the parenthetical with *"the only registered
  processor, `LuaRenameProcessor` (`plugin.xml:389-390`), excludes both `LuaLabelName` and
  `LuaLabelRef`, so the whole label pair reaches the platform default, whose conflict hooks are
  empty — and an unresolved `goto` is renamed by it until §3.1 lands"*.

### RD-4: `REFACT-04-13`'s fix is delegated, and [[BUG-458]]'s stated fix strategy is incomplete

`REFACT-04-13` says the fix is *"one `is LuaLabelName -> element.parent` branch"* in
`LuaSafeDeleteProcessor.declarationNodeFor`, and BUG-458 §4 says the same. Both are half a fix:
`LuaSafeDeleteProcessor.findUsages` searches `identifierLeafFor(element) ?: element`
(`refactoring/LuaSafeDeleteProcessor.kt:86`), and `identifierLeafFor` has no `LuaLabel` branch
(`:178-191`), so once the element is elevated to a `LuaLabel` the search target becomes the
`LuaLabel` — which nothing references. The delete would then find **zero** usages and remove a label
a live `goto` still needs, which is a worse outcome than `::::`.

- **Owner**: BUG-458, not this feature. Recorded here because REFACT-04 is where the interaction is
  visible and because BUG-458's test strategy ("assert the resulting text parses") would not catch it
  — a file with a dangling `goto` parses fine in Lunar and fails only in a real interpreter.
- **Proposed BUG-458 edit** (not applied): add the matching `is LuaLabel -> element.labelName.identifier`
  branch to `identifierLeafFor`, and add a test that Safe Delete of a label **with** a `goto` raises a
  conflict rather than deleting. See Gap 2.3.

### RD-5: `implementation-plan.md`'s TC-04-M fixture predates REFACT-07 broadening the predicate it tests

`implementation-plan.md`'s TC-04-M names `local x = 1`'s `LuaNameRef` as the negative case for
`LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` — expecting `false`. That was true when
this design was written, evidenced by the (correct, at the time) reading in `requirements.md`:
*"`isMemberInplaceRenameAvailable` returns `true` for exactly one type, `LuaLabelName`"*.

**REFACT-07 Phases 7-8 (`c52d333b`), which landed after this feature's design, broadened the
predicate**: `isMemberInplaceRenameAvailable` now also returns `true` for a **file-local**
declaration's `LuaNameRef` (`LuaRefactoringSupportProvider.kt:87-95`,
`LuaDeclarationKind.isFileLocal`). `local x = 1` is a file-local `LOCAL_VARIABLE`, so the plan's
literal fixture now asserts `false` for an input that is actually `true` — confirmed by the
already-passing `LuaInplaceRenameTest.testMemberInplaceRenameIsOfferedForAFileLocalDeclaration`,
whose fixture (`local counter = 0`) is the same shape.

- **Where this surfaced**: Phase 1, writing TC-04-M into `LuaLabelRenameTest.kt`
  (`testInPlaceRenameAvailabilityConjuncts`).
- **Resolution applied**: the test uses a **global** declaration's `LuaNameRef` as the negative case
  instead (`::top::` / `x = 1`) — `LuaDeclarationKind.GLOBAL_VARIABLE.isFileLocal` is `false`,
  confirmed by the same test file's `testMemberInplaceRenameIsWithheldFromAGlobalDeclaration`. This
  preserves `REFACT-04-09`'s intent (a label is offered in-place rename; something that is not a
  label is not) with a fixture that is actually `false` under the code as it stands. The deviation is
  recorded in the test's own KDoc as well as here.
- **Not applicable elsewhere in this plan**: Phase 3's TC-04-D/E/F/G/H/L/N and Phase 2's TC-04-I/J
  do not go through `isMemberInplaceRenameAvailable` at all, so this staleness is local to TC-04-M.
- **Proposed `implementation-plan.md` edit** (not applied — this file is the tracked-status record;
  the plan text is left as the historical draft): TC-04-M's fixture description should read "a
  global declaration's `LuaNameRef`" in place of "`local x = 1`'s `LuaNameRef`".

## Design Gaps

### Gap 2.1: The conflict model uses PSI block ancestry; the resolver uses `statementList`

- **Question**: `LuaLabelScopes.blockOf` finds the nearest `LuaBlock` ancestor, while
  `LuaBlock.processLabelDeclarations` (`lang/psi/LuaBlockExt.kt:87-91`) only sees labels that are
  direct members of `statementList`. On a file with parse errors a label can have a block ancestor it
  is not a `statementList` member of, and the detector would model a scope the resolver does not.
- **Options / leaning**: (a) accept the divergence — a false conflict on unparseable code is a dialog
  the user dismisses; (b) skip the check when the enclosing block's `statementList` does not contain
  the label's `LuaLabel`, which disables the check exactly when the file is most likely broken.
- **Resolved**: **(a)**, recorded in design §6 E-4. Not a de-risking task — the decision is made and
  the reasoning is in the design. Revisit only if a user reports a conflict on a file with no visible
  duplicate.

### Gap 2.2: Structure View "autoscroll from source" still will not match a label node

- **Question**: `LuaStructureViewModel.SUITABLE_CLASSES` (`lang/structure/LuaStructureViewModel.kt:14-21`)
  lists `LuaLabel`, while after Phase 4 `getValue()` returns a `LuaLabelName`, so
  `TextEditorBasedStructureViewModel`'s current-element lookup and the tree's node values still
  disagree — as they already do today, where the value is an IDENTIFIER leaf.
- **Options / leaning**: adding `LuaLabelName` to `SUITABLE_CLASSES` would make them agree for a caret
  on the identifier, because the model returns the *nearest* suitable ancestor.
- **Resolved**: **out of scope.** No requirement asks for it, and the protected
  `getCurrentEditorElement` is not headlessly assertable, so the change would ship untested. Recorded
  as future work below rather than folded into Phase 4.

### Gap 2.3: BUG-458 and REFACT-01 both edit the same Safe Delete helpers

- **Question**: `REFACT-04-13` is delegated to BUG-458, whose fix edits
  `LuaSafeDeleteProcessor.declarationNodeFor` (`:156-171`). REFACT-01 design §2.1 **moves** that
  helper into `LuaDeclarationSite.declarationNodeOf`. Whichever lands second must apply the label
  branch in the *other* file.
- **Options / leaning**: order-independent guidance rather than a fixed order — the branch belongs
  wherever `declarationNodeFor`/`declarationNodeOf` lives at the time, and RD-4's `identifierLeafFor`
  branch belongs beside it.
- **Resolved by**: DR-04 (a five-minute check, not a spike). REFACT-04 ships no code here either way.

## Technical Debt & Future Work

- **TBD: a duplicate-label inspection.** `LuaLanguageLevelInspection` already visits `LuaLabel`
  (`analysis/inspections/LuaLanguageLevelInspection.kt:61-70`). A sibling inspection reporting
  `label 'x' already defined` at 5.4+ would catch the condition in code the user *typed*, not only in
  code they renamed. Complementary to this feature, not a substitute — design §9 Alternative D.
  Requires the same §3.2 rule, which is why it is worth doing only after §3.2 exists in code.
- **TBD: `REFACT-04-18` — label refactorings other than rename.** Convert-`goto`-to-`break`,
  hoist-a-label, extract-loop. All need control-flow rewriting; `LuaControlFlowBuilder` models labels
  but rewrites nothing. Out of scope by decision; the `INTENT` half of this epic is the right home.
- **TBD: `SUITABLE_CLASSES` for label autoscroll** — Gap 2.2.
- **TBD: `getUseScope` for the rest of Lunar's declarations.** Every non-label declaration is a
  `LuaNameRef` with no `getUseScope` override either, so a file-local `local` is also searched
  module-wide. That is REFACT-01's territory (its §3.2 narrows the *rename* search by kind but does
  not override `getUseScope`), and this feature deliberately does not widen its own edit to cover it.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :--- | :--- | :--- | :--- |
| `REFACT-04-00-DR-01` | Obtain a Lua 5.5 interpreter (build from source into `~/Documents/src/lua/interpreters/lua-5.5.x`) and run design §1's P-a…P-e matrix against it. Record the outcome in design §1's executed table and, if 5.5 diverges from 5.4, in §3.4's tier test. | Risk 1.5; `REFACT-04-08`'s unverified 5.5 claim | todo |
| `REFACT-04-00-DR-02` | Live in-place rename of a label via the `verify-in-ide` flow: confirm `MemberInplaceRenamer` is the handler, that committing runs a `RenameProcessor` (design §1 evidence table, read-derived from `MemberInplaceRenamer.java:309-317`), and that a colliding new name raises the conflicts dialog. | `REFACT-04-09`; the one read-derived claim on which Phase 3's user-visible behaviour depends | **done** |
| `REFACT-04-00-DR-03` | Live F2 on a label node in the Structure View after Phase 4: confirm the rename dialog opens instead of *"cannot be renamed"*. | `REFACT-04-14` | **done** |
| `REFACT-04-00-DR-04` | Before Phase 3: check whether REFACT-01 §2.1's `LuaDeclarationSite.declarationNodeOf` move and BUG-458's `declarationNodeFor` fix have landed, and in which order; record where the label branch belongs. | Gap 2.3, RD-4 | todo |
| `REFACT-04-00-DR-05` | Before writing TC-04-D: run one throwaway rename through `myFixture.renameElementAtCaret` with a deliberately broken `findCollisions` that always emits one collision, and confirm `ConflictsInTestsException` is thrown. Proves the fixture reaches `findCollisions` at all before seven tests are written against the assumption. | The read-derived claim `RenameUtil.java:103` → `RenameProcessor.java:179-182` | todo |

DR-01 and DR-04 gate nothing and may run in parallel with Phase 1. DR-05 gates the *writing* of
Phase 3's tests. DR-02 and DR-03 are post-phase live checks, recorded here so they are not lost.

### DR-02 outcome (executed, REFACT-04 Phase 3, 2026-09-02)

Run on `lunar-builder` (native `runIde`, Xvfb `:99`, `scrot`/`xdotool` — the `verify-in-ide` flow),
against a freshly built plugin (`Loaded custom plugins: lunar (0.18.0)`, timestamp confirmed same-run
per the append-only-log trap). Fixture, a throwaway `dr02_label.lua` in the disposable `proj` scratch
project (default `languageLevel`, i.e. `LUA54`):

```lua
local n = 0
::a::
n = n + 1
do
  if n < 2 then goto a end
  ::b::
end
print("n="..n)
```

1. **Caret on `::b::`, `Shift+F6`.** The identifier went into an editable in-place template (status
   bar `6:5 (1 char)`, the label text boxed inline) — confirms `MemberInplaceRenameHandler.isAvailable`
   resolved true and `MemberInplaceRenamer` is the handler, not the dialog. Typed `a`, pressed `Enter`.
2. **A "Conflicts Detected" modal appeared** (not a silent apply) titled *1 conflicts*, listing under
   `dr02_label.lua`: *"Renaming this label to 'a' duplicates the label declared on line 2. Lua 5.4 and
   later reject the file with "label 'a' already defined"; this project is configured for Lua 5.4."*
   — the exact `refactoring.rename.label.conflict.duplicate` message, byte-for-byte. This confirms
   `Enter` commits through a real `RenameProcessor` (not `VariableInplaceRenamer`'s lighter path,
   which never calls `RenameUtil.findUsages`) and that `findCollisions` is reached from the in-place
   route — the one hazard design §2.2 named as unreachable from a headless test (the `…Base`
   `ClassCastException` risk this phase's `RenamePsiElementProcessor` superclass choice avoids).
3. **Cancel** (`Escape`) aborted cleanly — `cat dr02_label.lua` afterward still read `::b::`
   unchanged; no half-apply.
4. **Regression check, same session**: caret back on `::b::`, `Shift+F6` → `zzz` → `Enter` (a
   non-colliding name) applied **instantly with no dialog** — `::zzz::` in the buffer immediately.
   Confirms the conflict check does not fire on safe renames and the happy path (`REFACT-04-02`,
   `-09`) is unaffected live.

**Screenshots** (session-scratch, not repo-tracked): in-place edit box active
(`dr02-4.png`/`dr02-5.png`), the Conflicts Detected dialog with the exact message
(`dr02-6.png`), post-cancel unchanged buffer (`dr02-7.png`), non-colliding apply
(`dr02-8.png`).

**Teardown**: the launched `runIde` tree (wrapper + `gradlew` + GoLand JBR + `fsnotifier`, 4 PIDs)
was killed and confirmed gone; the throwaway fixture file was deleted from the scratch project;
`git status --porcelain` on the repo was clean of any DR-02 side effect throughout.

All three things DR-02 was opened to confirm are now **executed, not read-derived**: `-09`'s
in-place-availability claim, the `RenamePsiElementProcessor` superclass choice (§2.2), and `-07`'s
conflicts-dialog behaviour (§3.3) all hold live, at the one boundary (in-place commit) no unit test
can reach.

### DR-03 outcome (executed, REFACT-04 Phase 4, 2026-09-02)

Run on `lunar-builder` (native `runIde`, Xvfb `:99`, `scrot`/`xdotool` — the `verify-in-ide` flow),
against a freshly built plugin (log truncated before launch; `Loaded custom plugins: lunar (0.18.0)`
confirmed same-run, not a stale append-only-log line). Fixture, a throwaway `dr03_label.lua` in the
disposable `proj` scratch project reused from DR-02 (default `languageLevel`, i.e. `LUA54`):

```lua
local n = 0
::top::
n = n + 1
if n < 3 then goto top end
print(n)
```

1. **Opened the file, `Alt+7` to open the Structure View.** The tree showed `dr03_label.lua` → `n`
   (variable icon) → `top` (bookmark icon) — the label node Phase 4 changes `getValue()` for.
2. **Clicked the `top` node to select it** (confirmed selected — blue highlight — in the screenshot).
3. **`F2` sent directly to the tree produced no visible effect** — no in-place box, no dialog, no
   error balloon, three consecutive attempts. This is left unexplained (a focus-transfer quirk of
   `xdotool click` against this specific `JTree` under Xvfb is the likely cause, not a plugin defect
   — nothing in Phase 4's diff touches key-event routing), and is recorded rather than glossed over.
4. **`Refactor ▸ Rename…` (the `Shift+F6` action) with the same selection opened the Rename dialog**:
   *"Rename label 'top' and its usages to:"*, pre-filled `top`. This is the outcome DR-03 was opened
   to confirm — a real rename dialog, not *"Cannot perform refactoring — the caret should be
   positioned at the name of an element to be renamed"* or similar. The Structure View's `getValue()`
   is what fed this action's data context, since the editor caret was still at `1:1` from the file
   having just been opened (screenshot confirms `1:1` in the status bar at the moment the menu was
   invoked) — not the label the dialog names.
5. **Completed the rename** (typed `loop`, clicked *Refactor*) as a stronger check than opening the
   dialog alone: the buffer became `::loop::` / `goto loop` (both occurrences, one commit) and the
   Structure View node relabelled to `loop` live — confirming the platform's rename pipeline, not
   just the dialog, resolves correctly from a Structure-View-selected `LuaLabelName`.

**Screenshots** (session-scratch, not repo-tracked): Structure View with the `top` node selected
(`dr03-6.png`), the Refactor menu with *Rename…* enabled from that selection (`dr03-9.png`), the
Rename dialog (`dr03-10.png`), and the post-refactor buffer + tree both reading `loop`
(`dr03-12.png`).

**Teardown**: the launched `runIde` tree (wrapper bash, `gradlew`, GoLand JBR, `fsnotifier` — 4 PIDs,
identified by parent/child relationship, not by killing on sight) was killed and confirmed gone; the
pre-existing Gradle and Kotlin compiler daemons (started before this session, ~3.2h uptime) were left
running, matching this skill's teardown scope. The throwaway fixture file was deleted from the scratch
project; `git status --porcelain` on the repo was clean of any DR-03 side effect throughout.

`REFACT-04-14` is now **executed, not read-derived**: the Structure View publishes a
`PsiNameIdentifierOwner` for a label node and the platform's rename entry point resolves it to a real
rename dialog that performs the rename correctly. The one open item is #3 above (`F2` not firing
directly on the tree) — not a regression this phase introduced (no keybinding code changed), and not
blocking, since `Shift+F6` / `Refactor ▸ Rename…` is the same action DR-02 already verified as the
live rename entry point elsewhere in this feature.

## Test Case Gaps

Two requirements have no test, and both say so rather than carrying an assertion that cannot fail.

- **`REFACT-04-15` (rename is one undoable command).** Platform-supplied by
  `RenameProcessor`'s write command. There is no mutation of *Lunar* code that would make it false —
  the only way to break it is to override `renameElement` and do the edit outside a write command,
  which design §3.6 forbids. A test asserting "undo restores the old text" would pass equally with
  and without any change this feature makes: it is a test of the platform. **Not written.**
- **`REFACT-04-20` (rename never creates a jump-into-block or jump-into-local error).** True by
  construction — a rename moves no code (design §3.2). There is no input that could make it false, so
  there is no test that could fail. **Not written.** The requirement is recorded so a future
  implementer does not build machinery for it.

Three further gaps are real but not headlessly closable:

- **`REFACT-04-09` in-place rename** cannot be exercised without an editor whose settings enable it
  (`MemberInplaceRenameHandler.isAvailable`'s third conjunct). TC-04-M asserts the two conjuncts that
  are observable; DR-02 covers the rest.
- **`REFACT-04-14` rename from the Structure View** needed a real tool window and data context. TC-04-K
  asserted the precondition; DR-03 (executed above) confirmed the rest live — `Refactor ▸ Rename…`
  from a Structure-View-selected label node opens a working rename dialog. A raw `F2` key sent
  directly to the tree under `xdotool` did not fire in that run; DR-03's outcome records this as an
  open, non-blocking item rather than treating `Shift+F6`/`Refactor ▸ Rename…` firing as a lesser
  substitute — it is the same action DR-02 already established as the live rename entry point.
- **Cross-version behaviour.** No test runs a real interpreter over the renamed file. The executed
  matrices in `requirements.md` and design §1 are the substitute, and they are planning evidence, not
  a regression gate. A test that shelled out to `~/bin/lua` would be the `LuaRecursiveReferenceTest`
  problem again — an out-of-repo dependency CI has to exclude. **Deliberately not written.**

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Shared machinery and the feature boundary: [[REFACT-01]] design §1
- Safe Delete of a label: [[BUG-458]]
- The rename refusal this feature's `LuaLabelRef` claim replaces: [[BUG-457]]
