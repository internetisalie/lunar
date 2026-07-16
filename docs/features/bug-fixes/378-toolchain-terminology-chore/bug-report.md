---
id: "BUG-378"
title: "Terminology unification pass across TOOLING/ROCKS user-facing strings (chore)"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-378: Terminology unification pass across TOOLING/ROCKS user-facing strings (chore)

This is a **chore** (naming/UX consistency pass), not a defect — no behavior changes.

## 1. Reproduction

Walk the toolchain/rocks UI surface end to end: *Settings → Lua → Toolchain* and *Lua Project*,
*Tools → Lua Toolchain*, the status-bar widget, a Lua run configuration, the New Project wizard,
and the LuaRocks tool windows. Five overlapping nouns name (roughly) the same machinery:

| Term | Where it appears (verified) |
|------|------------------------------|
| **toolchain** | Settings page `displayName = "Toolchain"` (`toolchain/ui/LuaToolchainConfigurable.kt:26`, `plugin.xml:568`); Tools-menu group `text="Lua Toolchain"` (`plugin.xml:794`); dialog title "Provision Lua Toolchain" (`provision/LuaProvisionDialog.kt:49`); confirm dialogs "Recreate/Remove Lua Toolchain" (`provision/LuaToolchainActions.kt:58,89`) |
| **environment** | Provision dialog browse title "Environment Directory" (`LuaProvisionDialog.kt:101`); status-bar widget "No Lua env" / "Add environment…" / "Lua Environment" (`ui/LuaEnvStatusBarWidget.kt:113-114,82`; factory display name `ui/LuaEnvStatusBarWidgetFactory.kt:11`); lifecycle actions "Recreate Environment" / "Remove Environment" (`LuaToolchainActions.kt:45,75`) — note the same actions' *confirm dialogs* say "Toolchain" (see row above). **Collides** with the run-config env-vars label "Environment" (`run/LuaRunConfiguration.kt:329`) |
| **runtime** | "Resolved Runtime" group + "Runtime:" row on the Lua Project page (`ui/LuaProjectConfigurable.kt:72-73`); "Runtime:" row in the provision dialog (`LuaProvisionDialog.kt:160`); batch dialog "Runtime" column (`provision/LuaBatchProvisionDialog.kt:76`) |
| **interpreter** | Run config editor label "Interpreter" (`run/LuaRunConfiguration.kt:325`) + "Interpreter arguments" (line 330); test run config "Interpreter" (`run/test/LuaTestRunConfiguration.kt:294`) and validation "Interpreter is not defined" (line 259); New Project wizard "Interpreter:" (`rocks/init/LuaRocksGeneratorPeer.kt:91`); "Unknown Interpreter" renderer text (`toolchain/ui/LuaRuntimeComboBox.kt:177` — the class itself is named *Runtime*ComboBox). Legacy label TOOLING-05 never renamed |
| **tool** | Inventory table/page strings ("No tools registered…", "Select Lua Tool Binary", `ui/LuaToolchainInventoryTable.kt:35,78`); health/diagnostics ("Toolchain diagnostics…", `health/LuaToolchainDiagnosticsAction.kt:47`) |

Plus the ROCKS split: "**package**" in browser strings ("Enter a package name to search.",
"No packages found…", "N package(s) found.", "(no package selected)" —
`rocks/browser/LuaRocksPackageBrowserToolWindowFactory.kt:122,142,152`,
`rocks/browser/PackageDetailPanel.kt:155`) vs "**rock**" in actions and library names
("Publish Rock to LuaRocks…", `rocks/publish/PublishRockAction.kt:28`; "Run Test Matrix" rows).

## 2. Expected vs Actual Behavior

- **Expected**: one coherent vocabulary so a user can form a mental model — the same thing is
  called the same word on every surface, and "Environment" never means two different things in
  one dialog.
- **Actual**: five interchangeable nouns across settings, menus, dialogs, widgets, and run
  configs; the lifecycle actions even mix two of them between the menu item and its own
  confirmation dialog.

## 3. Context / Environment

- **Confidence**: high — every string above verified in code at the cited locations (2026-07-16).
- This is user-reported friction ("what is the difference between my toolchain, my environment,
  and my runtime?"), not a functional bug.

## 4. Other Notes

- **Proposed canonical vocabulary** (starting point for the chore, to be ratified):
  - **runtime** — the executable binary kind (`lua`, `luajit`, `tarantool`) and its
    version/level. Replaces "interpreter" everywhere user-visible.
  - **environment** — a provisioned set (root dir + runtime + tools), the TOOLING-02 record.
  - **toolchain** — the whole facility (settings page, Tools-menu group).
  - **package** — in the browser UI; "Rock"/"LuaRocks" only in proper nouns (action names that
    reference luarocks.org concepts, e.g. "Publish Rock to LuaRocks…" may stay).
  - Rename the run-config env-vars label collision away (platform-standard "Environment
    variables" is the conventional fix for `LuaRunConfiguration.kt:329`).
- **Chore scope**: (1) ratify the vocabulary; (2) inventory every user-visible string (the table
  above is the seed; sweep `plugin.xml` action/group texts, dialog titles, notification texts,
  `emptyText`, validation messages); (3) rename, including the docs/checklists that quote the old
  strings (e.g. `docs/features/tooling/human-verification-checklists.md`).
- **Cross-references / scope boundaries**:
  - **ROCKS-16** (`docs/features/rocks/16-package-browser-redesign/`) will land the browser
    strings — do not double-touch them here.
  - **TOOLING-08** (being planned in parallel) covers the settings-page labels.
  - **This chore covers the remainder**: run/test configuration editors, the New Project wizard,
    Tools-menu actions and their confirm dialogs, the status-bar widget, notifications.
  - Related label bugs that should ride the same vocabulary: [[bug-report|BUG-370]] (raw kind ids
    in the provision dialog), [[bug-report|BUG-373]] (missing LLS kind → raw id fallback).
