# Human Verification Checklists: LuaRocks Integration (ROCKS Epic)

This document provides step-by-step verification procedures for the ROCKS tasks.

---

## ROCKS-02: Package Browser

### ROCKS-02 Phase 1: Search Service & Caching
- [ ] **Search Parsing**: Verify that CLI output from `luarocks search` is correctly mapped to package models.
- [ ] **Cache Check**: Verify that `luarocks list` results are cached and visible in the UI.

### ROCKS-02 Phase 2: Split-View Tool Window
- [ ] **UI Layout**: Open the Package Browser and verify the split-pane resizes correctly.
- [ ] **Search Debounce**: Verify that searching doesn't trigger a CLI call for every keystroke.
- [ ] **Metadata Rendering**: Verify that package homepages and descriptions are readable in the detail pane.

### ROCKS-02 Phase 3: Action Handlers & Versioning
- [ ] **Install Flow**: Click "Install" for a package and verify the async progress bar appears.
- [ ] **Version Selection**: Select a non-latest version and verify the install command uses the correct version string.
- [ ] **Post-Install Refresh**: Verify the "Installed" badge updates immediately after a successful installation.

---

## ROCKS-03: Dependency Resolution

### ROCKS-03 Phase 1: Data Extraction
- [ ] **JSON Bridge**: Verify that the bundled `rockspec.lua` can extract dependencies from a complex rockspec.
- [ ] **Manifest Parsing**: Verify that installed packages in `lua_modules` are correctly detected.

### ROCKS-03 Phase 2: Graph Logic & Conflict Engine
- [ ] **Transitive Walk**: Verify that deep dependencies (e.g., `A -> B -> C`) are resolved and correctly linked.
- [ ] **Conflict Flagging**: Induce a version conflict and verify the `VersionConflictEngine` flags the offending nodes.

### ROCKS-03 Phase 3: Tree View & Visualization
- [ ] **Tree UI**: Verify the dependency tree expands and collapses correctly.
- [ ] **Inspector Data**: Select a node and verify the "Required by" list is accurate.
- [ ] **Tree Filter**: Use the filter bar to isolate a specific dependency in the graph.

---

## ROCKS-01: Project Initialization & Setup

