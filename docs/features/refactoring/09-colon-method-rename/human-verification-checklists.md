---
id: "REFACT-09-CHECKLIST"
title: "09: Verification Checklists"
type: "qa"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# Verification Checklists: REFACT-09 — Colon-method rename

Run these in the containerised GoLand (`.agents/skills/verify-in-ide/SKILL.md`). They cover the two
surfaces no unit test can see:

- Under `BasePlatformTestCase`, `CommonRefactoringUtil.showErrorHint` **throws** instead of
  painting, so no automated test has seen a refusal balloon.
- `RenameProcessor.preprocessUsages` throws `ConflictsInTestsException` instead of showing a dialog
  (`RenameProcessor.java:179-181`), so no automated test has seen the conflicts dialog — and this
  feature's whole incompleteness verdict is delivered through it (`risks-and-gaps.md` Risk 1.1).

Use a scratch project with the files named in each scenario. Record the exact balloon or dialog text
observed, not a paraphrase.

## Live verification result — 2026-09-05, sandbox GoLand 2026.1.3 at `778ec948`

| Scenario | Verdict | Evidence |
| :-- | :-- | :-- |
| 1.1 declaration caret renames every call site | **PASS** | caret `2:12`; all three read `n`; no conflicts dialog |
| 1.2 the dialog names the method | **PASS** | *"Rename global function 'm' and its usages to:"* — the method, not the receiver |
| 1.3 annotated receiver, aliased | **PASS** | caret `3:20`; `function Builder:withName(x)` **and** `b:withName("x")`; no dialog |
| 1.4 one undo restores the file | **PASS** | single <kbd>Ctrl+Z</kbd>; Edit menu read *"Undo Renaming global function m to …"* — [[BUG-475]]'s `ElementDescriptionProvider` working, where it once showed a de-camel-cased PSI class name |
| 2.1 / 2.3 the incompleteness conflicts dialog | **PASS** | *"This call to 'm' cannot be bound to a declaration, so it may be a call of this method and will not be renamed."* — names the file, highlights the occurrence in a preview pane, offers **Refactor Anyway** (Risk 1.1's residual, visible in the UI) |
| 3.1 caret on `self` | **PASS** | *"Cannot perform refactoring. 'self' is not the method name; put the caret on the method name to rename it."* |
| 2.2 the dotted spelling | **PASS** | *"This names the same member as 'm' in the **dotted form**, which this rename does not rewrite."* |
| 2.4 a string-key spelling | **PASS** | *"This names the same member as 'm' through a **string key**…"*, preview showing `print(t["m"])` |
| 2.5 a table-constructor key | **PASS** | *"This **table-constructor key** declares the same member as 'm'…"* |
| 2.6 a positional value does NOT block | **PASS** | `local u = { m }` produced **no** entry — verified as an absence in the same run that produced 2.2/2.4/2.5, so the negative and the positives share one measurement |
| 2.7 a dynamic index does NOT block | **PASS** | neither `local k = 'm'` nor `print(t[k])` produced an entry; exactly 3 conflicts, all from the three blocking spellings |
| 3.2 a method declared outside the project | **FAIL** | balloon reads the platform's generic *"Cannot perform refactoring. This element cannot be renamed"*, **not** Lunar's message naming `runtime/standard/lua-5.4/io.lua`. Refusal outcome correct; diagnostic lost. **[[BUG-480]]** |
| 1.2 the preview lists the usages | **PASS** | Refactoring Preview: *"References in code to global function (**2 references in 1 file**)"* — the two `t:alpha()` sites |
| 3.3 a call on `self` refuses as unresolved | **PASS** | caret `3:23`; *"Cannot determine which declaration this name refers to, so its usages cannot be rewritten."* — the pre-existing `refactoring.rename.unresolved`, unchanged |
| 3.4 no shipped string claims colon calls are unresolved | **PASS** | verified against `LuaBundle.properties` rather than the UI: the falsified phrase appears nowhere, and all six `refactoring.rename.colonMethod.*` keys are present. `REFACT-09-09` |
| 4.1 the receiver already has the new name | **PASS** | *"This table already has a member named 'delta'; renaming would merge the two."* — and **not** *"a global named 't:delta' already exists"*, the rule Phase 3's arm replaced (DR-02 Finding 6) |
| 4.2 the annotated receiver reports the same collision | **PASS** | *"This table already has a member named 'withName'; renaming would merge the two."* on a `---@class Widget` receiver — **live evidence for §3.7's union-arm loop**, since `{ … } \| Widget` makes `LuaUnionType.resolveMember` return null without it and nothing would be reported |
| 4.3 a declaration with no call site reports nothing | **PASS** | no dialog; the file becomes two `function q:theta()` declarations, exactly as Gap 2.8 states |
| 4.4 an identical shape in another file is not a conflict | **PASS** | `k1.lua` renamed to `lambda`, no dialog; `k2.lua` **byte-identical on disk** afterwards despite being the same shape — per-receiver isolation holds across files |

**A false alarm, recorded because the process failure is worth more than the result.** Scenario 1.3
was first observed **failing** — declaration renamed, `b:setName("x")` untouched, nothing reported —
and filed as a critical [[BUG-479]]. It does not reproduce. Both failing runs wrote the fixture
**into an already-running IDE**, in a directory where a sibling had just raised
`Failed to change read-only status` and been `chmod`-ed mid-session. Staged as `builder` **before**
the IDE starts, so the file is indexed at startup, the scenario passes.

**Fixture lifecycle is part of the experiment.** A file created into a running IDE, or owned by a
different user than the IDE runs as, is not the same input as a file the IDE indexed at startup —
and neither is what a unit fixture models. Run the control before filing.

**Every scenario is now driven.** Eighteen checks: seventeen pass, one fails ([[BUG-480]], 3.2's
balloon wording).

**Two of the passes are accepted residuals, and confirming them is the point.** 4.3 (a declaration
with no call site renames silently, leaving two identical declarations) and 2.7 (a dynamic index is
never reported) are *documented misses*, not successes — driving them proves the shipped behaviour
matches what `risks-and-gaps.md` Gaps 2.8 and 2.2 tell a reader to expect, rather than surprising
them. A residual that behaves as documented is a different thing from one nobody checked.

**Giving each scenario its own member name is what unblocked §4.** The earlier round staged every
fixture as `m`, so one rename reported the other files' spellings and §4 could not be isolated;
`alpha` / `beta` / `gamma`+`delta` fixed it outright.

**Two mechanical notes for whoever finishes this**, both of which cost real time here. A modal
**Rename** dialog left open silently swallows every subsequent keystroke, so the Project tree and
Go-to-File appear broken when they are merely blocked — screenshot the whole screen, not a crop,
the moment anything stops responding. And a rename in one file reports conflicts from **every** file
sharing the member name, which is correct behaviour and a poor fixture design: give each scenario
its own member name, or its own project.

## 1. Rename

### Scenario 1.1: The declaration caret renames every call site
1. `a.lua`:
   ```lua
   local t = {}
   function t:m() end
   t:m()
   t:m()
   ```
2. Caret on the `m` of `function t:m()`. <kbd>Shift+F6</kbd>, new name `n`, Refactor.
- [ ] No conflicts dialog appears.
- [ ] All three occurrences read `n`.
- [ ] The Find Usages preview (Refactor → Rename → Preview) listed **two** usages before the write.

### Scenario 1.2: A call-site caret renames the same set
1. Same file, restored.
2. Caret on the `m` of the first `t:m()`. <kbd>Shift+F6</kbd> → `n`.
- [ ] The declaration and both call sites read `n`.
- [ ] The dialog's title names the method, not the receiver.

### Scenario 1.3: An annotated receiver, aliased
1. `b.lua`:
   ```lua
   ---@class Builder
   local Builder = {}
   function Builder:setName(x) end
   local b = Builder
   b:setName("x")
   ```
2. Caret on `setName` in the declaration. <kbd>Shift+F6</kbd> → `withName`.
- [ ] Both occurrences read `withName`. No conflicts dialog.

### Scenario 1.4: One undo restores the file
1. After Scenario 1.1, <kbd>Ctrl+Z</kbd> once.
- [ ] The file is back to its original text in a single undo — not two, not partial.

## 2. The incompleteness conflicts dialog

**This is the feature's headline surface.** Confirm it is readable and that its entries are
navigable, not just present.

### Scenario 2.1: A `self:` call in the same file
1. `c.lua`:
   ```lua
   local C = {}
   function C:m() end
   function C:a() self:m() end
   C:m()
   ```
2. Caret on `m` in `function C:m()`. <kbd>Shift+F6</kbd> → `n`.
- [ ] A conflicts dialog appears **before** anything is written.
- [ ] It names the `self:m()` call on line 3 and says it cannot be bound to a declaration.
- [ ] **Cancel** leaves the file byte-identical (check the editor's undo stack is empty and the
      file is not modified).
- [ ] Re-run and choose **Continue**: the declaration and `C:m()` become `n`, `self:m()` is left —
      i.e. the dialog told the truth. Undo.

### Scenario 2.2: The dotted spelling
1. `d.lua`: `local t = {}` / `function t:m() end` / `t:m()` / `print(t.m)`
2. Rename `m` → `n`.
- [ ] The dialog names the `.m` occurrence on line 4 and says the dotted form is not rewritten.

### Scenario 2.3: A cross-file occurrence
1. `e1.lua`: `Obj = {}` / `function Obj:m() end` / `Obj:m()`; `e2.lua`: `Obj:m()`
2. Rename from `e1.lua`'s declaration → `n`.
- [ ] The dialog names `e2.lua` and its line.
- [ ] "Show conflicts in view" opens a usage view whose entry **navigates to `e2.lua`** when
      double-clicked. (Each occurrence is its own `LuaRenameCollisionUsageInfo`; a single summary
      line would fail this step.)

### Scenario 2.4: A string-key spelling
1. `f.lua`: `local t = {}` / `function t:m() end` / `t:m()` / `print(t["m"])`
2. Rename `m` → `n`.
- [ ] The dialog names the `t["m"]` occurrence.

### Scenario 2.5: A table-constructor key
1. `f2.lua`: `local t = { m = 1 }` / `function t:m() end` / `t:m()`
2. Rename `m` → `n`.
- [ ] The dialog names the `m = 1` key on line 1 and says a constructor key is not rewritten.
- [ ] Repeat with `f3.lua`: `local t = {}` / `function t:m() end` / `t:m()` / `local u = { ["m"] = 1 }`
      — the dialog names `u`'s bracketed key.

### Scenario 2.6: A positional value spelled like the member does NOT block
1. `f4.lua`: `local t = {}` / `function t:m() end` / `t:m()` / `local m = 1` / `local u = { m }`
2. Rename `m` → `n`.
- [ ] **No** dialog entry for `{ m }`. (`local m` is renamed or not by the ordinary local rules; the
      constructor entry must not be reported as a member spelling.)

### Scenario 2.7: A dynamic index does NOT block
1. `g.lua`: `local t = {}` / `function t:m() end` / `t:m()` / `local k = 'm'` / `print(t[k])`
2. Rename `m` → `n`.
- [ ] **No** conflicts dialog. The rename applies and `print(t[k])` is untouched.
- [ ] This is the accepted residual (`risks-and-gaps.md` Gap 2.2) — confirm it behaves as stated
      rather than surprising a reader of the docs.

## 3. Refusals — the balloon text

### Scenario 3.1: Caret on `self`
1. `h.lua`: `local C = {}` / `function C:m() end` / `function C:a() self:m() end` / `C:m()`
2. Caret **inside the word `self`** on line 3. <kbd>Shift+F6</kbd>.
- [ ] A balloon says `'self' is not the method name; put the caret on the method name to rename it.`
- [ ] Nothing is renamed — in particular `function C:a()` is **not** renamed.

### Scenario 3.2: A method declared outside the project
1. `i.lua`: `local f = io.open("x")` / `f:write("y")`
2. Caret on `write`. <kbd>Shift+F6</kbd>.
- [ ] A balloon says the method is declared outside this project and names a path ending in
      `runtime/standard/lua-5.4/io.lua`.
- [ ] No rename dialog opens and no file is modified.

### Scenario 3.3: A call on `self` still refuses as unresolved
1. `h.lua` again, caret on the `m` of `self:m()`.
- [ ] A balloon says the name cannot be bound to a declaration
      (`refactoring.rename.unresolved`) — unchanged pre-existing behaviour.

### Scenario 3.4: No shipped string claims colon calls are unresolved
- [ ] Search the plugin's Settings → Editor → Inspections and every balloon seen above: the phrase
      *"calls written `obj:method()` are not resolved"* appears nowhere. (`REFACT-09-09`.)

## 4. Member collision

### Scenario 4.1: The receiver already has the new name
1. `j.lua`: `local t = {}` / `function t:m() end` / `function t:n() end` / `t:m()` / `t:n()`
2. Rename `m` → `n`.
- [ ] A conflicts dialog says the table already has a member named `n`.
- [ ] It does **not** say "a global named 't:n' already exists" — that is the rule this feature
      replaced (`risks-and-gaps.md` DR-02 Finding 6).

### Scenario 4.2: The annotated receiver reports the same collision
1. `j2.lua`:
   ```lua
   ---@class Builder
   local Builder = {}
   function Builder:setName(x) end
   function Builder:withName(x) end
   Builder:setName("x")
   ```
2. Rename `setName` → `withName`.
- [ ] The same "already has a member" conflict appears. (This is the shape the union-arm loop of
      `design.md` §3.7 exists for; without it nothing is reported.)

### Scenario 4.3: A declaration with no call site reports nothing — a known miss
1. `j3.lua`: `local t = {}` / `function t:m() end` / `function t:n() end`
2. Rename `m` → `n`.
- [ ] **No** conflicts dialog, and the file becomes two `function t:n()` declarations.
- [ ] This is the accepted residual (`risks-and-gaps.md` Gap 2.8) — confirm it behaves as stated
      rather than surprising a reader of the docs.

### Scenario 4.4: An identical shape in another file is not a conflict
1. `k1.lua` and `k2.lua` each: `local t = {}` / `function t:m() end` / `t:m()`
2. Rename from `k1.lua` → `n`.
- [ ] **No** conflicts dialog.
- [ ] `k1.lua` renames; `k2.lua` is untouched.

### Scenario 4.5: A redefinition on the same receiver is left behind — a known miss
1. `j4.lua`: `local t = {}` / `function t:m() end` / `function t:m() end` / `t:m()`
2. Rename the **first** `m` → `n`.
- [ ] The first declaration and `t:m()` read `n`; the second `function t:m()` is unchanged; no
      dialog appears. (`risks-and-gaps.md` Gap 2.10 — the one residual that moves which body the
      call reaches.)

## 5. No regression

### Scenario 5.1: The dotted form is unchanged
1. `l.lua`: `local M = {}` / `function M.run() end` / `M.run()`
2. Rename `run` → `go` from the declaration, then undo and repeat from the call site.
- [ ] Both directions rename both occurrences, exactly as before this feature.

### Scenario 5.2: In-place rename still starts for a local
1. `m.lua`: `local total = 0` / `print(total)`; caret on `total`. <kbd>Shift+F6</kbd>.
- [ ] An **inline** template starts in the editor (not a dialog), and committing renames both.

### Scenario 5.3: Label rename still works
1. `n.lua`: `::retry::` / `goto retry`; caret on the label.
- [ ] <kbd>Shift+F6</kbd> renames both.

### Scenario 5.4: Rename during indexing
1. Trigger a re-index (File → Invalidate Caches, or add a large directory), and during indexing
   attempt Scenario 1.1.
- [ ] Either the platform reports the action is unavailable while indexing, or the rename completes
      correctly. **Never** a declaration renamed with its call sites left behind.
