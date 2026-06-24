---
id: "SCHEMA-01-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "SCHEMA-01"
folders:
  - "[[features/schema/01-engine/requirements|requirements]]"
---

# Technical Design: SCHEMA-01 — Lua JSON-Schema Engine

> Platform symbols below are cited from the local reference checkout
> `~/Documents/src/lua/intellij-community` (paths relative to that root). Lua PSI symbols are cited
> from this repo's `src/main/gen/.../lang/psi`.

## 1. Architecture Overview

### How the platform engine is reached (verified)
The JSON Schema engine is decoupled from JSON PSI and dispatches through a walker:
- `JsonSchemaComplianceInspection.annotate` obtains `JsonLikePsiWalker.getWalker(element, rootSchema)`
  and runs `new JsonSchemaComplianceChecker(rootSchema, holder, walker, …).annotate(element)`
  (`json/backend/src/com/jetbrains/jsonSchema/impl/inspections/JsonSchemaComplianceInspection.java:50-59`).
- `JsonLikePsiWalker.getWalker(...)` first tries `JsonOriginalPsiWalker`, else picks the first
  registered `JsonLikePsiWalkerFactory` whose `handles(element)` is true
  (`json/backend/src/com/jetbrains/jsonSchema/extension/JsonLikePsiWalker.java:88-100`).
- **The inspection is registered per host language.** JSON registers it for `language="JSON"`; YAML
  ships its **own** `<localInspection language="yaml" … YamlJsonSchemaHighlightingInspection>`
  (`plugins/yaml/backend/resources/intellij.yaml.backend.xml:77-82`) that reuses the same checker via
  the walker. **Lua must likewise register its own `language="Lua"` compliance inspection.**

### Prior art — YAML (the template to copy)
YAML, a non-JSON language, plugs in with exactly the pieces SCHEMA-01 builds for Lua:
- `org.jetbrains.yaml.schema.YamlJsonPsiWalker implements JsonLikePsiWalker`
  (`plugins/yaml/backend/src/schema/YamlJsonPsiWalker.java:59`).
- `YamlJsonLikePsiWalkerFactory implements JsonLikePsiWalkerFactory` (`handles`+`create`) and
  `YamlJsonEnabler implements JsonSchemaEnabler`, registered under `com.intellij.json` namespace
  (`intellij.yaml.backend.xml:132-134`).
- Adapters `YamlPropertyAdapter` / `YamlObjectAdapter` / `YamlGenericValueAdapter` / `YamlArrayAdapter`.

### Lunar prior art
- `.rockspec` is already a Lua-language extension (`plugin.xml:55-60`, `extensions="lua;rockspec"`).
- The build uses the IntelliJ Platform Gradle Plugin with `bundledPlugins(providers.gradleProperty(
  "platformBundledPlugins"))` (`build.gradle.kts:62-66`); `platformBundledPlugins` lives in
  `gradle.properties:33` (currently `org.jetbrains.plugins.terminal`).
- `<depends>` entries are in `plugin.xml:22-28`.

### Target state
```
build.gradle.kts/gradle.properties + plugin.xml  -> depend on com.intellij.modules.json   (§2.1)
                                                          |
            +---------------------------------------------+
            |                       |                       |
  LuaJsonLikePsiWalker      Lua*Adapter (4)        LuaJsonLikePsiWalkerFactory + LuaJsonSchemaEnabler
   (maps Lua data PSI         (wrap Lua PSI as        (engage engine on provider-claimed Lua files)
    -> JSON-like model)        JSON values)                         |
            |                       |                               |
            +-----------> JsonSchemaComplianceChecker <-------------+   (engine, unchanged)
                                    ^
   LuaJsonSchemaComplianceInspection (language="Lua") drives it; completion+docs flow via the walker
                                    ^
   LuaSchemaProviderFactory / LuaSchemaFileProvider (base)  <-- SCHEMA-02..04 map file -> schema
```

## 2. Core Components

All new code in package `net.internetisalie.lunar.lang.schema`.

### 2.1 Build & manifest dependency (SCHEMA-01-01)
- `gradle.properties:33` — append the JSON plugin:
  ```properties
  platformBundledPlugins = org.jetbrains.plugins.terminal, com.intellij.modules.json
  ```
