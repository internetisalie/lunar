---
id: "BUG-97"
title: "Any edit to any file of lua-language-server's script/vm costs about a minute of type inference, even in a 15-line file"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-97: Any edit to any file of lua-language-server's `script/vm` costs about a minute of type inference, even in a 15-line file

Found by MAINT-56's representative run. Its `luals` member had been running for 149 minutes without
finishing when the run was stopped. The same run measured gitsigns, 61 files, in 23.6 minutes.

## 1. Reproduction
The corpus member `luals` (`tooling/corpus/type-corpus.json`): `LuaLS/lua-language-server` at commit
`a58fc7b01e1746c46275c71b0e61bfed40a6296d`, fetched into `test/corpus/luals` by
`python3 tooling/corpus/fetch-corpus.py --manifest tooling/corpus/type-corpus.json`. Root `script/vm`
(21 files, 10 497 lines), `moduleRoot` `script` (source path `<checkout>/script/?.lua;<checkout>/script/?/init.lua`),
target Standard Lua 5.4, no definition libraries.

In a `BasePlatformTestCase` (light fixture, builder `libvirt`, GoLand 2026.2.3 test platform):
1. Pin the target and source path as above, and copy `test/corpus/luals/script/vm` into the fixture
   with `myFixture.copyDirectoryToProject`.
2. Visit the files smallest first. For each one, time `LuaTypesSnapshot.forFile(file).getErrors()`.
   Then append `"\n"` to the file's document in a `WriteCommandAction`, commit it, and time the same
   call again.

## 2. Expected vs Actual Behavior
- **Expected**: the snapshot of a 15-line file after appending a newline costs about what the same
  file costs on its own: milliseconds to seconds.
- **Actual** (one run):

| file | lines | first read (ms) | after the edit (ms) |
|---|---|---|---|
| `library.lua` | 15 | 68 329 | 65 497 |
| `init.lua` | 25 | 0 | 68 277 |
| `precompile.lua` | 20 | 0 | 58 147 |
| `field.lua` | 59 | 0 | 55 472 |
| `node.lua` | 537 | 0 | 56 837 |
| `type.lua` | 930 | 0 | 53 916 |
| `compiler.lua` | 2 807 | 0 | 65 369 |

  All 21 files cost 53–72 s after an edit, whatever their size. The first file read took 68 s. Each
  later first read took 0 ms, so the first read had already produced every file's snapshot. An edit
  to one file therefore costs what analysing the whole copied directory costs.
- In MAINT-54's audit harness (`AuditInjector`), every measured site edits a file and reads its
  snapshot up to three times, so each `luals` site costs about three minutes.
- EDT stack while `luals` ran in the representative run (two jstacks, 20 s apart): deep recursive
  `VariableElement.resolveRead`/`resolveWrite` chains (`LuaTypeNodes.kt:206-245`) and
  `LuaGraphType$Table.hashCode`, under `AuditInjector.measureOpened` → `apply` → `keys`
  (`AuditInjector.kt:92,144,161,212`).

## 3. Related
[[BUG-96]]: the same stack frames, inside one file. MAINT-56's `results.md` records `luals` as timed
out, so its rule weights are to be re-measured after this bug is fixed.
