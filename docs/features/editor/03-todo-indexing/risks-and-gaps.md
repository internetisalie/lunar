---
id: "EDITOR-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-03"
folders:
  - "[[features/editor/03-todo-indexing/requirements|requirements]]"
---

# EDITOR-03: Risks & Gaps

Low-risk feature: one stateless EP class, one declarative registration, no lexer/PSI/attributes
changes. Risks are narrow and pre-mitigated by the design.

## Critical Risks

### Risk 1.1: Long-comment delta miscount slices the comment body wrong
- **Impact**: If `getCommentStartDelta`/`getCommentEndDelta` return the wrong marker length for
  `--[[`/`--[==[`, the platform matches patterns against the bracket run (false negatives, e.g.
  `--[[TODO]]` not detected) or throws on an out-of-range delta.
- **Likelihood**: low
- **Mitigation**: design §3.1 specifies the exact bracket-counting algorithm using the text-aware
  overload, caps deltas at safe minimums on malformed input, and never returns negative/out-of-range.
  TC-2 and TC-3 (implementation-plan) assert both `--[[` and `--[==[` bodies match.

### Risk 1.2: `LUACATS_COMMENT` is an `ILazyParseableElementType`, not a plain token
- **Impact**: The indexing lexer emits `LUACATS_COMMENT` for `---` comments
  (`LuaLexer.getTokenType`, `lang/lexer/LuaLexer.kt:97-106`). If the platform's
  `LexerBasedTodoIndexer` handled lazy-parseable comment tokens differently than plain ones, doc-comment
  TODOs (`EDITOR-03-04`) might be skipped.
- **Likelihood**: low
- **Mitigation**: ~~the indexer operates on the raw lexer stream, so it sees `LUACATS_COMMENT` as an
  ordinary `IElementType`~~ — **this premise proved FALSE (2026-07-10).** `findTodoItems` gates on
  `TodoCacheManager`'s count, which for a light file is fed by `TokenSetTodoIndexer` iterating the
  **layered editor highlighter** (`LuaEditorHighlighter` registers a `LUACATS_COMMENT` layer), **not**
  the `IndexPatternBuilder`. The layer re-lexes `---` into inner LuaCats tokens, so the outer
  `LUACATS_COMMENT` never reaches the counter → count 0 → search short-circuits. Adding
  `LUACATS_COMMENT` to the builder set does NOT fix it (verified empirically, incl. relabeling to
  `SHORTCOMMENT`). **Shipped: single-line `--- TODO` is a documented gap (EDITOR-03-04 Partial);
  block `--[[ ]]` doc comments work.** Tractable follow-up: register a per-file-type
  `com.intellij.todoIndexer` for Lua using a non-layered `LuaLexer` (token set incl.
  `LUACATS_COMMENT`) to bypass the layered highlighter for the count gate.

## Design Gaps

_None. All decisions are pinned in design.md §2–§7; delta algorithm fully specified in §3.1._

## Technical Debt & Future Work
- **TBD: SHEBANG TODO scanning** — `#!` shebang lines are intentionally excluded from
  `COMMENT_TOKENS` (design §6). No known user demand; if requested, add `LuaElementTypes.SHEBANG`
  to the set plus a delta of 2 (`#!`). Deferred, not blocking.
- **TBD: multi-line line-comment continuations** — the platform's
  `getCharsAllowedInContinuationPrefix` lets a TODO span wrapped `--` lines. Lua's
  `MultiLineMergingLexerAdapter` (`lang/lexer/LuaLexer.kt:171-197`) already merges consecutive `--`
  lines into one `SHORTCOMMENT`, so a multi-line TODO body is one token and matches without override.
  Left at the default (`""`) unless a test shows a gap; not required by any `Must`.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| EDITOR-00-DR-01 | Assert a `--- TODO` LuaCATS doc comment yields a TodoItem. | Risk 1.2 | **done — NOT satisfied**: `--- TODO` yields 0 (layered-highlighter count gate, see Risk 1.2). Shipped as EDITOR-03-04 Partial with a tractable `com.intellij.todoIndexer` follow-up; `testLuaCatsLineDocTodoIsKnownGap` pins current behavior. |
| EDITOR-00-DR-02 | Assert `--[==[ TODO ]==]` (bracket level 2) yields one TodoItem (confirms the text-aware start-delta overload counts the `=` run). | Risk 1.1 | **done — confirmed** by `testLeveledBlockCommentTodo`. |

## Test Case Gaps
None beyond TC-1…TC-7 in the implementation plan. The two negative cases (string literal TC-5, bare
identifier TC-6) cover the "not in code / not in strings" DoD requirement.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
