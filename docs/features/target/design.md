---
folders:
  - "[[features/target/requirements|requirements]]"
title: "Detailed Design"
---

# Target Configuration Design Document

## Overview

This document provides the technical design for the **Target Configuration** feature (`TARGET`), which manages the runtime environment for Lua code by allowing users to switch between different platforms (e.g., Standard Lua, Redis, LuaJIT) and their respective versions.

---

## 1. Data Model Structure

### 1.1 Core Entities

#### `LuaPlatform` (Existing Enum)

The `LuaPlatform` enum already exists at `net.internetisalie.lunar.platform.LuaPlatform`. Add a `pathSegment` property to decouple the resource directory name from the UI label and enum name:

```kotlin
enum class LuaPlatform(val label: String, val pathSegment: String) {
    STANDARD("Standard",   "standard"),
    LUAJIT  ("LuaJIT",     "luajit"),
    PANDOC  ("Pandoc",     "pandoc"),
    REDIS   ("Redis",      "redis"),
    TARANTOOL("Tarantool", "tarantool"),
    NGX     ("OpenResty",  "ngx");

    override fun toString(): String = label
}
```

**`pathSegment`**: Stable identifier used for resource directory names. Never derived from `label` or `name` — changing the display label or renaming the enum constant does not affect resource paths.

---

#### `VersionEntry` (Data Class)
```kotlin
data class VersionEntry(
    val label: String,           // Displayed in UI (e.g., "7+", "2.1", "latest")
    val pathSegment: String,     // Used in resource paths (e.g., "redis-7", "luajit-2.1")
    val luacheckStd: String? = null  // luacheck --std value, or null if unsupported
)
```

**Purpose**: Separates the user-visible version label from the stable resource path segment. The `label` may contain characters invalid in file paths (e.g., `+`); `pathSegment` is always path-safe.

---

#### `Target` (Data Class)
```kotlin
data class Target(
    val platform: LuaPlatform,
    val version: VersionEntry
) {
    /**
     * Derives the implicit language level from the target.
     * Examples:
     *   Target(STANDARD, VersionEntry("5.1", "lua-5.1")) -> LuaLanguageLevel.LUA51
     *   Target(LUAJIT, VersionEntry("2.1", "luajit-2.1")) -> LuaLanguageLevel.LUA51
     *   Target(REDIS, VersionEntry("7+", "redis-7")) -> LuaLanguageLevel.LUA51
     */
    fun getImplicitLanguageLevel(): LuaLanguageLevel {
        return when {
            platform == STANDARD && version.label == "5.1" -> LuaLanguageLevel.LUA51
            platform == STANDARD && version.label == "5.2" -> LuaLanguageLevel.LUA52
            platform == STANDARD && version.label == "5.3" -> LuaLanguageLevel.LUA53
            platform == STANDARD && version.label == "5.4" -> LuaLanguageLevel.LUA54
            platform == STANDARD && version.label == "5.5" -> LuaLanguageLevel.LUA54  // 5.5 language level is future work
            platform == LUAJIT    -> LuaLanguageLevel.LUA51  // LuaJIT is based on Lua 5.1
            platform == REDIS     -> LuaLanguageLevel.LUA51
            platform == TARANTOOL -> LuaLanguageLevel.LUA51
            platform == NGX       -> LuaLanguageLevel.LUA51  // OpenResty uses LuaJIT
            else -> LuaLanguageLevel.LUA54  // default fallback
        }
    }

    /**
     * Returns the library root path for this target in the unified runtime structure.
     * Uses explicit path segments — no transformation of labels or enum names.
     * Examples:
     *   Target(STANDARD, VersionEntry("5.1", "lua-5.1")) -> "runtime/standard/lua-5.1"
     *   Target(LUAJIT,   VersionEntry("2.1", "luajit-2.1")) -> "runtime/luajit/luajit-2.1"
     *   Target(REDIS,    VersionEntry("7+",  "redis-7")) -> "runtime/redis/redis-7"
     */
    fun getLibraryRootPath(): String =
        "runtime/${platform.pathSegment}/${version.pathSegment}"

    companion object {
        fun default(): Target = Target(STANDARD, PlatformVersionRegistry.defaultVersion(STANDARD))
    }
}
```

