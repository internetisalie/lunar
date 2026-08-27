---
id: "REFACT-07-HVC"
title: "Human Verification Checklists"
type: "qa"
parent_id: "REFACT-07"
priority: "high"
folders:
  - "[[features/refactoring/07-inplace-rename/requirements|requirements]]"
---

# REFACT-07: Human Verification Checklists

Run through `.agents/skills/verify-in-ide`. Attach a screenshot per checked item to
`phase-5-live-evidence/`.

## Run record

| Item | Value |
| :--- | :--- |
| Run date | 2026-08-26, Phase 5 |
| Source commit | `077734d9` (main, primary worktree, tree clean) |
| IDE | GoLand 2026.1.3 (`GO-261.25134.147`), `./gradlew runIde` sandbox, JBR 25.0.3 |
| Host | `gce-builder` **gce** backend (`GCE_BUILDER_BACKEND=gce`); the libvirt host and its LAN gateway were both unreachable for the whole session |
| Display | Xvfb `:99`, 1920x1080, openbox; window maximised to 1920x1061 |
| Fixture | `~/refact07/proj/inplace.lua`, pristine md5 `e13ea3dd80bb4182ba7494ef5b376db0` |
| Drive | `xdotool` for input, `scrot` for capture, `Go to Line:Column` for exact caret placement |
| Evidence | `phase-5-live-evidence/` |

**Which build was loaded, proved against bytecode rather than the version string.** The sandbox jar
`build/idea-sandbox/GO-2026.1.3/plugins/lunar/lib/lunar-0.18.0.jar` contains
`net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameHandler.class`, its bundled
`META-INF/plugin.xml` carries
`<renameHandler implementation="net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler" />`,
and `javap -p` on `LuaNameRefBaseImpl.class` reports
`implements com.intellij.psi.PsiNameIdentifierOwner` with `getNameIdentifier()`. Those are Phase 1's
and Phase 3's deltas; the version string `lunar (0.18.0)` is identical before and after this feature
and would have proved nothing.

**Caret placement was read back, not assumed.** Every caret in this run was set with *Go to
Line:Column* and confirmed against the status-bar readout before the action was sent; the same
dialog's `Offset:` field is what the <kbd>Ctrl+D</kbd> rows below record. Two early attempts drifted
because a **File Cache Conflict** modal was holding the input queue after the fixture was rewritten
from a shell while the editor buffer was dirty — the fix is to save the buffer *before* rewriting the
file and to re-sync with <kbd>Ctrl+Alt+Y</kbd>, and the affected measurements were re-run from a
verified caret.

**Fixture file** — create `inplace.lua` in the loaded Lua project:

```lua
---@param a number
local function f(a) return a end

local counter = 0
print(counter)
counter = counter + 1

config = {}
print(config)

for i = 1, 10 do
  print(i)
end

::retry::
goto retry
```

## The template starts and is usable — `REFACT-07-01`, `-03`, `-04`

- [x] Caret inside `counter` on the `local counter = 0` line; press <kbd>Shift+F6</kbd>.
      **No dialog appears.** An inline editing box surrounds `counter` at the declaration.
      → **PASS.** Caret 4:10. `wmctrl` listed only the project window at the moment of capture, so no
      dialog and no chooser existed. `01-template-starts-declaration.png`.
- [x] **Every** `counter` occurrence below the declaration is highlighted as a linked segment — the
      one inside `print(counter)`, and **both** of the two on the `counter = counter + 1` line.
      Count them on the line, not from memory: that line reads `counter` twice, and a check that
      expects one occurrence there passes while a usage is silently left behind, which is the exact
      corruption `REFACT-07-04` exists to exclude.
      → **PASS, counted on the line: 3 linked segments.** `print(counter)` has one; the assignment
      line has **two**, both boxed — the one before `=` and the one after.
      `01-template-starts-declaration.png`.
