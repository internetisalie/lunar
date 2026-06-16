---
id: COMP-08-DESIGN
title: Auto Complete Design
type: design
parent_id: COMP-08
status: done
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
The built `then`/`do`/`function`/`repeat` auto-close (COMP-08-01) is retained and hardened with a
**balance check** (COMP-08-02, §3.2) that never inserts a redundant terminator, **full opener coverage**
including table `{`→`}` (COMP-08-03, §3.3), **between-pair smart indent** via a new
`LuaEnterBetweenBlockHandler` (COMP-08-04, §3.4), and a `postProcessEnter` **reformat + caret**
placement step (COMP-08-05, §3.5). The opener→terminator pairs are unified with `LuaPairedBraceMatcher`
through a shared `LuaBlockPairs` object (§2.3).

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

### 2.2 Grounded PSI facts (the basis for all algorithms below)

These are the load-bearing structural facts confirmed in this repo's grammar
(`lang/psi/lua.bnf`) and generated PSI (`src/main/gen/.../lang/psi/`):

- **Block-bearing statements** all implement the hand-written marker interface
  `net.internetisalie.lunar.lang.psi.LuaBlockParent`
  (`LuaBaseElements.kt:178`, `fun getBlockList(): List<LuaBlock>`):
  `LuaIfStatement`, `LuaWhileStatement`, `LuaNumericForStatement`,
  `LuaGenericForStatement`, `LuaRepeatStatement`, `LuaDoStatement`, and the **three** function
  forms — `LuaFuncDecl` (named `function f() … end`, `lua.bnf:170`), `LuaLocalFuncDecl`
  (`local function f() … end`, `lua.bnf:158`), and `LuaFuncDef` (anonymous `function() end`
  expression, `lua.bnf:255`) — all in `src/main/gen/.../lang/psi/`. **All algorithms dispatch on the
  `LuaBlockParent` interface, never on a concrete function class** — do NOT narrow the parent lookup
  to `LuaFuncDef::class.java` (that would break the common named/local-function cases).
  `LuaTableConstructor` is an **expression**, not a `LuaBlockParent`, and is handled separately (§3.3).
- **The terminator token is a DIRECT CHILD of the statement node, a sibling of `LuaBlock`** —
  NOT a child of `LuaBlock`. From the grammar (`lua.bnf`):
  - `ifStatement ::= IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END` (`lua.bnf:133`)
  - `whileStatement ::= WHILE expr DO block END` (`lua.bnf:125`)
  - `numericForStatement ::= FOR IDENTIFIER '=' expr ',' expr [',' expr] DO block END` (`lua.bnf:136`)
  - `genericForStatement ::= FOR nameList IN exprList DO block END` (`lua.bnf:140`)
  - `doStatement ::= DO block END` (`lua.bnf:121`)
  - `repeatStatement ::= REPEAT block UNTIL expr` (`lua.bnf:129`)
  - `funcDef ::= FUNCTION funcBody` where `funcBody ::= '(' [parList] ')' block END` is **private**
    (inlined), so `END` is a direct child of the function node — `LuaFuncDecl`/`LuaLocalFuncDecl`
    (named/local, `lua.bnf:170,158`) or `LuaFuncDef` (anonymous, `lua.bnf:255,261`)
  - `tableConstructor ::= '{' [fieldList] '}'` — `LCURLY`/`RCURLY` are direct children of
    `LuaTableConstructor` (`lua.bnf:265`)
- **Terminator detection is therefore a single child-type query** on the statement node:
  `statement.node.findChildByType(terminatorType) != null` means "already balanced". (`ASTNode.findChildByType`
  is a standard platform API; the codebase already uses `PsiTreeUtil.getChildrenOfType` for `LuaBlock`
  at `LuaPsiImplUtil.kt:63`.) On an *unbalanced* statement (opener typed, no terminator yet) the parser
  still produces the `LuaBlockParent` node (with an error/missing-terminator), so the lookup returns
  null and we insert.
- **Element-type constants** (all in `net.internetisalie.lunar.lang.psi.LuaElementTypes`, confirmed in
  `LuaElementTypes.java`): openers `IF`, `WHILE`, `FOR`, `THEN`, `DO`, `FUNCTION`, `REPEAT`, `LCURLY`;
  terminators `END`, `UNTIL`, `RCURLY`. The built handler already keys off `THEN`/`DO`/`FUNCTION`/`REPEAT`
  (`LuaEnterHandler.kt:38`).

### 2.3 Shared opener→terminator map (single source of truth)

