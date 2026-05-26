---
id: TARGET-05
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-05: Luacheck Integration"
status: not_implemented
---

# TARGET-05: Luacheck Integration

**Requirement**: The Luacheck static analyser must use the correct `--std` value for the active project Target. The std value is declared on the `VersionEntry` and requires no string transformation.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md](design.md)

---

## Overview

Luacheck's `--std` flag controls which global symbols and built-in functions are recognised for a given Lua environment. If `--std` is wrong for the project's platform, luacheck emits false-positive "undefined global" errors (e.g., `redis.call` flagged in a Redis project) or misses real errors (e.g., `bit` used in a non-LuaJIT project). After TARGET, `--std` is always taken directly from `target.getLuacheckStd()` — there is no mapping logic at the call site.

---

## Acceptance Criteria

- [ ] `Target.getLuacheckStd()` returns the correct value for every row in the mapping table defined in Design.
- [ ] Luacheck invocations pass `--std <value>` when `getLuacheckStd()` is non-null
- [ ] Luacheck invocations omit `--std` entirely when `getLuacheckStd()` is null
- [ ] Changing the Target clears cached luacheck results for open files
- [ ] No string transformation is performed between `VersionEntry.luacheckStd` and the `--std` argument
- [ ] Unit tests verify `getLuacheckStd()` output for all registered targets
