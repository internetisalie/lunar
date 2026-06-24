---
id: "ROCKS-13-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "ROCKS-13"
folders:
  - "[[features/rocks/13-rockspec-editor-support/requirements|requirements]]"
---

# Technical Design: ROCKS-13 — Rockspec Editor Support

## 1. Architecture Overview

### Current State
- `.rockspec` is registered as a Lua-language extension
  ([plugin.xml:55-60](../../../../src/main/resources/META-INF/plugin.xml), `extensions="lua;rockspec"`),
  so rockspec files parse to the standard Lua PSI and are highlighted. No schema-aware insight exists.
- The bundled JSON schemas
  ([rockspec-schema-v30.json](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json),
  [rockspec-schema-v31.json](../../../../src/main/resources/jsonschema/rockspec-schema-v31.json)) and
  LuaCATS meta-files (`platform/LuaRocks/rockspec-v3X.lua`) are present but **unused** by any Kotlin
  code.
- There is **no** file-type-scoped scope/`_ENV` override hook: `LuaFile.processDeclarations`
  ([LuaFile.kt:41-76](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaFile.kt)) and
  `LuaScopeProcessor` ([LuaScopeProcessor.kt:25](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt))
  resolve globals identically for every Lua file.

### Prior Art in This Repo (reused patterns — verified)
- **`LuaUndeclaredVariableInspection`**
  ([LuaUndeclaredVariableInspection.kt:30](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUndeclaredVariableInspection.kt))
  — the `LocalInspectionTool` + `buildVisitor()` + `holder.registerProblem(...)` template that
  `RockspecSchemaInspection` follows; registered via `<localInspection language="Lua" …>`
  ([plugin.xml:150-155](../../../../src/main/resources/META-INF/plugin.xml)).
- **`LuaGlobalCreationInspection`**
  ([LuaGlobalCreationInspection.kt:24](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaGlobalCreationInspection.kt))
  — visits `LuaAssignmentStatement` and attaches a `LocalQuickFix`; the model for ROCKS-13 validation +
  quick fix.
- **`LuaCompletionContributor`**
  ([LuaCompletionContributor.kt:21](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaCompletionContributor.kt))
  — `extend(CompletionType.BASIC, psiElement(), provider)`, `LookupElementBuilder.create(...)`,
  `PrioritizedLookupElement.withPriority(...)`, `PsiTreeUtil.prevVisibleLeaf(position)`; registered
  `<completion.contributor language="Lua" …>` ([plugin.xml:239-241](../../../../src/main/resources/META-INF/plugin.xml)).
- **`LuaDocumentationTargetProvider`**
  ([LuaDocumentationTargetProvider.kt:25](../../../../src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationTargetProvider.kt))
  — `DocumentationTargetProvider.documentationTargets(file, offset)`; registered
  `<platform.backend.documentation.targetProvider …>` ([plugin.xml:83-85](../../../../src/main/resources/META-INF/plugin.xml)).
- **`RockspecBridge`**
  ([RockspecBridge.kt](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt))
  — establishes that `com.google.gson` (`JsonObject`/`JsonElement`) is on the plugin classpath; ROCKS-13
  reuses Gson to parse the schema. **Not** invoked at runtime (no subprocess).

### Target State
A new package `net.internetisalie.lunar.rocks.editor` holds: an immutable `RockspecSchema` model, a
singleton `RockspecSchemaLoader` that parses the bundled JSON once per version, a `RockspecFileSupport`
guard, and four extension components (inspection + quick fix, completion, documentation). All read the
Lua PSI of the open file; none touch the type engine.

```
RockspecSchemaLoader (object, lazy + cached)
   |  parse /jsonschema/rockspec-schema-v3X.json (Gson) -> RockspecSchema
   v
RockspecSchema (immutable: root SchemaNode + definitions)
   ^                ^                 ^
   |                |                 |
RockspecSchemaInspection   RockspecCompletionContributor   RockspecDocumentationTargetProvider
   |  (+ RockspecAddRequiredFieldsQuickFix)
   |
   all gated by RockspecFileSupport.isRockspec(file)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.editor.RockspecSchema`
- **Responsibility**: immutable, reduced representation of a rockspec JSON schema, sufficient for
  key/required/kind/description lookups at one nesting level.
