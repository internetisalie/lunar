---
id: "BUG-370"
title: "Provision dialog tool checkboxes show raw kind ids instead of display names"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-370: Provision dialog tool checkboxes show raw kind ids instead of display names

## 1. Reproduction

1. Open *Tools → Lua Toolchain → Provision Lua Toolchain…* (or the **Provision…** toolbar button
   on the Toolchain settings page).
2. Look at the dev-tool checkboxes below the LuaRocks row.

The checkboxes are labeled with the raw kind ids from the feed — "luacheck", "stylua", "busted",
"luacov", "lua-language-server" — rather than the human-readable display names used everywhere
else ("luacheck", "StyLua", "Busted", "LuaCov", …).

## 2. Expected vs Actual Behavior

- **Expected**: tool labels match the rest of the UI — the inventory table's Kind column and the
  Lua Project page resolve display names through `LuaToolKindRegistry` (e.g.
  `kindDisplayName(...)` in `LuaToolchainInventoryTable.kt:144-145`), so the dialog should show
  "StyLua", "Busted", "LuaCov".
- **Actual**: raw lowercase kind ids are shown, purely cosmetic but inconsistent.

## 3. Context / Environment

- **Confidence**: high — root-caused in code.
- **Root cause**: `LuaProvisionDialog` builds each checkbox directly from the kind id string:
  `src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaProvisionDialog.kt:43` —
  `LuaToolCatalog.TOOL_KINDS.associateWith { JBCheckBox(it, false) }` (`it` is the id, e.g.
  `"stylua"`). The id list comes from
  `src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaToolCatalog.kt:20`.
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaProvisionDialog.kt` (line 43)
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/registry/LuaToolKindRegistry.kt`
    (display names)
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaToolchainInventoryTable.kt:144-145`
    (the pattern to copy)

## 4. Other Notes

- **Fix direction**: resolve the label via
  `LuaToolKindRegistry.findById(kindId)?.displayName ?: kindId` when constructing the checkboxes
  (keep the id as the map key — only the visible text changes).
- Caveat: `lua-language-server` has **no** registry entry at all, so its fallback stays the raw
  id until [[bug-report|BUG-373]] is fixed — these two pair naturally.
- Part of the broader toolchain terminology/labeling cleanup tracked as [[bug-report|BUG-378]].
