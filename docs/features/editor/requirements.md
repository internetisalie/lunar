---
id: "EDITOR"
title: "EDITOR: Editor Ergonomics & Structural Editing"
type: "epic"
status: "todo"
priority: "medium"
folders:
  - "[[features]]"
---

# Editor Ergonomics & Structural Editing Requirements (`EDITOR`)

The "long tail" of IntelliJ Platform editor extension points that make a language plugin
feel **native** rather than merely functional. Lunar's head (parser, highlighter, completion,
navigation) and mid-tail (find-usages, rename, safe-delete, inlays, hierarchy, formatter) are
done; this epic collects the remaining small, high-ergonomics-per-line editor EPs that first-party
JetBrains languages ship and Lua users implicitly expect.

**Scope boundary.** These are *user-visible editor behaviors*, deliberately **not** part of Wave 12
(`MAINT`), whose charter is "internal & invisible." Each feature is a distinct, declaratively
registered extension point over the existing lexer/PSI — parallel-safe, mostly new files.

**Relation to prior work.** [`COMP-07`](../completion/07-live-templates/requirements.md) shipped four
*live-template* surrounds (`$SELECTION$` wrappers). `EDITOR-05` adds the real
`Surround With` action (`SurroundDescriptor`, Ctrl+Alt+T) — a **different EP**; the two complement
rather than duplicate each other.

**Reference.** Prefer `~/Documents/src/lua/intellij-community` for the canonical implementation of
each EP (e.g. the Java/Groovy/JSON `TypedHandler`, `SpellcheckingStrategy`, `SurroundDescriptor`).

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`EDITOR-01`](01-smart-typing/requirements.md) | **Smart Typing** | **M** | **Not Implemented** | Auto-close/auto-skip brackets and quotes and auto-complete keyword block pairs (`do`/`then`/`function` → `end`, `repeat` → `until`) via `TypedHandlerDelegate` + `QuoteHandler` + `BackspaceHandlerDelegate`. |
| [`EDITOR-02`](02-spellchecking/requirements.md) | **Spellchecking** | **M** | **Not Implemented** | `SpellcheckingStrategy` with a Lua-aware tokenizer so comments, string literals, and identifiers are spell-checked with typo quick-fixes and Rename integration. |
| [`EDITOR-03`](03-todo-indexing/requirements.md) | **TODO / FIXME Indexing** | **M** | **Not Implemented** | `IndexPatternBuilder` that surfaces `TODO`/`FIXME`/custom patterns inside Lua comments in the TODO tool window, gutter, and structural search. |
| [`EDITOR-04`](04-word-selection/requirements.md) | **Smart Word Selection** | **S** | **Not Implemented** | `ExtendWordSelectionHandler`s so Ctrl+W expands the selection along meaningful Lua constructs (argument → call → statement → block → function). |
| [`EDITOR-05`](05-surround-with/requirements.md) | **Surround With** | **S** | **Not Implemented** | `SurroundDescriptor` providing Ctrl+Alt+T templates: wrap a selection in `if`/`while`/`for`/`function`/`do`/`pcall`. Complements COMP-07 live-template surrounds. |
| [`EDITOR-06`](06-unwrap-remove/requirements.md) | **Unwrap / Remove** | **S** | **Not Implemented** | `UnwrapDescriptor` (Ctrl+Shift+Delete) to remove or unwrap a surrounding `if`/`while`/`for`/`function`/`do` block, hoisting its body. Inverse of EDITOR-05. |
| [`EDITOR-07`](07-move-statement/requirements.md) | **Move Statement / Element** | **C** | **Not Implemented** | `StatementUpDownMover` (Ctrl+Shift+↑/↓, block-boundary aware) and `MoveElementLeftRightHandler` (Ctrl+Alt+Shift+←/→) for arguments and list elements. |
| [`EDITOR-08`](08-smart-enter/requirements.md) | **Smart Enter (Complete Statement)** | **C** | **Not Implemented** | `SmartEnterProcessor` (Ctrl+Shift+Enter) that completes a partial `function`/`if`/`for`/`while` skeleton (adds the matching `end`/`then`/`do` and positions the caret). |

---

## Execution order & dependencies

All eight features are parallel-safe (each adds a new file + one declarative `plugin.xml`
registration). Two soft couplings only, for shared PSI helpers — **not** blocking edges:

- `EDITOR-06` (Unwrap) reuses the block-structure PSI helpers introduced by `EDITOR-05` (Surround).
- `EDITOR-08` (Smart Enter) reuses the keyword-pair table introduced by `EDITOR-01` (Smart Typing).

Sequence by priority: land the three **Must** items first (`EDITOR-01`/`-02`/`-03`) — highest
ergonomics-per-line and each leans entirely on existing lexer/PSI/comment-token infrastructure —
then the **Should** structural-editing pair, then the two **Could** polish items.

## Planning decisions (confirmed 2026-07-09)

- **Scope:** plan all eight features up front (design + implementation-plan + risks per feature).
- **EDITOR-01 keyword auto-close:** ships **on by default** behind an Editor > Smart Keys toggle
  (JetBrains-language convention); bracket/quote auto-close is unconditional. See `EDITOR-01-05`.

## Definition of Done (per feature)

Per the roadmap DoD gate (precedence #5): a feature that surfaces through a platform EP is "done"
only when a **real-flow** test drives that machinery and asserts the user-visible result —
e.g. `myFixture.type('(')` + assert document text for smart typing, `myFixture.getReferenceAtCaretPosition`
/ inspection highlighting for spellchecking, `CodeInsightTestFixture` action invocation for
surround/unwrap/move/smart-enter. Engine-only assertions do not satisfy the gate.