- `plugin.xml` (after `:23`) — add:
  ```xml
  <depends>com.intellij.modules.json</depends>
  ```
- Verified id: the bundled GoLand `plugins/json/lib/json.jar` `META-INF/plugin.xml` declares
  `<id>com.intellij.modules.json</id>` and registers `<JsonSchema.ProviderFactory>` /
  `<jsonLikePsiWalkerFactory>` / `<jsonSchemaEnabler>`.

### 2.2 `LuaJsonLikePsiWalker` (SCHEMA-01-02)
- **Implements** `com.jetbrains.jsonSchema.extension.JsonLikePsiWalker`
  (`json/backend/src/com/jetbrains/jsonSchema/extension/JsonLikePsiWalker.java`).
- **Singleton** `INSTANCE` (YAML pattern).
- **Method mapping** (the contract a host language must satisfy):
  | Walker method | Lua implementation |
  |---|---|
  | `isName(element)` | `ThreeState.YES` if element is the key identifier of a top-level `LuaVar.nameRef` or a `LuaField.identifier`; `NO` if it is a value position; else `UNSURE` |
  | `isPropertyWithValue(element)` | true for a `LuaAssignmentStatement` (shape A) or a `LuaField` with an identifier + value (shape B) |
  | `findElementToCheck(element)` | nearest enclosing assignment/field/value the engine should validate (walk up to `LuaAssignmentStatement`/`LuaField`/`LuaExpr`) |
  | `getParentPropertyAdapter(element)` | wrap the enclosing assignment/field as `LuaPropertyAdapter`, else null |
  | `createValueAdapter(element)` | `LuaValueAdapter.of(element)` when element is a `LuaExpr`, else null (§2.3) |
  | `isTopJsonElement(element)` | true for the `LuaFile` (shape A) or the returned `LuaTableConstructor`'s parent `LuaFinalStatement` (shape B) |
  | `getRoots(file)` | §3.1 — returns the single synthetic/real root object element |
  | `getPropertyNamesOfParentObject(originalPosition, computedPosition)` | collect sibling property names (top-level keys, or field keys) — for "already present" filtering |
  | `findPosition(element, forceLastTransition)` | §3.2 — builds the `JsonPointerPosition` |
  | `requiresNameQuotes()` | `false` (Lua bareword keys) |
  | `allowsSingleQuotes()` / `hasMissingCommaAfter(...)` | `true` / Lua-comma rules (§3.3) |
- Methods not meaningful for a config file (e.g. JSON-pointer-to-comment) take the interface default.

### 2.3 Adapters (SCHEMA-01-03)
Package-private, in `…schema.adapters`. Each wraps one Lua PSI node.
- `LuaValueAdapter(expr: LuaExpr) : JsonValueAdapter`
  (`json/backend/src/com/jetbrains/jsonSchema/extension/adapters/JsonValueAdapter.java`):
  | Adapter method | Lua impl |
  |---|---|
  | `isObject()` | `expr is LuaTableConstructor && §3.4 isObjectTable(it)` |
  | `isArray()` | `expr is LuaTableConstructor && !isObjectTable(it)` |
  | `isStringLiteral()` | `expr is LuaTerminalExpr && expr.string != null` |
  | `isNumberLiteral()` | `expr is LuaTerminalExpr && expr.number != null` |
  | `isBooleanLiteral()` | first child `elementType ∈ {LuaElementTypes.TRUE, LuaElementTypes.FALSE}` |
  | `isNull()` | first child `elementType == LuaElementTypes.NIL` |
  | `getAsObject()` | `LuaObjectAdapter(expr)` when `isObject()` else null |
  | `getAsArray()` | `LuaArrayAdapter(expr)` when `isArray()` else null |
  | `getDelegate()` | `expr` |
- `LuaObjectAdapter(table)`/`LuaFileObjectAdapter(file)` : `JsonObjectValueAdapter`
  (`…/adapters/JsonObjectValueAdapter.java`) — `getPropertyList()` returns `LuaPropertyAdapter`s:
  - file form: one per top-level `LuaAssignmentStatement` (key = `varList.varList[0].nameRef`)
  - table form: one per `LuaField` with a non-null identifier or string `[ "k" ]` key
