---
id: FORMAT-05-DESIGN
title: "Technical Design"
type: design
parent_id: FORMAT-05
status: "done"
priority: "medium"
folders:
  - "[[features/formatting/05-alignment-logic/requirements|requirements]]"
---

# Technical Design: FORMAT-05 Alignment Logic

## 1. Architecture Overview

### Current State
`LuaFormatBlock.addChildBlocks` already creates a shared `Alignment` for the items of a
`NAME_LIST`/`EXPR_LIST` (`LuaFormatBlock.kt:50-56`) — proving the alignment mechanism works
here. There is no alignment of `=` across consecutive statements or table fields, and no setting.

### Target State
For a contiguous run of assignment statements (and for the fields of a table constructor),
share one `Alignment` on the `ASSIGN` token so the `=` line up; gated by new settings.

## 2. Core Components

### 2.1 `LuaCodeStyleSettings` (add fields)
```kotlin
@JvmField var ALIGN_CONSECUTIVE_ASSIGNMENTS: Boolean = false
@JvmField var ALIGN_TABLE_FIELDS: Boolean = false
```

### 2.2 `LuaFormatBlock` (modify) — alignment groups
- When building children of a `BLOCK`, group **maximal runs** of adjacent
  `LuaAssignmentStatement`/`LuaLocalVarDecl` (no blank line / non-assignment between them) and
  give each statement's `ASSIGN` child the **same** `Alignment` (one per run). When building a
  `TABLE_CONSTRUCTOR`, give each `FIELD`'s `ASSIGN` the same `Alignment` (one per constructor).
- Alignment objects use `Alignment.createAlignment(true)` (align by width) so the `=` columns
  line up.

## 3. Algorithms

### 3.1 Consecutive-assignment grouping (FORMAT-05-01)
- **Input**: a `BLOCK` node's statement children.
- **Steps**:
  1. If `!luaSettings.ALIGN_CONSECUTIVE_ASSIGNMENTS` → no alignment.
  2. Walk statements; start a new run at each `LuaAssignmentStatement`/`LuaLocalVarDecl`; end the
     run at the first statement that is neither (or at a blank-line gap detected via the
     statements' line numbers).
  3. For each run with ≥2 members, create one `Alignment.createAlignment(true)`; tag every
     member statement's `ASSIGN` leaf block with it.
- **Result**: the platform pads before each `=` so the columns align.

### 3.2 Table-field alignment (FORMAT-05-02)
- If `ALIGN_TABLE_FIELDS`, within a `TABLE_CONSTRUCTOR` create one alignment and tag each
  `FIELD`'s `ASSIGN` leaf with it.

### 3.3 Settings UI
- `ALIGNMENT_SETTINGS` (or the wrapping tab): `consumer.showCustomOption(LuaCodeStyleSettings,
  "ALIGN_CONSECUTIVE_ASSIGNMENTS", …)` and `"ALIGN_TABLE_FIELDS"`.

## 4. External Data & Parsing
None.

## 5. Data Flow
Reformat a run `x = 1 / abc = 2` with the setting on → both `ASSIGN` blocks share an alignment →
the formatter pads `x` so its `=` aligns under `abc`'s `=`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Single assignment | run of 1 → no alignment. |
| Blank line breaks the run | detected by line gap → separate alignment groups. |
| Multi-target `a, b = …` | aligns the single `ASSIGN` of the statement (the `=`), not each var. |
| Setting off | no alignment objects created (default). |

## 7. Integration Points
- No new EP — extends `LuaFormatBlock` + `LuaCodeStyleSettings` + the settings UI.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| FORMAT-05-01 Align assignments | S | §2.2, §3.1 |
| FORMAT-05-02 Align table fields | S | §2.2, §3.2 |
| FORMAT-05-03 Configurable | S | §2.1, §3.3 |

## 9. Alternatives Considered
- **Per-run `Alignment` vs file-wide**: per-run keeps unrelated assignment groups independent,
  matching common style tools (and avoiding over-padding across the whole file).

## 10. Open Questions

_None — feature has cleared the planning bar._
