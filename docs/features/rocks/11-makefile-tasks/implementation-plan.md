---
id: ROCKS-11-PLAN
title: "Implementation Plan"
type: plan
parent_id: ROCKS-11
folders:
  - "[[features/rocks/11-makefile-tasks/requirements|requirements]]"
---

# ROCKS-11: Implementation Plan

Sequence the design into shippable, verifiable phases. The Must-scope template work (Phase 1) is
fully independent and ships on its own; the optional plugin dependency (Phase 2) is gated on the
spike (Phase 0). Each phase leaves the build green.

## Phases

### Phase 0: Makefile-plugin spike (DR ROCKS-11-00-01) [Should]
- **Goal**: retire the unverifiable API claims before adding the optional `<depends>` line.
- **Tasks**:
  - [ ] Run DR ROCKS-11-00-01 (risks-and-gaps.md §De-risking): install the JetBrains "Makefile
        Language" plugin in GoLand 2026.1.3, confirm its exact `pluginId` (expected
        `com.jetbrains.lang.makefile`), confirm it provides target gutter-run markers / a Makefile
        run-config natively, and confirm optional-`<depends>` + empty `config-file` load/skip
        behavior with the plugin present vs absent.
- **Exit criteria**: spike outcome recorded inline in risks-and-gaps.md; `pluginId` confirmed (or
  corrected); decision on whether `lunar-makefile.xml` stays empty.

### Phase 1: Template enrichment [Must]
- **Goal**: enriched Makefile with seven canonical targets and correct `.PHONY`.
- **Tasks**:
  - [ ] Edit `net.internetisalie.lunar.rocks.init.LuaRocksTemplates.makefile(name)` to the exact
        §3.1 body (realizes design §2.1, §3.1, §3.2). Preserve hard-tab recipe indentation.
  - [ ] Update / add unit tests in
        `src/test/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksTemplatesTest.kt`
        (`kotlin.test.assertContains` style) for TC #1, #2, #3, #5.
  - [ ] Add / extend a scaffolder test in
        `src/test/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksScaffolderTest.kt`
        (`assertFileContains` idiom) for TC #4.
- **Exit criteria**: `./gradlew test --tests "*LuaRocksTemplatesTest*" --tests
  "*LuaRocksScaffolderTest*"` green; TC #1–#5 pass.

### Phase 2: Optional Makefile-plugin dependency [Should]
- **Goal**: one-click target runnability when the Makefile plugin is installed; graceful when not.
- **Tasks**:
  - [ ] Add the optional `<depends ... config-file="lunar-makefile.xml">` line to
        `src/main/resources/META-INF/plugin.xml` (realizes design §7), using the `pluginId`
        confirmed by Phase 0.
  - [ ] Create `src/main/resources/META-INF/lunar-makefile.xml` with the §7 contents (empty
        `<idea-plugin>` unless Phase 0 surfaced a usable extension point).
- **Exit criteria**: `./gradlew buildPlugin` succeeds; plugin verifier passes; manual TC #6 (plugin
  absent → Lunar loads) and the human-verification checklist for plugin-present one-click run pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-11-01 | M | Phase 1 |
| ROCKS-11-02 | M | Phase 1 |
| ROCKS-11-03 | M | Phase 1 |
| ROCKS-11-04 | S | Phase 2 (gated on Phase 0) |
| ROCKS-11-05 | C | Phase 2 (documentation) |

## Verification Tasks
- [ ] Unit: `LuaRocksTemplatesTest` covers TC #1, #2, #3, #5.
- [ ] Integration/light: `LuaRocksScaffolderTest` covers TC #4.
- [ ] Manual: TC #6 (plugin absent → Lunar loads cleanly) — see human-verification-checklists.md.
- [ ] Manual: one-click target run with the Makefile plugin present — see
      human-verification-checklists.md.
- [ ] Run `./gradlew ktlintFormat ktlintCheck` on changed files; match surrounding style.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: Makefile-plugin spike | done | Should |
| Phase 1: Template enrichment | todo | Must |
| Phase 2: Optional plugin dependency | todo | Should |
