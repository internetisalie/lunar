---
id: TARGET-02
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-02: Implicit Language Level"
status: not_implemented
---

# TARGET-02: Implicit Language Level

**Requirement**: Selecting a Target must automatically derive the `LuaLanguageLevel` (e.g., Redis implies Lua 5.1). The language level is not independently user-selectable when a platform is active.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md](design.md)

---

## Overview

`LuaLanguageLevel` controls which parser grammar and built-in symbol definitions apply to a project. Before TARGET, language level was a free user choice. After TARGET, it is **derived** from the `Target` — the user selects a platform and version, and the language level follows deterministically. The user cannot set it independently.

---

## Acceptance Criteria

- [ ] `Target.getImplicitLanguageLevel()` returns the correct level for every entry in `PlatformVersionRegistry`
- [ ] `LuaLanguageLevel` is not user-selectable when a Target is active
- [ ] `state.languageLevel` is always updated on `setTarget()`
- [ ] `STANDARD / "5.5"` returns `LUA54` until `LUA55` is implemented
- [ ] Unit tests cover every row in the platform-to-language-level table defined in Design.
