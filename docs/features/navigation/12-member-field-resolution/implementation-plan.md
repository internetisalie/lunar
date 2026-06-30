---
id: NAVIGATION-12-PLAN
title: "12: Member Field Resolution — Implementation Plan"
type: plan
parent_id: NAVIGATION-12
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

## Phase 3 — Quick documentation (NAV-12-03) — DONE
- [x] In `LuaDocumentationTargetProvider.documentationTargets`, a member segment routes to a new
      `LuaFieldDocumentationTarget`. The `---@type`/doc comment rides the assignment statement (not a
      `LuaCommentOwner`, and the cats renderer is tied to func/local-var declarations), so the doc is
      built directly from the preceding `LuaCatsComment`: `LuaCatsSummary.getText` for the description +
      the `@type` `argType`. The first declaration carrying a comment is chosen over a bare re-assignment.
- [x] Test (`MemberFieldQuickDocTest`): quick-doc on `package.path` resolves to the documented declaration
      (not the re-assignment), and renders documentation (no longer "No documentation found").

## Phase 4 — Verify & document — DONE
- [x] CHANGELOG entry; requirement statuses set to **Full**.
- [x] `verify-in-ide`: quick-doc on `package.path` in a `.luawork` renders `package.path : string` + the
      field description (was "No documentation found"). Confirmed live (hot-swapped jar, clean relaunch).

## De-risking
- Confirm the index key form for chains (`a.b.c`) before Phase 2; start with depth-2 (`a.b`) which covers
  the stdlib field case, then extend.
