---
id: "MAINT-62"
title: "62: Reach Cost of Cold Reads"
type: "feature"
status: "todo"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-62: Reach Cost of Cold Reads

## Overview
A cold read is slow because it builds snapshots of files it reaches, none of which alone takes 250 ms. Filed by MAINT-60 Phase 3 from the sweeps' `ranked.tsv`; MAINT-60-02 requires every pass that
missed a 250 ms bound to be named in the table of its cause item. v0.21.21 is held on this item
(`release HOLD` in MAINT-60's `verdict.txt` until no pass misses). Parent:
[[features/maint/requirements|MAINT]]. Measurement feature: [[MAINT-60]].

## Measured Cause
DR-06 (the cold read builds every snapshot the file reaches that is not yet built) and DR-08 (per-build pairs, passes and time).

## Scope
Holds: Every cold pass whose own build is under 250 ms and whose dominant dependency's own build is also under 250 ms: the cost is the sum of many builds.

Direction (not designed): Build fewer or cheaper dependency snapshots on a cold read (for instance a dependency's snapshot without the full checker), without changing findings.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-62-01 | **Every listed pass under 250 ms** | M | Not Implemented | Every pass in the table below clears its bound, and its row is removed from `src/test/resources/snapshot-cost/exceptions.tsv`. |
| MAINT-62-02 | **Findings unchanged or recorded** | M | Not Implemented | The MAINT-54 audit's `luacheck` member, the MAINT-55 conformance results and the sweep digests are identical before and after the change, or each difference is recorded as a correction (MAINT-60-03). |

## Files over target

20 files, 20 passes; slowest measured restyhttp/lib/resty/http.lua cold-250 1222 ms. Generated from the union of the `ranked.tsv` of three MAINT-60 Phase 3 sweeps on the libvirt
builder, run at commit `9fb91435` (the engine and sweep code the sweeps measured). A pass is in the
table when it missed in at least one run. *Value* is the largest measured time across the runs that
missed, or `timeout`, `not-run` or `unmeasured` when a run did not complete the pass. *Item* is the
item most of those runs filed the pass under (design 3.3 rule 4 reads a dependency's time in the
run being judged, so a pass near 250 ms moves between items); on a tie, the second run's. *Runs
missed* is how many of the 3 runs the pass missed in.

| Member | File | Bound | Value | Runs missed | Cause | Dependency | Dependency ms |
|---|---|---|---|---|---|---|---|
| apisix | `apisix/core/config_etcd.lua` | cold-250 | 1117 ms | 3/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 214 |
| differ | `lua/differ/git/editor.lua` | cold-250 | 334 ms | 1/3 | reach | `definitions:neovim/vim/_meta/vimfn.gen.lua` | 102 |
| differ | `lua/differ/git/staging/frozen.lua` | cold-250 | 574 ms | 3/3 | reach | `differ/lua/differ/model/stage.lua` | 134 |
| differ | `lua/differ/git/watch.lua` | cold-250 | 301 ms | 1/3 | reach | `definitions:neovim/vim/_meta/vimfn.gen.lua` | 95 |
| differ | `lua/differ/merge/deleted.lua` | cold-250 | 327 ms | 1/3 | reach | `definitions:neovim/vim/_meta/vimfn.gen.lua` | 89 |
| kong | `kong/clustering/data_plane.lua` | cold-250 | 408 ms | 1/3 | reach | `/usr/local/share/lua/5.1/pl/utils.lua` | 98 |
| kong | `kong/clustering/init.lua` | cold-250 | 404 ms | 2/3 | reach | `/usr/local/share/lua/5.1/pl/utils.lua` | 100 |
| kong | `kong/plugins/jwt/handler.lua` | cold-250 | 341 ms | 2/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 225 |
| kristal | `src/engine/game/battle/arena.lua` | cold-250 | 515 ms | 3/3 | reach | `definitions:love2d/love/graphics.lua` | 223 |
| lde | `packages/lde-core/src/compiler.lua` | cold-250 | 349 ms | 3/3 | reach | `/usr/local/share/lua/5.1/pl/utils.lua` | 53 |
| lnko | `lnko/cli.lua` | cold-250 | 346 ms | 3/3 | reach | `/src/lnko/init.lua` | 39 |
| opensmce | `src/GameBase.lua` | cold-250 | 565 ms | 3/3 | reach | `/src/src/UI/Widget.lua` | 223 |
| pokisample | `game/modules/bootstrap/entry.lua` | cold-250 | 287 ms | 1/3 | reach | `/src/game/modules/profile/progress.lua` | 25 |
| prism | `engine/algorithms/astar/astar.lua` | cold-250 | 464 ms | 3/3 | reach | `definitions:love2d/love.lua` | 71 |
| prism | `spectrum/camera.lua` | cold-250 | 309 ms | 2/3 | reach | `definitions:love2d/love/graphics.lua` | 198 |
| restyhttp | `lib/resty/http.lua` | cold-250 | 1222 ms | 3/3 | reach | `definitions:openresty/ngx.lua` | 216 |
| tntcrud | `crud/atomic_batch.lua` | cold-250 | 592 ms | 3/3 | reach | `/src/crud/common/utils.lua` | 171 |
| tntcrud | `crud/borders.lua` | cold-250 | 334 ms | 3/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 228 |
| tntmetrics | `metrics/api.lua` | cold-250 | 564 ms | 3/3 | reach | `/src/metrics/collectors/shared.lua` | 48 |
| tntmetrics | `metrics/plugins/json.lua` | cold-250 | 269 ms | 1/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 231 |
