---
id: MAINT-15-PLAN
title: "Implementation Plan"
type: plan
parent_id: MAINT-15
folders:
  - "[[features/maint/15-remove-legacy-annotators/requirements|requirements]]"
---

# MAINT-15: Implementation Plan

## Phases

### Phase 1: Delete the three dead classes [Must]
- **Goal**: Remove `LuaLocalBindingsAnnotator`, `LuaGotoAnnotator`,
  `LuaGlobalBindingsAnnotator` from
  `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaAnnotators.kt`.
- **Task**: Delete the contiguous span `LuaAnnotators.kt:151–:172` (the three trailing
  classes and their bodies). Leave classes `:12–:149` (the four live annotators) and the
  imports `:1–:10` unchanged — every import is still used, so no import edit is needed.
- **Verification (compile)**:
  `tooling/gce-builder/gce-builder.sh run "compileKotlin"` — compiles clean; no unresolved
  references (the classes were referenced nowhere but plugin.xml, edited in Phase 2).
- **Verification (grep)**:
  `grep -rn "LuaLocalBindingsAnnotator\|LuaGotoAnnotator\|LuaGlobalBindingsAnnotator" src/main src/test src/integrationTest`
  returns only the `plugin.xml` lines (removed in Phase 2) — no Kotlin matches remain.

### Phase 2: Delete the three plugin.xml registrations [Must]
- **Goal**: Remove the three `<annotator>` extensions that pointed at the deleted classes.
- **Task**: Delete the contiguous block `META-INF/plugin.xml:142–:150` (three
  `<annotator language="Lua" implementationClass="…LuaLocalBindingsAnnotator/GlobalBindingsAnnotator/GotoAnnotator"/>`
  entries). Keep the four live-annotator registrations at `:130–:141`.
- **Verification**:
  `grep -n "Bindings\|GotoAnnotator" src/main/resources/META-INF/plugin.xml` → no matches;
  `grep -c "<annotator" src/main/resources/META-INF/plugin.xml` reflects four remaining
  Lua annotator blocks (plus any non-Lua annotators unrelated to this change).

### Phase 3: Clean stale documentation references [Should]
- **Goal**: After Phases 1–2 delete the three classes, no doc under `docs/` may still name a
  class that no longer exists (a dangling reference). Independent audit
  (`grep -rn "LuaLocalBindingsAnnotator\|LuaGotoAnnotator\|LuaGlobalBindingsAnnotator" docs/`)
  found four live-doc references outside the MAINT-15 artifacts and outside the forbidden
  files (`docs/roadmap.md`, `docs/status.md`, `docs/features/maint/requirements.md`, and the
  `.obsidian/` session logs, which are agent-history JSON and are NOT edited).
- **Task**: For each reference below, either remove the class mention or reword it to
  past-tense ("removed in MAINT-15"), so no doc claims a live class that no longer exists:
  - `docs/features/inspections/requirements.md:38` — INSP-01 status line
    "**Partial** (Implemented in `LuaGlobalBindingsAnnotator`)". Reword to note the annotator
    was removed in MAINT-15 (e.g. drop the "Implemented in `LuaGlobalBindingsAnnotator`"
    parenthetical or mark it as no longer the implementation site); do not change the INSP-01
    Partial/Not-Implemented judgement itself, only the dangling class name.
  - `docs/features/syntax/17-inferred-type-highlighting/design.md:17–18` — prose listing
    `net.internetisalie.lunar.lang.syntax` example annotators
    "(e.g. `LuaLocalBindingsAnnotator`, `LuaGlobalBindingsAnnotator`)". Replace the two dead
    names with a live example (e.g. `LuaNumeralAnnotator`) or reword to "the scope annotators
    removed in MAINT-15", so the example no longer points at deleted classes.
  - `docs/features/syntax/17-inferred-type-highlighting/risks-and-gaps.md:19` — risk
    `SYNTAX-17-R-03` ("Overlap with scope annotators") references
    `LuaLocalBindingsAnnotator`/`LuaGlobalBindingsAnnotator`. Reword: since those annotators
    were removed in MAINT-15 the overlap risk is now moot/reduced — note the removal rather
    than naming live classes (this may downgrade the risk narrative; keep the row, adjust text).
  - `docs/status-detail.md:280` — MAINT-15 row detail
    "`LuaLocalBindingsAnnotator` still exists and is registered (empty body)". This file is a
    **hand-maintained** source-verified audit (manually dated/updated; NOT produced by
    `scripts/gen_status.py`, which only generates `docs/status.md`), so it IS in scope: update
    the MAINT-15 row status/detail to reflect that the three classes were removed
    (e.g. status `done`, detail "removed in MAINT-15").
- **Verification (grep)**:
  `grep -rn "LuaLocalBindingsAnnotator\|LuaGotoAnnotator\|LuaGlobalBindingsAnnotator" docs/`
  returns **no** live/dangling reference — remaining hits, if any, are limited to
  `.obsidian/` session-log JSON (immutable agent history) and past-tense "removed in MAINT-15"
  mentions; no doc presents the three as live/existing classes. `docs/roadmap.md`,
  `docs/status.md`, and `docs/features/maint/requirements.md` are out of scope for this phase.

## Final Gate [Must]
- **Full build + verify**: `tooling/gce-builder/gce-builder.sh run "clean build"` →
  `BUILD SUCCESSFUL` (compile + plugin verification + `:checkStatus`/`:lintDocs`).
- **Full unit suite**: `tooling/gce-builder/gce-builder.sh run test` → 0 failures; the
  live-annotator tests `TestLuaNumeralAnnotator` and `LuaInferredTypeAnnotatorTest` pass,
  proving no live-annotator regression.
