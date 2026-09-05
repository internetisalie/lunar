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

Run 2026-09-03 against the shipped branch, in the sandbox GoLand 2026.1.3 on the `lunar-builder`
VM (`verify-in-ide`). Caret positions are the IDE's own `line:column` readout; column 1 is the
start of the line, so `2:12` is the `m` of `function t:m()`.

| Check | Result | Notes |
| :-- | :-- | :-- |
| HV-1 Go to Declaration | **PASS** | Caret `3:4` (the `m` of `t:m()`) → <kbd>Ctrl+B</kbd> → **`2:12`** — the method name, not the `function` keyword (col 1) and not the receiver `t` (col 10) |
| HV-2 Find Usages | **FAIL as written on 2026-09-03; PASS on the 2026-09-05 re-run** | The checklist asks for the caret on the `m` of `function t:m()`. On 2026-09-03 <kbd>Alt+F7</kbd> there was **refused** — *"Cannot search for usages from this location."*, caret column verified at `3:12` — while the same search from the **call site** returned what this row expects, making it a fail with a working neighbour rather than a pass. Filed and fixed as **[[BUG-478]]**: the declaration caret was targeting the `LuaNameRef` composite, which the Find Usages provider classifies as nothing. Re-driven at the declaration caret (verified `2:12`) after the fix: the Find window opens on "Global function → m", **1 result**, `t:m()` line **3**, declaration line not listed — with a `function gfun()` control in the same session giving **2 results** |
| HV-3 Two receivers | **PASS** | `t:m()` → `2:12`; `u:m()` → `4:12`. The two same-named methods do not merge |
| HV-4 Annotated, cross-file | **PASS** | <kbd>Ctrl+B</kbd> on `b:setName("x")` in `hv4use.lua` opened **`hv4cls.lua` at `3:18`** — the start of `setName` in `function Builder:setName(n) end` |
| HV-5 Refusals | **PASS (chain, suffix); self and cross-file-global rows not run** | Each refusal was driven **with a positive control on the same line of the same file**, so "nothing happened" is a refusal and not a dead keystroke. Chain: `go` at `6:11` → unchanged, **no jump to `function A:go()`**; control `next` at `6:4` → `5:12`. Suffix: `a.b:m()`'s `m` at `5:6` → unchanged, **no jump to `function a:m()`**; control, the receiver `a` → `1:7` |
| HV-6 Lexical name not offered | **PASS, both halves** | In one file: `t:m()`'s `m` (`4:4`) → **`2:12`** (the method); `print(m)`'s `m` (`5:8`) → **`3:7`** (`local m = 1`). The withdrawal and the intact lexical route, side by side |
| HV-7 No balloon, no lag | **PASS (exception half); lag half not run** | Zero occurrences of `LuaColonCallResolution`, `notNullChild` or `TestLoggerAssertionError` in `idea.log`, and no `ERROR`/`SEVERE` beyond platform noise (`CefApp`, `go-linter`, the version banner), across every step above. The ten-second typing test in a corpus file was not driven |
| HV-8 Consumer-visible changes | **PASS on 1, 2, 3, 4, 5, 6** | **1-2**: the Problems panel lists `Deprecated API: Use the method instead` at **`:4`** and **`:6`** only — **nothing at line 5**, the `t:m()` call site, where the pre-feature build raised a third. **3**: <kbd>Shift+F6</kbd> at the call site gives *"Cannot perform refactoring. Renaming a 'function Obj:method()' declaration is not supported yet…"* — the `METHOD_FUNCTION` refusal, **not** an inline template. **5**: <kbd>Ctrl+H</kbd> at the call site declines; control on `local m = {}` opens the Hierarchy window on class `m`. **6**: `t:print( alpha: 1, beta: 2)` — both parameter-name inlays render. **4**: <kbd>Ctrl+Q</kbd> at the call site shows a struck-through `function t:m() : any` above a red **⚠ Deprecated: gone** |
| HV-9 Mirror direction | **PASS on 3, 5, 7; steps 2, 4 blocked, 6 not run** | **3**: rename `local m = 1` → `local count : number = 1` with **`t:m()` untouched** (pre-feature it became `t:count()`). **5**: Lunar reports **"Unused local variable 'm'"** on `local m = 1`, LuaCheck independently agreeing `(W211)`. **7**: `local box = {}` / `function m:m() end` / **`box:m()`** — the receiver rewritten, the method name not (pre-feature: `box:box()`). **2 and 4** drive Find Usages and Safe Delete *at a declaration*, which the defect below blocked; its fix unblocks step 2 (HV-2's re-run drives that gesture), and step 4's Safe Delete has not been re-driven |

### One reported defect was my caret placement; the other was real and I withdrew it wrongly

Both are recorded because the errors were of opposite kinds and both are instructive.

**Quick Documentation was never broken — that one was caret placement.** The first run read "No
documentation found." at a `---@deprecated` colon method and at three other LuaCATS shapes, while a
plain `--` comment rendered, which looked like a clean split between the two comment forms. It was
not. A unit probe on the real entry point found it:
`LuaDocumentationTargetProvider.documentationTargets` returns **1 target with documentation** at
every declaration offset and **0 targets** one character past the identifier — and 0 targets is what
the platform renders as "No documentation found." Every failing case had been driven with `End` and
a Left-count landing one past the name; the one case that "worked" was where the arithmetic happened
to land inside it. Re-driven with the column verified, Quick Doc renders a struck-through
`function t:m() : any` above a red **⚠ Deprecated: gone** at the colon call site, and the same for
`local function f()`. **HV-8 step 4 passes.**

Verified across **three** LuaCATS shapes, each with the caret's column read back from the status bar
rather than inferred from a keystroke count — because generalizing from two fixtures to a whole class
is the same move that produced the wrong report in the first place:

| Shape | Caret | Rendered |
| :-- | :-- | :-- |
| `---@deprecated` on `function t:m()`, driven at the **call site** | `4:3` | struck-through `function t:m() : any` + **⚠ Deprecated: gone** |
| `---@deprecated` on a plain `local function f()`, at its **declaration** | `2:16` | struck-through `local function f() : any` + **⚠ Deprecated: gone** |
| description + `---@param a number` + `---@return number` on `function u:add(a)`, at the **call site** | `6:3` | `function u:add(a: number) : number`, the description, a **Parameters** row `a (number)` and a **Returns** row `number` |

The third row is the strongest positive this feature has on that surface, and it is more than HV-8
step 4 asks for: it is `design.md` §7's claim that Quick Documentation **gains the method's own doc
where the call site had none**, shown with real content rather than only a deprecation marker.

**Find Usages at a colon-method declaration was a real defect, and the first withdrawal of it was
wrong.** The original report said the action was *silent*; corrected driving showed a **visible**
refusal, and that detail being wrong is why it was withdrawn. **The substance was never about
silence.** The usage set is computable — `canFindUsagesFor` returns true for a `METHOD_FUNCTION`
leaf, and `ReferencesSearch` on that same leaf returns the call site under a green test
(`LuaColonCallFindUsagesTest`) — and the action declines anyway, while a **global function**
declaration searches fine in the same session. That is a defect, not the plain-local
`PsiNameIdentifierOwner` limitation it was mistaken for: for a plain local the refusal is *correct*,
because nothing is behind it. Filed as [[BUG-478]] and since **fixed** — a colon or dotted
declaration name resolves to nothing, so the platform fell through to the `LuaNameRef` composite
that `LuaDeclarationSite.kindOf` does not classify, and the declaration caret now targets its own
IDENTIFIER leaf like the control always did. **HV-2 is recorded above as failing on the original run
and passing on the re-run.**

**Two lessons, and the second cost more than the first.**

1. **Verify the caret's column before believing a negative result.** A control elsewhere in the file
   proves the *action* works; it says nothing about where the caret is. `2:16` and `2:17` are one
   keystroke apart and mean opposite things. Prefer `Home` + N×`Right`, which is checkable against
   the source text, over `End` + N×`Left`, which is not.
2. **Do not withdraw a finding because its description was wrong — re-describe it.** The Find Usages
   defect was visible in a screenshot with a verified caret, and it was dropped because one adjective
   in the report did not survive contact. Correcting a report is cheap; deleting a real defect is
   not, and "PASS in substance, by a different gesture" is how the row nearly stayed green.
