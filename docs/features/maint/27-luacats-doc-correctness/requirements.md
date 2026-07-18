---
id: "MAINT-27"
title: "27: LuaCATS Doc & Lexer Correctness"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
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
| MAINT-27-01 | Lexer containment | M | Full | Exclude `\r\n` from `CODE`/`STRINGD` negated classes (#19); replace `HIGH_ASCII=[\x80-\xff]` with the JFlex `[:letter:]` Unicode class in `NAME_LEADING`/`NAME_TRAILING` (#66); drop the dead `COMMENT_END` state + `<TAG_OVERLOAD>` block (review §3) — regen via `generate-parser` (design §3.1) |
| MAINT-27-02 | Escaped, correct doc HTML | M | Not Implemented | `renderTypeText` HTML-escapes type text and links only simple identifiers `^[\p{L}_][\p{L}\p{N}_.]*$`; structured types render as escaped code (#35); `---@type T` local renders `local <name> : <type>`, not `class T` (#57) — design §3.2/§3.3 |
| MAINT-27-03 | Inheritance rendering | S | Not Implemented | `resolveClassComment` iterates individual `parentTypes.argTypeList` and falls back to `LuaCatsTypeNameIndex` for bare `@class` (#36); `collectInheritedFieldTags` BFS-walks the chain with a `visited` cycle guard + `<= 64` depth cap (#67) — design §3.4/§3.5 |
| MAINT-27-04 | Alias values | S | Not Implemented | Gate the alias `Values:` section on `comment.typeOptionList.isNotEmpty()` instead of `enumTagList` (#37) — design §5 Ex.3 |
| MAINT-27-05 | Direct-children getters | S | Not Implemented | Swap all 26 `LuaCatsLazyCommentImpl` getters from recursive `findChildrenOfType` to `getChildrenOfTypeAsList` (direct children, matching generated `LuaCatsCommentImpl`); no cache (#38, review §2.5.5) — design §3.6 |
| MAINT-27-06 | Annotator cleanup | C | Not Implemented | Fixture-verify current highlighting, then remove the unreachable `LuaCatsNamedType`/`NAME` classTag/aliasTag special cases in `LuaCatsAnnotator` (#72) — design §3.7 |

## Test Cases

Every case is a `configureByText` fixture asserted via the existing test patterns
(`LuaCatsDocumentationRendererTest` renderer HTML; `TestLuaCatsLexer`/`LuaCatsElementTypeTest`
token streams; `LuaCatsLazyCommentTest` getter results; `LuaCatsAnnotatorTest` `doHighlighting()`).

| TC | Requirement | Input | Expected output |
| :--- | :--- | :--- | :--- |
| TC-01a | 01 | ``---@param x `unclosed`` newline `---@param y number` | The `CODE`/backtick token does not span the newline; line 2 lexes `@param`, `y`, `number` as normal tokens (no single token covering `y number`) |
| TC-01b | 01 | `---@class 名前` newline `local x = {}` | `名前` lexes as a single `LCATS_NAME` token (not `LCATS_BAD_CHARACTER`) |
| TC-01c | 01 | `---@overload fun(x: string): string` newline `function f(x) end` | Lexes via `TAG_TYPE` (`fun` → `LCATS_KEYWORD`); `comment.overloadTagList.size == 1` — no reliance on the removed `TAG_OVERLOAD` state |
| TC-02a | 02 | `---@type table<string, integer>` newline `local m = {}` | `renderDoc` HTML contains `table&lt;string, integer&gt;` and contains no raw `<table` / unescaped `<`; no `psi_element://table<` href |
| TC-02b | 02 | `---@type Player` newline `local m = {}` | Definition block renders `local m : ` + a hyperlink for `Player`; does NOT contain `class Player` |
| TC-02c | 02 | `---@param a Player` newline `function f(a) end` | `Player` (simple) is hyperlinked via `buildTypeLink`; HTML well-formed |
| TC-03a | 03 | `---@class A` + `---@field id integer`; `---@class B : A`; `---@class C : B` (three locals) | `renderDoc` for `C` contains "Inherited Fields:" and `id` (grandparent field) |
| TC-03b | 03 | bare `--- @class Parent` + `--- @field p string` (no `local`); `---@class Child : Parent` newline `local Child = {}` | `renderDoc` for `Child` lists inherited field `p` (found via `LuaCatsTypeNameIndex`) |
| TC-03c | 03 | `---@class A : B`; `---@class B : A` | `renderDoc` for `A` terminates (no stack overflow / hang); each class's fields render at most once per level |
| TC-04 | 04 | `---@alias Mode "r"\|"w"` newline `local m` | `renderDoc` for the alias tag contains "Values:", `"r"`, and `"w"` |
| TC-05a | 05 | `---@param x number Some desc` newline `function f(x) end` | `comment.getDescriptionList()` returns only top-level descriptions — the `@param`-nested description is NOT included; `LuaCatsSummary` has no duplicated text |
| TC-05b | 05 | `---@param x number` newline `function f(x) end` | `isDocCommentEmpty`-equivalent check reflects only the (empty) top-level description, not the tag subtree |
| TC-06 | 06 | `---@class Foo : Bar` newline `local Foo = {}`; and `---@alias Mode Player` newline `local m` | After removal: `Bar`/`Player` highlight as `LuaCatsHighlight.TYPE`; class name `Foo` highlighting unchanged from the captured baseline |