- [x] Type `total`. All highlighted occurrences change to `total` as you type — none goes blank,
      none keeps `counter`.
      → **PASS.** All four positions read `total`. `02-typing-updates-every-occurrence.png`.
- [x] Press <kbd>Enter</kbd>. The template closes and the file reads `local total = 0`,
      `print(total)`, `total = total + 1`.
      → **PASS**, read back off disk after *Save All*: `local total = 0` / `print(total)` /
      `total = total + 1`. `03-enter-commits-no-balloon.png`.
- [x] **No internal-error balloon appears** at any point.
      → **PASS on screen** — no balloon in any capture of this run, and no error indicator in the
      status bar. **One qualification, from the log rather than the screen**: `idea.log` carries a
      single `ThreadingAssertions` SEVERE, *"Read access is allowed from inside read-action only"*,
      raised on the **invalid-identifier rollback** path below. Its stack contains **zero
      `net.internetisalie` frames** — it runs
      `VariableInplaceRenamer.performOnInvalidIdentifier` → `tryRollback` (`:411-412`) →
      `ModCommandBatchExecutorImpl` → `FileDocumentManagerBase.getDocument` — so it is a platform
      frame, it is a *soft* assertion (logged, not thrown), and the behaviour it accompanies was
      correct. Full trace in `threading-assertion-stack.txt`. `grep -c "	at net.internetisalie"`
      over the whole session log is `0`.

## Cancel restores — `REFACT-07-06`

- [x] Undo back to the original file. Caret in `counter`; <kbd>Shift+F6</kbd>; type `tot`; press
      <kbd>Esc</kbd>.
      → **Done**, with one deviation forced by the undo finding below: undo does not restore after a
      rename commit, so the file was restored by rewriting it and re-syncing the editor, verified
      back to the pristine md5 before the template was started. Template confirmed live with all
      four segments reading `tot` before <kbd>Esc</kbd> was sent —
      `04-esc-template-live-with-tot.png` — which is what makes the next line a measurement rather
      than a no-op.
- [x] The file reads exactly as it did before: `local counter = 0`, `print(counter)`,
      `counter = counter + 1`.
      → **PASS — byte-for-byte.** After <kbd>Esc</kbd> the editor read the original three lines and
      *Save All* left md5 at `e13ea3dd80bb4182ba7494ef5b376db0`, identical to the pristine fixture.
      `05-esc-restores-byte-for-byte.png`. **This is the live confirmation design §3.3 step 9
      deferred and no spike ever took**: the platform does revert on <kbd>Esc</kbd>.

## Undo after a commit — `risks-and-gaps.md` Risk 1.4, Gap 2.3

- [x] Perform a full in-place rename of `counter` to `total` and commit it.
      → Done, as the first section above.
- [x] Press <kbd>Ctrl+Z</kbd> **once**. Record whether the file is fully restored, or whether a
      second <kbd>Ctrl+Z</kbd> is needed.
      → **FINDING: the file is not restored, and no number of undos restores it.** *Edit ▸ Undo*
      is present and **enabled**, and three invocation routes were tried — the <kbd>Ctrl+Z</kbd>
      keystroke, clicking the *Edit ▸ Undo* menu item, and *Find Action ▸ Undo ▸ Enter*. After all
      three the document still read `total`, and the undo entry was still sitting at the top of the
      stack. `07-undo-does-not-restore.png`.
- [x] Record the undo action's name as shown in **Edit → Undo**.
      → **"Undo Renaming Lua Name Ref Impl cou…"** (truncated in the menu).
      `06-undo-label-inplace-path.png`. The label exposes the PSI implementation class name
      `LuaNameRefImpl`, de-camel-cased, because nothing supplies an `ElementDescriptionProvider` for
      the composite. Cosmetic, user-visible, and separate from the restore failure.