- **Threading**: immutable value object; freely shared.
- **Key API**:
  ```kotlin
  enum class SchemaKind { STRING, OBJECT, ARRAY, BOOLEAN, ANY }   // maps draft-07 "type"

  /** One object node: its keys (name -> child), required keys, and whether unknown keys are allowed. */
  data class SchemaNode(
      val kind: SchemaKind,
      val description: String?,
      val properties: Map<String, SchemaNode>,   // empty for non-object kinds
      val required: Set<String>,                  // subset of properties keys
      val closed: Boolean,                        // additionalProperties == false
  )

  data class RockspecSchema(val root: SchemaNode) {
      /** Top-level child node for a key, or null if not a known key. */
      fun topLevel(key: String): SchemaNode? = root.properties[key]
      val topLevelKeys: Set<String> get() = root.properties.keys
      val requiredTopLevel: Set<String> get() = root.required
  }
  ```
- **Notes**: only **object** nodes carry `properties`/`required`/`closed`. `$ref` and `oneOf` are
  flattened at load time (§4.1) so the model has no refs. Nesting is materialised one level below root
  for the four closed objects; deeper sub-trees collapse to `SchemaKind.ANY` (out of scope).

### 2.2 `net.internetisalie.lunar.rocks.editor.RockspecSchemaLoader`
- **Responsibility**: parse each bundled schema once, cache it, and pick the version for a file.
- **Threading**: lazy init guarded by `by lazy` (idempotent); the parsed `RockspecSchema` is immutable.
- **Collaborators**: Gson (`com.google.gson.JsonParser`/`JsonObject`), `RockspecFileSupport`,
  the §3.6 `rockspec_format` reader.
- **Key API**:
  ```kotlin
  object RockspecSchemaLoader {
      /** Parsed once; v3.0 is the default/fallback schema. */
      val v30: RockspecSchema by lazy { load("/jsonschema/rockspec-schema-v30.json") }
      val v31: RockspecSchema by lazy { load("/jsonschema/rockspec-schema-v31.json") }

      /** Choose the schema for a rockspec PSI file via its rockspec_format (§3.6). */
      fun schemaFor(file: PsiFile): RockspecSchema

      private fun load(resource: String): RockspecSchema   // §4.1 interpreter
  }
  ```
- **Resource read**: `RockspecSchemaLoader::class.java.getResourceAsStream(resource)` (the JSON ships in
  the plugin jar under `src/main/resources/jsonschema/`). A null stream (missing resource) throws at
  load — caught by the inspection's `try` and degraded to "no schema" (no markers); logged once via
  `Logger.getInstance`.

### 2.3 `net.internetisalie.lunar.rocks.editor.RockspecFileSupport`
- **Responsibility**: the single `.rockspec` predicate every component shares (ROCKS-13-01).
- **Key API**:
  ```kotlin
  object RockspecFileSupport {
      fun isRockspec(file: PsiFile?): Boolean =
          file?.virtualFile?.extension?.equals("rockspec", ignoreCase = true) == true

      /** Top-level (assignment-statement) entries of a rockspec file: key -> value expr. */
      fun topLevelAssignments(file: PsiFile): List<Pair<LuaNameRef, LuaExpr>>
  }
  ```
- **`topLevelAssignments`**: iterates `file.children`, keeps each `LuaAssignmentStatement`
  ([LuaAssignmentStatement.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaAssignmentStatement.java)),
  pairs `varList.varList[0].nameRef`
  ([LuaVar.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaVar.java)) with the
  positionally-matched `exprList.exprList[i]`. Single-target assignments only (`a = b`); a multi-assign
  (`a, b = 1, 2`) at file scope is malformed for a rockspec and contributes each LHS paired with its
  positional RHS (or skipped when no RHS at that index).

### 2.4 `net.internetisalie.lunar.rocks.editor.RockspecSchemaInspection`
- **Responsibility**: ROCKS-13-03/04/05 — unknown-key, missing-required, value-kind diagnostics.
- **Threading**: standard inspection; platform runs `buildVisitor` inside a read action.
- **Collaborators**: `RockspecFileSupport`, `RockspecSchemaLoader`, `ProblemsHolder`,
  `RockspecAddRequiredFieldsQuickFix`.