**Purpose**: Immutable representation of a selected target. Provides deterministic derivation of language level and library paths with no string manipulation of display values.

---

### 1.2 Settings State Update

#### Current Structure
```kotlin
// LuaProjectSettings.State
class State {
    var languageLevel: LuaLanguageLevel = LuaLanguageLevel.LUA54
    // ... other settings
}
```

#### New Structure (Backward Compatible)
```kotlin
// LuaProjectSettings.State
class State {
    // New field (optional during migration)
    var target: Target? = null
    
    // Legacy field (deprecated, kept for migration)
    @Deprecated("Use target instead")
    var languageLevel: LuaLanguageLevel = LuaLanguageLevel.LUA54
    
    // Note: existing `platform` field is superseded by target.platform and removed
    // ... other settings
}
```

**Serialization Strategy**:
- Serialize `target` as a map: `{ platform: "STANDARD", version: "5.1" }`
- When deserializing, check for `target` first; if missing, fall back to `languageLevel`
- During save, write both fields for backward compatibility until migration period ends

---

### 1.3 Static Pre-Populated Version Registry

Available versions are hardcoded and pre-populated in the plugin. All supported targets are bundled with the plugin release. Each version has an explicit `label` (for UI) and `pathSegment` (for resource paths) — the two are never derived from one another.

```kotlin
object PlatformVersionRegistry {
    private val registry = mapOf(
        LuaPlatform.STANDARD  to listOf(
            VersionEntry("5.1", "lua-5.1", luacheckStd = "lua51"),
            VersionEntry("5.2", "lua-5.2", luacheckStd = "lua52"),
            VersionEntry("5.3", "lua-5.3", luacheckStd = "lua53"),
            VersionEntry("5.4", "lua-5.4", luacheckStd = "lua54"),
            VersionEntry("5.5", "lua-5.5", luacheckStd = "lua55"),
        ),
        LuaPlatform.LUAJIT    to listOf(
            VersionEntry("2.0", "luajit-2.0", luacheckStd = "luajit"),
            VersionEntry("2.1", "luajit-2.1", luacheckStd = "luajit"),
        ),
        LuaPlatform.REDIS     to listOf(
            VersionEntry("5",   "redis-5", luacheckStd = "redis5"),
            VersionEntry("6",   "redis-6", luacheckStd = "redis6"),
            VersionEntry("7+",  "redis-7", luacheckStd = "redis7"),
        ),
        LuaPlatform.TARANTOOL to listOf(VersionEntry("2.10", "tarantool-2.10")),
        LuaPlatform.NGX       to listOf(VersionEntry("latest", "ngx-latest")),
        LuaPlatform.PANDOC    to listOf(VersionEntry("latest", "pandoc-latest")),
    )

    fun getVersions(platform: LuaPlatform): List<VersionEntry> =
        registry[platform] ?: emptyList()

    fun defaultVersion(platform: LuaPlatform): VersionEntry =
        getVersions(platform).last()

    fun findVersion(platform: LuaPlatform, label: String): VersionEntry? =
        getVersions(platform).find { it.label == label }
}
```

**Rationale**:
- All supported targets are pre-populated in plugin resources
- No late-installable SDKs (future work)
- No runtime discovery overhead
- Simplified UI: version combo always populated from registry
- New versions require plugin release (controlled distribution)

**Platform Notes**:
- **LUAJIT**: Separate platform from STANDARD (not a version of Lua). Based on Lua 5.1 with extended libraries (`bit`, `jit` modules).
- **NGX**: OpenResty/Nginx Lua scripting. Built on LuaJIT.
- **REDIS**: Version 7+ represents Redis 7 and all later versions (Redis 8+ share the same Lua API as 7.0).
- **LUA 5.5**: Recently released; language level support is future work (currently treated as generic 5.5).
- Versions shown are examples; actual versions depend on available resources.

---

## 2. Unified Resource Structure

### 2.1 Directory Structure

Migrate to a unified resource structure under `resources/runtime/`:

