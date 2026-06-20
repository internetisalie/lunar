---
id: "COMP-05"
title: "05: Parameter Name Hints"
type: "feature"
status: "done"
priority: "medium"
parent_id: "COMP"
folders:
  - "[[features/completion/requirements|requirements]]"
---

# COMP-05: Parameter Name Hints

Show inlay hints for parameter names at call sites. When calling a function like
`move(10, 20)`, the IDE displays `posX:` before the first argument and `posY:`
before the second, making it clear which argument maps to which parameter.

## Overview

Parameter name hints reduce the cognitive load of reading function calls with
positional arguments. Lua does not have named-argument syntax, so call sites
like `move(10, 20)` provide no indication of what `10` or `20` means. The inlay
hint injects `posX:` and `posY:` as non-editable annotations, sourced from the
callee's parameter names (declared in the function signature or `---@param`
LuaCATS annotations).

The feature is implemented as a declarative `InlayHintsProvider` registered in
`plugin.xml` under the `PARAMETERS_GROUP` group, and shares the same
large-file threshold as the other Lunar inlay hint providers.

## Scope

### In Scope
- Function call argument → parameter name mapping for `func(args)` call syntax.
- Colon method calls (`obj:method(args)`) — implicit `self` is suppressed, parameter
  names start from the first explicit parameter.
- Both function declarations (`function foo(x, y)`) and local function declarations
  (`local function foo(x, y)`).
- LuaCATS `@param` tags — when present, the annotated name is used instead of
  the signature name (e.g. `---@param speed number` on `function foo(s)` shows
  `speed:` not `s:`).
- String arguments (`func "string"`) and table-constructor arguments
  (`func { a = 1 }`).
- Anonymous function expressions (`local f = function(x, y) end`).
- Resolution through the type engine — method calls resolved from receiver types.

### Out of Scope
- Parameter name hints for built-in / standard-library functions (no parameter
  names available in the type graph for standard library stubs).
- Vararg parameters (`...`) — no hint is shown for the vararg itself.
- Hints for calls with 0 or 1 effective arguments (suppressed to avoid noise).
- Parameter types — the hint shows only the parameter name, not the type
  (unlike local-variable type hints which show `: number`).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| COMP-05-01 | **Parameter Name Display** | M | Show `paramName:` as an inlay hint before each argument at a function call site when the callee has multiple parameters. |
| COMP-05-02 | **Colon-Call Self Suppression** | M | For colon method calls (`obj:method(args)`), suppress the `self` parameter — hints start from the second declared parameter. |
| COMP-05-03 | **LuaCATS @param Name Preference** | M | When the callee has `---@param` tags with names different from the signature, use the annotated name. |
| COMP-05-04 | **Name-Matching Suppression** | M | Suppress the hint when the argument is a simple identifier whose text matches the parameter name (e.g. `move(posX, posY)`). |
| COMP-05-05 | **Single-Parameter Suppression** | M | Suppress all hints when the callee has 0 or 1 effective parameters (after self-stripping). |
| COMP-05-06 | **Single-Char / Special Name Suppression** | M | Suppress hints for parameter names of length ≤1, or named `_` or `p` (obvious positional names). |
| COMP-05-07 | **Large-File Threshold** | S | Disable all parameter hints when the file exceeds the configured line-count threshold (`LuaInlayHintsSettings.largeFileThreshold`, default 10,000 lines). |
| COMP-05-08 | **Toggle via IDE Settings** | M | Allow enabling/disabling parameter hints via the IDE's inlay hints settings panel (provider ID `lua.parameter.hints`). |
| COMP-05-09 | **String and Table Args** | S | Show hints for single string arguments (`func "str"`) and single table constructor arguments (`func { k = v }`). |

## Detailed Specifications

### COMP-05-01: Parameter Name Display

When the IDE encounters a `LuaFuncCall` PSI element, it resolves the callee's
function type (via the type engine or reference resolution). If the function has
`N` parameters where `N > 1` (after stripping `self` for colon calls), a hint
`paramName:` is displayed before each of the first `N` arguments.

Arguments are collected from the call's `LuaArgs`:
- `args.exprList.exprList` for normal argument lists.
- `args.string` for single-string-arg calls.
- `args.tableConstructor` for single-table-arg calls.

The hint is positioned at the argument's `textRange.startOffset` using
`InlineInlayPosition(offset, true)`.

### COMP-05-02: Colon-Call Self Suppression

When the call uses method syntax (`obj:method(args)`), the first parameter of
the function is `self`. The hint for `self` is suppressed:
- If the first declared parameter is named `"self"`, drop it.
- Otherwise, if the method's parameter count exceeds the argument count by 1,
  drop the first parameter (heuristic for inferred `self`).

### COMP-05-03: LuaCATS @param Name Preference

When a function declaration has `---@param <name> <type>` annotations, the
type engine's `LuaFunctionType` already carries those names in its `params`
list. The provider reads `functionType.params` and uses `param.name` directly
— no separate LuaCATS parsing at the hint level.

### COMP-05-04: Name-Matching Suppression

