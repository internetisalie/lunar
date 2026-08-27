---
id: REFACT-07
title: "07: In-place (Inline) Rename"
type: feature
status: "done"
priority: "high"
parent_id: REFACT/INTENT
folders: ["[[features/refactoring/requirements|requirements]]"]
---

# 07: In-place (Inline) Rename

Press <kbd>Shift+F6</kbd> on a Lua local, parameter, `for` variable or local function and rename it
by typing in the editor — the declaration and every usage in the file changing together under a
live template — instead of being sent to the modal rename dialog.

This feature owns `REFACT-01-12`, which [[REFACT-01]] delegates here under the same house pattern
that moved `REFACT-01-11` to [[REFACT-05]] and `REFACT-01-17` to [[REFACT-04]].

## Priority note

The MoSCoW grade inherited from `REFACT-01-12` is **S**. The user has stated this is a
high-priority usability feature, and the front-matter `priority` above records that. Plan and
schedule it above its inherited letter.

## Why this is a feature and not a REFACT-01 phase

The behavioural delta is small; the **blast radius** is not. Delivering in-place rename requires
declaring `LuaNameRef` a `com.intellij.psi.PsiNameIdentifierOwner`, and that interface is a
platform-wide discriminator: highlighting, Safe Delete, the declaration/target API, bookmarks and
line markers all branch on it (design §4 enumerates every consumer found in the platform tree, each
with `file:line`). None of those subsystems is inside REFACT-01's scope, and REFACT-01's
`requirements.md` is frozen.

**The criterion, stated before DR-01 runs:** if DR-01 shows that the interface change moves the
observed behaviour of **no** consumer outside `com.intellij.refactoring.rename`, and that the
delivery adds no production file beyond those design §2 names and no `plugin.xml` registration
beyond the one design §2.4 names, then this work is small enough to be a REFACT-01 phase and the
recommendation flips. That outcome is a legitimate planning result, not a failure;
`risks-and-gaps.md` DR-01 records both branches.

DR-05 has run, and design §3.5's rename handler **is** part of the delivery: the applied branch of
its decision rule is recorded in `risks-and-gaps.md`. The criterion above therefore weighs a
delivery that includes §2.3's class and §2.4's registration.

## Overview

Two rename experiences exist in the IntelliJ Platform. The **dialog** path is shipped and correct
for Lua (`LuaRenameProcessor`, REFACT-01). The **in-place** path replaces the dialog with a live
editor template: the declaration's identifier becomes an editable segment, every usage becomes a
linked segment that mirrors it as the user types, and <kbd>Enter</kbd> commits while <kbd>Esc</kbd>
restores. Today <kbd>Shift+F6</kbd> on a Lua local silently falls through to the dialog.

The user value is the ordinary one — rename without leaving the editor, with the affected
occurrences visible while typing — and the correctness bar is that the in-place path must produce
**exactly** the file the dialog path produces, including LuaCATS `---@param` propagation and
conflict refusal. An in-place path that renames the code but not the doc tag is a worse outcome
than no in-place path at all, because the divergence is invisible at the moment it happens.

## Scope

### In Scope

- Starting an inline rename template from a **file-local** Lua declaration site: local variable,
  parameter, generic-`for` variable, and `local function` name.
- Routing that template through `LuaRenameProcessor` so the committed result is byte-identical to
  the dialog path's result on the same input.
- Guaranteeing exactly one rename handler claims a Lua declaration, so no handler-chooser dialog
  is ever shown.
- Serving every caret whose data-context element is a declaration IDENTIFIER **leaf** and which
  therefore reaches no platform in-place handler — a **usage-site** caret and a **parameter
  declaration** caret, both measured — with a Lua `renameHandler` that normalises it (design §3.5).
- Declaring the declaring `LuaNameRef` a `PsiNameIdentifierOwner`, and auditing every platform
  consumer of that interface for behaviour change.
- Preserving `REFACT-04`'s label in-place rename unchanged.
- Closing the coverage hole: an executable assertion on the **editor-visible document**, not on an
  availability predicate.

### Out of Scope

- **In-place rename of a global.** A Lua global is `_ENV.x`, visible in every file, so its usages
  cannot be previewed in one editor. Globals keep the dialog and its preview pane. `REFACT-07-10`
  makes that a positive requirement rather than an omission.
- **The numeric-`for` control variable.** `numericForStatement ::= FOR IDENTIFIER '=' …`
  (`lua.bnf:152`) binds a bare IDENTIFIER leaf with no `nameRef` wrapper, so there is no
  `PsiNamedElement` for a template to anchor on. Measured, on the unchanged tree: the data context
  supplies **no element** at that caret and `RenameHandlerRegistry` returns an **empty** handler
  list (`risks-and-gaps.md` DR-05, probes `f`/`f2`/`f3`), so no Lunar code is on that path at all.
  `REFACT-07-14` records the refusal.
- **`function Obj:method()` and dotted-function receiver segments.** Refused with a reason by
  `LuaRenameProcessor` today (`LuaBundle.properties:152-153`); in-place inherits that refusal and
  does not widen it. Tracked under REFACT-01, not here.
- **Table fields and constructor keys** (`REFACT-01-09`, `Not Implemented`).
- **Changing what the dialog path does.** REFACT-01's `renameElement`, conflict rules and
  `---@param` propagation are inputs to this feature, not subjects of it.

## Premises examined

Each constraint this feature treats as fixed, and whether it actually is.

| Premise | Fixed? |
| :--- | :--- |
| Lunar models no declaration PSI, so a declared name is a `LuaNameRef` in a container | **Fixed, and irrelevant to this feature.** The premise is about *grammar shape*, and this feature does not change it. `nameRef ::= IDENTIFIER` (`lua.bnf:169`) stays as it is. |
| Giving a declaration a `PsiNameIdentifierOwner` requires editing `lua.bnf` and regenerating the parser | **NOT fixed — removed.** `nameRef` declares `mixin="net.internetisalie.lunar.lang.psi.LuaNameRefBaseImpl"` (`lua.bnf:170`), a hand-written Kotlin class in `LuaBaseElements.kt:95-106`. The platform tests the interface with `instanceof`, which a mixin satisfies, and `LuaNameRef.getIdentifier()` is already generated `@NotNull` (`LuaNameRefImpl.java:30-34`). Design §3.1 delivers the interface in that one file, with **no** `.bnf` edit and **no** regeneration. |
| Route A's `getUseScope()` override is a prerequisite for in-place rename | **NOT fixed — removed by route choice.** It is a prerequisite for `VariableInplaceRenamer` only, whose `checkLocalScope()` reads `PsiSearchHelper.getUseScope` (`InplaceRefactoring.java:283-290`). `MemberInplaceRenamer.checkLocalScope()` overrides that method to return the editor's own file and never consults the use scope at all (`MemberInplaceRenamer.java:105-111`). Design §2 chooses Route B; the override is not written. |
| In-place rename and dialog rename are two implementations of the same operation | **Route-dependent, and the reason the route matters.** Under `VariableInplaceRenamer` they are *not*: the template's own document edits are the rename, and `renameSynthetic` is an empty method (`VariableInplaceRenamer.java:447-448`), so `LuaRenameProcessor.renameElement` never runs. Under `MemberInplaceRenamer` they are one operation: `performRefactoringRename` (`MemberInplaceRenamer.java:250-307`) rolls the template back and runs a real `RenameProcessor` through `performRenameInner` (`:309-317`). |
| Both in-place handlers can be enabled and the platform will pick sensibly | **NOT true, and it inverts the choice.** `RenameHandlerRegistry.doGetRenameHandlers` removes `MemberInplaceRenameHandler` from the candidate list whenever more than one handler is available (`RenameHandlerRegistry.java:114-119`). Enabling both makes Route B unreachable. `REFACT-07-02` is the requirement that follows. |
| The shipped `isInplaceRenameAvailable` predicate is progress toward this feature | **Chosen, and it is *moved*, not kept.** Its shape — `element is LuaNameRef && LuaDeclarationSite.kindOf(element.identifier)?.isFileLocal == true` — is right and is reused verbatim; the method it sits in changes, and the old one must return `false` for the same input, per the row above. |
| The element the platform hands an in-place rename handler is the `LuaNameRef` under the caret | **ESTABLISHED by DR-05, 2026-08-26 — and it is false for a usage caret, false for a parameter declaration caret, and false for a declaration whose name is already in scope.** Measured with nothing injected into the data context; the raw per-caret fields are in `risks-and-gaps.md` under DR-05 and in `dr-05-evidence/`. A usage caret supplies the declaration IDENTIFIER **leaf**, a plain `local`/global declaration caret supplies the declaring `LuaNameRef`, a numeric-`for` caret supplies **null**, and a parameter declaration caret supplies the leaf — with and without a `---@param` tag, and with the caret at the token boundary or inside it — which is why `REFACT-07-09` is served by design §3.5's handler rather than a platform one. The reasoning below is the one the run confirmed: Both in-place gates read `PsiElementRenameHandler.getElement(dataContext)` *before* any substitution (`VariableInplaceRenameHandler.java:34`, consumed at `MemberInplaceRenameHandler.java:46` and `:56`). In an editor that key is `TargetElementUtil.findTargetElement(editor, getReferenceSearchFlags(), caret.offset)` (`TextEditorPsiDataRule.kt:63-64` → `getPsiElementIn` at `:183-192`), which returns `ref.resolve()` whenever a reference resolves at the offset (`TargetElementUtilBase.java:235-236` → `:173-183`). Lunar's Phase-1 resolution returns the declaration IDENTIFIER **leaf** — every assignment to `LuaScopeProcessor.result` is an `.identifier`. Enumerate them with `grep -nE '^ *result *=' src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt` and read each right-hand side: the implicit-`self` assignment spans several lines, so the single-line form `grep -n "result = "` misses it and does not prove the universal. `LuaNameReference.kt:268-269` says the same in words. A leaf is neither a `PsiNameIdentifierOwner` nor a `LuaNameRef`. Design §3.5 delivers `REFACT-07-11` against the leaf. The two caret kinds this premise touched that DR-05 did not probe are now **ESTABLISHED by DR-01 probe (b), 2026-08-26**: a generic-`for` variable caret supplies the declaring `LuaNameRef` (measured at three placements) and a `local function` name caret supplies the IDENTIFIER leaf. DR-01 probe (b) additionally measured a `local` shadowing an earlier same-file `local`, which supplies the **earlier** declaration's leaf — `risks-and-gaps.md` Gap 2.21. |
| No registered `renameHandler` other than the platform's two in-place handlers is available for a Lua editor caret | **ESTABLISHED by DR-02 for this build (2026-08-26).** A running GoLand 2026.1.3 registers **34** `renameHandler` extensions; 31 answered `isRenaming` = `false` at a declaration, a usage and a parameter caret, and the three that ever answered `true` are the platform's two in-place handlers and Lunar's own. Enumerated per caret in `risks-and-gaps.md` DR-02 Table 1 and raw in `dr-02-evidence/measured-rows.txt`. **The scope is this build** — a different IDE or a user's third-party plugin registers a different list, and Risk 1.9 carries that residual. The reasoning the measurement confirms: The pairwise fact that Lunar's handler and `MemberInplaceRenameHandler` are never both available is true (design §3.5) and is *not sufficient*: `RenameHandlerRegistry.doGetRenameHandlers` returns early only when the map built from **every** registered handler holds one entry (`RenameHandlerRegistry.java:106-113`); otherwise its removal loop deletes the first entry that is `instanceof MemberInplaceRenameHandler` and `break`s (`:114-119`). Design §3.5 grounded the refusal of each handler registered under `platform/`; a bundled IDE registers more, and this checkout cannot enumerate GoLand's, which is why DR-02 read the live `RenameHandler.EP_NAME` list instead. Two consequences are designed for rather than assumed away: §2.3 implements `RenameHandler` **directly**, so no entry the removal loop can match is Lunar's, and `risks-and-gaps.md` Risk 1.9 carries the visible-chooser residual that remains if the premise is false. |
| A green full suite means the PSI change is safe | **NOT true, and it is this feature's founding measurement — now with three named instances.** The suite is green with an override that blanks three usages in a live editor, because nothing drives a rename template end to end. `REFACT-07-15` exists to make "green" mean something. **DR-03 re-confirmed it on 2026-08-26 by running the full suite on both commits and comparing the test-NAME sets, not the counts**: 2851 names on each side, `diff` empty, the only failure the predicted `LuaInplaceRenameTest` one — and *none* of the three behaviour changes DR-03 measured (the <kbd>Ctrl+D</kbd> caret, a Related-Symbol item, a bookmark anchor) is asserted on by any test in that set, on either commit. |
| `LuaLabelName`'s in-place rename is a working precedent to copy | **Deliberately reused, and stated why.** It is the same route (`MemberInplaceRenameHandler`), in the same plugin, against the same platform build — the strongest evidence available that Route B is viable here. Copying it copies its shape, not its rules: labels keep going through the platform's default processor because `LuaRenameProcessor.canProcessElement` excludes them, and `REFACT-07-13` pins that. |

