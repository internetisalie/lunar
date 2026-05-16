---
folders:
  - "[[features/navigation/requirements|requirements]]"
priority: medium
status: done
vf_icon: ✅
title: "11: Bindings Caching"
---
# Specification: NAV-11 Bindings Caching

This document outlines the requirements for caching PSI analysis results.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-11-01` | **Reference Caching** | Cache the results of `LuaBindingsVisitor` (variable/function references within a file). | **M** | Full |
| `NAV-11-02` | **Invalidation** | Automatically invalidate the cache when the file's PSI/Document changes. | **M** | Full |

## 2. Technical Details
- Utilize `CachedValuesManager` or `UserData` with document hash checks.
- Prevent repetitive tree traversals during typing or resolving references multiple times in the same file.
