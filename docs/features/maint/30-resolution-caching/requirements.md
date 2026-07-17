---
id: "MAINT-30"
title: "30: Indexing & Resolution Caching"
type: "feature"
parent_id: "MAINT"
status: "todo"
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
| MAINT-30-01 | Declaration-only index | M | Not Implemented | Restrict file-bindings indexing to file-scope declarations (#20) — index-version bump + rebuild |
| MAINT-30-02 | Platform caching | M | Not Implemented | `CachedValuesManager` replaces `FileUserData` (#21); `ResolveCache` in `multiResolve` (§2.5.2) |
| MAINT-30-03 | Single canonical helpers | S | Not Implemented | One scope-walk, one require-extractor, one module-resolver, one `LuaRocksEnvironment.command()` (§2.5.3) |
| MAINT-30-04 | Idiom migration | C | Not Implemented | Method-chain provider → `resolveType`/`resolveMember` (§2.5.4) |

**Blast radius:** resolution touches everything — full-suite gate mandatory; watch the two
external-fixture tests (`LuaRecursiveReferenceTest`) that exercise cross-file resolution.
