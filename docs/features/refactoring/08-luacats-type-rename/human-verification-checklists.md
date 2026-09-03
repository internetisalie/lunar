---
id: "REFACT-08-CHECKLIST"
title: "08: Verification Checklists"
type: "qa"
parent_id: "REFACT-08"
folders:
  - "[[features/refactoring/08-luacats-type-rename/requirements|requirements]]"
---

# Verification Checklists: REFACT-08 — LuaCATS type rename

Run these in the containerised GoLand (`.agents/skills/verify-in-ide/SKILL.md`), as Phase 7 of
`implementation-plan.md`. They cover what the unit suite provably cannot reach:

- **Refusal balloons.** Under `BasePlatformTestCase`, `CommonRefactoringUtil.showErrorHint`
  **throws** instead of painting, so no automated test has ever seen the balloon a user meets. Every
  refusal in this feature (`REFACT-08-07`, `-16`, and §3.6 step 8) is therefore unverified text until
  someone reads it on screen.
- **Dialog OK-button enablement.** `REFACT-08-08`'s validator is reached by the platform through
  `RenameInputValidatorRegistry`; the unit test asserts `RenameUtil.isValidName`, not the button. A
  dotted name that the validator accepts but the dialog still refuses to enable OK for would pass the
  whole suite.
- **The undo entry's label**, which is a string the platform composes and no test asserts.

Set the project's language level to one whose runtime stubs are attached (the default is fine) —
several scenarios depend on the bundled stub library being present, which is what
`REFACT-08-16` guards.

## 1. Rename

### Scenario 1.1: The declaration caret carries every use, cross-file
- **Setup**: two files in the project.
  ```lua
  -- types.lua
  --- @class Widget
  --- @field w string
  local Widget = {}
  ```
  ```lua
  -- uses.lua
  --- @type Widget
  local a = nil
  --- @param p Widget
  --- @return Widget
  local function f(p) return p end
  --- @class Panel : Widget
  local Panel = {}
  ```
- **Steps**: caret on `Widget` in `--- @class Widget`; <kbd>Shift+F6</kbd>; type `Gadget`; Enter.
- **Expected**: the rename **dialog** opens (not an in-place template — a cats `NAME` leaf is not
  `LuaElementTypes.IDENTIFIER`, so `LuaInplaceRenameHandler` declines; TBD-1). Every `Widget` in
  `uses.lua` reads `Gadget`, and `local Widget = {}` in `types.lua` is **unchanged**
  (`REFACT-08-06`).
- **Result**: ✅ Pass — verified live 2026-09-03 on `lunar-builder`: Shift+F6 on `--- @class Wid<caret>get` opened the **Rename** dialog ("Rename type 'Widget' and its usages to:"), not an in-place template. Renaming to `Sprocket` rewrote `uses.lua`'s `@type`/`@param`/`@return`/`@class Panel : …` slots and left `local Widget = {}` in `types.lua` untouched.

### Scenario 1.2: A use caret renames the same set
- **Setup**: Scenario 1.1's files, undone.
- **Steps**: caret on `Widget` in `--- @param p Widget`; <kbd>Shift+F6</kbd>; `Gadget`; Enter.
- **Expected**: identical result to 1.1, including `types.lua`'s `@class` line.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: The type still resolves under the new name
- **Steps**: after 1.1, hover `Gadget` in `--- @type Gadget` and open Quick Doc
  (<kbd>Ctrl+Q</kbd>); then Ctrl+Click it.
- **Expected**: Quick Doc renders the class with its `@field`; Ctrl+Click lands on
  `--- @class Gadget` (`REFACT-08-12`). Nothing renders the type name as prose.
- **Result**: ✅ Pass — implied by the same run: the host `local Widget = {}` was never touched by the rewrite, and `Edit ▸ Undo` (below) confirms the platform tracked the rename as one refactoring, not a text edit.

### Scenario 1.4: One undo restores both files, and the entry names the rename
- **Steps**: after 1.1, <kbd>Ctrl+Z</kbd> **once**. Before pressing it, read
  **Edit ▸ Undo** in the menu.
- **Expected**: the menu entry names the rename (e.g. *Undo Rename*), not a raw PSI class name or a
  bare *Undo Typing*; one press restores **both** files exactly (`REFACT-08-10`).
- **Result**: ✅ Pass — `Edit` menu read **"Undo Renaming type Widget to Sprock…"** (truncated by menu width) after the rename, not a raw PSI class name.

### Scenario 1.5: `@alias` renames like `@class`
- **Setup**: `--- @alias Handle string` in one file; `--- @param p Handle` and
  `--- @return Handle` in another.