`LuaPairedBraceMatcher` (`lang/syntax/LuaPairedBraceMatcher.kt`) already declares these pairs as
structural `BracePair`s (`LCURLY`/`RCURLY`, `REPEAT`/`UNTIL`, `DO`/`END`, `FUNCTION`/`END`, `IF`/`END`).
To avoid a second hard-coded copy, COMP-08 introduces ONE shared table reused by both the brace matcher
and the Enter handler:

```kotlin
// net.internetisalie.lunar.lang.syntax.LuaBlockPairs
object LuaBlockPairs {
    // opener element type -> terminator element type, used by the balance check (§3.2)
    val terminatorByOpener: Map<IElementType, IElementType> = mapOf(
        LuaElementTypes.THEN to LuaElementTypes.END,      // if … then … end
        LuaElementTypes.DO to LuaElementTypes.END,        // while/for … do … end, bare do … end
        LuaElementTypes.FUNCTION to LuaElementTypes.END,  // function … end
        LuaElementTypes.REPEAT to LuaElementTypes.UNTIL,  // repeat … until
        LuaElementTypes.LCURLY to LuaElementTypes.RCURLY  // { … }
    )
    // the literal text inserted on the next line for each terminator
    val insertTextFor: Map<IElementType, String> = mapOf(
        LuaElementTypes.END to "end",
        LuaElementTypes.UNTIL to "until",
        LuaElementTypes.RCURLY to "}"
    )

    // owner-NODE-kind -> terminator, used by §3.4 between-pair indent (which starts from the
    // owner node, not the opener leaf). Distinct from terminatorByOpener above (keyed by leaf).
    fun terminatorForOwner(owner: PsiElement): IElementType = when (owner) {
        is LuaRepeatStatement -> LuaElementTypes.UNTIL
        is LuaTableConstructor -> LuaElementTypes.RCURLY
        else -> LuaElementTypes.END   // all other LuaBlockParent (if/while/for/do/function*)
    }
}
```

**Two maps, two purposes — keep them separate (do NOT collapse):**
- `terminatorByOpener` is keyed by the **opener leaf at `offset-1`** (`THEN`/`DO`/`FUNCTION`/`REPEAT`/
  `LCURLY`) — what the Enter handler sees. `if … then` keys on `THEN`, and `while`/`for` key on their
  `DO`.
- `LuaPairedBraceMatcher` keys its `if` pair on **`IF`→`END`** (it highlights the `if`↔`end` *span*),
  not `THEN`. So the matcher's pairs are **not** 1:1 with `terminatorByOpener`. **Do NOT refactor the
  matcher to derive from `terminatorByOpener`** — that would re-key brace matching from `IF` to `THEN`
  and break `if`↔`end` highlighting (violating its "no behavior change" exit criterion). The
  "reuse, don't duplicate" requirement is satisfied by `LuaBlockPairs` being the single source for the
  **Enter handler's** maps; the matcher retains its own `BracePair` list (it serves a different concern
  — span highlighting/navigation). If a shared constant is still wanted, add a separate
  `LuaBlockPairs.braceMatcherPairs` keyed on `IF`/`REPEAT`/`DO`/`FUNCTION`/`LCURLY`; it is NOT the same
  map as `terminatorByOpener`.

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

### 3.2 Balance check before insert (COMP-08-02 — the bug fix)

**Purpose:** never insert a redundant terminator when the enclosing block already has its matching
`end`/`until`/`}`. Replaces step 5's unconditional insert.

- **Input → Output:** `(file, editor, openerLeaf, openerType, offset)` →
  `EnterHandlerDelegate.Result` (inserts only when unbalanced).
- **Steps** (run inside `preprocessEnter`, after the existing `commitDocument` + leaf lookup):
  1. `terminatorType = LuaBlockPairs.terminatorByOpener[openerType]`; if null → `Result.Continue`
     (the leaf is not a recognized opener).
  2. Find the enclosing statement node that owns this opener:
     `statement = PsiTreeUtil.getParentOfType(openerLeaf, LuaBlockParent::class.java, /*strict=*/false)`.
     - For `THEN`/`DO`/`FUNCTION`/`REPEAT` the opener leaf is a direct child of its `LuaBlockParent`
       statement, so this returns the correct node.
  3. If `statement == null` → fall through to the legacy unconditional insert (step 6) — a leaf with no
     parsed `LuaBlockParent` is a freshly-typed opener with no block yet; inserting is correct.
  4. **Balance test:** `alreadyBalanced = statement.node.findChildByType(terminatorType) != null`.
     - This is exact for the *immediate* block: because the terminator is a direct child of the
       statement node (§2.2), `findChildByType` checks only this opener's own terminator and is NOT
       confused by terminators of nested blocks (those live under the child `LuaBlock`, not as direct
       children of `statement`).
  5. If `alreadyBalanced` → **do not insert**. Return `Result.DefaultForceIndent` so the platform still
     opens and indents a fresh body line (this is exactly Test Case 2: `if true then⏎end` ⇒ a new
     indented body line, no second `end`).
  6. Else (unbalanced) → insert `"\n" + LuaBlockPairs.insertTextFor[terminatorType]` at `offset`, move
     caret to `offset`, return `Result.DefaultForceIndent` (the existing behavior, now gated).