## The basis for `status: done` with two rows at `Partial`

Every Definition-of-Done box in `implementation-plan.md` is ticked and evidenced. Two requirement
rows nonetheless remain `Partial`, and both are **as complete as this repository can make them**
rather than work outstanding — which is what makes `done` honest here, and is stated so a reader
does not read the pairing as an unearned promotion.

- **`REFACT-07-02`** — *"exactly one handler claims a Lua declaration"* is measured true against the
  **34** `renameHandler` extensions a running GoLand 2026.1.3 registers (DR-02), of which only the
  platform's two in-place handlers and Lunar's ever answer `isRenaming`. It is a universal over a
  set this repository does not control: another installed plugin can register a handler tomorrow. No
  work closes it, and `Risk 1.9` carries the residual.
- **`REFACT-07-12`** — its ranking half is now measured inert (task 5.1a: byte-identical completion
  order across a baseline and treatment build proven distinct by bytecode, on equalised navigation
  history). What remains is the **minimap** rows, which name classes GoLand 2026.1.3 does not ship —
  the reference checkout carries a rewrite the shipping build does not. They are **unexecutable on
  this platform build, not unattempted**, and are tied to `Risk 1.3`.

The distinction that governs both: a `Partial` that no available action can advance is a recorded
limit, and a `Partial` that a measurement would close is outstanding work. Task 5.1a was the second
kind, which is why `done` was not earned until it ran. Neither remaining row is.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `REFACT-07-01` | **The template starts on a file-local declaration** | **M** | **Full** | <kbd>Shift+F6</kbd> with the caret on a `local`, parameter, generic-`for` variable or `local function` name opens an inline template in the editor rather than the modal dialog. **Coverage, per kind, because one case cannot stand for four:** each of the four declaration kinds has its own document-layer case, driven end to end to a committed document — `local` is TC-01, parameter is TC-09, generic-`for` is TC-16 and `local function` is TC-17 — and each reddens under a mutation this table names. TC-16's and TC-17's mutations are **kind-specific**, which is what TC-01's is not: each deletes one arm of `LuaDeclarationSite.kindFromNameRefGrandParent` and, run against the **whole** suite on 2026-08-26, reddened 3 of 2867 cases — its own, `LuaDeclarationSiteTest`'s shape enumeration, and the matching dialog-path case in `LuaRenameTest`. **Live drive is narrower than that and stays disclosed:** Phase 5 drove **two** of the four in the IDE — the `local` declaration and the parameter — plus a usage caret; the generic-`for` and `local function` kinds are evidenced at the document layer and at DR-01's availability probes (b4, b4b, b4c, b5), not in a running GoLand. |
| `REFACT-07-02` | **Exactly one handler claims a Lua declaration** | **M** | **Partial** | `RenameHandlerRegistry.getRenameHandlers` returns exactly one handler for the caret context, so no handler chooser is shown and the chosen route is deterministic. Design §3.5's availability invariant makes the two in-place handlers mutually exclusive; the requirement additionally needs the premise that no *other* registered handler claims the caret, which DR-02 establishes and the Premises table records as not established. **Measured state (Phase 5):** the behaviour ships and is verified — TC-02 passes under its mutation, DR-02 enumerated the live `RenameHandler.EP_NAME` list in a running GoLand 2026.1.3 (34 registrations, 31 answering `isRenaming` = `false` at all three carets), and Phase 5 saw no handler-chooser at any of six live carets — declaration, usage, parameter, global, numeric-`for` and label (`human-verification-checklists.md`, "Exactly one handler"). It is **not `Full`** because the requirement is a claim about *every* registered handler, and that premise is a property of one build's extension list rather than an invariant: a different IDE, or a user's third-party plugin, can add a handler that claims the caret. `risks-and-gaps.md` Risk 1.9 is the standing residual. Rounding this to `Full` would assert an invariant nobody has established. |
| `REFACT-07-03` | **The declaration's own identifier is the editable segment** | **M** | **Full** | The template's primary variable covers the declaration's IDENTIFIER text range; typing replaces that text. A template that starts with no current variable is a defect, not a degraded mode. |
| `REFACT-07-04` | **Every in-file usage is a linked segment** | **M** | **Full** | Each usage of the declaration in the same file mirrors the primary segment as it is typed. No usage is blanked, duplicated or left on the old text while the template is live. |
| `REFACT-07-05` | **Commit produces the dialog path's exact result** | **M** | **Full** | On <kbd>Enter</kbd>, the resulting file text is byte-identical to the text the dialog path produces for the same declaration and the same new name. |
| `REFACT-07-06` | **Cancel restores the file byte-for-byte** | **M** | **Full** | <kbd>Esc</kbd> during the template leaves the document exactly as it was before <kbd>Shift+F6</kbd>, including the case where several characters were already typed. |
| `REFACT-07-07` | **An invalid or reserved new name does not commit** | **M** | **Full** | Typing a name that `LuaNamesValidator` rejects — a keyword, a leading digit, a hyphen — leaves the declaration and its usages on the old name. Nothing is half-applied. |
| `REFACT-07-08` | **Conflicts are detected with the dialog path's rules** | **M** | **Full** | A new name that would capture or shadow an existing binding is refused before any edit, by the same `LuaRenameConflictDetector` rules `REFACT-01-14` ships. The in-place path adds no second conflict model. |
| `REFACT-07-09` | **`---@param` propagation happens in-place too** | **M** | **Full** | Renaming a parameter that carries a `---@param` tag moves the tag, exactly as the dialog path does. Parity with `REFACT-01-16`; divergence here is silent doc rot. |
| `REFACT-07-10` | **A global takes the dialog** | **S** | **Full** | With the caret on a global declaration, <kbd>Shift+F6</kbd> opens the rename dialog with its preview pane. A global's usages span files and cannot be previewed in one editor. |
| `REFACT-07-11` | **A usage-site caret opens the template on its declaration** | **M** | **Full** | <kbd>Shift+F6</kbd> on a *read* of a file-local declaration renames that declaration, not just the read, and the committed file matches `REFACT-07-05`. Delivered by design §3.5's handler, because the element the data context supplies for such a caret is the declaration IDENTIFIER leaf, which no platform in-place handler accepts. |
| `REFACT-07-12` | **The PSI change alters no behaviour Lunar ships a feature for, and every platform behaviour it does move is recorded and accepted** | **M** | **Partial** | Declaring `LuaNameRef` a `PsiNameIdentifierOwner` leaves Find Usages, Safe Delete, identifier highlighting, Go to Declaration, the Structure View and the interface-branching inspections **byte-identical across the two commits** — **and** the platform behaviours it does move are each named here and accepted: `NamedElementDuplicateHandler` (the resting caret after <kbd>Ctrl+D</kbd>, document unchanged), `RelatedItemLineMarkerProvider` (a composite context yields one related item where it yielded none; the editor action is unaffected), and `BookmarkManager.findElementBookmark` (dead code — referenced only by its own `@Deprecated` declaring class). A behaviour outside that list moving is a defect. **Measured state (Phase 5):** the accepted-list half holds and gained live corroboration — <kbd>Ctrl+D</kbd>'s resting caret was driven with **no selection** on three line shapes and the document was a correct duplication every time, with the keyword-start line unchanged (`human-verification-checklists.md`). Two behaviours the row names as byte-identical were also checked live and are **confirmed not to have moved**: Find Usages and Safe Delete are both refused at a *declaration* caret, and DR-03's base and treatment probe rows for each are character-identical, so the refusals are pre-existing rather than this feature's. **The two completion-ranking rows are now MEASURED and inert (task 5.1a, 2026-08-27).** `RecentPlacesFeatures.findDeclaration` and `VcsFeatureProvider` both take a newly-taken branch and neither moves completion item order, established three independent ways: the value `findDeclaration` feeds downstream is `[]` on **both** arms (a `LuaNameRef`'s only child is the IDENTIFIER leaf, which `getChildren()` omits, so `getChildrenNames()` matches base's `?: emptyList()`); **no** `com.intellij.completion.ml.model` provider matches Lua in GoLand 2026.1.3 — the registered set is SQL, Go, JavaScript, TypeScript, Shell — so `shouldReRank()` is false and no feature value of any kind can reorder a Lua lookup; and the live A/B over two bytecode-discriminated sandboxes, one unedited driving script and an equalised navigation history produced **pixel-identical frames**. Evidence: `phase-5-live-evidence/task-5-1a-ranking-measurement.txt` and `33-`/`34-ranking-ab-*-completion-order.png`. It is **still not `Full`**, and now for exactly one reason: DR-03's two minimap rows (`MinimapStructureMarkerCollector`, `MinimapHoverHitCheck`) remain unaudited because **the classes do not exist in the shipped platform** — unexecutable rather than unattempted, and not closable until Lunar moves to a build carrying the minimap rewrite. |
| `REFACT-07-13` | **Label rename is unchanged** | **S** | **Full** | `::label::` in-place rename, its `goto` rewriting and its per-function scope isolation behave exactly as `REFACT-04` specifies, before and after this feature. |
| `REFACT-07-14` | **The numeric-`for` variable is not offered the template** | **C** | **Full** | With the caret on `i` in `for i = 1, 10 do`, no inline template starts; the user gets whatever the dialog path gives them today. There is no `nameRef` to anchor a template on. |
| `REFACT-07-15` | **The editor-visible outcome is asserted** | **M** | **Full** | At least one automated test drives a rename template to commit and asserts the resulting **document text**. An availability predicate is not an acceptance check for a template that starts and then corrupts. |
| `REFACT-07-16` | **Cross-file in-place rename** | **W** | **Won't** | Out of reach by construction: `InplaceRefactoring.performInplaceRefactoring` aborts when any collected reference lives in another file (`InplaceRefactoring.java:228-234`, abort at `:231-232`), and a linked template can only highlight segments in one editor. |