- `LuaArrayAdapter(table) : JsonArrayValueAdapter` — `getElements()` = `LuaValueAdapter` over each
  positional `LuaField`'s single expr.
- `LuaPropertyAdapter(keyOwner) : JsonPropertyAdapter` (`…/adapters/JsonPropertyAdapter.java`):
  `getName()` = key text (unquoted); `getNameValueAdapter()` = adapter over the key element (for
  key-position validation); `getValues()` = `[LuaValueAdapter(rhs)]`; `getDelegate()` = the
  assignment/field; `getParentObject()` = enclosing object adapter.

### 2.4 Factory + enabler (SCHEMA-01-06)
- `LuaJsonLikePsiWalkerFactory : JsonLikePsiWalkerFactory`:
  ```kotlin
  override fun handles(element: PsiElement) = element.containingFile is LuaFile || element is LuaExpr
  override fun create(schema: JsonSchemaObject?) : JsonLikePsiWalker = LuaJsonLikePsiWalker.INSTANCE
  ```
- `LuaJsonSchemaEnabler : JsonSchemaEnabler`
  (`json/backend/src/com/jetbrains/jsonSchema/extension/JsonSchemaEnabler.java`):
  ```kotlin
  override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean =
      file.fileType is LuaFileType && project != null &&
      JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file).isNotEmpty()
  ```
  (Gate to Lua files a provider actually maps — keeps plain `.lua` inert. Risk 1.2 covers the
  cost of the service lookup; a name-pattern fast-path is the fallback.)
- Registration in `plugin.xml`:
  ```xml
  <extensions defaultExtensionNs="com.intellij.json">
    <jsonLikePsiWalkerFactory implementation="net.internetisalie.lunar.lang.schema.LuaJsonLikePsiWalkerFactory"/>
    <jsonSchemaEnabler        implementation="net.internetisalie.lunar.lang.schema.LuaJsonSchemaEnabler"/>
  </extensions>
  ```

### 2.5 Compliance inspection (SCHEMA-01-07)
- `LuaJsonSchemaComplianceInspection : LocalInspectionTool` — copies the shape of
  `YamlJsonSchemaHighlightingInspection`: in `buildVisitor`, for each root element resolve the schema
  via `JsonSchemaService`, obtain the walker, and run `JsonSchemaComplianceChecker`. (If the platform
  exposes a reusable base like `JsonSchemaBasedInspectionBase`, subclass it instead — DR-02 confirms
  which is accessible from a third-party plugin.)
- Registration:
  ```xml
  <localInspection language="Lua" shortName="LuaJsonSchemaCompliance"
        displayName="Schema validation (Lua data files)" groupName="Lua"
        enabledByDefault="true" level="WARNING"
        implementationClass="net.internetisalie.lunar.lang.schema.LuaJsonSchemaComplianceInspection"/>
  ```
- **Completion & documentation** require no Lua code: once the walker is registered, the engine's
  own `JsonSchemaCompletionContributor` and documentation provider consult it (verified: they dispatch
  through `JsonLikePsiWalker.getWalker`). DR-03 confirms completion fires for Lua without a Lua-side
  contributor; if not, a thin `language="Lua"` delegating contributor is the fallback (Gap 2.3).

### 2.6 Provider seam (SCHEMA-01-08)
- `abstract class LuaSchemaFileProvider(...) : JsonSchemaFileProvider`
  (`json/backend/src/com/jetbrains/jsonSchema/extension/JsonSchemaFileProvider.java`): subclasses
  implement `isAvailable(file)` (claim predicate) + `getSchemaFile()` (the bundled schema VFS) +
  `getName()`/`getSchemaType()`/`getSchemaVersion()`.
- `abstract class LuaSchemaProviderFactory : JsonSchemaProviderFactory` (EP
  `"JavaScript.JsonSchema.ProviderFactory"`, registered `<JsonSchema.ProviderFactory>`): returns the
  provider list. SCHEMA-02..04 register their own factory; SCHEMA-01 ships only the base classes +
  a helper to load a bundled schema resource into a `VirtualFile`
  (`JsonSchemaProviderFactory.getResourceFile(this::class.java, "/schema/<name>.json")` — a platform
  helper; DR-04 confirms its signature).

