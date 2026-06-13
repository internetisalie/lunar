---
id: "NAV-05-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "NAV-05"
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/05-method-override-markers/requirements|requirements]]"
---

# Risks & Design Gaps: NAV-05 Method Override Markers

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `NAV-05-R-01` | **Hierarchy resolution cost** | Medium | `resolveType` + supertype walk runs in the highlighting pass; the type layer caches class materialization. Bounded by hierarchy depth + a `visited` cycle guard. |
| `NAV-05-R-02` | **Abstract vs concrete detection** | Low | `isAbstract` relies on the super member's `sourceElement` being a declaration vs a `function … end`; if ambiguous, default to `OverridingMethod` (no functional loss). |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `NAV-05-G-01` | **Go to Super action** | The Ctrl+U action handler is not wired (only gutter navigation). | `NAV-05-DR-01` |

## De-risking Tasks (DR)

- [ ] `NAV-05-DR-01`: Wire the platform "Go to Super Method" action for Lua (reusing
      `findSuperMembers`) so Ctrl+U/Cmd+U works in addition to the gutter (NAV-05-04).
