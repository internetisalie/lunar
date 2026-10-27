---
id: "BUG-537"
title: "The Lua Environment widget opens the Provision dialog from PopupStep.onChosen, which the platform logs as SEVERE"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-537: env widget opens a dialog from `PopupStep.onChosen`

Found by MAINT-44's HV-01 live check on GoLand 2026.2.3 (`runIde`).

## 1. Reproduction
1. In a project with a Lua environment, click the Lua Environment status-bar widget.
2. Choose *Add environment…*.

## 2. Expected vs Actual Behavior
- **Expected**: the Provision dialog opens and nothing is logged.
- **Actual**: the dialog opens, and the IDE reports *IDE error occurred*:
  ```
  SEVERE - #c.i.u.p.u.PopupImplUtil - Showing dialogs in PopupStep.onChosen can result in focus issues. Please put the handler into BaseStep.doFinalStep or PopupStep.getFinalRunnable.
    at ...LuaEnvStatusBarWidget$EnvPopupStep.invokeProvisionAction(LuaEnvStatusBarWidget.kt:106)
    at ...LuaEnvStatusBarWidget$EnvPopupStep.onChosen(LuaEnvStatusBarWidget.kt:97)
  Plugin to blame: lunar
  ```

Evidence: `~/.cache/claude-scratch/lunar/maint-44/hv/hv01-runIde-popup.png` and
`hv01-runIde-provision-dialog.png`.

## 3. Context / Environment
- `toolchain/ui/LuaEnvStatusBarWidget.kt` (`EnvPopupStep.onChosen`)
- `toolchain/ui/LuaToolchainInventoryTable.kt` (`showProjectChooser`, the same pattern)
- Platform: `BaseStep.doFinalStep`, `PopupStep.getFinalRunnable`, `PopupImplUtil`

## 4. Root Cause
`EnvPopupStep.onChosen` runs the `Lunar.Toolchain.Provision` action synchronously for the
*Add environment…* row, and that action opens `LuaProvisionDialog`. The popup is still showing while
`onChosen` runs, so the dialog opens over it. The platform detects a dialog shown from inside
`onChosen` and logs the SEVERE above. Its contract is to return `doFinalStep(runnable)`; the popup
runs the final runnable after it has closed and focus has returned.

`grep -rn onChosen src/main` finds one other implementation: the Settings toolchain inventory's
*Choose Target Project* popup (`LuaToolchainInventoryTable.showProjectChooser`), shown when several
projects are open. Its `onChosen` calls `provisionInto`, which shows `LuaProvisionDialog` with
`showAndGet()`: the same defect.

The other `EnvPopupStep` choice, selecting an environment, only writes project state and publishes
`LuaToolchainListener.TOPIC`. It opens no dialog and stays synchronous.

## 5. Fix Strategy
- `EnvPopupStep.onChosen`: return `doFinalStep { invokeProvisionAction() }` for the add row. Only the
  dispatch moves; `invokeProvisionAction` is untouched, so MAINT-44's `ActionUtil.performAction`
  change on the next line rebases cleanly. `EnvPopupStep` becomes `internal` so the test can reach it.
- `showProjectChooser`: the anonymous step becomes `internal class ProvisionTargetStep(candidates,
  provisionInto)`, whose `onChosen` returns `doFinalStep { provisionInto(selectedValue) }`.

## 6. Test Strategy
`LuaEnvPopupFinalStepTest` (light fixture):
- the add row, with `Lunar.Toolchain.Provision` replaced by a recording action: `onChosen` returns
  `FINAL_CHOICE` and runs nothing; running `finalRunnable` performs the action once;
- `ProvisionTargetStep`: `onChosen` provisions nothing; `finalRunnable` provisions the chosen project.

## 7. Verification
- With synchronous dispatch both tests fail (`expected:<0> but was:<1>`); with the fix both pass, and
  `LuaEnvStatusBarWidgetTest` still passes.
- Live, widget → *Add environment…* opens the Provision dialog with no SEVERE from lunar on `runIde`
  (2026.2.3) and `runIde261` (2026.1.3).