- [x] If more than one undo is required, file a bug and add a roadmap row; it is not a reason to
      change route.
      → **Not filed, deliberately, because the control says it is not this feature's.** Two
      measurements bound the finding:
      **(a) A positive control proves undo works in this session at all** — typing `XX` into the
      file and pressing <kbd>Ctrl+Z</kbd> removed it.
      **(b) The same failure reproduces on the dialog path**, which this feature does not touch:
      renaming the global `config` to `settings` through the modal *Rename* dialog and then pressing
      <kbd>Ctrl+Z</kbd> also left the file at `settings`. Its undo entry reads **"Undo Renaming
      global variable confi…"** — a properly user-facing label, and enabled —
      `08-undo-label-dialog-path-control.png`.
      So the behaviour is **common to both rename routes** and is not introduced by in-place rename.
      Whether it is a standing Lunar defect or an artifact of this headless sandbox (a global-undo
      confirmation that never paints) is **not established here**, and asserting either would be
      guessing. Recorded for the supervisor to route: it needs its own reproduction on a normal
      desktop IDE before it earns a bug ID.

## Invalid name — `REFACT-07-07`

- [x] Caret in `counter`; <kbd>Shift+F6</kbd>; type `end`; press <kbd>Enter</kbd>.
      → Done; caret 4:10.
- [x] The rename does **not** apply. The file still reads `local counter = 0` — in particular it
      does **not** read `local end = 0`.
      → **PASS.** md5 stayed at the pristine `e13ea3dd80bb4182ba7494ef5b376db0` throughout, and
      after *Cancel* the editor read `local counter = 0` / `print(counter)` /
      `counter = counter + 1`.
- [x] The IDE tells the user why, rather than doing nothing silently.
      → **PASS.** A popup reads **"Inserted identifier is not valid"** and offers **Continue
      editing** / **Cancel**; the template stays live until one is chosen.
      `09-invalid-name-rejected.png`. *Cancel* is the route that raises the platform
      `ThreadingAssertions` SEVERE noted above.

## Conflict — `REFACT-07-08`

- [x] Add `local total = 1` after the `local counter = 0` line.
- [x] Caret in `counter`; <kbd>Shift+F6</kbd>; type `total`; press <kbd>Enter</kbd>.
      → Done; caret confirmed at 4:10 before the action.
- [x] A conflict is reported, naming the capture. The file is unchanged.
      → **PASS.** A modal **Conflicts Detected** window, header **"1 conflicts"**.
      `10-conflicts-detected-dialog.png`.
- [x] **This is the only place the conflicts *dialog* is exercised.** Unit-test mode throws instead
      of constructing it (`BaseRefactoringProcessor.java:538-541`), so record what the dialog
      actually shows: whether both the capture and the shadow message appear, and in what order.
      → **The capture message appears; the shadow message does not.** Exactly one entry, grouped
      `Value read` → `proj` → `inplace.lua` → `1 result`, reading:
      *"Renaming to 'total' would bind a usage of 'counter' to a different declaration that is
      already visible here."* The same string repeats in the dialog's hover tooltip. There is no
      second message and no ordering question to record. The dialog's preview pane shows the
      **original** text (`local counter = 0` on line 4) while the editor painted behind the modal
      still shows the template's `total` — consistent with DR-04 probe (d)'s finding that the
      refactoring sees restored text at this point.
- [x] Press the dialog's **Continue** button and record what happens. The template's edits have
      already been rolled back by this point (measured, `risks-and-gaps.md` DR-04 probe (d)), so a
      Continue that proceeds is renaming from the restored text. Whatever it does, record it — no
      automated case covers this.
      → **Correction to the checklist as written: there is no button labelled "Continue".** The
      dialog offers **Open in Find Window**, **Refactor Anyway** and **Cancel**; *Refactor Anyway*
      is the continue-equivalent and is what was pressed. **It proceeds and applies the capture**:
      the file becomes `local total = 0` / `local total = 1` / `print(total)` / `total = total + 1`,
      so the reads now bind to the second declaration. That is renaming from the restored text,
      exactly as DR-04 predicted. `11-refactor-anyway-applies-capture.png`.
