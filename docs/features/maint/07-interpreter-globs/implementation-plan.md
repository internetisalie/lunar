---
id: "MAINT-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-07"
folders:
  - "[[features/maint/07-interpreter-globs/requirements|requirements]]"
---

# MAINT-07: Implementation Plan

## Phases

### Phase 1: Glob path expander [Must]
- **Goal**: Add pure, testable directory-glob expansion to `LuaInterpreterService.kt`.
- **Tasks**:
  - [ ] Add top-level `fun expandSearchPath(spec: String): List<Path>` in
        `net/internetisalie/lunar/platform/LuaInterpreterService.kt` — realizes design §2.1 / §3.1.
  - [ ] Add private top-level `fun expandSegment(base: Path, segment: String): List<Path>` in the
        same file — realizes design §2.2 / §3.2 (literal `resolve`; glob via
        `Files.newDirectoryStream` + `patternFromGlob`, `Files.isDirectory` filter, `sortedBy` name,
        `catch IOException`).
  - [ ] Add imports `java.nio.file.Files`, `java.io.IOException`,
        `com.intellij.openapi.progress.ProgressManager`; reuse existing `isGlob`/`patternFromGlob`
        (`:209`/`:219`).
- **Exit criteria**: new unit test class passes TC 1–9 (see Verification Tasks); build compiles.

### Phase 2: Wire expansion into discovery [Must]
- **Goal**: Route `findInterpreters` through `expandSearchPath` and glob the Windows defaults.
- **Tasks**:
  - [ ] Refactor `findInterpreters()` (`LuaInterpreterService.kt:18`) to
        `.map { substituteEnvVars(it) }.flatMap { expandSearchPath(it) }.flatMap { find(it) }` —
        realizes design §2.3 / §5.
  - [ ] Remove the now-unused `pathFromEnvVarString` (`:112`) — realizes design §2.3 note.
  - [ ] Replace `PATHS_WINDOWS` (`:149`) with `C:\Program Files\Lua 5.*` and
        `C:\Program Files (x86)\Lua 5.*`, deleting `// TODO: Search Path Globs` (`:148`) —
        realizes design §2.4 (MAINT-07-03).
- **Exit criteria**: `PATHS_UNIX` literal discovery behaves exactly as before (TC 3, 4 path);
  Windows entries expand to `Lua 5.x` dirs (TC 5); `findInterpreters` throws nothing on missing
  bases.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-07-01 | M | Phase 1 |
| MAINT-07-02 | M | Phase 1 (non-glob branch), Phase 2 (pipeline) |
| MAINT-07-03 | M | Phase 2 |
| MAINT-07-04 | S | Phase 1 |
| MAINT-07-05 | S | Phase 1 |
| MAINT-07-06 | C | Phase 2 |

## Verification Tasks
- [ ] Add unit test `net.internetisalie.lunar.platform.LuaInterpreterSearchPathGlobTest`
      (`src/test/kotlin/net/internetisalie/lunar/platform/`), extending `BasePlatformTestCase`;
      create a temp directory tree in `setUp()` (`Files.createTempDirectory`) and
      `toFile().deleteRecursively()` in `tearDown()`. Cover:
  - `testExpandsGlobDirectoriesExcludingFiles` — TC 1 (MAINT-07-01).
  - `testExpandsMidSegmentGlob` — TC 2 (MAINT-07-01).
  - `testLiteralPathReturnsSingleElement` — TC 3 (MAINT-07-02).
  - `testLiteralNonExistentPathReturnsSingleElement` — TC 4 (MAINT-07-02).
  - `testDottedGlobRequiresDot` — TC 5 (MAINT-07-03).
  - `testMatchesAreSortedAscending` — TC 6 (MAINT-07-04).
  - `testMissingBaseReturnsEmpty` — TC 7 (MAINT-07-05).
  - `testNoMatchReturnsEmpty` — TC 8 (MAINT-07-05).
  - `testQuestionMarkMatchesSingleChar` — TC 9 (MAINT-07-01).
- [ ] Run `tooling/gce-builder/gce-builder.sh run "test --tests *SearchPathGlob*"`.
- [ ] Run `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on the touched file.
- [ ] Manual (Windows sanity, optional): Settings → interpreters → Re-scan detects installs under
      `C:\Program Files\Lua 5.x`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Glob path expander | todo | Must |
| Phase 2: Wire expansion into discovery | todo | Must |
