---
id: "BUG-98"
title: "A local holding a `require`d module whose table is a `@class` is reported as `Class | { ... } is not assignable to { ... }` on its member accesses"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-98: A local holding a `require`d module whose table is a `@class` is reported as `Class | { ... } is not assignable to { ... }` on its member accesses

Found by MAINT-65's DR-08. The findings are HYPOTHESIS severity, which `LuaTypeHypothesisAnnotator`
shows in the editor. Whether a file gets them depends on build order. A file read on its own gets
them. The same file built inside another file's snapshot build (a dependent's `require`) usually
does not.

## 1. Reproduction
Type corpus members gitsigns and blinkcmp. Open the file first, in a fresh project, so it is
built on its own:

| File | Local | Findings |
|---|---|---|
| blinkcmp `lua/blink/cmp/completion/list.lua` | `local cmp = require('blink.cmp')` | 3 × `blink.cmp.API \| { ... } is not assignable to { ... }` (the declaration, `cmp.is_menu_visible`, `cmp.is_ghost_text_visible`) |
| blinkcmp `lua/blink/cmp/fuzzy/init.lua` | `local cmp = require('blink.cmp')` | 3 × `blink.cmp.API \| { ... }` (the declaration, `cmp.get_repo_root`, `cmp.get_library_name`) |
| gitsigns `lua/gitsigns/debug.lua` and three siblings | `log`, `manager` | `Gitsigns.log \| { ... }` × 6 and `gitsigns.manager \| { ... }` × 4 in total |

Per-file digests from the opened-first build and the sweep-order build, plus the finding rows, are
in MAINT-65's `risks-and-gaps.md` (DR-08).

## 2. Expected
No finding. The module's `@class` table defines every member the file uses on that local, so each
access succeeds at run time.

## 3. Actual
The member-access sites, and the local's declaration, get the HYPOTHESIS finding when the file is built
on its own. Built inside a dependent's build they usually do not, and `fuzzy/init.lua` changes
between identical sweeps depending on which file builds it. The same message shape for other
classes (`Gitsigns.Util`, `blink.cmp.Fuzzy`) is already in the base sweep's findings. Related
message shape, different trigger: BUG-94.

## 4. Why it blocks MAINT-65
MAINT-65's change stops building a callee's snapshot inside the caller's build for calls with no lambda
argument. More files are then built on their own, which surfaces this report in five files, and in `fuzzy/init.lua` depending on build order (MAINT-65 DR-05).
