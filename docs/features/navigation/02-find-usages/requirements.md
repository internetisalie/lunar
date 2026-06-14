---
id: "NAV-02"
title: "02: Find Usages"
type: "feature"
parent_id: "NAV"
status: "done"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---

# Specification: NAV-02 Find Usages (Symbols)

This document defines the requirements for finding references to a specific symbol across the project.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-02-01` | **Local Variable Usages** | Find all reads and writes of a local variable within its scope. | **M** | Full |
| `NAV-02-02` | **Global Symbol Usages** | Find all usages of a global function or variable across the entire project, using the stub index. | **M** | Full |
| `NAV-02-03` | **Label Usages** | Find all `goto` statements referencing a specific label within a function scope. | **S** | Full |
| `NAV-02-04` | **Table Field Usages** | Find usages of a specific table field, differentiating by type if possible, or offering broad text-based searches. | **C** | Not Implemented |
| `NAV-02-05` | **LuaCATS Type Usages** | Find usages of a LuaCATS type definition (e.g., where a `@class` is used in `@type` annotations). | **S** | Not Implemented |

## 2. Implementation Constraints
- **Performance**: Usages of global symbols must use the standard IntelliJ `FileBasedIndex` or `StubIndex` to avoid parsing un-opened files.
- **Read/Write Access**: The `FindUsagesProvider` should correctly categorize usages as "Read" or "Write" (integration with NAV-10).

## 3. UI/UX Elements
- Standard IntelliJ "Find Usages" tool window grouping by file, package, and usage type (Read/Write).
- "Show Usages" popup (Ctrl+Alt+F7 / Cmd+Option+F7) support.

## 4. Test Cases

Verified with `myFixture.findUsages(targetElement)` asserting the count and read/write type.

### TC-NAV-02-01: Local Variable (NAV-02-01)
- **Input**: `local x = 1; print(x); x = 2`.
- **Action**: Find Usages on the `local x` declaration.
- **Output**: 2 usages — `print(x)` (Read) and `x = 2` (Write); the declaration is not a usage.

### TC-NAV-02-02: Cross-File Global (NAV-02-02)
- **Input**: `a.lua`: `function Helper() end`; `b.lua`: `Helper()`.
- **Action**: Find Usages on `Helper` (both indexed).
- **Output**: 1 usage in `b.lua` (Read).

### TC-NAV-02-03: Label (NAV-02-03)
- **Input**: `::done:: goto done`.
- **Action**: Find Usages on the `done` label.
- **Output**: 1 usage (`goto done`).

### TC-NAV-02-04: Scope Isolation (NAV-02-01)
- **Input**: `local x=1` used once in `f`; `local x=2` used once in `g`.
- **Action**: Find Usages on `f`'s `x`.
- **Output**: only `f`'s usage (the `g` usage excluded via `resolve()` scoping).

### TC-NAV-02-05: Read/Write Classification (NAV-10 integration)
- **Input**: `t = {}; t.k = 1; print(t)`.
- **Action**: Find Usages on global `t`.
- **Output**: `t` in `t.k = 1` is Read (index base); `print(t)` Read; `t = {}` Write.
