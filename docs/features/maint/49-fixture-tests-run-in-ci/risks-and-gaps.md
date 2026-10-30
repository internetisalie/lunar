---
id: "MAINT-49-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-49"
folders:
  - "[[features/maint/49-fixture-tests-run-in-ci/requirements|requirements]]"
---

# MAINT-49: Risks & Gaps

## Pre-Implementation De-risking Tasks

The plan's load-bearing premises were runtime claims, so each was executed before the design. All
builder runs were on the libvirt builder at worktree `adf755f5`, in a separate directory
(`/home/builder/m49probe`), synced by a copy of `gce-builder.sh cmd_sync` that left out the fixture
tree's `test/test` loop (Risk 1.3) and the compiled `test/luac`, plus a throwaway overlay. The
overlay is deleted; nothing from it is committed.

| ID | Premise (kind) | Command / method | Observed | Verdict |
|----|----------------|------------------|----------|---------|
| MAINT-49-00-DR-01 | `LuaRecursiveReferenceTest` passes unchanged against `test/corpus/luacheck` at `cc089e3f` (runtime) | `sha256sum test/luacheck/src/luacheck/parser.lua test/corpus/luacheck/src/luacheck/parser.lua`; then the class copied as `M49RepointProbeTest` with only the path changed, run 1: `test --rerun --no-build-cache --tests '*ParseOracleTest' --tests '*LuaRecursiveReferenceTest' --tests '*M49RepointProbeTest' --tests '*LuaDescriptionIndexTest' --tests '*LuaTypeGoldenMemberFilesTest' --tests '*M49DescProbeTest'` | Both files `3cb8ebfcc2e4409a80a11a79d2b70f34b349f98dce1331844a74c41513638554`, 1,025 lines; line 613 `local function parse_subexpression(state, limit, kind)`, line 621 `local operand = parse_subexpression(state, unary_priority)`. `cc089e3f` is an ancestor of `f0fdfde`; the 12 files they differ in do not include `parser.lua`. Run 1 `BUILD SUCCESSFUL in 3m 27s`; `M49RepointProbeTest` and `LuaRecursiveReferenceTest`: every case passed, 0 failures, 0 errors | **confirmed**: no asserted count or line moves |
| MAINT-49-00-DR-02 | Which member `testIndexRealWorldProjectAndMeasureSize` should walk, and whether its assertion can fail (runtime) | `M49DescProbeTest`: the test's own metric, plus the same metric over `GlobalSearchScope.projectScope` skipping keys with no values, plus the origin of every record, for `test` and each member, with lazynvim at its `corpus.json` pin `85c7ff3711` (65 `.lua` files). Run 1 and run 3 on the real indexer; run 2 and run 4 with `DescriptionRecord(owner.text, fileUrl, owner.textOffset)` in `LuaDescriptionIndex.Indexer.map` | Real indexer, `allScope` bytes / lines (B/line): `test` 315,448 / 273,391 (1.15); zerobrane 98,441 / 104,304; luacheck 97,986 / 21,915; luarocks 103,229 / 34,503; lazynvim 129,431 / 14,327 (9.03); penlight 106,167 / 23,906. Every member's total includes the same 308 entries (558 records) whose files are under `jar:///…/plugins-test/lunar…` (bundled definitions); they are all of luacheck's. Project scope: luacheck **0** entries; zerobrane 10; luarocks 90; penlight 147 (9,006 B); **lazynvim 411 entries, 33,556 B, 2.34 B/line**. Mutant: lazynvim project scope **586,727 B (40.95 B/line)**; `test` `allScope` 2,118,190 B (7.75 B/line) against its 13,997,619-byte limit; the jar's 308 entries unchanged (97,986 B for luacheck in both runs). `LuaDescriptionIndexTest.testIndexRealWorldProjectAndMeasureSize` **passed under the mutant** in run 2. Walk times: `test` 19–26 s, lazynvim 1.0–1.2 s | **refuted, as written**: the 500 KB / 10 k-line budget passes a 17-fold blow-up. Design §3.2: lazynvim, project scope, 10 B/line |
| MAINT-49-00-DR-03 | The downloaded `luac` gives the compiled one's verdicts, and 5.5.0 gives 5.5.1's (runtime) | Throwaway differential: `luac -p -` on stdin from both binaries over every `.lua` of the five corpus members, the 1,697 `corpus-torture` files and 21 hand cases (5.5 `global` forms, `<const>`/`<close>`, `goto` into scope, `//`); compare exit status and the diagnostic after `stdin:`. Control: downloaded 5.1.5 against downloaded 5.4.8. Then run 1 with the downloaded binaries in `test/luac`, design §4.1's `luac.json`, and `ParseOracleTest`'s `"5.5.1"` changed to `"5.5.0"`. Source: `diff lua-5.5.0/src lua-5.5.1/src` | 5.1.5, 5.2.4, 5.3.6, 5.4.8 compiled vs downloaded: `inputs=2517 … verdictFlips=0 messageDiffs=0` each. 5.5.1 compiled vs 5.5.0 downloaded: `inputs=2517 acceptA=858 acceptB=858 verdictFlips=0 messageDiffs=0`. Control: 5 flips on the hand cases. Downloaded 5.1.5 vs 5.4.8 over corpus and torture: 1 flip, `corpus/luacheck/src/luacheck/vendor/sha1/lua53_ops.lua` (a `falseAccept` site in `luacheck.baseline:90`). Run 1: `ParseOracleTest` every case passed. Parser-side source differences 5.5.0→5.5.1: scanner-table anchoring, `repeat … until` scope closed after the upvalue fix-up, `#if defined(LUA_COMPAT_GLOBAL)` → `#if LUA_COMPAT_GLOBAL` with `luaconf.h` defining it in both, so `global` is unreserved in both | **confirmed** |
| MAINT-49-00-DR-04 | Cold fetch cost, and whether `build` should fetch a subset (runtime) | Gitea API job logs of two cold `corpus` jobs (`gradle cache: COLD`): 44976 (run 31665) and 44955 (run 31580), timestamps at the step boundary and at each fetcher's last line. Locally: `LUNAR_CORPUS_ROOT=<scratch> python3 tooling/corpus/fetch-corpus.py` | 44976: corpus 7.9 s, `luac` compile 32.9 s, torture 0.6 s, step 41.6 s. 44955: 7.5 s / 31.2 s / 0.7 s, step 39.5 s. Warm jobs 45074 and 45065: every fetcher "already … skipping", the step 0.33 s. Local cold corpus 10.0 s, 30 MB. The filed "about two minutes" for the compile is about 32 s on `lunar-ci` | **resolved**: fetch everything (design §9); the compile is what downloading removes |
| MAINT-49-00-DR-05 | `fetch-luac.py` can download, verify and install from the release, and each check in design §3.1 is load-bearing (runtime) | `curl` of `SHA256SUMS` and the five `linux-x86_64` archives; `sha256sum -c --ignore-missing SHA256SUMS`; `tar tzvf`; `file`; `objdump -T`. A prototype of design §3.1 run twice against the real release into an empty root, then against `tar -czf` fakes for requirements cases 7–11, then once per mutant: member `bin/lua`, digest check removed, version check removed, stamp skip removed, member check removed | All five `OK`; layout `lua-5.1.5-linux-x86_64/{LICENSE,bin/lua,bin/luac}`; `ELF 64-bit LSB executable, x86-64, … statically linked, stripped`; `objdump -T` lists no `GLIBC_` symbol (the compiled ones need `2.38`). First run: five `-> …/luac` lines, `rc=0 wall=1.47`; second run: five `already installed — skipping`; installed 5.5.0 `cmp`-identical to the archive's. Fakes: a `exit=0`, installed `luac` = compiler, stamp = digest; b `exit=1`, binary and stamp absent; c `exit=1`, stamp absent; d `exit=0`, `kept`; e `exit=1`, `archive lacks lua-5.1.5-linux-x86_64/bin/luac`. Each mutant reddened its own case: member → a installed `interpreter`; digest → b `exit=0`, both present; version → c stamped; skip → d `exit=1`; member check → e no message. `ParseOracleTest`'s decoy (`"not a lua tarball"`) is not a tar archive, so with the digest check removed `tarfile` fails and that test stays green | **confirmed**; replaced test noted in design §1 |

