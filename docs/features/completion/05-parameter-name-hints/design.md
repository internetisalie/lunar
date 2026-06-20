---
id: "COMP-05-DESIGN"
title: "Technical Design"
type: "design"
status: "done"
parent_id: "COMP-05"
folders:
  - "[[features/completion/05-parameter-name-hints/requirements|requirements]]"
---

# Technical Design: COMP-05 — Parameter Name Hints

## 1. Architecture Overview

### Current State

The feature is fully implemented and integrated. The `LuaParameterInlayHintsProvider`
(committed to `src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/`)
implements `com.intellij.codeInsight.hints.declarative.InlayHintsProvider` and
is registered as a declarative inlay provider in `plugin.xml` alongside
type hints and method-chain hints.

### Prior Art in This Repo

The provider is part of Lunar's inlay hints subsystem, which includes three
providers sharing common infrastructure:

- `LuaTypeInlayHintProvider` (`src: main/kotlin/.../hint/LuaTypeInlayHintProvider.kt`)
  — local variable type hints and return type hints. **SHARES UTILITIES** with
  the parameter hints provider: `shouldShowHint()`, `unwrapExpression()`, and
  the `LuaInlayHintsSettings` threshold.
- `LuaMethodChainInlayHintProvider`
  (`src: main/kotlin/.../hint/LuaMethodChainInlayHintProvider.kt`)
  — method-chain return-type hints. Independent but operates on the same
  `LuaFuncCall` PSI nodes; uses a separate `InlayHintsProvider` registration.
- `LuaParameterInfoHandler` (`src: main/kotlin/.../hint/LuaParameterInfoHandler.kt`)
  — the popup parameter info (Ctrl+P) handler. **INDEPENDENT** — resolves
  parameters through reference resolution and LuaCATS directly, not the type
  engine. Does not share logic with the inlay hints provider.

Search terms used: `grep` for `InlayHints`, `InlayHintsProvider`,
`parameterInfo` across `src/main/` and `plugin.xml`; verified PSI types against
`src/main/gen/.../lang/psi/*.java`.

### Target State

A single `InlayHintsProvider` registered in `plugin.xml` under
`codeInsight.declarativeInlayProvider` with:
- Provider ID: `lua.parameter.hints`
- Group: `PARAMETERS_GROUP`
- Implementation: `LuaParameterInlayHintsProvider`
- Enabled by default

