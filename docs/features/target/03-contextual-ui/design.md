---
id: TARGET-03-DESIGN
parent_id: TARGET-03
type: design
folders:
  - "[[features/target/03-contextual-ui/requirements|requirements]]"
title: "Technical Design"
status: "done"
---

# Technical Design: UI Contextual Versions

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
