---
id: "ROCKS-13"
title: "13: Rockspec Editor Support"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "ROCKS"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-13: Rockspec Editor Support

> **⚠️ Superseded.** This standalone, hand-rolled design is **replaced by the [SCHEMA epic](../../schema/requirements.md)**:
> the [SCHEMA-01 engine](../../schema/01-engine/requirements.md) adapts the platform JSON-Schema engine
> to Lua, and [SCHEMA-02](../../schema/02-rockspec-provider/requirements.md) delivers rockspec support
> as a thin provider that **reuses this doc's bundled schemas, `rockspec_format` selection, and test
> cases**. Reset to `todo` and retained for context. The §10 premise that the platform engine "only
> validates JSON/YAML PSI" was incomplete — it is walker-extensible (YAML proves it).

## Overview

`.rockspec` files are Lua source whose top-level globals (`package`, `version`, `source`,
`build`, `dependencies`, …) form a fixed schema. As of the file-type registration in
[plugin.xml:55-60](../../../../src/main/resources/META-INF/plugin.xml) (`extensions="lua;rockspec"`)
they are already lexed/parsed and **syntax-highlighted** as Lua. ROCKS-13 adds **schema-aware code
insight** on top of that: it validates a rockspec's top-level (and one level of nested) keys against
the bundled rockspec schema, completes those keys, and shows their documentation on hover.