The provider resolves function types from the Lunar type engine, maps argument
positions to parameter names, and emits inlay annotations using the declarative
hints API (`InlayTreeSink`, `InlineInlayPosition`, `HintFormat.default`).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.insight.hint.LuaParameterInlayHintsProvider`

- **Responsibility**: Collect parameter-name inlay hints for `LuaFuncCall` PSI nodes.
- **Threading**: EDT (inlay pass infrastructure — the declarative inlay hints
  pass runs on the EDT; PSI access happens within the platform's read-action
  scope that the pass setup provides).
- **Collaborators**:
  - `LuaTypesVisitor.getTypes(element): LuaTypes` (Lunar type engine) — `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt`
  - `LuaTypeInlayHintProvider.unwrapExpression()` — shared utility
  - `LuaTypeInlayHintProvider.shouldShowHint()` — shared suppression logic
  - `LuaGraphType.graphTypeToLuaType(): LuaType` — type resolution
  - `LuaType.resolveMember(name): LuaTypeMember?` — method resolution
  - `LuaTypesSnapshot.forFile(file): LuaTypes` — fallback type resolution (used for non-type-engine resolution of `LuaFuncDecl`/`LuaLocalFuncDecl`)
  - `LuaInlayHintsSettings.instance.state.largeFileThreshold: Int` — file-size gating
  - IntelliJ Platform: `InlayHintsProvider`, `InlayHintsCollector`,
    `SharedBypassCollector`, `InlayTreeSink`,
    `InlineInlayPosition`, `HintFormat`

- **Key API**:
  ```kotlin
  class LuaParameterInlayHintsProvider : InlayHintsProvider {
      companion object {
          const val PROVIDER_ID = "lua.parameter.hints"
      }

      override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector?
      // Returns a SharedBypassCollector that runs collectFromElement for
      // every PsiElement reachable from the file's PSI tree.
      // If the file exceeds LuaInlayHintsSettings.largeFileThreshold, returns null.

      // Inside the collector:
      // override fun collectFromElement(element: PsiElement, sink: InlayTreeSink)
      //   If element is LuaFuncCall, delegates to collectParameterHints().
      //   Otherwise no-op.

      // private fun collectParameterHints(element: LuaFuncCall, sink: InlayTreeSink)
      //   1. Get types snapshot: LuaTypesVisitor.getTypes(element)
      //   2. Unwrap the callee expression (strip parens/whitespace):
      //      LuaTypeInlayHintProvider.unwrapExpression(element.varOrExp)
      //   3. Get the first LuaNameAndArgs (element.nameAndArgsList.firstOrNull())
      //   4. If methodExpr != null (colon call), resolve via receiver type:
      //      a. getValueType(callee) → graphTypeToLuaType → resolveMember(methodName)
      //      b. If member.type is LuaFunctionType, use it directly
      //      c. Else fall back to reference resolution on methodExpr.nameRef
      //   5. Else (direct call), resolve callee type:
      //      a. getValueType(callee) → graphTypeToLuaType, check if LuaFunctionType
      //      b. Else fall back to reference resolution
      //   6. Extract args from LuaArgs (string / exprList / tableConstructor)
      //   7. Strip self for colon calls (see §3.1)
      //   8. If effectiveParams.size <= 1, return
      //   9. For i in 0..min(argSize, paramSize)-1:
      //      a. if shouldShowHint(paramName, argExpr): emit hint
      //   10. Emit: sink.addPresentation(
      //         InlineInlayPosition(argExpr.textRange.startOffset, true),
      //         null, null, HintFormat.default
      //       ) { text("$paramName:") }
  }
  ```

### 2.2 Shared Utilities (reused from `LuaTypeInlayHintProvider`)

#### `unwrapExpression(expr: PsiElement?): PsiElement?`
Walks up to 10 levels of single-child wrappers (skipping `PsiWhiteSpace` and
`PsiComment`) to find the real expression inside a `LuaVarOrExp` / `LuaExpr`.
Located in `LuaTypeInlayHintProvider.Companion`.

#### `shouldShowHint(paramName: String, argExpr: PsiElement): Boolean`
Suppression rules:
1. `paramName.length <= 1 || paramName == "_" || paramName == "p"` → false.
2. `argExpr is LuaNameRef && argExpr.text == paramName` → false.
3. `argExpr is LuaExpr` → unwrap → if `LuaNameRef` with matching text → false.
4. Otherwise → true.

### 2.3 `net.internetisalie.lunar.lang.insight.hint.LuaInlayHintsSettings`

- **Responsibility**: Application-level persistent settings for inlay hints.
- **Storage**: `lunar_inlay_hints.xml` (XML-persisted via `@State`).
- **State class**:
  ```kotlin
  class State {
      var largeFileThreshold: Int = 10000
  }
  ```
- **Service accessor**: `LuaInlayHintsSettings.instance` (via
  `ApplicationManager.getApplication().getService(...)`).

- `@Service(Service.Level.APP)` — `src/main/kotlin/.../hint/LuaInlayHintsSettings.kt`
- Registered in `plugin.xml` as `<applicationService serviceImplementation="...LuaInlayHintsSettings"/>` (line 422).

### 2.4 `plugin.xml` Registration

```xml
<codeInsight.declarativeInlayProvider
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.hint.LuaParameterInlayHintsProvider"
        isInternal="false"
        isEnabledByDefault="true"
        group="PARAMETERS_GROUP"
        providerId="lua.parameter.hints"
        bundle="net.internetisalie.lunar.LuaBundle"
        nameKey="lua.type.hints.parameter.name"/>