- **Nesting / partial-block / EOF handling:**
  - *Nested unbalanced openers* (`if a then⏎ if b then⏎`): each `if` is its own `LuaIfStatement`; the
    inner one's `node.findChildByType(END)` is null → inner `end` inserted, outer untouched. Correct.
  - *Partial outer, balanced inner*: the balance test is scoped to the statement that owns the caret's
    opener, so an inner balanced block never suppresses the outer insert.
  - *EOF / unparseable tail*: when the opener is the last token, the parser yields a `LuaBlockParent`
    with no terminator child → `findChildByType` null → insert. Correct.

### 3.3 Full opener coverage incl. table `{`→`}` (COMP-08-03)

**Purpose:** extend the recognized-opener set from the built four (`THEN`/`DO`/`FUNCTION`/`REPEAT`) to
every Lua block opener plus the table literal, all keyed off element types through `LuaBlockPairs`.

- **Opener→terminator map** (the value of `LuaBlockPairs.terminatorByOpener`, §2.3):

  | Construct | Opener leaf at `offset-1` | Owning `LuaBlockParent` (or expr) | Terminator inserted |
  |-----------|---------------------------|-----------------------------------|----------------------|
  | `if … then … end` | `THEN` | `LuaIfStatement` | `END` → `"end"` |
  | `while … do … end` | `DO` | `LuaWhileStatement` | `END` → `"end"` |
  | numeric `for … do … end` | `DO` | `LuaNumericForStatement` | `END` → `"end"` |
  | generic `for … do … end` | `DO` | `LuaGenericForStatement` | `END` → `"end"` |
  | bare `do … end` | `DO` | `LuaDoStatement` | `END` → `"end"` |
  | `function … end` | `FUNCTION` | `LuaFuncDecl` (named) / `LuaLocalFuncDecl` (local) / `LuaFuncDef` (anon) — all `LuaBlockParent` | `END` → `"end"` |
  | `repeat … until` | `REPEAT` | `LuaRepeatStatement` | `UNTIL` → `"until"` |
  | table `{ … }` | `LCURLY` | `LuaTableConstructor` (expr) | `RCURLY` → `"}"` |

- **How each opener is reached:** the handler only inspects the single leaf at `offset-1` and looks its
  type up in `LuaBlockPairs.terminatorByOpener`. Note `while`/`for` reach their `do` first — the relevant
  opener leaf is `DO` (not `WHILE`/`FOR`), which is why the map keys on `THEN`/`DO`/…, NOT on the
  statement keyword. `if … then` likewise keys on `THEN`. This means a single uniform lookup covers all
  seven keyword forms with no per-statement branching.
- **Table-specific branch (`LCURLY`):** the balance check (§3.2) uses
  `PsiTreeUtil.getParentOfType(openerLeaf, LuaTableConstructor::class.java, false)` instead of
  `LuaBlockParent` (tables are not `LuaBlockParent`), then the same
  `statement.node.findChildByType(RCURLY) != null` test. Insert `"\n}"`. (Test Case 3.)
- **Steps:** identical to §3.1 + §3.2 with the parent type chosen by opener:
  1. `terminatorType = LuaBlockPairs.terminatorByOpener[openerType]`; null → `Continue`.
  2. `parentClass = if (openerType == LCURLY) LuaTableConstructor::class.java else LuaBlockParent::class.java`.
  3. Run the §3.2 balance check against `parentClass`; insert `insertTextFor[terminatorType]` if unbalanced.

### 3.4 Between-pair smart indent (COMP-08-04)

**Purpose:** when Enter is pressed with the caret **between** an already-matched opener and its
terminator on the SAME logical construct (e.g. caret right after `function f()` whose `end` already
exists), open a blank body line indented to the nested level and insert **nothing**.

This is the same observable outcome as §3.2 step 5 for the keyword case, but it is split into its own
delegate so it also fires when the caret is not immediately after the opener leaf (e.g. between an
opener and terminator separated by whitespace) and so the two concerns (insert vs. indent-only) stay
small per the engineering contract's ≤30-line rule.

