---
id: "ROCKS-13-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "planned"
parent_id: "ROCKS-13"
folders:
  - "[[features/rocks/13-rockspec-editor-support/requirements|requirements]]"
---

# ROCKS-13: Risks & Gaps

## Critical Risks

### Risk 1.1: Extension points fire on all Lua files
- **Impact**: `language="Lua"` inspections/completion/doc providers run on every `.lua` file, not just
  `.rockspec`. Without a guard, ROCKS-13 would flag ordinary globals (`local x; y = 1`) as "unknown
  rockspec fields" and pollute normal Lua completion.
- **Likelihood**: certain if unguarded.
- **Mitigation**: every component's first line is `RockspecFileSupport.isRockspec(file)` returning
  `EMPTY_VISITOR` / no completions / no targets otherwise (design §2.3, §2.4-§2.6). TC #1 is the
  regression guard; verify-in-ide Phase 7 confirms a plain `.lua` file stays clean.

### Risk 1.2: False positives from a too-eager validator
- **Impact**: rockspecs legitimately compute values (`version = ver .. "-1"`, `source = source_for(...)`,
  per-platform override tables); flagging these as kind mismatches or unknown keys would train users to
  disable the inspection.
- **Likelihood**: medium.
- **Mitigation**: conservative kind check — only literal/table RHS is judged; `UNKNOWN` kind is never
  flagged (design §3.3, Behavior Rule 2; TC #7). Unknown-key checks run only on **closed** objects
  (design §3.1; `build` and depth ≥ 2 are open, TC #4).

### Risk 1.3: Schema resource missing / parse failure at runtime
- **Impact**: a packaging slip (schema not in jar) or malformed JSON would throw inside an inspection,
  surfacing as a platform error banner on every rockspec.
- **Likelihood**: low.
- **Mitigation**: `RockspecSchemaLoader.load` failure is caught (`runCatching`) and logged once; the
  inspection degrades to `EMPTY_VISITOR` (design §2.2, §4.1 Failure handling). DR-02 asserts the
  resource loads from the built plugin jar.

## Design Gaps

### Gap 2.1: "Closed" semantics for nested objects with no `additionalProperties`
- **Question**: `description`/`source`/`hooks`/`deploy` omit `additionalProperties`, so by strict
  JSON-schema rules they are **open** — yet we want typo detection (`sumary`, TC #3) inside them.
- **Decision**: treat a depth-1 object with non-empty `properties` and no explicit
  `additionalProperties:true` as **closed** (design §4.1 Closed flag). `build`'s explicit
  `additionalProperties:true` keeps it open. This is a deliberate deviation from strict draft-07 for a
  better authoring UX.
- **Resolved by**: decision recorded — **deliberate**. Not a blocker. If a real rockspec uses an
  undocumented nested key the user can suppress the inspection per-line.

### Gap 2.2: Schema constraints the value-kind check cannot express
- **Question**: `version` has a `pattern` (`^[a-zA-Z0-9._]+-[0-9]+$`
  [rockspec-schema-v30.json:26](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json));
  `source.url` is required; `dependencies` entries follow a version-constraint grammar.
- **Decision**: **out of scope** for v1 — only key presence/identity and coarse value kind are checked,
  not value content (requirements Out of Scope). `version` pattern, `source.url` requiredness, and
  dependency grammar are deferred.
- **Resolved by**: decision recorded — **deferred** (Technical Debt). The Musts (key/kind checks) are
  fully specified without them.

### Gap 2.3: `oneOf` / per-platform override sub-tables collapse to ANY
- **Question**: `dependencies` is `oneOf [array, object]` and several nodes nest `platforms` override
  sub-tables; the reduced model collapses these to `SchemaKind.ANY` / depth ≥ 2 open.
- **Decision**: acceptable — a Lua table satisfies ANY, and depth ≥ 2 emits no unknown-key markers, so
  no false positives. Deeper modelling is deferred (design §4.1 oneOf, Depth bound).
- **Resolved by**: decision recorded — **deferred**.

## Technical Debt & Future Work
- **TBD: LuaCATS meta-file indexing route** — register `platform/LuaRocks/rockspec-v3X.lua` as a
  synthetic library (would require a `LuaPlatform.LUAROCKS` enum entry + an `_ENV`-scope hook) to unlock
  Go-to-Class on `Rockspec` and type-engine inference. Larger effort; see design §10 (Alternatives).
- **TBD: value-content validation** — `version` pattern, `source.url` requiredness, dependency grammar
  (Gap 2.2).
- **TBD: deep / per-platform override validation** (Gap 2.3).
- **TBD: Rename / Find-Usages on rockspec keys** (requirements Out of Scope).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-13-00-DR-01 | Confirm the Lua PSI shape of a top-level rockspec assignment and a nested table field in a fixture `.rockspec` (`LuaAssignmentStatement` → `varList.varList[0].nameRef`; `LuaTableConstructor` → `fieldList.fieldList[i].identifier`) matches design §2.3/§3.1. | Risk 1.2, schema model | todo |
| ROCKS-13-00-DR-02 | Load `/jsonschema/rockspec-schema-v30.json` from the **built** plugin jar (`runIde` or a packaged test) via `getResourceAsStream`; confirm non-null and parses to the expected root required set. | Risk 1.3 | todo |
| ROCKS-13-00-DR-03 | Confirm a `language="Lua"` inspection registered for rockspec actually fires on a `.rockspec` buffer in a fixture test and NOT on a `.lua` buffer (guards Risk 1.1 before building the full checks). | Risk 1.1 | todo |
| ROCKS-13-00-DR-04 | Assert both schemas parse to their **per-version** shapes despite structural divergence: v3.0 uses `definitions` + `$ref` (`source`/`build`/`dependencies`), v3.1 inlines those objects with **no** `definitions`/`$ref`. Confirm `requiredTopLevel` = `{package,version,source,build}` (v3.0) vs `{package,source,version}` (v3.1, `build` absent). | Gap 2.3, design §3.2/§4.1 | todo |

## Test Case Gaps
- A rockspec with per-platform override sub-tables (`build.platforms.unix = {...}`): collapses to ANY/
  open; covered in spirit by TC #4 but no dedicated fixture — add if Gap 2.3 is ever closed.
- `rockspec_format = 3.0` (a **number**, not a string): treated as absent → v3.0; add a guard test.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
