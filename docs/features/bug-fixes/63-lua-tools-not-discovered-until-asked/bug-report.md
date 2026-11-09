---
id: "BUG-63"
title: "Lua tools on PATH are not found until the user clicks Discover"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-63: Lua tools on PATH are not found until the user clicks Discover

## 1. Reproduction
Start a fresh IDE sandbox with `lua` and `luarocks` on `PATH` and open a Lua project (seen on the
builder, 2026-11-09). `idea.log` shows `[TOOLCHAIN-DIAG] resolve kind=lua -> none` and
`kind=luarocks -> none`.

## 2. Expected vs Actual Behavior
- **Expected**: tools already installed are found without visiting settings.
- **Actual**: nothing resolves. The rockspec bridge, the packages browser, run configurations and
  the test runner all behave as if no Lua were installed, until the user opens Settings → Lua →
  Toolchain and clicks Discover.

## 3. Root Cause
`LuaToolchainRegistry.autoDiscover()` has a single caller, the Discover action of
`LuaToolchainInventoryTable`. TOOLING-01 specified discovery as an operation, never when it runs on
its own.

## 4. Fix
`LuaFirstRunDiscovery`, a `postStartupActivity`, runs `autoDiscover()` off the EDT when a project
opens and the registry is empty. It runs once per installation, recorded in `PropertiesComponent`,
so a user who later removes every tool is not overruled. It then reports what it registered in a
*Lua tools found on PATH* notification with a *Review toolchain* action. It is skipped in unit-test
mode, like `LuaToolHealthStartup` (BUG-422), because the registry is shared by the whole suite.

## 5. Test
`LuaFirstRunDiscoveryTest` checks three cases:
- an empty registry is discovered once and reports what was registered;
- a registry with tools is left alone;
- after the first run, an emptied registry is not rediscovered. This case fails when the done
  flag is not recorded.
