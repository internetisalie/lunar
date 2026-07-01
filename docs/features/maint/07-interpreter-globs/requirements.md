---
id: "MAINT-07"
title: "MAINT-07: Interpreter Search Path Globs"
type: "feature"
status: "done"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-07: Interpreter Search Path Globs

## Overview

Lunar auto-discovers Lua interpreters by scanning a fixed list of directories
(`LuaInterpreterService.PATHS_UNIX` / `PATHS_WINDOWS`). Those entries are literal
directory paths, so version-suffixed install locations must be hand-enumerated — the
Windows list spells out `C:\Program Files\Lua 5.1` … `Lua 5.4` one line at a time and
carries a `// TODO: Search Path Globs`. This feature adds glob (`*`, `?`) expansion to
**directory components** of a search-path entry, so a single pattern such as
`C:\Program Files\Lua 5.*` expands to every matching install directory, each of which is
then scanned for interpreters. Parent epic: [MAINT](../requirements.md).

## Scope

### In Scope
- Glob (`*`, `?`) metacharacters in any **directory segment** of an interpreter search-path
  entry, expanded against the real filesystem to the set of existing directories that match.
- Each expanded directory is scanned by the existing `LuaInterpreterService.find(Path)` for
  interpreter binaries (no change to the family / executable-name model).
- Replacing the hand-enumerated `PATHS_WINDOWS` `Lua 5.1..5.4` entries with glob patterns
  and deleting the `// TODO: Search Path Globs`.
- Deterministic ordering and non-crashing behavior on missing base directories, non-matching
  patterns, and I/O errors.

### Out of Scope
- **Executable-leaf globbing** (matching binaries like `lua*` as the final path segment).
  Family-level executable-name globbing already exists via `LuaInterpreterFamily.find` and
  the `isGlob(exeName)` branch of `find` (`LuaInterpreterService.kt:37`) and is neither
  changed nor extended here.
- Recursive `**` (cross-segment) globbing and brace `{a,b}` alternation.
- Relative search-path specs (all interpreter search paths in this repo are absolute).
- Globbing of the module-resolution `package.path` (`PathConfiguration`/`SourcePathPattern`) —
  a separate subsystem, not touched by this feature.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-07-01 | **Directory glob expansion** | M | A search-path entry containing `*`/`?` in one or more directory segments expands to every existing directory whose path matches the pattern; each is then scanned for interpreters. |
| MAINT-07-02 | **Literal paths unchanged** | M | A search-path entry with no glob metacharacter resolves to exactly the single literal directory (existence still checked downstream by `find`), preserving current discovery behavior. |
| MAINT-07-03 | **Globbed Windows defaults** | M | `PATHS_WINDOWS` uses `C:\Program Files\Lua 5.*` and `C:\Program Files (x86)\Lua 5.*` in place of the four hand-enumerated `Lua 5.1..5.4` entries, and the `// TODO: Search Path Globs` comment is removed. |
| MAINT-07-04 | **Deterministic ordering** | S | Directories matched by one glob segment are returned sorted ascending by their simple name, so discovery order is stable across filesystems. |
| MAINT-07-05 | **Robust edge handling** | S | A non-existent base directory, a pattern that matches nothing, or an I/O/permission error yields an empty result for that entry and never throws out of `findInterpreters`. |
| MAINT-07-06 | **Env-var composition** | C | Environment-variable substitution (`${HOME}`) is applied before glob expansion, so specs like `${HOME}/lua*/bin` work. |

## Detailed Specifications

