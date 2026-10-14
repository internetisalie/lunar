---
id: "MAINT-37-RESULTS"
title: "Re-baseline Attribution Record"
type: "results"
parent_id: "MAINT-37"
folders:
  - "[[features/maint/37-pinned-definition-libraries/requirements|requirements]]"
---

# MAINT-37: Re-baseline Attribution Record

One section per run of the record sequence ([implementation-plan.md](implementation-plan.md) →
Phase 4, S1–S12). Every output below is pasted verbatim. `$SCR` is the scratch directory the
sequence ran in.

## Phase 4 — `busted` pins (2026-10-14)

- **Inputs**: `PAIRED` = `luacheck`, `luarocks`, `penlight`; `LIBS` = `busted` (closure `busted`,
  `luassert`).
- **Commit measured**: `ada6fc470df9672b15923af7f568bd2b54e09007`. S1, S2 and S2a ran on that commit;
  S4 onwards ran on it plus the uncommitted S3 pin edit to `tooling/corpus/corpus.json` (and, in the
  working tree, this phase's Python test and documentation edits, which no sweep reads). All builder
  runs went to the libvirt builder.
- **Withheld members**: none. Every pair's classifier exited `0`, so every pin is kept.
- **`TRIAGE-CONTRACT` inspection ids with a positive delta**: none (no `TRIAGE-*` row at all), so no
  BUG is filed.
- **S1**: the record run ended `BUILD SUCCESSFUL in 5m 19s`, the copy-back exited `0`, and
  `git status --porcelain -- src/test/resources/corpus/` printed nothing.
- **S7**: `git diff --exit-code` on the two torture files exited `0`.

### S2b — offsets, old side (DR-08)

```
$ python3 tooling/corpus/reconcile_baseline.py --corpus luacheck --offset --old $SCR/before/luacheck.tally --new $SCR/old/luacheck.baseline
## luacheck — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0

$ python3 tooling/corpus/reconcile_baseline.py --corpus luarocks --offset --old $SCR/before/luarocks.tally --new $SCR/old/luarocks.baseline
## luarocks — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0

$ python3 tooling/corpus/reconcile_baseline.py --corpus zerobrane --offset --old $SCR/before/zerobrane.tally --new $SCR/old/zerobrane.baseline
## zerobrane — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0

$ python3 tooling/corpus/reconcile_baseline.py --corpus penlight --offset --old $SCR/before/penlight.tally --new $SCR/old/penlight.baseline
## penlight — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0
```

### S4 — classifier

```
$ python3 tooling/corpus/attribute_delta.py --corpus luacheck --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned
## luacheck — libraries: busted=5ed85d0e, luassert=d3528bb6
| key | bare | pinned | delta | class | evidence |
| :-- | --: | --: | --: | :-- | :-- |
| inspection.LuaUndeclaredVariable | 608 | 32 | -576 | EXPLAINED | it:-469, describe:-96, pending:-3, before_each:-2, setup:-2 |
exit=0

$ python3 tooling/corpus/attribute_delta.py --corpus luarocks --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned
## luarocks — libraries: busted=5ed85d0e, luassert=d3528bb6
| key | bare | pinned | delta | class | evidence |
| :-- | --: | --: | --: | :-- | :-- |
| inspection.LuaUndeclaredVariable | 955 | 15 | -940 | EXPLAINED | it:-449, finally:-186, describe:-166, before_each:-44, after_each:-41 |
exit=0

$ python3 tooling/corpus/attribute_delta.py --corpus penlight --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned
## penlight — libraries: busted=5ed85d0e, luassert=d3528bb6
| key | bare | pinned | delta | class | evidence |
| :-- | --: | --: | --: | :-- | :-- |
| inspection.LuaUndeclaredVariable | 359 | 0 | -359 | EXPLAINED | it:-235, describe:-106, pending:-9, before_each:-6, after_each:-2 |
exit=0
```

### S8 — reconciliation and offsets, new side

#### luacheck

```
$ python3 tooling/corpus/reconcile_baseline.py --corpus luacheck --old $SCR/old/luacheck.baseline --new src/test/resources/corpus/luacheck.baseline --before $SCR/before --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned
## luacheck — shape residual
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |

## luacheck — refresh effect
| key | before | bare | effect | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0

$ python3 tooling/corpus/reconcile_baseline.py --corpus luacheck --offset --old $SCR/attribution/pinned/luacheck.tally --new src/test/resources/corpus/luacheck.baseline
## luacheck — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0
```

#### luarocks

```
$ python3 tooling/corpus/reconcile_baseline.py --corpus luarocks --old $SCR/old/luarocks.baseline --new src/test/resources/corpus/luarocks.baseline --before $SCR/before --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned
## luarocks — shape residual
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |

## luarocks — refresh effect
| key | before | bare | effect | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0

$ python3 tooling/corpus/reconcile_baseline.py --corpus luarocks --offset --old $SCR/attribution/pinned/luarocks.tally --new src/test/resources/corpus/luarocks.baseline
## luarocks — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0
```

#### zerobrane (not in `PAIRED`)

```
$ python3 tooling/corpus/reconcile_baseline.py --corpus zerobrane --old $SCR/old/zerobrane.baseline --new src/test/resources/corpus/zerobrane.baseline
## zerobrane — shape residual
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0

$ python3 tooling/corpus/reconcile_baseline.py --corpus zerobrane --offset --old $SCR/before/zerobrane.tally --new src/test/resources/corpus/zerobrane.baseline
## zerobrane — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0
```

#### penlight

```
$ python3 tooling/corpus/reconcile_baseline.py --corpus penlight --old $SCR/old/penlight.baseline --new src/test/resources/corpus/penlight.baseline --before $SCR/before --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned
## penlight — shape residual
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |

## penlight — refresh effect
| key | before | bare | effect | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0

$ python3 tooling/corpus/reconcile_baseline.py --corpus penlight --offset --old $SCR/attribution/pinned/penlight.tally --new src/test/resources/corpus/penlight.baseline
## penlight — shape offset
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| — | 0 | 0 | 0 | no |
exit=0
```
