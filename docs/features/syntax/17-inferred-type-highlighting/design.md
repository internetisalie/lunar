---
id: SYNTAX-17-DESIGN
title: "Technical Design"
type: design
parent_id: SYNTAX-17
status: "planned"
priority: "low"
folders:
  - "[[features/syntax/17-inferred-type-highlighting/requirements|requirements]]"
---

# SYNTAX-17: Inferred-Type Highlighting — Technical Design

## 1. Architecture Overview

### Current State
Token coloring is done by `LuaSyntaxHighlighter` + scope annotators in
`net.internetisalie.lunar.lang.syntax` (e.g. `LuaLocalBindingsAnnotator`,
`LuaGlobalBindingsAnnotator`). Inferred types are already available via
`net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor.getTypes(element).getValueType(element):
LuaGraphType` (snapshot cached per document hash through `FileUserData.cacheFileUserData`) and
displayed as inlay hints by `LuaTypeInlayHintProvider`. Color keys live in
`net.internetisalie.lunar.lang.syntax.LuaHighlight`; the settings UI is
`LuaColorSettingsPage`.

### Target State (decisions that close the gaps)
- **Annotator, not a custom `HighlightingPass`** (closes the "undecided" gap). A single
  `Annotator` registered with `<annotator language="Lua">` runs inside the platform's
  background highlighting pass, which is already **viewport-incremental and throttled** — so
  SYNTAX-17-04 ("lower-priority background pass") needs no custom pass or manual viewport math.
- **No custom cache** (closes the "caching mechanics" gap): the annotator only calls
  `getValueType`, which reads the per-document-hash snapshot.
- **New `TextAttributesKey`s** in `LuaHighlight` + `LuaColorSettingsPage` (closes the "no
  TextAttributesKey defs" gap).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.syntax.LuaInferredTypeAnnotator`
- **Responsibility**: apply a type-derived `TextAttributesKey` to call-site, class-ref, and
  member identifiers.
- **Threading**: runs on the platform highlighting thread (read action provided); no I/O.
- **Key API**:
  ```kotlin
  class LuaInferredTypeAnnotator : Annotator {
      override fun annotate(element: PsiElement, holder: AnnotationHolder) {
          if (element !is LuaNameRef) return
          if (DumbService.isDumb(element.project)) return            // index needed (SYNTAX-17-04)
          val key = classify(element) ?: return                      // §3.1
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
              .range(element.identifier).textAttributes(key).create()
      }
      private fun classify(ref: LuaNameRef): TextAttributesKey?       // §3.1; ≤30 lines via helpers
  }
  ```
  `newSilentAnnotation` applies color only (no tooltip), layering over the base highlight.

### 2.2 `LuaHighlight` — new keys (add)
```kotlin
val INFERRED_LOCAL_CALL  = key("LUA_INFERRED_LOCAL_CALL",  DefaultLanguageHighlighterColors.FUNCTION_CALL)
val INFERRED_GLOBAL_CALL = key("LUA_INFERRED_GLOBAL_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL)
val INFERRED_CLASS       = key("LUA_INFERRED_CLASS",       DefaultLanguageHighlighterColors.CLASS_NAME)
val INFERRED_FIELD       = key("LUA_INFERRED_FIELD",       DefaultLanguageHighlighterColors.INSTANCE_FIELD)
val INFERRED_METHOD      = key("LUA_INFERRED_METHOD",      DefaultLanguageHighlighterColors.INSTANCE_METHOD)
// key(name, base) = TextAttributesKey.createTextAttributesKey(name, base)
```

### 2.3 `LuaColorSettingsPage` — register the five keys (add)
Add a `Pair("color.inferred.<x>", LuaHighlight.INFERRED_<X>)` to `attributeDescriptors` and a
`"<tag>" → key` entry to the `highlightingTagToDescriptorMap` for each, so users can recolor
them in *Settings ▸ Editor ▸ Color Scheme ▸ Lua*. **Also** add the five
`color.inferred.localCall|globalCall|class|field|method` keys to `LuaBundle.properties` —
`attributeDescriptors` resolves the display name via `LuaBundle.message(pair.first)`
(`LuaColorSettingsPage.kt`), so a missing bundle key would fail to render the row.

## 3. Algorithms

### 3.1 `classify(ref: LuaNameRef): TextAttributesKey?`
- **Input**: a `LuaNameRef`. **Output**: a key, or null (leave base coloring).
- **Steps** (first match wins):
  1. `val snap = LuaTypesVisitor.getTypes(ref)`; `val gt = snap.getValueType(ref)`.
  2. **Member of an index** (SYNTAX-17-03): if `ref.parent` is a `LuaIndexExpr` reached through
     a `.`/`:` (i.e. `ref` is the member name, not the base), resolve the member type:
     `val recv = receiverOf(ref)` (the base expr); `val m = snap.getValueType(recv).getMembers()
     [ref.text]?.write`. If `m is LuaGraphType.Function` → `INFERRED_METHOD` else if `m != null`
     → `INFERRED_FIELD`. (Covers `t.data` vs `t:func` / `t.func`.)
  3. **Call site** (SYNTAX-17-01): else if `gt is LuaGraphType.Function` **and** `ref` is in
     callee position — `ref` is the `nameRef` of the `var`/`varOrExp` that is the callee of an
     enclosing `LuaFuncCall` (no `.`/`:` suffix between `ref` and the args), or the method name
     of an `obj:method()` call — then choose local vs global by `isLocalTarget(ref)`:
     - `val target = ref.reference?.resolve()` — **note**: `LuaNameReference.resolve()` returns
       the *declaration's IDENTIFIER leaf* (e.g. `attName.nameRef.identifier`), **not** the
       decl node (see `LuaScopeProcessor`). So classify by the **parent of `target`**:
       `INFERRED_LOCAL_CALL` if `target != null` and `target.parent` (via its `nameRef`) is a
       local/param/loop binding — i.e. the enclosing declaration is a `LuaAttName` (local),
       a `LuaNameList` under `LuaParList` (parameter) or under `LuaGenericForStatement`
       (generic-for var), or a `LuaNumericForStatement` identifier. (This is the same
       declaration-site classification used in INSP-01 §3.1.)
     - else (`target == null` for builtins like `print`, or the target is a global function-name
       / global assignment / stub-index global) → `INFERRED_GLOBAL_CALL`.
  4. **Class reference** (SYNTAX-17-02): else if `gt is LuaGraphType.Table && gt.className != null`
     and `ref.text == gt.className` (the identifier names the class, e.g. `MyClass` in
     `MyClass()` or a `@type` position) → `INFERRED_CLASS`.
  5. Else null.
- **Rules / edge handling**: `gt` of `Any`/`Undefined` → only step 2 (member) may still apply
  if the receiver has the member; otherwise null. `receiverOf` reuses the same PSI navigation as
  the completion provider's `findReceiverExpr` (walk to the base `LuaVar`/`LuaNameRef` before the
  `.`/`:`). Helpers `classifyMember`, `classifyCall`, `classifyClassRef` keep each ≤30 lines.