The function `shouldShowHint(paramName, argExpr)` checks:
1. If `paramName` is too short (≤1 char) or is `_` or `p` → suppress.
2. If `argExpr` is a `LuaNameRef` whose `text` equals `paramName` → suppress.
3. If `argExpr` is a `LuaExpr` and unwrapping it yields a `LuaNameRef` whose
   `text` equals `paramName` → suppress.

This prevents redundant hints like `move(posX: posX, posY: posY)` when the
caller passes identically-named variables.

### COMP-05-05: Single-Parameter Suppression

If `effectiveParams.size <= 1`, the provider returns early with no hints.
This avoids showing a hint for calls like `log(message)` where the single
argument is already obvious.

### COMP-05-06: Single-Char / Special Name Suppression

The suppression logic in `shouldShowHint` checks:
```
paramName.length <= 1 || paramName == "_" || paramName == "p"
```

### COMP-05-07: Large-File Threshold

At the start of `createCollector`, the provider reads the document's line count
and compares against `LuaInlayHintsSettings.instance.state.largeFileThreshold`
(default 10000). If exceeded, `createCollector` returns `null`, and no hints
are shown for any provider in that file.

## Behavior Rules

1. **Resolution order**: For colon calls, resolve the receiver type →
   `resolveMember(methodName)` → use `LuaFunctionType.params`. For direct calls,
   resolve the callee graph type → `graphTypeToLuaType() as? LuaFunctionType`.
   If the type engine fails to resolve, fall back to reference resolution.

2. **Hint ordering**: Hints are emitted per argument position, in order.
   `for (i in 0 until minOf(argExprs.size, effectiveParams.size))`.

3. **Threading**: Hints are collected during the inlay pass on the EDT (inlay
   pass infrastructure handles read-action wrapping). No background work.

4. **Interference with other hints**: Parameter hints share the file with
   type hints and method-chain hints. All three pass in `SharedBypassCollector`.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | COMP-05-01 | `local function move(posX, posY) end; move(10, 20)` | Inlay pass runs | `move(posX:10, posY:20)` in hints |
| 2 | COMP-05-02 | `function obj:method(value) end; obj:method(5)` | Inlay pass runs | No hint (single effective param after self strip) |
| 3 | COMP-05-02 | `function obj:move(posX, posY) end; obj:move(10, 20)` | Inlay pass runs | `obj:move(posX:10, posY:20)` in hints |
| 4 | COMP-05-03 | `---@param speed number; ---@param force number; local function apply(s, f) end; apply(10, 20)` | Inlay pass runs | `apply(speed:10, force:20)` |
| 5 | COMP-05-04 | `local posX, posY = 1, 2; move(posX, posY)` with `function move(posX, posY) end` | Inlay pass runs | No parameter hints (name matches) |
| 6 | COMP-05-05 | `local function log(message) end; log("hello")` | Inlay pass runs | No hint |
| 7 | COMP-05-07 | File with 12,000 lines, threshold at 10,000 | Inlay pass runs | No hints from any provider |
| 8 | COMP-05-08 | Enable/disable via Settings → Editor → Inlay Hints → Lua | Toggle checkbox | Hints appear/disappear accordingly |
| 9 | COMP-05-06 | `function move(a, b) end; move(1, 2)` | Inlay pass runs | No hints (single-char params) |
| 10 | COMP-05-09 | `function log(msg) end; log "hello"` | Inlay pass runs | `log(msg:"hello")` (if msg not suppressed) |

## Acceptance Criteria

- [x] `LuaParameterInlayHintsProvider` registered in `plugin.xml` (field: `COMP-05-01–06, 08–09`)
- [x] Parameter hints appear for multi-param function calls (field: `COMP-05-01`)
- [x] Colon calls suppress `self` hint (field: `COMP-05-02`)
- [x] LuaCATS `@param` names override signature names (field: `COMP-05-03`)
- [x] Name-matching, single-param, and short-name heuristics suppress hints appropriately (field: `COMP-05-04–06`)
- [x] Large-file threshold respected (field: `COMP-05-07`)
- [x] Provider toggleable via IDE settings (field: `COMP-05-08`)
- [x] String and table constructor arguments supported (field: `COMP-05-09`)
- [x] `LuaParameterInlayHintsTest` (6 tests) all green in `./gradlew test`

## Non-Functional Requirements

- **EDT-only**: Inlay hints are collected on the EDT via the declarative inlay
  pass; no background work or coroutines needed.
- **Memory**: No retained `Project`/`Editor`/`PsiFile` references — the
  collector uses the file/editor passed to `createCollector` only within that
  method scope (per the `SharedBypassCollector` contract).
- **Performance**: Large-file threshold prevents expensive analysis on very
  large files. Resolution via `LuaTypesSnapshot.forFile(file)` is file-scoped
  and reuses cached snapshots.

## Dependencies

- `TYPE` epic — type engine (`LuaTypesVisitor.getTypes`,
  `LuaGraphType.graphTypeToLuaType`, `LuaType.resolveMember`).
- `COMP-02` (Basic Symbol Completion) — reference resolution fallback uses the
  same `resolve()` path.
- `LuaInlayHintsSettings` for large-file threshold.

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)