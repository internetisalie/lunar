---
id: TARGET-03
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-03: UI Contextual Versions"
status: "done"
vf_icon: ✅
---

# TARGET-03: UI Contextual Versions

**Requirement**: The Project Settings panel must dynamically update available version options based on the selected Platform. Language level must be read-only, derived from the current Target.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md](design.md)

---

## Overview

The current settings panel has two independent dropdowns: `Platform` and `Language Level`. After TARGET, these are replaced with `Platform` + `Version` dropdowns, where the version list is always driven by the platform selection. Language level becomes a read-only derived label, never directly selectable.

---

## Acceptance Criteria

- [ ] Version combo box repopulates immediately when platform selection changes
- [ ] Version list is always consistent with the selected platform (no stale entries)
- [ ] Default version selected after platform change is the last (most recent) entry
- [ ] Language level label updates immediately when either combo changes
- [ ] Language level label is read-only (not editable by user)
- [ ] `apply()` saves the currently displayed `(platform, version)` pair as the project Target
- [ ] `reset()` restores both platform and version combos to the saved state
- [ ] `isModified()` returns true only when the displayed selection differs from saved state
