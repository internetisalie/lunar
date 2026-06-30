---
id: NAVIGATION-12-DESIGN
title: "12: Member Field Resolution — Design"
type: design
parent_id: NAVIGATION-12
folders:
  - "[[features/navigation/12-member-field-resolution/requirements|requirements]]"
---
# Technical Design: NAV-12 Member Field Resolution

## 1. Problem & Constraints
`receiver.field = value` declarations are **not indexed**:
- Only `LuaFuncDecl`, `LuaLocalVarDecl`, `LuaLocalFuncDecl` are stubbed; a field assignment
  (`LuaAssignmentStatement` of an `LuaIndexExpr`) is not, so `LuaClassNameIndex`/`LuaGlobalDeclarationIndex`
  never see it. (`function path.x` *is* indexed — keyed by **receiver** name — which is why bare member
  lookups collided across namespaces; see the member-reference fix.)
- The **type-engine route is not viable** for the PSI anchor: a probe shows the global receiver `package`
  infers as `undefined` in a plain fixture (cross-file global typing is seeded from the runtime library, not
  the project stub index), and `graphTypeToLuaType` builds field members with `sourceElement = null`. So
  `resolveMember(...).sourceElement` cannot anchor navigation today.

**Decision:** index field declarations directly with a **`FileBasedIndex`**, keyed by the qualified name
`receiver.field`. This mirrors the existing `LuaCatsTypeNameIndex` precedent (prefer a `FileBasedIndex` over
heavy comment/assignment stubbing — avoids the `IElementType` registry-size limit), is testable without the
runtime-library seeding, and resolves cross-file by name.

## 2. Components
- **`LuaMemberFieldIndex` (`FileBasedIndex<String, Void>` / `ScalarIndexExtension`)** in `lang/indexing/`.
  - Indexer walks each `LuaFile`'s assignment statements; for every LHS `LuaVar` of the dotted form
    `a.b` (`.c…`), emit the qualified key `a.b` (and, for chains, the full dotted path). Stores the file;
    the declaration offset is recovered by re-finding the matching `LuaIndexExpr` in the file (same approach
    as `LuaCatsTypeNavigation`).
  - Version bumped on shape changes.
- **`LuaMemberFieldNavigation`** helper: `qualifiedName -> List<PsiElement>` (the field-identifier elements),
  used by both consumers below so resolution and doc agree.
- **`LuaNameReference.multiResolve`** (qualified-member branch): after the receiver-qualified function lookup,
  also query `LuaMemberFieldNavigation` for the field declaration(s) and add them as resolve results.
- **`LuaDocumentationTargetProvider`**: in the member-segment branch, if the qualified name has a field
  declaration, build a documentation target for it (render the riding doc comment + `---@type`).

## 3. Resolution Flow (`package.path`)
1. `getQualifiedName(pathSegment)` → `"package.path"` (existing logic).
2. `LuaMemberFieldNavigation.find("package.path")` → the stub's `package.path` identifier in `package.lua`.
3. `multiResolve` returns that single element → `resolve()` is non-null → Go-to works.
4. Doc provider renders the field's doc comment ("Paths for searching modules") + `string`.

## 4. Doc Rendering
Reuse `LuaDocumentationRenderer`: a field's doc owner is the assignment's riding `LuaCatsComment`
(`---@type` / leading `---` text). Render description + the declared/inferred type string. If no comment,
fall back to the qualified name + inferred type (`LuaTypesSnapshot.getValueType` on the field element).

## 5. Threading & Memory
- Index build runs on the indexing thread; reads via `FileBasedIndex.getValues`/`getContainingFiles` inside
  `runReadAction`. No hard refs to `Project`/`PsiFile`; recover PSI lazily per query (`PsiManager.findFile`).

## 6. Test Strategy
- Unit (`BasePlatformTestCase` + `addFileToProject`): the index returns the field for `package.path`;
  `multiResolve` returns exactly the stub field and **no** `path.*` functions; the doc target renders the
  field comment. Reuse `ReceiverAwareMemberResolutionTest`'s fixture.
- Real-flow (`verify-in-ide`): Go-to + quick-doc on `package.path` in a `.luawork`.
