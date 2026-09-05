---
id: "BUG-480"
title: "The out-of-project rename refusal never reaches the user — the platform refuses first"
type: "bug"
parent_id: "BUG"
status: "done"
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

## Row 3 verified live — the guard fires, and the path is richer than measured

The three-state table that decided this bug was established by a unit probe with
`enableChecksInTests`. Row 3 — the state that saved the guard from deletion — is now driven in the
sandbox, on merged `main` at `e3d66469`, through the IDE's own UI.

**Fixture** staged as `builder` before IDE startup at `/home/builder/outside/outside.lua`, outside
every content root:

```lua
local w = {}
function w:sigma() end
w:sigma()
w:sigma()
```

Caret verified `2:13`, <kbd>Shift+F6</kbd>.

**The platform does not hard-refuse first.** It asks:

> Cannot perform refactoring.
> Selected element is **used from non-project files**. These usages won't be renamed. **Proceed anyway?**  \[Yes\] \[No\]

On **Yes**, Lunar's guard fires:

> Cannot perform refactoring.
> **This method is declared outside this project, in `'/home/builder/outside/outside.lua'`, so it cannot be renamed here.**

So the guard is reachable through an explicit **consent dialog**, not only through the
`isFileRecentlyChanged` window the probe inferred. That is a stronger result than the probe gave:
the user has to *ask* for the state in which Lunar's message is the one that answers. **Deleting the
guard would have removed the only message that names the declaring file, on a path a user reaches
deliberately.**

The prompt is a third path neither this report nor the probe described, and it is why row 2 was
recorded as "platform refuses" — under `enableChecksInTests` the same condition surfaces as a
refusal rather than a question, because a headless probe cannot answer a dialog.

**Merged-state control, same session**: an in-project colon method renamed both its declaration and
its call site (`v:tau` → `v:upsilon`), confirming REFACT-09, [[BUG-478]]'s evaluator change and this
bug's tests coexist.

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

## Resolution — NEITHER option: the guard is shadowed here, not unreachable

Both options above rest on *"the guard earns nothing"*, and measurement falsifies it. The ordering
was instrumented headlessly with a temporary probe (`Bug480OrderingProbeTest`, a `println` in
`substituteElementToRename`, both since removed) driving three routes over one fixture.

### The ordering, executed

`PsiElementRenameHandler.invoke(project, editor, file, dataContext)` — what `RenameElementAction`
calls — reaches `canRename` at `PsiElementRenameHandler.java:114` and the processor only at `:132`.
Driven over `local f = io.open("x")` / `f:<caret>write("y")`:

```
R480 dataContextElement = LeafPsiElement('write')
R480 file = …/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua   fileSystem = JarFileSystemImpl
R480 isInProject = false   isWritable = false   nonProjectWriteAccessAllowed = true
R480 selectedHandler = com.intellij.refactoring.rename.PsiElementRenameHandler
B (the user's path)   -> RefactoringErrorHintException: Cannot perform refactoring.
                         This element cannot be renamed
                         — and the probe line inside substituteElementToRename NEVER PRINTED
C (substituteElement…) -> "This method is declared outside this project, in '…/io.lua', …"
D (renameElementAtCaret) -> the same as C: the fixture API calls C directly
```

So the platform decides first and Lunar's guard is not entered — the report's premise, confirmed.
`renameElementAtCaretUsingHandler` is no escape either: it sets `DEFAULT_NAME`, which in unit-test
mode short-circuits `invoke` past `canRename` (`:61-67`).

**Why the message is the *generic* one and not the platform's own out-of-project message:**
`NonProjectFileWritingAccessProvider.isWriteAccessAllowed` returns `true` for anything not on a
`LocalFileSystem` (`:109`), so a jar-backed element skips `error.out.of.project.element` and falls
to `!isWritable` → `error.cannot.be.renamed`.

### Where the guard IS the deciding layer

The same probe over a `.lua` file under **no content root**, in the two states of the IDE's
non-project write protection (`enableChecksInTests` so the check is live headlessly):

| state | `isInProject` | `isWritable` | write access | platform `canRename` | Lunar's guard |
| :-- | :-- | :-- | :-- | :-- | :-- |
| jar stub (`io.lua`) | false | false | true | **refuses** — *This element cannot be renamed* | shadowed |
| local, protected | false | true | false | **refuses** — *Selected global function is not located inside the project* | shadowed |
| local, **unlocked** | false | true | true | **returns normally** | **refuses, naming `outside.lua`** |

The third row is reachable in the IDE: it is the state *"I want to edit this file anyway"* leaves a
file in, and `isFileRecentlyChanged` grants it to any non-project file just edited. There the
platform allows the rename and `outOfProjectRefusal` is the only thing standing between the user and
a rewrite of a declaration outside `projectScope` — the half-rename REFACT-01 exists to prevent.
Deleting the guard (option 2) would give that up.

### What was done

- **Kept `outOfProjectRefusal` unchanged.** No production code changed by this bug.
- **Re-aimed row 14 at the layer that decides it**, as two tests in `LuaColonMethodRenameTest`:
  - `aMethodDeclaredInABundledStubIsRefusedByThePlatformBeforeLunarIsAsked` drives the four-argument
    `PsiElementRenameHandler.invoke` with the editor's own data context, and asserts the balloon is
    the platform's `error.cannot.be.renamed` and **does not** name `io.lua`. Mutation — swap the
    drive back to `myFixture.renameElementAtCaret` — reddens it. That mutation *is* this bug.
  - `aMethodInAnUnlockedNonProjectFileIsRefusedByLunar` asserts the platform allows the element and
    Lunar refuses naming the declaring file. Mutation — delete the `outOfProjectRefusal` call —
    reddens it, and leaves the first test green.
- **Rejected option 1.** The only surface earlier than `canRename` is a `RenameHandler`, whose
  `isAvailableOnDataContext` runs on the EDT for every rename in every language and whose second
  entry in the registry's map is what raises the *Renamer* chooser popup that
  `LuaInplaceRenameHandler`'s KDoc documents at length. That is a large, hazardous surface to reword
  one balloon, in one of the three out-of-project states, where the outcome is already correct.

### What remains, deliberately

At a bundled stub the user still reads *"This element cannot be renamed"*, and is not told which
file declares the method. `REFACT-09-05` is satisfied in outcome everywhere and in wording in the
one state where Lunar is asked. Scenario 3.2 is re-stated to expect the platform's message.

### The lesson, restated more precisely than the report had it

*"A test that enters below the layer that decides can pass while the behaviour it describes is
unreachable"* — true, and row 14 was that test. But the second half does not follow: **shadowed at
one input is not dead everywhere.** Deleting a guard because one fixture cannot reach it is how a
real refusal gets removed. Enumerate the inputs and run each one before concluding a guard earns
nothing.

## The general lesson, worth more than the bug

**A test that enters below the layer that decides can pass while the behaviour it describes is
unreachable.** Row 14 is green, `outOfProjectRefusal` is correct in isolation, and the user still
never sees it. Live verification is what found this; no headless fixture on this path could.
