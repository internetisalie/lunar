---
id: "MAINT-49"
title: "49: Fixture Tests Run in CI"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-49: Fixture Tests Run in CI

## The gap

CI runs the unit suite with `-PexcludeExternalFixtureTests`, which skips four classes because they
read the out-of-repo `test/` tree (`build.gradle.kts:397-415`):

| Class | Reads |
|---|---|
| `LuaRecursiveReferenceTest` | `test/luacheck/src/luacheck/parser.lua`, through a `test/luacheck` symlink to a checkout outside the tree |
| `LuaDescriptionIndexTest` | `testIndexRealWorldProjectAndMeasureSize` walks all of `test/` |
| `ParseOracleTest` | `test/luac/<version>/luac`, compiled by `tooling/corpus/fetch-luac.py` |
| `LuaTypeGoldenMemberFilesTest` | four files of the pinned corpus under `test/corpus/` |

So CI and the builder run different test sets, and a green CI build says nothing about these four.
[[MAINT-48]] stores CI's results as the per-commit baseline, and an excluded set makes every
comparison against a builder run carry an exception.

Most of what they read is already reproducible: the `corpus` job provisions it with
`fetch-corpus.py` and `fetch-luac.py`, each pinned by SHA. Compiling the five `luac` oracles is the
expensive part of a cold provision (DR-04).

## Scope

### In Scope
- **Read the pinned corpus.** `LuaRecursiveReferenceTest` reads the corpus copy of luacheck, and
  `testIndexRealWorldProjectAndMeasureSize` walks one pinned corpus member with a budget a real
  index blow-up exceeds.
- **Download `luac`.** `fetch-luac.py` installs the [[TOOLING-09]] release binaries, verified by
  archive sha256, instead of compiling. The 5.5 pin moves from 5.5.1 to 5.5.0, the release
  TOOLING-09 publishes.
- **Provision in every lane that runs the suite.** Gitea `build` and `build-release`, and GitHub
  `build`, run the two fetchers before the tests.
- **Drop the exclusion.** Remove `-PexcludeExternalFixtureTests` from both workflows and its filter
  from `build.gradle.kts`.

### Out of Scope
- **The hand-made files in `test/`** (`cases/`, `examples/`, `regression/`, `schema/`, `rocks/`, the
  loose `.lua` files). After this feature no unit test reads them; they remain the `runIde` sandbox
  project. Whether to version them is a separate decision (risks Gap 2.1).
- **The CI image.** `lua5.4` + `lua-socket` stay for the debugger tests (a static Lua cannot load C
  modules). `build-essential` becomes unused by these jobs; removing it is an image change
  (risks Gap 2.2).
- **The `corpus` job's ratchet.** It runs the new `fetch-luac.py` unchanged.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-49-01 | **Recursive-reference test reads the corpus** | M | Not Implemented | `testLuacheckParserRecursiveCall` copies `test/corpus/luacheck/src/luacheck/parser.lua`; no test source names `test/luacheck/` (design §2.1) |
| MAINT-49-02 | **Index-size guard walks one pinned member** | M | Not Implemented | `testIndexRealWorldProjectAndMeasureSize` walks `test/corpus/lazynvim` and asserts its pinned line count, measures the project-scope index only and asserts no bundled-definition record is counted, and fails above 10 bytes per line (design §2.2, §3.2) |
| MAINT-49-03 | **`fetch-luac.py` downloads and verifies** | M | Full | Downloads each pinned archive, refuses on a sha256 mismatch before extracting, installs only `bin/luac` after it reports its version, stamps the archive digest, and skips a stamped install (design §2.3, §3.1) |
| MAINT-49-04 | **`luac.json` pins the TOOLING-09 release** | M | Full | Five `linux-x86_64` archives of release `v2026.10.30`, each with its archive sha256; `LUA55` pins 5.5.0 (design §2.4, §4.1) |
| MAINT-49-05 | **Gitea `build` provisions fixtures** | M | Not Implemented | Re-points `test` at `/cache/lunar/test` and runs both fetchers before the build; a failure is recorded, fails the job in the final step, and suppresses the `a3` store (design §2.5) |
| MAINT-49-06 | **The other suite lanes provision fixtures** | M | Not Implemented | Gitea `build-release` and GitHub `build` run both fetchers into a fresh `test/` before the tests, failing the job on any fetch failure (design §2.6) |
| MAINT-49-07 | **The exclusion is gone** | M | Not Implemented | No workflow passes `-PexcludeExternalFixtureTests`; `build.gradle.kts` has no such filter; the four classes run in every CI suite lane (design §2.7) |
| MAINT-49-08 | **The glibc preflight is removed** | S | Full | `check_luac_glibc` and its README section go: static binaries carry no `GLIBC_` symbol (design §2.8) |
| MAINT-49-09 | **No stale prose** | S | Not Implemented | Comments, KDoc and READMEs stop describing the exclusion, the compile, and `test/luacheck` (design §2.9) |