```

Bundle entries (from `LuaBundle.properties` lines 126–127):
```properties
lua.type.hints.parameter.name=Parameter names
lua.type.hints.parameter.name.desc=Show parameter names at call sites
```

## 3. Algorithms

### 3.1 Colon-Call Self Stripping

- **Input**: `params: List<LuaParameter>`, `isColonCall: Boolean`, `argExprs.size: Int`
- **Output**: `effectiveParams: List<LuaParameter>` to align with arguments.

- **Steps**:
  1. If `!isColonCall` → `effectiveParams = params`
  2. If `isColonCall && params.isNotEmpty() && params[0].name == "self"` →
     `effectiveParams = params.drop(1)`
  3. Else if `isColonCall && params.size > argExprs.size` →
     `effectiveParams = params.drop(1)` (heuristic: implicit self)
  4. Else → `effectiveParams = params`

- **Rationale**: Colon-call syntax passes `self` implicitly. The first declared
  parameter should not get a hint because no argument corresponds to it. The
  heuristic (step 3) catches cases where the type engine has not labeled the
  first param as `self` but the parameter count still suggests it.

### 3.2 Function Type Resolution (Two-Path Fallback)

- **Input**: `element: LuaFuncCall`
- **Output**: `LuaFunctionType?` with resolved parameter names.

- **Path A — Type engine (colon calls)**:
  1. `receiverGraphType = types.getValueType(callee)`
  2. `receiverType = types.graphTypeToLuaType(receiverGraphType)`
  3. `member = receiverType.resolveMember(methodName)` (see AGENTS.md: methods are class members)
  4. If `member.type is LuaFunctionType` → use it.

- **Path B — Type engine (direct calls)**:
  1. `calleeGraphType = types.getValueType(callee)`
  2. `t = types.graphTypeToLuaType(calleeGraphType) as? LuaFunctionType`
  3. If `t != null` → use it.

- **Path C — Reference resolution fallback (both call types)**:
  1. For direct calls: `(callee as? LuaNameRef)?.reference?.resolve()` → navigate to
     the declaration (`LuaLocalFuncDecl`, `LuaFuncDecl`, or `LuaFuncDecl` as
     grandparent of the resolved element).
  2. For colon calls: `methodExpr.nameRef?.reference?.resolve()` →
     `resolved.parent.parent as? LuaFuncDecl`.
  3. Load the declaration file's types snapshot: `LuaTypesSnapshot.forFile(declFile)`
  4. Get function type: `declTypes.graphTypeToLuaType(declTypes.getValueType(funcDecl)) as? LuaFunctionType`

- **Complexity**: The dual-path design exists because the type engine's
  `LuaTypesSnapshot` is file-scoped (computed on the file being edited), while
  the function declaration may be in a different file. Path C handles cross-file
  resolution by loading a snapshot for the declaration's file.

### 3.3 Argument Enumeration

- **Input**: `nameAndArgs.args: LuaArgs`
- **Output**: `List<PsiElement>` — the concrete argument expressions.

- **Steps**:
  1. If `args.string != null` → `listOf(args.string)`
  2. Else if `args.exprList != null` → `args.exprList.exprList` (returns `List<LuaExpr>`)
  3. Else if `args.tableConstructor != null` → `listOf(args.tableConstructor)`
  4. Else → `emptyList()`

- **Rationale**: Lua supports three argument forms: expression list, single
  string literal, and single table constructor. `LuaArgs` reflects this with
  three nullable fields.

### 3.4 Hint Positioning

- **Position**: `InlineInlayPosition(argExpr.textRange.startOffset, true)`
  — placed at the start of the argument expression, with `relatesToPrecedingText = true`
  so the hint appears before the argument.
- **Format**: `HintFormat.default` — renders as inline text in a subdued editor
  color.
- **Text**: `"$paramName:"` — the parameter name followed by a colon.

### 3.5 Large-File Gating

- **Check**: `editor.document.lineCount > settings.largeFileThreshold`
- **Action**: If exceeded, `createCollector()` returns `null`, preventing the
  provider from running any collection for that file. This is the same gate
  used by `LuaTypeInlayHintProvider` and `LuaMethodChainInlayHintProvider`.

## 4. External Data & Parsing

_None._ The provider consumes no CLI output, file contents, or network
responses. All data comes from the IntelliJ PSI tree and Lunar's type engine.

## 5. Data Flow

### Example 1: Direct function call with type engine resolution

```
Input PSI:  move(10, 20)  where move is local function move(posX, posY) end
Trigger:    Editor text change → declarative inlay hints pass

