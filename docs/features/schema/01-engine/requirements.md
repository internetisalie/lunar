---
id: "SCHEMA-01"
title: "01: Lua JSON-Schema Engine"
type: "feature"
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: "SCHEMA"
folders:
  - "[[features/schema/requirements|requirements]]"
---

# SCHEMA-01: Lua JSON-Schema Engine

## Overview

Adapt the IntelliJ Platform JSON Schema engine to the Lua PSI so that a Lua *data* file claimed by a
registered schema provider receives JSON-Schema validation, completion, and documentation — without
Lunar implementing any schema interpreter. The engine is reached through a `JsonLikePsiWalker` plus
value/property adapters; YAML (a non-JSON language) is the working precedent
(`YamlJsonPsiWalker` / `YamlJsonLikePsiWalkerFactory` / `YamlJsonEnabler` in the local
`intellij-community` reference). SCHEMA-01 delivers the Lua equivalent; SCHEMA-02..04 are the
declarative providers that map specific files to schemas.

## Scope

### In Scope
- A runtime dependency on the bundled JSON plugin (`com.intellij.modules.json`).
- `LuaJsonLikePsiWalker` implementing `com.jetbrains.jsonSchema.extension.JsonLikePsiWalker` for Lua.
- Lua adapters implementing `JsonValueAdapter` / `JsonObjectValueAdapter` / `JsonArrayValueAdapter` /
  `JsonPropertyAdapter`.
- Both Lua-data **document shapes**: (A) bare top-level globals (`package = …`, the file chunk is the
  root object — rockspec/luacheckrc); (B) a single `return { … }` (the returned table is the root —
  busted).
- `LuaJsonLikePsiWalkerFactory` + `LuaJsonSchemaEnabler` registration so the engine engages on Lua
  data files **only when a provider claims them** (plain `.lua` is never engaged).
- A `language="Lua"` schema-compliance inspection that drives the engine's checker (mirrors YAML's own
  `YamlJsonSchemaHighlightingInspection`).
- A reusable base `JsonSchemaFileProvider`/`JsonSchemaProviderFactory` seam that SCHEMA-02..04 extend.

### Out of Scope
- Authoring any specific schema or file mapping (SCHEMA-02 rockspec, SCHEMA-03 luacheckrc,
  SCHEMA-04 busted own those).
- Modelling Lua tables with **non-literal / computed keys** (`[expr] = v` where `expr` is not a string
  literal) as schema properties — such fields are skipped (risks-and-gaps Gap 2.1).
- Validating arbitrary `.lua` source, or any file no provider maps.
- Schema-driven *syntax colouring* (the engine yields validation/completion/docs, not custom
  `TextAttributes`).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| SCHEMA-01-01 | **JSON plugin dependency** | M | Full | Add `com.intellij.modules.json` to `platformBundledPlugins` (`gradle.properties`) and `<depends>com.intellij.modules.json</depends>` (`plugin.xml`) so the JSON Schema engine + extension points are available. |
| SCHEMA-01-02 | **Lua walker** | M | Full | `LuaJsonLikePsiWalker : JsonLikePsiWalker` maps Lua data PSI to the engine's JSON-like model: identifiers/field keys → names, `LuaTableConstructor` → object/array, literals → scalars; implements position/parent/value-adapter lookups. |
| SCHEMA-01-03 | **Lua adapters** | M | Full | `LuaObjectAdapter` (`JsonObjectValueAdapter`), `LuaArrayAdapter` (`JsonArrayValueAdapter`), `LuaPropertyAdapter` (`JsonPropertyAdapter`), `LuaValueAdapter` (`JsonValueAdapter`) wrap Lua PSI into the adapter contract. |
| SCHEMA-01-04 | **Two document shapes** | M | Full | `getRoots(file)` / root-object adapter handles shape A (file chunk of top-level `LuaAssignmentStatement`s as the root object) and shape B (the `LuaTableConstructor` returned by a `LuaFinalStatement`). |
| SCHEMA-01-05 | **Object vs array mapping** | M | Full | A `LuaTableConstructor` is an **object** if any `LuaField` has a non-null identifier (or a string `[ "k" ] =` key); an **array** if its fields are positional (null identifier, single expr). Mixed tables are treated as objects; positional entries are ignored for object property checks. |
| SCHEMA-01-06 | **Walker factory + enabler** | M | Full | `LuaJsonLikePsiWalkerFactory` (`handles` = element in a Lua file) returns the walker; `LuaJsonSchemaEnabler.isEnabledForFile` gates the engine to Lua files that a registered provider can claim. Registered via `<jsonLikePsiWalkerFactory>` / `<jsonSchemaEnabler>` (ns `com.intellij.json`). |
| SCHEMA-01-07 | **Compliance inspection** | M | Full | `LuaJsonSchemaComplianceInspection` (`language="Lua"`) obtains the walker via `JsonLikePsiWalker.getWalker(...)` and runs `JsonSchemaComplianceChecker`, mirroring `YamlJsonSchemaHighlightingInspection`. Completion + docs are supplied by the engine through the registered walker (no extra Lua code). |
| SCHEMA-01-08 | **Provider seam + plain-`.lua` safety** | M | Full | A base `LuaSchemaFileProvider`/`LuaSchemaProviderFactory` (registered `<JsonSchema.ProviderFactory>`) that SCHEMA-02..04 subclass to map a file → bundled schema. With no matching provider, schema resolution yields nothing and no engine feature engages, so ordinary `.lua` editing is unaffected. |

