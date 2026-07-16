---
id: TARGET-08-CHECKLIST
title: "Verification Checklists"
type: qa
parent_id: TARGET-08
folders:
  - "[[features/target/08-on-demand-definition-libraries/requirements|requirements]]"
---

# Verification Checklists: TARGET-08 — On-demand LuaLS / LuaCATS Definition Libraries

Manual, human-run scenarios (VNC / GoLand) confirming the real-flow DoD. Run against the containerized GoLand per the `verify-in-ide` skill, with a network-enabled session for the fetch scenarios.

## 1. Settings & Enable UX

### Scenario 1.1: Catalog appears in settings
- **Setup**: Open any Lua project in GoLand with the plugin loaded.
- **Steps**:
  1. Settings → Languages & Frameworks → Lua → Lua Project → Definition Libraries.
- **Expected**: A table lists the v1 curated libraries (love2d, busted, luassert, openresty) each with an unchecked enable box, a "Not fetched" status, a License column (MIT), and a clickable attribution link.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Enable love2d (online) and see resolution
- **Setup**: Scenario 1.1 open; network available.
- **Steps**:
  1. Check `love2d`, click Apply/OK.
  2. Wait for the "Fetching Lua definition libraries" background task to finish.
  3. Open/create `main.lua` and type `love.graphics.` then invoke completion.
- **Expected**: A background task runs (no UI freeze). After it completes, completion offers love2d `graphics` members (e.g. `newImage`, `print`); Ctrl-hover shows the `@meta` signature.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Reopen project — cached, no network
- **Setup**: Scenario 1.2 done; close and reopen the project (or disable network).
- **Steps**:
  1. Reopen the project.
  2. Type `love.graphics.` and invoke completion.
- **Expected**: love2d members resolve immediately with no fetch/network activity (cache hit).
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Failure & Attribution

### Scenario 2.1: Offline enable surfaces a balloon, no crash
- **Setup**: Disable the container's network; a not-yet-cached library (e.g. `openresty`).
- **Steps**:
  1. Enable `openresty`, click Apply.
- **Expected**: An ERROR balloon appears (Lua tools notification group); the IDE stays responsive; `openresty` symbols do not resolve (no root registered); no stack trace in idea.log beyond the logged fetch failure.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Attribution link opens upstream
- **Setup**: Settings → Definition Libraries with `love2d` enabled.
- **Steps**:
  1. Click the love2d attribution link.
- **Expected**: The browser opens the LuaCATS love2d repo (the catalog `attributionUrl`); the License column reads `MIT`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Disable drops resolution
- **Setup**: `love2d` enabled + resolving (Scenario 1.2).
- **Steps**:
  1. Uncheck `love2d`, Apply.
  2. Type `love.graphics.` and invoke completion.
- **Expected**: love2d members no longer resolve after the roots refresh (no IDE restart needed).
- **Result**: ⬜ Pass / ⬜ Fail