## 3. Algorithms

### 3.1 `getRoots(file)` — the two document shapes (SCHEMA-01-04)
- **Input → Output**: `PsiFile` → `Collection<PsiElement>` (the root object element(s)).
- **Steps**:
  1. **Shape B first**: scan `file.children` for a `LuaFinalStatement`
     (`finalStatement ::= RETURN [exprList]`, `LuaFinalStatement.getExprList()`); if present and its
     `exprList.exprList[0]` is a `LuaTableConstructor`, return `listOf(thatTable)` (root = table).
  2. **Shape A otherwise**: return `listOf(file)` — `LuaFileObjectAdapter` enumerates top-level
     `LuaAssignmentStatement`s as the root object's properties (§2.3).
- A file with both top-level assignments and a `return {…}` uses shape B (the returned table is the
  conventional root); the assignments are then locals/side declarations, not schema properties
  (Gap 2.2).

### 3.2 `findPosition` — JSON pointer for a Lua element
- **Input → Output**: `(element, forceLastTransition)` → `JsonPointerPosition` (the path from root).
- **Steps** (depth-general, walk up to a root from §3.1):
  1. Start with an empty `JsonPointerPosition`; set `current = element`.
  2. While `current` is not a root element:
     - if `current`/ancestor is a `LuaField` with identifier `k` (or a top-level assignment with key
       `k`): prepend object step `k`.
     - if `current` is a positional `LuaField` in an array table: prepend the index (its position
       among positional siblings).
     - advance `current` to the enclosing `LuaTableConstructor` (or the file for shape A).
  3. Return the accumulated position.
- Mirrors `YamlJsonPsiWalker.findPosition`; DR-01 validates pointer equality against a YAML/JSON
  oracle on the harness schema.

### 3.3 Name/value disambiguation & syntax flags
- `isName`: the engine asks "is this a property name?" — YES only for the key identifier
  (`LuaVar.nameRef` at file scope, or `LuaField.identifier`); the RHS expr is a value (NO). Ambiguous
  bare identifiers default `UNSURE` so the engine consults position.
- `requiresNameQuotes() = false`, `allowsSingleQuotes() = true` (Lua strings allow `'`/`"`); comma
  handling reports Lua field separators (`,`/`;`).

