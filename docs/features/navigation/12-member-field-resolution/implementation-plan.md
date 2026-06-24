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

## Phase 1 — Field index (NAV-12-01) — DONE
- [x] Add `LuaMemberFieldIndex` (`FileBasedIndexExtension<String, String>`, mirroring `LuaCatsTypeNameIndex`)
      in `lang/indexing/`; key = qualified `receiver.field` for every dotted-LHS assignment in a `LuaFile`.
      Registered `<fileBasedIndex>` in `plugin.xml`.
- [x] Add `LuaMemberFieldNavigation.find(project, qualifiedName, scope): List<PsiElement>` (re-resolve the
      field identifier in each containing file).
- [x] Test (`MemberFieldIndexTest`): finds `package.path` field declarations (the bundled stdlib ships one
      per Lua version); empty for unknown names.

## Phase 2 — Go to declaration (NAV-12-02, NAV-12-04) — DONE
- [x] In `LuaNameReference.multiResolve` qualified-member branch, add field-declaration results from
      `LuaMemberFieldNavigation` alongside the qualified function lookup.
- [x] Extend `ReceiverAwareMemberResolutionTest`: every `package.path` result is the `path` field
      identifier (never a `path.*` function — NAV-12-04 guard); the stub field is among them (NAV-12-02).
- [x] Update `NAV-01-03` → **Full**.
- Note: a field assigned in N files (e.g. LuaRocks `path.add_to_package_paths` reassigns `package.path`)
      yields N navigation targets — fine for Go-to (chooser); doc must rank (risk #2, handled in Phase 3).

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
