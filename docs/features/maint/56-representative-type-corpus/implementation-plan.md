---
id: "MAINT-56-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-56"
folders:
  - "[[features/maint/56-representative-type-corpus/requirements|requirements]]"
---

# MAINT-56: Implementation Plan

Two PRs: Phases 1–3 (tooling, no measurement) and Phase 4 (the run and its results). Every Gradle run
goes through the builder with `--rerun --no-build-cache`; nothing under `src/main` changes, and a diff
there fails the phase. Python tests run as `python3 -m unittest discover -s tooling/corpus -p 'test_*.py'`.
There is no live-IDE surface, so there is no `human-verification-checklists.md`.

### Phase 1: Manifest and fetch [Must]
- **Tasks**:
  - [x] Create `tooling/corpus/type-domains.json` from design §2.0, its counts and `measuredAt` from
        risks-and-gaps DR-12, and `tooling/corpus/type-corpus.json` from design §2.1, each with a
        `$comment` naming its fields and pointing at the design.
  - [x] `fetch-corpus.py`: `--manifest`, `sparse`, `sparse_patterns` — design §5.1.
  - [x] Create `tooling/corpus/type_corpus.py` with `load`, `load_domains`, `weights_of`, `coverage`,
        `NAMED_HOSTS`, `share_crossings`, `check`, `density`, `measure_usage`, `gh_search` and the `check`
        and `usage` subcommands — design §2.0, §3.1, §4.2.
  - [x] Create `tooling/corpus/test_type_corpus.py` with TC-1 to TC-5, TC-19 to TC-21, TC-24, TC-25 and
        their fixtures.
  - [x] `tooling/corpus/README.md`: one section "Type corpus" naming the manifest, the fetch command and
        `type_corpus.py`, citing this design for everything else.
- **Exit criteria**: the unittest command green; `python3 tooling/corpus/type_corpus.py check
  --checkouts test/corpus` exits 0 after the fetch below;
  `python3 tooling/corpus/fetch-corpus.py --manifest tooling/corpus/type-corpus.json` fetches every member
  and its definition archives; `python3 tooling/corpus/fetch-corpus.py` alone leaves the existing
  checkouts at their stamps; each TC's mutation, applied by hand, turns it red, then reverted.

### Phase 2: Harness [Must]
- **Tasks**:
  - [x] `TypeAuditExtras` (design §3.2, §3.8, §4.1) and `TypeAuditExtrasTest` TC-13.
  - [x] Before touching shared code, on the base commit: run the MAINT-54 audit (MAINT-54 design §5
        command) and keep `build/type-audit/` as TC-8's reference.
  - [x] `CorpusManifest`, `AuditOutput.writeTo`, `AuditRunMode`, `AuditMemberRun`; move
        `LuaTypeFalseNegativeAuditTest` onto `AuditMemberRun` — design §5.2.
  - [x] Create `LuaRepresentativeTypeAuditTest` and `RepresentativeMemberSetTest` (TC-6); add TC-7 to
        `TypeAuditExtrasTest`.
  - [x] Add the `withTypeCorpus` block to `build.gradle.kts` — design §5.3.
- **Exit criteria**: `run test` green with the new routine tests present and
  `LuaRepresentativeTypeAuditTest` absent from its report; `run ktlintCheck` green; TC-8 (the MAINT-54
  audit re-run, `diff -r` against the reference empty except the DR-14 site, risks-and-gaps DR-14); each
  Kotlin TC's mutation turns it red, then reverted.

### Phase 3: Summary [Must]
- **Tasks**:
  - [x] Create `tooling/corpus/type_bug_map.json` from design §3.6.
  - [x] Add `exposure`, `domain_verdicts`, `false_positives`, `bug_for_cell`, `bug_rules`,
        `weights`, `render` and the `summarize` subcommand to `type_corpus.py` — design §3.3–§3.7, §4.2, §4.3.
  - [x] Add TC-9 to TC-12, TC-16, TC-18, TC-22 and TC-23, with a synthetic run directory under
        `tooling/corpus/testdata/type_corpus/run/`; TC-16's fixture is the MAINT-54 "Cell → BUG" table,
        transcribed.
- **Exit criteria**: the unittest command green; each TC's mutation turns it red, then reverted;
  `scripts/lint_docs.py docs` 0 errors. Phases 1–3 merge as one PR.

### Phase 4: Run and results [Must; the FP sample is Should]
- **Tasks**:
  - [ ] `python3 tooling/corpus/type_corpus.py usage`; commit the re-measured `type-domains.json`. The
        sampled set and the members stay as they are whatever the new shares; a 2 % crossing is only
        reported, by `summarize` (design §2.0, "The sampled set is frozen").
  - [ ] Amendment (DR-15; [[BUG-96]], [[BUG-97]]): the per-member deadline of design §5.2 (`MemberDeadline`,
        `AuditRunMode.memberLimit` = 30 min, `ExtrasPayload.timedOut`) with TC-28, and `summarize`'s
        `timedOut` list (design §4.3) with TC-27. The member set stays frozen: a timed-out member stays in
        the manifest and is listed, not dropped.
  - [ ] Run design §5.3's command; copy back; `python3 tooling/corpus/type_corpus.py summarize
        build/type-corpus --checkouts test/corpus --lunar-commit <the run's commit> --out
        tooling/corpus/rule-weights.json`.
  - [ ] Create `results.md` (`type: "results"`, `parent_id: "MAINT-56"`): run command, Lunar commit,
        builder and duration, the summary output verbatim, the density table, the readings of design §3.7,
        and (Should) the false-positive sample of design §3.8.
  - [ ] Each `unmapped` silent cell: file a BUG (`loom-cli id mint BUG`, `report-bug` skill) with a roadmap
        row, as MAINT-54 Phase 2 did.
  - [ ] In MAINT-55's `requirements.md`, replace the Open Question on weighting with a link to
        `tooling/corpus/rule-weights.json` and design §4.3.
  - [ ] Set this feature `done`; delete the MAINT-56 roadmap row.
- **Exit criteria**: `rule-weights.json` committed and `summarize` reproduces it byte-identically from
  the copied-back run; `results.md` has every section of MAINT-56-10; `scripts/lint_docs.py docs` and
  `scripts/lint_planning.py` 0 errors.

## Requirement → Phase Coverage
Phase 1: MAINT-56-01, -02 (density), -03, -12. Phase 2: -04, -07 (collection). Phase 3: -05, -06, -07
(rates), -08, -09. Phase 4: -10, -11.
