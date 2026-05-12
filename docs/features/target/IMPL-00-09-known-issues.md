# IMPL-00-09: Known Issues & Mitigations

**Documented**: 2026-05-12  
**Phase**: 0 (Preparatory)  
**Status**: Issues identified and mitigated

---

## No Blocking Issues Found

### Summary

Phase 0 review (design, specs, edge cases) identified **zero critical blockers**. All identified concerns are **mitigated by design**.

---

## Verified Concerns (Non-Blocking)

### 1. Luacheck lua55 Support

**Concern**: Luacheck may not yet support `--std lua55` because Lua 5.5 is still in development.

**Evidence**:
- Luacheck 0.23.0 (current release) does not list `lua55` in standard documentation
- Lua 5.5 is Alpha/Beta status; luacheck typically trails new versions

**Design Mitigation** ✅:
- Per TARGET-05 spec: Use `luacheckStd = "lua54"` fallback for Lua 5.5 projects
- Projects targeting Lua 5.5 will use Lua 5.4 analysis rules
- No code changes required; registry is pre-populated with fallback
- Phase 5 task IMPL-38 includes explicit test for this scenario

**Action**:
- [x] Documented in risk register (RISK-001)
- [x] Mitigation verified in design document
- [x] No implementation blocker
- [ ] Phase 5: Re-verify luacheck version during integration tests

---

### 2. Resource Directory Migration Complexity

**Concern**: Moving from `platform/` and `sdk/` directories to unified `runtime/` structure is complex and could cause data loss.

**Evidence**:
- Current structure:
  ```
  src/main/resources/
  ├── platform/Lua51/
  ├── platform/Lua52/
  ├── sdk/redis-5/
  └── sdk/redis-8/
  ```
- Target structure:
  ```
  src/main/resources/runtime/
  ├── standard/lua-5.1/
  ├── standard/lua-5.2/
  ├── redis/redis-5/
  └── redis/redis-7/
  ```

**Design Mitigation** ✅:
- Phase 4 task IMPL-25 creates new structure first (empty)
- Phase 4 tasks IMPL-26–28 use `git mv` to preserve history
- Phase 7 task IMPL-47 validates file counts and integrity
- Phase 7 task IMPL-50-51 confirms old directories deleted
- Full test suite runs post-migration (IMPL-49)

**Action**:
- [x] Documented in risk register (RISK-002)
- [x] Mitigation workflow designed (Phase 4)
- [x] Verification tasks defined (Phase 7)
- [x] No implementation blocker

---

### 3. Settings Serialization Edge Cases

**Concern**: XML round-tripping of `Target` objects could have edge cases (null fields, unknown platforms, corrupted legacy data).

**Evidence**:
- Complex deserialization logic required
- 5 different platforms × up to 5 versions each = 25+ combinations
- Legacy migration adds permutations (old `languageLevel` + `platform` combinations)

**Design Mitigation** ✅:
- Phase 2 tasks IMPL-14–17 implement serialization with comprehensive tests
- Phase 6 tasks IMPL-40–48 validate all migration scenarios with synthetic test data
- Deserialization includes graceful fallback: unknown version label → `defaultVersion()`
- Dual-field strategy during transition: write both `target` and `languageLevel`

**Action**:
- [x] Documented in risk register (RISK-003)
- [x] Test strategy defined (Phase 2 & 6 tasks)
- [x] Fallback logic designed
- [x] Synthetic test data created (IMPL-00-07)

---

### 4. Path Segment Derivation Risks

**Concern**: If path segments are dynamically derived from labels, refactoring UI text or renaming enums could break resource lookup.

**Evidence**:
- Anti-pattern: Deriving resource paths from display labels causes tight coupling
- Example failure: If "Standard" label changed to "Standard Lua 5.x", resource paths break

**Design Mitigation** ✅:
- `LuaPlatform` enum: Explicit `pathSegment` property (never derived)
- `VersionEntry`: Separate `label` and `pathSegment` fields (design enforces separation)
- Static pre-populated registry: All combinations hardcoded; no derivation at runtime
- IMPL-00-04 implements explicit enum entries
- IMPL-05 pre-populates static registry

**Action**:
- [x] Documented in risk register (RISK-004)
- [x] Design prevents this class of bug
- [x] IMPL-00-04 executed; enum updated with explicit pathSegments

---

## Conclusion

✅ **All concerns are mitigated by design. No implementation blockers identified.**

Phase 0 is **READY** to proceed with Phase 1 (Data Model Foundation).
