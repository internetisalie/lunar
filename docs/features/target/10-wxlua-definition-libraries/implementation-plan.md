---
id: TARGET-10-PLAN
parent_id: TARGET-10
type: plan
folders:
  - "[[features/target/10-wxlua-definition-libraries/requirements|requirements]]"
title: "Implementation Plan"
---

# TARGET-10: Implementation Plan

> **Precondition**: [design.md](design.md) has cleared the bar. Three measurements —
> **DR-02** (static-method encoding), **DR-06** (namespace file layout) and **DR-04** (indexing
> budget) — must be run before the phases that consume them; each is named in the phase that needs
> it and each has every outcome specified, so none leaves a design decision to the implementer.
>
> **Scope guard for every phase**: no file under `src/main/kotlin/net/internetisalie/lunar/` may be
> modified. If a phase seems to require it, the emission shape is wrong — stop and re-read design
> §3.5/§3.6.

## Phases

### Phase 0: De-risking [Must]

- **Goal**: retire the two measurements the later phases branch on, before any generator code exists.
- **Tasks**:
  - [ ] Run **DR-02** — add a throwaway test extending `LibraryRootTestCase` that registers a root
        declaring both `function wx.wxFileName(path) end` (with `---@return wxFileName`) and
        `function wx.wxFileName.GetCwd() end`, then asserts (a) `local f = wx.wxFileName("x")` +
        `f:<caret>` completes an instance method **and** (b) `wx.wxFileName.<caret>` completes
        `GetCwd`. Both pass → Branch A (`statics_mode="dotted"`). Either fails → Branch B
        (`statics_mode="on-class"`). Record the verdict and the pasted test output in
        [risks-and-gaps.md](risks-and-gaps.md), and fold it into design §3.6.
  - [ ] Run **DR-06** — the TC 7a spike: register a root whose `wx.lua` holds only
        `---@class wx` + `wx = {}` + `return wx` and whose `wx/wxcore.lua` holds
        `---@type number` + `wx.wxID_ANY = nil` with **no** `---@class wx` re-anchor; assert
        `wx.wxID_<caret>` completes `wxID_ANY`. Passes → `split` (design §3.5.2 as written). Fails →
        `single` (one self-contained file per namespace). Record the verdict and the pasted output in
        [risks-and-gaps.md](risks-and-gaps.md), and fold it into design §3.5.2.
  - [ ] Run **DR-04** — generate a throwaway tree of the right order of magnitude (~5,500
        `---@type number` constants + ~4,000 method stubs), register it with `registerLibraryRoot`,
        and measure first-index wall-clock and heap against a run with no root. Record the numbers;
        set the Non-Functional budget in [requirements.md](requirements.md) from them.
  - [ ] Fetch and pin the wxLua checkout: create `tooling/definitions/wxlua/wxlua.json`
        (design §2.5) and verify `bindingsDir` exists at the pinned commit.
  - [ ] Re-run design §4.1's counting commands against the pinned checkout and confirm every
        non-zero form has a §3.3 rule. A new non-zero form is a design change, not an
        implementation detail — stop and amend §3.3 rather than improvising a rule.
- **Exit criteria**: `statics_mode` and the layout are decided and written into design §3.6/§3.5.2;
  the indexing budget is a number in requirements → Non-Functional; `wxlua.json` is committed and
  the form counts re-confirmed.

### Phase 1: Parser [Must]

