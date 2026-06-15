---
id: NAVIGATION-11
title: "11: Bindings Caching"
type: feature
parent_id: NAV
status: "cancelled"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---
# Specification: NAV-11 Bindings Caching — Retired

> **🗄️ Retired (2026-06-13).** NAV-11 has been **retired**. Its premise — caching the results of `LuaBindingsVisitor` — no longer applies: that visitor was removed in **MAINT-04**, which replaced eager whole-file binding traversal with lazy `PsiScopeProcessor` / `PsiReference` resolution. There is no longer an eager binding pass to cache; per-file `CachedValuesManager` caching now lives only at the resolution sites that need it (e.g. type-graph snapshots). The original specification is retained below for historical reference only and is no longer a tracked requirement.

This document outlines the (retired) requirements for caching PSI analysis results.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-11-01` | **Reference Caching** | Cache the results of `LuaBindingsVisitor` (variable/function references within a file). | **M** | Cancelled |
| `NAV-11-02` | **Invalidation** | Automatically invalidate the cache when the file's PSI/Document changes. | **M** | Cancelled |

## 2. Technical Details
- Utilize `CachedValuesManager` or `UserData` with document hash checks.
- Prevent repetitive tree traversals during typing or resolving references multiple times in the same file.
