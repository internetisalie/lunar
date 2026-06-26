---
id: MAINT-02-DESIGN
title: "Technical Design"
type: design
parent_id: MAINT-02
status: planned
folders:
  - "[[features/maint/02-label-refactoring/requirements|requirements]]"
---

# Technical Design: MAINT-02 — Label Refactoring

<!-- TDD — the "how". Grounded in real Lunar symbols (grep'd, file:line below). -->

## 1. Architecture Overview

### Current State

```
LuaLabelRef.getReference()                       (LuaLabelRefBaseImpl, LuaBaseElements.kt:99)
  ↓
LuaLabelReference.multiResolve(name)             (LuaLabelReference.kt:16)
  ↓
findLabels(containingFile, name)                 (LuaLabelReference.kt:59)
  → PsiTreeUtil.findChildrenOfType(file, LuaLabel)   [EAGER, whole-file, scope-blind]
  ↓
returns label.labelName.identifier  (the IDENTIFIER leaf)
```

Problems:
1. **Scope-blind** — a `goto X` resolves to *any* `::X::` in the file, including labels in
   sibling blocks or nested functions that Lua's visibility rules forbid.
2. **Eager** — a full-file PSI scan on every resolve.
3. **No rename binding** — `LuaLabelName` is a `PsiNamedElement` (so
   `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` returns true for it,
   `LuaRefactoringSupportProvider.kt:24`) but is **not** a `PsiNameIdentifierOwner`, and no
   `ElementManipulator`/`handleElementRename` exists for `LuaLabelRef`, so renaming a label
   does not reliably rewrite its `goto` references.

### Prior Art in This Repo (grounding)

Searched `src/main` for scope/label/rename machinery. Found and reused:

- **`net.internetisalie.lunar.lang.LuaScopeProcessor`** (`LuaScopeProcessor.kt:25`) +
  **`LuaCompletionScopeProcessor`** (`:144`) — MAINT-04's `PsiScopeProcessor` pattern for
  variables. This design adds **label-specific** sibling processors in the same file,
  modelled on these. *Extended (new sibling classes), not replaced.*
- **`LuaBlock.processDeclarations(...)`** (`LuaBlockExt.kt:24`) — block declaration feeder
  for variables; it gates on `lastParent`/`textOffset` to forbid forward references. Labels
  need the opposite (forward refs legal) and only match `LuaLabel`, so this design adds a
  **separate** `processLabelDeclarations` rather than overloading it. *New sibling function.*
- **`LuaNameReference`** (`LuaNameReference.kt:36`) — the manual ancestor up-walk
  (`while (current != null && current !is PsiFile)`) that drives `processDeclarations`. The
  label walk copies this control-flow shape with a function-boundary stop. *Pattern reused.*
- **`LuaNameDeclElementImpl`** (`LuaBaseElements.kt:48`) — already implements `getName`/
  `setName` (via `LuaElementFactory.createIdentifier`, `LuaElementFactory.kt:12`). Only
  `LuaLabelName` uses this mixin (`lua.bnf:229-232`). This design adds `getNameIdentifier`
  here and changes the interface to `PsiNameIdentifierOwner`. *Extended in place.*
- **`LuaFindUsagesProvider`** (`LuaFindUsagesProvider.kt:54`) already returns true from
  `canFindUsagesFor(LuaLabelName)`, and **`LuaNameReferenceSearcher`** explicitly skips
  labels (`LuaNameReferenceSearcher.kt:36-38,82`) so the platform's default named-element
  searcher drives label usages. *Unchanged — relied upon for rename's reference search.*
- **`LuaNamesValidator`** (`LuaNamesValidator.kt:12`, registered `plugin.xml:264`) validates
  rename input. *Unchanged — reused.*

No EmmyLua-style `LuaLabelStat` exists; the prior stub `design.md` named that fictional type.
The real types are `LuaLabel` / `LuaLabelName` / `LuaLabelRef` (`src/main/gen/.../psi/`).

### Target State

```
LuaLabelReference.multiResolve(name)
  ↓
resolveLabel: walk up PSI ancestors from LuaLabelRef
  ├─ at each LuaBlock: block.processLabelDeclarations(LuaLabelScopeProcessor, state)  [LAZY]
  │     → processor.execute(LuaLabel) matches name → stop
  └─ at a function node (LuaFuncDef/LuaFuncDecl/LuaLocalFuncDecl/LuaGlobalFuncDecl): STOP, fail
  ↓
processor.result : LuaLabelName  →  PsiElementResolveResult

Rename:  RenameProcessor → LuaLabelName.setName(new)          (declaration)
                         → ReferencesSearch → LuaLabelReference.handleElementRename(new)  (gotos)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.LuaLabelScopeProcessor`
- **Responsibility**: resolve a single label by name; stop on first match.
- **Threading**: invoked inside the platform read action that wraps resolution; holds no
  framework refs.
- **Collaborators**: `LuaLabel`, `LuaLabelName` (`lang.psi`); `PsiScopeProcessor`,
  `ResolveState` (platform). Mirrors `LuaScopeProcessor` (`LuaScopeProcessor.kt:25`).
- **Key API** (new file `lang/LuaLabelScopeProcessor.kt`):
  ```kotlin
  class LuaLabelScopeProcessor(val name: String) : PsiScopeProcessor {
      var result: LuaLabelName? = null
          private set
      private var found = false

      override fun execute(element: PsiElement, state: ResolveState): Boolean {
          if (found) return false
          if (element is LuaLabel && element.labelName.identifier.text == name) {
              result = element.labelName
              found = true
              return false            // stop the walk
          }
          return true
      }

      override fun <T : Any?> getHint(hintKey: Key<T>): T? = null
      override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.LuaLabelCompletionScopeProcessor`
- **Responsibility**: collect every visible label name (no early stop) for `goto` completion.
- **Threading**: as §2.1.
- **Collaborators**: `LuaLabel`/`LuaLabelName`; modelled on `LuaCompletionScopeProcessor`
  (`LuaScopeProcessor.kt:144`).
- **Key API** (same new file):
  ```kotlin
  class LuaLabelCompletionScopeProcessor : PsiScopeProcessor {
      val results: MutableMap<String, LuaLabelName> = LinkedHashMap()

      override fun execute(element: PsiElement, state: ResolveState): Boolean {
          if (element is LuaLabel) {
              val labelName = element.labelName
              results.putIfAbsent(labelName.identifier.text, labelName)   // nearest scope wins
          }
          return true                 // always continue
      }
      override fun <T : Any?> getHint(hintKey: Key<T>): T? = null
      override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
  }
  ```

### 2.3 `LuaBlock.processLabelDeclarations` (new extension)
- **Responsibility**: feed a block's `LuaLabel` statements to a processor in source order;
  **no** `lastParent`/`textOffset` gate (forward `goto` is legal).
- **Threading**: read action.
- **Collaborators**: `LuaBlock.statementList`, `LuaLabel`. Lives beside the variable feeder
  in `lang/psi/LuaBlockExt.kt` (`LuaBlockExt.kt:24`).
- **Key API**:
  ```kotlin
  fun LuaBlock.processLabelDeclarations(
      processor: PsiScopeProcessor,
      state: ResolveState,
  ): Boolean {
      for (statement in statementList) {
          if (statement is LuaLabel && !processor.execute(statement, state)) {
              return false            // processor matched → stop walk
          }
      }
      return true
  }
  ```

### 2.4 `net.internetisalie.lunar.lang.LuaLabelReference` (rewritten)
- **Responsibility**: lazy scope-aware resolution, visible-label completion, and rename
  rewrite for `goto` references.
- **Threading**: resolution/completion under read action; `handleElementRename` mutates PSI
  under the platform-supplied write command (no extra wrapping).
- **Collaborators**: §2.1–§2.3; `LuaLabelRef`/`LuaLabelName` (`lang.psi`);
  `LuaNameRefElement.setName` (`LuaBaseElements.kt:69-84`, inherited by `LuaLabelRefImpl`).
- **Key API** (replaces the current body of `LuaLabelReference.kt`):
  ```kotlin
  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
      val target = resolveLabel() ?: return ResolveResult.EMPTY_ARRAY
      return arrayOf(PsiElementResolveResult(target))
  }

  override fun resolve(): PsiElement? = resolveLabel()

  private fun resolveLabel(): LuaLabelName? {
      val ref = myElement ?: return null
      val processor = LuaLabelScopeProcessor(name)
      walkLabelScopes(ref) { block -> block.processLabelDeclarations(processor, ResolveState.initial()) }
      return processor.result
  }

  override fun handleElementRename(newElementName: String): PsiElement {
      val ref = myElement as? PsiNamedElement ?: return myElement ?: error("no element")
      return ref.setName(newElementName)      // LuaLabelRefImpl/LuaNameRefElementImpl.setName
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
      val resolved = resolveLabel() ?: return false
      val owner = (element as? LuaLabelName)
          ?: (element.parent as? LuaLabelName)          // tolerate IDENTIFIER leaf targets
      return resolved === owner && resolved.identifier.text == name
  }

  override fun getVariants(): Array<Any> {
      val ref = myElement ?: return emptyArray()
      val processor = LuaLabelCompletionScopeProcessor()
      walkLabelScopes(ref) { block -> block.processLabelDeclarations(processor, ResolveState.initial()) }
      return processor.results.values
          .map { LookupElementBuilder.create(it.identifier.text).withIcon(FILE) }
          .toTypedArray()
  }
  ```
  `walkLabelScopes` is the shared §3.1 up-walk (private helper in this class).

### 2.5 `LuaNameDeclElement` / `LuaNameDeclElementImpl` → `PsiNameIdentifierOwner`
- **Responsibility**: make `LuaLabelName` a first-class renameable named element.
- **Threading**: PSI mutation under platform write command.
- **Why no parser regeneration**: the generated `LuaLabelName` interface already declares
  `extends LuaNameDeclElement` (`src/main/gen/.../psi/LuaLabelName.java:8`) and
  `LuaLabelNameImpl extends LuaNameDeclElementImpl` (`.../impl/LuaLabelNameImpl.java:14`).
  Widening the **hand-written** base interface/impl in `LuaBaseElements.kt` propagates to
  `LuaLabelName` without touching `lua.bnf` or `src/main/gen`. `LuaNameDeclElementImpl` is
  used only by `labelName` (`lua.bnf:229-232`), so the change is label-local.
- **Key API** (edit `LuaBaseElements.kt:46-63`):
  ```kotlin
  interface LuaNameDeclElement : PsiNameIdentifierOwner      // was : PsiNamedElement

  abstract class LuaNameDeclElementImpl(node: ASTNode) : LuaBaseElement(node), LuaNameDeclElement {
      override fun getName(): String? = nameIdentifier?.text                  // null-safe (no !!)
      override fun getNameIdentifier(): PsiElement? = findChildByType(LuaElementTypes.IDENTIFIER)
      override fun setName(newName: String): PsiElement {                     // unchanged body
          val identifierNode = node.findChildByType(LuaElementTypes.IDENTIFIER)
          if (identifierNode != null) {
              val newIdentifier = LuaElementFactory.createIdentifier(project, newName)
              if (newIdentifier != null) node.replaceChild(identifierNode, newIdentifier.node)
          }
          return this
      }
  }
  ```

## 3. Algorithms

### 3.1 Label scope up-walk (`walkLabelScopes`)
- **Input → Output**: `(start: LuaLabelRef, visit: (LuaBlock) -> Boolean)` → `Unit`; `visit`
  returns `false` once the processor matched, which stops the walk.
- **Steps**:
  1. `var current: PsiElement? = start`.
  2. While `current != null` and `current !is PsiFile`:
     a. If `current is LuaBlock`: if `!visit(current)` then **return** (matched).
     b. If `current is LuaFuncDef || current is LuaFuncDecl || current is LuaLocalFuncDecl ||
        current is LuaGlobalFuncDecl`: **return** (function boundary — fail to find).
     c. `current = current.parent`.
  3. Return (reached `PsiFile` with no match).
- **Rules / edge handling**:
  - The function-body `LuaBlock` is a descendant of the function node, so step 2a runs
    **before** 2b for that block — the function's own labels remain visible while the
    enclosing function's do not.
  - No `textOffset` gating: forward and backward `goto` both match (contrast
    `LuaBlock.processDeclarations`, `LuaBlockExt.kt:32-35`).
  - Nearest-enclosing-block precedence is automatic: inner blocks are visited first and the
    resolver stops on the first match.
- **Complexity**: O(depth × statements-per-visited-block); independent of file size.

### 3.2 Completion collection
- Same up-walk as §3.1 but driven with `LuaLabelCompletionScopeProcessor` (never returns
  `false`), so it visits every enclosing block up to the function boundary. `putIfAbsent`
  keeps the nearest declaration when a name repeats. Output: distinct visible label names.

### 3.3 Rename propagation (no new algorithm — platform-driven)
1. User invokes Rename on `::X::` (a `LuaLabelName`, now `PsiNameIdentifierOwner`) or on a
   `goto X` (`PsiElementRenameHandler` substitutes the reference's `resolve()` =
   `LuaLabelName`).
2. `RenameProcessor` calls `LuaLabelName.setName(new)` → §2.5 rewrites the IDENTIFIER leaf.
3. `RenameProcessor` runs `ReferencesSearch.search(labelName)`; the default named-element
   searcher word-scans `X`, finds `LuaLabelRef` references (their `getReference()` is
   `LuaLabelReference`, `LuaBaseElements.kt:100-104`), and keeps those where
   `isReferenceTo(labelName)` holds (§2.4 — only in-scope gotos).
4. For each kept reference, `RenameProcessor` calls
   `LuaLabelReference.handleElementRename(new)` → §2.4 rewrites the `goto` identifier via the
   inherited `setName`.

## 4. External Data & Parsing
None — this feature consumes only PSI produced by Lunar's own parser. No CLI/file/network
input is parsed.

## 5. Data Flow

### Example 1: backward `goto` in the same block (TC 1)
`::done:: … goto done` → `LuaLabelReference.resolve()` → `walkLabelScopes` from the
`LuaLabelRef`; first `LuaBlock` ancestor is the file root block; `processLabelDeclarations`
hits the `::done::` `LuaLabel`; processor `result = labelName`; walk stops; returns the
`LuaLabelName`.

### Example 2: cross-function `goto` (TC 4)
`::outer:: local f = function() goto outer end` → walk from the inner `goto`'s ref; the
function body `LuaBlock` has no `::outer::`; ascending reaches the `LuaFuncDef` node →
function boundary → return null → `multiResolve` empty.

### Example 3: rename from the declaration (TC 7)
Caret on `::myLabel::`; `RenameProcessor` → `LuaLabelName.setName("newLabel")` rewrites the
declaration; `ReferencesSearch` finds the `goto myLabel` ref;
`handleElementRename("newLabel")` rewrites it → `::newLabel:: … goto newLabel`.

## 6. Edge Cases
- **Empty/whitespace name**: `LuaLabelReferenceContributor` only builds a reference when the
  `labelRef` identifier text is non-null (`LuaLabelReferenceContributor.kt:19-23`); `name`
  is therefore non-empty. No change.
- **Duplicate visible labels** (illegal Lua): the up-walk returns the nearest; we do not
  diagnose the conflict (out of scope).
- **Label with no matching goto / goto with no label**: `resolve()` returns null →
  unresolved reference; completion returns whatever is visible (possibly empty).
- **Rename collision** (new name already a visible label): not specially handled; relies on
  `LuaNamesValidator` for identifier legality only. Documented, not blocked.
- **`getNameIdentifier` nullability**: returns `null` if the IDENTIFIER leaf is absent
  (malformed PSI); callers (`getName`) are null-safe.

## 7. Integration Points
No new `plugin.xml` registrations. The relevant extensions already exist and are reused:

```xml
<!-- plugin.xml (existing — unchanged) -->
<psi.referenceContributor language="Lua"
    implementation="net.internetisalie.lunar.lang.LuaLabelReferenceContributor"/>   <!-- :230 -->
<lang.findUsagesProvider language="Lua"
    implementationClass="net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider"/> <!-- :248 -->
<lang.refactoringSupport language="Lua"
    implementationClass="net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider"/> <!-- :257 -->
<lang.namesValidator language="Lua"
    implementationClass="net.internetisalie.lunar.refactoring.LuaNamesValidator"/>   <!-- :264 -->
```

- `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` already returns true for
  `LuaLabelName` (`LuaRefactoringSupportProvider.kt:23-25`); making `LuaLabelName` a
  `PsiNameIdentifierOwner` is what lets the in-place handler actually fire.
- `LuaLabelStructureViewTreeElement` reads `labelName` directly (not via the reference's
  resolve target), so changing the resolve target from leaf → `LuaLabelName` does not affect
  the structure view.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-02-01 | M | §2.1, §2.3, §2.4 (`multiResolve`/`resolveLabel`), §3.1 |
| MAINT-02-02 | M | §2.5 |
| MAINT-02-03 | M | §2.4 (`handleElementRename`/`isReferenceTo`), §2.5, §3.3, §7 |
| MAINT-02-04 | S | §2.2, §2.4 (`getVariants`), §3.2 |

## 9. Alternatives Considered
- **Register a `lang.elementManipulator` for `LuaLabelRef`** instead of overriding
  `handleElementRename`. Rejected: `LuaLabelRefImpl` already inherits a working `setName`
  (`LuaBaseElements.kt:69-84`); overriding `handleElementRename` reuses it with one method and
  no new `plugin.xml` registration.
- **Add labels to the existing `LuaScopeProcessor`/`LuaBlock.processDeclarations`.** Rejected:
  that path enforces no-forward-reference gating (`LuaBlockExt.kt:33-35`) which is wrong for
  labels, and mixing label and variable matching bloats a hot resolver. Separate sibling
  classes keep each rule simple (≤30-line contract).
- **Keep resolving to the IDENTIFIER leaf.** Rejected: a leaf is not a `PsiNameIdentifierOwner`,
  so Rename/in-place rename cannot target it cleanly. Resolving to `LuaLabelName` aligns with
  the find-usages target that `LuaFindUsagesProvider` already declares.
- **Make `LuaLabel` (not `LuaLabelName`) the `PsiNameIdentifierOwner`.** Rejected:
  `LuaLabelName` is the name-bearing element with the IDENTIFIER child and the existing
  `getName`/`setName`; `LuaLabel` is the `:: ::` wrapper statement.

## 10. Open Questions

_None — feature has cleared the planning bar._
