---
folders:
  - "[[features/target/requirements|requirements]]"
title: "03: UI Contextual Versions"
---

# TARGET-03: UI Contextual Versions

**Requirement**: The Project Settings panel must dynamically update available version options based on the selected Platform. Language level must be read-only, derived from the current Target.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md §3](../design.md)

---

## Overview

The current settings panel has two independent dropdowns: `Platform` and `Language Level`. After TARGET, these are replaced with `Platform` + `Version` dropdowns, where the version list is always driven by the platform selection. Language level becomes a read-only derived label, never directly selectable.

---

## UI Layout

```
┌─ Lua ──────────────────────────────────┐
│ Platform:       [Redis          ▼]     │
│ Version:        [7+             ▼]     │
│                  Language Level: Lua 5.1│
│ ...                                    │
└────────────────────────────────────────┘
```

- **Platform** combo: lists all `LuaPlatform` entries (uses `LuaPlatform.label` as display text)
- **Version** combo: populated from `PlatformVersionRegistry.getVersions(platform)` (uses `VersionEntry.label` as display text); changes when platform changes
- **Language Level**: static `JLabel`, always shows the derived level for the current `(platform, version)` pair

---

## Component Types

**File**: `src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettingsPanel.kt`

```kotlin
class LuaProjectSettingsPanel : UnnamedConfigurable {
    private val platformComboBox = ComboBox<LuaPlatform>()
    private val versionComboBox  = ComboBox<VersionEntry>()
    private val languageLevelLabel = JLabel()
    // ...
}
```

The version combo box holds `VersionEntry` objects. The renderer must call `VersionEntry.label` for display:

```kotlin
versionComboBox.renderer = SimpleListCellRenderer.create { label, value, _ ->
    label.text = value?.label ?: ""
}
```

---

## Event Handling

### Platform selection changes

When the platform combo box selection changes:

1. Clear all items from `versionComboBox`
2. Populate with `PlatformVersionRegistry.getVersions(newPlatform)`
3. Select the **last** item (most recent version) as default
4. Call `updateLanguageLevelDisplay()`

```kotlin
private fun onPlatformChanged(platform: LuaPlatform?) {
    if (platform == null) return
    val versions = PlatformVersionRegistry.getVersions(platform)
    versionComboBox.removeAllItems()
    versions.forEach { versionComboBox.addItem(it) }
    if (versionComboBox.itemCount > 0) {
        versionComboBox.selectedIndex = versionComboBox.itemCount - 1
    }
    updateLanguageLevelDisplay()
}
```

### Version selection changes

When the version combo box selection changes, call `updateLanguageLevelDisplay()`.

### Language level display update

```kotlin
private fun updateLanguageLevelDisplay() {
    val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
    val version  = versionComboBox.selectedItem as? VersionEntry ?: return
    val level    = Target(platform, version).getImplicitLanguageLevel()
    languageLevelLabel.text = "Language Level: $level"
}
```

---

## `apply()` / `reset()` / `isModified()`

```kotlin
override fun apply() {
    val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
    val version  = versionComboBox.selectedItem as? VersionEntry ?: return
    settings.setTarget(Target(platform, version))
}

override fun reset() {
    val target = settings.getTarget()
    platformComboBox.selectedItem = target.platform
    onPlatformChanged(target.platform)          // repopulates version list
    versionComboBox.selectedItem = target.version
    updateLanguageLevelDisplay()
}

override fun isModified(): Boolean {
    val current = settings.getTarget()
    val panel   = platformComboBox.selectedItem as? LuaPlatform ?: return false
    val version = versionComboBox.selectedItem  as? VersionEntry ?: return false
    return panel != current.platform || version != current.version
}
```

---

## State Consistency Rules

- The version combo box must always contain only versions valid for the currently selected platform
- Language level label must always reflect the live `(platform, version)` selection, not the saved state
- Changing platform must reset version to the platform's default **before** `apply()` is called, so the panel always holds a coherent `Target`
- `reset()` must fully restore the saved state including the correct version entry for the current platform

---

## Acceptance Criteria

- [ ] Version combo box repopulates immediately when platform selection changes
- [ ] Version list is always consistent with the selected platform (no stale entries)
- [ ] Default version selected after platform change is the last (most recent) entry
- [ ] Language level label updates immediately when either combo changes
- [ ] Language level label is read-only (not editable by user)
- [ ] `apply()` saves the currently displayed `(platform, version)` pair as the project Target
- [ ] `reset()` restores both platform and version combos to the saved state
- [ ] `isModified()` returns true only when the displayed selection differs from saved state