- [x] Remove the added line before continuing.
      → Done; fixture restored to the pristine md5.

## `---@param` parity — `REFACT-07-09`

- [x] Caret on the parameter `a` in `local function f(a)`; <kbd>Shift+F6</kbd>; type `count`;
      <kbd>Enter</kbd>.
      → Done; caret 2:18. The template started with `a` in `f(a)` as the editable box and `a` in
      `return a` as a linked segment — `12-param-template-starts.png`. The `---@param a` on line 1
      is **not** a template segment, which is correct: the tag is rewritten at commit by
      `LuaRenameProcessor`, not mirrored live.
- [x] The file reads `---@param count number` and `local function f(count) return count end` — the
      **doc tag moved with the code**.
      → **PASS**, read back off disk. `13-param-tag-moved.png`.

## A global takes the dialog — `REFACT-07-10`

- [x] Caret in `config` on the `config = {}` line; press <kbd>Shift+F6</kbd>.
      → Done; caret 8:3.
- [x] The **rename dialog** appears, with its preview affordance. No inline template starts.
      → **PASS.** Modal **Rename** window titled *"Rename global variable 'config' and its usages
      to:"*, with a **Preview** button, a **Scope** selector and the *Search in comments and
      strings* checkbox. No inline box appeared anywhere in the editor.
      `14-global-takes-dialog.png`.

## A usage-site caret — `REFACT-07-11`

- [x] Caret in `counter` inside `print(counter)`; press <kbd>Shift+F6</kbd>. **No dialog and no
      handler-chooser appears**, and an inline editing box surrounds the `counter` **inside
      `print(...)`** — the occurrence at the caret is the editable one, per design §3.5. The
      declaration is a linked segment, not the primary one; that is correct, not a defect.
      → **PASS, exactly as §3.5 predicts.** Caret 5:10. The box is on the `counter` inside
      `print(...)`; the declaration on line 4 and both occurrences on line 6 are linked segments.
      `15-usage-caret-template.png`.
- [x] Type `total`; press <kbd>Enter</kbd>.
- [x] The **declaration** changed too: the file reads `local total = 0`, and every other occurrence
      reads `total`.
      → **PASS**, read back off disk. `16-usage-caret-committed.png`.

## Numeric-`for` — `REFACT-07-14`

- [x] Caret on `i` in `for i = 1, 10 do`; press <kbd>Shift+F6</kbd>.
- [x] No inline template starts. Record what does happen, whatever it is.
      → **Nothing happens at all.** Caret 11:5. No template, no dialog, no handler-chooser, no
      balloon, no status-bar message; the document is unchanged and the action is silently inert.
      `17-numeric-for-inert.png`. That is the guard `REFACT-07-14` describes — "the user gets
      whatever the dialog path gives them today" — and what the dialog path gives them, measured, is
      silence.

## Labels are unchanged — `REFACT-07-13`

- [x] Caret in `retry` on the `::retry::` line; <kbd>Shift+F6</kbd>; type `again`; <kbd>Enter</kbd>.
      → Done; caret 15:5. A template started with `retry` in `::retry::` editable and `retry` in
      `goto retry` linked — `18-label-template.png`.
- [x] The file reads `::again::` and `goto again`.
      → **PASS**, read back off disk. `19-label-committed.png`.

## Exactly one handler — `REFACT-07-02`

- [x] For each of the caret positions above, confirm that pressing <kbd>Shift+F6</kbd> never shows a
      **handler-chooser** dialog asking which rename to perform.
      → **PASS at every caret driven in this run** — declaration (4:10), usage (5:10), parameter
      (2:18), global (8:3), numeric-`for` (11:5) and label (15:5). `wmctrl -l` was captured
      immediately after each <kbd>Shift+F6</kbd>: it listed the project window alone in every case
      except the global, where it listed the project window and `Rename` — the rename dialog
      `REFACT-07-10` requires, which is not a chooser. This confirms the requirement **for this
      build's extension list**; Risk 1.9 remains the residual for a different IDE or a third-party
      plugin, exactly as DR-02 left it.

