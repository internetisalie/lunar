---
id: "INSP-09-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "INSP-09"
---

# Implementation Plan: INSP-09 — Language Level Compliance

## Phase 1: Visitor Skeleton (Must)
- Create `LuaLanguageLevelInspection` (extending `LocalInspectionTool`, visitor `LuaVisitor`) and register in `plugin.xml` as an ERROR-level inspection.
- Wire level retrieval: `LuaProjectSettings.getInstance(project).state.languageLevel` (mirrors `LuaGlobalCreationInspection`).

## Phase 2: Implement Checks (Must)
- Override `visitAttribName` (attributes, 5.4), `visitBinOpExpr` + `visitUnOpExpr` (bitwise operators, 5.3 — filter by the `LuaTokenTypes` operator tokens `AMP/PIPE/NEG/BSL/BSR`), and `visitGotoStatement` (goto, 5.2).
- Gate each on `level < LuaLanguageLevel.LUA5x` and register the specific error messages when below threshold.
- Carry over the existing quick fixes (`UpgradeLanguageLevelFix`, `RemoveGotoFix`, `RemoveLabelFix`) as `LocalQuickFix`es on each problem.

## Phase 3: Replace the existing annotator (Must — mandatory)
- `LuaLanguageLevelAnnotator` already flags these exact constructs and is registered in `plugin.xml`. Delete it (or strip it to the reused quick-fix classes) and remove its `<annotator>` registration **in the same change** — otherwise every construct is double-reported.
- Reuse the existing fix classes rather than reimplementing them.
