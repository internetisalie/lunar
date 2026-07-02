---
id: MAINT-15
title: "MAINT-15: Remove Legacy Annotators"
type: feature
status: planned
priority: medium
parent_id: MAINT
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-15: Remove Legacy Annotators

## Overview

`src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaAnnotators.kt` contains three
dead, no-op `Annotator` classes whose `annotate(...)` bodies do nothing but carry a
"disabled … retained for future enhancement" comment:

- `LuaLocalBindingsAnnotator` — `LuaAnnotators.kt:151`, body `:152–:156`
  ("Bindings annotation disabled after removing LuaBindingsVisitor … retained for future enhancement").
- `LuaGotoAnnotator` — `LuaAnnotators.kt:159`, body `:160–:164`
  ("Goto annotation disabled after removing LuaBindingsVisitor …").
- `LuaGlobalBindingsAnnotator` — `LuaAnnotators.kt:167`, body `:168–:171`
  ("Global bindings annotation disabled after removing LuaBindingsVisitor …").

Each is registered as a live `<annotator>` extension in `plugin.xml` (`:142–:144`,
`:145–:147`, `:148–:150`), so the platform instantiates and dispatches them on every PSI
element for zero effect. This is a behavior-preserving dead-code deletion: because the
`annotate(...)` bodies are already empty, removing the classes and their registrations
changes no observable IDE behavior. It is **not** a functional replacement — nothing new is
added; the responsibilities they once had (binding/goto/global highlighting) already moved
to the completion contributor, `LuaLabelReference` file search, and LuaCATS/type-aware
semantic analysis (per the in-code comments), so the empty shells are safe to delete.

## Scope

### In Scope
- Deleting the three dead classes `LuaLocalBindingsAnnotator`, `LuaGotoAnnotator`,
  `LuaGlobalBindingsAnnotator` from `LuaAnnotators.kt`.
- Deleting their three `<annotator>` registrations from `META-INF/plugin.xml`.

### Out of Scope
- The four **live** annotators in the same file, which emit annotations and MUST be
  preserved along with their `plugin.xml` registrations:
  - `LuaNumeralAnnotator` (`LuaAnnotators.kt:12`; emits via `holder.newAnnotation(...)`
    `:29`/`:40`/`:48`/`:54` and `holder.newSilentAnnotation(...)` `:65`; registered
    `plugin.xml:130–:132`).
  - `LuaLongStringAnnotator` (`LuaAnnotators.kt:72`; emits `:85`/`:89`; registered
    `plugin.xml:133–:135`).
  - `LuaLongCommentAnnotator` (`LuaAnnotators.kt:96`; emits `:109`/`:113`; registered
    `plugin.xml:136–:138`).
  - `LuaAttribNameAnnotator` (`LuaAnnotators.kt:120`; emits `:132`/`:144`; registered
    `plugin.xml:139–:141`).
- Any change to `TestLuaNumeralAnnotator` or `LuaInferredTypeAnnotatorTest` (they cover
  live annotators; untouched).
- Adding new highlighting/binding functionality.

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-15-01 | Remove dead classes | Must | planned | Delete `LuaLocalBindingsAnnotator`, `LuaGotoAnnotator`, `LuaGlobalBindingsAnnotator` (and only these) from `lang/syntax/LuaAnnotators.kt`. |
| MAINT-15-02 | Remove registrations | Must | planned | Delete the three `<annotator>` blocks registering those classes in `META-INF/plugin.xml` (`:142–:150`); leave the four live-annotator registrations (`:130–:141`) intact. |
| MAINT-15-03 | Preserve live annotators | Must | planned | The four live annotators keep their classes and registrations and still emit annotations. |
| MAINT-15-04 | Green build & tests | Must | planned | Full `run build` (compile + verify) and `run test` pass after removal. |
| MAINT-15-05 | No dangling doc references | Should | planned | After removal, no doc under `docs/` names the three deleted classes as live/existing. The four live-doc references (`docs/features/inspections/requirements.md:38`, `docs/features/syntax/17-inferred-type-highlighting/design.md:17–18`, `docs/features/syntax/17-inferred-type-highlighting/risks-and-gaps.md:19`, `docs/status-detail.md:280`) are removed or reworded to past-tense ("removed in MAINT-15"). Excludes `.obsidian/` session logs, `docs/roadmap.md`, `docs/status.md`, and `docs/features/maint/requirements.md`. |

## Verification / Test Cases
Because this is dead-code removal, verification is by grep + build, not new tests.

| TC | Verifies | Action | Expected |
|---|---|---|---|
| TC-1 | MAINT-15-01 | `grep -rn "LuaLocalBindingsAnnotator\|LuaGotoAnnotator\|LuaGlobalBindingsAnnotator" src/main src/test src/integrationTest` | No matches (all three classes gone; no stray references). |
| TC-2 | MAINT-15-02 | `grep -n "Bindings\|GotoAnnotator" src/main/resources/META-INF/plugin.xml` | No matches for the three dead-class registrations. |
| TC-3 | MAINT-15-03 | `grep -n "LuaNumeralAnnotator\|LuaLongStringAnnotator\|LuaLongCommentAnnotator\|LuaAttribNameAnnotator" src/main/resources/META-INF/plugin.xml` | Four registrations still present (lines `:130–:141`). |
| TC-4 | MAINT-15-03 | `tooling/gce-builder/gce-builder.sh run "test --tests *LuaNumeralAnnotator* --tests *LuaInferredTypeAnnotator*"` | Existing live-annotator tests pass (no regression). |
| TC-5 | MAINT-15-04 | `tooling/gce-builder/gce-builder.sh run "clean build"` then `run test` | `BUILD SUCCESSFUL`; full unit suite green. |
| TC-6 | MAINT-15-05 | `grep -rn "LuaLocalBindingsAnnotator\|LuaGotoAnnotator\|LuaGlobalBindingsAnnotator" docs/` | No live/dangling reference: remaining hits limited to `.obsidian/` session-log JSON (immutable agent history) and past-tense "removed in MAINT-15" mentions; the four target doc references are cleaned. |
