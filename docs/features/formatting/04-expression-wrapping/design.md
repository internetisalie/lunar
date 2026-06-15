---
id: FORMAT-04-DESIGN
title: "Technical Design"
type: design
parent_id: FORMAT-04
status: "planned"
priority: "medium"
folders:
  - "[[features/formatting/04-expression-wrapping/requirements|requirements]]"
---

# Technical Design: FORMAT-04 Expression Wrapping

## 1. Architecture Overview

### Current State
`LuaFormatBlock` passes a single `Wrap.createWrap(WrapType.NONE, false)` to every block
(`LuaFormatBlock.kt:39`) and assigns no per-child wraps — so the formatter never wraps long
constructs. `LuaCodeStyleSettings` is empty (no wrap fields). Continuation indents already exist
(`EXPR_LIST → getContinuationIndent`, `TABLE_CONSTRUCTOR child → getNormalIndent`).

### Target State
Attach a shared `Wrap` to the children of `ARGS` and `TABLE_CONSTRUCTOR` (and `EXPR_LIST`) so
the platform chops them onto separate lines when the line exceeds `RIGHT_MARGIN`, governed by
new per-construct wrap settings.

## 2. Core Components

### 2.1 `LuaCodeStyleSettings` (add fields)
```kotlin
@JvmField var WRAP_ARGUMENTS: Int = CommonCodeStyleSettings.WRAP_AS_NEEDED
@JvmField var WRAP_TABLE_CONSTRUCTOR: Int = CommonCodeStyleSettings.WRAP_AS_NEEDED
// values: DO_NOT_WRAP | WRAP_AS_NEEDED | WRAP_ALWAYS (CommonCodeStyleSettings constants)
```

### 2.2 `LuaFormatBlock` (modify `addChildBlocks`)
- When `node.elementType == ARGS` (and its child is the arg `EXPR_LIST`) or
  `TABLE_CONSTRUCTOR`, create one shared `Wrap` for the item children:
  ```kotlin
  val wrap = when (node.elementType) {
      LuaElementTypes.ARGS -> Wrap.createWrap(wrapType(luaSettings.WRAP_ARGUMENTS), true)
      LuaElementTypes.TABLE_CONSTRUCTOR -> Wrap.createWrap(wrapType(luaSettings.WRAP_TABLE_CONSTRUCTOR), true)
      else -> null
  }
  ```
  Pass `wrap` to each item child's `LuaFormatBlock` (a new `wrap` ctor param replacing the
  hard-coded `WrapType.NONE`). `wrapType(int)` maps the setting:
  `DO_NOT_WRAP → NONE`, `WRAP_AS_NEEDED → CHOP_DOWN_IF_LONG`, `WRAP_ALWAYS → ALWAYS`.

### 2.3 `LuaLanguageCodeStyleSettingsProvider` (extend WRAPPING_AND_BRACES)
- Add `consumer.showCustomOption(LuaCodeStyleSettings::class.java, "WRAP_ARGUMENTS",
  "Call arguments", null, CodeStyleSettingsCustomizable.WRAP_OPTIONS,
  CodeStyleSettingsCustomizable.WRAP_VALUES)` and the same for `WRAP_TABLE_CONSTRUCTOR`.

## 3. Algorithms

### 3.1 Wrap assignment (FORMAT-04-01/02/03)
- In `addChildBlocks`, compute the construct `wrap` (§2.2) once per `ARGS`/`TABLE_CONSTRUCTOR`
  node and give the **same** `Wrap` instance to every item child (so they wrap together). The
  platform decides per `RIGHT_MARGIN`:
  - `CHOP_DOWN_IF_LONG`: if the whole construct fits, stay inline; else every item on its own
    line.
  - `NONE`: never wrap. `ALWAYS`: always one per line.
- The existing continuation indents (§1) place wrapped items correctly.

## 4. External Data & Parsing
None.

## 5. Data Flow
Reformat a long `f(a,b,c,…)` → the `ARGS` children share a `CHOP_DOWN_IF_LONG` wrap → exceeds
`RIGHT_MARGIN` → each arg on its own continuation-indented line.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Single argument | nothing to chop; stays inline. |
| Nested calls | each `ARGS` node has its own wrap; inner wraps independently. |
| `DO_NOT_WRAP` | wrap = NONE → never wrapped regardless of length. |

## 7. Integration Points
- No new EP — extends `LuaFormatBlock` (the registered `<lang.formatter>`) +
  `LuaCodeStyleSettings` + the wrapping settings UI.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| FORMAT-04-01 Wrap arguments | S | §2.2, §3.1 |
| FORMAT-04-02 Wrap table | S | §2.2, §3.1 |
| FORMAT-04-03 Configurable | S | §2.1, §2.3 |

## 9. Alternatives Considered
- **Shared `Wrap` per construct vs per-item independent wraps**: a shared `Wrap` makes the items
  chop down together (all-or-nothing), the standard IDE behaviour for argument lists.

## 10. Open Questions

_None — feature has cleared the planning bar._
