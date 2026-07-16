---
id: "TARGET-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TARGET-07"
folders:
  - "[[features/target/07-lua-5.5-stdlib-stubs/requirements|requirements]]"
---

# TARGET-07: Implementation Plan

Sequence: (1) create + patch the stub set, (2) attribution, (3) tests. Each phase leaves the
build green. There is no production Kotlin, so phases are small.

## Phases

### Phase 1: Create and patch the 5.5 stub set [Must]
- **Goal**: `runtime/standard/lua-5.5/` exists with the 10 mirrored files and the two §1
  deltas applied.
- **Tasks**:
  - [ ] Copy `src/main/resources/runtime/standard/lua-5.4/` → `.../lua-5.5/` verbatim
        (`cp -r`) — realizes design §3.1 step 1 (TARGET-07-01, TARGET-07-04).
  - [ ] Edit `lua-5.5/builtin.lua`: doc line + `_VERSION` → `"Lua 5.5"` — realizes design §3.1
        step 2 (TARGET-07-03).
  - [ ] Edit `lua-5.5/table.lua`: append the `table.create(nseq, nrec)` block from
        requirements §TARGET-07-02 after `table.move` — realizes design §3.1 step 3
        (TARGET-07-02).
  - [ ] Verify no other file was modified and the MIT header is intact on all 10
        (`git diff --stat` shows only builtin.lua/table.lua changed vs. their 5.4 copies).
- **Exit criteria**: TC 4, 5, 6 pass (resource-presence + content assertions).

### Phase 2: Attribution [Must]
- **Goal**: `THIRD-PARTY.md` accurately records the 5.5 stubs.
- **Tasks**:
  - [ ] Edit `THIRD-PARTY.md` "Lua standard-library API stubs" entry: `5.1-5.4`→`5.1-5.5`,
        glob `lua-5.{1,2,3,4,5}`, count `39`→`49`, note `table.create` — realizes design §7
        (TARGET-07-05).
- **Exit criteria**: entry lists `lua-5.5` and the corrected count; provenance sweep
  (THIRD-PARTY.md "Maintaining this file") surfaces no new unattributed copyright.

### Phase 3: Automated tests [Must]
- **Goal**: prove 5.5 resolution, version gating, baseline parity, and resource presence.
- **Tasks**:
  - [ ] Add `Lua55StdlibStubResourceTest` (light fixture, extends
        `net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase`; JUnit4), modeled on
        `ValkeyStubResourceTest` — realizes design §5:
        - `testTableCreateResolvesUnder55Target` — set `Target(STANDARD, "5.5")`,
          `PlatformLibraryIndex.reload()`, `configureByText("test.lua","table.create(4)")`,
          assert `create` `LuaNameRef.reference.multiResolve(false).size >= 1` (TC 1,
          TARGET-07-06).
        - `testTableCreateDoesNotResolveUnder54Target` — same with `Target(STANDARD, "5.4")`,
          assert `.size == 0` (TC 2, TARGET-07-07).
        - `testTableInsertStillResolvesUnder55Target` — 5.5 target, `table.insert({},1)`,
          assert `.size >= 1` (TC 3, TARGET-07-08 baseline parity).
        - `testLua55LibraryFilesMirror54` — assert the `getLibraryFiles(Target(STANDARD,"5.5"))`
          name set equals the `Target(STANDARD,"5.4")` name set (TC 4, TARGET-07-01).
        - `testTableCreateAndHeaderPresentInResource` — read `lua-5.5/table.lua` bytes; assert
          contains `function table.create(` and `Copyright © 1994–2025 Lua.org, PUC-Rio.`
          (TC 5, TARGET-07-02/-04).
        - `testVersionStringBumpedInBuiltin` — read `lua-5.5/builtin.lua` bytes; assert
          contains `_VERSION = "Lua 5.5"` and not `_VERSION = "Lua 5.4"` (TC 6, TARGET-07-03).
  - [ ] Run the gate: `tooling/gce-builder/gce-builder.sh run "test --tests *Lua55StdlibStubResourceTest*"`,
        then the full suite per the CLAUDE.md gate before committing.
- **Exit criteria**: all six tests green; full suite green (regression-relative).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TARGET-07-01 | M | Phase 1, Phase 3 (TC 4) |
| TARGET-07-02 | M | Phase 1, Phase 3 (TC 5) |
| TARGET-07-03 | M | Phase 1, Phase 3 (TC 6) |
| TARGET-07-04 | M | Phase 1, Phase 3 (TC 5) |
| TARGET-07-05 | M | Phase 2 |
| TARGET-07-06 | M | Phase 3 (TC 1) |
| TARGET-07-07 | M | Phase 3 (TC 2) |
| TARGET-07-08 | S | Phase 3 (TC 3, 4) |

## Verification Tasks
- [ ] Add `Lua55StdlibStubResourceTest` with the six methods above — covers TC 1–6.
- [ ] Run `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py`.
- [ ] (Optional) Run [human-verification-checklists.md](human-verification-checklists.md) —
      live IDE spot-check; not the primary gate (the resolution test is).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Create & patch 5.5 stub set | todo | Must |
| Phase 2: Attribution | todo | Must |
| Phase 3: Automated tests | todo | Must |
