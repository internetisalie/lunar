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
---
## Technical Debt Fixes
- **DOC-06-06 Implementation**: Full implementation of platform symbol documentation lookup (Task 273).