### MAINT-07-01: Directory glob expansion
A search-path entry is a directory spec whose segments are separated by `/` or `\`. A
segment is a **glob segment** if `isGlob(segment)` is true (contains `*` or `?`), otherwise
a **literal segment**. Expansion walks the spec segment-by-segment from the filesystem root:

- A literal segment `s` resolves to `base.resolve(s)` if that child is an existing directory,
  else contributes nothing.
- A glob segment `g` resolves to every **direct child directory** of `base` whose simple
  name matches `patternFromGlob(g)` (`LuaInterpreterService.kt:219`).

Matching descends only through explicit segments (finite depth = segment count); files and
non-directory entries are excluded at every segment. Example: given directories
`/opt/lua5.1/bin` and `/opt/lua5.4/bin`, the spec `/opt/lua5.*/bin` expands to
`[/opt/lua5.1/bin, /opt/lua5.4/bin]`.

### MAINT-07-02: Literal paths unchanged
When `isGlob(spec)` is false, expansion returns exactly `listOf(Path.of(spec))` — one
element, unconditionally, regardless of whether the directory exists. Existence is then
checked by the existing `find` via `directoryAsVirtualFile()` (`LuaInterpreterService.kt:31`,
`:168`). This guarantees identical behavior to the pre-feature code path for every existing
literal entry in `PATHS_UNIX`.

### MAINT-07-03: Globbed Windows defaults
The glob `Lua 5.*` translates (via `patternFromGlob`) to the regex `^Lua 5\..*$`, which
matches `Lua 5.1`, `Lua 5.2`, `Lua 5.3`, `Lua 5.4` (and any future `Lua 5.x`) but not
`Lua 5` or `Lua 55`. The literal segments `C:` and `Program Files (x86)` are resolved
verbatim (the `(`/`)` never reach `patternFromGlob`, so they are not misread as regex).

## Behavior Rules
- Expansion runs on the discovery background thread (`findInterpreters` is invoked from the
  `newProjectBackgroundTask` in `LuaInterpretersTable.kt:151`); it performs `java.nio.file`
  I/O and must call `ProgressManager.checkCanceled()` once per segment iteration.
- Only directories are ever yielded by expansion; interpreter *binaries* are located
  afterward by the unchanged `find(Path)`.
- Only `*`, `?`, `.` and `\` are given special meaning by `patternFromGlob`; every other
  character (including spaces) matches literally.
- Expansion assumes an **absolute** spec (leading `/` on Unix, `<Drive>:` on Windows).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-07-01 | Temp tree with dirs `base/Lua 5.1`, `base/Lua 5.4` and a file `base/README` | `expandSearchPath("$base/Lua 5.*")` | Returns `[base/Lua 5.1, base/Lua 5.4]` (file excluded, sorted) |
| 2 | MAINT-07-01 | Temp tree `base/lua5.1/bin`, `base/lua5.4/bin`, `base/lua5.4/share` | `expandSearchPath("$base/lua5.*/bin")` | Returns `[base/lua5.1/bin, base/lua5.4/bin]` (mid-segment glob; `share` excluded) |
| 3 | MAINT-07-02 | Temp dir `base/Lua 5.1` exists | `expandSearchPath("$base/Lua 5.1")` | Returns a single element equal to `Path.of("$base/Lua 5.1")` |
| 4 | MAINT-07-02 | Path `/no/such/literal/dir` does not exist | `expandSearchPath("/no/such/literal/dir")` | Returns a single element `Path.of("/no/such/literal/dir")` (size 1, unfiltered) |
| 5 | MAINT-07-03 | Temp tree `pf/Lua 5.1`, `pf/Lua 5.4`, `pf/Lua 5` | `expandSearchPath("$pf/Lua 5.*")` | Returns `[pf/Lua 5.1, pf/Lua 5.4]` (`Lua 5` excluded — no dot) |
| 6 | MAINT-07-04 | Temp tree `base/lua5.4`, `base/lua5.1`, `base/lua5.2` | `expandSearchPath("$base/lua5.*")` | Returns exactly `[base/lua5.1, base/lua5.2, base/lua5.4]` in ascending name order |
| 7 | MAINT-07-05 | Base `/no/such/base` does not exist | `expandSearchPath("/no/such/base/Lua 5.*")` | Returns `[]` (empty, no exception) |
| 8 | MAINT-07-05 | Temp `base` exists but has no `Ruby *` child | `expandSearchPath("$base/Ruby *")` | Returns `[]` (empty, no exception) |
| 9 | MAINT-07-01 | Temp tree `base/lua51`, `base/lua54`, `base/luaX` | `expandSearchPath("$base/lua5?")` | Returns `[base/lua51, base/lua54]` (`?` matches one char; `luaX` excluded) |

## Acceptance Criteria
- [ ] MAINT-07-01: glob directory segments expand to matching existing directories (TC 1, 2, 9).
- [ ] MAINT-07-02: literal specs return one element unchanged (TC 3, 4).
- [ ] MAINT-07-03: `PATHS_WINDOWS` uses `Lua 5.*` globs and the TODO comment is gone (TC 5).
- [ ] MAINT-07-04: matches are ordered ascending by name (TC 6).
- [ ] MAINT-07-05: missing base / no match / I/O error yield `[]` without throwing (TC 7, 8).
- [ ] `tooling/gce-builder/gce-builder.sh run test` is green.

## Non-Functional Requirements
- **Threading**: expansion executes on the discovery pooled/background thread; no EDT work,
  no read/write action (uses `java.nio.file`, not PSI/VFS writes). `ProgressManager.checkCanceled()`
  is invoked each segment iteration (engineering-contract §2 cancellation).
- **Method size**: each new function ≤30 executable lines (contract §3); expansion is split
  into `expandSearchPath` + a private `expandSegment` helper.
- **Memory**: no retained `Project`/`Editor`/`VirtualFile` refs; only `java.nio.file.Path`
  values flow through expansion.

## Dependencies
- `net.internetisalie.lunar.platform.LuaInterpreterService` (host of `findInterpreters`, `find`,
  and the `isGlob`/`patternFromGlob` primitives).
- No new plugin.xml extension points (the service is `@Service(Service.Level.APP)`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