- **Registration**: `<localInspection>` (§7).
- **Key API**:
  ```kotlin
  class RockspecSchemaInspection : LocalInspectionTool() {
      override fun getShortName() = "RockspecSchema"
      override fun getGroupDisplayName() = "LuaRocks"
      override fun getDisplayName() = "Rockspec schema validation"
      override fun isEnabledByDefault() = true
      override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
          val file = holder.file
          if (!RockspecFileSupport.isRockspec(file)) return PsiElementVisitor.EMPTY_VISITOR
          val schema = runCatching { RockspecSchemaLoader.schemaFor(file) }.getOrNull()
              ?: return PsiElementVisitor.EMPTY_VISITOR
          return RockspecVisitor(schema, holder)   // private inner; see §3.1-§3.3
      }
  }
  ```
- The missing-required pass (§3.2) runs once: the inspection also overrides
  `checkFile(file, manager, isOnTheFly)` **or** performs the file-level check when visiting the file's
  first child. Chosen approach: override `buildVisitor` only and run the required-key scan lazily on the
  **first** visited `LuaAssignmentStatement` guarded by a `var requiredChecked = false` flag on the
  visitor (single emission, Behavior Rule 5).

### 2.5 `net.internetisalie.lunar.rocks.editor.RockspecCompletionContributor`
- **Responsibility**: ROCKS-13-06 — schema-key completion at top level and inside known nested tables.
- **Threading**: completion runs off the document snapshot; PSI reads only.
- **Collaborators**: `RockspecFileSupport`, `RockspecSchemaLoader`, `PsiTreeUtil`,
  `LookupElementBuilder`, `PrioritizedLookupElement`.
- **Registration**: `<completion.contributor>` (§7).
- **Key API**:
  ```kotlin
  class RockspecCompletionContributor : CompletionContributor() {
      init {
          extend(CompletionType.BASIC, psiElement(), object : CompletionProvider<CompletionParameters>() {
              override fun addCompletions(
                  parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet,
              ) = addKeyCompletions(parameters, result)   // §3.4
          })
      }
  }
  ```

### 2.6 `net.internetisalie.lunar.rocks.editor.RockspecDocumentationTargetProvider`
- **Responsibility**: ROCKS-13-07 — Quick-Doc on a schema key shows its `description`.
- **Collaborators**: `RockspecFileSupport`, `RockspecSchemaLoader`; mirrors
  `LuaDocumentationTargetProvider` ([LuaDocumentationTargetProvider.kt:25-36](../../../../src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationTargetProvider.kt)).
- **Registration**: `<platform.backend.documentation.targetProvider>` (§7).
- **Key API**:
  ```kotlin
  class RockspecDocumentationTargetProvider : DocumentationTargetProvider {
      override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget>
  }
  /** Renders SchemaNode.description (+ kind, required flag) as the hover body. */
  class RockspecKeyDocumentationTarget(/* key, node, anchor pointer */) : DocumentationTarget
  ```
- **Resolution**: `file.findElementAt(offset)` with `elementType == LuaElementTypes.IDENTIFIER`
  ([LuaElementTypes.java:92](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaElementTypes.java));
  if its parent chain is a top-level `LuaVar.nameRef` or a nested `LuaField.identifier`
  ([LuaField.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaField.java)) whose key
  is in the (resolved) schema node, return one `RockspecKeyDocumentationTarget`.

### 2.7 `net.internetisalie.lunar.rocks.editor.RockspecAddRequiredFieldsQuickFix`
- **Responsibility**: ROCKS-13-08 — insert stubs for missing required top-level keys.
- **Collaborators**: `WriteCommandAction`, the document; `RockspecSchema.requiredTopLevel`.
- **Key API**:
  ```kotlin
  class RockspecAddRequiredFieldsQuickFix(private val missing: List<String>) : LocalQuickFix {
      override fun getFamilyName() = "Add missing required rockspec fields"
      override fun applyFix(project: Project, descriptor: ProblemDescriptor) { /* §3.5 */ }
  }
  ```

## 3. Algorithms