### ROCKS-01 Phase 1: Templates & Resources (#164)
- [ ] **Resource Check**: Verify that \`.luacheckrc\` and \`.stylua.toml\` templates are present in the JAR resources.
- [ ] **Setup Template**: Verify \`generateSetupLua\` output matches the technical design for various Lua versions.

### ROCKS-01 Phase 2: CLI Integration (#165)
- [ ] **Init Command**: Run the scaffolder and verify \`luarocks init\` is called with correct flags.
- [ ] **Git Check**: Verify \`.gitignore\` is updated with standard LuaRocks entries.

### ROCKS-01 Phase 3: Project Wizard (#166)
- [ ] **Wizard UI**: Open the "New Project" dialog and verify the LuaRocks template appears.
- [ ] **Template Selection**: Verify that selecting "Neovim Plugin" creates a \`lua/\` directory structure.
- [ ] **End-to-End**: Create a project and verify all files (rockspec, setup.lua, configs) are created.

---

## ROCKS-02: Package Browser

### ROCKS-02 Phase 1: Search Service & Caching
- [ ] **Search Parsing**: Verify that CLI output from `luarocks search` is correctly mapped to package models.
- [ ] **Cache Check**: Verify that `luarocks list` results are cached and visible in the UI.

### ROCKS-02 Phase 2: Split-View Tool Window
- [ ] **UI Layout**: Open the Package Browser and verify the split-pane resizes correctly.
- [ ] **Search Debounce**: Verify that searching doesn't trigger a CLI call for every keystroke.
- [ ] **Metadata Rendering**: Verify that package homepages and descriptions are readable in the detail pane.

### ROCKS-02 Phase 3: Action Handlers & Versioning
- [ ] **Install Flow**: Click "Install" for a package and verify the async progress bar appears.
- [ ] **Version Selection**: Select a non-latest version and verify the install command uses the correct version string.
- [ ] **Post-Install Refresh**: Verify the "Installed" badge updates immediately after a successful installation.

---

## ROCKS-03: Dependency Resolution

### ROCKS-03 Phase 1: Data Extraction
- [ ] **JSON Bridge**: Verify that the bundled `rockspec.lua` can extract dependencies from a complex rockspec.
- [ ] **Manifest Parsing**: Verify that installed packages in `lua_modules` are correctly detected.

### ROCKS-03 Phase 2: Graph Logic & Conflict Engine
- [ ] **Transitive Walk**: Verify that deep dependencies (e.g., `A -> B -> C`) are resolved and correctly linked.
- [ ] **Conflict Flagging**: Induce a version conflict and verify the `VersionConflictEngine` flags the offending nodes.

### ROCKS-03 Phase 3: Tree View & Visualization
- [ ] **Tree UI**: Verify the dependency tree expands and collapses correctly.
- [ ] **Inspector Data**: Select a node and verify the "Required by" list is accurate.
- [ ] **Tree Filter**: Use the filter bar to isolate a specific dependency in the graph.

---

## ROCKS-04: Task Execution & Run Configurations

### ROCKS-04 Phase 1: Core Type (#169)
- [ ] **Registration**: Verify "LuaRocks" appears in the "Edit Configurations" -> "+" menu.

### ROCKS-04 Phase 2: Editor UI (#170)
- [ ] **Fields**: Verify Command, Arguments, and Rockspec fields are functional and persist changes.

### ROCKS-04 Phase 3: Execution Engine (#171)
- [ ] **Execution**: Run a "make" configuration. Verify it uses the project's bound \`luarocks\` binary.
- [ ] **Console**: Verify that process output is streamed to the Run tool window.

---

## ROCKS-02: Package Browser

### ROCKS-02 Phase 1: Search Service & Caching
- [ ] **Search Parsing**: Verify that CLI output from `luarocks search` is correctly mapped to package models.
- [ ] **Cache Check**: Verify that `luarocks list` results are cached and visible in the UI.

### ROCKS-02 Phase 2: Split-View Tool Window
- [ ] **UI Layout**: Open the Package Browser and verify the split-pane resizes correctly.
- [ ] **Search Debounce**: Verify that searching doesn't trigger a CLI call for every keystroke.
- [ ] **Metadata Rendering**: Verify that package homepages and descriptions are readable in the detail pane.

### ROCKS-02 Phase 3: Action Handlers & Versioning
- [ ] **Install Flow**: Click "Install" for a package and verify the async progress bar appears.
- [ ] **Version Selection**: Select a non-latest version and verify the install command uses the correct version string.
- [ ] **Post-Install Refresh**: Verify the "Installed" badge updates immediately after a successful installation.

---

## ROCKS-03: Dependency Resolution

### ROCKS-03 Phase 1: Data Extraction
- [ ] **JSON Bridge**: Verify that the bundled `rockspec.lua` can extract dependencies from a complex rockspec.
- [ ] **Manifest Parsing**: Verify that installed packages in `lua_modules` are correctly detected.

### ROCKS-03 Phase 2: Graph Logic & Conflict Engine
- [ ] **Transitive Walk**: Verify that deep dependencies (e.g., `A -> B -> C`) are resolved and correctly linked.
- [ ] **Conflict Flagging**: Induce a version conflict and verify the `VersionConflictEngine` flags the offending nodes.

### ROCKS-03 Phase 3: Tree View & Visualization
- [ ] **Tree UI**: Verify the dependency tree expands and collapses correctly.
- [ ] **Inspector Data**: Select a node and verify the "Required by" list is accurate.
- [ ] **Tree Filter**: Use the filter bar to isolate a specific dependency in the graph.

---

## ROCKS-DR: De-risking Tasks

### ROCKS-DR-01: Prototype resolution mechanism (#167)
- [ ] **LUA_INIT Test**: Verify that setting \`LUA_INIT\` correctly loads \`setup.lua\` across different shells.
- [ ] **-l setup Test**: Verify if \`-l setup\` works as a reliable alternative for scripts.

### ROCKS-DR-02: Test init robustness (#168)
- [ ] **Existing Dir**: Run init on a directory with an existing \`src/\` folder. Verify it doesn't delete existing files.