**Current Structure**:
```
resources/
├── platform/
│   ├── Lua50/
│   ├── Lua51/
│   ├── Lua52/
│   ├── Lua53/
│   └── Lua54/
└── sdk/
    └── redis-8/
```

**New Unified Structure**:
```
resources/
└── runtime/
    ├── standard/
    │   ├── lua-5.0/
    │   ├── lua-5.1/
    │   ├── lua-5.2/
    │   ├── lua-5.3/
    │   ├── lua-5.4/
    │   └── lua-5.5/
    ├── luajit/
    │   ├── luajit-2.0/
    │   └── luajit-2.1/
    ├── redis/
    │   ├── redis-5/
    │   ├── redis-6/
    │   └── redis-7/
    ├── tarantool/
    │   └── tarantool-2.10/
    ├── ngx/
    │   └── ngx-latest/
    └── pandoc/
        └── pandoc-latest/
```

**Benefits**:
- Single source of truth for all runtimes/platforms
- Consistent naming: `<platform>/<platform-version>/`
- Easier to add new platforms/versions
- Library resolution becomes uniform and predictable
- Clear structure for future expansion

---

### 2.2 Directory Migration

All library files are migrated as part of TARGET implementation:

| Source | Destination |
| :--- | :--- |
| `platform/Lua50/*` | `runtime/standard/lua-5.0/*` |
| `platform/Lua51/*` | `runtime/standard/lua-5.1/*` |
| `platform/Lua52/*` | `runtime/standard/lua-5.2/*` |
| `platform/Lua53/*` | `runtime/standard/lua-5.3/*` |
| `platform/Lua54/*` | `runtime/standard/lua-5.4/*` |
| `sdk/redis-8/*` | `runtime/redis/redis-7/*` ¹ |

¹ Redis 8.0 uses Lua 5.1 like Redis 7, so it can share the redis-7 library.

**Future Migrations** (as libraries become available):
- `runtime/luajit/luajit-2.0/` and `runtime/luajit/luajit-2.1/` (LuaJIT-specific globals)
- `runtime/ngx/ngx-latest/` (OpenResty/Nginx)
- `runtime/tarantool/tarantool-2.10/` (Tarantool database)

Remove old directories after migration is complete.

---

## 3. Architecture for Version Contextual UI Updates

### 3.1 Settings Panel Flow

#### `LuaProjectSettingsPanel`

**Current Structure**:
```kotlin
class LuaProjectSettingsPanel : UnnamedConfigurable {
    private val languageLevelComboBox = ComboBox<LuaLanguageLevel>()
    
    override fun createComponent(): JComponent {
        // ... UI setup
        languageLevelComboBox.addItemListener { event ->
            // Apply selection
        }
        return panel
    }
}
```

**New Structure (Updated)**:
```kotlin
class LuaProjectSettingsPanel : UnnamedConfigurable {
    private val platformComboBox = ComboBox<LuaPlatform>()
    private val versionComboBox = ComboBox<VersionEntry>()
    private val languageLevelLabel = JLabel()  // Read-only display
    
    override fun createComponent(): JComponent {
        setupPlatformComboBox()
        setupVersionComboBox()
        setupLanguageLevelDisplay()
        
        platformComboBox.addItemListener { event ->
            onPlatformChanged(event.item as? LuaPlatform)
        }
        
        versionComboBox.addItemListener { event ->
            onVersionChanged(event.item as? VersionEntry)
        }
        
        return createPanel()
    }

    private fun onPlatformChanged(platform: LuaPlatform?) {
        if (platform == null) return
        
        // Update version combo box with pre-populated VersionEntry objects
        val versions = PlatformVersionRegistry.getVersions(platform)
        versionComboBox.removeAllItems()
        versions.forEach { versionComboBox.addItem(it) }
        
        // Select last version as default (most recent)
        if (versionComboBox.itemCount > 0) {
            versionComboBox.selectedIndex = versionComboBox.itemCount - 1
        }
        
        updateLanguageLevelDisplay()
    }

    private fun onVersionChanged(version: VersionEntry?) {
        updateLanguageLevelDisplay()
    }

    private fun updateLanguageLevelDisplay() {
        val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
        val version = versionComboBox.selectedItem as? VersionEntry ?: return
        val target = Target(platform, version)
        val level = target.getImplicitLanguageLevel()
        languageLevelLabel.text = "Language Level: ${level}"
    }

    override fun apply() {
        val platform = platformComboBox.selectedItem as? LuaPlatform ?: return
        val version = versionComboBox.selectedItem as? VersionEntry ?: return
        settings.target = Target(platform, version)
    }

    override fun reset() {
        val currentTarget = settings.target ?: Target.default()
        platformComboBox.selectedItem = currentTarget.platform
        onPlatformChanged(currentTarget.platform)
        versionComboBox.selectedItem = currentTarget.version
        updateLanguageLevelDisplay()
    }

    private fun createPanel(): JComponent {
        // Create a FormBuilder layout with:
        // - "Platform:" label + platformComboBox
        // - "Version:" label + versionComboBox
        // - languageLevelLabel (read-only)
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Platform:", platformComboBox)
            .addLabeledComponent("Version:", versionComboBox)
            .addLabeledComponent("", languageLevelLabel)
            .panel
    }
}
```