### 3.1 Unknown-key check (ROCKS-13-03)
- **Input → Output**: `(SchemaNode parent, key: String, keyElement: PsiElement)` → optional problem.
- **Steps**:
  1. If `!parent.closed` → return (open object, e.g. `build`; never flag unknowns). Behavior Rule 3.
  2. If `key in parent.properties` → return (known).
  3. Else `holder.registerProblem(keyElement, "Unknown rockspec field '<key>'"<, in '<objName>'>,
     ProblemHighlightType.WARNING)`.
- **Where applied**:
  - Root: for each top-level assignment, `parent = schema.root`, `key = nameRef.text`,
    `keyElement = nameRef`.
  - Nested: only when a top-level key's node is a **closed object** (`description`/`source`/`hooks`/
    `deploy`) and its RHS is a `LuaTableConstructor`; iterate `fieldList.fieldList`
    ([LuaFieldList.java:11](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaFieldList.java)),
    take `field.identifier` ([LuaField.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaField.java))
    as the key, recurse one level with `parent = <that nested node>`.

### 3.2 Missing-required check (ROCKS-13-04)
- **Input → Output**: `PsiFile` → zero or one problem (lists all missing keys).
- **Steps**:
  1. `seen = RockspecFileSupport.topLevelAssignments(file).map { it.first.text }.toSet()`.
  2. `missing = schema.requiredTopLevel - seen` — **read from the parsed schema, version-dependent, never
     hardcoded**: v3.0 root `required` = `{package, version, source, build}`
     ([rockspec-schema-v30.json:6-11](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json));
     v3.1 root `required` = `{package, source, version}` — **`build` is NOT required in v3.1**
     ([rockspec-schema-v31.json:6-10](../../../../src/main/resources/jsonschema/rockspec-schema-v31.json)).
  3. If `missing.isNotEmpty()`: anchor on `file.firstChild ?: file`; message
     `"Rockspec is missing required field(s): <missing sorted, comma-joined>"`; attach
     `RockspecAddRequiredFieldsQuickFix(missing.sorted())`.
- **Single emission**: run once per `buildVisitor` (guarded flag, §2.4).

### 3.3 Value-kind check (ROCKS-13-05)
- **Input → Output**: `(SchemaNode expected, valueExpr: LuaExpr)` → optional problem.
- **Lua kind of `valueExpr`** (`luaKind`):
  - `valueExpr is LuaTableConstructor` → `OBJECT_OR_ARRAY` (a Lua table serves both).
  - `valueExpr is LuaTerminalExpr && valueExpr.string != null`
    ([LuaTerminalExpr.java:14](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaTerminalExpr.java)) → `STRING`.
  - first child `elementType == LuaElementTypes.TRUE || == LuaElementTypes.FALSE`
    ([LuaElementTypes.java:84,125](../../../../src/main/gen/net/internetisalie/lunar/lang/psi/LuaElementTypes.java)) → `BOOLEAN`.
  - `valueExpr is LuaTerminalExpr && valueExpr.number != null` → `NUMBER`.
  - anything else (name refs, calls, binops, `nil`) → `UNKNOWN`.
- **Decision table** (expected `SchemaKind` vs `luaKind`):
  | expected \ luaKind | STRING | OBJECT_OR_ARRAY | BOOLEAN | NUMBER | UNKNOWN |
  |---|---|---|---|---|---|
  | STRING | ok | **bad** | **bad** | **bad** | ok (conservative) |
  | OBJECT | **bad** | ok | **bad** | **bad** | ok |
  | ARRAY | **bad** | ok | **bad** | **bad** | ok |
  | BOOLEAN | **bad** | **bad** | ok | **bad** | ok |
  | ANY | ok | ok | ok | ok | ok |