## Critical Risks

### Risk 1.1: The shared fixture tree changes under other branches
- **Impact**: Phase 2's local `fetch-luac.py` run re-installs `test/luac/<version>` with the new
  stamps. A branch still on the old `luac.json` then gets `ParseOracle`'s "not the pinned build"
  refusal on the builder until it rebases. It is a refusal, never a wrong verdict.
- **Likelihood**: medium.
- **Mitigation**: merge Phase 2 soon after its builder run, and name the re-run in the PR.

### Risk 1.2: Two provisioning steps first execute after merge
- **Impact**: GitHub runs only on `main` pushes, `v*` tags and GitHub PRs
  (`.github/workflows/build-plugin.yml:36-48`), and lunar's PRs are Gitea PRs, so GitHub `build`
  first runs the Phase 2 apt line and the Phase 3 step on the mirror's `main` push after each merge.
  Gitea `build-release` runs only on `v*` tags, so it first runs them on the next release, where a
  failure fails that release's job.
- **Likelihood**: low. Both steps are the commands Gitea `build` runs on the Phase 3 PR, without its
  `/cache` re-point and sentinel, under `bash -e`; they differ only in the `apt-get` line, which
  already runs in both lanes today.
- **Mitigation**: the GitHub `main` run after each of PR 2 and PR 3 is the check, and it precedes
  any tag; if red, a follow-up fix PR (not a revert by default), and no `v*` tag until it is green.
  The tag's own run is requirements case 16's second half; a red tag job publishes nothing.

