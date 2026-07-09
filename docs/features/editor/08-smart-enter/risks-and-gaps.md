---
id: "EDITOR-08-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-08"
folders:
  - "[[features/editor/08-smart-enter/requirements|requirements]]"
---

# EDITOR-08: Risks & Gaps

## Risks

| ID | Risk | Impact | Likelihood | Mitigation |
| :--- | :--- | :--- | :---: | :--- |
| R-01 | **Partial skeleton parses to `ERROR_ELEMENT`s, not a clean `LuaIfStatement`.** `if x<caret>` has no `then`/`end`, so the parser may not build a `LuaIfStatement` node at all — `getStatementAtCaret`/`keywordPairFor` could miss it. | Fixers no-op; feature silently does nothing. | High | Design §3.4 keys off **direct child tokens** (`hasChildToken`) and the leaf under the caret, not on a well-formed subtree. If PSI-class matching proves unreliable for a bare opener, fall back to matching the opener **keyword token** at the statement start via `LuaKeywordPairs.byOpener(leaf.elementType)`. De-risk with DR-03. |
| R-02 | **Nested/own write command.** The `EditorCompleteStatement` action already opens a `WriteCommandAction`; a fixer opening its own would nest or throw. | Runtime exception on Ctrl+Shift+Enter. | Medium | Contract in design §5: fixers use `Document.insertString` only, never `WriteCommandAction`. Verified against `JsonSmartEnterTest` (drives `process` inside a single write command). |
| R-03 | **Reformat changes expected whitespace**, so "after" text in the Test Matrix drifts from what `LuaFormatBlock` emits. | Brittle tests; false failures. | Medium | DR-02: capture the formatter's actual output once per TC and pin it; assert on the reformatted result, not a hand-written literal. |
| R-04 | **Bracket balancer mis-nests** on interleaved `({[` or brackets inside strings. | Wrong closer order; corrupt edit. | Low | Design §3.2 uses a LIFO stack over leaf tokens and skips `STRING`/`LONGSTRING`/comment leaves (single leaves, naturally skipped); on a non-matching closer it registers an unresolved error and stops rather than guessing. |
| R-05 | **`function foo` vs `function foo()` detection.** Deciding "no parens yet" vs "open paren, no close" can double-insert. | `function foo()()` or similar. | Low | `LuaFunctionParenFixer` only supplies the pair when **neither** paren token is a child; the open-but-unclosed case is owned solely by `LuaMissingBracketFixer` (design §3.2). |

## De-risking actions

| ID | Action | When | Exit criterion |
| :--- | :--- | :--- | :--- |
| `EDITOR-08-00-DR-01` | **Keyword-pair-table ordering handshake with EDITOR-01.** Confirm which feature lands `LuaKeywordPairs` first; agree the exact `LuaBlockKeyword` shape + six rows (design §2.1) so neither feature forks the table. | Before Phase 0 coding. | A single `lang/syntax/LuaKeywordPairs.kt` (owned by whichever lands first) matches design §2.1; the other feature consumes it unchanged. |
| `EDITOR-08-00-DR-02` | **Reformat-fidelity spot check.** In a scratch test, run one block completion (e.g. `if x<caret>` → `if x then … end`) through the processor and print the reformatted text + caret offset; use it to pin the Test Matrix "after" literals. | Start of Phase 6 (or when writing the first TC). | Every TC "after" literal equals the observed `LuaFormatBlock` output. |
| `EDITOR-08-00-DR-03` | **Partial-parse PSI probe.** In a test, `configureByText("if x<caret>")` and dump `findElementAt(caret)` ancestry + `element.node.getChildren(null)` element types. Confirm whether a `LuaIfStatement` (possibly error-bearing) is produced and whether `IF`/expr tokens are reachable as direct children. Decides between PSI-class matching and keyword-token fallback (R-01). | Before Phase 2 coding. | Documented: the reliable anchor (statement node vs. opener token) each fixer uses to identify the construct on a bare, unbalanced skeleton. |

## Gaps / explicitly out of scope

- **`elseif`/`else` completion** is not in scope (requirements list only `function`/`if`/`for`/
  `while`/`repeat`). A future `LuaConditionalPartFixer` could add it; not planned here.
- **Multi-caret** Smart Enter is not specifically handled; the platform applies the processor per
  caret, which is acceptable for a `Could`-priority feature.
- **Settings toggle:** none. Unlike EDITOR-01's keyword auto-close, Smart Enter is an explicit
  user action (Ctrl+Shift+Enter), so it needs no on/off Smart Keys option.
