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
need human eyes is that **the gate can actually fail** — a green ratchet is worthless if the checks
are inert, which is precisely how the `parseErrors` metric hid BUG-392 for a whole wave.

Each scenario deliberately *breaks* something and confirms the gate notices.

## Scenario 1: The oracle catches a false reject

| # | Step | Expected |
| :-- | :-- | :-- |
| 1.1 | Add a corpus fixture containing valid Lua that Lunar mis-parses (revert BUG-392's lexer fix locally to manufacture one) | `oracleDisagreements` rises; the path appears as `falseReject:<path>` |
| 1.2 | Run the ratchet without re-recording | **Fails**, naming the file |
| 1.3 | Restore the fix | Ratchet green again |

## Scenario 2: An unavailable oracle does not read as clean

| # | Step | Expected |
| :-- | :-- | :-- |
| 2.1 | Temporarily rename `luac5.1` out of `PATH`, re-run the sweep | Reason printed; `oracleDisagreements` **absent** from the rendered baseline |
| 2.2 | Compare that run against the committed baseline (which has a number) | **Fails** with `oracle unavailable — baseline recorded <n>`, not "no regression" |
| 2.3 | Restore `luac5.1` | Ratchet green |

> 2.2 is the scenario that matters most. A gate that silently disables itself when its oracle
> vanishes is worse than no gate, because it reports success.

## Scenario 3: The round-trip invariant is live

| # | Step | Expected |
| :-- | :-- | :-- |
| 3.1 | Locally break `LongStringMergingLexerAdapter` so a long-string run loses a token | `lexerRoundTripFailures` > 0 |
| 3.2 | Run the ratchet | **Fails** |
| 3.3 | Restore | Green |

## Scenario 4: Version matching is real

| # | Step | Expected |
| :-- | :-- | :-- |
| 4.1 | Judge `local a = 1 // 2` at `LUA51` | Reject |
| 4.2 | Judge the identical text at `LUA54` | Accept |
| 4.3 | Confirm which binary each used | `luac5.1` and `luac5.4` respectively — never a bare `luac` |

## Sign-off

| Scenario | Result | Date | Notes |
| :-- | :-- | :-- | :-- |
| 1 — false reject caught | | | |
| 2 — unavailable oracle fails loudly | | | |
| 3 — round-trip live | | | |
| 4 — version matching real | | | |
