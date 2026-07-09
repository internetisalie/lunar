---
id: "TOOLING-06-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TOOLING-06"
folders:
  - "[[features/tooling/06-settings-ui/requirements|requirements]]"
---

# TOOLING-06: Risks & Gaps

## Phase 2 — project rocks-server-URL override routing (design §2.7 drift, resolved)

**Design §2.7** routes the *Lua Project* rocks-server-URL override to the project-scoped
`LuaToolchainProjectSettings.setKindOption(LUAROCKS_SERVER_URL, …)` kind option.

**Grep of the live consumer** shows this is a write nobody reads:

- The only reader of a *project-level* rocks server override is
  `net.internetisalie.lunar.rocks.LuaRocksEnvironment.resolveServer(project)`
  (`rocks/LuaRocksEnvironment.kt:37`), which reads
  `LuaProjectSettings.getInstance(project).state.rocksServerUrl`.
- `LuaToolchainProjectSettings.effectiveKindOption(key)` (the only reader of the project
  `kindOptions` map) is called **only** for `LUACHECK_ARGUMENTS`
  (`analysis/luacheck/LuaCheckCommandLine.kt:32`). Nothing reads the project
  `kindOptions[LUAROCKS_SERVER_URL]`.

**Decision (per the task's "write to whatever the live consumer reads" rule):** the Phase 2
project page routes the rocks-URL override to `LuaProjectSettings.state.rocksServerUrl` and
fires `LuaSettingsChangedListener.TOPIC` once (same channel as source-path / underscore), which
is exactly what `LuaRocksEnvironment.resolveServer` reads. This is **not** an ABORT_REPLAN: the
mismatch is resolvable by targeting the live-consumer field; the design's `setKindOption`
routing would have written to dead state. The *project luacheck-args* override still routes to
`LuaToolchainProjectSettings.setKindOption(LUACHECK_ARGUMENTS, …)` per §2.7, because its live
consumer (`LuaCheckCommandLine`) reads `effectiveKindOption`.

**Follow-up (out of Phase 2 scope):** if a later feature wants the project rocks override to
live on the toolchain kindOption (unifying with the app default via `effectiveKindOption`),
`LuaRocksEnvironment.resolveServer` must be re-pointed at
`LuaToolchainProjectSettings.effectiveKindOption(LUAROCKS_SERVER_URL)` and
`LuaProjectSettings.state.rocksServerUrl` retired. That is a consumer cutover, not a UI change.
