---
id: "MAINT-27"
title: "27: LuaCATS Doc & Lexer Correctness"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-27: LuaCATS Doc & Lexer Correctness

Coalesces the LuaCATS defects from [`docs/review.md`](../../../review.md) (re-verified
2026-07-17): six documentation-renderer bugs concentrated in `LuaCatsDocumentationRenderer`,
the two `luacats.flex` lexing hazards, the recursive-getter contract violation in
`LuaCatsLazyCommentImpl`, and an annotator dead branch.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 19 | `CODE`/`STRINGD` negated classes match `\n` — unclosed backtick/quote corrupts all following tag lines |
| 35 | No HTML escaping of type text — `table<string, integer>` / `fun(x)` break the doc popup |
| 36 | `lookupParentComment` keys on the whole parent-type list text (`"A, B"`) — Inherited Fields never renders |
| 37 | Alias Values gated on `enumTagList` — the standard `---|` union-alias form never renders values |
| 38 | Lazy-comment getters use recursive `findChildrenOfType` — duplicated descriptions, wrong `isDocCommentEmpty` |
| 57 | `---@type T` locals render as `class T` |
| 66 | `HIGH_ASCII=[\x80-\xff]` under `%unicode` — CJK/Cyrillic names abort tag lexing |
| 67 | Inheritance rendering is single-level — grandparent fields never appear |
| 72 | `LuaCatsAnnotator` unreachable/mis-highlighting special case — verify with a fixture, then drop |
| §2.5.5 | 26 recursive getters × full subtree walk; per-instance lexer token map; renderer boilerplate ×5 |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-27-01 | Lexer containment | M | Not Implemented | Exclude `\r\n` from `CODE`/`STRINGD`; Unicode letter class for names (#19, #66) — regen via `generate-parser` |
| MAINT-27-02 | Escaped, correct doc HTML | M | Not Implemented | HTML-escape type text; link only simple identifiers (#35); `local <name> : <type>` rendering (#57) |
| MAINT-27-03 | Inheritance rendering | S | Not Implemented | Per-`ArgType` parent lookup + `LuaCatsTypeNameIndex` fallback (#36); walk the chain with a cycle guard (#67) |
| MAINT-27-04 | Alias values | S | Not Implemented | Gate on `typeOptionList` (#37) |
| MAINT-27-05 | Direct-children getters | S | Not Implemented | `getChildrenOfTypeAsList` semantics + collect-once caching (#38, §2.5.5) |
| MAINT-27-06 | Annotator cleanup | C | Not Implemented | Fixture-verify then remove the dead special case (#72) |