- **Goal**: `.i` + `*_rules.lua` → the `Group` model, with the full declaration grammar.
- **Tasks**:
  - [ ] Create `tooling/definitions/wxlua/wxi_parser.py` with the dataclasses and the two public
        functions in design §2.2.
  - [ ] Implement `namespaces()` — realizes design §3.1 (including the empty-namespace skip).
  - [ ] Implement line normalisation — realizes design §3.2, **all ten steps in the stated order**.
        The order is load-bearing and five steps carry a ⚠ with the measured cost of omitting them;
        in particular `//` line comments are stripped **before** `/* */` spans (step 1 before
        steps 2–3), not after. Do not reorder, and do not skip steps 4 (sigil detach), 5 (bounded
        continuation join) or 6 (bare `wxUSE_*` prefix).
  - [ ] Implement declaration recognition — realizes design §3.3, all **seventeen** anchored ordered
        rules including `struct`, plus the brace bookkeeping (enum state checked before class state),
        first-occurrence-wins de-duplication, and class re-opening merge.
  - [ ] Implement parameter-list parsing — realizes design §3.3b (depth-aware comma split, default
        values, unnamed `argN`, array suffixes, optional cascade).
  - [ ] Create `tooling/definitions/wxlua/testdata/` fixtures: a `zz_rules.lua` + `zz_a.i` pair
        (TC 4), a `qq_rules.lua` with an empty namespace (TC 5), and a `sample.i` exercising **every**
        non-zero form in design §4.1's table — `#define`, `%wxEventType`, all four `#define_*`,
        `%rename`, `%override_name`, `%member_func`, a `!%`-negated guard, a bare `wxUSE_*`
        condition prefix, an enum, **an attributed `class %delete Name : public Base`**, a plain
        class, a constructor, a static, a pure virtual `= 0;`, a `const T&` return, a
        `T *Name()` pointer-bound-to-name, an `operator`, a multi-line declaration, a `/* */`
        default inside a parameter list, a `//` line containing `/*`, a multi-line `/** */` prose
        block with an unmatched `(`, **a compound guard `%A && %B <decl>`**, **a `struct %delete Name`
        with a static member**, and a `//`-commented declaration (TC 1–3, 9a–9f), plus a qualified `class Parent::Nested` and a guard-preceded
        `%rename` (`%wxchkver_3_0_0 %rename LeftDown bool LeftIsDown() const;`).
  - [ ] Add `tooling/definitions/wxlua/test_generate.py` (stdlib `unittest`) covering TC 1–5, 9a–9f.
  - [ ] Assert the parser reproduces design §3.3's measured firing counts against the pinned
        checkout **and** that its emitted name set matches the probe's `names2.json` in **both**
        directions. A count-only check is insufficient: a surplus name (`wx.IsCompatible`, the
        `struct` hole) raises the count and passes. `tooling/spikes/target-10-wxi-grammar/probe.py`
        is the empirical reference, but **where it and design §3.2/§3.3 differ, the design wins** —
        the probe implements no parameter parsing at all (§3.3b), and the two differ in a
        handful of regex details. Delete the probe once this parser matches.
  - [ ] **Classify** — do not spot-check — the ~389 `;`-terminated lines the parser does not
        recognise. Each is either a plain C++ struct field (the large majority; correctly ignored)
        or needs a new §3.3 rule. Two of the four grammar holes found during planning were sitting
        in this residue, and both emitted *invented* API. Finding a further form here is expected
        work, not a re-plan: add the rule, re-run, update design §3.3's counts.
  - [ ] Assert no emitted class carries a member declared inside a differently-named type — the
        qualified-name (`Parent::Name`) check. Nothing else catches it: those lines *match* rule 9,
        so neither the residue classification nor the coverage ratchet can see the corruption.
- **Exit criteria**: `python3 -m unittest discover tooling/definitions/wxlua` green; TC 1–5 and
  9a–9f pass; parsing the real pinned checkout raises no unbalanced-brace error, emits **no**
  namespace with zero declarations, and reproduces the §3.3 counts.

### Phase 2: Emitter [Must]

- **Goal**: `Group` → LuaCATS text in the exact shape Lunar resolves.
- **Tasks**:
  - [ ] Create `tooling/definitions/wxlua/emit.py` with the three functions in design §2.3.
  - [ ] Implement `map_type` — realizes design §3.4, including the alias-name set and the `void`
        return special case.
  - [ ] Implement `render_namespace_root` — realizes design §3.5.1 (`---@class`, global assignment,
        `return`), in the layout DR-06 selected.
  - [ ] Implement `render_group` — realizes design §3.5.2–§3.5.8: header, aliases, constants,
        classes (class block → instance methods → statics → constructors), free functions.
  - [ ] Implement parameter rendering — realizes design §3.5.7 (keyword suffixing, `?` for optional,
        varargs, collision suffixing).
  - [ ] Implement overload rendering — realizes design §3.5.6 (primary selection, ordering).
  - [ ] Implement `statics_mode` per the Phase 0 DR-02 verdict — realizes design §3.6.
  - [ ] Extend `test_generate.py` with the emission cases.
- **Exit criteria**: emitted text for the `sample.i` fixture matches a checked-in golden file
  byte-for-byte; TC 1–3 assert on the emitted text.

### Phase 3: Driver, determinism and coverage [Must]