### 3.2 Performance / throttling (SYNTAX-17-04)
- The annotator participates in the platform's `GeneralHighlightingPass`, which: runs on a
  background thread, is restricted to the **visible range** (+ a margin) and re-runs
  incrementally on edits, and is lower priority than syntax highlighting. No custom scheduling.
- `getValueType` reads the cached snapshot (no re-inference per heartbeat); depth is bounded by
  the type graph's existing `visited` guard — no separate depth limit needed.
- Dumb-mode guard (`DumbService.isDumb`) skips the pass while indexes are unavailable.

## 4. External Data & Parsing
None — inputs are PSI + the in-memory type snapshot.

## 5. Data Flow

### Example: `local x = function() end; x()` (TC-01)
Annotator visits the `x` in `x()` → not a member → `getValueType(x)` is `Function` and `x` is the
callee → `resolve()` is the `local x` decl (local) → `INFERRED_LOCAL_CALL` applied to `x`.

### Example: `t.data = 1; t:func()` (TC-03)
`data`: member of `t`, type non-function → `INFERRED_FIELD`. `func`: member of `t`, type
`Function` → `INFERRED_METHOD`. Distinct colors.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Unknown/`Any` receiver | members unresolved → no member color (base coloring kept). |
| `x` used as a value, not called | step 3 requires callee position → not colored as a call. |
| Builtin `print()` (global function) | `INFERRED_GLOBAL_CALL` (resolve not local). |
| Dumb mode (indexing) | annotator returns early; colors appear after indexing completes. |
| Same name field and method across union | member lookup uses the receiver's merged members; first non-null wins. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<annotator language="Lua"
    implementationClass="net.internetisalie.lunar.lang.syntax.LuaInferredTypeAnnotator"/>
```
- New keys added to `LuaHighlight`; descriptors added to `LuaColorSettingsPage` (§2.2/§2.3).
- Reuses `LuaTypesVisitor.getTypes`/`getValueType`, `LuaGraphType.getMembers`, and the
  completion provider's receiver-navigation helper.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SYNTAX-17-01 Call Site | M | §3.1 step 3 (local/global call keys) |
| SYNTAX-17-02 Class/Enum | S | §3.1 step 4 |
| SYNTAX-17-03 Field vs Method | S | §3.1 step 2 |
| SYNTAX-17-04 Throttling | M | §3.2 (Annotator on the platform highlighting pass + dumb guard) |

## 9. Alternatives Considered
- **Annotator vs custom `HighlightingPass`/`TextEditorHighlightingPassFactory`**: the Annotator
  inherits the platform's viewport-incremental, background, throttled pass — a custom pass would
  re-implement that for no benefit. Chosen: Annotator.
- **Custom type cache vs the document-hash snapshot**: the snapshot is already cached and
  invalidated on edit; a second cache would risk staleness. Dropped the `LuaHighlightCache`.

## 10. Open Questions

_None — feature has cleared the planning bar._