### Risk 1.3: A symlink loop in the fixture tree breaks the builder sync
- **Impact**: `gce-builder.sh sync` copies `test/` with `rsync -L`. A link inside it that points at
  an ancestor makes rsync recurse until `Too many levels of symbolic links` and exit 23. A
  `test/test -> ../test` link observed during planning (since removed) did this: 178,146 entries,
  nested 40 levels. After Phase 1 no unit test walks all of `test/`, so the loop affects only the
  sync.
- **Likelihood**: low; it needs a mistaken link in an untracked tree.
- **Mitigation**: before a builder run on a fixture-tree change,
  `find -L /home/mini/Documents/src/lua/test -maxdepth 3 -type l` prints nothing and exits 0 (run
  2026-10-30: `rc=0`); a loop makes it print a filesystem-loop error and exit 1 (control: a
  `t/a/up -> ../` link under scratch gave `Filesystem loop back to t`, `rc=1`).

### Risk 1.4: The ratchets cannot see a more permissive oracle on input Lunar accepts
- **Impact**: a re-pin under which `luac` accepts input Lunar rejects raises
  `oracleDisagreements`, which both ratchets gate. One under which `luac` accepts input Lunar already
  accepts lowers `oracleFalseAccepts`: ungated on the corpus members (`CorpusMetrics.kt:42-48`),
  gated on the torture corpus (`TortureCorpus.kt:249-255`, BUG-409) only against an increase, so the
  decrease prints `IMPROVED` and stays green. DR-03's 5.4.8 flip is such a case.
- **Likelihood**: low per re-pin.
- **Mitigation**: repeat DR-03's differential on any future `luac.json` change.

## Design Gaps

These are named, out of scope, and need the owner; none blocks implementation.

### Gap 2.1: The hand-made files in `test/`
- **Question**: version `cases/`, `examples/`, `regression/`, `schema/`, `rocks/` and the loose
  `.lua` files, and where?
- **Resolved by**: the owner. After Phase 1 no unit test reads them.

### Gap 2.2: The C toolchain in the CI image
- **Question**: `build-essential` (`tooling/ci-image/Dockerfile:78`) and the `corpus` job's
  `cc -dumpversion` check exist for the compile Phase 2 removes, and `Dockerfile:59-62` says so.
- **Resolved by**: a follow-up image change.

### Gap 2.3: Only `linux-x86_64` is pinned
- **Question**: the oracle cannot install on macOS or arm64 hosts. The builder and CI are
  `linux-x86_64`.
- **Resolved by**: pinning more targets if another host needs the oracle.

### Gap 2.4: Text outside the repository
- `.agents/AGENTS.md` (the CLAUDE.md target) describes the exclusion and is git-ignored, so no
  phase can commit its edit. The fixture tree's `test/luacheck` symlink is unused by tests after
  Phase 1.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