## Consumer audit spot-checks — `REFACT-07-12`, DR-03

DR-03 measured this audit in fixtures across both commits. What remains here is every behaviour
change it found that is user-visible and that no test asserts, plus the cheap live confirmations.

**<kbd>Ctrl+D</kbd>'s resting caret — the condition on which `REFACT-07-12` accepts this change.**
It is almost certainly *more* consistent with other languages, and nobody has looked at it.

Resting caret read from *Go to Line:Column*'s `Offset:` field, which reports it exactly.

| Line, caret before | Caret after | Reading |
| :--- | :--- | :--- |
| `print(counter)`, 5:10 (offset 80) | **6:1, offset 86** | start of the duplicated line — **moved** |
| `counter = counter + 1`, 6:4 (offset 89) | **7:4, offset 111** | column preserved on the duplicated line |
| `local counter = 0`, 4:10 (offset 62) | **5:10, offset 80** | column preserved — **unchanged** |

- [x] Put the caret inside `counter` on the `print(counter)` line with **NO SELECTION**, and press
      <kbd>Ctrl+D</kbd>. That is a line DR-03 measured directly (resting caret 158 on base, 144 on
      treatment). **The no-selection part is the whole test**: `hasSelection()` short-circuits the
      branch, so a selection measures nothing and looks inert — DR-03's first probe made exactly
      that mistake.
      → **Run with no selection**, caret placed by *Go to Line:Column* (which sets a caret and never
      a selection). The caret **moves**, to the start of the duplicated line.
      `20-ctrl-d-print-line-document.png`, `21-ctrl-d-print-line-caret.png`.
- [x] Repeat on `counter = counter + 1`, which DR-03 did **not** measure — its measured
      assignment-shaped line was `counter = helper(1, 2)`. Expect the same behaviour; record it.
      → **The expectation does not hold: the caret does not move here**, it keeps column 4 on the
      duplicated line. DR-03's assignment-shaped line did move (151 → 129). The two are **not
      directly comparable** — DR-03's emitted row does not record the caret it started from, and
      this run's absolute offsets belong to a different fixture — so this is recorded as a
      difference in outcome, not as a contradiction of DR-03.
      `22-ctrl-d-assignment-line-document.png`, `23-ctrl-d-assignment-line-caret.png`.
- [x] Confirm the duplicated line is correct and the result is **sane to work with**: the document
      must be byte-identical to the baseline build's, and the caret should rest on the duplicated
      line's `counter` rather than at the line end. Record where it lands either way.
      → **The document is correct in all three cases** — the line is duplicated verbatim, nothing
      else changes. **Where it lands**: at the *start* of the duplicated line for `print(counter)`,
      and at the *same column* for the other two. It lands neither on the duplicated `counter` nor
      at the line end. The result is sane to work with and is **accepted**, which is the condition
      `REFACT-07-12` attaches to this change.
      **The byte-identical-to-baseline half is DR-03's, not this run's**: no baseline sandbox was
      built here, so the live captures cannot compare two commits. DR-03 measured the documents
      identical and only the `caretAfter` values differing.
- [x] Repeat on a line beginning with a keyword (`local counter = 0`). Expect no change from the
      baseline — there is no `LuaNameRef` ancestor for the handler to pick.
      → **PASS, no change.** Column preserved (5:10, offset 80), which is the platform's default
      duplicate behaviour, and DR-03's row for this line is the one row of the three that is
      **identical on base and treatment** (`caretAfter=35` both sides).
      `24-ctrl-d-keyword-line-caret.png`.

Cheap live confirmations of behaviours already measured Δ none in fixtures:

