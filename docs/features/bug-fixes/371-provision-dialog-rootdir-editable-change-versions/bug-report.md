---
id: "BUG-371"
title: "Change Toolchain Versions leaves rootDir editable despite documented \"fixed\" contract"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-371: Change Toolchain Versions leaves rootDir editable despite documented "fixed" contract

> **RESOLVED 2026-07-17 (this commit)**: Added `rootDirField.isEnabled = false` in `LuaProvisionDialog.prefill()` so that the Change Versions flow (initial != null) disables both the text field and its browse button, matching the documented contract.

## 1. Reproduction

1. Provision an environment (*Tools → Lua Toolchain → Provision Lua Toolchain…*).
2. Invoke *Tools → Lua Toolchain → Change Toolchain Versions…* on that environment.
3. In the dialog, edit the **Root directory** field to a different path and confirm.

The field accepts edits (typing and the browse button both work).

## 2. Expected vs Actual Behavior

- **Expected**: per the class's own KDoc
  (`src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaProvisionDialog.kt:26-27`):
  "When [initial] is given (Change Versions), the fields are pre-filled from that request and
  **the rootDir is fixed**" — i.e. the root-directory field should be disabled (or edits should be
  a deliberately supported re-root flow).
- **Actual**: nothing disables `rootDirField`. `prefill(...)` (lines 123-133) sets
  `rootDirField.text = request.rootDir` (line 128) but never calls `isEnabled = false` /
  `setEditable(false)`, and no other code path does either. The user can silently change the root
  of an existing environment mid-"change versions", with unclear consequences (the engine would
  provision into a new directory while the environment record/manifest still points at the old
  one, or vice versa — untested territory).

## 3. Context / Environment

- **Confidence**: high for the code/KDoc mismatch (root-caused); the exact downstream corruption
  from actually re-rooting has not been characterized live.
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaProvisionDialog.kt`
    (KDoc lines 26-27; `rootDirField` at 38; `configureRootDir()` 96-104; `prefill()` 123-133)
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaToolchainActions.kt`
    (`LuaChangeToolchainVersionsAction`, the `initial != null` caller)

## 4. Other Notes

- **Fix direction**: honor the documented contract — disable `rootDirField` (field and browse
  button) when `initial != null` — or, if re-rooting is genuinely wanted, support it deliberately
  (migrate/validate the environment record) and update the KDoc. Disabling is the cheap,
  contract-matching fix.
