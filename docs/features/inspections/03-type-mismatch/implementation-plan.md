---
id: "INSP-03-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "INSP-03"
---

# Implementation Plan: INSP-03 — Type Mismatch

## Phase 1: Visitor Skeleton (Must)
- Create `LuaTypeMismatchInspection` extending `LocalInspectionTool` (visitor `LuaVisitor`).
- Register in `plugin.xml`.
- Implement `buildVisitor` overriding `visitLocalVarDecl`.

## Phase 2: Type Resolution & Check (Must)
- Extract the expected type from the decl's `catsComment` (`typeTagList.first().argType.text`) and resolve it via `LuaTypeManager.resolveType(name, context)`.
- Infer the RHS actual type via the file snapshot: `LuaTypes.forFile(file).getValueType(expr)` → `graphTypeToLuaType(...)`.
- Compare with `actual.isAssignableTo(expected)` (short-circuit on unknown/any). Register problems formatted `"Type mismatch: expected '%s', got '%s'"`.

## Phase 3: Assignments and Returns (Should)
- Extend the visitor with `visitAssignmentStatement` and `visitFinalStatement`.
- Ensure enclosing-function resolution works for returns (`LuaFuncDecl` / `LuaLocalFuncDecl` / `LuaFuncDef`).
