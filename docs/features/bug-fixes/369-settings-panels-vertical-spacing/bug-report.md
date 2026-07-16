---
id: "BUG-369"
title: "Various settings panels have inconsistent vertical spacing"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-369: Various settings panels have inconsistent vertical spacing

## 1. Reproduction

Open *Settings → Languages & Frameworks → Lua* and visit the several Lua settings/config pages
(application settings, project/toolchain pages, Redis connections, code style). Across these panels
the vertical spacing between rows/sections is inconsistent — some are tighter, some looser, so the
settings tree doesn't feel uniform.

## 2. Expected vs Actual Behavior

- **Expected**: consistent vertical rhythm across all Lua settings panels, matching the platform
  default spacing.
- **Actual**: spacing varies from panel to panel.

## 3. Context / Environment

- **Confidence**: medium — the layout heterogeneity is confirmed in code; the *specific* per-panel
  visible defects still need live characterization (a `verify-in-ide` VNC screenshot pass to record
  which panels look off and how).
- **Root cause (hypothesis)**: the settings panels are built with **different layout technologies**,
  each with its own spacing defaults:
  - **Kotlin UI DSL** (`com.intellij.ui.dsl`, automatic row spacing) — `LuaProjectConfigurable`,
    `LuaToolchainConfigurable`, `LuaRedisConnectionsConfigurable`.
  - **Legacy `FormBuilder`** with hand-tuned vertical gaps — `LuaApplicationSettingsPanel`
    (`addComponent(component, 2)`, `addComponentFillVertically(...)`).
  - Other panels (`LuaEditorOptions`, `LuaCodeStyleSettings`, `LuaRocksGeneratorPeer`) — layout not
    yet audited.
  Mixing DSL auto-spacing with `FormBuilder` manual vgaps yields different rhythm between pages.
- **Relevant files** (Lua configurables):
  - `src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettingsPanel.kt` (FormBuilder)
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaProjectConfigurable.kt` (DSL)
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaToolchainConfigurable.kt` (DSL)
  - `src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisConnectionsConfigurable.kt` (DSL)
  - `src/main/kotlin/net/internetisalie/lunar/lang/editor/LuaEditorOptionsConfigurable.kt`,
    `src/main/kotlin/net/internetisalie/lunar/lang/format/LuaCodeStyleSettings.kt` (to audit)

## 4. Other Notes

- **Fix direction**: standardize on the **Kotlin UI DSL** (`panel { }`) across the Lua settings
  panels — it is the current IntelliJ standard and enforces consistent row/section spacing — or, at a
  minimum, align the `FormBuilder` gaps to the DSL defaults. Best tackled as one "settings UI
  consistency" pass after the VNC characterization.
- Same modernize-legacy-Swing theme as the `PackageDetailPanel` cluster
  ([[bug-report|BUG-363]]/[[bug-report|BUG-365]]/[[bug-report|BUG-367]]/[[bug-report|BUG-368]]), but
  settings-scoped and tracked separately.

## Absorbed by TOOLING-08

This bug is folded into **[TOOLING-08: Lua Settings Restructure](../../tooling/08-settings-restructure/requirements.md)**
as acceptance criteria (TOOLING-08-07 / TOOLING-08-08). Planning narrowed the FormBuilder offenders to
`LuaApplicationSettingsPanel` and `LuaRocksGeneratorPeer` (migrated to the Kotlin UI DSL);
`LuaEditorOptionsConfigurable` (`BeanConfigurable`) and `LuaCodeStyleSettings` (`CustomCodeStyleSettings`)
are platform-driven with no manual layout, so they are audit-only. It will be fixed as part of that
feature's DSL standardization pass rather than as a standalone bug fix.