Flow:
1. collectFromElement fires on LuaFuncCall "move(10, 20)"
2. LuaTypesVisitor.getTypes(call) → LuaTypesSnapshot for the file
3. Unwrap varOrExp → LuaNameRef "move"
4. nameAndArgsList.first().methodExpr == null → direct call path
5. types.getValueType(LuaNameRef "move") → LuaGraphType.Function
6. types.graphTypeToLuaType(functionType) → LuaFunctionType(
     params = [LuaParameter("posX", ...), LuaParameter("posY", ...)],
     returnType = ...)
7. args.exprList.exprList → [LuaExpr "10", LuaExpr "20"]
8. Not colon call → effectiveParams = [posX, posY]
9. effectiveParams.size (2) > 1 → proceed
10. i=0: shouldShowHint("posX", "10") → true (10 is not a LuaNameRef)
    → emit hint "posX:" at offset of "10"
11. i=1: shouldShowHint("posY", "20") → true
    → emit hint "posY:" at offset of "20"

Output:    Editor shows posX:10, posY:20 (with "posX:" and "posY:" as inlays)
```

### Example 2: Colon method call with name-match suppression

```
Input PSI:  obj:move(posX, posY)  where function obj:move(posX, posY) end
Trigger:    Declarative inlay hints pass

Flow:
1. collectFromElement fires on LuaFuncCall
2. Unwrap varOrExp → "obj"
3. methodExpr != null → colon call path
4. getValueType(obj) → ... → graphTypeToLuaType → resolveMember("move")
   → LuaTypeMember("move", LuaFunctionType(params=[posX,posY]))
5. effectiveParams = [posX, posY] (first param is not "self" — or if it is,
   the drop logic strips it)
6. Args: [LuaExpr "posX", LuaExpr "posY"] (but these are LuaNameRefs)
7. i=0: shouldShowHint("posX", LuaNameRef text="posX") → text matches → false
8. i=1: shouldShowHint("posY", LuaNameRef text="posY") → text matches → false

Output:    No parameter hints (already clear from argument names)
```

### Example 3: LuaCATS @param overrides signature name

```
Input PSI:  apply(10, 20)
            ---@param speed number
            ---@param force number
            local function apply(s, f) end

Flow:
1. Type engine computes LuaFunctionType for "apply"
   → params = [LuaParameter("speed", ...), LuaParameter("force", ...)]
   (LuaCATS @param names override signature names in the type engine)
2. Args: [LuaExpr "10", LuaExpr "20"]
3. effectiveParams = [speed, force]
4. Both args pass shouldShowHint → hints emitted

