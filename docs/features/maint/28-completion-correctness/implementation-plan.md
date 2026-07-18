---
id: "MAINT-28-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-28"
folders:
  - "[[features/maint/28-completion-correctness/requirements|requirements]]"
---

# MAINT-28: Implementation Plan

Five defect fixes across five existing files, sequenced so each phase leaves the build green and
ships an independently verifiable, real-flow-tested behavior. No new production class; edits only.

## Phases

### Phase 1: Original-file discipline (#24) [Must]
- **Goal**: cross-file require-graph + project-global completion works again (index queries and
  proximity read the real on-disk file, not the never-indexed completion copy).
- **Tasks**:
  - [x] Edit `LuaCrossFileCompletionProvider.addCompletions` (`:36-48`) to read
    `parameters.originalFile as? LuaFile ?: return` and pass it through — realizes design §2.1, §3.6.
  - [x] Thread `originalFile` into `processCrossFileSymbols`, `getLocalSymbolNames`, and
    `processGlobalsWithRanking` (they currently receive the copy) — realizes design §3.6 table
    rows 1–3. Do **not** touch the member-provider snapshot (`LuaCompletionContributor.kt:290`)
    or the `addSymbolCompletions` scope walk — those stay on the copy (§3.6 KEEP rows).
- **Exit criteria**: TC-24 passes (`LuaCrossFileCompletionOriginalFileTest`, heavy-fixture,
  `completeBasic()` offers a required global). `LuaCrossFileCompletionHeavyTest` still green.

### Phase 2: Ranking accuracy (#40, #62) [Should]
- **Goal**: method receivers stop leaking into global completion; the expression-keyword gate
  suppresses keywords only when a real typed prefix exists.
- **Tasks**:
  - [x] Replace `GlobalSymbolRankingService.extractFuncDeclName` (`:213-217`) with
    `extractGlobalFuncName` per design §3.2 (skip `funcName.funcNameMethod != null` and
    `funcName.funcNamePropertyList.isNotEmpty()`); update the caller at `:104`.
  - [x] In `LuaCompletionContributor` (`:220-223`): delete the shadowing `prevLeaf` local, compute
    `hasPrefix = result.prefixMatcher.prefix.isNotEmpty()` — realizes design §3.5.
- **Exit criteria**: TC-40 (method receiver absent from globals) and TC-62 (keyword suppression
  by real prefix) pass via `completeBasic()`.

### Phase 3: Single symbol pass (#39) [Should]
- **Goal**: exactly one `addSymbolCompletions` invocation per completion session.
- **Tasks**:
  - [ ] In `LuaCompletionContributor`: delete the redundant `addSymbolCompletions(position, result)`
    at `:241` and its wrapping `if (canBeExpressionStart)` block (`:236-242`); keep the `:216`
    call — realizes design §3.3 steps 1.
  - [ ] Delete the IDENTIFIER-pattern symbol provider `extend(...)` (`:247-261`) — realizes design
    §3.3 step 2. Retain the IDENTIFIER-pattern **cross-file** provider (`:263-268`).
- **Exit criteria**: TC-39 passes (identifier-position completion still offers in-scope locals; no
  duplicate lookup strings). Full suite green.

### Phase 4: Session cost — key caching (#25 perf half is §2.5.5) [Should]
- **Goal**: `getAllKeys` runs once per PSI-modification window, not per completion invocation.
- **Tasks**:
  - [ ] Add `funcKeyCache`/`classKeyCache` `CachedValue<List<String>>` fields to
    `GlobalSymbolRankingService` (mirroring `LuaRockspecDiscoveryService.kt:40-49`) and replace the
    `getAllKeys` calls at `:81`/`:151` with `.value` reads — realizes design §3.4 steps 1–3.
  - [ ] Add the §3.4 doc-comment recording that the member-snapshot item is already-cached
    (no member-provider change) — realizes design §3.4 "PARTLY MOOT".
- **Exit criteria**: TC-25p passes (two invocations on unchanged PSI; keys computed once — asserted
  via a spy/counter or by confirming the `CachedValue` identity is stable). Full suite green.

### Phase 5: Enter-between-blocks guard (#25) [Could]
- **Goal**: COMP-08-04 fires — Enter inside an open block body force-indents.
- **Tasks**:
  - [ ] Rewrite the final `return if` in `LuaEnterBetweenBlockHandler.preprocessEnter` (`:45`) to
    `offset in (owner.textRange.startOffset + 1)..terminator.startOffset` — realizes design §3.1.
- **Exit criteria**: TC-25 passes (real-flow: type Enter between `then`/`end` → indented blank
  body line). Full suite green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-28-01 | M | Phase 1 |
| MAINT-28-02 | S | Phase 3 |
| MAINT-28-03 | S | Phase 2 |
| MAINT-28-04 | C | Phase 5 |
| MAINT-28-05 | S | Phase 4 |

## Verification Tasks

Real-flow (`myFixture.completeBasic()` / actual Enter) is **mandatory** per the requirements DoD
note — engine-only tests hid #24 for a full wave.

- [ ] `LuaCrossFileCompletionOriginalFileTest` (new, extends the `LuaCrossFileCompletionHeavyTest`
  heavy-fixture pattern: `IdeaTestFixtureFactory` + `EmptyModuleFixtureBuilder.addSourceContentRoot`
  + `TempDirTestFixtureImpl` + `runInEdtAndWait`) — covers TC-24.
- [ ] Add TC-40 + TC-62 cases to `LuaCompletionTest` (light `BasePlatformTestCase`,
  `myFixture.completeBasic()`) — covers TC-40, TC-62.
- [ ] Add TC-39 case to `LuaCompletionTest` — covers TC-39.
- [ ] Add a key-caching assertion (TC-25p) near `GlobalSymbolRankingService` tests — covers §2.5.5.
- [ ] Add TC-25 (Enter-between) to the enter-handler test (real-flow `myFixture.type("\n")`) —
  covers TC-25.
- [ ] Run `human-verification-checklists.md`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Original-file discipline (#24) | done | Must |
| Phase 2: Ranking accuracy (#40, #62) | done | Should |
| Phase 3: Single symbol pass (#39) | todo | Should |
| Phase 4: Session cost — key caching (§2.5.5) | todo | Should |
| Phase 5: Enter-between-blocks guard (#25) | todo | Could |
