---
id: "BUG-480"
title: "The out-of-project rename refusal never reaches the user — the platform refuses first"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-480: `outOfProjectRefusal`'s message is unreachable in the IDE

[[REFACT-09]] `human-verification-checklists.md` **Scenario 3.2 fails as written**. The outcome is
right — the rename is refused — but the *diagnostic* the feature was built to give is never shown.

## Reproduction

Sandbox GoLand 2026.1.3, `feat-refact-09` at `778ec948`, fixture staged as `builder` before IDE
startup so it is indexed at start.

```lua
local f = io.open("x")
f:write("y")
```

Caret inside `write` (verified `2:4`), <kbd>Shift+F6</kbd>.

| | Expected (Scenario 3.2) | Observed |
| :-- | :-- | :-- |
| balloon | names the method as declared outside the project, and a path ending `runtime/standard/lua-5.4/io.lua` | **"Cannot perform refactoring. This element cannot be renamed"** |
| rename dialog | none | none ✅ |
| file modified | no | no ✅ |

The message is the **platform's** generic refusal. Lunar's `outOfProjectRefusal`
([LuaRenameProcessor.kt](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt))
never runs on this path.

## Why the unit test does not catch it

`LuaColonMethodRenameTest`'s row-14 method calls `substituteElementToRename` **directly**. The
platform's own writability/renameability check sits *above* that call, so the test enters below the
layer that actually decides and asserts a message the user never sees.

**This was predicted and recorded during Phase 2, and the prediction was right.** That phase's report
noted: *"DR-04's predicted M3 symptom did not reproduce. Not `AssertionError: element not found in
file` but the platform's `This element cannot be renamed`. The guard is justified by the message (it
names `io.lua`; the platform's names neither method nor file)."* The justification was the message —
and the message is exactly the part that does not arrive.

## Impact — low, and stated as such

The **behaviour** is correct in both worlds: the rename is refused and nothing is written. What is
lost is diagnostic quality: a user renaming a stdlib method is told "this element cannot be renamed"
rather than which file declares it and why. `REFACT-09-05` is satisfied in outcome and not in
intent.

## Fix strategy — two options, neither obviously right

1. **Move the refusal earlier**, to whatever extension point runs before the platform's check
   (`RenameHandler.isAvailableOnDataContext`, or a `RenameInputValidator`). Costs an extra surface;
   the message becomes reachable.
2. **Accept the platform's message and delete `outOfProjectRefusal`.** The guard then earns nothing
   and its unit test asserts a path no user takes — which is worse than deleting it, because a green
   test implies a reachable behaviour.

**Do not choose from this report.** Instrument which platform check fires first and confirm the
ordering before touching either. The one thing that must not stand is the present state: a guard, a
green test, and a message no user can reach.

## The general lesson, worth more than the bug

**A test that enters below the layer that decides can pass while the behaviour it describes is
unreachable.** Row 14 is green, `outOfProjectRefusal` is correct in isolation, and the user still
never sees it. Live verification is what found this; no headless fixture on this path could.
