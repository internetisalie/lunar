---
id: "MAINT-55"
title: "55: LuaCATS Type-Checking Conformance Suite"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-55: LuaCATS Type-Checking Conformance Suite

Concept only. It is to be planned through `plan-feature` before any work starts.

## Overview

The [[MAINT-54]] audit measured how often the type checker misses deliberate type errors in corpus
code. It cannot show whether the checker matches what LuaCATS annotations *mean*, and it does not
measure false positives. A conformance suite answers that per case. Each case is a short Lua snippet
in which the lines that must report, the lines that may report, and the lines that must stay silent
are marked inline. Running every case gives a table of where Lunar agrees with the specification and
where it does not, in both directions.

## Prior Art

- **Python's typing conformance suite** (`python/typing`, `conformance/`) is the only
  cross-implementation standard. Each test file marks the lines that must error and the lines where
  an error is optional, and it is scored against mypy, pyright, pyre and pytype in a published results
  table. Its format is the model to copy.
- **lua-language-server (LuaLS)** effectively defines LuaCATS semantics. Its repository's diagnostic
  tests mark the expected ranges inline in Lua snippets. It is MIT-licensed
  (`LuaLS/lua-language-server/LICENSE`), so its cases can be adapted with the copyright and permission
  notice kept.
- **EmmyLua analyzer** and **Luau** have their own suites. Luau's semantics differ from LuaCATS, so its
  cases carry over only in part.
- **TypeScript**'s `tests/cases/conformance` is a single compiler's baseline suite, not a standard.

## Draft Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-55-01 | Case format | M | Not Implemented | A Lua snippet with inline markers for "must report", "may report" and "must stay silent", read by a light-fixture runner. |
| MAINT-55-02 | Seed cases from LuaLS | M | Not Implemented | Adapt LuaLS's LuaCATS type-check cases, with attribution; record each case's source. |
| MAINT-55-03 | Results table | M | Not Implemented | Each case is scored as agree, missed or false-report; the table is committed so changes show in review. |
| MAINT-55-04 | Ratchet, not gate | S | Not Implemented | The suite fails only when a case that agreed stops agreeing, so known disagreements do not block builds. |
| MAINT-55-05 | Disagreements become bugs | S | Not Implemented | Each distinct disagreement mechanism is filed as one BUG, not one per case. |

## Open Questions

- Where Lunar disagrees with LuaLS on purpose (for example, deliberate primitive leniency in
  `checkCompatibility`), who rules which is right, and how is the ruling recorded?
- Which LuaLS diagnostics count as type checking, and which are out of scope (style, unused code)?
- Do cases run at every language level, or does each case declare one?
- How should the suite's rules be weighted by importance? That needs [[MAINT-56]]'s frequency data.
