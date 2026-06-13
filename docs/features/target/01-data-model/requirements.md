---
id: TARGET-01
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-01: Target Data Model"
status: "done"
---

# TARGET-01: Target Data Model

**Requirement**: Define a `Target` configuration combining a `LuaPlatform` and a platform-specific `VersionEntry`.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md](design.md)

---

## Overview

A `Target` is the single source of truth for a project's runtime environment. It encodes a platform (e.g., Redis) and version (e.g., "7+") as an immutable value and provides deterministic derivation of the implicit language level and library root path. No component may independently track language level or library path without going through `Target`.

---

## Acceptance Criteria

- [ ] `LuaPlatform` enum has `pathSegment` property; `LUAJIT` and `NGX` entries exist
- [ ] `VersionEntry` data class exists with `label`, `pathSegment`, `luacheckStd`
- [ ] `Target` data class exists with `getImplicitLanguageLevel()`, `getLibraryRootPath()`, `getLuacheckStd()`
- [ ] `PlatformVersionRegistry` is the sole place `VersionEntry` instances are constructed
- [ ] `LuaProjectSettings.State` has `target: Target?` field; `platform` field removed
- [ ] `getTarget()` / `setTarget()` accessor methods exist on `LuaProjectSettings`
- [ ] Serialization round-trips correctly for all registered platforms and versions
- [ ] Unknown version label on deserialization falls back to `defaultVersion()` without exception
