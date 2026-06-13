---
id: "SYNTAX-17-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "SYNTAX-17"
status: "planned"
priority: "low"
folders:
  - "[[features/syntax/17-inferred-type-highlighting/requirements|requirements]]"
---

# Risks & Design Gaps: Inferred-Type Highlighting (SYNTAX-17)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `SYNTAX-17-R-01` | **Highlight latency** | Medium | Annotator runs on the platform's viewport-incremental background pass and reads the cached snapshot; no per-keystroke re-inference. Profile large files. |
| `SYNTAX-17-R-02` | **Color noise** | Low | Defaults inherit neutral platform colors; users can recolor/disable per key in the Color Scheme. Low-priority feature. |
| `SYNTAX-17-R-03` | **Overlap with scope annotators** | Low | `newSilentAnnotation` layers attributes; ensure the inferred keys are visually compatible with `LuaLocalBindingsAnnotator`/`LuaGlobalBindingsAnnotator` (verify in the color page). |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `SYNTAX-17-G-01` | **Enum/`@alias` highlighting** | SYNTAX-17-02 mentions `@enum`; v1 colors `@class` table types. `@alias`/enum coloring deferred. | `SYNTAX-17-DR-01` |
| `SYNTAX-17-G-02` | **Annotator ordering** | Whether the inferred-type colors should win over or yield to scope-based colors when both apply. | `SYNTAX-17-DR-02` |

## De-risking Tasks (DR)

- [ ] `SYNTAX-17-DR-01`: Decide whether to extend `classify` to color `@alias`/enum references
      (needs an alias type check on `gt`).
- [ ] `SYNTAX-17-DR-02`: Confirm the desired layering of inferred-type vs scope-binding colors
      and document the precedence in the color page.
