---
id: TARGET-06
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-06: Target Migration"
status: "done"
---

# TARGET-06: Target Migration

**Requirement**: Existing project settings must migrate to the new Target data model without user action or data loss.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md](design.md)

---

## Overview

Before TARGET, `LuaProjectSettings.State` stores `languageLevel: LuaLanguageLevel` and `platform: LuaPlatform` as separate fields. After TARGET, `target: Target?` is the single source of truth. Opening an existing project must silently convert the old fields to a `Target` and persist the result.

---

## Acceptance Criteria

- [ ] `migrateFromLegacySettings()` is called when `state.target` is null
- [ ] Migration correctly handles all `LuaLanguageLevel` values including `LUA50`
- [ ] Non-STANDARD legacy platforms migrate to the platform's default version
- [ ] STANDARD legacy platform migrates using `languageLevel` to version label mapping
- [ ] After migration, `state.target` is populated and `state.languageLevel` is in sync
- [ ] XML serialization writes both `target` and `languageLevel` fields
- [ ] XML deserialization reads `target`; falls back to migration if absent
- [ ] Unknown version label on deserialization uses `defaultVersion()`, not an exception
- [ ] Unit tests cover all rows in the Migration Scenarios table defined in Design.
