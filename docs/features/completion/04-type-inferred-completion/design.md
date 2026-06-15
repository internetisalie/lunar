---
id: COMP-04-DESIGN
title: "Technical Design"
type: design
parent_id: COMP-04
status: "done"
priority: "high"
folders:
  - "[[features/completion/04-type-inferred-completion/requirements|requirements]]"
---

# COMP-04: Type-Inferred Completion — Technical Design

## 1. Architecture Overview

### Current State (what already works)
Member completion after `.`/`:` **already exists** in
`net.internetisalie.lunar.lang.LuaCompletionContributor` (registered
`<completion.contributor language="Lua" implementationClass="…LuaCompletionContributor"/>`).
Its member provider:
- pattern `psiElement().afterLeaf(".", ":")`; detects colon via the previous leaf;
- finds the receiver expression (`findReceiverExpr`);
- `val gt = LuaTypesVisitor.getTypes(originalFile).getValueType(receiverExpr)` →
  `LuaGraphType`;
- `gt.getMembers(): Map<String, VariableNode>` — which **already** walks `Table.superTypes`
  (`LuaGraphType.kt:81`, inheritance) and `Union.types` (`:87`, unions);
- on colon, keeps only members whose `VariableNode.write` is `LuaGraphType.Function`.

`@type C` / `@class` receivers reach the graph via
`LuaTypeGraphBridge.injectTypeAnnotation` → `LuaTypeManager.resolveType` → `LuaClassType`
(with `@field` members + `superTypes`) → `LuaGraphType.fromLuaType`. The richer immutable layer
`LuaType` (`getMembers(): Map<String, LuaTypeMember>` with `visibility` + `LuaParameterizedType`
substitution) is reachable via `snapshot.graphTypeToLuaType(gt)`.

So requirements **01, 02, 03, 04, 05** are largely satisfied by the existing path. The genuine
gaps are: **`__index`/metatable (08)** and **`self` typing (09)** are *not modeled*
(`LuaTypeGraph.kt:438` only comments on it); union "partial" marking (06), visibility filtering,
overload display (10), and richer lookup presentation are unimplemented.

### Target State
Enhance the existing member provider (presentation, visibility, union-partial, overloads) and
add two type-engine capabilities in `LuaTypesVisitor`/`LuaTypeGraph`: `self`-typing and
`setmetatable __index` member merging. No new extension point — the contributor is already
registered.

## 2. Core Components (changes)

### 2.1 `net.internetisalie.lunar.lang.LuaCompletionContributor` — member provider (modify)
- **Responsibility**: build lookup elements from the receiver's members, with separator/
  visibility filtering, union-partial marking, icons, tail text.
- **Threading**: completion thread (read action provided by platform).
- **Key logic** (replaces the inner loop): see §3.1.

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor` — `self` typing (modify)
- **Responsibility**: bind the implicit `self` of a method to its enclosing class type.
- **Change**: in the method-definition visit (`funcName.funcNameMethod != null`), resolve the
  receiver name's `LuaGraphType` and add a flow edge into the `self` variable node. See §3.2.

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor` — `setmetatable` `__index` (modify)
- **Responsibility**: when a value is `setmetatable(t, mt)`, expose `mt.__index`'s members on
  `t`'s type. See §3.3.

### 2.4 Lookup presentation helper `net.internetisalie.lunar.lang.completion.LuaMemberLookup`
- **Responsibility**: turn a `(name, memberGraphType, isPartial, visibility)` into a
  `LookupElement` (icon Field vs Method, tail text = type/signature, partial suffix). Pure.

## 3. Algorithms

