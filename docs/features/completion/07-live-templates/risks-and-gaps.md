---
id: COMP-07-RISKS
title: Live Templates Risks & Gaps
type: risk
status: planned
parent_id: COMP-07
folders:
  - "[[features/completion/07-live-templates/requirements|requirements]]"
---

# COMP-07: Risks & Gaps

Surface what could go wrong before building the COMP-07 template set. Design reference:
[design.md](design.md).

## Critical Risks

### Risk 1.1: Suppression token set is incomplete or wrong
- **Impact**: if `LuaCodeContextType.SUPPRESS` (design §3.1) misses a string/comment/number leaf
  variant, templates still fire inside that construct — the COMP-07-10 defect is only partially fixed.
- **Likelihood**: medium — the codebase has two parallel token layers (lexer `LuaTokenTypes.*`,
  parser `LuaElementTypes.*`) and long-string/long-comment content is split across begin/end/content
  leaves.
- **Grounded names used** (verified file:line): comments via `LuaSyntax.CommentTokens`
  (`src/main/kotlin/.../lang/syntax/LuaSyntax.kt:34` → `LuaElementTypes.SHORTCOMMENT`,
  `LuaElementTypes.LONGCOMMENT`, `LuaElementTypes.SHEBANG`, `LuaLazyElementTypes.LUACATS_COMMENT`);
  strings via `LuaSyntax.StringLiteralTokens` (`LuaSyntax.kt:42` → `LuaElementTypes.STRING`); plus
  lexer leaves `LuaTokenTypes.LONGSTRING` / `LONGSTRING_BEGIN` / `LONGSTRING_END`
  (`src/main/java/.../lexer/LuaTokenTypes.java:75,77,78`), `LuaTokenTypes.NUMBER`
  (`LuaTokenTypes.java:68`) and `LuaElementTypes.NUMBER`
  (`src/main/gen/.../psi/LuaElementTypes.java:107`).
- **Mitigation**: the §3.1 algorithm walks the leaf **and its ancestors** for membership in
  `SUPPRESS` (not just the leaf), so a content leaf inside a `LONGSTRING`/`LONGCOMMENT` parent is
  still caught even if its own type is whitespace/newline. DR-01 confirms the exact leaf type
  `file.findElementAt` returns for each construct.

### Risk 1.2: Context-id migration breaks user customizations
- **Impact**: re-pointing the four shipped templates from context option `LUA` to `LUA_CODE`
  (design §2.2.1) could disable templates for users who toggled the `LUA` context, or orphan their
  saved enable/disable state.
- **Likelihood**: low — but user-visible if it regresses.
- **Mitigation**: keep `LuaTemplateContextType` (`LUA`) registered and make `LUA_CODE` declare it as
  **parent** (design §2.1.2, 3-arg `TemplateContextType` ctor, confirmed in
  `platform/analysis-api/.../TemplateContextType.java:43`). The platform resolves a template if any
  enabled context matches, and a user copy still bound to `LUA` keeps working. DR-02 verifies
  enable/disable persistence across the rename.

### Risk 1.3: `$SELECTION$` surround context never offered
- **Impact**: if `LuaSurroundContextType` is mis-wired, the four COMP-07-11 surround templates do not
  appear in Surround-With (Ctrl+Alt+T) and the requirement silently fails.
- **Likelihood**: medium — surround binding is the least-exercised path in this feature.
- **Mitigation**: bind surround templates to a dedicated `LUA_SURROUND` option (design §2.3) and
  reuse the §3.1 code predicate at the selection start. `$SELECTION$` is a reserved platform variable
  — do **not** declare it with `<variable>`. DR-03 prototypes one surround template end-to-end.

## Design Gaps

### Gap 2.1: Smart defaults for `req` / `mod`
- **Question**: should `req` derive the local name from the module path (e.g.
  `require("a.b.c")` → `c`) and `mod` from the file name, instead of literal placeholder defaults?
- **Options / leaning**: leaning **literal defaults** (`$NAME$`/`$MODULE$`, `$M$=M`) for the first
  cut — smart defaults need a `Macro` subclass + `liveTemplateMacro` EP (parked in requirements.md
  backlog) and are out of scope for the Should set.
- **Resolved by**: DR-04 — confirm literal defaults are acceptable; if not, promote the macro work
  from the backlog. Fold the decision into design §2.2.2.

## Technical Debt & Future Work
- **TBD: `LuaIfContextType` + `elseif`** — designed (§2.1.3) but deferred; ship only when the parked
  `elseif` backlog item is pulled in (plan Phase 5, Could).
- **TBD: smart variable-name macros** — `SuggestFirstLuaVarName`-style defaults via a `Macro`
  subclass + `liveTemplateMacro` EP; parked in requirements.md backlog.
- **TBD: `fn` closure / `pcall` wrapper / `class` boilerplate** — Could/Watch backlog templates, not
  in this plan.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| COMP-07-00-DR-01 | In a fixture, log `PsiUtilCore.getElementType(file.findElementAt(offset))` for carets inside `"s"`, `[[s]]`, `--c`, `--[[c]]`, `--- @x`, and `42`; confirm each type (or an ancestor) is in `SUPPRESS` | Risk 1.1 | todo |
| COMP-07-00-DR-02 | Toggle a bundled template off, restart the fixture, confirm enable/disable state survives the `LUA`→`LUA_CODE` migration (parent context) | Risk 1.2 | todo |
| COMP-07-00-DR-03 | Wire one surround template (`surr_if`) + `LuaSurroundContextType`; confirm it appears in Surround-With for a selection and wraps `$SELECTION$` | Risk 1.3 | todo |
| COMP-07-00-DR-04 | Decide literal vs. smart defaults for `req`/`mod`; record outcome in design §2.2.2 | Gap 2.1 | todo |

## Test Case Gaps
- requirements.md TC 6 covers string/comment suppression; **add** a number-literal suppression case
  (caret inside `42`) and a long-string/long-comment case to fully exercise Risk 1.1.
- No TC yet asserts the surround templates wrap a multi-line selection (COMP-07-11) — add a manual
  check (plan Verification Tasks).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