- **Goal**: a runnable end-to-end generator whose output is reviewable and whose coverage is ratcheted.
- **Tasks**:
  - [ ] Create `tooling/definitions/wxlua/generate.py` — realizes design §2.1, including `--check`.
  - [ ] Implement every ordering rule — realizes design §3.7 (sorted iteration, fixed section order,
        `\n` newlines, single trailing newline, UTF-8 no BOM).
  - [ ] Implement the regeneration rules — realizes design §6: overwrite generated files, write
        `supplement.lua` / `LICENCE` / `config.json` / `PROVENANCE.md` only if absent, delete
        no-longer-generated files with `library/supplement.lua` exempt.
  - [ ] Create `tooling/definitions/wxlua/supplement.lua` by **re-deriving** it per design §4.5: run
        the generator, run the coverage report, and add exactly the names in `coverage.json`'s
        `missing` array, each with a `-- absent from .i: <why>` comment naming the `*_override.hpp`
        or C++ site that registers it. Do **not** copy design §4.5's three-row table blind — it is
        the expected answer, and a name that turns out to be declared belongs in §3.3, not here.
        Assert the generated/curated name sets are disjoint (TC 11a).
  - [ ] Create `tooling/definitions/wxlua/coverage.py` — realizes design §2.4 and §3.8; write
        `coverage.json` in the design §4.3 schema.
  - [ ] Run the generator against the real pinned checkout and the pinned ZeroBrane corpus; commit
        `coverage-floor.json` set from that first real run (design §3.8).
  - [ ] Add `tooling/definitions/wxlua/README.md`: how to fetch the checkout, run the generator,
        read the coverage report, and publish (Phase 4).
- **Exit criteria**: TC 9 passes (`--check` against a freshly generated tree is silent); TC 15
  passes; `coverage.json` reports ≥ the committed floor for `wx`, `wxstc`, `wxaui`.

### Phase 3b: Type-error delta [Must]

- **Goal**: know what typing the members costs before anyone fetches it.
- **Blocked on BUG-419 landing.** Measured before it, wx-induced errors are buried in ZeroBrane's
  4,452-emission assignability floor (99.9 % of which BUG-419 demotes); measured after, each is
  visible and attributable to a §3.4 row.
- **Tasks**:
  - [ ] Run **DR-08** — record ZeroBrane's `LuaTypeAssignability` (baseline 358) and
        `LuaReturnTypeMismatch` (65) with the generated tree registered, and diff against
        `src/test/resources/corpus/zerobrane.baseline`. Triage every new hit to a §3.4 row.
  - [ ] Spike `---@param x number` vs `---@param x integer` against a numeric literal at `LUA51`,
        and paste the result into design §3.4. The mapping choice is currently argued from
        `LuaPrimitiveType.kt:10-18` and `LuaGraphType.kt:147` — reading, not running.
  - [ ] Widen any §3.4 row a new hit traces back to. **Never narrow user code to fit the library.**
- **Exit criteria**: the type-error delta is a recorded number with every new hit attributed; §3.4
  is updated from the spike.

### Phase 4: Publish and catalogue [Must]

- **Goal**: the tree is fetchable and the plugin knows about it.
- **Tasks**:
  - [ ] Create the `lunar-definitions-wxlua` repository with the layout in design §7
        (`library/`, `config.json`, `LICENCE`, `PROVENANCE.md`, `README.md`). **This is an
        outward-facing publication — confirm with the maintainer before pushing.**
  - [ ] Commit the generated tree; record the commit SHA.
  - [ ] Download `https://github.com/<owner>/lunar-definitions-wxlua/archive/<sha>.tar.gz`; read
        back its `sha256` and byte `size`.
  - [ ] Append the `wxlua` entry to `src/main/resources/definitions/lunar-definitions-catalog.json`
        with the exact fields in design §7 — including `detectionPatterns` (TARGET-10-11).
  - [ ] Add the `THIRD-PARTY.md` row (TARGET-10-10): component, upstream, wxWindows Library Licence
        v3, and the "fetched at runtime, never bundled" note.
- **Exit criteria**: `LuaDefinitionCatalogLoader.load()` succeeds with the new entry (TC 13, TC 16);
  `everyBundledEntryIsPinnedAndAttributed` still passes; the full test suite is green.

### Phase 5: Acceptance tests [Must]

