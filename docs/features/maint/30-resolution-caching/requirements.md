---
id: "MAINT-30"
title: "30: Indexing & Resolution Caching"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-30: Indexing & Resolution Caching

Coalesces the indexing-correctness and platform-caching-adoption work from
[`docs/review.md`](../../../review.md) (re-verified 2026-07-17): the over-broad file-bindings
indexer, the hand-rolled snapshot cache, `ResolveCache` adoption, and the scope-walk /
require-extraction copy-paste consolidation that the review traced to real divergence bugs.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 20 | `LuaFileBindingsIndex` records every `PsiNamedElement` incl. usages — globals can resolve to a mere usage in another file |
| 21 | `FileUserData` snapshot cache keyed on `text.hashCode()` — stale on reparse, wrong on collision, re-hashes per access |
| §2.5.2 | No `ResolveCache` in `LuaNameReference.multiResolve` — full bindings iteration per unresolved name per pass; replace `FileUserData` with `CachedValuesManager` |
| §2.5.3 | Copy-paste drift: scope walk ×3, require extraction ×2, module-file resolution ×2 (the divergence *was* P1 #3), luarocks command-building ×5, run-config boilerplate, assignability-inspection twins |
| §2.5.4 | Method-chain inlay provider still probes stub indexes manually — migrate to `resolveMember` per the documented idiom |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-30-01 | Declaration-only index | M | Full | Restrict file-bindings indexing to file-scope declarations (#20) — index-version bump + rebuild |
| MAINT-30-02 | Platform caching | M | Full | `CachedValuesManager` replaces `FileUserData` (#21); `ResolveCache` in `multiResolve` (§2.5.2) |
| MAINT-30-03 | Single canonical helpers | S | Full | One scope-walk, one require-extractor, one module-resolver, one `LuaRocksEnvironment.command()` (§2.5.3) |
| MAINT-30-04 | Idiom migration | C | Full | Method-chain provider → `resolveType`/`resolveMember` (§2.5.4) |

**Blast radius:** resolution touches everything — full-suite gate mandatory; watch the two
external-fixture tests (`LuaRecursiveReferenceTest`, `LuaDescriptionIndexTest`) that exercise
cross-file resolution (builder-only — CI skips them; gate on `gce-builder run test`).

## Test Cases

Each `Must` requirement has ≥1 concrete input→output case (design section in parens).

| TC | Requirement | Input | Action | Expected Output |
| :--- | :--- | :--- | :--- | :--- |
| TC-01 | MAINT-30-01 | `c.lua`: `function bar() end`; `b.lua`: `print(bar)` (a usage); `d.lua`: `bar()` | Resolve `bar` from `d.lua` (§3.1) | Resolves to `c.lua` declaration only; `b.lua`'s usage leaf is **not** a candidate (before fix it was) |
| TC-02 | MAINT-30-03 | `local x = <caret>x` (RHS `x` in a self-referential initializer) | Resolve the RHS `x` reference via `multiResolve`/Go-to-Declaration (§3.3) | RHS `x` does **not** resolve to the enclosing `local x` declaration (Lua scope: the local is not yet in scope on its own RHS); resolves elsewhere or to `null`. Locks the scope-walk correction (before the §2.1 collapse the inline walk wrongly resolved it to the local) |
| TC-07 | MAINT-30-01 | `t.f = 1` at file top level | Read the FileBindings record for the file (§3.1) | Record contains **no** binding named `t` or `f` (dotted assignment owned by member-field index) |
| TC-03 | MAINT-30-02 | `local x = 1; print(x)` — reference the `x` in `print(x)` | `configureByText` → resolve the `x` reference once and record `LuaNameReference.RESOLVE_INVOCATIONS.get()` (call it `n`) → resolve the **same** reference again with **no** PSI edit → assert the counter delta for the second resolve is `0` (`RESOLVE_INVOCATIONS.get() == n`, i.e. served from `ResolveCache`); then perform a PSI edit (e.g. `myFixture.type(...)`) and resolve again → assert `RESOLVE_INVOCATIONS.get() > n` (the cache was dropped and the body recomputed) (§2.2) | Second (no-change) resolve does **not** re-enter `doMultiResolve` (counter unchanged); post-edit resolve **does** re-enter (counter increments). Seam: `@VisibleForTesting internal val RESOLVE_INVOCATIONS: AtomicInteger` on `LuaNameReference.Companion`, incremented as the first statement of `doMultiResolve` |
| TC-04 | MAINT-30-02 | `local x = KEYS[1]` under REDIS target | Read `forFile` snapshot, switch target to plain Lua (no text edit), read again (§3.4) | Snapshot recomputes; `x` type differs across targets (KEYS unseeded) |
| TC-05 | MAINT-30-02 | `local a = 1` then edit to `local a = "s"` (same length) | Read `forFile` before and after the edit (§3.4) | Second read reflects the new type (no stale-hash serve) |
| TC-06 | MAINT-30-02 | A file whose `visitFuncCall` re-enters `forFile` during a build | Build the snapshot (§3.4, §6) | Completes without recursion; `inProgressSnapshot` short-circuits before `CachedValuesManager` |
| TC-08 | MAINT-30-03 | `require("m")` where `m.lua` exists on disk but not yet in VFS | Resolve the module via `resolveModuleCandidates` (§3.6) | Resolution does **not** trigger a synchronous VFS refresh (refresh flag `false`) |
| TC-09 | MAINT-30-04 | Existing multi-line method-chain fixtures | Run the inlay-hint provider (§2.7) | Hint text identical to the pre-migration stub-index path (or DR-03 fallback documented) |

