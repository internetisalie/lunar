---
id: "BUG-66"
title: "Installing or removing a rock does not refresh the Installed tab"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-66: Installing or removing a rock does not refresh the Installed tab

Found in the ROCKS-16 live verification run (2026-11-09).

## 1. Reproduction
Uninstall a rock from the *Installed* tab.

## 2. Expected vs Actual Behavior
- **Expected**: The row disappears; an install or update shows its new version.
- **Actual**: The rock left the disk but its row stayed listed.

## 3. Root Cause
`onInstallSucceeded`/`onRemoveSucceeded` updated only the Marketplace row.

## 4. Fix
Both now re-list the Installed tab under their own request id (`installedRequestId`), so it never cancels a Marketplace search. The list re-selects the same rock by name after an update.

## 5. Test
`LuaRocksBrowserModelTest`: install and remove both re-list (red under mutation); seen live.