## Detailed Specifications

### REFACT-07-01: The template starts on a file-local declaration

**Eligible kinds** are exactly those whose `LuaDeclarationKind.isFileLocal` is `true` and that are
reachable as a `LuaNameRef`: `LOCAL_VARIABLE`, `PARAMETER`, `GENERIC_FOR_VARIABLE`,
`LOCAL_FUNCTION` (`LuaDeclarationSite.kt:19-28`). `NUMERIC_FOR_VARIABLE` is `isFileLocal` but has
no `LuaNameRef` (see `REFACT-07-14`); `LABEL` is `isFileLocal` and is REFACT-04's, not this
feature's.

"Opens an inline template rather than the dialog" is observable in two independent ways, and both
are required, because either alone has been shown to pass on a broken feature:

1. The handler the platform selects for the caret context is the in-place one.
2. Driving that handler to commit changes the document to the expected text.

**Which in-place handler that is depends on the kind, and it is not a free choice.** The platform's
`MemberInplaceRenameHandler` serves a caret whose data context supplies the declaring `LuaNameRef`;
Lunar's `LuaInplaceRenameHandler` (design §3.5) serves a caret whose data context supplies the
declaration IDENTIFIER **leaf**. Measured on the unchanged tree: a `local` declaration caret is the
first (`risks-and-gaps.md` DR-05 probe `a`) and a **parameter** declaration caret is the second
(probes `d`/`d2`/`d3`). The generic-`for` and `local function` kinds are now measured too, by DR-01
probe (b) with the §3.1 and §2.3 edits present: a **generic-`for`** variable caret is the first —
its context supplies the declaring `LuaNameRef` at all three placements probed — and a
**`local function`** name caret is the second, its context supplying the IDENTIFIER leaf exactly as
a parameter's does. So clause (2) of design §3.2 serves `local`, global and generic-`for`, and
design §3.5's handler serves usage, parameter and `local function`. A test case or checklist item
that drives the wrong one measures the wrong route.

### REFACT-07-02: Exactly one handler claims a Lua declaration

`RenameHandlerRegistry.doGetRenameHandlers` builds a `TreeMap` of every registered handler whose
`isRenaming(dataContext)` is true (`RenameHandlerRegistry.java:106-110`). It returns immediately
when that map holds exactly one entry (`:111-113`); otherwise it deletes the first entry that is
`instanceof MemberInplaceRenameHandler` and `break`s (`:114-119`). The consequences that bind this
feature:

- If both `isInplaceRenameAvailable` and `isMemberInplaceRenameAvailable` answer `true` for the
  same element, the selected handler is `VariableInplaceRenameHandler` — the route this feature
  rejects — and the rejection is silent. Design §3.2 makes `isInplaceRenameAvailable` `false` for
  that reason.
- If neither answers `true` and nothing else claims the caret, `PsiElementRenameHandler` is returned
  (`:121-123`) and the user gets the dialog.
- **The early return is over the whole extension list, not over those two handlers.** So "no element
  is both a `PsiNameIdentifierOwner` and a Lua IDENTIFIER leaf" — which is true — does not by itself
  give "the map never holds two entries". This requirement additionally rests on the premise that no
  other registered `renameHandler` is available for a Lua editor caret. That premise is in the
  Premises table, and DR-02 **established it for this build** on 2026-08-26: of the 34 handlers a
  running GoLand registers, only the platform's two in-place handlers and Lunar's own ever claim a
  Lua caret.

The requirement is therefore a mutual exclusion **plus a premise about everything else registered**,
not merely "one of them is true". Design §2.3 chooses the handler's base type so that the failure
mode when the premise is false is a visible chooser rather than the silent deletion of Lunar's own
handler; `risks-and-gaps.md` Risk 1.9 carries the residual.

### REFACT-07-05: Commit produces the dialog path's exact result

"Byte-identical to the dialog path" is a comparison against a second execution, not against a
hand-written expected string. The dialog path's result for a given fixture and new name is
whatever `myFixture.renameElementAtCaret(newName)` produces on that fixture; the in-place result
is what the template produces on the same fixture. A test that hard-codes only the expected text
would stay green if *both* paths regressed together, so `TC-05` asserts the expected text **and**
the equality of the two paths.

### REFACT-07-07: An invalid or reserved new name does not commit

`LuaNamesValidator.isIdentifier` is `IDENTIFIER_PATTERN.matches(name) && !LuaKeywords.isReserved(name)`
with `IDENTIFIER_PATTERN = ^[A-Za-z_][A-Za-z0-9_]*$` (`LuaNamesValidator.kt:18-24`), registered at
`plugin.xml:393-395`. The template consults it through
`InplaceRefactoring.isIdentifier` → `LanguageNamesValidation.isIdentifier`
(`InplaceRefactoring.java:832-834`).

The specified outcome is **the old name everywhere**. The hazard this requirement exists to
exclude is the shape where the template's own document edits have already put the invalid text
into the file and the commit step merely declines to do anything further — leaving `local 1total`
on disk. That is a parse error the user did not ask for, and it is exactly what a route whose
template edits *are* the rename would produce.

### REFACT-07-08: Conflicts are detected with the dialog path's rules

The rules are `LuaRenameConflictDetector`'s, unchanged: capture, shadow, existing global, and
ambiguous global (`LuaBundle.properties:154-157`). The in-place path must reach them through the
same `RenamePsiElementProcessor.findCollisions` entry the dialog uses, so that a rule added to
REFACT-01 later applies here with no edit.

### REFACT-07-11: A usage-site caret opens the template on its declaration

