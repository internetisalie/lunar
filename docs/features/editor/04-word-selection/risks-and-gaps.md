---
id: "EDITOR-04-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-04"
folders:
  - "[[features/editor/04-word-selection/requirements|requirements]]"
---

# EDITOR-04: Risks & Gaps

## Critical Risks

### Risk 1.1: Platform default already provides an overlapping range
- **Impact**: A handler that re-adds a range the platform's `SelectWordHandler` already
  supplies produces a duplicate ladder step (visible as a "dead" Ctrl+W press that doesn't
  grow the selection).
- **Likelihood**: low
- **Mitigation**: The platform deduplicates equal `TextRange`s and sorts by size, so duplicates
  are harmless. Each handler is scoped to add *only* the intermediate range the default cannot
  infer (un-bracketed list span, shell-free block body, string/comment interior) — verified in
  design §3.3/§3.4 notes. TC-01/03/06 assert the exact successive selections, catching any
  extra or missing rung.

### Risk 1.2: Long-comment / long-string delimiter miscount → out-of-bounds
- **Impact**: A truncated `--[` or malformed `[=[` could index past the string end when
  counting `=` levels, throwing during an editor action (contract §2 error-bounding: must never
  crash the host action).
- **Likelihood**: medium
- **Mitigation**: The string handler delegates to the already-hardened
  `LuaLiterals.getLuaStringDelimiterLength` (returns 0 on degenerate input → handler returns
  `null`). The comment handler adds an explicit `level + 3 < raw.length` bounds guard before the
  `=` scan (design §3.2 step 3/edge handling) and returns `null` when `textStart >= textEnd`.
  DR-01 fuzzes malformed literals.

## Design Gaps

_No open design gaps. Every construct rung maps to a named handler or the platform default
(design §8); all algorithms (§3.1–§3.5), the plugin.xml registration (§7), and every Must/Should
test case are specified._

## Technical Debt & Future Work
- **TBD: LuaCATS doc-comment interior** — `LUACATS_COMMENT` (`---@…`) is excluded from the
  comment-interior handler (design §2.2). Selecting a tag's argument (e.g. the type name in
  `---@type Foo`) as an intermediate step is a separate, tag-PSI-aware feature; deferred.
- **TBD: Escape-sequence sub-steps in strings** — JSON's `SelectWordUtil.addWordHonoringEscape
  Sequences` offers per-escape sub-selection; Lua omits this (design §9). Could be added later
  if requested; not in EDITOR-04 scope.
- **TBD: `elseif`/`else`-branch-aware block steps** — Groovy ships a `GroovyElseSelectioner`;
  Lunar's `LuaBlockSelectioner` treats every `LuaBlock` uniformly. Branch-granular steps are a
  polish item, deferred.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| EDITOR-00-DR-01 | Add a fuzz/edge test over malformed literals and comments (`"abc`, `[[`, `--[`, `--`, `""`, `[[]]`) asserting each handler returns without throwing and yields a valid or empty range | Risk 1.2 | **done** — `testMalformedLiteralsAndCommentsDoNotThrow` fuzzes `"abc`, `[[`, `--[`, `--`, `""`, `[[]]`; every handler returns without throwing. |
| EDITOR-00-DR-02 | In a scratch test, log the platform default's ladder for `print(x, y)` (no custom handlers) to confirm which ancestor rungs are already provided before adding `LuaArgumentListSelectioner` / `LuaBlockSelectioner` | Risk 1.1 | **done** — TC-01 ladder confirmed `[x, x, y, (x, y), print(x, y), local a = print(x, y), local function … end]`; the arg-list + block-body rungs are the only Lunar contributions, no duplicate/dead step. |

## Test Case Gaps
- Nested lists (a call argument that is itself a table constructor) — the ladder should step
  through the inner list before the outer; add a nested-case test during Phase 3 if TC-03/TC-06
  do not already exercise it.
- Caret exactly on a delimiter character (on the `"` or on `--`) rather than inside the content:
  confirm the handler still offers the full-literal/comment step (the leaf under caret may be
  the STRING/COMMENT token or an adjacent token) — add to TC-02/TC-04.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
