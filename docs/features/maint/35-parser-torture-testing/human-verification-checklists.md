---
id: "MAINT-35-HVC"
title: "Human Verification Checklists"
type: "qa"
parent_id: "MAINT-35"
folders:
  - "[[features/maint/35-parser-torture-testing/requirements|requirements]]"
---

# MAINT-35: Human Verification Checklists

This feature is test infrastructure with no UI, so there is nothing to drive over VNC. What does
need checking is that **the gate can actually fail** — a green ratchet is worthless if the checks
are inert, which is precisely how the `parseErrors` metric hid BUG-392 for a whole wave.

Each scenario deliberately *breaks* something and confirms the gate notices. Every one of them now
has an automated counterpart, named in the result row; the manual form stays here because the
automated form tests a component while the scenario tests the gate.

## Scenario 1: The oracle catches a false reject

| # | Step | Expected |
| :-- | :-- | :-- |
| 1.1 | Reintroduce BUG-392 (`while` → `if` in `LongStringMergingLexerAdapter`, `LuaLexer.kt:130`) | Lunar mis-parses valid luarocks files that `luac` accepts |
| 1.2 | Run `test -PwithCorpus --tests '*LuaCorpusSweepTest.testLuarocksCorpus*'` | **Fails**: `oracleDisagreements` rises above the baseline, and each file is listed as `falseReject:<path>` |
| 1.3 | Restore the fix, re-run | Ratchet green |

## Scenario 2: An oracle that cannot judge is never treated as clean

The design deliberately has **no fallback**: the binary is resolved by table lookup to exactly one
path, `PATH` is never consulted, and a missing binary throws before any file is judged.

| # | Step | Expected |
| :-- | :-- | :-- |
| 2.1 | `mv test/luac/5.1.5 /tmp/`, re-run a `LUA51` sweep | Throws naming the path *and* `tooling/corpus/fetch-luac.py`; **no metrics are produced at all** |
| 2.2 | Restore the directory | Green |
| 2.3 | Replace the binary with a script that exits 0 unconditionally | `assertDiscriminates` fails: "accepted malformed Lua … makes oracleDisagreements structurally zero" |

> 2.3 is the scenario that matters most. A gate that keeps reporting success once its oracle stops
> discriminating is worse than no gate. `requireBinary` alone cannot see this — only judging a
> known-bad input can.

## Scenario 3: The lexer invariants are live

| # | Step | Expected |
| :-- | :-- | :-- |
| 3.1 | `while` → `if` in `LongCommentMergingLexerAdapter` (`LuaLexer.kt:160`) | `unmergedTokens` > 0 |
| 3.2 | Run `LexerInvariantsTest` | **Fails** on "internal tokens escaped the merge" — *not* on the round-trip |
| 3.3 | Restore | Green |

> The round-trip half cannot be falsified this way, and that is structural rather than a gap:
> `MergingLexerAdapterBase` derives each merged token's end from the delegate's next start, so
> adapter-level defects cannot lose a character. It guards `_LuaLexer` beneath the adapters.

## Scenario 4: Version matching is real

| # | Step | Expected |
| :-- | :-- | :-- |
| 4.1 | Judge `local a = 1 // 2` at `LUA51` | Reject |
| 4.2 | Judge the identical text at `LUA54` | Accept |
| 4.3 | Confirm which binary each used | `test/luac/5.1.5/luac` and `test/luac/5.4.8/luac` — built from pinned source, never a distro package and never a bare `luac` from `PATH` |

## Scenario 5: Provisioning refuses to install what it cannot verify

| # | Step | Expected |
| :-- | :-- | :-- |
| 5.1 | Corrupt a `sha256` in `luac.json`, delete that version's directory, run `fetch-luac.py` | Non-zero exit naming both digests; **no binary and no `.luac-sha` stamp** written |
| 5.2 | On a machine with no `test/luac/` at all, run `fetch-luac.py` | All five versions build; each reports its own version |

## Sign-off

| Scenario | Result | Date | Notes |
| :-- | :-- | :-- | :-- |
| 1 — false reject caught | ☑ **Pass** | 2026-08-05 | Agent-run. `while` → `if` on `NL_BEFORE_LONGSTRING`, luarocks: `oracleDisagreements` **0 → 1**, ratchet **failed**, site named as `falseReject:src/luarocks/cmd.lua` — the exact file BUG-392 was found in. `unmergedTokens` 8 fired independently; `lexerRoundTripFailures` stayed **0**, confirming again which invariant does the work |
| 2 — non-judging oracle fails loudly | ☑ **Pass — automated** | 2026-08-05 | 2.1 = `ParseOracleTest.testMissingBinaryNamesTheFetchScript`; 2.3 = `testDiscriminationCheckPassesForAPinnedBinary` plus `CorpusSweep.run`'s `assertDiscriminates` call. The operator forms (moving the directory, substituting a stub binary) were **not** run separately |
| 3 — lexer invariants live | ☑ **Pass** | 2026-08-05 | Mutation-proved: `unmergedTokens` 1 with `LuaTokenTypes.LONGCOMMENT` listed, 0 without; round-trip green throughout, and three separate adapter mutations failed to falsify it |
| 4 — version matching real | ☑ **Pass — automated** | 2026-08-05 | `ParseOracleTest.testIntegerDivisionDiscriminatesByLevel` + `testPinnedVersionsComeFromTheManifest` |
| 5 — provisioning refuses unverified input | ☑ **Pass** | 2026-08-05 | 5.1 = `ParseOracleTest.testChecksumMismatchInstallsNothing` (drives the real script via a `file://` entry). 5.2 run on the builder with `LUNAR_LUAC_ROOT=/tmp/luac-fresh`: five builds, `luac -v` correct for each |
