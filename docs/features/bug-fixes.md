---
id: "BUG"
title: "BUG: Bugfixes & Stability"
type: "epic"
priority: critical
status: planned
folders:
  - "[[features]]"
---
# Bugfixes & Stability Requirements (`BUG`)
This document tracks critical stability issues and functional bugs that impact the reliability of Lunar.
## Requirements & Status
| ID | Issue | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `BUG-01` | **Recursive Local Resolution** | **C** | **Done** | Fix failure to resolve recursive calls in `local function` declarations. |
| `BUG-132` | **Duplicate Problems Reporting** | **M** | **Todo** | Fix duplicate problem reporting for function argument type mismatches. |
| `BUG-133` | **Union Inlay Hints (OR)** | **M** | **Todo** | Fix missing inlay hints for variables initialized with an `or` expression. |
| `BUG-134` | **@return Comma Parsing** | **H** | **Todo** | Fix parse error on comma-separated multiple types in `@return` tags. |
| `BUG-135` | **Stdlib Inlay Hints** | **M** | **Todo** | Fix missing inlay hints for values returned from standard library functions. |
| `BUG-272` | **Local Var Navigation** | **H** | **Done** | Fix Go to Definition failures for local variables with table initializers. |
| `BUG-349` | **Flaky Inlay Hint Tests** | **M** | **Todo** | Fix intermittent failures in inlay hint tests caused by state pollution and cache staling. |
| `BUG-355` | **EmmyLua @-description Parse** | **H** | **Done** | Fix hard parse error on `@`-prefixed descriptions after `@return`/`@param` types (`@return number @desc`). |
| `BUG-356` | **Boolean Concat Not Flagged** | **L** | **Done** | Concatenating a non-concatenable value (e.g. `boolean`) raises no diagnostic. |
| `BUG-357` | **LuaCATS `fun()` Param Names** | **M** | **Cancelled** | Cannot reproduce — parameter names from `fun(...)` `@type` signatures are already extracted correctly (resolved by BUG-133). Regression guards added for the plain and `\| string` union cases. |
| `BUG-358` | **Reformat Read-Only Exception** | **L** | **Todo** | TransactionGuard write-unsafe context exception when reformating a read-only file. |
| `BUG-359` | **package.path False Positive** | **M** | **Todo** | False positive "nil value is not assignable to string" on `package.path` concat assignment (reported twice). |
| `BUG-360` | **Failed to make file writable** | **M** | **Todo** | Failed to make in-project file writable due to container/host user UID mismatch. |
| `BUG-361` | **`global` Lexed as Keyword** | **H** | **Todo** | `global` is unconditionally lexed as the `GLOBAL` keyword, so it fails to parse as an identifier/field under Lua < 5.5 (and should be contextual even in 5.5). |
| `BUG-362` | **Platform Target Not Selectable** | **M** | **Todo** | No discoverable way to actively choose the platform target (e.g. Redis) after an interpreter is discovered. |
| `BUG-363` | **LuaRocks Panel Font Mismatch** | **L** | **Todo** | LuaRocks browser detail panel uses raw `JTextArea` (monospaced LAF default) for summary/deps, not matching the UI font. |
| `BUG-364` | **Exceptions During Indexing / Panels** | **H** | **Todo** | Numerous exceptions thrown during project indexing and when opening tool-window panels; stack traces not yet captured. |
| `BUG-365` | **LuaRocks Packages Panel Alignment** | **L** | **Todo** | UI alignment defect in the "LuaRocks Packages" detail panel; needs live characterization. |
| `BUG-366` | **LuaRocks Tool-Window Overlap** | **L** | **Todo** | Unclear separation of responsibilities between the "LuaRocks" (dependencies) and "LuaRocks Packages" (browser) tool windows. |
---
## Technical Debt Fixes
- **DOC-06-06 Implementation**: Full implementation of platform symbol documentation lookup (Task 273).
