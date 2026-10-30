---
id: "MAINT-49"
title: "49: Fixture Tests Run in CI"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-49: Fixture Tests Run in CI

## The gap

CI runs the unit suite with `-PexcludeExternalFixtureTests`, which skips four classes because they
read the out-of-repo `test/` tree (`build.gradle.kts`, the `test` task filter):

| Class | Reads |
|---|---|
| `LuaRecursiveReferenceTest` | `test/luacheck/src/luacheck/parser.lua`, through a `test/luacheck` symlink to a checkout outside the tree |
| `LuaDescriptionIndexTest` | `testIndexRealWorldProjectAndMeasureSize` walks all of `test/` |
| `ParseOracleTest` | `test/luac/<version>/luac`, compiled by `tooling/corpus/fetch-luac.py` |
| `LuaTypeGoldenMemberFilesTest` | the pinned corpus under `test/corpus/` |

So CI and the builder run different test sets, and a green CI build says nothing about these four.
[[MAINT-48]] stores CI's results as the per-commit baseline, and an excluded set makes every
comparison against a builder run carry an exception.

Most of what they read is already reproducible. The corpus job provisions it with
`fetch-corpus.py`, `fetch-luac.py` and `fetch-torture.py`, each pinned by SHA. The pinned corpus
already contains `luacheck/src/luacheck/parser.lua`. Only `luac` is expensive: compiling five Lua
releases adds about two minutes to a push, which is why MAINT-35 excluded `ParseOracleTest`.

## Scope

- **Read the pinned corpus.** Point `LuaRecursiveReferenceTest` at the corpus copy of luacheck,
  and have `testIndexRealWorldProjectAndMeasureSize` walk a pinned corpus member rather than the
  whole `test/` tree.
- **Download `luac`.** `fetch-luac.py` downloads the [[TOOLING-09]] binaries and verifies them by
  sha256 instead of compiling. The oracle's 5.5 pin moves to the release TOOLING-09 publishes.
- **Provision in the `build` job.** The `build` job runs the fetchers the corpus job already runs,
  on both CI platforms.
- **Drop the exclusion.** Remove `-PexcludeExternalFixtureTests` from both workflows and the
  filter from `build.gradle.kts`.

## Not established

- **Fetch cost.** The corpus download time on a cold `lunar-ci` VM, and whether `build` should
  fetch only the members these tests read.
- **The hand-made files in `test/`.** `cases/`, `examples/`, `regression/`, `schema/`, `rocks/`
  and the loose `.lua` files are in no repository. No unit test reads them except through the
  whole-tree walk; they serve as the `runIde` sandbox project. Whether to version them, and where,
  is a separate decision.
- **Ordering.** Repointing the two tests needs nothing from TOOLING-09 and can ship first.
