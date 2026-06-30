---
id: "SCHEMA"
title: "SCHEMA: Schema-Driven Data Files"
type: "epic"
status: "planned"
priority: "medium"
folders:
  - "[[features]]"
---

# Schema-Driven Data Files (`SCHEMA`)

Lua is frequently used as a **data/configuration** language: a `.rockspec`, a `.luacheckrc`, a
busted `.busted` config — each is a Lua chunk whose top-level shape is a fixed schema. This epic
gives those files JSON-Schema-driven **validation, completion, and documentation** by adapting the
IntelliJ Platform's existing JSON Schema engine to the **Lua PSI**, rather than hand-rolling a schema
interpreter per file type.

The platform's JSON Schema engine is language-agnostic: it validates through a `JsonLikePsiWalker`
abstraction, and YAML — a non-JSON language — reuses the entire engine purely by implementing that
walker plus value/property adapters (`plugins/yaml/backend/src/schema/YamlJsonPsiWalker.java` in the
local `intellij-community` reference). Lunar does the same for Lua: implement **one** Lua walker +
adapters (SCHEMA-01), and every Lua *data* file with a registered schema mapping gets draft-04…2020
validation, completion, quick-fixes, and hover docs for free.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`SCHEMA-01`](01-engine/requirements.md) | **Lua JSON-Schema Engine** | **M** | **Full** | A Lua `JsonLikePsiWalker` + adapters + walker-factory/enabler that plug Lua data files into the platform JSON Schema engine; handles the two Lua-data document shapes (bare top-level globals; `return {table}`). |
| [`SCHEMA-02`](02-rockspec-provider/requirements.md) | **Rockspec Schema Provider** | **S** | **Full** | A `JsonSchemaFileProvider` mapping `.rockspec` → the bundled rockspec v3.0/v3.1 schema; VNC-verified live (fixed a SCHEMA-01 EP-namespace bug — see [risks-and-gaps](02-rockspec-provider/risks-and-gaps.md)). **Supersedes the standalone [ROCKS-13](../rocks/13-rockspec-editor-support/requirements.md) design.** |
| [`SCHEMA-03`](03-luacheckrc-provider/requirements.md) | **Luacheckrc Schema Provider** | **S** | **Full** | A `LuaSchemaFileProvider` mapping `.luacheckrc`/`.luacheckrc.lua` → the bundled `luacheck-config.schema.json`, contributed to the SCHEMA-01 engine's `schemaFileProvider` EP (the second consumer that proves the engine generalises). Unit-verified (TC #1-#5 green) **and VNC-verified live** (V-01..V-04 pass: status bar binds "Schema: Luacheckrc", `globals = true` → "Incompatible types. Required: array. Actual: boolean.", `max_*` key completion with schema docs, `.luacheckrc.lua` parity, and plain `main.lua` shows "No JSON schema"). |
| [`SCHEMA-04`](04-busted-provider/requirements.md) | **Busted Config Schema Provider** | **C** | **Full** | A provider mapping `.busted` (the `return {table}` document shape) → a bundled busted-config schema. Unit-verified (TC #1-#4 green). |

---

## Motivation
Each Lua-data file otherwise needs its own bespoke validator (the approach ROCKS-13 took). That
duplicates schema-interpretation logic (`$ref`, `oneOf`, `patternProperties`, depth recursion) per
file type and drifts from the JSON schemas we already ship. Adapting the platform engine once means:
- **One engine, many consumers** — providers are declarative (a file pattern + a schema file).
- **Full JSON-Schema coverage** — recursion, refs, conditionals, enums, patterns — not the depth-1
  subset a hand-rolled checker can afford.
- **Free downstream features** — completion ranking, quick-fixes, hover docs, and "go to schema"
  all flow through the engine.

## Relationship to ROCKS-13
[ROCKS-13](../rocks/13-rockspec-editor-support/requirements.md) was planned as a **standalone**,
hand-rolled rockspec validator (its design §10 rejected the platform engine on the incomplete premise
that it only validates JSON/YAML PSI — it is in fact walker-extensible). With the engine approach
chosen, ROCKS-13's standalone design is **superseded** by **SCHEMA-02**, which reuses ROCKS-13's
bundled schemas, `rockspec_format` version selection, and test cases as a thin provider on top of
SCHEMA-01. ROCKS-13 is reset to `todo` and retained for context; its rockspec-domain knowledge moves
into SCHEMA-02.

## Scope boundaries (epic-level)
- **In**: Lua *data* files explicitly claimed by a registered schema provider.
- **Out**: arbitrary `.lua` source (not data; never engaged); TOML configs such as StyLua's
  `.stylua.toml` (handled by the platform's TOML walker + a SchemaStore mapping, unrelated to Lua
  PSI); `.luarc.json` (already JSON, handled natively).

## See Also
- Engine design: [01-engine/design.md](01-engine/design.md)
- Epic risks: [01-engine/risks-and-gaps.md](01-engine/risks-and-gaps.md)
- Superseded standalone: [ROCKS-13](../rocks/13-rockspec-editor-support/requirements.md)
