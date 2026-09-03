---
id: "NAVIGATION-13-CHECKLIST"
title: "13: Verification Checklists"
type: "qa"
parent_id: "NAVIGATION-13"
folders:
  - "[[features/navigation/13-colon-call-resolution/requirements|requirements]]"
---

# Verification Checklists: NAV-13 — Colon Call Site Resolution

Run these in the containerised GoLand (`.agents/skills/verify-in-ide/SKILL.md`). They cover the
surfaces no unit fixture reaches: the Ctrl+Click target, the Go to Declaration jump, the Find Usages
tool window and the gutter. Every fixture below is a **separate file in the sandbox project** — a
sibling file declaring the same class name binds an arm to the wrong file
(`requirements.md`, "Test Cases").

## HV-1 — Go to Declaration on a plain local table

1. Create `hv1.lua`:
   ```lua
   local t = {}
   function t:m() end
   t:m()
   ```
2. Ctrl+Click the `m` of `t:m()` on line 3.
3. **Expect**: the caret jumps to the `m` of `function t:m()` on line 2 — to the *method name*, not
   to the `function` keyword and not to the receiver `t`.
4. **Expect**: hovering the same `m` with Ctrl held underlines it and shows a single target, not a
   "multiple implementations" popup.

## HV-2 — Find Usages on the declaration

1. In `hv1.lua`, put the caret on the `m` of `function t:m()`.
2. <kbd>Alt+F7</kbd>.
3. **Expect**: the tool window opens with **one** usage, on line 3, grouped under "global function"
   (`LuaDeclarationSite.METHOD_FUNCTION.usageViewType`), and the preview shows `t:m()`.
4. **Expect**: the declaration line itself is **not** listed as a usage.

## HV-3 — Two receivers with a same-named method do not merge

1. Create `hv3.lua`:
   ```lua
   local t = {}
   function t:m() end
   local u = {}
   function u:m() end
   t:m()
   u:m()
   ```
2. Find Usages on `function t:m()`'s `m`.
3. **Expect**: exactly one usage, on the `t:m()` line. The `u:m()` line is absent.
4. Repeat for `function u:m()`'s `m`; expect the mirror result.

## HV-4 — An annotated receiver, across files

1. Create `hv4cls.lua`:
   ```lua
   ---@class Builder
   local Builder = {}
   function Builder:setName(n) end
   return Builder
   ```
2. Create `hv4use.lua`:
   ```lua
   ---@type Builder
   local b
   b:setName("x")
   ```
3. Ctrl+Click `setName` in `hv4use.lua`.
4. **Expect**: the editor opens `hv4cls.lua` with the caret on `setName`.
5. Find Usages on `hv4cls.lua`'s `setName`. **Expect**: one usage, in `hv4use.lua`.

## HV-5 — The refusals produce nothing, not a wrong jump

Each row is its own file. In every one, Ctrl+Click the member name of the **last** call.

| file | content | expect |
| :-- | :-- | :-- |
| `hv5chain.lua` | `local A = {}` / `function A:go() end` / `local B = {}` / `function B:go() end` / `function A:next() return B end` / `A:next():go()` | Ctrl+Click on the second `go` does nothing — **specifically it must not jump to `function A:go()`** |
| `hv5suffix.lua` | `local a = {}` / `a.b = {}` / `function a:m() end` / `function a.b:m() end` / `a.b:m()` | Ctrl+Click on the call's `m` does nothing — **it must not jump to `function a:m()`** |
| `hv5self.lua` | `local C = {}` / `function C:b() end` / `function C:a() self:b() end` | Ctrl+Click on `b` in `self:b()` does nothing |
| `hv5global.lua` + `hv5globaluse.lua` | `Obj = {}` / `function Obj:m() end` — and, in the second file, `Obj:m()` | Ctrl+Click in the second file does nothing (`risks-and-gaps.md` Gap 2.2) |

## HV-6 — A lexical name of the same spelling is not offered

1. Create `hv6.lua`:
   ```lua
   local t = {}
   function t:m() end
   local m = 1
   t:m()
   print(m)
   ```
2. Ctrl+Click the `m` of `t:m()`.
3. **Expect**: the caret jumps to `function t:m()`'s `m`, **not** to `local m = 1`.
4. Ctrl+Click the `m` of `print(m)`.
5. **Expect**: the caret jumps to `local m = 1` — the ordinary lexical route is unchanged.

## HV-7 — No new error balloon or freeze while typing

1. Open `hv1.lua` and type a partial method header above the call — `function` on its own line, then
   pause, then delete it.
2. **Expect**: no "IDE internal error" balloon, no red exception in
   `build/idea-sandbox/GO-*/log/idea.log` mentioning `LuaColonCallResolution`, `notNullChild` or
   `TestLoggerAssertionError`.
3. **Expect**: no perceptible typing lag in a file with many colon calls — open a corpus file such as
   `test/corpus/zerobrane/src/editor/debugger.lua` and type at the end of it for ten seconds.

## HV-8 — The consumer-visible changes, live (`NAV-13-08`)

These are the surfaces `design.md` §7's executed enumeration moved. Each is measurable in a unit
fixture, but none is visible to the corpus ratchet (0 `---@` tags across its files), so the live
check is the one that shows a user what changed.

1. Create `hv8dep.lua`:
   ```lua
   ---@deprecated Use the method instead
   local function m() end
   local t = {}
   function t:m() end
   t:m()
   m()
   ```
