---
id: EDITOR-02
title: "02: Spellchecking"
type: feature
parent_id: EDITOR
status: "done"
priority: "high"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-02 Spellchecking

Wire Lua comments, string literals, and identifiers into the platform spellchecker. Cheap given
the existing lexer, and its absence is something users unconsciously register as "thin."

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-02-01` | **Comment spellcheck** | Words inside line/block comments (and LuaDoc/LuaCATS prose) are spell-checked as plain text. | **M** | Full |
| `EDITOR-02-02` | **String literal spellcheck** | Words inside string literals are spell-checked (escapes and long-bracket strings handled). | **S** | Full |
| `EDITOR-02-03` | **Identifier spellcheck** | Declarations are tokenized (camelCase/snake_case split) and spell-checked; typo highlighting on names. | **S** | Full |
| `EDITOR-02-04` | **Quick fixes** | "Typo" quick-fixes: change-to suggestions, Save-to-dictionary, and Rename integration for identifiers. | **S** | Full |
| `EDITOR-02-05` | **Suppression** | Keywords, known stdlib names, and LuaCATS type tokens are excluded from identifier spellcheck. | **C** | Full |

> **Implementation notes (2026-07-10):**
> - **Identifier declarations (§3.3 correction):** the design assumed declaration names are a
>   `PsiNameIdentifierOwner` (`LuaNameDeclElement`), but in this PSI *only `::labels::` are* — every
>   other name is a plain `LuaNameRef` (identical to a reference). The tokenizer therefore routes
>   `LuaNameRef` and emits only when the parent is a declaration-only container (`LuaAttName`,
>   `LuaLocalFuncDecl`, `LuaNameList`), covering local variables, local functions, parameters, and
>   generic-`for` variables. **Not** covered (documented limitations): the base name of a global
>   `function tbl.foo()` (leading `tbl` is a table *reference*) and numeric-`for` variables (a bare
>   `IDENTIFIER`, not a `LuaNameRef`).
> - **Tests:** platform spellcheck runs through Grazie asynchronously, so `<TYPO>`/`checkHighlighting`
>   assertions are unreliable in-process. `LuaSpellcheckingStrategyTest` verifies routing + tokenizer
>   output + suppression deterministically; the live "typo underlined" behaviour is a VNC-gate item.

## 2. Technical Details
- EP: `com.intellij.spellchecker.support` (`SpellcheckingStrategy` + `Tokenizer` per PSI element type).
- Use `TokenizerBase`/`Splitter` variants: plain-text tokenizer for comments/strings, identifier
  splitter for declaration names; return `EMPTY_TOKENIZER` for keywords/operators/numbers.
- Identifier rename quick-fix leverages the existing `refactoringSupport` / `namesValidator`.
- Reference: `intellij-community` `*SpellcheckingStrategy` (e.g. Properties, JSON, Groovy).

## 3. Test Cases

All cases use `myFixture` + `<TYPO descr="Typo: In word '…'">…</TYPO>` markers and
`checkHighlighting` (real-flow DoD gate). See design §5 and implementation-plan Verification Tasks.

| TC | Input | Action | Expected Output | Covers |
| :--- | :--- | :--- | :--- | :--- |
| TC-1 | `-- helo world` | highlight | `<TYPO>helo</TYPO>` flagged; `world` not | 02-01 |
| TC-2 | `local s = "helo"` | highlight | `helo` flagged inside the string | 02-02 |
| TC-3 | `local s = [==[helo]==]` | highlight | `helo` flagged at the inner offset (not on `[==[`) | 02-02 |
| TC-4 | `local recieveBuffer = 1` | highlight + Alt+Enter | `recieve` flagged; **Rename** fix offered | 02-03, 02-04 |
| TC-5 | `local pairs = 1` | highlight | no typo (stdlib name suppressed) | 02-05 |
| TC-6 | `#!/usr/bin/lua\n` | highlight | no typo (shebang skipped) | 02-01 |
| TC-7 | `---@class Buildr\n---helo` | highlight | prose `helo` flagged; `Buildr` / `class` not | 02-01, 02-05 |
| TC-8 | `-- helo` | Alt+Enter | **Change to…** and **Save 'helo' to dictionary** fixes offered | 02-04 |
