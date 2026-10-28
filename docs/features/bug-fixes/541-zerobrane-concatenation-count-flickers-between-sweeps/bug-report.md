---
id: "BUG-541"
title: "The zerobrane corpus sweep's `LuaSuspiciousConcatenation` count flickers between 19 and 17 from run to run once members are pinned to their real target"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-541: zerobrane's concatenation count flickers between corpus sweeps

Found while planning [[BUG-515]], whose fix pins each corpus member to its real `Standard/<level>`
target. Reported here without a root cause.

## 1. Reproduction

1. Check out a tree with BUG-515's fix (`corpus/CorpusTarget.kt` pinning each member's target).
2. Fetch the corpus: `python3 tooling/corpus/fetch-corpus.py` (and `fetch-luac.py`).
3. Record the corpus baselines several times on the libvirt builder:
   `test --rerun -PwithCorpus --tests '*Corpus*'`, then read the `zerobrane` member's
   `LuaSuspiciousConcatenation` count from its recorded baseline.

## 2. Expected vs actual

- **Expected:** the same count on every run with the same tree and invocation. BUG-418 holds the
  sweep to "byte-deterministic per invocation shape".
- **Actual:** four runs with BUG-515's fix read **19, 17, 19, 19**. The two-hit difference is the pair
  of `nil`-operand hits at `editor/menu_project.lua:96` and `:136` (operand
  `ide.config.default.interpreter`), present in some runs and absent in others. The other three
  members (luacheck, luarocks, penlight) recorded byte-identically across the same runs.
- **On main** (`224a2f3f`, before BUG-515's fix), three runs read 19 every time.

## 3. Context

- The only visible new ingredient is the per-member target reload: BUG-515's fix triggers a full
  stub-index rebuild (`StubIndex.forceRebuild`) twice per member.
- Unknown whether the fix introduces the nondeterminism or only exposes an existing one. The
  BUG-515 planner names the type of `ide.config.default.interpreter`, a global table assembled
  across several files, as the candidate.
- BUG-515 records the zerobrane baseline at 19, so a 17 run reads as `IMPROVED` and passes. The
  ratchet stays green, but the nondeterminism remains.
- Evidence (scratch, not durable): the BUG-515 planning runs' highlight dumps
  (`hits-fix.txt`, `hits-fix6.txt`, `hits-control.txt`) under the BUG-515 scratch directory.