Output:    Editor shows speed:10, force:20
```

## 6. Edge Cases

| Case | Handling |
|------|----------|
| Call with 0 arguments (`foo()`) | `argExprs.isEmpty()` → early return, no hints |
| Nested call `foo(bar(1), baz(2))` | Each `LuaFuncCall` is visited independently by `collectFromElement`; hints stack correctly |
| Method chain `a:m1():m2()` | Only the first `LuaNameAndArgs` is processed (`.firstOrNull()`). Hints only show for the first call segment. Method-chain hints are handled by `LuaMethodChainInlayHintProvider`. |
| Unresolvable callee (dynamic dispatch) | Type engine returns non-function type; reference fallback returns null → `functionType` is null → early return, no hints |
| Vararg function `function f(a, ...) end` | Vararg parameter is in `params` list; if it's the only effective param, suppresses all hints. If paired with named params, hint emitted for vararg param name too (typically `...` → suppressed by short-name rule). |
| Nested expressions as args `move(1+2, x or y)` | `shouldShowHint` handles `LuaExpr` by unwrapping; the expression itself doesn't match any param name → hint emitted |
| String arg `log("hello")` with single param `log(msg)` | Single effective param → suppressed (COMP-05-05). But `log "hello"` with multi-param function would show hint. |
| File exceeds threshold | `createCollector` returns `null` → no collection for any provider |
| Function in another file (stub-resolved) | Path C loads `LuaTypesSnapshot.forFile(declFile)` for the declaration's file |

## 7. Integration Points

```xml
<!-- plugin.xml — actual registration (lines 346-354) -->
<codeInsight.declarativeInlayProvider
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.hint.LuaParameterInlayHintsProvider"
        isInternal="false"
        isEnabledByDefault="true"
        group="PARAMETERS_GROUP"
        providerId="lua.parameter.hints"
        bundle="net.internetisalie.lunar.LuaBundle"
        nameKey="lua.type.hints.parameter.name"/>

<!-- Related: parameter info (Ctrl+P popup) — separate feature (lines 300-302) -->
<codeInsight.parameterInfo
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.hint.LuaParameterInfoHandler"/>

<!-- Shared custom settings provider (lines 356-359) -->
<codeInsight.declarativeInlayProviderCustomSettingsProvider
        language="Lua"
        providerId="lua.type.hints"
        implementationClass="net.internetisalie.lunar.lang.insight.hint.LuaInlayHintsCustomSettingsProvider"/>

<!-- Shared settings service (line 422) -->
<applicationService serviceImplementation="net.internetisalie.lunar.lang.insight.hint.LuaInlayHintsSettings"/>
```

Settings file: `lunar_inlay_hints.xml` (auto-created by `PersistentStateComponent`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| COMP-05-01 | M | §2.1 (collectParameterHints, hint emission loop), §5 (data flow examples) |
| COMP-05-02 | M | §3.1 (Colon-Call Self Stripping), §2.1 step 7 |
| COMP-05-03 | M | §2.1 step 4 (resolveMember / reference fallback returns LuaFunctionType with LuaCATS-influenced params), §5 example 3 |
| COMP-05-04 | M | §2.2 (shouldShowHint), §5 example 2 |
| COMP-05-05 | M | §2.1 step 8 (`effectiveParams.size <= 1` → return) |
| COMP-05-06 | M | §2.2 (shouldShowHint single-char/special name rules) |
| COMP-05-07 | S | §3.5 (Large-File Gating), §2.1 createCollector |
| COMP-05-08 | M | §2.4 (plugin.xml: isEnabledByDefault, providerId) |
| COMP-05-09 | S | §3.3 (Argument Enumeration — handles string and tableConstructor) |

## 9. Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| Extend `LuaParameterInfoHandler` to also emit inlay hints | Parameter info and inlay hints are fundamentally different UIs (popup vs inline) and use different extension points (`parameterInfo` vs `declarativeInlayProvider`). Merging would couple two unrelated subsystems. |
| Show hints for all parameters including single-param calls | Tested and rejected — too noisy. Single-param calls like `log(message)` have obvious meaning; a hint adds visual clutter. |
| Show hints for standard library functions | Parameter names for stdlib functions are not available in the type graph. Would require maintaining a separate mapping of function→paramNames. Deferred. |
| Use non-declarative (imperative) inlay hints | The declarative API (`InlayHintsProvider` + `InlayHintsCollector` + `InlayTreeSink`) integrates with the platform's settings UI, preview, and toggle infrastructure with zero extra code. |

## 10. Open Questions

_None — feature has cleared the planning bar._