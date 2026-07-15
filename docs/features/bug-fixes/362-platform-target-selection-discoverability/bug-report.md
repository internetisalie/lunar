---
id: "BUG-362"
title: "No discoverable way to actively choose the platform target (e.g. Redis) after interpreter discovery"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-362: No discoverable way to actively choose the platform target (e.g. Redis) after interpreter discovery

## 1. Reproduction

1. Open a Lua project; let Lunar discover/register a Lua interpreter via *Settings → Languages &
   Frameworks → Lua → Toolchain* (Auto-Discover), so a runtime is present.
2. Try to set the project's **platform target** to **Redis** (so `redis.*` / `KEYS` / `ARGV` resolve
   and the correct luacheck `--std` applies).

Observed (user feedback): it is unclear how to *actively* choose the Redis platform target even
though the interpreter was discovered — the selection path is not discoverable from the settings UI.

## 2. Expected vs Actual Behavior

- **Expected**: a clear, discoverable control in the *Lua Project* settings to actively pick the
  platform target (Standard Lua, LuaJIT, Redis, Valkey, …) and its version, independent of / in
  addition to interpreter discovery. Choosing Redis should drive library resolution and the luacheck
  standard (per TARGET-02 / TARGET-04 / TARGET-05).
- **Actual**: the user could not find how to actively select the Redis target after the interpreter
  was discovered. Whether the control is missing, disabled, mislabeled, or merely hard to find is not
  yet determined.

## 3. Context / Environment

- **Confidence**: low–medium — user-reported UX; **not yet reproduced or root-caused**. Needs live
  verification in the IDE (the `verify-in-ide` VNC flow) to see the actual *Lua Project* page state.
- **Relevant files** (starting points):
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaProjectConfigurable.kt` — the
    consolidated *Lua Project* settings page (TOOLING-06), which hosts the environment selector /
    per-kind binding combos / resolved-runtime display.
  - `src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt` — persisted project
    target/environment state.
  - `src/main/kotlin/net/internetisalie/lunar/platform/target/PlatformVersionRegistry.kt` — the
    platform → version list that should back the selector.
- **Related features**: TARGET-03 (UI Contextual Versions), TOOLING-06 (Consolidated Settings UI).

## 4. Other Notes

- Likely a discoverability/labeling problem in the reworked settings tree rather than missing
  functionality (the target model and library resolution exist and are exercised by tests), but that
  must be confirmed live before deciding the fix.
- A `plan-bug` pass should VNC-verify the exact gap (control absent vs. present-but-unclear) and
  decide whether the fix is UI wording/placement, an always-visible target combo, or a hint when a
  discovered interpreter has no explicit target chosen.