The caret is on a **read**, so the element the platform's data context supplies is not the
`LuaNameRef` under the caret — it is whatever that read's reference resolves to. For Lua that is
the **declaration IDENTIFIER leaf** — measured, DR-05 probes `b` and `c`; see the Premises table
row above. A leaf
is not a `PsiNameIdentifierOwner`, so neither platform in-place handler is available for such a
caret and no widening of `isMemberInplaceRenameAvailable` can change that — the `instanceof` gate
at `MemberInplaceRenameHandler.java:46` runs before the provider is consulted. That refusal is
measured too: `MemberInplaceRenameHandler().isAvailableOnDataContext` was `false` at both carets.

Design §3.5 therefore delivers this requirement with a Lua-specific `RenameHandler` that accepts
the leaf and hands its declaring `LuaNameRef` to the platform handler. Two consequences a reader
must not mistake for defects:

- **The primary template segment is the occurrence at the caret, not the declaration.**
  `buildTemplateAndStart` makes the reference containing the caret `PRIMARY_VARIABLE_NAME`
  (`InplaceRefactoring.java:351-357`) and adds the declaration's name identifier as a linked
  `OTHER_VARIABLE_NAME` (`:362-367`). `REFACT-07-03` is written against a **declaration** caret and
  is not contradicted by this.
- **The committed file is still the declaration's rename**, because the commit runs
  `LuaRenameProcessor` on the substituted declaration leaf, exactly as for a declaration caret.

### REFACT-07-12: The PSI change alters no behaviour Lunar ships a feature for

The consumers to audit are every platform site that branches on `PsiNameIdentifierOwner`, listed
with `file:line` in design §4. For each, the requirement is that the *observed* Lunar behaviour is
unchanged — not that the code path is untaken. Some paths are newly taken and still produce the same
answer, and design §4 says which, with the reasoning.

**MEASURED by DR-03, 2026-08-26**, on `f6148451` (base) and `8bbb7032` (treatment) against the same
fixtures. The behaviours this requirement names — Find Usages, Safe Delete, identifier highlighting,
Go to Declaration, the Structure View, and the interface-branching inspections — are byte-identical
across the two commits.

**The three platform behaviours that do move, each accepted:**

| Consumer | What moves | Why it is accepted |
| :--- | :--- | :--- |
| `NamedElementDuplicateHandler` | the **resting caret** after <kbd>Ctrl+D</kbd>. The document is byte-identical in every case; the caret lands on the duplicated line's first Lua name instead of at the line end. Measured on `counter = helper(1, 2)` (151 → 129) and `print(counter)` (158 → 144); unchanged on `local counter = 0`, whose line starts with a keyword and has no `LuaNameRef` ancestor | it is the platform's intended behaviour — land on the name so it can be typed over — and is what other languages already get. **But it is user-visible on a keystroke unrelated to rename**, so it carries a `human-verification-checklists.md` item rather than an argument. "No test asserts it on either commit" is not "nobody will notice" |
| `RelatedItemLineMarkerProvider` | with a **composite** context element, `getItems` yields one related item where it yielded none. With the IDENTIFIER leaf it yields one on both commits | **the editor action is unaffected, and that was measured**: `GotoRelatedSymbolAction.getContextElement` is `psiFile.findElementAt(caretOffset)`, which is always a leaf, and `GotoRelatedSymbolAction.getItems(file, editor)` returns the same on both. The composite path is reachable only where something supplies a composite `PSI_ELEMENT` with no editor, and there the extra item is correct |
| `BookmarkManager.findElementBookmark` | answers where it answered `null` | **dead code.** A bytecode scan of the shipped distribution finds the method referenced only by its own declaring class, and `BookmarkManager` is `@Deprecated` |

**None of the three is undesirable, so the decision rule's fourth branch — reopen design §3.1
Alternative B — did not fire, and this requirement is not reopened.**

**A green suite cannot see any of this, and that is now concrete rather than a worry.** The two
commits executed the **identical set of tests** — every `<testcase name= classname=>` extracted from
every `TEST-*.xml`, `LC_ALL=C` sorted, gives 2851 names on each side and an **empty `diff`** — so
the matching totals are not masking a different selection. And **no test in that set asserts any of
the moved behaviours in the table above, on either commit.** Treatment's one failure is the predicted
`LuaInplaceRenameTest > testInplaceRenameIsOfferedForAFileLocalDeclaration`; the single skip is
`LuaCompletionTest` on both. Raw comparison: `dr-03-evidence/suite-results-comparison.txt`.

DR-03 also found design §4's table inconsistent with the compiled platform; design §4 now carries
each correction with its reason, and `risks-and-gaps.md` DR-03 carries the per-consumer verdicts and
the rows that remain unrun.

## Behaviour Rules

- **Refusals are loud, half-applications are forbidden.** Every outcome in which the template
  cannot proceed leaves the file exactly as it found it. There is no state in which the
  declaration carries one name and a usage carries another.
- **One source of truth for what a declaration is.** Availability, reference search, conflict
  detection and the rewrite all ask `LuaDeclarationSite`. This feature adds no second predicate.
- **One source of truth for the rewrite.** The committed edit is `LuaRenameProcessor.renameElement`
  on both paths. A behaviour the dialog has and the template lacks is a defect in this feature. The
  mechanism that can cause one is the *element* each path hands the processor: the platform
  re-derives it as a `LuaNameRef` composite on the in-place path and as a leaf on the dialog path,
  so design §3.6 normalises inside `renameElement` and both paths classify the same thing.
- **Threading.** Availability predicates are called on the EDT and must not resolve, index or touch
  VFS. The template's reference collection runs in a background read action supplied by the
  platform (`InplaceRefactoring.java:217-224`). The commit runs in a write command opened by the
  platform. No component this feature adds opens a write action of its own.

## Test Cases

Every case either names the mutation that turns it red — expressible against code this repo can
edit, and reachable from that case's own fixture — or is labelled **guard** and carries the
argument for why no such mutation exists. A guard's green run is not evidence the feature works;
it is evidence nothing regressed. Fixture text is the literal file content; `<caret>` is the caret
marker `myFixture.configureByText` consumes.

**Which handler a document-layer case drives, and through which driver**, is part of the case, not
an implementation detail, and it follows from **which element the data context supplies at that
case's caret** — design §1's caret table — not from whether the caret is on a declaration:

- Context supplies the declaring `LuaNameRef` → drive `MemberInplaceRenameHandler()` through
  `CodeInsightTestUtil.tryInlineRename(handler, …)`, which takes the handler as an argument and
  calls `doRename` on it directly (`CodeInsightTestUtil.java:246`).
- Context supplies a declaration IDENTIFIER **leaf** → drive `LuaInplaceRenameHandler()` through
  `renameInPlaceViaHandler`, the helper design §6 prints verbatim. `tryInlineRename` cannot take it,
  because its parameter type is `VariableInplaceRenameHandler` (`CodeInsightTestUtil.java:236`) and
  `LuaInplaceRenameHandler` implements `RenameHandler` directly (design §2.3).

Every row that drives an in-place rename handler names which one and through which driver — in its
last column, or in its "When" column where the case spells the call out — and those rows are the
single list of which case is in which group. A registry-layer row (§6 layer 1) drives no handler,
and TC-13 and TC-14 drive Find Usages and Safe Delete rather than a rename.
Both drivers return `false` when no template started; asserting that return is what Phase 2's
binding rule requires. Using the wrong handler or the wrong driver makes the case measure the wrong
route — on a leaf-supplying caret `MemberInplaceRenameHandler.doRename` refuses the element at
`MemberInplaceRenameHandler.java:56` and falls through to `performDialogRename` at `:87`, so no
template starts and the case fails against a route the feature does not take.