**Key Design Principles**:
- Version combo box **dynamically populated** based on platform selection
- Language level is **read-only** and **derived** from target (not user-selectable)
- Platform drives version availability (contextual UI)

---

### 3.2 State Management

**Settings Store Integration**:
```kotlin
// In LuaProjectSettings.getService()
fun getTarget(): Target {
    return state.target ?: migrateFromLegacySettings()
}

fun setTarget(target: Target) {
    state.target = target
    state.languageLevel = target.getImplicitLanguageLevel()  // Keep in sync
}

private fun migrateFromLegacySettings(): Target {
    // The existing State has both `platform` and `languageLevel` fields.
    // Use both to construct the best possible default target.
    val existingPlatform = state.platform  // already present in State
    if (existingPlatform != LuaPlatform.STANDARD) {
        // Non-standard platform: use the newest registered version as default
        return Target(existingPlatform, PlatformVersionRegistry.defaultVersion(existingPlatform))
    }
    // Standard platform: derive version label from languageLevel, then look up VersionEntry
    val versionLabel = when (state.languageLevel) {
        LuaLanguageLevel.LUA50 -> "5.0"
        LuaLanguageLevel.LUA51 -> "5.1"
        LuaLanguageLevel.LUA52 -> "5.2"
        LuaLanguageLevel.LUA53 -> "5.3"
        LuaLanguageLevel.LUA54 -> "5.4"
    }
    val version = PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, versionLabel)
        ?: PlatformVersionRegistry.defaultVersion(LuaPlatform.STANDARD)
    return Target(LuaPlatform.STANDARD, version)
}
```

---

## 4. Library Root Resolution

### 4.1 Current Implementation (Baseline)

**Current Code** (`PlatformLibraryIndex`):
```kotlin
object PlatformLibraryIndex {
    fun getPlatformLibrary(languageLevel: LuaLanguageLevel): LuaLibrary? {
        val libraryName = when (languageLevel) {
            LuaLanguageLevel.LUA51 -> "Lua51"
            LuaLanguageLevel.LUA52 -> "Lua52"
            LuaLanguageLevel.LUA53 -> "Lua53"
            LuaLanguageLevel.LUA54 -> "Lua54"
            else -> return null
        }
        val pluginPath = getPluginResourcePath("platform/$libraryName")
        return loadLibraryFromPath(pluginPath)
    }
}
```

---

### 4.2 New Implementation (Target-Based)

#### Architecture: Multi-Provider Pattern

