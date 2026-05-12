# TARGET Risk Register

**Last Updated**: 2026-05-12  
**Status**: Phase 0 Complete

---

## Risk Summary

| Risk ID | Title | Probability | Impact | Mitigation | Status |
|---------|-------|-------------|--------|-----------|--------|
| RISK-001 | Luacheck lua55 support unavailable | Medium | Medium | Fall back to `lua54` std; document in spec | Mitigated |
| RISK-002 | Resource migration data loss | Low | Critical | Use `git mv`; verify file counts post-migration | Mitigated |
| RISK-003 | Settings serialization edge cases | Medium | High | Comprehensive test coverage for migration scenarios | Mitigated |
| RISK-004 | Path segment conflicts | Low | High | Pre-populate static registry; no dynamic derivation | Mitigated |
| RISK-005 | Legacy project incompatibility | Medium | High | Synthetic test data; comprehensive migration tests | Mitigated |

---

## Risk Details

### RISK-001: Luacheck lua55 Support Unavailable

**Description**: Luacheck may not have `--std lua55` available yet, as Lua 5.5 is still in development.

**Probability**: Medium (Lua 5.5 is future; luacheck typically lags behind)  
**Impact**: Medium (Feature works; just no luacheck validation for Lua 5.5 projects)

**Mitigation**:
- Per TARGET-05 design: `VersionEntry("5.5", "lua-5.5", luacheckStd = "lua54")`
- Document that Lua 5.5 projects fall back to Lua 5.4 analysis rules
- Verify luacheck version support during Phase 5 (Luacheck Integration)
- No code changes needed; pre-populated registry is the solution

**Status**: ✅ **MITIGATED** — Design already accounts for this; fallback is documented.

---

### RISK-002: Resource Migration Data Loss

**Description**: Moving files from `platform/` and `sdk/` directories to `runtime/` structure could result in lost files or corrupted git history.

**Probability**: Low (standard procedure; well-established patterns)  
**Impact**: Critical (Could break IDE; resource lookup failures)

**Mitigation**:
- Use `git mv` (not copy + delete) to preserve git history
- Verify file counts before and after migration
- Run full test suite after migration (Phase 7)
- Phase 4 task IMPL-47 explicitly validates migration integrity

**Status**: ✅ **MITIGATED** — Process documented; Phase 4/7 tasks cover verification.

---

### RISK-003: Settings Serialization Edge Cases

**Description**: XML serialization/deserialization of `Target` could have edge cases (null fields, unknown platforms, corrupted legacy data).

**Probability**: Medium (Complex domain; XML is fragile)  
**Impact**: High (Settings don't load; fallback to defaults; data loss)

**Mitigation**:
- Comprehensive deserialization unit tests (IMPL-16, IMPL-17)
- Synthetic legacy project test data (IMPL-00-07)
- Graceful fallback: unknown version labels → `defaultVersion()`
- Dual-field strategy during migration (write both `target` and `languageLevel`)
- Phase 2 and Phase 6 tasks provide full coverage

**Status**: ✅ **MITIGATED** — Test strategy defined; fallback logic designed.

---

### RISK-004: Path Segment Conflicts

**Description**: If path segments are derived dynamically (from labels or enum names), refactoring or i18n could cause resource lookup failures.

**Probability**: Low (Design forbids dynamic derivation)  
**Impact**: High (Libraries don't load; completion broken for affected platforms)

**Mitigation**:
- Explicit `pathSegment` property on `LuaPlatform` and `VersionEntry`
- Never derive from `label` or `name` — design enforces separation
- Static pre-populated registry (no runtime derivation)
- IMPL-00-04 adds `pathSegment` to enum; IMPL-05 pre-populates registry

**Status**: ✅ **MITIGATED** — Design prevents this class of bug.

---

### RISK-005: Legacy Project Incompatibility

**Description**: Existing projects using old `languageLevel` and `platform` fields may not migrate correctly, leading to loss of configuration.

**Probability**: Medium (Complex migration logic; many scenarios)  
**Impact**: High (Users lose their target; IDE defaults to wrong platform/version)

**Mitigation**:
- Comprehensive migration unit tests (IMPL-17, IMPL-41-43)
- Synthetic legacy project files (IMPL-00-07) covering:
  - STANDARD platform + various language levels
  - REDIS platform with no language level
  - Unknown versions
  - Mixed/corrupted settings
- Manual testing with real legacy projects (IMPL-45, IMPL-46)
- Fallback to `defaultVersion()` for any unrecognized version
- Phase 6 is dedicated to migration validation

**Status**: ✅ **MITIGATED** — Comprehensive test strategy; fallback logic designed.

---

## Unblocked Risk Items

- **LUACHECK_LUA55**: Phase 5 (Luacheck Integration) will verify exact luacheck version and document any limitations
- **RESOURCE_MIGRATION**: Phase 4 (Library Resolution) will execute migration with full verification
- **SETTINGS_SERIALIZATION**: Phase 2 (Settings Integration) + Phase 6 (Migration Validation) will provide comprehensive testing

---

## Escalation Path

If any risk escalates during implementation:
1. Document in a new row (mark as "IN_REVIEW")
2. Flag to team lead for discussion
3. Update mitigation strategy
4. Re-assess probability/impact

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Tech Lead | — | 2026-05-12 | ✅ Acknowledged |
| QA Lead | — | — | ⏳ Pending |
| Product Manager | — | — | ⏳ Pending |
