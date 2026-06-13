---
id: "INSP-01-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "INSP-01"
status: "planned"
priority: "medium"
folders:
  - "[[features/inspections/01-undeclared-variable/requirements|requirements]]"
---

# Risks & Design Gaps: INSP-01 Undeclared Variable

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `INSP-R-01` | **Resolution cost** | Medium | `resolve()` runs once per read in the platform's read action; results are platform-cached. Inspection adds only cheap classification. Profile on a large file before optimizing. |
| `INSP-R-02` | **Warning fatigue** | Medium | Default level is `WARNING` (re-mappable); the Additional Globals allowlist (INSP-01-07) and `suppressUnderscorePrefixedGlobals` reduce noise for host-injected globals. |
| `INSP-R-03` | **Plain-global indexing gap** | Medium | `LuaGlobalDeclarationIndex` indexes `LuaFuncDecl` (function-style globals). Plain table-assigned globals (`X = 1`) in *other* files may not resolve cross-file, risking false positives. Mitigated by the allowlist; tracked as `INSP-DR-03`. |
| `INSP-R-04` | **Standard-globals drift** | Low | Hard-coded sets in §3.3 must track Lua releases. Bounded list; covered by per-level tests (TC-04). |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `INSP-G-01` | **Luacheck scope fidelity** | §4.2 supports only own-line + next-line `ignore`; full `push`/`pop` block scoping is deferred. | `INSP-DR-02` |
| `INSP-G-02` | **Stdlib coverage source** | The hard-coded floor must match what `PlatformLibraryIndex` bundles to avoid divergence. | `INSP-DR-01` |
| `INSP-G-03` | **Cross-file plain globals** | Whether to extend resolution to plain global assignments across files. | `INSP-DR-03` |

## De-risking Tasks (DR)

- [ ] `INSP-DR-01`: Confirm the platform-library bundle (`global.lua`/`builtin.lua`) defines
      the same built-in names as `LuaStandardGlobals.§3.3`; add a test asserting parity for
      one level. Owner: implementer, before Phase 2 sign-off.
- [ ] `INSP-DR-02`: Spike `-- luacheck: push ignore … pop` block scoping; if cheap, extend
      §4.2; else document as unsupported. Owner: implementer, Phase 3.
- [ ] `INSP-DR-03`: Decide whether plain cross-file globals (`X = 1`) should resolve; if yes,
      extend `LuaGlobalDeclarationIndex` (separate feature). Owner: triage after Phase 1.
