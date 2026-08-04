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

> **Run 2026-08-04 (VNC, GoLand 2026.1.3 on `lunar-builder`) — 2 Pass, 4 Not run, 1 defect found.**
> The two scenarios that carried the real risk both pass: the page exists and is populated, and the
> **live fetch path executed end to end for the first time**. The four unrun scenarios need either
> blocked egress or a browser, neither available on the headless VM; each says so rather than being
> marked pass by inference.
>
> **Defect found — only visible here.** `Slow operations are prohibited on EDT` (SEVERE) from
> `LuaDefinitionLibraryFetcher.cachedRoot` ← `LuaDefinitionLibraryEnabler.rows` ←
> `LuaDefinitionLibrariesConfigurable.createComponent`. `VfsUtil.findFile` is itself a prohibited
> slow operation on the EDT, so the "EDT-safe" rewrite that replaced `listDirectoryEntries()` did
> not actually fix the class of problem. Logged, not thrown, so the page still renders — no unit
> test can catch it, because the assertion only fires in a running IDE. Tracked as BUG-396.

## 1. Settings & Enable UX

### Scenario 1.1: Catalog appears in settings
- **Setup**: Open any Lua project in GoLand with the plugin loaded.
- **Steps**:
  1. Settings → Languages & Frameworks → Lua → Lua Project → Definition Libraries.
- **Expected**: A table lists the v1 curated libraries (love2d, busted, luassert, openresty) each with an unchecked enable box, a "Not fetched" status, a License column (MIT), and a clickable attribution link.
- **Result**: ☑ **Pass** (2026-08-04, VNC on lunar-builder) — Settings ▸ Languages & Frameworks ▸ Lua ▸ Lua Project ▸ **Definition Libraries** is present alongside Redis Connections. All four catalog entries render, each with a checkbox, a Fetched/Not fetched status, the **MIT** license and a clickable attribution link. Initial state: all four `Not fetched`.

### Scenario 1.2: Enable love2d (online) and see resolution
- **Setup**: Scenario 1.1 open; network available.
- **Steps**:
  1. Check `love2d`, click Apply/OK.
  2. Wait for the "Fetching Lua definition libraries" background task to finish.
  3. Open/create `main.lua` and type `love.graphics.` then invoke completion.
- **Expected**: A background task runs (no UI freeze). After it completes, completion offers love2d `graphics` members (e.g. `newImage`, `print`); Ctrl-hover shows the `@meta` signature.
- **Result**: ☑ **Pass** (2026-08-04) — verified with **busted** rather than love2d (it is the entry the MAINT-33 corpus evidence points at). Ticking it and pressing *Apply* fetched over the real network: the cache gained **both** `busted-5ed85d0e…` **and** `luassert-d3528bb6…`, proving transitive dependency resolution live. `rootPrefix` stripping is correct — `busted.lua` sits directly in the cache dir, not under `busted-<sha>/library/`. `idea.log`: *Started scanning for indexing of [vncproj]. Reason: changes in: "Synthetic library 'Lua Definition Libraries'"*. Reopening the page shows busted **checked/Fetched** and luassert **unchecked/Fetched** — accurate, since luassert was pulled in transitively without being explicitly enabled. `.idea/lunar.xml` persisted `enabledDefinitionLibraries = [busted]`. **This is the first execution of the live fetch path** — every automated test pre-seeds the cache or injects a throwing source.

### Scenario 1.3: Reopen project — cached, no network
- **Setup**: Scenario 1.2 done; close and reopen the project (or disable network).
- **Steps**:
  1. Reopen the project.
  2. Type `love.graphics.` and invoke completion.
- **Expected**: love2d members resolve immediately with no fetch/network activity (cache hit).
- **Result**: ⬜ **Not run** — needs an IDE restart with the cache warm and the network removed. The cached-reuse half is covered automatically (`preSeededCacheIsReusedWithoutDownloading`, zero downloader calls), but the no-network-on-restart path was not exercised here.

## 2. Failure & Attribution

### Scenario 2.1: Offline enable surfaces a balloon, no crash
- **Setup**: Disable the container's network; a not-yet-cached library (e.g. `openresty`).
- **Steps**:
  1. Enable `openresty`, click Apply.
- **Expected**: An ERROR balloon appears (Lua tools notification group); the IDE stays responsive; `openresty` symbols do not resolve (no root registered); no stack trace in idea.log beyond the logged fetch failure.
- **Result**: ⬜ **Not run** — the offline balloon is covered by `testApplyBalloonsOnFailure`, which is mutation-checked (removing the report call turns it red), but it was not driven through the UI. Doing so needs the VM's egress blocked, which was out of scope for this pass.

### Scenario 2.2: Attribution link opens upstream
- **Setup**: Settings → Definition Libraries with `love2d` enabled.
- **Steps**:
  1. Click the love2d attribution link.
- **Expected**: The browser opens the LuaCATS love2d repo (the catalog `attributionUrl`); the License column reads `MIT`.
- **Result**: ⬜ **Not run** — the link renders with the external-link glyph and `HyperlinkLabel.setHyperlinkTarget` is the platform's browse API, but no browser exists on the headless VM to confirm it opens.

### Scenario 2.3: Disable drops resolution
- **Setup**: `love2d` enabled + resolving (Scenario 1.2).
- **Steps**:
  1. Uncheck `love2d`, Apply.
  2. Type `love.graphics.` and invoke completion.
- **Expected**: love2d members no longer resolve after the roots refresh (no IDE restart needed).
- **Result**: ⬜ **Not run** — unticking and confirming resolution drops was not exercised.