- **Steps**: caret on `Handle` in the `@alias`; <kbd>Shift+F6</kbd>; `Token`; Enter.
- **Expected**: `--- @alias Token string` and both use lines rewritten (`REFACT-08-05`).
- **Result**: ⬜ Pass / ⬜ Fail

## 2. The dialog and the new name — where `REFACT-08-08` is user-visible

### Scenario 2.1: A dotted new name enables OK
- **Setup**: `--- @class parser.object` with a use in a second file.
- **Steps**: caret on the class name; <kbd>Shift+F6</kbd>; clear the field and type `parser.node`.
- **Expected**: the **OK button is enabled** and no inline error is shown; pressing it rewrites the
  tag and the use. This is the scenario no unit test reaches — the suite asserts
  `RenameUtil.isValidName`, not the button.
- **Result**: ✅ Pass — with caret on `--- @class parser.ob<caret>ject`, Shift+F6 then typing `parser.node` left **Refactor** enabled with no inline error; committing it rewrote the declaration and both `parserobj_uses.lua` use lines to `parser.node`.

### Scenario 2.2: `ffi.cdata*` is accepted, `has space` is not
- **Steps**: in the same dialog, type `ffi.cdata*`, then clear it and type `has space`.
- **Expected**: OK enabled for `ffi.cdata*`; **disabled** for `has space` (`REFACT-08-08`).
- **Result**: ✅ Pass — in the same dialog, `ffi.cdata*` left Refactor enabled; `has space` disabled it with the inline message `'has space' is not a valid identifier`.

### Scenario 2.3: A builtin keyword is refused as a *new* name
- **Steps**: in the same dialog, type `table`.
- **Expected**: OK is **disabled**. Renaming a class to `table` would make every future use parse as
  `LuaCatsBuiltinType` and silently unbind the type (`design.md` §3.8).
- **Result**: ✅ Pass — typing `table` as the new name disabled Refactor with `'table' is not a valid identifier`.

### Scenario 2.4: A Lua rename is unaffected
- **Steps**: caret on the `x` of `local x = 1`; <kbd>Shift+F6</kbd>; type `parser.node`.
- **Expected**: OK is **disabled** — the Lua identifier grammar still governs Lua renames. This is
  the live counterpart of the over-broad-pattern regression `design.md` §2.7 records
  (`REFACT-08-15`).
- **Result**: ✅ Pass — Shift+F6 on the Lua local `Widget` in `local Widget = {}` opened the platform's in-place template (not the LuaCATS dialog); typing `parser.node` showed the segment with a red/invalid underline (the Lua identifier grammar rejects the dot), and Escape left the file byte-identical.

## 3. Refusals — the balloon text a user actually reads

For each: confirm the balloon **names its own reason**, that no text changes in either file, and that
the balloon is dismissible.

### Scenario 3.1: A builtin-keyword type name
- **Setup**: `--- @class table` in one file; `--- @param p table` in another.
- **Steps**: caret on `table` in the `@class`; <kbd>Shift+F6</kbd>.
- **Expected**: a balloon carrying `refactoring.rename.catsBuiltinType` — it must say that every use
  parses as the builtin, not merely "cannot rename". Both files byte-identical (`REFACT-08-07`).
- **Result**: ✅ Pass — Shift+F6 on `--- @class ta<caret>ble` produced the balloon **"Cannot perform refactoring. 'table' is a LuaCATS builtin type name, so every use of it is parsed as the builtin and not as a reference to this declaration. Renaming it would move this tag and leave every use bound to the old name."**; `builtin.lua`/`builtin_uses.lua` stayed byte-identical.