## Detailed Specifications
All mechanics — the per-method walker mapping, the two-shape `getRoots` logic, the JSON-pointer
position algorithm, and the exact `plugin.xml` / `gradle.properties` edits — are in
[design.md](design.md) §2–§7. Genuinely uncertain platform behaviours (JSON-pointer construction for
bare-globals; completion insertion of `key = `) are de-risking spikes in
[risks-and-gaps.md](risks-and-gaps.md), not open questions.

## Behavior Rules
1. **Provider-claimed only**: the engine engages a Lua file only if a registered Lua schema provider
   returns a schema for it; otherwise it is inert (SCHEMA-01-08).
2. **Plain `.lua` untouched**: no validation, completion, or docs change on a Lua source file with no
   schema mapping.
3. **Two shapes, one model**: shapes A and B both present a root **object** to the engine; downstream
   schema checks are identical.
4. **Literal keys only**: only `IDENTIFIER = v` and `["string"] = v` fields are surfaced as object
   properties; computed/non-string keys are skipped.
5. **No EDT blocking**: the walker/adapters do PSI reads only (engine runs inside platform read
   actions); schema files are read by the platform's cached `JsonSchemaService`.

## Test Cases

A **harness schema** `test-config.schema.json` (`required: [name]`, `properties: {name:string,
opts:{type:object, properties:{level:{type:string, enum:[low,high]}}}, tags:{type:array, items:string}}`,
`additionalProperties:false`) is mapped via a **TEST-ONLY** provider to files named `*.testcfg`
(registered as a Lua extension in the test fixture) for shape A and to `*.testret` for shape B.

| # | Requirement | Given | When | Then |
|---|-------------|-------|------|------|
| 1 | 01-04(A),05,07 | `name = "x"; bogus = 1` in a `*.testcfg` | highlight | WARNING on `bogus` (additionalProperties:false), none on `name` |
| 2 | 01-04(A),07 | `opts = { level = "mid" }` (enum low/high) | highlight | WARNING on `"mid"` (not in enum) — proves engine enum check, not hand-rolled |
| 3 | 01-04(A),07 | `tags = { "a", "b" }` (array of strings) | highlight | no warning (positional table validated as array<string>) |
| 4 | 01-05 | `tags = "a"` (string where array expected) | highlight | WARNING type mismatch on `"a"` |
| 5 | 01-04(B) | `return { name = "x", bogus = 1 }` in a `*.testret` | highlight | WARNING on `bogus`; root resolved from the returned table |
| 6 | 01-06,08 | a plain `*.lua` file with `name = 1; bogus = 2` (no provider) | highlight | **no** schema warnings (engine not engaged) |
| 7 | 01-07(completion) | caret at `<caret>` top level of a `*.testcfg` | completeBasic | lookup contains `name`, `opts`, `tags` (engine-supplied via walker) |
| 8 | 01-07(docs) | Quick-Doc on `opts` key | quick-doc | shows the schema `description` for `opts` |

## Acceptance Criteria
- [x] An enum/array/required check the depth-1 hand-rolled checker could NOT do now works via the
      engine (TC #2, #3) — confirming real JSON-Schema semantics.
- [x] Both document shapes validate against the same schema (TC #1 vs #5).
- [x] A plain unmapped `.lua` file is completely unaffected (TC #6).
- [x] Completion and hover docs flow from the engine through the walker (TC #7, #8).

## Non-Functional Requirements
- The walker/adapters retain no hard refs to `Project`/`PsiFile`/`VirtualFile`; adapters hold their
  delegate PSI element only for the duration of a check.
- Enabling the engine on Lua files must not measurably slow plain `.lua` editing (gate via the enabler
  + provider `isAvailable`; risks-and-gaps Risk 1.2).

## Dependencies
- Bundled JSON plugin `com.intellij.modules.json` (SCHEMA-01-01).
- Platform symbols (verified in `~/Documents/src/lua/intellij-community`): `JsonLikePsiWalker`,
  `JsonLikePsiWalkerFactory`, `JsonValueAdapter`/`JsonObjectValueAdapter`/`JsonArrayValueAdapter`/
  `JsonPropertyAdapter`, `JsonSchemaEnabler`, `JsonSchemaProviderFactory`/`JsonSchemaFileProvider`,
  `JsonSchemaComplianceChecker`. See design.md §1.
- Lua PSI: `LuaAssignmentStatement`, `LuaVarList`/`LuaVar`/`LuaNameRef`, `LuaExprList`,
  `LuaTableConstructor`, `LuaFieldList`/`LuaField`, `LuaTerminalExpr`, `LuaFinalStatement`,
  `LuaElementTypes` (all in `src/main/gen/.../lang/psi`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Manual checks: [human-verification-checklists.md](human-verification-checklists.md)
