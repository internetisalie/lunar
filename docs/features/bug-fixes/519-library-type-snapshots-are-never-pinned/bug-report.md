---
id: "BUG-519"
title: "A definition library's type snapshots are never pinned, so they rebuild after every edit"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-519: a definition library's type snapshots are never pinned

[[TYPE-11]] pins the type snapshot of a provisioned library file, so a keystroke in a project file
does not rebuild it. On the Neovim definition library ([[TARGET-11]]), no file's snapshot is pinnable.
Every one is rebuilt after each PSI modification. Each one records stdlib globals such as `error`
and `type` as *rescued*, and each one records an *in-progress* hit; per TYPE-11's rule, either
alone forbids the pin.

Found while attributing TARGET-11's highlighting cost (TARGET-11 DR-08). The measurement was
executed; the mechanism below is read from the code, not run.

## 1. Reproduction (executed)

Libvirt builder, `test --rerun --no-build-cache -PwithCorpus`, HEAD `a6d2605c`. lazy.nvim
`85c7ff37` is swept with the `neovim` catalog library provisioned through
`CorpusDefinitionLibraries` (the real `LuaDefinitionLibraryProvider` root, so
`LuaLibraryProvenance.isProvisioned` is true).

Then, for each configuration, the probe:
1. increments `PsiModificationTrackerImpl` and drops the resolve caches;
2. highlights the 54 files;
3. counts `LuaTypesSnapshot.forFile` builds per file, and records
   `LuaTypesSnapshot.isPinnable(file, frame)` with the frame's sets (first three entries of each).

Run `c6`, the all-inspections pass. The paths are under
`neovim-779c36969e31080503be2dfccbcc3f999e289606/`; the output is
`~/.cache/claude-scratch/lunar/target-11/r5/results/c6-library-snap/probe.txt`, and the probe diff is
that run's `tree.diff`:

```
vim/_core/shared.lua builds=5 ms=32401 pinnable=false absences=[global:vim, global:unpack, module:vim._core.options] unreplayedWarm=0 inProgress=26 rescued=[global:_G, global:type, global:error]
vim/_core/editor.lua builds=4 ms=7239 pinnable=false absences=[global:unpack, global:vim, module:vim._core.options] unreplayedWarm=0 inProgress=8 rescued=[global:pairs, global:type, global:error]
vim/_meta/vimfn.gen.lua builds=4 ms=2348 pinnable=false absences=[] unreplayedWarm=0 inProgress=1 rescued=[global:error]
vim/_core/system.lua builds=4 ms=1817 pinnable=false absences=[] unreplayedWarm=0 inProgress=4 rescued=[global:setmetatable, global:error, global:type]
vim/_core/options.lua builds=4 ms=1153 pinnable=false absences=[global:vim, global:unpack] unreplayedWarm=0 inProgress=3 rescued=[global:setmetatable, global:error, global:type]
vim/_meta/api.gen.lua builds=3 ms=919 pinnable=false absences=[] unreplayedWarm=0 inProgress=1 rescued=[global:error]
vim/_meta/builtin.lua builds=3 ms=20 pinnable=false absences=[] unreplayedWarm=0 inProgress=1 rescued=[global:error]
vim/_meta/misc.lua builds=2 ms=3 pinnable=false absences=[] unreplayedWarm=0 inProgress=1 rescued=[global:error]
vim/_meta/json.lua builds=2 ms=3 pinnable=false absences=[] unreplayedWarm=0 inProgress=1 rescued=[global:error]
```

The output has no `pinnable=true` line (`grep -c` gives 0). The per-file lines were printed for
this pass only. Build times
include nested builds, so they overlap and do not add up.

## 2. Expected vs actual

- **Expected:** a provisioned library file whose type depends on nothing the user can change is
  pinned once. It is not rebuilt by edits elsewhere (TYPE-11's purpose).
- **Actual:** each Neovim library file is rebuilt two to five times in one highlighting pass, after
  every tick. Even the `_meta` declaration files are rebuilt; their only rescued global is `error`.

## 3. Context

- **Read, not run:**
  - `LuaTypesSnapshot.isPinnable` (`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt:354`)
    requires `rescuedGlobals` and `inProgressHits` to be empty.
  - `LuaTypeManagerImpl.doResolveGlobal` reports a rescued global whenever project scope does not
    answer and all scope does (`LuaTypeManagerImpl.kt:232`). The bundled stdlib is outside project
    scope, so reading `error` or `type` would always count. This is a reading of the code; no
    experiment isolated it.
- **TYPE-11's own record** (`docs/features/type/11-library-snapshot-invalidation/design.md`, "What
  the fixes cost") priced this rule at zero lost pins. It also warned that the zero was structural:
  none of the files it measured referenced another library's global during its own build.
- **Cost, measured but not attributed to this:** with no inspection enabled, the lazy.nvim pass is
  21.8 s without the library and 25.9 s with it (TARGET-11 DR-08). How much of that is these rebuilds
  was not measured.
- **Not established:**
  - whether other catalog libraries (`love2d`, `busted`, `luassert`, `openresty`) are affected: only
    Neovim was run;
  - whether pinning a stdlib-rescued snapshot would be correct, which is a question of TYPE-11's
    out-ranking rule (§1.10 V2).
