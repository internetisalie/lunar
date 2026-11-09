---
id: "BUG-65"
title: "An outdated installed rock never offers Update"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-65: An outdated installed rock never offers Update

Found in the ROCKS-16 live verification run (2026-11-09).

## 1. Reproduction
Install `inspect` 3.1.2-0 from the Marketplace (3.1.3-0 exists), then view it in either tab.

## 2. Expected vs Actual Behavior
- **Expected**: An update marker on the row, and Update in the detail pane, in both tabs.
- **Actual**: Neither tab ever showed Update.

## 3. Root Cause
After an install, `LuaRocksBrowserModel.flipRow` set only `installed`. The Installed tab's detail pane offered just the installed version, because `InstalledRockRow` holds no available versions.

## 4. Fix
`onInstallSucceeded(name, version)` sets `hasUpdate` when the installed version is older than the row's newest. The Installed tab asks `availableVersions(name)` and `PackageDetailPane.showAvailableVersions` offers them, with Update when one is newer.

## 5. Test
`LuaRocksBrowserModelTest`: installing an older version flags an update, and the available versions are one package's, newest first. Both are red under mutation; seen live in both tabs.
