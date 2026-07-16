---
id: "BUG-364"
title: "Numerous exceptions thrown during indexing and when opening tool-window panels"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-364: Numerous exceptions thrown during indexing and when opening tool-window panels

> **DIAGNOSED 2026-07-16 (VNC session, GoLand 2026.1.3, lunar 0.18.0) — this umbrella is now
> resolved into two root-caused children; fix work happens in them, not here:**
> - **[[../379-luarocks-packages-alarm-parent-disposable/bug-report|BUG-379]]** (high) — the
>   *panel-open* half: opening the **LuaRocks Packages** tool window logs a `SEVERE`
>   `IllegalArgumentException` (Alarm constructed without a parent `Disposable`) and shows a red
>   "IDE error occurred" balloon on **every** open. Full stack trace captured. The sibling
>   **LuaRocks** (dependency tree) and **Redis Functions** tool windows verified **clean**.
>   **✅ RESOLVED 2026-07-16 (commit `1b6a8ee2`, live-verified).**
> - **[[../380-rockspec-bridge-indexing-warn-flood/bug-report|BUG-380]]** (low) — the *indexing*
>   half, **reclassified**: these are **not exceptions** but a `RockspecBridge` `WARN` flood (one
>   line per rockspec — 165 in the session) on rockspec-heavy projects when no runtime resolves.
> A full-session sweep (all three implicated tool windows + full indexing) found **exactly one**
> throwable through lunar code (BUG-379) and **zero** other exceptions. The user's "numerous
> exceptions" = BUG-379's red balloon + BUG-380's Event-Log WARN noise.

## 1. Reproduction

Observed during normal use (user feedback): opening a project triggers **many exceptions during
indexing**, and **opening panels** (tool windows) throws further exceptions. Exact stack traces have
not yet been captured.

*(Now captured — see the DIAGNOSED banner above and the two child reports.)*

## 2. Expected vs Actual Behavior

- **Expected**: project indexing and opening any tool window complete cleanly with no exceptions in
  the IDE log / Event Log.
- **Actual**: a high volume of exceptions surface during indexing and on panel open.

## 3. Context / Environment

- **Confidence**: ~~low — not root-caused~~ → **DIAGNOSED** (see banner). Captured live via the
  `verify-in-ide` VNC flow on the `lunar-builder` VM: opened the `test` fixture tree (rockspec-heavy),
  ran full indexing, and opened all three implicated tool windows while diffing `idea.log`.
- **What the capture showed** (method: `grep` fresh `idea.log` for `ERROR`/`SEVERE` and any throwable
  frame through `net.internetisalie.*`, marking the log before each tool-window open):
  - **0** `ERROR`-level lines and **0** `SEVERE` lines during pure indexing; **0** throwables through
    lunar code from indexing. The "indexing exceptions" were **165 `WARN`** lines from
    `RockspecBridge` → **[[../380-rockspec-bridge-indexing-warn-flood/bug-report|BUG-380]]**.
  - Opening **LuaRocks Packages** → one `SEVERE` `IllegalArgumentException` + red error balloon,
    reproducible on every open →
    **[[../379-luarocks-packages-alarm-parent-disposable/bug-report|BUG-379]]** (stack trace captured
    there).
  - Opening **LuaRocks** (dependency tree, `LuaRocksToolWindowFactory`) and **Redis Functions**
    (`LuaRedisFunctionsToolWindowFactory`) → **clean**, no logged error.
- **Candidate areas from the original triage, now settled**: stub indexing (`lang/indexing/*`) and the
  LuaCATS file-based indices threw **nothing**. The [[bug-report|BUG-361]] `global`-mislex link did
  **not** manifest as an indexing throwable in this session (the `test` tree did not exercise it into
  an exception). Reopen that thread only if a `global`-using file later produces a captured trace.

## 4. Other Notes

- Kept as the **umbrella / diagnosis record**; the two children carry the fixes. Close this once
  BUG-379 and BUG-380 land.
- Original action ("reproduce with logging on, bucket the stack traces by root cause, then file one
  focused bug per cause") — **done**: two buckets, two focused reports.