- [x] Run **Find Usages** on `counter`: the reads and the write are reported; the declaration is
      not listed as a usage of itself.
      → **PASS from a usage caret; refused from a declaration caret, and that refusal is
      pre-existing.**
      From the usage caret (5:10): *Local variable `counter`*, **Usages in Project Files 3
      results** — **Value read 2 results** (`5 print(counter)`, `6 counter = counter + 1`) and
      **Value write 1 result**. The declaration is not among them.
      `26-find-usages-usage-caret-3-results.png`.
      From the **declaration** caret (4:10) the IDE answers *"Cannot search for usages from this
      location. Place the caret on the element to find usages for and try again."*
      `25-find-usages-declaration-caret-refused.png`. **This is not a REFACT-07 regression**, and
      the evidence for that is a measurement, not an argument: DR-03 ran this consumer on both
      commits and the rows are character-identical —
      `DR03|REFACT-07-12/FindUsages|THREW=java.lang.AssertionError: Cannot find handler for: NAME_REF`
      appears at line 29 of **both** `dr-03-evidence/probe-observations-base.txt` and
      `…-treatment.txt`. Design §4 states the mechanism: `LuaNameRef` was already a `PsiNamedElement`
      before §3.1, so `TargetElementUtil` already supplied the composite at a declaration caret, and
      `LuaFindUsagesProvider.canFindUsagesFor` is `kindOf(element) != null`, false for a composite.
      It is a standing Lunar gap, visible only in the IDE because
      `CodeInsightTestFixtureImpl` passes the IDENTIFIER **leaf** — which is exactly why TC-13 is
      green over it.
- [x] With the caret in a Lua name, invoke **completion** and record the item **order** against the
      baseline build. This is `implementation-plan.md` task 5.1a: `RecentPlacesFeatures` and
      `VcsFeatureProvider` are registered for all languages and their branch flips after §3.1, but
      the ranking plugin is not loaded in the unit-test container so DR-03 could not measure the
      effect. A difference is a finding to record, not necessarily a defect.
      → **RUN 2026-08-27. Verdict: the order does not move — measured-inert.** Full record in
      `phase-5-live-evidence/task-5-1a-ranking-measurement.txt`.
      **(1) Two builds, told apart by bytecode rather than by the version string** (both report
      `lunar (0.18.0)`, so the string discriminates nothing). `javap` on the class the sandbox
      actually loaded shows `LuaNameRefBaseImpl` **without** `PsiNameIdentifierOwner` on the
      baseline arm (jar md5 `d1244987…`) and **with** it, plus `getNameIdentifier()`, on the
      treatment arm (jar md5 `0429cf13…`). The baseline is this branch with §3.1 reverted.
      **(2) Navigation history equalised, and shown to be, not asserted to be.**
      `RecentPlacesStorage` is a plain in-memory project service with no `@State`, so it starts
      empty at every launch by construction; both arms ran on a fresh IDE over a deleted sandbox
      `system/` directory; both were driven by the *same unedited script file* (md5 `c6002f0a…`,
      re-checked immediately before the second run) using Go-to-Line rather than mouse targeting;
      and Recent Locations was captured in each arm, showing **(2)** entries with the second
      breadcrumbed `rank.lua > configure` — so the navigation really did record a place inside the
      declaration whose `findDeclaration` result differs.
      `31-ranking-ab-baseline-recent-locations.png`, `32-ranking-ab-treatment-recent-locations.png`.
      **(3) The orders.** Caret at `6:12`, prefix `al`, on a fixture built to be maximally
      ranking-sensitive (the visited declaration is a function whose parameter shares a prefix with
      two sibling locals). Baseline: `alpha` *(parameter)*, `alphabet` *(local)*, `alpine` *(local)*.
      Treatment: **identical**. `33-ranking-ab-baseline-completion-order.png`,
      `34-ranking-ab-treatment-completion-order.png` — which are in fact **md5-identical PNGs**,
      captured five minutes and one IDE restart apart (mtimes 11:56:43 and 12:01:52, so neither is
      a stale frame).
      **(4) Why no fixture could have shown a difference**, which is what makes this a verdict
      rather than one lucky sample. Reordering requires a `com.intellij.completion.ml.model`
      provider matching the language; that EP is a plain `ExtensionPointName`, **not** a
      `LanguageExtension`, so it has no `language=""` catch-all. Enumerating every registration in
      the GoLand 2026.1.3 build under test gives SQL, Go, JavaScript, TypeScript and Shell — **no
      Lua**. So `shouldReRank()` is false and `MLSorter` returns the list unchanged; and
      `MLCompletionWeigher`, the component that *does* call these providers, returns a
      `DummyComparable` whose `compareTo` is a constant `0`.
      **(5) What this does not cover.** `VcsFeatureProvider`'s `PsiNameIdentifierOwner` branch was
      not exercised as its own arm — the fixture was not a VCS-modified tracked file — so it is
      settled by (4) rather than by its own fixture. Recorded as a scope limit, not glossed.
