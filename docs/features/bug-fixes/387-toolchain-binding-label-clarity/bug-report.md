---
id: "BUG-387"
title: "Toolchain binding combos render an unclear \"Inherit (none)\" label (chore)"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-387: Unclear "Inherit (none)" toolchain binding label (chore)

**Chore** (UX wording), not a defect — no behaviour change, no resolution-semantics change.

## Problem

The tool-binding combos (per kind: Lua / LuaJIT / Tarantool / LuaRocks / luacheck / StyLua / Busted)
render the un-bound option as **`Inherit (none)`**, which is unclear:

- **App-level *Global Default Bindings*** (`toolchain/ui/LuaToolchainConfigurable.kt:137`): the label
  is a fixed literal `"Inherit (none)"`. At the global level "**Inherit**" is misleading — the global
  default *is* the top explicit binding tier, so there is nothing above it to inherit from; the
  option just means "no app-wide default set for this kind."
- **Project-level *Toolchain Bindings*** (`toolchain/ui/LuaProjectConfigurable.kt:388` `inheritLabel`):
  the computed `Inherit (…)` form is fine when it shows a resolved value (`Inherit (/usr/bin/lua5.4 —
  5.4.7)` or `Inherit (app default: …)`), but the null case falls back to the bare `"none"`, giving
  `Inherit (none)` — ambiguous between "inherits to none" and "nothing resolved."

Resolution precedence is unaffected (`LuaToolResolver`: active env → project binding → global binding
→ inventory fallback). This is label text only.

## Fix

| Site | Before | After |
| :--- | :--- | :--- |
| `LuaToolchainConfigurable.kt:137` (global) | `"Inherit (none)"` | `"No default"` |
| `LuaProjectConfigurable.kt:393` (project `inheritLabel` null case) | `"none"` → `Inherit (none)` | `"nothing resolved"` → `Inherit (nothing resolved)` |

No test asserts on the combo's `"Inherit (none)"` (the `Inherit (app default: …)` / `Inherit
(luarocks.org)` assertions in `LuaProjectConfigurableTest` are the separate luacheck-args / rocks-URL
placeholder fields, unchanged).

## Resolution (2026-07-18)

Applied both label changes; full-suite gate green (no regression); no test edits needed.
