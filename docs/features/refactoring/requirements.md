---
id: "REFACT/INTENT"
title: "REFACT: Refactoring & Intentions"
type: "epic"
priority: low
status: done
vf_icon: ✅
folders:
  - "[[features]]"
---

# Refactoring & Intentions Requirements (`REFACT`, `INTENT`)

Lunar provides tools to safely restructure code and perform automated transformations.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `REFACT-01` | **Rename Refactoring** | **M** | Safely rename symbols and update all references across the project. |
| `REFACT-02` | **Introduce Variable** | **S** | Extract an expression into a local variable and replace all occurrences. |
| `REFACT-03` | **Safe Delete** | **S** | Verify if a symbol is unused before allowing its deletion. |
| `REFACT-04` | **Label Refactoring** | **C** | Support renaming and refactoring of `goto` labels and their references. |
| `REFACT-05` | **Name Validator** | **S** | Validate names for idiomatic conventions and suggest corrections. |
| `REFACT-06` | **Stubs for Declaring Identifiers** | **C** | Generate `.lua` stub files for declaring external APIs. |
| `REFACT-07` | **In-place (Inline) Rename** | **S** | Rename a file-local Lua declaration by typing in the editor, under a live template, instead of through the modal dialog. |
| `INTENT-01` | **String Style Conversion** | **C** | Switch between different string quote styles. |
| `INTENT-02` | **Invert If Statement** | **C** | Automatically flip an `if-else` block and its condition. |
| `INTENT-03` | **Name Suggestion** | **S** | Suggest idiomatic names when creating new variables or functions. |

---

## Detailed Implementation Status

### REFACT-01: Rename Refactoring
- **Status**: **Partial** — locals, parameters, `for` variables, local functions and globals rename
  end to end, in this file and across the project, via
  `net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor` (registered
  `plugin.xml`, `<renamePsiElementProcessor>`) plus `LuaNameReference.handleElementRename` and the
  `LuaDeclarationSite` classifier. `function Obj:method()` and function-name receiver segments are
  **refused with a reason** rather than half-applied; conflict detection, `require(...)` rewriting,
  `---@param` propagation and in-place rename are not shipped. See [[REFACT-01]] for the row-level
  status.
- This line read *"**Implemented** (`LuaNameReference.handleElementRename`)"* from `47df3605` until
  2026-08-23. That method **did not exist** at any point before REFACT-01 Phase 2 created it, and the
  false claim is why the feature was believed shipped while renaming an identifier silently corrupted
  the file (BUG-457).

### REFACT-04: Label Refactoring
- **Status**: **Implemented** — `LuaLabelName` is the codebase's only `PsiNameIdentifierOwner`,
  `LuaLabelReference.handleElementRename` rewrites the `goto` side, `LuaFindUsagesProvider` reports
  labels, and `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` enables the in-place
  template for them. Rename itself is the platform default's:
  `LuaRenameProcessor.canProcessElement` excludes `LuaLabelName`/`LuaLabelRef` first and
  unconditionally so that it stays that way.
- The classes this line named until 2026-08-23 — `LuaLabelFindUsagesProvider` and
  `LuaLabelRefactoringSupportProvider` — **do not exist** and never did
  (`grep -rn LuaLabelRefactoringSupportProvider src/` is empty; the only `LuaLabelFindUsagesProvider`
  hit is a historical note in `LuaFindUsagesProvider.kt:15`).

### REFACT-07: In-place (Inline) Rename

- **Status**: **Implemented** — delivered by [[REFACT-07]]. <kbd>Shift+F6</kbd> on a Lua local
  starts an inline template: typing updates the declaration and every usage together,
  <kbd>Enter</kbd> commits, <kbd>Esc</kbd> restores the file byte-for-byte. Verified live in
  GoLand, not only under test. The primitive both in-place routes required — a
  `PsiNameIdentifierOwner` on the declaring `LuaNameRef` — ships on the `nameRef` mixin, so labels
  are no longer the only identifiers with a working inline template.
- Owns `REFACT-01-12`, delegated from [[REFACT-01]] under the same pattern as `REFACT-01-11` →
  [[REFACT-05]] and `REFACT-01-17` → [[REFACT-04]].