- [x] Run **Safe Delete** on an unused local: the whole `local` statement is removed, not just the
      identifier.
      → **Could not be exercised: Safe Delete is disabled at a declaration caret, and that is
      pre-existing.** With `local unused = 42` added and the caret at 8:9 inside `unused`, the
      **Refactor** menu shows **"Safe Delete… Alt+Delete" greyed out**, *Find Action* does not offer
      it among enabled actions, and <kbd>Alt+Delete</kbd> is inert. *Rename… Shift+F6* is enabled in
      the same menu. `28-safe-delete-disabled-at-declaration-caret.png`.
      Same mechanism and same verdict as Find Usages: `isSafeDeleteAvailable` takes the raw element,
      the declaration caret supplies the composite, and it did so **before** §3.1 too. DR-03 ran the
      Safe Delete consumer on both commits and every emitted row is character-identical —
      `leafTarget`, `nameRefTarget`, `afterLeafDelete`, `afterNameRefDelete` and the
      `ConflictsInTestsException` text all match between `probe-observations-base.txt` and
      `…-treatment.txt`, and the row appears in neither side of `probe-observations.diff`. The
      requirement's substance — *the whole statement is removed, not just the identifier* — is
      carried by TC-14, which passes; what this run adds is that the IDE entry point to it is closed
      at a declaration caret, independently of this feature.

## Findings this run produced

Ordered by what a reader has to decide about, not by severity.

| # | Finding | Attributable to REFACT-07? | Where it is recorded |
| :--- | :--- | :--- | :--- |
| 1 | Undo does not restore after a rename commit, by any of three routes | **No** — reproduces identically on the untouched dialog path; typing-undo control passes | This file, "Undo after a commit"; `requirements.md` Risk 1.4 / Gap 2.3 row |
| 2 | Find Usages refuses at a declaration caret | **No** — DR-03 base and treatment rows are character-identical | This file; design §4 already carries the mechanism |
| 3 | Safe Delete disabled at a declaration caret | **No** — same, DR-03 rows identical on both commits | This file; design §3.6 row already says "Not newly affected" |
| 4 | `ThreadingAssertions` SEVERE on the invalid-identifier rollback | **No** — zero `net.internetisalie` frames; soft assertion; behaviour correct | `threading-assertion-stack.txt` |
| 5 | Undo label reads "Renaming Lua Name Ref Impl…" on the in-place path | **Yes**, cosmetically — the composite has no `ElementDescriptionProvider` | This file, "Undo after a commit" |
| 6 | The conflicts dialog's continue button is **Refactor Anyway**, not "Continue" | n/a — a correction to this checklist's own wording | Corrected in place above |
| 7 | <kbd>Ctrl+D</kbd> caret does not move on `counter = counter + 1` | Accepted behaviour, not a defect | The <kbd>Ctrl+D</kbd> table above |

Findings 1, 2 and 3 are user-visible and **none of them is this feature's**. They are routed to the
supervisor rather than filed here, because each needs its own reproduction before it earns a bug ID
— finding 1 in particular has not been separated from the headless sandbox.
