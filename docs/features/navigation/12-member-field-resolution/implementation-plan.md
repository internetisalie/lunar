---
id: NAVIGATION-12-PLAN
title: "12: Member Field Resolution — Implementation Plan"
type: implementation-plan
parent_id: NAVIGATION-12
status: "planned"
folders:
  - "[[features/navigation/12-member-field-resolution/requirements|requirements]]"
---
# Implementation Plan: NAV-12 Member Field Resolution

Sequential phases; each ends green (`./gradlew test` for the named tests + no regressions in
`*Reference* *Resolution* *Navigation* *Goto* *Documentation*`).

## Phase 1 — Field index (NAV-12-01)
- [ ] Add `LuaMemberFieldIndex` (`ScalarIndexExtension<String>`) in `lang/indexing/`; key = qualified
      `receiver.field` for every dotted-LHS assignment in a `LuaFile`. Register `<fileBasedIndex>` in `plugin.xml`.
- [ ] Add `LuaMemberFieldNavigation.find(project, qualifiedName): List<PsiElement>` (re-resolve the field
      identifier in each containing file).
- [ ] Test: index/navigation returns the `package.path` field for fixture stub; empty for unknown names.

## Phase 2 — Go to declaration (NAV-12-02, NAV-12-04)
- [ ] In `LuaNameReference.multiResolve` qualified-member branch, add field-declaration results from
      `LuaMemberFieldNavigation` alongside the qualified function lookup.
- [ ] Extend `ReceiverAwareMemberResolutionTest`: `package.path` resolves to the stub field and to **no**
      `path.*` functions (NAV-12-04 regression guard).
- [ ] Update `docs/features/navigation/01-go-to-definition/requirements.md` `NAV-01-03` → **Full**.

## Phase 3 — Quick documentation (NAV-12-03)
- [ ] In `LuaDocumentationTargetProvider`, member-segment branch: when the qualified name has a field
      declaration, return a `DocumentationTarget` rendering its doc comment + declared/inferred type.
- [ ] Test: quick-doc on `package.path` renders the field's description (not "No documentation found",
      not an unrelated symbol).

## Phase 4 — Verify & document
- [ ] `verify-in-ide`: Go-to + quick-doc on `package.path` in a `.luawork`.
- [ ] CHANGELOG entry; set requirement statuses to **Full**.

## De-risking
- Confirm the index key form for chains (`a.b.c`) before Phase 2; start with depth-2 (`a.b`) which covers
  the stdlib field case, then extend.
