---
id: "BUG-372"
title: "App-level Provision… button silently guesses the target project"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-372: App-level Provision… button silently guesses the target project

## 1. Reproduction

1. Open **two** projects in the IDE.
2. From the *second* project's window, open *Settings → Languages & Frameworks → Lua → Toolchain*
   (an application-level page) and click the **Provision…** toolbar button.
3. Complete the dialog.

Variant: close all projects, open the Toolchain page from the Welcome-screen settings — the
**Provision…** button is disabled with no explanation.

## 2. Expected vs Actual Behavior

- **Expected**: provisioning targets an explicit, visible project — either a project chooser when
  more than one is open, or the target project displayed in the dialog. When no project is open,
  the disabled button should explain why (or the action be hidden).
- **Actual**: the app-level page has no project context, so it guesses
  `ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault && !it.isDisposed }` —
  i.e. the *first* open project, regardless of which window the settings dialog belongs to. With
  two projects open, the environment record can be written into the **wrong project's** settings
  (`LuaToolchainProjectSettings`) with no indication anywhere in the flow. With none open, the
  button silently disables.

## 3. Context / Environment

- **Confidence**: high — root-caused in code (wrong-project registration with two projects open
  follows directly from `openProjects.firstOrNull`; not yet demonstrated live).
- **Root cause**: `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaToolchainInventoryTable.kt`:
  - `provision()` at lines 98-104 uses `openProject()` as the dialog's `targetProject`;
  - `openProject()` at lines 106-107 is the `firstOrNull` guess;
  - `provisionButton()` at lines 119-126 disables the action when `openProject() == null`
    (no tooltip/reason).
  The provision result is then registered into that guessed project's
  `LuaToolchainProjectSettings` via `LuaProvisionResultSink`.
- **Doc mismatch**: `docs/features/tooling/human-verification-checklists.md` Scenario 06.2
  (line 398 ff.) expects "Provision… opens the TOOLING-04 dialog and does nothing else" — it does
  not cover the multi-project targeting question, so the checklist would pass while the wrong
  project gets the environment.

## 4. Other Notes

- **Fix direction**: when invoked from the app-level page, show a project chooser if more than one
  project is open (platform `ProjectSelector`-style popup), or at minimum display the resolved
  target project prominently in `LuaProvisionDialog` (it already receives `targetProject`) so the
  guess is visible. Also give the disabled button a "no open project" tooltip. Update checklist
  scenario 06.2 to cover the two-project case.
- The project-level entry points (Tools-menu actions in `LuaToolchainActions.kt`, the status-bar
  widget) are unaffected — they have a real project context.
