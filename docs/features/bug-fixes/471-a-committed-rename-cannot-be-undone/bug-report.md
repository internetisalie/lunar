---
id: "BUG-471"
title: "A committed rename cannot be undone — Edit ▸ Undo is enabled but restores nothing, on both rename paths"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-471: undo after a rename commit is enabled and inert

Found 2026-08-26 during [[REFACT-07]]'s Phase 5 live verification, in the containerized GoLand
2026.1.3 (`verify-in-ide`). **Not caused by REFACT-07** — it reproduces on the modal dialog path,
which that feature does not touch, and which shipped with [[REFACT-01]]. Filed against the dialog
path.

Phase 5 declined to mint an ID because the result was not separated from the headless sandbox. The
review gate disagreed, on Phase 5's own evidence: its typing-undo control already rules out "undo is
broken in this sandbox generally". The sandbox caveat is real and is stated below as the first thing
to rule out — it is a reason to qualify the report, not to withhold it.

## Reproduction

In a Lua file in a running IDE:

```lua
local counter = 0
print(counter)
counter = counter + 1
```

1. Put the caret on the `counter` declaration and rename it to `total` — either route:
   - **in-place**: <kbd>Shift+F6</kbd>, type `total`, <kbd>Enter</kbd>; or
   - **dialog**: <kbd>Shift+F6</kbd> on a global, type the new name, *Refactor*.
2. Press <kbd>Ctrl+Z</kbd>.

## Expected

The document returns to its pre-rename text, in one undo — the declaration and every rewritten
usage together, since the rename is a single refactoring command.

## Actual

The document stays renamed. Measured on both paths:

| | in-place path (REFACT-07) | dialog path (REFACT-01) |
| :--- | :--- | :--- |
| *Edit ▸ Undo* present | yes | yes |
| *Edit ▸ Undo* **enabled** | yes | yes |
| entry text | "Undo Renaming Lua Name Ref Impl cou…" | "Undo Renaming global variable confi…" |
| document after undo | still `total` | still `settings` |
| entry after undo | still at the top of the stack | still at the top of the stack |

**Three invocation routes were tried on the in-place path and all three were inert**: the
<kbd>Ctrl+Z</kbd> keystroke, clicking the *Edit ▸ Undo* menu item, and *Find Action ▸ Undo ▸ Enter*.
No number of undos restores the file.

Evidence:
`docs/features/refactoring/07-inplace-rename/phase-5-live-evidence/07-undo-does-not-restore.png`,
`…/06-undo-label-inplace-path.png`, `…/08-undo-label-dialog-path-control.png`, and
`human-verification-checklists.md` § "Undo after a commit".

## Controls, and what they do and do not establish

- **Undo works in that session.** Typing `XX` into the same file and pressing <kbd>Ctrl+Z</kbd>
  removed it. So this is not "undo is dead in the sandbox"; it is undo of a *Lunar rename*.
- **It reproduces on the dialog path**, which REFACT-07 does not touch. **The cause is therefore not
  the in-place feature**, and a fix aimed at the in-place route would be aimed at the wrong place.
- **It was NOT separated from the headless sandbox.** Every observation above comes from the
  containerized IDE. **Rule this out first**: reproduce on a desktop GoLand before investigating
  further. If it does not reproduce there, this is a harness defect and the report closes.

## A contrary observation from a different sandbox — 2026-08-27

**Undo worked.** During `BUG-472`'s live verification, in the **VM-native `runIde` sandbox** (not the
containerized GoLand this report was found in), a committed inline rename was followed by a single
<kbd>Ctrl+Z</kbd> and the document returned **byte-for-byte** to its prior state — confirmed on disk
after <kbd>Ctrl+S</kbd>, on both of that run's reproductions.

**This does not close this report**, and the differences matter:

- **different fixture** — `BUG-472`'s shadowing-`local` cases, not this report's;
- **different rename route** — an in-place template committed by <kbd>Enter</kbd>, where this report
  drives the dialog path;