- **New class:** `net.internetisalie.lunar.lang.completion.LuaEnterBetweenBlockHandler :
  EnterHandlerDelegateAdapter`.
- **Input → Output:** `(file, editor, caretOffset)` → `Result` (`DefaultForceIndent` when between a
  matched pair, else `Continue`).
- **Steps (`preprocessEnter`):**
  1. If `file !is LuaFile` → `Continue`. `offset = caretOffset.get()`; if `offset == 0` → `Continue`.
  2. `commitDocument`. `leaf = file.findElementAt(offset - 1)`; null → `Continue`.
  3. `owner = PsiTreeUtil.getParentOfType(leaf, LuaBlockParent::class.java, false)
       ?: PsiTreeUtil.getParentOfType(leaf, LuaTableConstructor::class.java, false)`; null → `Continue`.
  4. `terminatorType = LuaBlockPairs.terminatorForOwner(owner)` (§2.3 — owner-node-kind map:
     `LuaRepeatStatement`→`UNTIL`, `LuaTableConstructor`→`RCURLY`, every other `LuaBlockParent`→`END`).
     This map is keyed on the owner NODE, unlike `terminatorByOpener` which is keyed on the opener leaf.
  5. `terminator = owner.node.findChildByType(terminatorType)`; if null → `Continue` (unmatched — that
     case belongs to §3.2's insert path, not here).
  6. **Between test:** `offset` lies strictly between the opener leaf's end offset and
     `terminator.startOffset`. If not → `Continue`.
  7. Return `Result.DefaultForceIndent` — open and indent a blank body line; insert nothing.
     (`DefaultForceIndent` drives the platform to re-indent the new line to the body level; §3.5
     guarantees the caret sits on that indented line.)
- **Ordering vs. §3.1/§3.2 handler:** both are `enterHandlerDelegate`s. §3.4 returns `Continue` whenever
  the pair is unmatched, and §3.2 only inserts when unmatched; for a *matched* pair §3.2 returns
  `DefaultForceIndent` without inserting too — so whichever runs first, the matched case never
  double-acts (no insert happens in either) and the unmatched case is owned solely by §3.2. They cannot
  both insert. See risks-and-gaps DR-03.

### 3.5 Reformat + caret placement (COMP-08-05)

**Purpose:** after a terminator is inserted (§3.2/§3.3), re-indent the affected range and leave the
caret on the correctly-indented body line, rather than relying on `DefaultForceIndent` alone (which
indents the *new* line but not always the inserted terminator line).

- **Phase:** `postProcessEnter(file, editor, dataContext): Result` on the same handler class
  (`LuaEnterHandler`), mirroring how `lang.format.LuaEnterHandlerDelegate` does its work in
  `postProcessEnter`.
- **Coordination — prefer STATELESS re-derivation (no instance field).** The platform registers ONE
  `EnterHandlerDelegate` instance and reuses it across every editor/caret, so a mutable
  `private var pendingReformatRange` would be **shared mutable state** — unsafe under multi-caret or
  interleaved Enter actions. The existing DOC `lang.format.LuaEnterHandlerDelegate` is deliberately
  **stateless** (it re-derives everything in `postProcessEnter` from the committed document) for exactly
  this reason, and COMP-08-05 MUST follow that pattern: `postProcessEnter` recomputes whether the line
  just above the caret is a freshly-inserted terminator (commit the document, inspect the leaf at the
  caret / previous line) and reindents from that, carrying **no** state between phases. A transient
  instance field is the rejected alternative — see risks-and-gaps Risk 1.3 + DR-04.
- **Steps (`postProcessEnter`, stateless):**
  1. If `file !is LuaFile` → `Result.Continue`. `PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)`.
  2. **Re-derive** the target without any carried state: `bodyLine = document.getLineNumber(caret.offset)`;
     inspect the line immediately below the caret — if its first non-blank leaf is a terminator
     (`END`/`UNTIL`/`RCURLY`, i.e. an `insertTextFor` value just placed by §3.2/§3.3) → this Enter opened a
     block body, so proceed; otherwise → `Result.Continue` (nothing to reformat). This replaces the
     instance-field check and is safe under shared-instance reuse.
  3. `terminatorLine = bodyLine + 1` (the re-derived terminator line).
  4. `val csm = CodeStyleManager.getInstance(file.project)`
     (`com.intellij.psi.codeStyle.CodeStyleManager`).
  5. Re-indent each line in `range` (the body line and the terminator line):
     `csm.adjustLineIndent(file, editor.document.getLineStartOffset(line))` for `line in bodyLine..terminatorLine`.
     `adjustLineIndent(PsiFile, Int): Int` is the standard platform call.
  6. Place the caret at the end of the now-indented body line:
     `editor.caretModel.moveToOffset(csm.adjustLineIndent(file, bodyLineStart))` — `adjustLineIndent`
     returns the offset of the first non-blank position after indenting, which is exactly where the
     caret should rest.
  7. Return `Result.Stop` so the default post-processing does not re-indent again.
- **Why `preprocessEnter` cannot do it:** the body newline does not exist until the platform applies the
  Enter; `adjustLineIndent` needs a committed document reflecting the inserted lines, available only in
  `postProcessEnter`. See risks-and-gaps DR-04.

## 4. External Data & Parsing
None — operates on the editor document and PSI only.

## 5. Data Flow
### Example: `function f()`⏎
Caret after `function` keyword, Enter → leaf at `offset-1` is `FUNCTION` → insert `\nend`, caret
returns to the line after `function f()` → `DefaultForceIndent` re-indents → balanced
`function f()\n    <caret>\nend`. (`repeat`⏎ → inserts `\nuntil`.)

## 6. Edge Cases
- **Already-balanced block** (`then`/`do`/`function`/`repeat`/`{` whose terminator already exists) →
  §3.2 balance check suppresses the redundant insert and instead opens an indented body line
  (Test Case 2). This is the COMP-08-02 fix; the formerly-noted limitation is resolved.
- Caret at offset 0 → no-op.
- Non-Lua file → no-op.
- Opener leaf with no parsed `LuaBlockParent` (freshly typed, EOF) → insert (§3.2 step 3).
- Nested unbalanced openers → only the innermost (caret-owning) block is closed (§3.2 nesting note).
- Table `{` (`LuaTableConstructor`, not a `LuaBlockParent`) → balance check uses the
  `LuaTableConstructor` parent and `RCURLY` terminator (§3.3).

## 7. Integration Points
```xml
<!-- plugin.xml (extensions defaultExtensionNs="com.intellij") -->

<!-- EXISTING (plugin.xml:188) — gains the §3.2 balance check, §3.3 opener coverage and
     §3.5 postProcessEnter reformat; registration unchanged. -->
<enterHandlerDelegate implementation="net.internetisalie.lunar.lang.completion.LuaEnterHandler"/>

<!-- NEW (COMP-08-04) — between-pair smart indent; register adjacent to the above. -->
<enterHandlerDelegate
    implementation="net.internetisalie.lunar.lang.completion.LuaEnterBetweenBlockHandler"/>

<!-- NOTE: the separate enterHandlerDelegate at plugin.xml:177
     (net.internetisalie.lunar.lang.format.LuaEnterHandlerDelegate) is the DOC-comment
     continuation feature, not COMP-08 — see §1. All registrations are intentional;
     each delegate returns Result.Continue when not applicable so the platform runs them
     in turn without conflict (see risks-and-gaps DR-03). -->
```
No new `LuaBlockPairs` registration is needed — it is a plain `object` consumed by
`LuaPairedBraceMatcher` and the two Enter handlers.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Status |
|-------------|----------|--------------------------|--------|
| COMP-08-01 Block Auto-close (`then`/`do`/`function`/`repeat`) | M | §2.1, §3.1 | **Built** |
| COMP-08-02 Balance check (no redundant `end`) | M | §2.2, §2.3, §3.2 | **Done** — TC 2 |
| COMP-08-03 Full opener coverage incl. `{`→`}` | S | §2.3, §3.3 | **Done** — TC 3 |
| COMP-08-04 Between-pair smart indent | S | §3.4 (`LuaEnterBetweenBlockHandler`) | **Done** — TC 4 |
| COMP-08-05 Reformat + caret placement | S | §3.5 (`postProcessEnter`) | **Done** |

## 9. Alternatives Considered
- **Brace-matcher / typed-handler** instead of an Enter handler: rejected — the requirement is
  specifically Enter-after-opener; `EnterHandlerDelegate` is the platform-idiomatic hook.
- **Optional clarity rename**: rename `lang.format.LuaEnterHandlerDelegate` →
  `LuaDocCommentEnterHandler` (and/or move under `luadoc/`) so the two handlers aren't confused.
  Cosmetic; tracked as a `MAINT` nicety, not required for COMP-08.

## 10. Open Questions
_None._ The redundant-`end` hardening (§3.1/§6) is an optional improvement, not a blocking decision.