- **bad** → `holder.registerProblem(valueExpr, "Field '<key>' should be a <expected.lowercase()>",
  ProblemHighlightType.WARNING)`. UNKNOWN is never flagged (Behavior Rule 2; TC #7).
- `OBJECT` and `ARRAY` both accept a Lua table (a Lua array is a table), so neither is distinguished
  beyond "is it a table constructor" — i.e. a table passed where `array` is wanted is accepted.

### 3.4 Key completion (ROCKS-13-06)
- **Input → Output**: `CompletionParameters` → added `LookupElement`s.
- **Steps**:
  1. `file = parameters.originalFile`; if `!isRockspec(file)` → return.
  2. `schema = RockspecSchemaLoader.schemaFor(file)`.
  3. Determine context table from `parameters.position`:
     - Walk up from `position`; if the nearest enclosing `LuaTableConstructor` is the RHS of a top-level
       assignment whose key node is a known object → `node = that nested SchemaNode`, `present =`
       keys already in that table; offer `node.properties.keys - present`.
     - Else (no enclosing rockspec table; `prevVisibleLeaf` is null / a statement separator / file
       start) → top-level context: `node = schema.root`, `present =` top-level keys already seen;
       offer `schema.topLevelKeys - present`.
  4. For each offered key: `LookupElementBuilder.create(key).withTailText(" = ", true)` with
     `.withInsertHandler` appending `" = "`; wrap in `PrioritizedLookupElement.withPriority(it, 20.0)`;
     `result.addElement(...)`.
- Uses `PsiTreeUtil.prevVisibleLeaf(position)` to disambiguate "inside a table" vs "statement start",
  per the AGENTS.md dummy-identifier lesson.

### 3.5 Add-required-field quick fix (ROCKS-13-08)
- **Input → Output**: `missing: List<String>` → document edit.
- **Steps** (inside `applyFix`, the platform already wraps it in a write action):
  1. `doc = PsiDocumentManager.getInstance(project).getDocument(descriptor.psiElement.containingFile)`.
  2. `stub = missing.joinToString("\n") { "$it = ${stubFor(it)}" } + "\n"` where
     `stubFor(key) = if (schema.topLevel(key)?.kind == SchemaKind.STRING) "\"\"" else "{}"`
     (so `package`/`version` → `""`, `source`/`build` → `{}`).
  3. `doc.insertString(0, stub)`; commit via `PsiDocumentManager.commitDocument(doc)`.

### 3.6 `rockspec_format` version selection (ROCKS-13-09)
- **Input → Output**: `PsiFile` → `RockspecSchema`.
- **Steps**:
  1. Find the top-level `rockspec_format` assignment via `RockspecFileSupport.topLevelAssignments`.
  2. If its value is a `LuaTerminalExpr` string literal, strip the surrounding quotes from
     `terminal.string!!.text` → `fmt`.
  3. `return if (fmt.startsWith("3.1")) RockspecSchemaLoader.v31 else RockspecSchemaLoader.v30`.
  4. Absent/non-string `rockspec_format` → `v30` (default).

## 4. External Data & Parsing

### 4.1 JSON-schema → `RockspecSchema` interpreter (`RockspecSchemaLoader.load`)
- **Format**: draft-07 JSON ([rockspec-schema-v30.json:1](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json)).
  The interpreter consumes **only** this bounded subset (everything else is ignored):
  `type` (string), `description` (string), `properties` (object), `required` (string array),
  `additionalProperties` (boolean — schema-valued forms are treated as `closed=false`),
  `$ref` (string, local `#/definitions/<name>` only), `oneOf` (array; see below), `items` (ignored —
  arrays collapse to `SchemaKind.ARRAY`).
- **Parse strategy** (Gson):
  ```kotlin
  private fun load(resource: String): RockspecSchema {
      val root = JsonParser.parseReader(InputStreamReader(stream(resource), UTF_8)).asJsonObject
      val defs = root.getAsJsonObject("definitions") ?: JsonObject()
      return RockspecSchema(toNode(root, defs, depth = 0))
  }
  // toNode resolves a single $ref against defs, maps "type" -> SchemaKind, reads description,
  // and (only for depth < 2 objects) recurses into "properties"/"required"/"additionalProperties".
  ```
- **`type` → `SchemaKind`**: `"string"`→STRING, `"object"`→OBJECT, `"array"`→ARRAY, `"boolean"`→BOOLEAN,
  absent/other→ANY.
- **`$ref`**: `"#/definitions/source"` → look up `defs["source"]` and interpret that node in place
  (single hop; `source`/`build`/`dependencies` are the only refs used —
  [rockspec-schema-v30.json:92-95,126](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json)).
- **`oneOf`** (used by `dependencies`
  [rockspec-schema-v30.json:128](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json)):
  collapse to `SchemaKind.ANY` (a dependencies value may be array **or** object — both are Lua tables, and
  value-kind §3.3 treats a table as acceptable for ANY). No deeper modelling.
- **Depth bound**: `toNode` materialises `properties` for the root (depth 0) and its **direct** object
  children (depth 1). At depth ≥ 2, object nodes are emitted as `SchemaKind.OBJECT` with empty
  `properties` and `closed=false` (so no unknown-key checks fire below the first nesting level —
  matches Out of Scope).
- **Closed flag**: `closed = additionalProperties == false`. Root is closed
  ([rockspec-schema-v30.json:12](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json));
  `build` is open ([:315](../../../../src/main/resources/jsonschema/rockspec-schema-v30.json)); `description`,
  `source`, `hooks`, `deploy` have no `additionalProperties` key → JSON-schema default is *open*, BUT the
  rockspec model treats "no `additionalProperties`" on these four as **closed** (they enumerate a fixed,
  known key set and we want typo detection). This single deviation is encoded as: a depth-1 object with a
  non-empty `properties` and no explicit `additionalProperties:true` is `closed=true`. `build`'s explicit
  `additionalProperties:true` keeps it open. (Recorded as risks-and-gaps Gap 2.1.)
- **Failure handling**: a malformed/absent resource or parse error → exception; the loader logs once and
  the inspection degrades to "no schema" (returns `EMPTY_VISITOR`). Never throws into the platform.

## 5. Data Flow

### Example 1: unknown top-level key (TC #2)
1. User types `pkg_name = "foo"` in `foo-1.0-1.rockspec`.
2. `RockspecSchemaInspection.buildVisitor`: `isRockspec` true; `schemaFor` → v3.0.
3. Visitor on the `LuaAssignmentStatement`: `key = "pkg_name"`, `parent = root` (closed),
   `"pkg_name" !in root.properties` → WARNING on the `nameRef` (§3.1).

### Example 2: nested typo (TC #3)
1. `description = { sumary = "x" }`.
2. Top-level key `description` → known closed object node.
3. RHS is a `LuaTableConstructor`; iterate fields; `sumary !in description.properties`
   (`{summary, detailed, homepage, license, maintainer, labels, issues_url}`) → WARNING on the
   nested identifier (§3.1 nested branch).

### Example 3: version selection (TC #10/#11)
1. `RockspecSchemaLoader.schemaFor`: reads `rockspec_format`. `"3.1"` → `v31` (which has `test`);
   `test = {}` is a known key → no warning. Absent → `v30` (no `test`) → unknown-key warning.

### Example 4: completion inside a nested table (TC #9)
1. Caret in `description = { <caret> }`.
2. §3.4: nearest enclosing `LuaTableConstructor` is `description`'s RHS → node = description;
   offer its keys minus present → `summary`, `detailed`, … added as lookups.

## 6. Edge Cases
- **Plain `.lua` file**: `isRockspec` false → `EMPTY_VISITOR` / no completions / no doc (TC #1).
- **Multi-assign at file scope** (`a, b = 1, 2`): `topLevelAssignments` pairs positionally; unusual in
  rockspecs but handled without exception.
- **Non-literal RHS** (`version = VER .. "-1"`): `luaKind == UNKNOWN` → never flagged (TC #7).
- **`build` open object**: unknown keys inside `build` are never flagged (TC #4); only `build` itself
  (must be a table) and—optionally—its required `type` are subject to §3.3/§3.2-style checks at depth 1
  if `build` is modelled closed=false (it is), so no nested unknown-key pass runs for `build`.
- **Duplicate top-level key**: each visited assignment is checked independently; required-key scan still
  emits once.
- **Missing schema resource**: degrade to no markers; one log line.
- **`rockspec_format` as a non-string** (number/ref): treated as absent → v3.0.

## 7. Integration Points (plugin.xml)
Add to [plugin.xml](../../../../src/main/resources/META-INF/plugin.xml) `<extensions defaultExtensionNs="com.intellij">`:
```xml
<localInspection
        language="Lua"
        shortName="RockspecSchema"
        displayName="Rockspec schema validation"
        groupName="LuaRocks"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="net.internetisalie.lunar.rocks.editor.RockspecSchemaInspection"/>

<completion.contributor
        language="Lua"
        implementationClass="net.internetisalie.lunar.rocks.editor.RockspecCompletionContributor"/>

<platform.backend.documentation.targetProvider
        implementation="net.internetisalie.lunar.rocks.editor.RockspecDocumentationTargetProvider"/>
```
- The `localInspection` mirrors the attributes of `LuaUndeclaredVariable`
  ([plugin.xml:150-155](../../../../src/main/resources/META-INF/plugin.xml)) with a new `groupName="LuaRocks"`.
- `RockspecSchema` / `RockspecSchemaLoader` / `RockspecFileSupport` /
  `RockspecAddRequiredFieldsQuickFix` are plain classes/objects — **no** `plugin.xml` entry.
- No new file type, language, index, settings key, or action is added.

## 8. Testing Strategy
- `RockspecSchemaLoaderTest` (pure): asserts v3.0/v3.1 parse → root closed; **per-version required set**
  — v3.0 `requiredTopLevel == {package,version,source,build}`, v3.1 `requiredTopLevel ==
  {package,source,version}` and explicitly `"build" !in v31.requiredTopLevel` (regression-guards the
  v3.0/v3.1 divergence); `description` closed with its 7 keys, `build` open, v3.1 has top-level `test`.
- `RockspecSchemaInspectionTest` (`BasePlatformTestCase`): `myFixture.configureByText("x-1.0-1.rockspec",
  …)` + `myFixture.enableInspections(RockspecSchemaInspection())` + `doHighlighting()` /
  `checkHighlighting()` with `<warning …>` markers — TC #2-#7, #10-#12. Includes a `test.lua` negative
  (TC #1).
- `RockspecCompletionTest` (`BasePlatformTestCase`): `configureByText` + `completeBasic()` +
  `lookupElementStrings` — TC #8-#9.
- Patterns mirror `LuaUndeclaredVariableInspectionTest`
  ([LuaUndeclaredVariableInspectionTest.kt:11](../../../../src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaUndeclaredVariableInspectionTest.kt))
  and `LuaCompletionTest`
  ([LuaCompletionTest.kt:9](../../../../src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaCompletionTest.kt)).

## 9. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-13-01 File guard | M | §2.3 |
| ROCKS-13-02 Schema model & loader | M | §2.1, §2.2, §4.1 |
| ROCKS-13-03 Unknown-key validation | M | §2.4, §3.1 |
| ROCKS-13-04 Missing-required validation | M | §2.4, §3.2 |
| ROCKS-13-05 Value-kind validation | M | §2.4, §3.3 |
| ROCKS-13-06 Key completion | S | §2.5, §3.4 |
| ROCKS-13-07 Hover documentation | S | §2.6 |
| ROCKS-13-08 Add-required-field quick fix | S | §2.7, §3.5 |
| ROCKS-13-09 `rockspec_format` version selection | S | §2.2, §3.6 |

## 10. Alternatives Considered
- **Type-engine `_ENV` mapping** (`@class Rockspec` via the indexed LuaCATS meta-files): rejected for
  v1. It needs a new file-type-scoped implicit-`_ENV`/global-type override on `LuaScopeProcessor` /
  `LuaFile.processDeclarations` ([LuaFile.kt:41-76](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaFile.kt)),
  which does not exist; the meta-files also infer a class as a **union** (AGENTS.md type-engine lesson),
  complicating member resolution. The schema-driven path reuses existing extension points and is fully
  specified here. (The meta-file indexing route remains an optional follow-up — risks-and-gaps Technical
  Debt.)
- **IntelliJ JSON Schema engine** (`JsonSchemaProviderFactory`): rejected — it validates the JSON/YAML
  PSI, not Lua PSI; a `.rockspec` never maps to a JSON tree, so the engine cannot see it.
- **Annotator instead of inspection**: rejected for the main validation — an inspection is toggleable,
  groups under Settings, and carries quick fixes (ROCKS-13-08) more naturally; the repo uses both, but
  `LuaGlobalCreationInspection` is the closest precedent.
- **Hand-authored Kotlin schema model** (no JSON load): rejected — it would duplicate the bundled JSON
  schema and drift from it. Loading the JSON keeps a single source of truth; the bounded interpreter
  (§4.1) keeps the parse mechanical.

## 11. Open Questions
_None — feature has cleared the planning bar; the one schema-semantics decision (the four no-additionalProperties nested objects treated as closed) is resolved and tracked in risks-and-gaps Gap 2.1._