### 3.1 Member enumeration & filtering (in the member provider) — COMP-04-01/02/03/06/07/10
- **Input**: `receiverExpr: PsiElement`, `isColon: Boolean`, completion `position`.
- **Steps**:
  1. `val snap = LuaTypesVisitor.getTypes(originalFile)`; `val gt = snap.getValueType(receiverExpr)`.
  2. `val members: Map<String, VariableNode> = gt.getMembers()` (already walks superTypes/union).
  3. **Union-partial set** (COMP-04-06): if `gt is LuaGraphType.Union`, compute
     `common = ∩ over t in gt.types of t.getMembers().keys`; a member `m ∉ common` is *partial*.
     For non-union, nothing is partial.
  4. **Visibility** (COMP-04 visibility): `val lt = snap.graphTypeToLuaType(gt)`; for each member
     `name`, `vis = lt.resolveMember(name)?.visibility ?: PUBLIC`. Skip the member when
     `vis != PUBLIC` **and** the completion site is not inside a method of `lt` (determine by
     walking up from `position` to an enclosing `LuaFuncDecl` whose receiver type equals `lt`;
     PROTECTED also allowed when the enclosing class is a subtype of `lt`).
  5. **Separator filter** (COMP-04-02/07): if `isColon`, keep only members whose
     `VariableNode.write` is `LuaGraphType.Function`; if dot, keep all (fields + functions).
  6. **Overloads** (COMP-04-10): if a member's type is an overload set (a `Union` of
     `Function`s, or `LuaFunctionType` with multiple signatures), emit one lookup element per
     signature with distinct tail text.
  7. For each surviving member call `LuaMemberLookup.create(name, memberType, isPartial, vis)`
     and `result.addElement(PrioritizedLookupElement.withPriority(it, 100.0))`.
- **Presentation** (`LuaMemberLookup`): icon = `AllIcons.Nodes.Method` if the member type is a
  `Function` else `AllIcons.Nodes.Field`; tail text = `memberType.displayName()` (signature for
  functions); partial members get `" (partial)"` appended and their type is treated as
  `member | nil`.

### 3.2 `self` typing (COMP-04-09) — `LuaTypesVisitor`
`self` is **implicit** for a `:` method (not in `parList.nameList`), so nothing declares it
today. The injection must happen in `visitFunctionBody` — which creates `funcScope` and then
visits the block — **before** the block is visited, so the visitor's normal name resolution
binds in-body `self` references to the injected node.

- **Step A — capture the receiver node in `visitFuncDecl`** (`LuaTypesVisitor.kt:307`): in the
  `funcName.funcNameMethod != null` branch (`:331`), the local `calleeNode` *before* it is
  reassigned to the method member node (i.e. its value at line 331, after the property loop)
  **is** the receiver's type node `R` (e.g. `C`). Capture `val selfReceiver = calleeNode`
  and thread it into the `visitFunctionBody(...)` call (add a nullable
  `selfReceiver: VariableNode? = null` parameter; pass `null` from `visitFuncDef`).
- **Step B — declare `self` in `visitFunctionBody`** *after* `funcScope` is created and
  *before* `visitBlock`:
  ```kotlin
  if (selfReceiver != null) {
      val selfKey = element /* the LuaFuncDecl */         // stable, distinct from funcNode? use method ident:
      val selfNode = graph.variable(method.nameRef.identifier)  // fresh node keyed on the ':m' identifier
      funcScope.declare("self", selfNode)
      graph.addEdge(selfReceiver, selfNode)               // flow C's type into self
  }
  ```
  (Key the `self` node on the method-name identifier PSI — distinct from the member node
  `graph.variable(method)` created in §A — so it is deterministic and not aliased.)
- **Result**: when the visitor walks the body, a `self` `LuaNameRef` resolves via
  `funcScope.lookup("self")` → `selfNode`, and `elementNodes[selfRef]` is recorded as usual, so
  `getValueType(selfRef)` yields `C`'s Table type and `self.<member>` completes (TC-06).
- For `function C.m(self, …)` (explicit dot-self), `self` is already a real `parList` parameter;
  bind it the same way only if its name is literally `self` and a receiver `C` is resolvable.

### 3.3 `setmetatable __index` (COMP-04-08) — `LuaTypesVisitor`
- **Where**: the visit of a `LuaFuncCall` whose callee name is `setmetatable` with two args
  `setmetatable(t, mt)`.
- **Steps**:
  1. Infer `tType = getValueType(arg0)` (must be a `Table` graph node; if not, create one).
  2. Infer `mtType = getValueType(arg1)`; locate its `__index` member node (`mt.getMembers()
     ["__index"]`).
  3. If `__index`'s type is a `Table` → add it to `tType.superTypes` (so `t.getMembers()`
     includes the index table's members — TC-05).
  4. If `__index`'s type is a `Function` → use that function's first inferred **return** type
     (`Function.returns.firstOrNull()?.type`); if it is a `Table`, add it to `tType.superTypes`.
  5. Bind the result back to the variable receiving the `setmetatable(...)` value (the call's
     own value node = `tType`).
