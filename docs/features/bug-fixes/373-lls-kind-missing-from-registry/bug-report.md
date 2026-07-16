---
id: "BUG-373"
title: "lua-language-server is provisionable but missing from LuaToolKindRegistry"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-373: lua-language-server is provisionable but missing from LuaToolKindRegistry

## 1. Reproduction

1. *Tools → Lua Toolchain → Provision Lua Toolchain…*, tick **lua-language-server**, provision.
2. Open *Settings → Languages & Frameworks → Lua → Toolchain* and inspect the inventory row for
   the provisioned tool.
3. Open the *Lua* project page and look for a binding row for the language-server kind.

## 2. Expected vs Actual Behavior

- **Expected**: a provisionable kind is a first-class kind — display name in the inventory Kind
  column, a binding row on the Lua Project page, and health-monitor/banner classification like
  every other kind.
- **Actual**: `lua-language-server` is *provisionable* (it is in the bundled feed and in the
  provision dialog's tool list) but has **no `LuaToolKind` descriptor**:
  - The inventory Kind/Name cells fall back to the raw id
    (`LuaToolKindRegistry.findById(tool.kindId)?.displayName ?: tool.kindId`,
    `LuaToolchainInventoryTable.kt:144-145`).
  - No per-kind binding row appears on the Lua Project page (rows are driven by the registry's
    kind list).
  - The health monitor / banner logic cannot classify it (no probe spec, no capabilities).
  It only enters the system at all because provisioning registers the produced binary directly
  via `registry.registerProvisioned(...)` — so it exists as an inventory row but is second-class
  everywhere downstream.

## 3. Context / Environment

- **Confidence**: high — root-caused in code.
- **Root cause**: the kind is absent from `LuaToolKindRegistry.BUILT_IN`
  (`src/main/kotlin/net/internetisalie/lunar/toolchain/registry/LuaToolKindRegistry.kt:16-153` —
  exactly 10 kinds: `lua`, `luajit`, `tarantool`, `luarocks`, `luacheck`, `stylua`, `luacov`,
  `busted`, `redis-server`, `valkey-server`; no `lua-language-server`), while it **is** present in:
  - the bundled feed `src/main/resources/toolchain/lunar-toolchain-feed.json` (kind entry at
    line 771, release binaries for 3.18.2);
  - the provision dialog's tool list `LuaToolCatalog.TOOL_KINDS`
    (`src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaToolCatalog.kt:20`).
  Registration path: `RegistryProvisionResultSink.register`
  (`src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaProvisionResultSink.kt:39-49`,
  `registerProvisioned` call at line 43) creates the `LuaRegisteredTool` with `kindId =
  "lua-language-server"` regardless of registry membership.

## 4. Other Notes

- **Fix direction**: add a `LuaToolKind` entry (id `lua-language-server`, displayName
  "Lua Language Server", binaryNames `lua-language-server`, `--version` probe, an appropriate
  capability) — TOOLING-01-14 documents that adding a kind is data-only.
- **Requirements drift**: TOOLING-01-02 in
  `docs/features/tooling/01-toolchain-model/requirements.md:72` still says the registry "ships
  **8 built-in kinds**", but the list has grown to 10 (REDIS-01 added `redis-server` /
  `valkey-server`). The hardcoded count drifts with every addition — reword to not embed a count,
  or update it alongside this fix.
- The raw-id label this causes in the provision dialog itself is [[bug-report|BUG-370]].