### 3.4 `isObjectTable(table)` — object vs array (SCHEMA-01-05)
- **Input → Output**: `LuaTableConstructor` → `Boolean`.
- **Steps**: `fieldList?.fieldList` — if **any** `LuaField.identifier != null` (an `IDENTIFIER = v`
  key) or any field is a string-keyed `[ "k" ] = v`, return true (object). If all fields are
  positional (`identifier == null`, single expr, no `[…]` key), return false (array). Empty table →
  object (an empty `{}` validates against `type:object` or `type:array`; the engine's `getAlternateType`
  handles the empty-collection ambiguity, matching YAML's empty-mapping-≡-null treatment).

## 4. External Data & Parsing
- **Schema files** are JSON loaded by the platform's `JsonSchemaService` (cached, off-thread); SCHEMA-01
  ships **no** schema and performs **no** JSON parsing of its own — that is the whole point versus the
  ROCKS-13 hand-rolled interpreter. Providers (SCHEMA-02..04) supply schema `VirtualFile`s.
- **Lua data files** are read as PSI; no serialization. The adapter layer is the only translation.

## 5. Data Flow

### Shape A (rockspec/luacheckrc), validation
1. User edits a provider-claimed `*.testcfg`/`.rockspec`. `LuaJsonSchemaEnabler` → enabled (a provider
   maps it).
2. `LuaJsonSchemaComplianceInspection` resolves the schema (`JsonSchemaService`), gets
   `LuaJsonLikePsiWalker`, runs the checker on the root = `LuaFile` (shape A).
3. `LuaFileObjectAdapter.getPropertyList()` yields a `LuaPropertyAdapter` per top-level assignment; the
   engine compares against the schema (`required`, `additionalProperties`, types, enums, `$ref` — all
   engine-side) and annotates (TC #1, #2).

### Shape B (busted), validation
1. `return { name = "x", bogus = 1 }`; `getRoots` returns the table (shape B); `LuaObjectAdapter`
   enumerates its keyed fields; engine flags `bogus` (TC #5).

### Completion / docs
1. Caret at a key position; the engine's completion/doc path calls `getWalker` → Lua walker →
   adapters; lookups/descriptions come from the schema (TC #7, #8) with no Lua-side contributor.

## 6. Edge Cases
- **No provider maps the file** → enabler false → engine inert; plain `.lua` safe (TC #6).
- **Mixed table** (`{ 1, 2, x = 3 }`) → object (has a keyed field); positional entries ignored for
  property checks (Gap 2.1).
- **Computed/non-string key** (`[var] = v`) → skipped as a property (Behavior Rule 4).
- **Both top-level globals and `return {…}`** → shape B wins (Gap 2.2).
- **Empty file / no roots** → engine no-ops.
- **Schema resource missing** → `JsonSchemaService` resolves nothing → no validation (provider bug,
  surfaced in that provider's tests, not here).

## 7. Integration Points (plugin.xml / build)
- `gradle.properties:33` + `plugin.xml` `<depends>` — §2.1.
- `<extensions defaultExtensionNs="com.intellij.json">` — `<jsonLikePsiWalkerFactory>` +
  `<jsonSchemaEnabler>` — §2.4.
- `<localInspection language="Lua" … LuaJsonSchemaCompliance>` — §2.5.
- Base provider/factory classes are abstract — registered by the concrete SCHEMA-02..04 features, not
  here. No new index, action, or settings key.

## 8. Testing Strategy
- A **TEST-ONLY** `JsonSchemaProviderFactory` maps a fixture extension (e.g. `*.testcfg`/`*.testret`,
  registered to `LuaFileType` in the fixture) to the harness schema (SCHEMA-01-requirements Test
  Cases), exercising the walker/adapters without depending on SCHEMA-02..04.
- `LuaJsonSchemaEngineTest` (`BasePlatformTestCase`): `configureByText` + `doHighlighting()` /
  `checkHighlighting()` (TC #1-#6), `completeBasic()` (TC #7), Quick-Doc (TC #8). Mirrors
  `LuaUndeclaredVariableInspectionTest` / `LuaCompletionTest` fixtures.
- Adapter unit coverage: `isObjectTable`, the shape-A/B `getRoots`, and `findPosition` against a
  hand-built pointer oracle (DR-01).

## 9. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SCHEMA-01-01 JSON plugin dependency | M | §2.1 |
| SCHEMA-01-02 Lua walker | M | §2.2, §3.2, §3.3 |
| SCHEMA-01-03 Lua adapters | M | §2.3 |
| SCHEMA-01-04 Two document shapes | M | §3.1 |
| SCHEMA-01-05 Object vs array | M | §3.4 |
| SCHEMA-01-06 Walker factory + enabler | M | §2.4 |
| SCHEMA-01-07 Compliance inspection | M | §2.5 |
| SCHEMA-01-08 Provider seam + safety | M | §2.6, §6 |

## 10. Alternatives Considered
- **Hand-rolled per-file validators** (the ROCKS-13 approach): rejected as the general strategy — it
  duplicates schema interpretation per file type, caps at depth 1, and drifts from the shipped schemas.
  SCHEMA-01 reuses the platform engine; ROCKS-13 becomes SCHEMA-02.
- **Grow the ROCKS-13 reduced interpreter into a full draft-07 engine**: rejected — reimplements
  ~thousands of lines (refs/oneOf/conditionals/enums) the platform already provides via the walker.
- **Enable the engine for all `.lua` files unconditionally**: rejected — risks overhead and surprise
  validation on ordinary source; the enabler gates to provider-claimed files (§2.4).
- **One catch-all Lua provider with internal file routing**: rejected — the platform's
  `JsonSchemaFileProvider.isAvailable` per provider is the idiomatic seam; each consumer owns its
  mapping (SCHEMA-02..04).

## 11. Open Questions
_None — feature has cleared the planning bar; platform-behaviour confirmations (pointer construction, base-inspection accessibility, engine-supplied completion, bundled-schema resource helper) are tracked as DR-01..DR-04 de-risking spikes in risks-and-gaps.md._
