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

## 1. Reproduction

Observed during normal use (user feedback): opening a project triggers **many exceptions during
indexing**, and **opening panels** (tool windows) throws further exceptions. Exact stack traces have
not yet been captured.

## 2. Expected vs Actual Behavior

- **Expected**: project indexing and opening any tool window complete cleanly with no exceptions in
  the IDE log / Event Log.
- **Actual**: a high volume of exceptions surface during indexing and on panel open.

## 3. Context / Environment

- **Confidence**: low — **not root-caused**; no stack traces captured yet. This report is a
  placeholder to drive diagnostics, not a diagnosis.
- **Needed first — capture the exceptions**:
  - Sandbox: `build/idea-sandbox/GO-*/log/idea.log`.
  - Container: `docker exec lunar-ide …` / the VNC `verify-in-ide` flow, then read `idea.log`.
  - Each distinct throwable → its own `plan-bug` scope.
- **Candidate areas to check once traces exist**:
  - Stub indexing (`lang/indexing/*`, e.g. `LuaGlobalDeclarationIndex`, the LuaCATS file-based
    indices) — indexing-time throwables often originate here.
  - Tool-window factories that do work on open: `LuaRocksToolWindowFactory` /
    `LuaRocksPackageBrowserToolWindowFactory` (rocks), `LuaRedisFunctionsToolWindowFactory` (redis).
  - **Possible link to [[bug-report|BUG-361]]**: `global` mis-lexing produces `ERROR_ELEMENT`s /
    broken PSI in any file using `global` as an identifier, which can throw during indexing —
    worth checking whether the indexing exceptions correlate with such files.

## 4. Other Notes

- High priority because indexing/PSI exceptions can degrade resolution, completion, and inspections
  silently.
- Action: reproduce with logging on, bucket the stack traces by root cause, then file one focused
  bug per cause (this umbrella report tracks the effort).