```kotlin
interface LibraryProvider {
    /**
     * Returns the library for a given target.
     * Returns null if not applicable.
     */
    fun getLibrary(target: Target): LuaLibrary?
}

object PlatformLibraryIndex {
    private val providers = listOf(
        RuntimeLibraryProvider
    )

    fun getPlatformLibrary(project: Project): LuaLibrary? {
        val settings = LuaProjectSettings.getInstance(project)
        val target = settings.getTarget()
        
        // Try each provider in order
        for (provider in providers) {
            val library = provider.getLibrary(target)
            if (library != null) return library
        }
        
        return null  // Fallback to default
    }
}

// Single unified provider for all platforms using Target.getLibraryRootPath()
object RuntimeLibraryProvider : LibraryProvider {
    override fun getLibrary(target: Target): LuaLibrary? {
        val rootPath = target.getLibraryRootPath()
        val pluginPath = getPluginResourcePath(rootPath)
        // Returns null if the directory doesn't exist (e.g., PANDOC/NGX have no bundled libs yet)
        return loadLibraryFromPath(pluginPath)
    }
}
```

**Note on missing libraries**: If `loadLibraryFromPath` returns null (directory not found), the platform provider returns null, and no library is contributed. This is the expected behavior for platforms with no bundled library files (e.g., PANDOC, NGX). Future work will add library files for these platforms.

**Key Design Principles**:
- **Separation of Concerns**: Each platform/provider handles its own library loading
- **Composability**: Easy to add new providers for new platforms (e.g., LuaJIT variants)
- **Testability**: Providers can be unit tested independently
- **Target-Driven**: Language level automatically derives from target; no need to track separately

---

### 4.3 Integration Points

**Where Library Resolution Occurs**:

1. **Indexing**: `LuaFileStubIndex` uses library for symbol resolution
2. **Inspection**: Type checking inspections use library to validate calls
3. **Completion**: Auto-completion uses library for standard library symbols
4. **Hover**: Documentation hover uses library symbols

**Update Flow**:
```
User changes Target in Settings
    ↓
LuaProjectSettings.setTarget(target) called
    ↓
ProjectLevelModificationTracker invalidated
    ↓
All caches depending on PlatformLibraryIndex cleared
    ↓
Next indexing/completion/inspection uses new library
```

---

### 4.4 Luacheck Integration

When a Target is selected, the luacheck `--std` argument must reflect the platform. The value is declared on the `VersionEntry` itself — no derivation from labels or enum names:

```kotlin
fun getLuacheckStd(): String? = version.luacheckStd
```

