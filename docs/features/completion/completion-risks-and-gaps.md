---
id: "COMP-RISKS"
title: "00: Design Gaps & De-risking"
type: "risk"
parent_id: "COMP"
priority: "high"
folders:
  - "[[features/completion/requirements|requirements]]"
---

# Risks & Design Gaps: COMP Epic

This document tracks technical risks and design gaps for the Code Completion (`COMP`) epic.

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `COMP-R-01` | **Broken PSI Tree** | High | Incomplete code often leads to malformed PSI. Completion must handle invalid trees gracefully by checking token types. |
| `COMP-R-02` | **Performance** | Medium | Large files or complex type hierarchies can slow down completion. Use caching and background computation where possible. |
| `COMP-R-03` | **Language Versions** | Low | Keywords change between Lua versions (e.g., `goto` in 5.2). Ensure completion respects the project's `LuaLanguageLevel`. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `COMP-G-01` | **LuaCATS Parsing** | How to suggest members from LuaCATS annotations when the PSI isn't fully resolved? | **Resolved**: Enhanced `LuaTypeGraphBridge` to recognize `@class` and implemented robust receiver identification in `LuaCompletionContributor`. |
| `COMP-G-02` | **Priority Ranking** | How to balance keywords vs. symbols vs. cross-file symbols? | `COMP-DR-02`: Define a weighting strategy for different completion types. |

## De-risking Tasks (DR)

- [ ] `COMP-DR-01`: Prototype a simple member completion from a `LuaCATS` annotated table.
- [ ] `COMP-DR-02`: Evaluate standard IntelliJ sorting/grouping for completion results.

## Known / Deferred Defects

| ID | Defect | Severity | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `COMP-D-01` | **`lo` prefix completion dropout** | Medium | Deferred | Typing `l` lists `local`, `lo` drops it, and `loc` lists it again — completion intermittently fails at a two-character keyword prefix. A fix was attempted but did not resolve the behaviour in the live IDE. Deferred until the COMP epic is otherwise complete. Reproducer: `LuaCompletionBugTest` (currently print-only / assertion-free, so it does not fail CI). |