## Test Cases

Each mutant is a change to the code under test that must turn its case red. "Executed" means the
mutant was run during planning (risks-and-gaps DR rows); the rest follow from the named assertion.

| # | Req | Given | When | Then | Mutant that turns it red |
|---|-----|-------|------|------|--------------------------|
| 1 | 01 | CI `build` (Phase 3), whose `test/` is `/cache/lunar/test` and has no `luacheck` entry | the suite runs | every `LuaRecursiveReferenceTest` case passed in `build/test-results/test/` | path reverted to `test/luacheck/src/luacheck/parser.lua`: `copyFileToProject` finds no file on CI |
| 2 | 01 | the tree after Phase 1 | `git grep -n '"test/luacheck/' -- src` | no output, exit 1 | the old path left in any test source |
| 3 | 02 | `test/corpus/lazynvim` at its `corpus.json` pin | builder `test --rerun --no-build-cache --tests '*LuaDescriptionIndexTest'` | passes; the estimate is under `PINNED_LINES × MAX_BYTES_PER_LINE` (DR-02) | `DescriptionRecord(owner.text, …)` in `LuaDescriptionIndex.Indexer.map`: over budget (executed, DR-02) |
| 4 | 02 | the same | the same | passes | walk root misspelled (`test/corpus/lazy.nvim`): §3.2 step 1's `corpus not fetched` assertion fails |
| 5 | 02 | the same | the same | passes | walk root `test` instead of `SIZE_MEMBER`: step 3's line-count assertion fails (DR-02 walked `test` and counted a different total) |
| 6 | 02 | the same | the same | passes | `GlobalSearchScope.allScope(project)` in `projectIndexBytes`: step 4 fails on a `jar://` record (DR-02 found the bundled definitions' records in `allScope` for every member) |
| 7 | 03 | a `tar -czf` archive holding `lua-5.1.5-linux-x86_64/bin/luac` (prints `Lua 5.1.5 compiler`) and `bin/lua` (prints `Lua 5.1.5 interpreter`); manifest with its true digest | `FetchLuacScriptTest` runs `fetch-luac.py` | exit 0; installed `luac` contains `compiler`; `.luac-sha` holds the digest | member `bin/lua` instead of `bin/luac`: installed file contains `interpreter` (executed, DR-05) |
| 8 | 03 | that archive; manifest digest `0`×64 | the same | exit ≠ 0; no `luac`, no `.luac-sha` | digest comparison removed: both present, exit 0 (executed, DR-05) |
| 9 | 03 | an archive whose `bin/luac` prints `Lua 5.4.8 compiler`; true digest | the same | exit ≠ 0; no `.luac-sha` | version check removed: stamped (executed, DR-05) |
| 10 | 03 | `5.1.5/luac` = `kept`, `.luac-sha` = the manifest digest; manifest URL names a missing file | the same | exit 0; `luac` still `kept` | stamp check removed: the download fails, exit 1 (executed, DR-05) |
| 11 | 03 | an archive with `LICENSE` and `bin/lua` but no `bin/luac`; true digest | the same | exit ≠ 0; output contains `archive lacks lua-5.1.5-linux-x86_64/bin/luac` | member check removed: a traceback, no such line (executed, DR-05) |
| 12 | 04 | the Phase 2 `luac.json`; an empty `LUNAR_LUAC_ROOT` | `python3 tooling/corpus/fetch-luac.py` | exit 0; five `-> …/luac` lines; each `.luac-sha` equals the `SHA256SUMS` line of its asset | any pinned digest altered by one character: exit 1 at that entry |
| 13 | 04 | binaries from case 12 under `test/luac` | builder `--tests '*ParseOracleTest'` | every case passes (DR-03) | `LUA55` left at `5.5.1`: `testPinnedVersionsComeFromTheManifest` fails |
| 14 | 05 | a Phase 3 Gitea `build` run | it completes | log shows `fixtures ok`; the stored record's `environment.extra.filter.excludePatterns` contains none of the four class patterns | the provisioning step deleted: the four classes fail (`corpus not fetched`, `No luac for LUA51`) and the job is red |
| 15 | 05 | a throwaway PR whose `luac.json` LUA51 digest is `0`×64 | Gitea `build` runs | the job is red with `provisioning the pinned fixtures failed`; the store step prints `not storing:`; `junit_ctrf.py fetch --commit <PR head> --out f.json` exits 3 | the store guard removed: an `a3` record is stored with the four classes failed |
| 16 | 06 | the first GitHub `build` run on `main` after Phase 3 merges (the GitHub mirror is pushed after each merge; GitHub runs no Gitea PR), and the next `v*` tag's Gitea `build-release` | they run | the provisioning step succeeds; their test reports include the four classes | provisioning step deleted: the four classes fail and the job is red |
| 17 | 07 | the tree after Phase 3 | `git grep -n excludeExternalFixtureTests -- . ':!docs'` | no output, exit 1 | the flag left on any `./gradlew` line |
| 18 | 08 | the tree after Phase 2 | `git grep -n check_luac_glibc -- . ':!docs'` | no output, exit 1 | the function or its call left in `gce-builder.sh` |
| 19 | 09 | the tree after Phase 3 | `git grep -n -e 'test/luacheck' -e 'not run in CI' -e 'CI skips' -- src tooling build.gradle.kts` | no output, exit 1 | any listed comment left unedited |

One property has no case that can fail, and says so rather than invent one:
- **The oracle swap changes no verdict.** DR-03 measured it directly. The ratchets cannot confirm it
  in the permissive direction. A swap that makes `luac` accept input Lunar rejects raises
  `oracleDisagreements`, which both ratchets gate. A swap that makes `luac` accept input Lunar
  already accepts *lowers* `oracleFalseAccepts`: on the corpus members that count is not gated
  (`CorpusMetrics.kt:42-48`), and on the torture corpus it is gated (`TortureCorpus.kt:249-255`,
  BUG-409) but only against an increase, so a decrease prints `IMPROVED` (`TortureCorpus.kt:268,288-289`)
  and stays green. DR-03's one 5.1.5→5.4.8 flip is of this kind.

## Acceptance Criteria
- [ ] Cases 2–6 and 17–19 hold on their phase's tree (MAINT-49-01, -02, -07, -08, -09).
- [ ] `FetchLuacScriptTest` passes in the unit suite (cases 7–11; MAINT-49-03).
- [ ] Cases 12 and 13 hold, and the Phase 2 PR's `corpus` job passes (MAINT-49-04).
- [ ] Cases 1, 14 and 15 hold on the Phase 3 PR (MAINT-49-01, -05).
- [ ] Case 16 holds on the first GitHub `main` run after Phase 3 and on the first tag after it (MAINT-49-06).
- [ ] The full suite (`test --rerun --no-build-cache`) is green on the builder after each phase.

## Non-Functional Requirements
- **Cost.** A cold provision costs seconds and a warm one is a stamp check (DR-04, DR-05).
- **Threading.** No plugin code changes. The tests touched are light fixtures or plain JUnit.

## Dependencies
- [[TOOLING-09]]: release `v2026.10.30` of `internetisalie/lua-binaries` (published). Phase 2 only.
- [[MAINT-48]]: the stored test-set identity changes when Phase 3 merges (design §5).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