- **Goal**: prove the emitted shape resolves in the IDE, without network.
- **Tasks**:
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/definitions/LuaWxLuaDefinitionShapeTest.kt`
        extending `LibraryRootTestCase` (`:33`).
  - [ ] Add the instance-method case — covers TC 6.
  - [ ] Add the constant-completion case — covers TC 7.
  - [ ] Add the **emitted-layout** case, promoted from the DR-06 spike — covers TC 7a. This is the
        one that must mirror what the generator actually writes; TC 6 and 7 are single-file shape
        tests and would pass regardless.
  - [ ] Add the cross-file, cross-namespace inheritance case — covers TC 8.
  - [ ] Add the supplement case (`wxaui.wxAUI_TB_PLAIN_BACKGROUND`) — covers TC 10.
  - [ ] Add the `require("wx")` resolution case, modelled on
        `LuaLibraryModuleResolutionTest.testRequireResolvesIntoALibraryRoot` — covers TC 14.
  - [ ] Extend `LuaDefinitionCatalogLoaderTest` with a `wxlua`-specific assertion — covers TC 13, 16.
  - [ ] Add a Python test asserting the supplement survives regeneration — covers TC 11.
  - [ ] Add a Python test asserting the published layout is produced — covers TC 12.
- **Exit criteria**: every TC 1–16 has a passing automated check; the **full** suite is green
  (`--rerun --no-build-cache`, per the gce-builder cache lesson), not just `--tests *WxLua*`.

### Phase 6: Documentation and status [Should]

- **Goal**: the feature is discoverable and the tracker reflects reality.
- **Tasks**:
  - [ ] Add a new **Definition Libraries** section to
        [`docs/features/target/user-guide.md`](../user-guide.md) — the file currently has only
        *Selecting a Target*, *Supported Platforms*, *Integration with Luacheck* and *Legacy
        Projects*, so this is a new heading, placed after *Supported Platforms*. Cover: how to
        enable a library, that wxLua is fetched rather than bundled, its wxWindows licence, and
        (if DR-02 landed on Branch B) that `wx.wxFileName.GetCwd()` does not complete.
  - [ ] Run [human-verification-checklists.md](human-verification-checklists.md) against a real IDE.
  - [ ] Set this feature's front-matter `status: done`; delete the TARGET-10 row from
        `docs/roadmap.md`; note in MAINT-37 that its blocker is cleared.
  - [ ] `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py` clean.
- **Exit criteria**: checklists recorded Pass; linters clean; roadmap row removed.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|---|---|---|
| TARGET-10-01 | M | Phase 1, Phase 2 |
| TARGET-10-02 | M | Phase 1 |
| TARGET-10-03 | M | Phase 0 (DR-02, DR-06), Phase 2, Phase 5 (TC 7a) |
| TARGET-10-04 | M | Phase 3 |
| TARGET-10-05 | M | Phase 3 |
| TARGET-10-06 | M | Phase 4 |
| TARGET-10-07 | M | Phase 4 |
| TARGET-10-08 | M | Phase 2 (§3.5.1), Phase 5 (TC 14) |
| TARGET-10-09 | S | Phase 3 |
| TARGET-10-10 | S | Phase 4 |
| TARGET-10-11 | C | Phase 4 |

## Verification Tasks

- [ ] `test_generate.py` — covers TC 1, 2, 3, 4, 5, 9, 9a–9f, 11, 11a, 12, 15.
- [ ] `LuaWxLuaDefinitionShapeTest` — covers TC 6, 7, 7a, 8, 10, 14.
- [ ] `LuaDefinitionCatalogLoaderTest` extension — covers TC 13, 16.
- [ ] Full-suite gate with `--rerun --no-build-cache` before each phase is called done (a green
      isolated `--tests` run has hidden a full-suite failure before).
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md).
- [ ] Confirm `git diff --stat src/main/kotlin` is empty across the whole feature.

## Task Summary

| Phase | Status | Priority |
|---|---|---|
| Phase 0: De-risking | todo | Must |
| Phase 1: Parser | todo | Must |
| Phase 2: Emitter | todo | Must |
| Phase 3: Driver, determinism and coverage | todo | Must |
| Phase 3b: Type-error delta | todo | Must |
| Phase 4: Publish and catalogue | todo | Must |
| Phase 5: Acceptance tests | todo | Must |
| Phase 6: Documentation and status | todo | Should |
