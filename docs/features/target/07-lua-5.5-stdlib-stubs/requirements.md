---
id: TARGET-07
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-07: Lua 5.5 Standard-Library Stubs"
status: "planned"
priority: "low"
---

# TARGET-07: Lua 5.5 Standard-Library Stubs

## Overview

Ship the `src/main/resources/runtime/standard/lua-5.5/` API-stub set that the Lua 5.5
language level ([`SYNTAX-09`](../../syntax/09-lua-55/requirements.md)) shipped **without**.
SYNTAX-09 wired the `LUA55` language level and the `global` keyword but explicitly deferred
the stdlib stubs (SYNTAX-09 design: "`table.create` … can be added to platform stubs
separately"). This feature completes completion, navigation, and type-inference for the Lua
5.5 standard library by mirroring the existing 5.4 stub set and layering the small 5.5 stdlib
delta on top. It is a resource-only, non-MVP-gating addition — the (platform, version) → root
resolver already recognises the `lua-5.5` path segment, so the files are additive.

## Scope

### In Scope
- A new `runtime/standard/lua-5.5/` directory containing the 10 stub files that mirror the
  5.4 set: `builtin.lua`, `coroutine.lua`, `debug.lua`, `io.lua`, `math.lua`, `os.lua`,
  `package.lua`, `string.lua`, `table.lua`, `utf8.lua`.
- The Lua 5.4 → 5.5 stdlib delta applied on top of the copied baseline (see §Detailed
  Specifications for the exact, enumerated delta).
- Per-file MIT (Lua.org/PUC-Rio) license header on every new file, identical to the existing
  5.1–5.4 stubs.
- A `THIRD-PARTY.md` update extending the "Lua standard-library API stubs" entry to cover
  `lua-5.5` and correcting the file count.
- Automated tests: resolution/completion of a **new** 5.5 stdlib symbol under a 5.5 target,
  a negative test proving version gating (the same symbol does **not** resolve under 5.4), and
  a resource-presence test.

### Out of Scope
- Any Kotlin production-code change to the resolver. The `lua-5.5` path segment is already
  registered in `PlatformVersionRegistry` and mapped to `LUA55` in `Target`; no wiring is
  needed (verified — see design §1). If a genuine wiring gap is found during implementation it
  becomes a risk (risks-and-gaps §Gap 2.1), not silent scope.
- Lua 5.5 **language/parser** features (`global` declarations, read-only for-loop variables,
  named vararg tables) — owned by SYNTAX-09; already shipped.
- LuaJIT/Redis/Valkey/Tarantool/OpenResty/Pandoc stubs — those platforms remain unbundled
  (their `getLibraryFiles` intentionally returns empty).
- The `luacheckStd` value for 5.5 (stays `lua54` until upstream luacheck ships a `lua55` std;
  tracked in risks-and-gaps §2.2).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TARGET-07-01 | **Mirror the 5.4 stub set** | M | `runtime/standard/lua-5.5/` contains exactly the same 10 file names as `runtime/standard/lua-5.4/`, each a copy of its 5.4 counterpart except where the delta below applies. |
| TARGET-07-02 | **`table.create` stub** | M | `lua-5.5/table.lua` declares `function table.create(nseq, nrec)` with LuaCATS annotations matching the sibling stub style. |
| TARGET-07-03 | **`_VERSION` bumped** | M | `lua-5.5/builtin.lua` sets `_VERSION = "Lua 5.5"` (the 5.4 stub sets `"Lua 5.4"`). |
| TARGET-07-04 | **Per-file license header** | M | Every new `.lua` file begins with the byte-identical MIT (Lua.org/PUC-Rio) header used by the 5.1–5.4 stubs. |
| TARGET-07-05 | **Attribution registry updated** | M | `THIRD-PARTY.md`'s Lua stubs entry lists `lua-5.5` and the corrected file count. |
| TARGET-07-06 | **5.5-target resolution** | M | With the project target `Standard 5.5`, a reference to `table.create` in an editor file resolves to the `lua-5.5/table.lua` declaration. |
| TARGET-07-07 | **Version gating (negative)** | M | With the project target `Standard 5.4`, a reference to `table.create` resolves to **zero** declarations (the symbol is 5.5-only). |
| TARGET-07-08 | **Baseline parity of unchanged symbols** | S | Every symbol present in a 5.4 stub file is present in the corresponding 5.5 file (no accidental drops during the copy). |

## Detailed Specifications

### The Lua 5.4 → 5.5 stdlib delta (the crux)

Lua 5.5 (released 2025-12-22) is a small delta over 5.4 for the *standard library* surface.
Most 5.5 changes are language/runtime (the `global` keyword, read-only for-loop variables,
compact arrays, incremental GC, external strings, C-API additions) and carry **no stub
impact**. The genuine, stub-visible stdlib delta is:

| Stub file | Disposition | Delta |
|-----------|-------------|-------|
| `builtin.lua` | **modified** | `_VERSION = "Lua 5.5"` (was `"Lua 5.4"`). No function added/removed. |
| `table.lua` | **modified** | Add `function table.create(nseq, nrec)` (new in 5.5 — pre-sizes a table's array and hash parts). |
| `coroutine.lua` | copied unchanged | — |
| `debug.lua` | copied unchanged | — |
| `io.lua` | copied unchanged | — |
| `math.lua` | copied unchanged | — |
| `os.lua` | copied unchanged | — |
| `package.lua` | copied unchanged | — |
| `string.lua` | copied unchanged | — |
| `utf8.lua` | copied unchanged | — |

**Non-deltas (explicitly called out to prevent mistakes):**
- `table.move` is **NOT** a 5.5 addition — it exists since Lua 5.3 and is already present in
  the 5.4 stub (`lua-5.4/table.lua:84`). The roadmap brief naming it as a 5.5 addition is
  imprecise; do not re-add it.
- `global` is a **keyword**, not a stdlib symbol. It has no stub file and is owned by
  SYNTAX-09. Do not add a `global.lua` to the standard dir (that name is the Redis/Valkey
  ambient-globals file, unrelated).
- No 5.4 stdlib symbol was **removed** in 5.5. The copy is strictly additive.

### TARGET-07-02: `table.create` stub

Append to `lua-5.5/table.lua`, after `table.move`, in the exact style of the sibling
declarations (leading `---` doc lines, `---@param`, `---@return`, empty-body `function`):

```lua
---Creates a new table pre-sized for nseq sequence elements and nrec other entries
---Introduced in Lua 5.5; avoids rehashing when the final size is known in advance
---@overload fun(nseq: integer): table
---@param nseq integer
---@param nrec? integer
---@return table
function table.create(nseq, nrec) end
```

### TARGET-07-03: `_VERSION`

In `lua-5.5/builtin.lua`, the copied line `_VERSION = "Lua 5.4"` becomes
`_VERSION = "Lua 5.5"`. The preceding doc comment `---Lua version string (e.g., "Lua 5.4")`
is updated to `---Lua version string (e.g., "Lua 5.5")`.

### TARGET-07-04: License header

Bytes 1–21 of every new file are the MIT header block used verbatim by the existing stubs
(see `lua-5.4/table.lua:1-21`), including `Copyright © 1994–2025 Lua.org, PUC-Rio.`. Because
each 5.5 file is produced by copying its 5.4 counterpart, the header is preserved automatically
for the unchanged files; the two modified files retain it unchanged.

## Behavior Rules
- Resolution is driven entirely by the project **target**, not by any per-file marker. The
  synthetic library root injected by `LuaLibraryProvider` for `Target(STANDARD, 5.5)` is
  `runtime/standard/lua-5.5/`; the stub-index (`LuaGlobalDeclarationIndex`) picks up every
  `function <ns>.<name>` declaration in that root. Therefore a symbol declared only in the 5.5
  set is visible only when the 5.5 target is active — version gating is automatic (design §1).
- The stub files are data. No ordering, threading, or algorithm is introduced by this feature.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TARGET-07-06 | Project target `Target(STANDARD, "5.5")`; editor file `test.lua` = `table.create(4)` | Resolve the `create` `LuaNameRef` via `multiResolve(false)` | `.size >= 1` (resolves into `lua-5.5/table.lua`). |
| 2 | TARGET-07-07 | Project target `Target(STANDARD, "5.4")`; editor file `test.lua` = `table.create(4)` | Resolve the `create` `LuaNameRef` via `multiResolve(false)` | `.size == 0` (5.5-only symbol not visible at 5.4). |
| 3 | TARGET-07-06 | Project target `Target(STANDARD, "5.5")`; editor file `test.lua` = `table.insert({}, 1)` | Resolve `insert` | `.size >= 1` (5.4-inherited symbol still resolves at 5.5, proving baseline parity — TARGET-07-08). |
| 4 | TARGET-07-01 | Bundled resources | `RuntimeLibraryProvider(project).getLibraryFiles(Target(STANDARD, "5.5"))` | Returns 10 `.lua` files whose name set equals the `lua-5.4` name set. |
| 5 | TARGET-07-02 / -04 | The `lua-5.5/table.lua` resource | Read its bytes | Content contains `function table.create(` and the MIT header line `Copyright © 1994–2025 Lua.org, PUC-Rio.`. |
| 6 | TARGET-07-03 | The `lua-5.5/builtin.lua` resource | Read its bytes | Content contains `_VERSION = "Lua 5.5"` and does **not** contain `_VERSION = "Lua 5.4"`. |

## Acceptance Criteria
- [ ] TARGET-07-01: `lua-5.5/` name set == `lua-5.4/` name set (10 files). (TC 4)
- [ ] TARGET-07-02: `table.create` declared in `lua-5.5/table.lua`. (TC 5)
- [ ] TARGET-07-03: `_VERSION = "Lua 5.5"` in `lua-5.5/builtin.lua`. (TC 6)
- [ ] TARGET-07-04: MIT header present on every new file. (TC 5)
- [ ] TARGET-07-05: `THIRD-PARTY.md` lists `lua-5.5` with corrected count.
- [ ] TARGET-07-06: `table.create` resolves under a 5.5 target. (TC 1, 3)
- [ ] TARGET-07-07: `table.create` does not resolve under a 5.4 target. (TC 2)
- [ ] TARGET-07-08: no 5.4 symbol dropped in the 5.5 copy. (TC 3, 4)

## Non-Functional Requirements
- **Threading**: tests drive resolution on the EDT under a read action and call
  `PlatformLibraryIndex.reload()` on the EDT to rebuild the stub index deterministically
  (mirroring `ValkeyStubResourceTest`). No production threading is introduced.
- **Memory / performance**: resource files only; no retained `Project`/`VirtualFile` refs; the
  10-file set adds negligible index weight (parity with the existing four version dirs).
- **Contract**: any (unlikely) Kotlin change stays ≤30 logic lines/function, ≤3 args; no EDT
  blocking; light fixtures (`BasePlatformTestCase` via `IndexedBasePlatformTestCase`).

## Dependencies
- [`SYNTAX-09`](../../syntax/09-lua-55/requirements.md) — Lua 5.5 language level & `LUA55`
  (done). Provides `LuaLanguageLevel.LUA55` and the `Target` 5.5 → `LUA55` mapping this feature
  relies on.
- [`TARGET-04`](../04-library-resolution/requirements.md) — Library Root Resolution (done).
  Provides `RuntimeLibraryProvider` / `LuaLibraryProvider` that this feature's resources plug
  into with no code change.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