This is the editor support that ROCKS-05 explicitly placed out of scope
([05-module-resolution/requirements.md:66](../05-module-resolution/requirements.md) — *"Rockspec
editing or code insight within `.rockspec` files themselves"*).

### Approach: standalone, schema-driven (not the type engine)

Validation is driven by the **bundled JSON schema** assets
([rockspec-schema-v30.json](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json),
[rockspec-schema-v31.json](../../../../src/main/resources/jsonschema/rockspec-schema-v31.json)),
loaded into a small reduced Kotlin model and applied directly to the Lua PSI of the open file. This
deliberately does **not** route through Lunar's type engine: typing a `.rockspec` file's globals via
`@class Rockspec` would require a file-type-scoped implicit-`_ENV` override extension point that does
not exist today (no hook on `LuaScopeProcessor` / `LuaFile.processDeclarations`
[LuaFile.kt:41-76](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaFile.kt)). The
schema-driven path reuses the proven inspection / completion / documentation extension points and
keeps the schema as a single source of truth in the resource files. See design.md §10 (Alternatives).

## Scope

### In Scope
- A file guard so every ROCKS-13 component acts **only** on `.rockspec` files (the `language="Lua"`
  extension points fire on all Lua files; regular `.lua` editing is unaffected).
- Loading the bundled JSON schema (v3.0 / v3.1) into a reduced `RockspecSchema` model, version-selected
  by the file's `rockspec_format`, cached for the plugin lifetime.
- A `LocalInspectionTool` reporting: unknown keys (closed objects only), missing **required** top-level
  keys, and wrong **value kind** (string vs table vs array vs boolean) for known keys.
- One level of nested validation for top-level objects whose schema is **closed**
  (`additionalProperties:false`): `description`, `source`, `hooks`, `deploy`.
- A `CompletionContributor` offering schema keys at the top level and inside known nested tables.
- Hover documentation for a schema key (from the schema node's `description`).
- A quick fix to insert a missing required field stub.

### Out of Scope
- Type-engine `_ENV` mapping / `@class Rockspec` inference for rockspec globals (design.md §10).
- Validating values **inside** `build` (schema is `additionalProperties:true`
  [rockspec-schema-v30.json:315](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json)) —
  only `build` itself and its required `type` key are checked, not backend-specific module entries.
- Deep / recursive validation below the first nested object level (e.g. per-platform override sub-tables,
  `dependencies` version-string grammar, `source.url` reachability).
- Validating semantic constraints the schema cannot express (e.g. `version` `pattern`
  [rockspec-schema-v30.json:26](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json) is
  **not** enforced in v1 — deferred, risks-and-gaps Gap 2.2).
- Reusing the LuaCATS meta-files (`rockspec-v30.lua` / `rockspec-v31.lua`) for indexing / Go-to-Class
  (that is a separate, optional follow-up — risks-and-gaps Technical Debt).
- Rename / Find-Usages on rockspec keys.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-13-01 | **Rockspec file guard** | M | A shared util identifies `.rockspec` files (`PsiFile.virtualFile.extension == "rockspec"`); every inspection/completion/doc component early-returns on non-rockspec Lua files so regular `.lua` editing is untouched. |
| ROCKS-13-02 | **Schema model & loader** | M | Load the bundled JSON schema into a reduced `RockspecSchema` (top-level + nested object nodes: key set, required set, closed flag, value kind, description). Version-selected by `rockspec_format` (ROCKS-13-09); parsed once and cached. |
| ROCKS-13-03 | **Unknown-key validation** | M | Flag a top-level assignment whose key is not in the schema (root is closed: `additionalProperties:false`); likewise unknown keys inside the four **closed** nested objects (`description`/`source`/`hooks`/`deploy`). Severity WARNING. |
| ROCKS-13-04 | **Missing required-field validation** | M | Flag a rockspec missing any **required** top-level key (`package`, `version`, `source`, `build`). The marker is anchored on the file (first PSI child). Severity WARNING. |
| ROCKS-13-05 | **Value-kind validation** | M | For a known key, flag a value whose Lua kind disagrees with the schema (`string`→STRING literal, `object`→table constructor, `array`→table constructor, `boolean`→TRUE/FALSE). Unknown/complex RHS (refs, calls, concat) are **not** flagged (v1 conservative). Severity WARNING. |
| ROCKS-13-06 | **Key completion** | S | At a top-level statement start, complete the schema's top-level keys (each inserted as `key = `); inside a known nested object table, complete that node's keys. Suppress keys already present in the same table. |
| ROCKS-13-07 | **Hover documentation** | S | Hovering / Quick-Doc on a schema key shows the schema node's `description` (and value kind / required flag). |
| ROCKS-13-08 | **Add-required-field quick fix** | S | On a ROCKS-13-04 problem, offer a quick fix that inserts stub assignments for the missing required keys at the top of the file. |
| ROCKS-13-09 | **`rockspec_format` version selection** | S | Choose the v3.1 schema when the file's `rockspec_format` string starts with `"3.1"`; otherwise (absent, `"1.0"`, `"3.0"`, anything else) use the v3.0 schema. |

## Detailed Specifications

### ROCKS-13-01: Rockspec file guard
`extensions="lua;rockspec"` ([plugin.xml:60](../../../../src/main/resources/META-INF/plugin.xml)) maps
both extensions to one `LuaFileType`, so any `language="Lua"` inspection/completion/doc provider fires
on **all** Lua files. A `RockspecFileSupport.isRockspec(file: PsiFile): Boolean` returns
`file.virtualFile?.extension.equals("rockspec", ignoreCase = true)`. Every ROCKS-13 component calls it
first and returns immediately when false. Design.md §2.3.

### ROCKS-13-02: Schema model & loader
The reduced model and the constrained JSON-schema interpreter (the exact subset of draft-07 consumed:
`type`, `properties`, `required`, `additionalProperties` bool, `description`, `$ref` to
`#/definitions/*`, `oneOf`, `items`) are specified in design.md §2.1 and §4.1. Loaded with the
`com.google.gson` already used by `RockspecBridge`
([RockspecBridge.kt](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt));
resource path `/jsonschema/rockspec-schema-v3X.json`. Cached in a singleton object.

### ROCKS-13-03 / 04 / 05: Validation
A single `RockspecSchemaInspection : LocalInspectionTool` (design.md §2.4) visits top-level
`LuaAssignmentStatement`s ([LuaAssignmentStatement.java](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaAssignmentStatement.java))
via `LuaVisitor.visitAssignmentStatement`
([LuaVisitor.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaVisitor.java)),
reads the LHS key from `varList.varList[0].nameRef`
([LuaVar.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaVar.java)) and the RHS
from `exprList.exprList[0]`, and applies the three checks per design.md §3.1–§3.3. Required-key
presence (ROCKS-13-04) is computed once per file over the set of top-level keys seen.

### ROCKS-13-06: Key completion
`RockspecCompletionContributor : CompletionContributor` (design.md §2.5) uses
`PsiTreeUtil.prevVisibleLeaf(position)` (per the completion lesson in AGENTS.md — the dummy
`IntellijIdeaRulezzz` identifier shifts the PSI) to decide top-level vs nested context, then adds the
applicable schema keys, skipping keys already present. Design.md §3.4.

### ROCKS-13-07: Hover documentation
`RockspecDocumentationTargetProvider` (design.md §2.6, mirrors
[LuaDocumentationTargetProvider.kt](../../../../src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationTargetProvider.kt))
resolves the identifier under the caret to a schema key and renders its `description`.

### ROCKS-13-08: Add-required-field quick fix
`RockspecAddRequiredFieldsQuickFix : LocalQuickFix` (design.md §2.7) inserts `key = <stub>` lines
(stub by kind: `""` for string, `{}` for object) for the missing required keys, via
`WriteCommandAction` on the document. Design.md §3.5.

### ROCKS-13-09: `rockspec_format` version selection
`RockspecSchemaLoader.schemaFor(file)` reads the file's `rockspec_format` string literal (top-level
assignment, same PSI walk as §2.4); `startsWith("3.1")` → v3.1 schema, else v3.0. Design.md §3.6.

## Behavior Rules
1. **Rockspec-only**: no ROCKS-13 component produces any marker, completion, or doc on a non-`.rockspec`
   Lua file (ROCKS-13-01).
2. **Conservative validation**: only literal/table RHS kinds are checked; a value computed by a
   reference, call, or operator is never flagged (avoids false positives).
3. **Closed objects only for unknown-key checks**: the root and the four `additionalProperties:false`
   nested objects flag unknowns; `build` (open) never flags unknown keys.
4. **No EDT blocking**: schema load is a one-shot classpath read at first use; the inspection/completion
   read PSI only (already inside platform read actions). No subprocess, no `RockspecBridge`.
5. **Single marker per problem**: duplicate top-level keys are each visited, but a missing-required
   marker is emitted once per missing key.

## Test Cases

| # | Requirement | Given (input `foo-1.0-1.rockspec`) | When | Then |
|---|-------------|-------------------------------------|------|------|
| 1 | 13-01 | `package = "foo"` in a file named `test.lua` (NOT `.rockspec`) | inspection runs | no rockspec markers reported |
| 2 | 13-03 | `pkg_name = "foo"` at top level | inspection runs | WARNING "Unknown rockspec field 'pkg_name'" on the key |
| 3 | 13-03 | `description = { sumary = "x" }` (typo) | inspection runs | WARNING "Unknown field 'sumary' in 'description'" on the nested key |
| 4 | 13-03 | `build = { foo_backend = 1 }` (build is open) | inspection runs | **no** unknown-key warning inside `build` |
| 5 | 13-04 | `package = "foo"; version = "1.0-1"` (no `source`/`build`) | inspection runs | WARNING listing missing required `source` and `build` |
| 6 | 13-05 | `package = { }` (table, schema wants string) | inspection runs | WARNING "Field 'package' should be a string" |
| 7 | 13-05 | `version = somevar` (a name ref, not a literal) | inspection runs | **no** value-kind warning (conservative) |
| 8 | 13-06 | caret at top-level statement start, `pack<caret>` | `completeBasic()` | lookup contains `package`, `version`, `source`, `build`, `dependencies` |
| 9 | 13-06 | caret inside `description = { <caret> }` | `completeBasic()` | lookup contains `summary`, `detailed`, `homepage`, `license` |
| 10 | 13-09 | `rockspec_format = "3.1"` + `test = {}` (v3.1-only key) | inspection runs | `test` is recognised (no unknown-key warning) |
| 11 | 13-09 | no `rockspec_format`, `test = {}` | inspection runs | WARNING unknown field `test` (v3.0 schema, no `test`) |
| 12 | 13-08 | TC #5 file, invoke the quick fix | apply fix | `source = {}` and `build = {}` stubs inserted; re-run → no missing-required warning |

## Acceptance Criteria
- [ ] No rockspec marker/completion/doc appears on a plain `.lua` file (TC #1).
- [ ] Unknown top-level and nested (closed-object) keys are flagged; `build` internals are not (TC #2-#4).
- [ ] Missing required `package`/`version`/`source`/`build` is flagged (TC #5).
- [ ] Value-kind mismatch on a literal is flagged; non-literal RHS is not (TC #6-#7).
- [ ] Completion offers schema keys at top level and inside a known nested table (TC #8-#9).
- [ ] `rockspec_format = "3.1"` selects the v3.1 schema (TC #10-#11).
- [ ] The quick fix inserts the missing required stubs (TC #12).

## Non-Functional Requirements
- Schema parse happens at most once per schema version per plugin run (cached object); the inspection
  must not re-parse JSON per file.
- All work is on-EDT-safe PSI reads inside the platform's read action; no subprocess, no I/O beyond the
  one-shot classpath schema read.
- No hard references to `Project`/`PsiFile`/`VirtualFile` retained; the schema model is immutable.

## Dependencies
- Existing PSI: `LuaAssignmentStatement` / `LuaVarList` / `LuaVar` / `LuaNameRef` / `LuaExprList` /
  `LuaTableConstructor` / `LuaFieldList` / `LuaField` / `LuaTerminalExpr` (all in
  [src/main/gen/.../lang/psi](../../../../src/main/gen/net/internetisalie/lunar/lang/psi)).
- Existing extension-point patterns: `LuaUndeclaredVariableInspection`, `LuaCompletionContributor`,
  `LuaDocumentationTargetProvider` (design.md §1 Prior Art).
- Bundled JSON schema assets (already present in `src/main/resources/jsonschema/`).
- `com.google.gson` (already on the plugin classpath via `RockspecBridge`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Manual checks: [human-verification-checklists.md](human-verification-checklists.md)