| # | Requirement | Given (fixture) | When (action) | Then (expected) | Mutation that reddens it |
|---|---|---|---|---|---|
| TC-01 | `REFACT-07-01`, `-03` | `local coun<caret>ter = 0`⏎`print(counter)` | drive the in-place handler to commit with `"total"` | document is `local total = 0`⏎`print(total)` | change `LuaNameRefBaseImpl.getNameIdentifier()` (§3.1) to `= null` — `buildTemplateAndStart` then calls `getSelectedInEditorElement(null, refs, stringUsages, offset)` (`InplaceRefactoring.java:347` → `:841-862`), whose first loop misses because no collected reference contains a declaration caret (design §3.3 step 5), whose `nameIdentifier` branch at `:851-854` is skipped because `nameIdentifier` is null, and whose `stringUsages` is empty — so control reaches `LOG.error` at `:860`. Under the test logger that **throws**: `TestLoggerFactory.TestLogger.error` (`TestLoggerFactory.java:550`) rethrows as `TestLoggerAssertionError` (`:578-580`, class at `:535-539`). The case therefore goes RED there, before any template exists and before the harness's own `assert range != null` (`CodeInsightTestUtil.java:257-258`) is reached; Phase 4 records which frame fired. The override cannot be **deleted**: `PsiNameIdentifierOwner.getNameIdentifier()` is an interface method with no default (`PsiNameIdentifierOwner.java:14-15`) and no ancestor of `LuaNameRefBaseImpl` supplies one, so a deletion does not compile and measures nothing. The fixture's caret is on a declaring `LuaNameRef`, so the mutated override is on the exact element the template anchors to. Drives `MemberInplaceRenameHandler()`. |
| TC-02 | `REFACT-07-02` | `local coun<caret>ter = 0`⏎`print(counter)` | `RenameHandlerRegistry.getInstance().getRenameHandlers(context)` | the returned list has exactly one element, and it is a `MemberInplaceRenameHandler` | restore `isInplaceRenameAvailable` to the shipped predicate (§3.2) — two handlers become available, the registry drops `MemberInplaceRenameHandler` (`RenameHandlerRegistry.java:114-119`), and the single returned handler is a `VariableInplaceRenameHandler`. The fixture is a file-local declaration, which is the only input for which the shipped predicate returns `true`. Assert the class with an `instanceof`/`is` test: `LuaInplaceRenameHandler` implements `RenameHandler` directly (design §2.3) and is **not** a `MemberInplaceRenameHandler`, so that test distinguishes the platform's handler from Lunar's. It also confirms, for this caret, that Lunar's handler correctly declines the `LuaNameRef` composite a declaration caret supplies. |
| TC-03 | `REFACT-07-04` | `local coun<caret>ter = 0`⏎`print(counter)`⏎`counter = counter + 1` | drive to commit with `"total"` | document is `local total = 0`⏎`print(total)`⏎`total = total + 1` — three occurrences plus the declaration all on `total` | make `LuaNameReferenceSearcher.processQuery` accept no candidate — replace `reference.isReferenceTo(target)` with `false` at `LuaNameReferenceSearcher.kt:76` — so the searcher yields no usages. The commit's own `ReferencesSearch` then finds none either, and the document is left as `local total = 0`⏎`print(counter)`⏎`counter = counter + 1`. All three usages are in this fixture's own file, which is the scope the searcher covers, and every path into this case's reference collection goes through that line. **Not** the `identifierLeafOf` deletion at `:57` that TC-13 uses: `MemberInplaceRenamer.collectRefs` searches **twice** (`MemberInplaceRenamer.java:173-183`) — once on `myElementToRename`, the `LuaNameRef` composite, and once on `getSubstituted()`, which for Lunar is the IDENTIFIER **leaf** and is returned bare at `:365` because a leaf is not a `PsiNameIdentifierOwner`. The second search normalises nothing, so it survives that mutation and yields all three usages, and the case would stay GREEN. `risks-and-gaps.md` Risk 1.5 records the double search. **What this case does not cover**: `REFACT-07-04` says no usage is left on the old text *while the template is live*, and the committed document cannot distinguish live mirroring from the commit's own rename, because both run `LuaRenameProcessor`. The live half is observed by DR-01 probes (d) and (e) and by `human-verification-checklists.md`, not by this case. Drives `MemberInplaceRenameHandler()`: this fixture's caret supplies the declaring `LuaNameRef` (DR-05 probe `a`, same fixture). |
| TC-04 | `REFACT-07-11` | `local counter = 0`⏎`print(coun<caret>ter)` | drive to commit with `"total"` | document is `local total = 0`⏎`print(total)` | delete the leaf→`LuaNameRef` normalisation from `LuaInplaceRenameHandler.invoke` (§2.3), passing `PsiElementRenameHandler.getElement(context)` straight to the delegated `MemberInplaceRenameHandler().doRename` — the leaf fails the `instanceof PsiNameIdentifierOwner` gate at `MemberInplaceRenameHandler.java:56`, control falls through to `performDialogRename` at `:87` (`:88` in the shipped GoLand 2026.1.3). **Phase 4 measured what happens after that fall-through, and it is not what this row originally predicted — the correction is the row's, not the mutant's.** Headlessly `performDialogRename` constructs a real `RenameProcessor` with `initialName = null`, i.e. the empty string, so `LuaRenameProcessor.refuseRewrite` throws `IncorrectOperationException: Rename was not applied: '' cannot be written as a Lua identifier…` out of the driver (`refuseRewrite` at `LuaRenameProcessor.kt:510` ← `renameElement(:259)` ← `RenameProcessor.performRefactoring(:420,:434)` ← `VariableInplaceRenameHandler.performDialogRename(:137)` ← `MemberInplaceRenameHandler.doRename(:88)`). The case therefore reddens on that **escaping exception**, reaching neither its return-value assertion nor its document assertion. Phase 2's own record had already established this for the fall-through; the defect was that this cell was never reconciled to it. **This is the mutant that discharges TC-04's exemption from Phase 2's fail-first pass** (`implementation-plan.md` Phase 2): the leaf→`LuaNameRef` normalisation is the whole of `LuaInplaceRenameHandler`'s own contribution, so removing it is removing the feature. The mutation must be applied in `invoke`, not in `declaringNameRefOf`. Both would redden the case — `renameInPlaceViaHandler` calls `handler.invoke(...)` directly and never consults `isAvailableOnDataContext` (design §6), so a null from the helper would simply return early — but they redden it by different mechanisms, and Phase 4 records the mechanism its row names. Mutating `invoke` isolates the normalisation and reddens through the platform's refusal of the leaf at `MemberInplaceRenameHandler.java:56`; mutating the helper reddens through `invoke`'s own early return, which is the availability gate's claim, not this case's. Reachable only from a caret whose data context supplies the leaf, which is this fixture's (DR-05 probe `b`, same fixture): a caret that supplies the declaring `LuaNameRef` never reaches Lunar's handler (§3.5's availability invariant). Drives `LuaInplaceRenameHandler()` through `renameInPlaceViaHandler` (design §6), **not** `CodeInsightTestUtil.tryInlineRename`, whose parameter type it does not satisfy. Note that `LuaRenameProcessor.substituteElementToRename`'s `resolvedDeclarationLeaf` fallback (`LuaRenameProcessor.kt:107`) is **not** on this path — §3.5 hands the handler the *declaring* `LuaNameRef`, for which `identifierLeafOf` succeeds at `:106` — so mutating that fallback would leave this case green. |
| TC-05 | `REFACT-07-05` | `local coun<caret>ter = 0`⏎`print(counter)`⏎`counter = counter + 1` | run the same fixture twice: once through the in-place handler to commit with `"total"`, once through `myFixture.renameElementAtCaret("total")` | both documents equal `local total = 0`⏎`print(total)`⏎`total = total + 1`, and equal each other | change `LuaRenameProcessor.renameElement` to rewrite the declaration and skip the usage loop — **both** executions change together, the hard-coded expectation reddens, and the equality assertion stays green. Recorded because it is the case that shows why the hard-coded text is the load-bearing half here and the equality is the guard against a route that stops using the processor. Drives `MemberInplaceRenameHandler()` for the in-place half: this fixture's caret supplies the declaring `LuaNameRef` (DR-05 probe `a`, same fixture). |
| TC-06 | `REFACT-07-06` | `local coun<caret>ter = 0`⏎`print(counter)` | start the template with `MemberInplaceRenameHandler().doRename(PsiElementRenameHandler.getElement(context), myFixture.editor, context)` under `TemplateManagerImpl.setTemplateTesting(disposable)`, assert `TemplateManagerImpl.getTemplateState(myFixture.editor) != null`, write `tot` into `getCurrentVariableRange()` in a write command, then cancel with `templateState.gotoEnd(true)` | document is exactly `local counter = 0`⏎`print(counter)` | change `LuaNameRefBaseImpl.getNameIdentifier()` (§3.1) to `= null`. The case asserts, before sending <kbd>Esc</kbd>, that a template started — `TemplateManagerImpl.getTemplateState(myFixture.editor) != null`, the assertion `implementation-plan.md` Phase 2 makes binding for every document-layer case — and under the mutation no template starts. **The case goes red on a THROWN error, not a failed assertion**: `getNameIdentifier()` returning null makes `getSelectedInEditorElement` miss its ranges and reach `LOG.error` (`InplaceRefactoring.java:859` in GoLand 2026.1.3, `:860` in the `intellij-community` checkout design §1 Provenance names — Risk 1.3 records why both are cited), which throws under `TestLoggerFactory` inside `doRename` — before the template-started assertion executes. Same mutation and fixture as TC-01, so it fails by the same route; see `implementation-plan.md`'s thrown-failure list. **This case is a gate, not a guard.** The template-started assertion is what makes it one: with only the document text asserted, the mutated tree leaves the file at exactly the expected content and the case passes with the feature absent. The template-started assertion is what removes that. The fixture's caret is on a declaring `LuaNameRef`, which is the element the mutated accessor sits on. **What the mutation does not reach** is Esc-restore itself, which is entirely the platform's: `InplaceRefactoring`'s own template listener reverts the document (§3.3 step 9) and this feature adds no commit path, no document write and no template listener of its own. So this case gates "a template started and the document came back unchanged"; that the *restoration* is what returned it is evidenced live, by `human-verification-checklists.md`'s "Cancel restores" item and DR-01 probe (e). |
| TC-07 | `REFACT-07-07` | `local coun<caret>ter = 0`⏎`print(counter)` | drive to commit with `"end"` | document is exactly `local counter = 0`⏎`print(counter)` | delete the reserved-word half of `LuaNamesValidator.isIdentifier`, leaving only `IDENTIFIER_PATTERN.matches(name)` (`LuaNamesValidator.kt:21`) — `end` matches the pattern, `findProblem()` no longer cancels the template, and the commit proceeds into `LuaRenameProcessor.renameElement`. **It reddens by exception, not by text**: `LuaElementFactory.createIdentifier(project, "end")` returns null for a reserved word — its own KDoc says why (`LuaElementFactory.kt:12-28`: the synthetic `goto end` rolls back and leaves no `LuaGotoStatement`) — so `refuseRewrite` throws `IncorrectOperationException` (`LuaRenameProcessor.kt:509-510`) before any edit. The document stays as asserted; the uncaught exception is what fails the case. The case must therefore assert the document text **and** that no exception escapes. `end` is a Lua keyword *and* matches the identifier pattern, so this fixture reaches the deleted clause. Drives `MemberInplaceRenameHandler()`. |
| TC-08 | `REFACT-07-08` | `local coun<caret>ter = 0`⏎`local total = 1`⏎`print(counter + total)` | drive to commit with `"total"` | every assertion below, and the **message** one is what makes the case a gate. **THROWS** — the driver throws `com.intellij.refactoring.BaseRefactoringProcessor$ConflictsInTestsException`; it escapes, so the call cannot be bare. **MESSAGE** — the exception **contains** `LuaBundle.message("refactoring.rename.conflict.capture", "total", "counter")`, as a member of `getMessages()` or a substring of `getMessage()`; **contains, never equals**, because this fixture also trips the shadow rule and the exception carries that message too, so an equality assertion would couple TC-08 to `refactoring.rename.conflict.shadow`'s wording. **DOCUMENT** — `myFixture.checkResult` is the unchanged fixture | delete the capture rule from `LuaRenameConflictDetector.collisions` (`LuaRenameConflictDetector.kt:120-131`, the `captures(target, usages)` term at `:125`) — **measured**: the rename is still refused, still by `ConflictsInTestsException`, and the document is still unchanged, because the fixture matches **two** rules and the shadow rule still fires (`target.kind` is `LOCAL_VARIABLE`, so `isFileLocal` selects `shadows(target)` at `:127`). What changes is the message: the capture message is gone. So THROWS and DOCUMENT stay green under the mutation and **MESSAGE is the only assertion that reddens** — a TC-08 asserting only the exception type and the unchanged text would be green under its own named mutation, which is Risk 1.2's shape. **Class of mutant**: this deletes a `LuaRenameConflictDetector` rule, which the dialog path shares, so `LuaRenameConflictTest.testCaptureOfRenamedUsageIsReported` (`LuaRenameConflictTest.kt:53-60`) reddens under it too — like TC-09's M2 it is not absence-detecting, and TC-08 gets its absence evidence from Phase 2's fail-first pass, which it is **not** exempt from. Drives `MemberInplaceRenameHandler()`: the caret is on a `local` declaration whose name is not already in scope, the shape DR-05 probe `a` measured as supplying the declaring `LuaNameRef`, and DR-04 probe (d) re-confirmed on this exact fixture. **Drain before reading anything** — `tryInlineRename` drains internally (`CodeInsightTestUtil.java:265`); a hand-driven variant that reads between `gotoEnd` and the drain reads the corrupt intermediate. |
| TC-09 | `REFACT-07-09` | `---@param a number`⏎`local function f(<caret>a) return a end` — the shape `LuaCatsParamRenameTest` already drives through the dialog path (`LuaCatsParamRenameTest.kt:52-59`) | drive to commit with `"count"` | document is `---@param count number`⏎`local function f(count) return count end` | **several mutants, with different jobs; Phase 4 executes every one named here.** **M1 — the absence-detecting mutant, and the one that discharges TC-09's exemption from Phase 2's fail-first pass** (`implementation-plan.md` Phase 2): delete the leaf→`LuaNameRef` normalisation from `LuaInplaceRenameHandler.invoke` (§2.3), passing `PsiElementRenameHandler.getElement(context)` straight to the delegated `MemberInplaceRenameHandler().doRename` — the same mutant TC-04 names, and reachable from this fixture for the same measured reason: the context supplies the parameter's IDENTIFIER leaf, which fails the `instanceof PsiNameIdentifierOwner` gate at `MemberInplaceRenameHandler.java:56`, so control falls through to `performDialogRename` at `:87` (`:88` in the shipped GoLand 2026.1.3). **Phase 4 measured the observable and it is an escaping exception, not a `false` return** — see TC-04's cell for the stack: `performDialogRename` runs a real `RenameProcessor` with `initialName = null` and `LuaRenameProcessor.refuseRewrite` throws `IncorrectOperationException: Rename was not applied: '' cannot be written as a Lua identifier…` before either assertion runs. **M1 is absence-detecting as claimed**: executed 2026-08-26, it reddened exactly TC-04 and TC-09 and nothing else across the six rename and usage suites. Apply it in `invoke` for the reason TC-04's row gives. **M2 — the requirement's substance**: replace the `---@param` hoist in `LuaRenameProcessor.renameElement` with `null` — delete the `if (declarationKind == LuaDeclarationKind.PARAMETER) LuaCatsParamRenamer.preparedRename(declarationLeaf, oldName, newName)` branch at `LuaRenameProcessor.kt:262-267`, leaving `applyCatsTagRewrite = null` — the code renames and the document keeps `---@param a number`. The fixture's caret is on a parameter carrying a `---@param` tag, which is the only shape that reaches that branch. **M2 is reachable but not absence-detecting, and must not be recorded as if it were**: that branch is on the commit path the *dialog* shares, and `LuaCatsParamRenameTest.testParamTagFollowsParameter` (`LuaCatsParamRenameTest.kt:50-60`) reddens under it too, with this feature entirely absent. What M2 proves is that the in-place commit reached `LuaRenameProcessor.renameElement` at all — Ground 1 of the route decision, which is what `REFACT-07-09` is a parity requirement about. **M3 — the second absence-detecting mutant**: revert design §3.6's normalisation, so `renameElement` classifies its raw argument again (`val declarationKind = LuaDeclarationSite.kindOf(element)`, at `LuaRenameProcessor.kt:255-256` — `:255` is the normalisation, `:256` the classification the mutation edits). On the in-place route `MemberInplaceRenamer.getSubstituted()` re-derives the target as a `LuaNameRef` **composite** (`MemberInplaceRenamer.java:367-372`, measured by DR-01 at range `(36,37)`), whose `kindOf` is null, so the `---@param` clause at `:262-267` never builds: the code renames and the document keeps `---@param a number` while reading `f(count)`. **M3 reddens this case and leaves `LuaCatsParamRenameTest` green**, which is what M2 fails to do — the dialog path substitutes to the leaf before `renameElement` (`:105-117`), and `kindOf` of a leaf is `PARAMETER` with or without the normalisation. It is therefore the mutant that isolates §3.6's contribution, exactly as M1 isolates §2.3's. **That is measured over the whole suite, not a subset.** Phase 4's sweep ran six test classes, which left three of the seven suites that drive `renameElement` (`LuaRenameTest`, `LuaRenameCrossFileTest`, `LuaRequireRenameTest`) unmutated and made the verdict a universal drawn from a partial run. Re-executed 2026-08-26 under `test --rerun --no-build-cache` with **no `--tests` filter**: `2867 tests completed, 1 failed, 1 skipped` — TC-09 alone, across every class in the repository. **Not** a mutation of `isMemberInplaceRenameAvailable`: neither driver consults an availability predicate (§6), so any predicate mutation leaves a document-layer case green. Drives `LuaInplaceRenameHandler()` through `renameInPlaceViaHandler` (design §6), **not** `CodeInsightTestUtil.tryInlineRename`. The data context supplies the parameter's IDENTIFIER **leaf** at this caret — measured on this exact fixture, DR-05 probe `d`, `textRange (36,37)` — which `MemberInplaceRenameHandler` refuses at `MemberInplaceRenameHandler.java:56` before falling through to `performDialogRename` at `:87`; Lunar's handler accepts it, because `kindOf` is `PARAMETER` (`LuaDeclarationSite.kt:243`), `isFileLocal` is `true` (`:20`) and the leaf's parent is a `LuaNameRef` (measured, same probe). The commit still runs `LuaRenameProcessor.renameElement` through `MemberInplaceRenamer.performRenameInner` (design §3.5, §3.3 step 8), which is what keeps the mutation reachable. |
| TC-10 | `REFACT-07-10` | `con<caret>fig = {}`⏎`print(config)` | ask the registry for available handlers | no in-place handler is available; the returned handler is the platform's default element rename handler | delete the `isFileLocal` clause from `isMemberInplaceRenameAvailable` (§3.2) — the returned handler becomes a `MemberInplaceRenameHandler` and the assertion fails. The data context supplies the declaring `LuaNameRef` at this caret (DR-05 probe `e`, this exact fixture, `LuaNameRefImpl` at `(0,6)`), so §3.2's predicate is the one on the path. The fixture's declaration classifies `GLOBAL_VARIABLE`, whose `isFileLocal` is `false` (`LuaDeclarationSite.kt:24`), which is the input that clause refuses. The corresponding `isFileLocal` mutation in `LuaInplaceRenameHandler.declaringNameRefOf` (§3.5 step 2) is **inert** here — Lunar's handler never sees this caret's element, because its first step requires an `IDENTIFIER` node type — so do not substitute it. |
| TC-11 | `REFACT-07-13` | `::ret<caret>ry::`⏎`goto retry` | `RenameHandlerRegistry.getInstance().getRenameHandlers(context)` | the returned list has exactly one element, and it is a `MemberInplaceRenameHandler` | delete the `elementToRename is LuaLabelName` clause from `isMemberInplaceRenameAvailable` (§3.2) — no in-place handler is available for a label, the registry falls back to `PsiElementRenameHandler` (`RenameHandlerRegistry.java:121-123`) and the assertion fails. The fixture is a label declaration, the only input that clause admits. **This is a registry-layer case, not a document-layer one** (§6 layer 1): `tryInlineRename` bypasses both predicates, so a document-layer label case cannot see this clause at all. REFACT-04's own `LuaLabelRenameTest` carries the document-layer half, and the Acceptance Criteria require it green. The clause is on this caret's path: DR-05 probe `g`, on this exact fixture, measured the data context supplying `LuaLabelNameImpl` with `isPsiNameIdentifierOwner` true, `MemberInplaceRenameHandler().isAvailableOnDataContext` true, and `getRenameHandlers` returning that handler alone — so the expected outcome is today's measured behaviour and the mutation reaches the predicate. Lunar's own handler declines a label composite, because `declaringNameRefOf`'s first step requires an `IDENTIFIER` node type and a `LuaLabelName` is a `LABEL_NAME` composite (design §3.5, step table row 1). |
| TC-12 | `REFACT-07-14` | `for i<caret> = 1, 10 do`⏎`  print(i)`⏎`end` | ask the registry for available handlers | no in-place handler is available | **Guard.** No mutation in code this repo can edit reddens it, and that is measured rather than argued: at this caret the data context supplies **null** and `RenameHandlerRegistry` returns an **empty** handler list (DR-05 probes `f`/`f2`/`f3`, all three caret placements, so it is not a whitespace artifact). No Lunar predicate is on the path — not `isMemberInplaceRenameAvailable`, whose gate at `MemberInplaceRenameHandler.java:46` is never reached with a null element, and not `LuaInplaceRenameHandler.declaringNameRefOf`, which returns at its first line for a null element and so never evaluates step 3. Widening §3.2's first clause from `LuaNameRef` to `PsiNamedElement` does not bring it into reach either, because `numericForStatement ::= FOR IDENTIFIER '='` (`lua.bnf:152`) produces no `LuaNameRef` to anchor on. The only reddening mutation is on the grammar — adding `nameRef` to `numericForStatement` and regenerating — which is outside the routine sweep. A green run of this case is evidence that nothing regressed, not that the feature works. Its KDoc must say **guard** in its first line. `risks-and-gaps.md` Risk 1.7 carries the argument. |
| TC-13 | `REFACT-07-12` | `local coun<caret>ter = 0`⏎`print(counter)`⏎`counter = counter + 1` | run Find Usages on the declaration | exactly the reads and writes are reported and the declaration itself is not among them | replace `reference.isReferenceTo(target)` with `false` at `LuaNameReferenceSearcher.kt:76` — the searcher yields nothing and the "exactly the reads and writes are reported" half fails with `expected:<3> but was:<0>`. **This cell was corrected in Phase 4, by a run.** It previously named the `identifierLeafOf` deletion at `:57`, which **SURVIVED**: Find Usages passes the IDENTIFIER **leaf** (`CodeInsightTestFixtureImpl.java:1249-1263`), so with the normalisation deleted `target` is that same leaf, `kindOf` is `LOCAL_VARIABLE` rather than null, the `:58` gate does not return early and the search proceeds unchanged — the whole 64-test sweep stayed green. Nor can the case be re-pointed at the composite that would reach `:57`: `LuaFindUsagesProvider.canFindUsagesFor` is `kindOf(element) != null` (`:35`), false for a `LuaNameRef`. `risks-and-gaps.md` Risk 1.11 raised this as read-not-run and Phase 4 settled it. **The cost of the correction, stated rather than hidden**: at `:76` the mutation is shared with TC-03 and with every other case that needs the searcher to find a usage — a set that grows with each new consumer, so it pins the *searcher* rather than the Find Usages path specifically. All three usages are in this fixture's own file, which is the scope the searcher covers. **The "declaration is not among them" half is a guard, not a gate**: the identity check at `LuaNameReference.kt:264` is masked by the very next line — `shadowsRatherThanUses(self)` at `:265` is `kindOf(host.identifier)?.isFileLocal == true` (`:189-192`), which is true for the declaring `LuaNameRef` of `local counter` — so deleting `:264` alone leaves the case green. **TC-03 names the same mutation, and after Phase 4's correction that is correct rather than a duplication to repair.** The reason the two cells once differed was the `:57` deletion, which TC-03's cell rightly refused because `MemberInplaceRenamer.collectRefs` searches twice (`MemberInplaceRenamer.java:173-183`) and its second search on `getSubstituted()` passes an already-normalised leaf. That reasoning was sound and the measurement showed `:57` is unreachable from **either** case. |
| TC-14 | `REFACT-07-12` | `local unu<caret>sed = 0` | run Safe Delete on the declaration | the whole `local unused = 0` statement is removed | change `LuaSafeDeleteProcessor`'s elevation so the bare IDENTIFIER leaf is deleted instead of its statement — the document is left as `local  = 0`, a parse error. The fixture's declaration has a statement-level container, which is what the elevation targets. |
| TC-15 | `REFACT-07-15` | `local coun<caret>ter = 0`⏎`print(counter)`⏎`counter = counter + 1` | drive the template to commit with `"total"` and assert the whole document | document is `local total = 0`⏎`print(total)`⏎`total = total + 1` | delete the final `LuaDeclarationSite.kindOf(element) != null` disjunct from `LuaRenameProcessor.canProcessElement` (`LuaRenameProcessor.kt:90`), leaving only the `element is LuaNameRef` test — the processor no longer claims the substituted IDENTIFIER **leaf**, so `PsiElementRenameHandler.getRenameErrorMessage`'s first clause is satisfied — `hasRenameProcessor` is false and the leaf is not a `PsiNamedElement` (`PsiElementRenameHandler.java:150-158`) — and `canRename` returns a message. **The case reddens by exception, not by an unchanged document.** `canRename` calls `CommonRefactoringUtil.showErrorHint(...)` at `PsiElementRenameHandler.java:139-140` *before* its `return false` at `:141`, and `showErrorHint` throws `RefactoringErrorHintException` in unit-test mode (`CommonRefactoringUtil.java:84-85`). The throw propagates out of `RenamePsiElementProcessorBase.substituteElementToRename` — which would otherwise have returned at its `canRename` gate, `:244` — through `MemberInplaceRenameHandler.doRename` and out of `tryInlineRename`, which therefore never returns anything and never asserts the document. Lunar's own code records this behaviour, at `LuaRenameProcessor.kt:97-99`. The case must assert the expected document text **and** that no exception escapes, exactly as TC-07 does; the escaping `RefactoringErrorHintException` is what fails it. The frame design §3.3 step 3 identifies as "could block and does not" is silent **in the IDE**, where `showErrorHint` paints a balloon; it is loud in the fixture that observes it, and this case is the only thing that would see either. Reachable from this fixture, whose caret is on a declaring `LuaNameRef`. **Not** the historical `getUseScope`-plus-no-`getNameIdentifier` mutation: that document state (`local counter = 0`⏎`print()`⏎` =  + 1`) was measured on Route A, which this design does not take, and half of it does not compile (see TC-01). Drives `MemberInplaceRenameHandler()`. |
| TC-16 | `REFACT-07-01` | `for ke<caret>y, value in pairs(t) do`⏎`  print(key, value)`⏎`end` | drive to commit with `"entry"` | document is `for entry, value in pairs(t) do`⏎`  print(entry, value)`⏎`end` | delete the generic-`for` arm from `LuaDeclarationSite.kindFromNameRefGrandParent` — `grandParent is LuaNameList && grandParent.parent is LuaGenericForStatement -> LuaDeclarationKind.GENERIC_FOR_VARIABLE` (`LuaDeclarationSite.kt:244-245`). **This is the kind-specific mutant, which is exactly what TC-01's is not**: TC-01 drives the `local` kind and mutates a shared primitive, so a regression confined to the generic-`for` kind left the whole suite green until this case existed. Drives `MemberInplaceRenameHandler()` through `CodeInsightTestUtil.tryInlineRename`: at this caret the data context supplies the declaring `LuaNameRef` **composite**, measured on this exact fixture — DR-01 probe `b4b`, `LuaNameRefImpl` at `textRange (4,7)`, `MemberInplaceRenameHandler.isAvailableOnDataContext` true and Lunar's own handler false. **Phase-5-remediation verdict: RED**, executed 2026-08-26 over the full suite — the case reddens on an **escaping exception**, not a wrong document: `kindOf` answers null for the composite, `identifierLeafOf` yields nothing, and the `resolvedDeclarationLeaf` fallback refuses, so `CommonRefactoringUtil$RefactoringErrorHintException: Cannot perform refactoring.\nCannot determine which declaration this name refers to…` escapes from `LuaRenameProcessor.refuse` (`:518`) ← `resolvedDeclarationLeaf` (`:379`) ← `substituteElementToRename` (`:107`). `2867 tests completed, 3 failed` — this case, `LuaDeclarationSiteTest.testKindOfEveryDeclarationShape`, and `LuaRenameTest.testRenameGenericForVariable`, the dialog-path sibling. |
| TC-17 | `REFACT-07-01` | `local function hel<caret>per() return 1 end`⏎`print(helper())` | drive to commit with `"compute"` | document is `local function compute() return 1 end`⏎`print(compute())` | delete the `local function` arm from `LuaDeclarationSite.kindFromNameRefGrandParent` — `grandParent is LuaLocalFuncDecl -> LuaDeclarationKind.LOCAL_FUNCTION` (`LuaDeclarationSite.kt:239`). Kind-specific for the same reason TC-16's is. `LuaInplaceRenameHandler.declaringNameRefOf`'s third step then refuses the leaf and `invoke` returns before starting a template. Drives `LuaInplaceRenameHandler()` through `renameInPlaceViaHandler` (design §6), **not** `CodeInsightTestUtil.tryInlineRename`: at this caret the data context supplies the name's IDENTIFIER **leaf**, measured on this exact fixture — DR-01 probe `b5`, `LeafPsiElement(IDENTIFIER)` at `textRange (15,21)`, `MemberInplaceRenameHandler.isAvailableOnDataContext` false and Lunar's handler true. This is the third of the three leaf-supplying carets design §3.5 names, beside TC-04's usage caret and TC-09's parameter caret, and the only one that had no case. **Phase-5-remediation verdict: RED**, executed 2026-08-26 over the full suite — `AssertionFailedError: no in-place template started — the document assertion below is satisfied by the feature being absent`, i.e. the case reddens on the template-started assertion rather than on its text, which is what stops it being satisfied by the feature being absent. `2867 tests completed, 3 failed` — this case, `LuaDeclarationSiteTest.testKindOfEveryDeclarationShape`, and `LuaRenameTest.testRenameLocalFunctionWithRecursiveCall`. |

## Acceptance Criteria

- [x] `REFACT-07-01`, `-03`: TC-01 passes and reddens under its named mutation, and each of the
      other three declaration kinds `REFACT-07-01` names has its own document-layer case that
      does the same — TC-09 for the parameter kind, TC-16 for generic-`for`, TC-17 for
      `local function`. TC-01 alone does not discharge this row: its mutation lands on a shared
      primitive, so it would break all four kinds in production while observing only the `local`
      one, and a kind-specific regression would leave the suite green.
- [x] `REFACT-07-02`: TC-02 passes and reddens under its named mutation.
- [x] `REFACT-07-04`: TC-03 passes and reddens under its named mutation.
- [x] `REFACT-07-05`: TC-05 passes, with both the text assertion and the two-path equality asserted.
- [x] `REFACT-07-06`: TC-06 passes and reddens under its named mutation — it asserts that a template
      started before <kbd>Esc</kbd> is sent, which is what makes it a gate — and the live
      "Cancel restores" checklist item is evidenced, because the mutation does not reach the
      restoration itself. **TC-06 is also the only executed check on design §3.3 step 9**, which
      stays `Read, not run` with no de-risking task behind it: if the platform does not revert on
      <kbd>Esc</kbd>, this case is where that surfaces.
      **Settled in Phase 5, and the platform does revert.** The template was started on
      `local counter = 0`, `tot` typed so all four segments read `tot`
      (`phase-5-live-evidence/04-esc-template-live-with-tot.png` — which is what stops the next
      observation being a no-op), then <kbd>Esc</kbd>: the document returned to the original three
      lines and *Save All* left the file's md5 at `e13ea3dd80bb4182ba7494ef5b376db0`, identical to
      the pristine fixture (`05-esc-restores-byte-for-byte.png`). This was the one `Must` whose
      acceptance rested on a step nobody had run.
- [x] `REFACT-07-07`: TC-07 passes and reddens under its named mutation.
- [x] `REFACT-07-08`: TC-08 passes and reddens under its named mutation, **and the recorded reason
      for the redness is the missing capture message** — not the exception type and not the document
      text, both of which the measurement shows survive the mutation. A verdict recording either of
      those has recorded a green assertion as if it were the gate.
- [x] `REFACT-07-09`: TC-09 passes and reddens under **every** mutant its row names — M1, M2 and
      M3 — with the mechanism recorded separately for each, and with `LuaCatsParamRenameTest` green
      under M1 and M3 and red under M2. M2 alone does not satisfy this criterion: it is shared with
      the dialog path and reddens with this feature absent. TC-09 is driven through
      `LuaInplaceRenameHandler` and `renameInPlaceViaHandler` — a run of it against
      `MemberInplaceRenameHandler` measures the platform's refusal of the parameter leaf, not this
      requirement.
- [x] `REFACT-07-10`: TC-10 passes and reddens under its named mutation.
- [x] `REFACT-07-11`: TC-04 passes and reddens under its named mutation. DR-05 has recorded what
      the data context supplies at a usage caret (probes `b`, `c`).
- [x] `REFACT-07-12`: TC-13 and TC-14 pass and redden under their named mutations — for TC-13 that
      is its gating half only, its "declaration is not among them" half being a recorded guard — and
      DR-03's consumer audit reports a decision for **every** row of design §4's table, whose
      enumeration is the corrected search §4 states — the `platform/` grep **and** the shipped-build
      enumeration — rather than a hand-picked subset. **DR-03 has run (2026-08-26), §4 is reconciled
      to the compiled platform, and the consumers DR-03 found missing are now rows.** Ticking this
      item additionally requires that the rows §4 marks **NOT RUN** or **NOT APPLICABLE** are still
      exactly the ones `risks-and-gaps.md` DR-03 names as outstanding — the two minimap rows, blocked
      by provenance, and the two completion-ranking rows routed to Phase 5 task 5.1a. A verdict
      table that appears complete over rows nobody ran does not satisfy this criterion.
- [x] `REFACT-07-13`: TC-11 passes and reddens under its named mutation, and every case in
      `LuaLabelRenameTest` is green.
- [x] `REFACT-07-14`: TC-12 passes and is recorded as a **guard** with the argument its row
      states, its KDoc saying so in its first line. A verdict recorded as a passed mutation does not
      satisfy this criterion, and neither does a green run with no argument attached.
- [x] `REFACT-07-15`: TC-15 passes and reddens under its named mutation, and the recorded reason for
      the redness is the one the case names — an escaping `RefactoringErrorHintException` from
      `CommonRefactoringUtil.showErrorHint` — not merely "the test failed".
- [x] `REFACT-07-02`: DR-02 has confirmed, for a declaration caret, a usage caret **and** a
      parameter caret, both halves — design §3.5's pairwise availability invariant, **and** the
      premise that no other registered `renameHandler` is available there, recorded as the
      enumerated list of handlers whose `isRenaming` was true (`risks-and-gaps.md` DR-02 Table 1;
      raw rows in `dr-02-evidence/measured-rows.txt`). The premise held, so the branch for a false
      premise was not applied. **This criterion is met; `REFACT-07-02` itself is not — the
      requirement still needs TC-02.**
- [x] The full unit suite is green, and the regression-relative baseline is unchanged.
- [x] `verify-in-ide` confirms a live <kbd>Shift+F6</kbd> on a Lua local starts a template that can
      be typed into and committed, per `human-verification-checklists.md`.
      Run 2026-08-26 against a GoLand 2026.1.3 sandbox whose loaded jar was proved by bytecode to
      carry `LuaInplaceRenameHandler` and `LuaNameRefBaseImpl implements PsiNameIdentifierOwner`.
      Every checklist item is complete with an attached screenshot except the completion-ranking
      item, which stays unticked with its reason (`REFACT-07-12`'s row). The run produced four
      user-visible findings — undo not restoring after a rename commit, Find Usages and Safe Delete
      both refused at a declaration caret, and a platform `ThreadingAssertions` SEVERE on the
      invalid-identifier rollback — and **none is attributable to this feature**: each is either
      measured identical on DR-03's base and treatment rows, reproduced on the untouched dialog
      path, or carries zero `net.internetisalie` stack frames.

## Non-Functional Requirements

- **Threading** — no component added by this feature blocks the EDT with I/O, index reads or PSI
  resolution. The availability predicate is a pure PSI shape test
  (`docs/engineering-contract.md` §1: *"never block the EDT with I/O, heavy parsing"*).
- **Memory** — no component retains a hard reference to `Project`, `Editor`, `PsiFile` or
  `VirtualFile`. `LuaDeclarationSite` is stateless and stays so.
- **Latency** — the availability predicate runs on every <kbd>Shift+F6</kbd> and on rename-action
  update; it must remain O(depth of the caret's ancestor chain) with no allocation of a provider
  per call.
- **No regression budget for anything Lunar ships a feature for** — Find Usages, Safe Delete,
  identifier highlighting, Go to Declaration, the Structure View and the interface-branching
  inspections must not move, and `REFACT-07-12` is a hard requirement on that, not an aspiration.
  The platform behaviours `REFACT-07-12` lists as accepted are the whole of the budget: a behaviour
  outside both lists moving is a defect, not a newly-discovered acceptance.

## Traceability

| REFACT-01 requirement | Relationship |
| :--- | :--- |
| `REFACT-01-12` (In-place rename in the editor) | **Delegated here.** REFACT-01's row is frozen; the proposed replacement wording is in `risks-and-gaps.md` RD-1 for the supervisor to apply. |
| `REFACT-01-14` (Conflict detection) | **Consumed unchanged.** `REFACT-07-08` requires the in-place path to reach it; it adds no rules. |
| `REFACT-01-16` (`---@param` propagation) | **Rules consumed unchanged; its gate is repaired.** `REFACT-07-09` requires parity, and the route choice in design §2 exists to keep it. Measured: the clause is keyed on `LuaDeclarationSite.kindOf` of `renameElement`'s raw argument, so **any** caller passing a `LuaNameRef` composite loses it — a latent REFACT-01 defect this feature is the first to expose. Design §2.5/§3.6 normalise; `LuaCatsParamRenamer` itself is untouched. |
| `REFACT-01-10` (Valid identifier) | **Consumed unchanged.** `REFACT-07-07` requires the template to honour `LuaNamesValidator`. |
| `REFACT-01-17` (Rename a `::label::`) | **Untouched.** `REFACT-07-13` pins it as a non-regression. |
