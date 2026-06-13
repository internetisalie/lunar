---
id: "NAV-02"
title: "02: Find Usages"
type: "feature"
parent_id: "NAV"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---

# Specification: NAV-02 Find Usages (Symbols)

This document defines the requirements for finding references to a specific symbol across the project.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-02-01` | **Local Variable Usages** | Find all reads and writes of a local variable within its scope. | **M** | Partial |
| `NAV-02-02` | **Global Symbol Usages** | Find all usages of a global function or variable across the entire project, using the stub index. | **M** | Not Implemented |
| `NAV-02-03` | **Label Usages** | Find all `goto` statements referencing a specific label within a function scope. | **S** | Full |
| `NAV-02-04` | **Table Field Usages** | Find usages of a specific table field, differentiating by type if possible, or offering broad text-based searches. | **C** | Not Implemented |
| `NAV-02-05` | **LuaCATS Type Usages** | Find usages of a LuaCATS type definition (e.g., where a `@class` is used in `@type` annotations). | **S** | Not Implemented |

## 2. Implementation Constraints
- **Performance**: Usages of global symbols must use the standard IntelliJ `FileBasedIndex` or `StubIndex` to avoid parsing un-opened files.
- **Read/Write Access**: The `FindUsagesProvider` should correctly categorize usages as "Read" or "Write" (integration with NAV-10).

## 3. UI/UX Elements
- Standard IntelliJ "Find Usages" tool window grouping by file, package, and usage type (Read/Write).
- "Show Usages" popup (Ctrl+Alt+F7 / Cmd+Option+F7) support.
