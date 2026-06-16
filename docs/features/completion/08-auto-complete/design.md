---
id: COMP-08-DESIGN
title: Auto Complete Design
type: design
parent_id: COMP-08
status: in_progress
folders:
  - "[[features/completion/08-auto-complete/requirements|requirements]]"
---

# Technical Design: COMP-08 — Block Auto-Complete (Enter handler)

When the user presses Enter immediately after a block-opening keyword, insert the matching closing
keyword on the next line so the block is balanced.

## 1. Architecture Overview

### Current State (what is actually built)
- `net.internetisalie.lunar.lang.completion.LuaEnterHandler`
  (`src/main/kotlin/.../lang/completion/LuaEnterHandler.kt:16`) extends
  `com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter` and overrides
  `preprocessEnter`.
- Registered at `plugin.xml:188`.
- Covered by `src/test/kotlin/.../lang/completion/LuaEnterHandlerTest.kt`.

### Prior Art in This Repo — the second Enter handler is COMPLEMENTARY, not a duplicate
There is a **second** `enterHandlerDelegate` registration at `plugin.xml:177`:
`net.internetisalie.lunar.lang.format.LuaEnterHandlerDelegate`
(`src/main/kotlin/.../lang/format/LuaEnterHandlerDelegate.kt:16`). It is **not** the same feature:

| Class | EP line | Phase used | Trigger | Action | Owning feature |
|-------|---------|-----------|---------|--------|----------------|
| `lang.completion.LuaEnterHandler` (this) | 188 | `preprocessEnter` | caret after `then`/`do`/`function`/`repeat` | insert `\nend` (or `\nuntil`) | **COMP-08** |
| `lang.format.LuaEnterHandlerDelegate` | 177 | `postProcessEnter` (its `preprocessEnter` is a no-op) | Enter after a `---` LuaDoc line | continue `--- ` / expand a doc template via `LuaDocGenerator` | **DOC** (doc-comment continuation) |

They act on disjoint triggers and mostly different phases, and each returns `Result.Continue` when
not applicable, so the platform runs both without conflict. **No de-duplication is required.** The
only real issue is the **confusingly similar names** (`LuaEnterHandler` vs `LuaEnterHandlerDelegate`)
— see §9 for an optional clarity rename. COMP-08 owns only the `completion.LuaEnterHandler`.

### Target State
Unchanged behavior, now documented and grounded. (Optional hardening in §6: verify a matching `end`
is not already present before inserting.)

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.completion.LuaEnterHandler`
- **Responsibility**: auto-insert the closing keyword for an opened block on Enter.
- **Threading**: platform-invoked on the EDT during the Enter action; commits the document before
  reading PSI.
- **Collaborators**: `LuaFile`, `LuaBlock`, `LuaElementTypes`, `PsiDocumentManager`, `PsiTreeUtil`.
- **Key API** (as built):
  ```kotlin
  class LuaEnterHandler : EnterHandlerDelegateAdapter() {
      override fun preprocessEnter(
          file: PsiFile, editor: Editor, caretOffset: Ref<Int>, caretAdvance: Ref<Int>,
          dataContext: DataContext, originalHandler: EditorActionHandler?
      ): EnterHandlerDelegate.Result
  }
  ```

## 3. Algorithms

### 3.1 Closing-keyword insertion
- **Input → Output**: `(file, editor, caretOffset)` → `EnterHandlerDelegate.Result`, mutating the
  document when a block opener precedes the caret.
- **Steps** (as built, `LuaEnterHandler.kt:25-51`):
  1. If `file !is LuaFile` → `Continue`.
  2. `offset = caretOffset.get()`; if `offset == 0` → `Continue`.
  3. `PsiDocumentManager.getInstance(project).commitDocument(document)`.
  4. `element = file.findElementAt(offset - 1)`; if null → `Continue`. Take `element.node.elementType`.
  5. If the type is `LuaElementTypes.THEN`, `DO`, `FUNCTION`, or `REPEAT`:
     - `keyword = if (type == REPEAT) "until" else "end"`.
     - `document.insertString(offset, "\n$keyword")`; `editor.caretModel.moveToOffset(offset)`.
     - return `Result.DefaultForceIndent` (platform re-indents the inserted lines).
  6. Otherwise → `Continue`.
- **Edge handling**: caret at file start (`offset==0`) is skipped; any non-opener leaf → `Continue`.
- **Known limitation (optional hardening)**: step 5 does not check whether a matching `end`/`until`
  already exists for the block (the `LuaBlock` parent is looked up but not used for this). Result:
  pressing Enter right after `then` when the block is already closed inserts a redundant `end`. See
  §6.

## 4. External Data & Parsing
None — operates on the editor document and PSI only.

## 5. Data Flow
### Example: `function f()`⏎
Caret after `function` keyword, Enter → leaf at `offset-1` is `FUNCTION` → insert `\nend`, caret
returns to the line after `function f()` → `DefaultForceIndent` re-indents → balanced
`function f()\n    <caret>\nend`. (`repeat`⏎ → inserts `\nuntil`.)

## 6. Edge Cases
- `then`/`do`/`function`/`repeat` followed by an already-balanced block → redundant insert (the
  known limitation above). Optional hardening: before inserting, walk the `LuaBlock` parent
  (`PsiTreeUtil.getParentOfType(element, LuaBlock::class.java)`) and skip insertion if a matching
  terminator leaf is already present.
- Caret at offset 0 → no-op.
- Non-Lua file → no-op.

## 7. Integration Points
```xml
<!-- plugin.xml:188 (extensions defaultExtensionNs="com.intellij") -->
<enterHandlerDelegate implementation="net.internetisalie.lunar.lang.completion.LuaEnterHandler"/>
<!-- NOTE: the separate enterHandlerDelegate at plugin.xml:177
     (net.internetisalie.lunar.lang.format.LuaEnterHandlerDelegate) is the DOC-comment
     continuation feature, not COMP-08 — see §1. Both registrations are intentional. -->
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Status |
|-------------|----------|--------------------------|--------|
| COMP-08-01 Block Auto-close (`then`/`do`/`function`) | M | §2.1, §3.1 | **Built** — also covers `repeat`→`until` (beyond the `Must`) |
| COMP-08-02 Balance check (no redundant `end`) | M | §3.1/§6 (limitation noted) | **pending** — the fix is sketched in §6; bug fix added 2026-06-15 |
| COMP-08-03…05 (opener coverage incl. `{`→`}` / between-pair indent / reformat+caret) | S | **pending design** | Added 2026-06-15 from the competitor survey — design sections to be added before implementation |

## 9. Alternatives Considered
- **Brace-matcher / typed-handler** instead of an Enter handler: rejected — the requirement is
  specifically Enter-after-opener; `EnterHandlerDelegate` is the platform-idiomatic hook.
- **Optional clarity rename**: rename `lang.format.LuaEnterHandlerDelegate` →
  `LuaDocCommentEnterHandler` (and/or move under `luadoc/`) so the two handlers aren't confused.
  Cosmetic; tracked as a `MAINT` nicety, not required for COMP-08.

## 10. Open Questions
_None._ The redundant-`end` hardening (§3.1/§6) is an optional improvement, not a blocking decision.