- **different IDE instance** — VM-native `runIde` sandbox versus the container.

**What it does do is promote this report's own first instruction.** It already says the sandbox
caveat is "the first thing to rule out". A second environment, exercising undo after a Lunar rename
and getting a correct restore, is direct evidence that the **environment is a live variable** rather
than a formality — so start by reproducing in both, and only then look at the rename path.

Evidence: `docs/features/bug-fixes/472-renaming-a-shadowing-local-rewrites-the-shadowed-one/live-evidence/`
(`06-repro-a-after-one-undo.png` and the run recorded in that report's §9.1).

## Where the fix may belong — a lead, not an attribution

`LuaRenameProcessor.renameElement` applies every rewrite inside
`ProgressManager.executeNonCancelableSection` (`LuaRenameProcessor.kt:268-272`) rather than a
`WriteCommandAction`, so whether those document edits join an undoable command depends entirely on
the caller's frame. That is a **plausible mechanism, read from the code and not executed** — nothing
here has proven it is the one on this path, and the shared-by-both-paths symptom is consistent with
it only because both paths reach that method. Prove which frame owns the command before changing it.

Note that `BUG-468` also lives in that method's rewrite loop, for a different reason (cancellation
mid-loop). They are not the same defect and neither fix implies the other.

## Verified 2026-09-02 — the restore failure does NOT reproduce; the label defect does

Run on the **VM-native `runIde` sandbox** (GoLand `GO-2026.1.3`, plugin load confirmed fresh at
`2026-09-02 10:50:16` — the log is append-only, so the last line was checked against today's date,
not the first). Both rename routes, this report's own fixture, file content read **from disk after
<kbd>Ctrl+S</kbd>** rather than from a screenshot.

| route | fixture | after rename | after one <kbd>Ctrl+Z</kbd> |
| :-- | :-- | :-- | :-- |
| **in-place** (REFACT-07) | `local config = 2` / `print(config)` | `local settings = 2` / `print(settings)` | **`local config = 2` / `print(config)`** — restored |
| **dialog** (REFACT-01) | `gconfig = 1` / `print(gconfig)` | `gsettings = 1` / `print(gsettings)` | **`gconfig = 1` / `print(gconfig)`** — restored |

A **headless control** was run first, before touching the IDE: a `BasePlatformTestCase` that renames
through `myFixture.renameElementAtCaret` and then calls `UndoManager.undo`. It reports
`isUndoAvailable=true` and restores the pre-rename text exactly. So the processor does produce a
single undoable command, which is what `LuaRenameProcessor.renameElement`'s KDoc asserts and what
this report contradicted.

**Disposition: the restore half of this report is an artifact of the containerized IDE**, which is
the deprecated verification path. Four independent observations now say undo works — the headless
fixture, the VM-native sandbox on both routes today, and `BUG-472`'s run on 2026-08-27 — against one
container. This report's own first instruction was to rule the environment out before investigating,
and that instruction was right.

**What this does not establish.** The container was **not** re-run, so this does not prove the
container still fails, nor identify what about it differs. If the container path is ever revived,
expect this to return there and treat it as a harness defect at that point.

**The cosmetic label below is a different matter — it reproduces here**, on the in-place path, and
is carried forward as [[BUG-475]].

## Separate, and cosmetic

**Carried forward as [[BUG-475]]; confirmed live on 2026-09-02 in the VM-native sandbox, where the
Edit menu reads `Undo Renaming Lua Name Ref Impl con…`.** On the in-place path the undo entry reads
**"Undo Renaming Lua Name Ref Impl cou…"** — the PSI
implementation class name, de-camel-cased — because nothing supplies an `ElementDescriptionProvider`
for the `LuaNameRef` composite. The dialog path's label is properly user-facing. This is REFACT-07's,
it is user-visible, and it is independent of the restore failure above.
