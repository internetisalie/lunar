---
id: "BUG-436"
title: "Five whole-project indexers accept only `*.lua`, so `.rockspec` / `.luacheckrc` / `.busted` declarations are invisible"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-436: the Lua indexers re-state the file-type registration and get it wrong

`LuaFileType` is registered for **four** things (`plugin.xml:99-100`):

```xml
extensions="lua;rockspec"
fileNames=".luacheckrc;.busted"
```

Every whole-project index re-states that as `file.extension == "lua"`, which is the first
half of the first half. Anything declared in a `.rockspec`, a `.luacheckrc` or a `.busted`
file is therefore absent from the index — not stale, **absent**, and absent in the direction
no gate looks.

## Sites

| file | line | consequence |
| :-- | :-- | :-- |
| `lang/indexing/LuaGlobalAssignmentIndex.kt` | `:63` | **the load-bearing one** — completion cannot see a global declared in a `.rockspec` at all, because this index selects the candidate declaring file |
| `lang/indexing/LuaCatsTypeNameIndex.kt` | `:58` | a bare `---@class` in a `.rockspec` is invisible to Go to Class / Go to Symbol |
| `lang/indexing/LuaMemberFieldIndex.kt` | `:52` | `@field` members declared in those files are missing |
| `lang/indexing/LuaDescriptionIndex.kt` | `:48` | descriptions missing for the same |
| `lang/indexing/LuaFileBindingsIndex.kt` | `:87` | ANDs the extension test **on top of** a correct `LuaFileInputFilter` — the file-type check is already there and is then narrowed away |
| `run/RuntimeLibraryProvider.kt` | `:43` | not an index; filters library files the same way |

`LuaReceiverMemberIndex` had the same defect and is **already fixed** (`fcce5966`) — it now
uses `DefaultFileTypeSpecificInputFilter(LuaFileType)`, which is the shape to copy.

## Measured

Found during COMP-09 Phase 3 review. A `Widget` receiver declaring one member in each of
`.lua`, `.rockspec`, `.luacheckrc` and `.busted`, through the `@class` door:

```
with the narrow filter:  [fromLua, same]
with the file-type filter: [fromBusted, fromLua, fromLuacheckrc, fromRockspec, same]
```

## The fix, and the platform trap in it

Replace each with `DefaultFileTypeSpecificInputFilter(LuaFileType)` and **bump that index's
`getVersion()`** — the index content changes and a persisted index would otherwise mask both
the defect and the fix.

**Instantiate it; never subclass it.** `RequiredIndexesEvaluator.toHint` converts the filter
into a real file-type predicate only when
`filter.javaClass == DefaultFileTypeSpecificInputFilter::class.java`. A subclass silently
degrades to accept-everything — a performance regression that no test would show.

## Why this went unnoticed

Every instrument in this area is built to catch a **superset**: the COMP-09 golden diffs for
added rows, the corpus ratchet stops on movement, and COMP-09-06's acceptance is "if any
baseline moves, enumeration has become a type source: stop". A **subset** — members that
quietly stop existing — passes all of them. Worth remembering when designing the gate for
this fix: assert exact sets on a fixture that declares members in all four file kinds, and
mutation-prove it by restoring the narrow filter.

## Scope

Deliberately **not** fixed inside COMP-09 Phase 3's remediation: five indexers plus a
provider is its own change, on a defect that predates the feature. Related: [[COMP-09]]
Phase 3, and `fcce5966` for the worked example.