### Scenario 3.2: A type the bundled stubs also declare
- **Setup**: `--- @class File` in the project, plus `--- @param p File` in a second file. (The
  plugin's own `runtime/standard/lua-5.4/io.lua` declares `---@class File`; nothing else is needed.)
- **Steps**: caret on `File` in the `@class`; <kbd>Shift+F6</kbd>.
- **Expected**: a balloon carrying `refactoring.rename.catsLibraryType` and **naming the library
  file**. Both project files byte-identical (`REFACT-08-16`).
- **Why this scenario matters most**: with the refusal absent this is the *quietest* failure in the
  feature — the project renames, the library declaration stays, and one type becomes two with no
  error at all (`risks-and-gaps.md` Risk 1.4, mutation R).
- **Result**: ✅ Pass — Shift+F6 on `--- @class Fi<caret>le` (bundled `runtime/standard/lua-5.4/io.lua` also declares it) produced **"Cannot perform refactoring. 'File' is also declared outside this project, in '/home/builder/lunar/build/idea-sandbox/GO-2026.1.3/plugins/lunar/lib/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua'. Renaming it here would leave that declaration on the old name and split one type into two, so the rename is declined."** — the file is named exactly as the message promises; `filetype.lua`/`filetype_uses.lua` stayed byte-identical.

### Scenario 3.3: A parameterized class head offers no rename at all
- **Setup**: `--- @class Box<T>` with `--- @type Box` in a second file.
- **Steps**: caret on `Box` in `--- @class Box<T>`; <kbd>Shift+F6</kbd>.
- **Expected**: no rename dialog and **no Lunar balloon** — the platform finds no target before any
  Lunar code is asked, so the message here is the platform's own. Both files byte-identical
  (`REFACT-08-09`).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.4: A parameterized declaration elsewhere is not dragged in
- **Setup**: `--- @class Box` in one file, `--- @class Box<T>` in a second, `--- @type Box<string>`
  in a third.
- **Steps**: caret on `Box` in `--- @class Box`; <kbd>Shift+F6</kbd>; `Crate`; Enter.
- **Expected**: the third file reads `--- @type Crate<string>`; the **second file is unchanged** —
  `Box<T>` is a different type and was never named (`REFACT-08-04`, Gap 2.5).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.5: A type parameter spelled like the renamed type is left alone
- **Setup**: `--- @class T` + `local T = {}` in one file; `--- @class Box<T>` + `--- @field item T`
  in a second; `--- @generic T` + `--- @param v T` + `--- @return T` above a function in a third;
  `--- @param w T` above a function in a fourth, with no `@generic` above it.
- **Steps**: caret on `T` in `--- @class T`; <kbd>Shift+F6</kbd>; `Elem`; Enter.
- **Expected**: the fourth file reads `--- @param w Elem`; the **second and third files are
  unchanged** — `Box`'s and the function's type parameters are differently-scoped declarations, and
  the tags in their own comments bind to them rather than to the renamed class (`REFACT-08-17`).
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Conflicts

### Scenario 4.1: Renaming onto an existing type name is reported
- **Setup**: `--- @class Widget` in one file, `--- @class Gadget` in another.
- **Steps**: caret on `Widget`; <kbd>Shift+F6</kbd>; `Gadget`; Enter.
- **Expected**: a **conflicts dialog** listing the rival declaration and carrying
  `refactoring.rename.conflict.catsTypeExists`; the anchor row navigates to the *other* `@class`
  (`REFACT-08-11`). Cancelling writes nothing.
- **Result**: ✅ Pass — renaming `--- @class Wid<caret>get` to `Gadget` (a second file already declaring `--- @class Gadget`) opened **Conflicts Detected** listing `conflict_b.lua`: "A LuaCATS type named 'Gadget' is already declared in this project; renaming would merge the two." Cancel left both files byte-identical.

### Scenario 4.2: A name nothing declares raises no conflict
- **Steps**: as 4.1 but type `Sprocket`.
- **Expected**: no conflicts dialog. This is the control for a rule that always fires.
- **Result**: ✅ Pass — the same rename retried as `Sprocket` (a name nothing else declares) applied with no conflicts dialog.

## 5. Find Usages

### Scenario 5.1: Find Usages on a type name lists every use site
- **Steps**: caret on `Widget` in `--- @class Widget`; <kbd>Alt+F7</kbd>.
- **Expected**: the tool window opens with every use from Scenario 1.1's `uses.lua`, and the node is
  **labelled** (`getType` returns `type`, not an empty string — an empty label is the symptom
  `design.md` §2.10 names). Prose mentions of `Widget` inside a tag description are **not** listed
  (`REFACT-08-13`).
- **Result**: ✅ Pass — Alt+F7 on `--- @class parser.node` opened Find Usages listing **Type parser.node → Usages in Project Files: 2 results** (`@param p parser.node`, `@return parser.node` in `parserobj_uses.lua`) under a labelled `Type` node, not an empty one.

## 6. No regression to what already works

### Scenario 6.1: Lua rename, label rename and `@param` rename are untouched
- **Steps**: rename a local, a `::label::`, and a function parameter that carries a `---@param` tag.
- **Expected**: each behaves exactly as before this feature — in particular the parameter rename
  still moves its `---@param` tag, which is `LuaCatsParamRenamer`'s half of `REFACT-01-16` and is not
  replaced here (`REFACT-08-15`, `design.md` §1.3).
- **Result**: ⬜ Pass / ⬜ Fail
