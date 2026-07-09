---
id: EDITOR-06
title: "06: Unwrap / Remove"
type: feature
parent_id: EDITOR
status: "planned"
priority: "medium"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-06 Unwrap / Remove

The inverse of `EDITOR-05`: Ctrl+Shift+Delete opens an "Unwrap/Remove" picker that either removes a
surrounding block (hoisting its body to the parent scope) or deletes the construct entirely, with a
live preview highlight.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-06-01` | **Unwrap block** | Remove the enclosing `if`/`while`/`for`/`do`/`function` keyword+`end`, hoisting the body to the parent, re-indented. | **M** | Not Implemented |
| `EDITOR-06-02` | **Unwrap `else`/`elseif`** | Collapse an `if/else` branch appropriately (remove branch, keep the chosen body). | **S** | Not Implemented |
| `EDITOR-06-03` | **Remove construct** | Delete the whole construct including its body. | **S** | Not Implemented |
| `EDITOR-06-04` | **Preview highlight** | Each offered option highlights the affected range in the editor before the user confirms. | **S** | Not Implemented |

## 2. Technical Details
- EP: `com.intellij.lang.unwrapDescriptor` (`UnwrapDescriptor` + `Unwrapper` per construct).
- Reuses the block-structure PSI helpers introduced by `EDITOR-05` (soft dependency, shared code —
  not a blocking edge).
- Edits under `WriteCommandAction`; body hoist must preserve local scoping and reformat.
- Reference: `intellij-community` Java `*UnwrapDescriptor`/`*Unwrapper`.

## 3. Test Cases

Real-flow DoD gate: each drives the platform `UnwrapHandler` (or a selected `Unwrapper`) via
`CodeInsightTestFixture` and asserts document text with `myFixture.checkResult`. `<caret>` marks the
caret; the chosen option is single per fixture (or selected by index — see implementation-plan).

| TC | Req | Input (`before`) | Action | Expected (`after`) |
| :-- | :-- | :-- | :-- | :-- |
| TC-01 | 06-01 | `if x then\n  a()\n  b()\nend<caret>` | Unwrap 'if' | `a()\nb()` |
| TC-02 | 06-02 | `if x then\n  a()\nelse\n  b()<caret>\nend` | Remove 'else' branch | `if x then\n  a()\nend` |
| TC-03 | 06-03 | `while c do\n  work()<caret>\nend` | Remove enclosing block | *(empty / whitespace)* |
| TC-04 | 06-04 | `function f()\n  do\n    if q then g()<caret> end\n  end\nend` | (no confirm) collect options | offered options include `Unwrap 'if'`, `Unwrap 'do'`, `Unwrap 'function'`; `collectAffectedElements` for `Unwrap 'if'` reports the `g()` body range |
| TC-05 | 06-01 | `while c do\n  step()<caret>\nend` | Unwrap 'while' | `step()` |
| TC-06 | 06-01 | `for i = 1, 3 do\n  use(i)<caret>\nend` | Unwrap 'for' | `use(i)` |
| TC-07 | 06-01 | `do\n  scoped()<caret>\nend` | Unwrap 'do' | `scoped()` |
| TC-08 | 06-01 | `function f()\n  body()<caret>\nend` | Unwrap 'function' | `body()` |
| TC-09 | 06-02 | `if a then\n  p()\nelseif b then\n  q()\nelse\n  r()<caret>\nend` | Remove 'else' branch | `if a then\n  p()\nelseif b then\n  q()\nend` |