2. **Expect**: the `m` of `t:m()` on line 5 carries **no** strikethrough and no
   `Deprecated API: Use the method instead` warning. The `m()` call on line 6 still does, and so does
   the `m` of `function t:m()` on line 4.
3. Put the caret on the `m` of `t:m()` and press <kbd>Shift+F6</kbd>.
   **Expect**: the "Cannot perform refactoring" hint — the colon-method refusal.
   **What this replaces is not a dialog.** Before this feature the platform resolved the caret to
   the *local function* `m`, which is file-local, so `LuaInplaceRenameHandler` accepted the context
   and <kbd>Shift+F6</kbd> started an **inline rename template** in the editor — no dialog, no
   preview, and `LuaRenameProcessor` never consulted. Committing that template rewrote **four**
   occurrences: `local function m`, `function t:m`, the call `t:m()` and the plain call `m()`
   (`design.md` §7, executed). If you want to see the pre-state, run this step against a build
   without the feature; the tell is a green in-editor edit box on the `m`, not a modal.
4. Create `hv8doc.lua`:
   ```lua
   local t = {}
   ---@deprecated gone
   function t:m() end
   t:m()
   ```
   Put the caret on the `m` of `t:m()` and press <kbd>Ctrl+Q</kbd>.
   **Expect**: Quick Documentation opens on `t:m`, showing the `@deprecated` tag. Before this feature
   it showed nothing.
5. Create `hv8hier.lua`:
   ```lua
   ---@class m
   local m = {}
   local t = {}
   function t:m() end
   t:m()
   ```
   Put the caret on the `m` of `t:m()` on line 5 and invoke **Navigate | Type Hierarchy**
   (<kbd>Ctrl</kbd>+<kbd>H</kbd>).
   **Expect**: nothing opens — the action declines. Before this feature the caret resolved to the
   `local m = {}` declaration and the Type Hierarchy tool window opened on the class `m`, which
   `t:m()` never named.
6. Create `hv8hint.lua`:
   ```lua
   local t = {}
   function t:print(alpha, beta) end
   t:print(1, 2)
   ```
   **Expect**: parameter-name inlay hints `alpha:` and `beta:` appear at the two arguments. Before
   this feature they were suppressed, because the member name `print` resolved to the stdlib global
   of that name.

## HV-9 — The mirror direction, live (`NAV-13-08`)

Steps 1-5 of HV-8 are all driven **at the call site**. This checklist drives the other end of the
binding the feature breaks — the same-named declaration — because that is where a user *loses*
something, and losses are the half a demo does not show.

1. Create `hv9mirror.lua`:
   ```lua
   local t = {}
   function t:m() end
   local m = 1
   t:m()
   ```
2. Put the caret on the `m` of `local m = 1` and press <kbd>Alt+F7</kbd> (Find Usages).
   **Expect**: the tool window reports **no usages** of the local `m`. Before this feature it
   reported one — the `m` of `t:m()`, which is a table key and never referred to the local.
3. With the caret still on `local m = 1`, press <kbd>Shift+F6</kbd> and rename it to `count`.
   **Expect**: the file reads `local count = 1` and the call is still **`t:m()`**. Before this feature
   the call was rewritten to `t:count()`, silently changing which method is called.
4. Put the caret on `local m = 1` and press <kbd>Alt+Delete</kbd> (Safe Delete).
   **Expect**: it deletes without reporting a usage. Before this feature it reported the `t:m()` site
   as a usage and asked for confirmation.
5. **Expect**, on the same file with no caret action: `local m = 1` is greyed as an unused local
   variable. Before this feature the colon member name kept it alive.
6. Create `hv9conflict.lua`:
   ```lua
   local t = {}
   function t:m() end
   local m = 1
   print(m)
   do
     local n = 2
     t:m()
   end
   ```
   Put the caret on the `m` of `local m = 1`, press <kbd>Shift+F6</kbd> and rename it to `n`.
   **Expect**: the rename applies with **no conflicts dialog**, giving `local n = 1` and `print(n)`
   while `t:m()` and the inner `local n = 2` stay as they are. Before this feature a conflicts
   dialog appeared — *"Renaming to 'n' would bind a usage of 'm' to a different declaration that is
   already visible here"* — raised by the `t:m()` site, which was never a usage of the local. The
   inner `local n = 2` is what makes the conflict reachable, and `print(m)` must stay **outside**
   the `do` block: move it inside and the conflict is raised on both sides by that site instead.
7. Create `hv9recv.lua`:
   ```lua
   local m = {}
   function m:m() end
   m:m()
   ```
   Put the caret on the `m` of `local m = {}` and press <kbd>Shift+F6</kbd>, renaming to `box`.
   **Expect**: `local box = {}` / `function m:m() end` / **`box:m()`** — the receiver is rewritten and
   the method name is not. Before this feature the result was `box:box()`, which renames the *call's
   method name* while leaving the declaration `function m:m()` alone: the file no longer calls a
   method that exists.

## Sign-off

| Check | Result | Notes |
| :-- | :-- | :-- |
| HV-1 Go to Declaration | | |
| HV-2 Find Usages | | |
| HV-3 Two receivers | | |
| HV-4 Annotated, cross-file | | |
| HV-5 Refusals | | |
| HV-6 Lexical name not offered | | |
| HV-7 No balloon, no lag | | |
| HV-8 Consumer-visible changes | | |
| HV-9 Mirror direction | | |
