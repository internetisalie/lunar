---
id: TARGET-04
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-04: Library Root Resolution"
status: "done"
---

# TARGET-04: Library Root Resolution

**Requirement**: Library definitions must be resolved from a unified `runtime/` resource directory tree, derived from the project's `Target` with no string transformation.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md](design.md)

---

## Overview

Platform-specific Lua library definitions (function signatures, type stubs) are bundled as resources and loaded by the IDE to power code completion, inspections, and hover documentation. Currently, the `platform/LuaXX/` layout is hardcoded in `PlatformLibraryProvider`. After TARGET, a unified `runtime/` tree is introduced and a single `RuntimeLibraryProvider` resolves all libraries from `target.getLibraryRootPath()`.

---

## Acceptance Criteria

- [ ] All resources in `platform/LuaXX/` migrated to `runtime/standard/lua-X.X/`
- [ ] All resources in `sdk/redis-*/` migrated to `runtime/redis/redis-*/` (including `redis-8` → `redis-7` rename)
- [ ] Old `platform/` and `sdk/` directories removed from resources
- [ ] `RuntimeLibraryProvider` resolves the correct resource directory from `target.getLibraryRootPath()`
- [ ] `RuntimeLibraryProvider` returns `null`/empty list without error for platforms with no bundled files
- [ ] `PlatformLibraryProvider` updated to use `RuntimeLibraryProvider`
- [ ] Code completion continues to work after migration for STANDARD and REDIS targets
- [ ] Changing the target in settings fires a settings change event and refreshes the library index