When `getLuacheckStd()` returns null, luacheck is invoked without a `--std` override (uses luacheck's default).

---

## 5. Backward Compatibility & Migration Strategy

### 5.1 Migration Scenarios

#### Scenario A: Project with Old `languageLevel` Setting
```xml
<!-- .idea/misc.xml (before) -->
<project>
  <component name="Lua.ProjectSettings">
    <option name="languageLevel" value="LUA52" />
  </component>
</project>
```

**Migration Logic**:
1. On plugin startup, `LuaProjectSettings` is loaded
2. Serializer detects `languageLevel` but no `target`
3. Automatic migration: `Target(STANDARD, "5.2")`
4. Both fields saved on next settings update
5. Eventually, old field can be deprecated and removed

---

#### Scenario B: New Project
```xml
<!-- .idea/misc.xml (after) -->
<project>
  <component name="Lua.ProjectSettings">
    <option name="target">
      <map>
        <entry key="platform" value="STANDARD" />
    <option name="version" value="5.4" />   <!-- version.label -->
      </map>
    </option>
  </component>
</project>
```

**User Flow**:
1. Open Lua project (no settings yet)
1. Settings panel defaults to `Target(STANDARD, PlatformVersionRegistry.defaultVersion(STANDARD))`
3. User can select different platform/version
4. Settings saved with new structure

---

### 5.2 Implementation Details

#### Serialization (JPS Serializer)
```kotlin
class LuaProjectSettingsSerializer : AbstractProjectComponent() {
    // Handle readExternal/writeExternal with dual-field support
    
    override fun readExternal(element: Element) {
        val targetChild = element.getChild("target")
        if (targetChild != null) {
            // New format: parse platform enum name and version label
            val platform = targetChild.getAttributeValue("platform")?.let { LuaPlatform.valueOf(it) }
            val versionLabel = targetChild.getAttributeValue("version")
            if (platform != null && versionLabel != null) {
                // Look up the VersionEntry by label — never construct from raw string
                val version = PlatformVersionRegistry.findVersion(platform, versionLabel)
                    ?: PlatformVersionRegistry.defaultVersion(platform)
                state.target = Target(platform, version)
            }
        } else {
            // Fallback: try old languageLevel format
            val levelAttr = element.getAttributeValue("languageLevel")
            if (levelAttr != null) {
                state.languageLevel = LuaLanguageLevel.valueOf(levelAttr)
            }
        }
    }

    override fun writeExternal(element: Element) {
        val target = state.target ?: return
        
        // Serialize platform by enum name (stable), version by label (user-readable key)
        val targetChild = Element("target")
        targetChild.setAttribute("platform", target.platform.name)
        targetChild.setAttribute("version", target.version.label)
        element.addContent(targetChild)
        
        // Also write legacy field for backward compat
        element.setAttribute("languageLevel", target.getImplicitLanguageLevel().name)
    }
}
```

---

### 5.3 Migration Phasing

**Phase 1** (Current):
- New `Target` field added to settings
- Old `languageLevel` field kept, auto-migrated if needed
- Both fields serialized for compatibility
- UI uses new field

**Phase 2** (Future Release, ~2-3 releases later):
- Remove old `languageLevel` field from serialization
- Deprecation warnings in logs if old format detected
- Still support reading old format

**Phase 3** (Further Future):
- Old format completely removed

---

## 6. Implementation Roadmap

### Prerequisites (before Phase 1)
- [ ] Add `LUAJIT` and `NGX` enum entries to `LuaPlatform.kt`
- [ ] Remove existing `platform: LuaPlatform` field from `LuaProjectSettings.State` (superseded by `target`)

### Phase 1: Core Data Model
- [ ] Define `Target` data class with `getImplicitLanguageLevel()`, `getLibraryRootPath()`, and `getLuacheckStd()`
- [ ] Create `PlatformVersionRegistry` with pre-populated versions
- [ ] Update `LuaProjectSettings.State` to include `target` field
- [ ] Implement `migrateFromLegacySettings()` (handles existing `platform` + `languageLevel` fields)
- [ ] Implement serialization logic with backward compat

### Phase 2: Resource Migration
- [ ] Move `platform/Lua5X/` directories to `runtime/standard/lua-5.X/`
- [ ] Move `sdk/redis-8/` to `runtime/redis/redis-7/`
- [ ] Update `PlatformLibraryIndex` to use unified `runtime/` paths via `RuntimeLibraryProvider`
- [ ] Remove old `platform/` and `sdk/` directories

### Phase 3: Settings UI
- [ ] Update `LuaProjectSettingsPanel` with platform/version dropdowns
- [ ] Implement contextual version filtering (population from registry)
- [ ] Add language level display (read-only, derived from target)
- [ ] Verify UI layout and usability

### Phase 4: Integration
- [ ] Update all code using `getLanguageLevel()` / `state.languageLevel` to use `getTarget()`
- [ ] Wire `getLuacheckStd()` into luacheck invocation (pass `--std` argument)
- [ ] Verify library loading for all platforms

### Phase 5: Testing & Polish
- [ ] Unit tests for `Target` derivation logic (language level, library path, luacheck std)
- [ ] UI integration tests for platform/version selection
- [ ] Library resolution tests for all platforms
- [ ] Migration tests (legacy `languageLevel`, legacy `platform`, combined)
- [ ] End-to-end integration tests

---

## 7. File Structure

### Kotlin Source Files

```
docs/features/target/
├── requirements.md       # User-facing requirements (existing)
├── design.md            # This document
└── spec/                # (Future) Detailed specs per requirement
    ├── TARGET-01-data-model.md
    ├── TARGET-02-implicit-level.md
    ├── TARGET-03-contextual-ui.md
    ├── TARGET-04-library-resolution.md
    ├── TARGET-05-luacheck-integration.md
    └── TARGET-06-migration.md

src/main/kotlin/net/internetisalie/lunar/
├── project/
│   └── LuaProjectSettings.kt  # Add Target field & migration logic
├── platform/
│   ├── PlatformLibraryIndex.kt  # Refactor to use RuntimeLibraryProvider
│   ├── LuaPlatform.kt           # (existing — add LUAJIT, NGX entries)
│   └── target/
│       ├── Target.kt
│       └── PlatformVersionRegistry.kt
└── settings/
    └── LuaProjectSettingsPanel.kt  # Update UI
```

**Note on `Target.kt` package**: `Target` is placed under `platform/target/` (not `lang/target/`) because it depends on `LuaPlatform` and represents a runtime environment concept, not a language syntax concept.

### Resources

Migrated to unified runtime structure:
```
src/main/resources/
└── runtime/
    ├── standard/
    │   ├── lua-5.0/    # Lua 5.0
    │   ├── lua-5.1/    # Lua 5.1
    │   ├── lua-5.2/    # Lua 5.2
    │   ├── lua-5.3/    # Lua 5.3
    │   └── lua-5.4/    # Lua 5.4
    ├── luajit/
    │   ├── luajit-2.0/ # LuaJIT 2.0 (future — files to be created)
    │   └── luajit-2.1/ # LuaJIT 2.1 (future — files to be created)
    ├── redis/
    │   ├── redis-5/    # Redis 5 SDK
    │   ├── redis-6/    # Redis 6 SDK
    │   └── redis-7/    # Redis 7+ SDK (migrated from sdk/redis-8/)
    ├── tarantool/      # (future — files to be created)
    ├── ngx/            # (future — files to be created)
    └── pandoc/         # (future — files to be created)
```

---

## 8. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `Target(platform, version: VersionEntry)` immutable data class | Simplifies reasoning about state; prevents accidental inconsistencies |
| `VersionEntry(label, pathSegment, luacheckStd)` | Decouples UI display, resource path, and luacheck std — none derived from the others |
| `LuaPlatform.pathSegment` explicit on enum | Resource directory names are stable identifiers; never transformed from `label` or `name` |
| Pre-populated `PlatformVersionRegistry` | No runtime discovery overhead; versions bundled with plugin |
| Derive language level from target | Single source of truth; prevents mismatch between platform, version, and level |
| `getLuacheckStd()` delegates to `version.luacheckStd` | No string transformation at call site; mapping lives with the data |
| Unified `runtime/` directory structure | Clean, consistent organization; supports all platforms uniformly |
| Direct migration (no phased rollout) | Simplifies code; no need for backward compatibility with old paths |
| Single `RuntimeLibraryProvider` using `getLibraryRootPath()` | Uniform resolution for all platforms; no per-platform conditional logic |
| Dual-field serialization during migration | Gradual migration without breaking existing projects |
| Settings panel with read-only language level | Prevents user confusion; enforces derived relationship |
| `Target` in `platform/target/` package | Depends on `LuaPlatform`; belongs with platform concepts, not language syntax |

---

## 9. Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Existing projects fail to load | `migrateFromLegacySettings()` handles both `platform` + `languageLevel` legacy fields |
| Library path resolution broken | Unified `RuntimeLibraryProvider` with null-safe path loading; unit tests for each platform |
| Platforms with no library files (PANDOC, NGX, TARANTOOL) | `loadLibraryFromPath` returns null gracefully; no library contributed |
| UI layout doesn't fit | Use FormBuilder for responsive layout; collapsible sections if needed |
| Symbol resolution inconsistent | Single Target source of truth; derived language level prevents mismatches |
| `LUAJIT`/`NGX` missing from `LuaPlatform.kt` | Enum additions are listed as explicit prerequisites before Phase 1 |

---

## 10. Acceptance Criteria

- [ ] All six requirements (TARGET-01 through TARGET-06) fully implemented
- [ ] Existing projects with `languageLevel` setting migrate automatically (including `LUA50`)
- [ ] Existing projects with non-STANDARD `platform` setting migrate to correct Target
- [ ] Settings panel correctly updates version list when platform changes
- [ ] Language level display correctly shows derived value
- [ ] Library root resolved correctly for all platform/version combinations with bundled files
- [ ] Luacheck `--std` argument reflects selected target
- [ ] All existing tests pass; new tests cover migration, luacheck std derivation, and library resolution
- [ ] Documentation updated to reflect new platform/version model