- **Bounds**: only literal/locally-inferable `mt` tables are handled; dynamic metatables fall
  back to `Any` (no members) — documented limitation `COMP-04-DR-01`.

### 3.4 Generic substitution (COMP-04 generics)
- Already modeled by `LuaParameterizedType.getMembers()` (substitutes type args for the class's
  `typeParameters`). The provider uses `snap.graphTypeToLuaType(gt)` for visibility (§3.1 step 4);
  when `gt` carries generic args, that `LuaType` is a `LuaParameterizedType` and its
  `getMembers()` returns substituted member types. No new logic; confirmed by TC for `List<string>`
  if present (else covered by `COMP-04-DR-02`).

## 4. External Data & Parsing
None — all inputs are PSI + the in-memory type graph. (No CLI/file/network input.)

## 5. Data Flow

### Example: `function MyClass:method() self.<caret>` (TC-06)
`LuaTypesVisitor` binds `self → MyClass` (§3.2). Completion: receiver `self` → `getValueType` =
`MyClass` Table → `getMembers()` (incl. inherited) → dot completion lists `MyClass` members.

### Example: `setmetatable({}, { __index = { x = 1 } }); t.<caret>` (TC-05)
`LuaTypesVisitor` adds the `__index` table as a superType of `t` (§3.3) → `t.getMembers()`
includes `x` → completion lists `x`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Receiver type `Any`/`Undefined` | `getMembers()` empty → no member suggestions (fall back to other providers). |
| Union with disjoint members (TC-04) | all members offered; those not in every branch marked `(partial)` and typed `… | nil` (§3.1 step 3). |
| Colon completion on a field (non-function) | filtered out (§3.1 step 5). |
| `self` outside a method | no `self` binding added; resolves as a normal name. |
| Dynamic/non-literal metatable | falls back to `Any` (no `__index` members). |
| Recursive `__index` chain | `getMembers()` superType walk is bounded by the existing `visited` guard in `fromLuaType`. |

## 7. Integration Points
- **No new extension point.** `LuaCompletionContributor` is already registered
  (`<completion.contributor language="Lua" …>`); this feature edits its member provider.
- Type-engine edits live in `LuaTypesVisitor`/`LuaTypeGraph` (existing classes); the
  `LuaTypesVisitor.KEY` document-hash cache (`FileUserData.cacheFileUserData`) invalidates on
  edits — no new cache.
- Reuses `LuaTypes.getValueType`/`graphTypeToLuaType`, `LuaGraphType.getMembers`,
  `LuaType.resolveMember`/`visibility`, `PrioritizedLookupElement`, `LookupElementBuilder`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| COMP-04-01 Dot members | M | §3.1 (existing path) |
| COMP-04-02 Colon methods | M | §3.1 step 5 |
| COMP-04-03 Inherited members | M | `LuaGraphType.Table.getMembers` superType walk |
| COMP-04-04 Literal table | M | graph `Table` for table literals |
| COMP-04-05 LuaCATS `@field` | M | `LuaTypeGraphBridge.injectTypeAnnotation` → graph |
| COMP-04-06 Union completion | S | §3.1 step 3 (partial marking) |
| COMP-04-07 Auto colon/dot | S | §3.1 step 5 + presentation |
| COMP-04-08 Metatable `__index` | M | §3.3 |
| COMP-04-09 `self` context | M | §3.2 |
| COMP-04-10 Overloads | S | §3.1 step 6 |

## 9. Alternatives Considered
- **Graph path vs `LuaType` path for enumeration**: enumerate via the proven graph
  `getMembers()` (handles inheritance/union/literal already) and consult `graphTypeToLuaType`
  only for visibility/generics metadata — avoids re-deriving inheritance at the `LuaType` layer.
- **Modeling `setmetatable` in the graph vs special-casing in completion**: modeled in
  `LuaTypesVisitor` so `__index` members also benefit hover/inlay, not just completion.

## 10. Open Questions

_None — feature has cleared the planning bar._
